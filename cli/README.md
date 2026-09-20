# ABK CLI

用于非Android设备快速触发ABK内核编译的命令行工具。

A command-line tool to trigger ABK kernel builds from non-Android devices.

## 安装 / Installation

确保已安装 Python 3.10+，然后直接运行脚本：

```bash
python3 ~/ABK/cli/abk.py --help
```

如需使用 `abk` 命令，可将可执行脚本链接到 `PATH` 中的目录：

```bash
mkdir -p "$HOME/.local/bin"
ln -sfn "$HOME/ABK/cli/abk.py" "$HOME/.local/bin/abk"
export PATH="$HOME/.local/bin:$PATH"
```

也可以创建系统级符号链接：

```bash
sudo ln -sfn "$HOME/ABK/cli/abk.py" /usr/local/bin/abk
```

产物签名与验证需要 `PyNaCl` 和一个 RSA 后端。推荐安装 `cryptography`；
CLI 也兼容 `pycryptodome` / `pycryptodomex`：

```bash
python3 -m pip install -r ~/ABK/cli/requirements.txt
# 或：python3 -m pip install pycryptodome PyNaCl certifi
```

## 配置 / Configuration

### 登录 GitHub / Login to GitHub

推荐使用 Device Flow 登录（类似 App）：

```bash
abk login
```

会自动打开浏览器进行授权。Linux 默认保存到 `~/.config/abk/config.json`
（遵循 `XDG_CONFIG_HOME`），Windows 保存到 `%APPDATA%\abk\config.json`。

首次构建会复用或初始化与 Android App 相同的 fork 签名 Secret、Release tag
和公钥资产；CLI 本地只按仓库保存公钥，不保存 RSA 私钥。

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

### CLI 版本 / CLI Version

```bash
abk --version
abk --json --version
```

