# ABK CLI

用于非Android设备快速触发ABK内核编译的命令行工具。

A command-line tool to trigger ABK kernel builds from non-Android devices.

## 安装 / Installation

确保已安装Python 3.6+，然后将 `cli/` 目录添加到PATH：

```bash
export PATH="$HOME/ABK/cli:$PATH"
```

或创建符号链接：

```bash
sudo ln -s ~/ABK/cli/abk /usr/local/bin/abk
```

## 配置 / Configuration

### 登录 GitHub / Login to GitHub

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

## 使用方法 / Usage

### 账户管理 / Account Management

```bash
abk login                                # 登录 GitHub (Device Flow)
abk logout                               # 登出
abk whoami                               # 显示当前用户和 fork 状态
```

### Fork 管理 / Fork Management

```bash
abk fork                                 # 创建/检查 fork
abk sync                                 # 同步 fork 与上游
```

### 触发构建 / Trigger Build

#### 自定义构建 (默认) / Custom Build (Default)

需指定 `--sub-level` 和 `--os-patch-level`：

```bash
abk build --sub-level 162 --os-patch-level 2026-03
abk build --android-version android14 --kernel-version 6.1 --sub-level 162 --os-patch-level 2026-03
```

#### 预览构建计划 / Preview Build Plan

```bash
abk build --sub-level 162 --os-patch-level 2026-03 --dry-run
abk build --matrix both --ksu all --dry-run
```

#### 矩阵构建 / Matrix Build

```bash
abk build --matrix a15                   # 单个目标
abk build --matrix both                  # 全版本 (a12~a16)
```

#### 全量工作流 / Full Workflows

```bash
abk build --matrix full                  # 全属性内核构建矩阵
abk build --matrix all-managers          # 全管理器全矩阵编译
```

#### OnePlus 构建 / OnePlus Build

```bash
abk build --oneplus --device oneplus_12_b
```

**OnePlus 设备列表 / Device List：**

| 设备名 | 设备 | CPU | Android | 内核 |
|--------|------|-----|---------|------|
| `oneplus_13_b` | OnePlus 13 | sm8750 | 15 | 6.6 |
| `oneplus_12_b` | OnePlus 12 | sm8650 | 14 | 6.1 |
| `oneplus_11_b` | OnePlus 11 | sm8550 | 13 | 5.15 |
| `oneplus_10_pro_b` | OnePlus 10 Pro | sm8450 | 12 | 5.10 |
| ... | (30+ 设备，见 `abk list --oneplus`) | | | |

**OnePlus 专属功能：**

| 选项 | 描述 |
|------|------|
| `--lz4kd` | 启用 LZ4KD 压缩 |
| `--bbr` | 启用 BBR 拥塞控制 |
| `--proxy-optimization` | 启用代理优化 (MTK CPU 不支持) |
| `--unicode-bypass` | 启用 Unicode 零宽字符绕过修复 |

**OnePlus 构建限制：**
- ZRAM / DDK / NTsync / 网络增强 / Re-Kernel / 虚拟化 / 自定义外部模块 → 自动禁用
- MTK CPU 设备 → 代理优化自动禁用
- SUSFS 仅支持 android14/6.1 和 android15/6.6
- KPM 仅支持 SukiSU / ReSukiSU 变体

不兼容的选项会被自动禁用并给出警告。

#### 全 KSU 变体 / All KSU Variants

```bash
abk build --sub-level 162 --os-patch-level 2026-03 --ksu all
abk build --matrix both --ksu all        # 全版本 × 全 KSU
```

### 查看构建状态 / Check Build Status

```bash
abk status                               # 最近构建
abk status --run-id 12345                # 特定构建
abk status --status in_progress          # 按状态过滤
```

### 管理构建产物 / Manage Artifacts

```bash
abk artifacts --run-id 12345             # 列出产物
abk artifacts --run-id 12345 --download  # 下载
abk artifacts --run-id 12345 -o ./out    # 指定目录
```

### 列出可用选项 / List Options

```bash
abk list
```

## 构建模式 / Build Modes

| 选项 / Option | 描述 / Description |
|------|------|
| (默认) | 自定义构建 - 需 `--sub-level` 和 `--os-patch-level` |
| `--matrix a12~a16` | 矩阵构建 - 单个目标所有子版本 |
| `--matrix both` | 全版本矩阵 - 同时触发 a12~a16 |
| `--matrix full` | 全属性内核构建矩阵 |
| `--matrix all-managers` | 全管理器全矩阵编译 |
| `--oneplus` | OnePlus/Oplus 设备 |
| `--ksu all` | 全 KSU 变体 (Official + SukiSU + ReSukiSU) |

