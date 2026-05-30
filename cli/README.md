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
abk --token "your_github_token" build --sub-level 66 --os-patch-level 2022-01
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

默认使用自定义构建 (kernel-custom.yml)，需指定子版本和补丁级别：

```bash
# 自定义构建 (默认)
abk build --sub-level 162 --os-patch-level 2026-03

# 指定 Android 和内核版本
abk build --android-version android14 --kernel-version 6.1 --sub-level 162 --os-patch-level 2026-03

# 矩阵构建 (所有子版本)
abk build --matrix a15

# OnePlus 构建
abk build --oneplus --device oneplus12

# 启用功能
abk build --sub-level 66 --os-patch-level 2022-01 --zram --kpm --virt 678
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

## 构建模式

| 选项 | 描述 |
|------|------|
| (默认) | 自定义构建 - 需 `--sub-level` 和 `--os-patch-level` |
| `--matrix a12-a16` | 矩阵构建 - 构建所有子版本 |
| `--oneplus` | OnePlus/Oplus 设备 - 需 `--device` |

## 内核版本参数

| 选项 | 说明 |
|------|------|
| `--android-version` | android12/13/14/15/16 (默认: android12) |
| `--kernel-version` | 5.10/5.15/6.1/6.6/6.12 (默认: 5.10) |
| `--sub-level` | 子版本号，如 66, 162 |
| `--os-patch-level` | 安全补丁级别，如 2022-01, 2026-03 |
| `--revision` | 修订版本，如 r11 (仅 5.10) |

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
| `ReSukiSU` | ReSukiSU (默认) |

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

# 自定义构建
abk build --sub-level 162 --os-patch-level 2026-03

# 指定版本构建
abk build --android-version android14 --kernel-version 6.1 --sub-level 162 --os-patch-level 2026-03 --zram --kpm

# 矩阵构建
abk build --matrix a15

# OnePlus 构建
abk build --oneplus --device oneplus12 --ksu SukiSU

# 查看构建进度
abk status

# 下载完成的构建
abk artifacts --run-id 12345 --download

# 同步 fork
abk sync
```