全局 `abk --version` 显示 CLI 版本；现有的 `abk build --version VALUE`
仍用于设置自定义内核版本，两者语义不变。

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
abk build --sub-level 246 --os-patch-level 2025-12
abk build --android-version android14 --kernel-version 6.1 --sub-level 162 --os-patch-level 2026-03
```

#### 预览构建计划 / Preview Build Plan

```bash
abk build --sub-level 246 --os-patch-level 2025-12 --dry-run
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
abk build --oneplus --device oneplus_15
```

**OnePlus 设备列表 / Device List：**

| 设备名 | 设备 | CPU | Android | 内核 |
|--------|------|-----|---------|------|
| `oneplus_15` | OnePlus 15 | sm8850 | 16 | 6.12 |
| `oneplus_15t` | OnePlus 15T | sm8850 | 16 | 6.12 |
| `oneplus_13_b` | OnePlus 13 | sm8750 | 15 | 6.6 |
| `oneplus_12_b` | OnePlus 12 | sm8650 | 14 | 6.1 |
| `oneplus_11_b` | OnePlus 11 | sm8550 | 13 | 5.15 |
| `oneplus_10_pro_b` | OnePlus 10 Pro | sm8450 | 12 | 5.10 |
| ... | (30+ 设备，见 `abk list --oneplus`) | | | |

**OnePlus 专属功能：**

| 选项 | 描述 |
|------|------|
| `--lz4kd` / `--no-lz4kd` | LZ4KD 压缩 |
| `--bbr` / `--no-bbr` | BBR 拥塞控制 |
| `--proxy-optimization` / `--no-proxy-optimization` | 代理优化 (MTK CPU 不支持) |
| `--unicode-bypass` / `--no-unicode-bypass` | Unicode 零宽字符绕过修复 |

**OnePlus 构建限制：**
- ZRAM / DDK / NTsync / 网络增强 / Re-Kernel / 虚拟化 / 自定义外部模块 → 自动禁用
- MTK CPU 设备 → 代理优化自动禁用
- SUSFS 支持 android14/6.1、android15/6.6 和 android16/6.12
- android16/6.12 自动关闭不兼容的 legacy lz4kd
- KPM 仅对 SukiSU / ReSukiSU 生效；其他变体会自动禁用
- 全管理器 OnePlus 矩阵由工作流按每个 KernelSU 变体独立决定 KPM

不兼容的选项会被自动禁用并给出警告。

#### 全 KSU 变体 / All KSU Variants

```bash
abk build --sub-level 246 --os-patch-level 2025-12 --ksu all
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
abk artifacts --run-id 12345 --download  # 下载到当前用户的 Downloads 目录
abk artifacts --run-id 12345 --download --artifact-id 67890  # 仅下载指定产物
abk artifacts --run-id 12345 -o ./out    # 指定目录
abk artifacts --set-download-dir ./out   # 持久化默认目录
```

### 签名密钥管理 / Signing Key Management

`abk signing` 管理目标 fork 的产物签名密钥和 CLI 验证策略。未显式指定
`--repo owner/name` 时使用当前用户的 ABK fork：

```bash
abk signing status
abk signing import --public-key-file public.pem --private-key-file private.pem
abk signing rotate
abk signing enable
abk signing disable
abk --repo owner/name signing status
```

`import` 要求一对相互匹配的 RSA 密钥（至少 2048 位）：

- 公钥必须是 SPKI PEM，即以 `-----BEGIN PUBLIC KEY-----` 开头。
- 私钥必须是未加密的 PKCS#8 PEM，即以 `-----BEGIN PRIVATE KEY-----` 开头。
- CLI 只读取用户提供的私钥文件，不会把私钥写入本地配置；`rotate` 生成的私钥
  也只保留在操作期间。私钥只会上传到仓库的 Actions Secret
  `ABK_ARTIFACT_SIGNING_KEY_BASE64`。
- 公钥会发布为 `abk-artifact-key` Release 中的
  `abk-artifact-signing-public.pem` 资产，并按仓库缓存到本地配置。

`rotate` 生成并安装一对新密钥。`import` 更换现有密钥和 `rotate` 都会替换
远程 Secret 与公钥资产；旧产物包仍保留原签名，但无法再使用仓库当前发布的
公钥验证。`disable` 会先删除 Actions Secret 并确认删除成功，再删除公钥资产，
最后记录仓库级的本地禁用状态。远程签名材料保持不存在时，CLI 触发的后续
构建不再签名，下载产物时也会明确跳过验证；如果其他客户端重新创建了任一
远程签名项目，已禁用的 CLI 会将其视为状态冲突并拒绝构建或下载，而不会静默
执行未签名构建或跳过验证。`enable` 会重新启用验证，并在远程密钥不存在时
生成一对新密钥。

GitHub 的 Secret 与 Release asset API 不提供跨资源原子事务。CLI 会检测并发
修改并在无法确认状态时 fail closed，但密钥导入、轮换或禁用期间仍应保证只有
一个客户端在管理同一 fork 的签名材料。如果导入或轮换因并发修改或失败响应
而无法确定最终远程状态，CLI 会为该仓库持久化一个“状态不确定”安全锁。在成功执行
`signing import`、`signing rotate` 或 `signing disable` 完成修复前，该锁会
阻止后续 CLI 构建和产物下载。

修改操作可先用 `--dry-run` 检查，不会更改 GitHub 或本地状态。更换已有密钥
和禁用签名需要交互确认；自动化环境请显式传入 `--yes`（`--json` 模式不会
读取确认输入）：

```bash
abk signing rotate --dry-run
abk signing rotate --yes
abk signing disable --dry-run
abk signing disable --yes
```

CLI 与 Android App 使用相同的远程 Secret 和公钥资产，但启用/禁用偏好及
公钥缓存分别保存在各自客户端。Android App 在检查 fork 签名状态时会优先读取
远程公钥资产并刷新本地缓存，因此可以继续验证 CLI `import` 或 `rotate` 后的
新产物；仍不应在两端同时更换同一 fork 的密钥。通过 CLI 禁用后也应在 App 中
同步禁用，否则仍处于启用状态的 App 可能重新创建远程签名材料，并导致 CLI
报告状态冲突。

### 机器可读 JSON / Machine-readable JSON

自动化调用可使用全局前置参数 `--json`。命令执行时 stdout 始终只有一个
包含 `schemaVersion: 1` 和 `cliVersion` 的 JSON 文档；该模式不会读取 stdin
或打开浏览器。
`--help` 仍是供人阅读的文本，不属于 JSON 合同：

```bash
abk --json --version
abk --json whoami
abk --json status --limit 20
abk --json build --matrix a14 --ksu ReSukiSU --force
abk --json artifacts --run-id 12345 --download --artifact-id 67890
```

冻结产物可用 `abk --json self-test` 离线验证 RSA、PyNaCl 和 CA bundle。

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
| `--revision` | 修订版本，如 r11；仅 custom 5.10、full、all-managers 接收 |

自定义 LTS 构建使用固定组合 `--sub-level X --os-patch-level lts`；两项必须
同时指定。Android 与内核版本也必须使用上游支持的配对（android12/5.10 至
android16/6.12）。

为避免自由文本进入现有 Actions Shell，CLI 会在本地拒绝不安全参数：
除固定的 `X`/`lts` 组合外，`--sub-level` 必须为数字，补丁级别必须为
`YYYY-MM`；自定义 ref 必须符合 Git ref 规则。版本、构建时间、ZRAM 算法及
自定义模块参数也会做长度和字符校验。

## 功能开关 / Feature Flags

| 选项 / Option | 默认值 / Default | 描述 / Description |
|------|--------|------|
| `--zram` / `--no-zram` | 禁用 | ZRAM 增强算法 |
| `--bbg` / `--no-bbg` | 禁用 | BBG 防格机 |
| `--ddk` / `--no-ddk` | 禁用 | DDK 防格机 LSM |
| `--kpm` / `--no-kpm` | 禁用 | KPM 功能 |
| `--susfs` / `--no-susfs` | 启用 | SUSFS |
| `--rekernel` / `--no-rekernel` | 禁用 | Re-Kernel 驱动 |
| `--oneplus-8e` / `--no-oneplus-8e` | 禁用 | 一加 8E 支持 |
| `--ntsync` | 禁用 | NTsync |
| `--networking` | 禁用 | 网络增强 |
| `--zram-full-algo` / `--no-zram-full-algo` | 禁用 | ZRAM 完整算法支持 |

以上默认值适用于普通、自定义和单目标矩阵构建。`--matrix full` 与
`--matrix all-managers` 默认启用完整功能集，可用对应的 `--no-*` 选项关闭。

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
| `Latest` | 最新版，映射到 Latest(最新) |
| `Dev` | 开发版，映射到 Dev(开发) |
| `Custom` | 自定义引用，映射到 Custom(自定义) |

## 虚拟化支持 / Virtualization

| 选项 / Option | 描述 / Description |
|------|------|
| `off` | 关闭（默认） |
| `on` | 启用（旧内核映射到 678，6.12 映射到 on） |
| `678` | 6_7_8 槽位补丁（推荐） |
| `123` | 1_2_3 槽位补丁（备用） |
| `345` | 3_4_5 槽位补丁（备用） |

## 示例 / Examples

```bash
# 登录并创建 fork
abk login
abk fork