## 内核版本参数 / Kernel Version Options

| 选项 / Option | 说明 / Description |
|------|------|
| `--android-version` | android12/13/14/15/16 (默认: android12) |
| `--kernel-version` | 5.10/5.15/6.1/6.6/6.12 (默认: 5.10) |
| `--sub-level` | 子版本号，如 66, 162 |
| `--os-patch-level` | 安全补丁级别，如 2022-01, 2026-03 |
| `--revision` | 修订版本，如 r11 (仅 5.10) |

## 功能开关 / Feature Flags

| 选项 / Option | 默认值 / Default | 描述 / Description |
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

## KernelSU 选项 / KernelSU Options

| 变体 / Variant | 描述 / Description |
|------|------|
| `None` | 无 Root |
| `Official` | KernelSU 官方版 |
| `SukiSU` | SukiSU Ultra |
| `ReSukiSU` | ReSukiSU (默认) |
| `all` | 全部 (Official + SukiSU + ReSukiSU) |

| 分支 / Branch | 描述 / Description |
|------|------|
| `Stable` | 稳定版 (默认)，映射到 Stable(标准) |
| `Dev` | 开发版，映射到 Dev(开发) |
| `Custom` | 自定义引用，映射到 Custom(自定义) |

## 虚拟化支持 / Virtualization

| 选项 / Option | 描述 / Description |
|------|------|
| `off` | 关闭（默认） |
| `678` | 6_7_8 槽位补丁（推荐） |
| `123` | 1_2_3 槽位补丁（备用） |
| `345` | 3_4_5 槽位补丁（备用） |

## 示例 / Examples

```bash
# 登录并创建 fork
abk login
abk fork

# 自定义构建
abk build --sub-level 162 --os-patch-level 2026-03

# 全版本矩阵
abk build --matrix both

# 全属性内核构建矩阵
abk build --matrix full

# 全管理器全矩阵编译
abk build --matrix all-managers

# OnePlus 构建
abk build --oneplus --device oneplus12 --ksu SukiSU

# 全 KSU 变体
abk build --matrix both --ksu all

# 查看构建进度
abk status

# 下载产物
abk artifacts --run-id 12345 --download

# 同步 fork
abk sync
```

## 语言支持 / Language Support

使用 `--lang` 切换语言（持久化保存）：

```bash
abk --lang en-us --help    # English
abk --lang ja-jp --help    # 日本語
abk --lang neko --help     # 🐱
```

| Code | Language |
|------|----------|
| `zh-cn` | 中文 (默认) |
| `en-us` | English |
| `ru-ru` | Русский |
| `ja-jp` | 日本語 |
| `ko-kr` | 한국어 |
| `hi-in` | हिन्दी |
| `de-de` | Deutsch |
| `fr-fr` | Français |
| `es-es` | Español |
| `pt-br` | Português |
| `jp-neko` | 日本語猫娘 🐱 |
| `zh-neko` | 中文猫娘 🐱 |
| `eo` | Esperanto |
| `zh-zako` | zako~ zako~ |

## 添加新语言 / Adding New Languages

1. 在 `cli/i18n/` 目录下创建新的 JSON 文件，文件名使用语言代码（如 `fr-fr.json`）
2. 复制 `zh-cn.json` 的内容，将所有值翻译为目标语言
3. 更新 `cli/i18n/__init__.py`，将新语言代码添加到 `detect_language()` 函数的白名单
4. 更新 `cli/abk.py` 中以下位置：
   - `parser.add_argument("--lang", choices=[...])` 的 choices 列表
   - `main()` 函数中早期语言检测的 `sys.argv` 检查列表
5. 更新本 README 的语言支持表格

**注意：** KernelSU 分支名作为 API 参数时**不能翻译**，CLI 会自动将 `Stable`/`Dev`/`Custom` 映射为 `Stable(标准)`/`Dev(开发)`/`Custom(自定义)`。语言文件只需展示短名。其他 API 值同理（如设备名、KSU 变体名）。

## 语言维护 / Language Maintenance

当添加新的翻译键时：
1. 首先在 `zh-cn.json` 中添加
2. 同步到所有其他语言文件（保持键一致）
3. 使用以下命令验证 JSON 格式：
   ```bash
   for lang in cli/i18n/*.json; do python3 -c "import json; json.loads(open('$lang').read()); print('$lang: valid')"; done
   ```

## 许可证 / License

GPL-3.0
