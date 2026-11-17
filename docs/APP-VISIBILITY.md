# 应用侧可见性加固（内核）

实现：`.github/scripts/patch_abk_app_vis.py`（构建步骤「应用侧可见性加固 (ABK)」）。
所有规则只在调用者是 **app uid（`current_uid().val >= 10000`）** 时生效；root、system、
ksu 域进程看到的永远是完整视图。

## 1. 目录遮蔽：看得见，里面是空的

`/data/local/tmp`（可用启动参数 `abk_empty_dirs=/a,/b` 覆盖）对 app 表现为空目录：

| 操作 | app uid | root |
|---|---|---|
| `stat("/data/local/tmp")` | 成功 | 成功 |
| `readdir("/data/local/tmp")` | 空（且 DAC 本来就拒绝，见下） | 完整 |
| `stat("/data/local/tmp/<任意子项>")` | **ENOENT** | 成功 |

实现分两层：

1. **readdir**：`fs/readdir.c` 的 5 个 actor（filldir / filldir64 / fillonedir /
   compat_filldir / compat_fillonedir）入口处查 `(dir_ino, dir_dev)` 是否命中遮蔽表；
2. **lookup**：`fs/namei.c` 的 `link_path_walk()` 在**进入**被遮蔽目录时返回 `-ENOENT`，
   于是任何子项都不存在。

**为什么两层都要**：只做 readdir 没有实际效果——`/data/local/tmp` 是 `0771 root:shell`，
app 本来就被 DAC 拒绝列表；真正的泄漏是 `stat` 一个已知文件名（`ksud`、`temp_su` 之类），
这一层只有 namei 规则能挡住。

遮蔽表在**策略 supercall**（`/.abk-policy`）里初始化：`kern_path()` 不能在 readdir 钩子里做，
那里已经持有被列目录的 `i_rwsem`，再走一次路径解析可能死锁。

## 2. devpts 属主

`/dev/pts/N` 只对**拥有它的 uid** 可见；其他 app 看到的是 ENOENT。

设备语境：容器（droidspace 等）留下的 pty 属主是 root，Duck Detector 会报
`/dev/pts/0: Abnormal Permission Detected`；stock 设备上这些条目本来就不可读。

**例外：`/dev/pts/ptmx` 必须放行**。它是分配 pty 的多路复用器（字符设备 5:2），
stock 上属主就是 root；把它隐藏会导致应用无法创建 PTY：

```c
if (S_ISCHR(inode->i_mode) && inode->i_rdev == MKDEV(5, 2))
        return false;
```

## 3. `/dev` 下的外来挂载

对 app 的 mountinfo/vfsstat 隐藏同时满足以下三条的挂载：

1. 挂载点父目录的 dentry 名为 `dev`（即 `/dev/<x>`）；
2. 挂载点 inode 属主为 **root**；
3. 文件系统不在设备类白名单内：

```
devtmpfs devpts tmpfs mqueue binder binderfs cgroup cgroup2 selinuxfs
proc sysfs configfs bpf tracefs debugfs fuse fuseblk overlay
```

于是容器/root 守护进程挂进来的镜像（ext4/f2fs/erofs/iso9660…）从 app 视野消失，
而系统自身的 `/dev` 挂载一个不动。

设备验证（2026-11-17）：手工在 `/dev` 下 bind 三个挂载——`/data/local/tmp`（f2fs）、
`/data/adb`（f2fs）、`/system`（erofs）——app 视角全不可见，root 视角全可见；
其中挂载点属主是 `shell` 的那个按判据**不隐藏**，说明规则精确而不是粗暴屏蔽整个 `/dev`。

## 内核参数

`CONFIG_USER_NS=y` + `CONFIG_NAMESPACES=y`：容器与部分 root 工具需要。
构建步骤「启用 CONFIG_USER_NS (ABK)」把它们写进真实 defconfig **和** `/proc/config.gz`
伪装用的 `stock_defconfig`——两者不一致本身就是检测信号。

## 测量方法学（踩过的坑）

- `su 10311 -c ...` **不降权**：KSU 的 su 忽略 uid 参数（`id -u` 仍为 0），
  不能用来模拟 app；
- **ksu 域进程**即使 uid 是 app uid，也会被 susfs 的域门控排除，看不到第 2、3 条规则的效果；
- 可用 `work/abkview.c`（NDK 编译，`setuid()` 后无任何 capability）测 DAC + uid 门控类规则；
- 第 2、3 条的最终判定必须靠**真 app 进程**（Duck Detector 等）。

## 设备实测记录

| 时间 | 内核 | 结果 |
|---|---|---|
| 2026-11-17 | `ab10005064` | `policy: applied 2146, deferred 245, failed 0`；`/data/local/tmp` 可 stat、子项 ENOENT；`/dev/pts/ptmx` 可 stat；droidspace 运行时 Duck 通过 |