# 自定义构建
abk build --sub-level 246 --os-patch-level 2025-12

# 全版本矩阵
abk build --matrix both

# 全属性内核构建矩阵
abk build --matrix full

# 全管理器全矩阵编译
abk build --matrix all-managers

# OnePlus 构建
abk build --oneplus --device oneplus_12_b --ksu SukiSU

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
abk --lang en-US --help          # English
abk --lang ja-JP --help          # 日本語
abk --lang zh-CN-x-neko --help   # 中文猫娘 🐱
```

| Code | Language |
|------|----------|
| `zh-CN` | 中文 (默认) |
| `en-US` | English |
| `ru-RU` | Русский |
| `ja-JP` | 日本語 |
| `ko-KR` | 한국어 |
| `hi-IN` | हिन्दी |
| `de-DE` | Deutsch |
| `fr-FR` | Français |
| `es-ES` | Español |
| `pt-BR` | Português |
| `ja-JP-x-neko` | 日本語猫娘 🐱 |
| `zh-CN-x-neko` | 中文猫娘 🐱 |
| `eo` | Esperanto |
| `zh-CN-x-zako` | zako~ zako~ |

Language tags are matched case-insensitively. Existing lowercase values and the
legacy custom IDs `jp-neko`, `zh-neko`, and `zh-zako` remain accepted for
backward compatibility. The CLI exposes canonical tags, while its shared config
continues to store the older IDs so previous CLI/Desktop builds can still read it.

## 添加新语言 / Adding New Languages

1. 在 `cli/i18n/` 目录下创建新的 JSON 文件；`LANGUAGE_CATALOGS` 将规范语言标签映射到全小写的目录文件名（例如对外使用 `fr-FR`，文件名为 `fr-fr.json`）
2. 复制 `zh-cn.json` 的内容，将所有值翻译为目标语言
3. 更新 `cli/i18n/__init__.py` 中的 `LANGUAGE_CATALOGS`；如需迁移旧值，同时添加兼容别名
4. 更新本 README 的语言支持表格

**注意：** KernelSU 分支名作为 API 参数时**不能翻译**，CLI 会自动将
`Stable`/`Latest`/`Dev`/`Custom` 映射为
`Stable(标准)`/`Latest(最新)`/`Dev(开发)`/`Custom(自定义)`。语言文件只需展示
短名。其他 API 值同理（如设备名、KSU 变体名）。

## 语言维护 / Language Maintenance

当添加新的翻译键时：
1. 首先在 `zh-cn.json` 中添加
2. 同步到所有其他语言文件（保持键一致）
3. 使用以下命令验证 JSON 格式：
   ```bash
   for lang in cli/i18n/*.json; do python3 -c "import json; json.loads(open('$lang').read()); print('$lang: valid')"; done
   ```

## 测试 / Tests

从仓库根目录运行离线回归测试：

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s cli/tests -v
```

## 许可证 / License

GPL-3.0
