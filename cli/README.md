# ABK CLI

用于非Android设备快速触发ABK内核编译的命令行工具。

## 安装

确保已安装Python 3.6+，然后将 `cli/` 目录添加到PATH：

```bash
export PATH="$HOME/ABK/cli:$PATH"
```

或创建符号链接：

```bash
sudo ln -s ~/ABK/cli/abk /usr/local/bin/abk
```

## 配置

### 登录 GitHub

推荐使用 Device Flow 登录（类似 App）：

```bash
abk login
```

会自动打开浏览器进行授权，授权完成后 Token 会保存到 `~/.config/abk/config.json`。

其他认证方式：

```bash
# 环境变量
export GITHUB_TOKEN="your_github_token"

# 命令行参数
abk --token "your_github_token" build a15
```

查看登录状态：

```bash
abk whoami
```

登出：

```bash
abk logout
```

## 使用方法

### 账户管理

```bash
abk login                                # 登录 GitHub (Device Flow)
abk logout                               # 登出
abk whoami                               # 显示当前用户和 fork 状态
```

### Fork 管理

```bash
abk fork                                 # 创建/检查 fork
abk sync                                 # 同步 fork 与上游
```

### 触发构建

```bash
# 构建 Android 15 内核 (默认 ReSukiSU)
abk build a15

# 构建 Android 14 内核 (Official KernelSU)
abk build a14 --ksu Official

# 启用 ZRAM 和 KPM
abk build a15 --zram --kpm

# 禁用 SUSFS
abk build a15 --no-susfs

# 构建 OnePlus 设备内核
abk build oneplus --device <设备名> --ksu SukiSU

# 使用自定义KSU引用
abk build a15 --ksu Custom --custom-ref "branch:5"

# 启用虚拟化支持
abk build a15 --virt 678

# 启用所有功能
abk build a15 --zram --bbg --kpm --ntsync --networking
```

### 查看构建状态

```bash
# 查看最近构建
abk status

# 查看特定构建详情
abk status --run-id 12345

# 按状态过滤
abk status --status in_progress
```

### 管理构建产物

```bash
# 查看构建产物
abk artifacts --run-id 12345

# 下载构建产物
abk artifacts --run-id 12345 --download

# 下载到指定目录
abk artifacts --run-id 12345 --download -o ./output
```

### 列出可用选项

```bash
abk list
```

## 构建目标

| 目标 | 描述 |
|------|------|
| `a12` | Android 12 (5.10) |
| `a13` | Android 13 (5.15) |
| `a14` | Android 14 (6.1) |
| `a15` | Android 15 (6.6) |
| `a16` | Android 16 (6.12) |
| `oneplus` | OnePlus/Oplus 设备 |

## 功能开关

| 选项 | 默认值 | 描述 |
|------|--------|------|
| `--zram` / `--no-zram` | 禁用 | ZRAM 增强算法 |
| `--bbg` / `--no-bbg` | 禁用 | BBG 防格机 |
| `--ddk` / `--no-ddk` | 禁用 | DDK 防格机 LSM |
| `--kpm` / `--no-kpm` | 禁用 | KPM 功能 |
| `--susfs` / `--no-susfs` | 启用 | SUSFS |
| `--rekernel` / `--no-rekernel` | 禁用 | Re-Kernel 驱动 |
| `--oneplus-8e` | 禁用 | 一加 8E 支持 |
| `--ntsync` | 禁用 | NTsync |
| `--networking` | 禁用 | 网络增强 |
| `--zram-full-algo` | 禁用 | ZRAM 完整算法支持 |

## KernelSU 选项

| 变体 | 描述 |
|------|------|
| `None` | 无 Root |
| `Official` | KernelSU 官方版 |
| `SukiSU` | SukiSU Ultra |
| `ReSukiSU` | ReSukiSU |

| 分支 | 描述 |
|------|------|
| `Stable(标准)` | 稳定版 (默认) |
| `Dev(开发)` | 开发版 |
| `Custom(自定义)` | 自定义引用 |

## 虚拟化支持

| 选项 | 描述 |
|------|------|
| `off` | 关闭（默认） |
| `678` | 使用 6_7_8 槽位补丁（推荐） |
| `123` | 使用 1_2_3 槽位补丁（备用） |
| `345` | 使用 3_4_5 槽位补丁（备用） |

## 示例

```bash
# 登录并创建 fork
abk login
abk fork

# 快速构建 Android 15 内核
abk build a15

# 构建带 ZRAM 和 KPM 的内核
abk build a15 --zram --kpm

# 构建精简版内核
abk build a15 --no-susfs

# 构建 OnePlus 设备内核
abk build oneplus --device oneplus9 --ksu SukiSU --virt 678

# 查看构建进度
abk status

# 下载完成的构建
abk artifacts --run-id 12345 --download

# 同步 fork
abk sync
```
