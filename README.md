<div align="center">

# ABK

**AnyBase Kernel**

用于构建、分发和管理 GKI KernelSU / SUSFS 内核的自动化仓库与 Android 应用。

[![Release](https://img.shields.io/github/v/release/xingguangcuican6666/ABK?label=Release&style=flat-square&logo=github&logoColor=white&color=2ea44f)](https://github.com/xingguangcuican6666/ABK/releases)
[![ABK App](https://img.shields.io/github/actions/workflow/status/xingguangcuican6666/ABK/build-abk-app.yml?label=ABK%20App&style=flat-square&logo=android&logoColor=white)](https://github.com/xingguangcuican6666/ABK/actions/workflows/build-abk-app.yml)
[![KernelSU](https://img.shields.io/badge/KernelSU-Supported-5AA300?style=flat-square)](https://kernelsu.org/)
[![SUSFS](https://img.shields.io/badge/SUSFS-Integrated-E67E22?style=flat-square)](https://gitlab.com/simonpunk/susfs4ksu)

简体中文 | [English](README-EN.md)

</div>

## 项目定位

ABK 的目标是把手动 fork、启用 Actions、填写 GKI 或 OnePlus/Oplus 参数、触发构建、下载产物和刷写安装这些步骤收敛到一个更顺手的流程里。

仓库侧提供 GitHub Actions 构建工作流；App 侧提供 Root 检查、GitHub 授权、fork 检查/同步、构建提交、进度通知、产物下载和刷写/安装入口。

## 快速入口

- 仓库主页：https://github.com/xingguangcuican6666/ABK
- Releases：https://github.com/xingguangcuican6666/ABK/releases
- Actions：https://github.com/xingguangcuican6666/ABK/actions
- Pages：https://xingguangcuican6666.github.io/ABK/
- ABK App CI：https://github.com/xingguangcuican6666/ABK/actions/workflows/build-abk-app.yml

## 支持范围

- Android 12 / 13 / 14 / 15 / 16 GKI 构建流程，以及 OnePlus/Oplus 机型构建流程。
- KernelSU Official、KernelSU Next、SukiSU、ReSukiSU 构建分支。
- SUSFS、ZRAM、BBG、KPM、Re-Kernel、lz4kd、BBR、代理优化、Unicode 绕过和一加 8E 支持等可选功能。
- AnyKernel3 包、kernel img、KernelSU 管理器和 SUSFS 模块产物整理。

实际可用性取决于目标设备、内核版本、上游分支状态和当前补丁兼容性。

## 使用方式

1. Fork 本仓库到自己的 GitHub 账号。
2. 首次进入 fork 仓库的 Actions 页面并启用工作流。
3. 使用 ABK App 登录 GitHub，授权后让 App 检查 fork 与上游同步状态。
4. 在 App 的“构建内核”页确认或调整设备推荐参数。
5. 提交构建后等待通知栏和 App 内进度更新。
6. 构建完成后下载需要的 img、AnyKernel3、管理器或 SUSFS 模块。
7. 在确认风险后按需刷写 boot 镜像或安装模块/APK。

也可以直接在 GitHub Actions 中手动运行对应工作流。

## OnePlus/Oplus 机型构建

App 的“构建内核”页可在 `GKI` 和 `OnePlus` 两种目标间切换。选择 `OnePlus` 后，App 会派发 [`oneplus-custom.yml`](.github/workflows/oneplus-custom.yml)，并通过 OnePlus/Oplus manifest 拉取对应 CPU 分支和机型 XML。
ABK 不再把 `_b/_v/_u/_t` 当作用户选择规则；App、工作流摘要和矩阵任务名会直接显示机型、ColorOS/OxygenOS 系统线、Android KMI 和 CPU，上游 XML 名称只保留为仓库初始化参数。

首版 OnePlus 构建支持 `android12/5.10`、`android13/5.15`、`android14/6.1`、`android15/6.6`，可选 KernelSU Official、KernelSU Next、SukiSU、ReSukiSU 或无 Root 内核。OnePlus 专用开关包括 SUSFS、KPM、lz4kd、BBG、BBR、代理优化和 Unicode 零宽绕过修复；SUSFS 仅在 `android14/6.1` 与 `android15/6.6` 生效，`android12/5.10` 和 `android13/5.15` 会自动关闭；MTK CPU 分支会强制关闭代理优化。

需要批量构建当前支持的全部 OnePlus/Oplus 机型时，可在 GitHub Actions 手动触发 [`oneplus-full-feature-matrix.yml`](.github/workflows/oneplus-full-feature-matrix.yml)。矩阵会读取上游 manifest，按 CPU 分支和 KMI 线生成构建任务。
如果要一次性触发 GKI 与 OnePlus 的全部管理器类型全矩阵编译，可使用 [`all-managers-full-feature-matrix.yml`](.github/workflows/all-managers-full-feature-matrix.yml)，并通过输入项控制是否包含某个变体、是否跑 GKI 或 OnePlus，以及常用构建自定义项。

## 风险提示

## 🧪 虚拟化支持（实验性）

> **实验性功能：** 不保证所有 GKI 版本均能成功构建或启动，刷入前请务必备份 Boot 镜像。
>
> **TIPS：** 工作流使用的是上游虚拟化补丁，如有更好的补丁可以提个 issues。此外由于存在三个补丁，或许需要反复试验以确保其中一个适配你的机型，请根据他人或实际经验来选择。

虚拟化支持会为内核启用 Linux 容器运行所需的 IPC、PID namespace、SysV IPC、POSIX mqueue 等能力，便于在 Android 上运行完整 Linux 环境、搭建开发环境或运行服务。

**支持范围：** 5.10 / 5.15 / 6.1 / 6.6 / 6.12

**使用方式：** 在手动触发构建时，选择 `虚拟化支持` 选项：

| 选项 | 说明 |
|:---:|:---|
| `off` | 关闭（默认） |
| `678` | 使用 6_7_8 槽位补丁（推荐） |
| `123` | 使用 1_2_3 槽位补丁（备用） |
| `345` | 使用 3_4_5 槽位补丁（备用） |

> **提示：** 6.12 内核仅有一个补丁，选择任意非关闭选项即可。

**如果构建失败或刷入后 bootloop：** 可尝试切换到其他槽位补丁（如 678 → 123 或 345），不同内核子版本可能适用不同的补丁。
- 刷写内核属于高风险操作，可能导致无法开机、数据损坏或需要恢复出厂 boot 镜像。
- 不建议在不确定设备分区、内核版本、Android 版本和安全补丁级别时强行构建或刷写。
- 一加 ColorOS/OxygenOS 13 / 14 / 15 / 16 等设备兼容性仍需自行验证，异常情况下可能需要清除数据。
- 如果构建失败，优先检查 SukiSU / SUSFS / ReSukiSU 等上游分支是否刚更新且尚未互相适配。
- 自定义外部模块会执行第三方仓库根目录的 `setup.sh`。启用前请审查脚本内容和来源可信度，避免执行未知或恶意代码。
- ABK 仅面向合法授权设备和合法研究/自用场景。禁止用于灰黑产、未授权访问、绕过风控、作弊、窃取数据、破坏服务或其他违法违规用途。

## 自定义提交固定

[`config/config`](config/config) 可用于固定 SUSFS 和 SukiSU 的 commit，适合在上游最新提交临时不可用时回退到稳定版本。

```ini
custom=true

gki-android12-5.10=
gki-android13-5.15=
gki-android14-6.1=
gki-android15-6.6=

sukisu=
```

留空表示使用对应分支的最新提交。

## KSU 分支 `Latest(最新)`（仅 GKI）

适用于所有 GKI 的 `workflow_dispatch` 工作流（[`kernel-custom.yml`](.github/workflows/kernel-custom.yml)、[`kernel-a12-5-10.yml`](.github/workflows/kernel-a12-5-10.yml)、[`kernel-a13-5-15.yml`](.github/workflows/kernel-a13-5-15.yml)、[`kernel-a14-6-1.yml`](.github/workflows/kernel-a14-6-1.yml)、[`kernel-a15-6-6.yml`](.github/workflows/kernel-a15-6-6.yml)、[`kernel-a16-6-12.yml`](.github/workflows/kernel-a16-6-12.yml) 及 [`kernel-full-feature-matrix.yml`](.github/workflows/kernel-full-feature-matrix.yml)）。App 派发的是 [`kernel-custom.yml`](.github/workflows/kernel-custom.yml)；在 GitHub 网页上也可对固定版本工作流手动选择 **Latest(最新)**。

在 GitHub Actions 与 App 的 GKI 构建界面中，**Latest(最新)** 位于 **Dev** 与 **Custom** 之间，由 [`resolve-ksu-ref.sh`](.github/scripts/resolve-ksu-ref.sh) 在运行时解析上游 KernelSU 来源：

- **Official / SukiSU / ReSukiSU（GKI）：** 内核与管理器共用上游 `main` 上最近一次成功的 `build-manager` 的 `head_sha`（非分支 HEAD）。管理器经 [nightly.link](https://nightly.link/) 拉取（`manager.zip` 或 `Manager-release.zip`）。若 `main` 上无成功的 `build-manager`，Latest 解析阶段直接失败。

若管理器下载失败，管理器 job 对应步骤会失败，但**内核构建仍会继续**。Latest 不会回退到 `releases/latest`（Stable/Dev 用的发布包路径）。

## Stock Config

如果需要让构建产物中的 `/proc/config.gz` 更接近官方内核配置，可以将设备官方内核导出的配置解压并命名为 `stock_defconfig`，提交到 [`config/`](config/) 目录。

构建流程会自动检测并应用该文件；不存在时会跳过，不需要额外开关。

## 自定义外部模块开发

自定义外部模块用于在 ABK 内置补丁流程之外插入额外仓库逻辑。该功能默认关闭；在 App 或 GitHub Actions 中启用后，工作流会按配置 clone 外部仓库并执行仓库根目录的 `setup.sh`。

工作流输入格式：

```text
https://github.com/user/module-a;after_patch|https://github.com/user/module-b;before_build
```

- 用 `|` 分隔多个模块。
- 每个模块用 `链接;阶段` 表示。
- 支持阶段：
  - `after_patch`：在内置补丁、ZRAM、BBG、DDK、Re-Kernel 等源码集成之后执行。
  - `before_build`：在内核名称、构建时间等最终配置之后，正式编译前执行。
- 工作流会将模块 clone 到 `$GITHUB_WORKSPACE/custom_external_module_XX-name`，与 `$KERNEL_ROOT`、`susfs4ksu`、`kernel_patches` 等目录同级。
- 执行 `setup.sh` 时，当前工作目录是模块仓库根目录。
- 脚本可使用 GitHub Actions 标准环境变量，以及 ABK 在前序步骤写入 `$GITHUB_ENV` 的变量。GitHub Actions 表达式（如 `${{ inputs.xxx }}`）不会在模块脚本中直接展开。

两个阶段都可用的常用变量：

| 变量 | 含义 |
| --- | --- |
| `GITHUB_WORKSPACE` | 当前 Actions 工作区，也是 ABK 仓库根目录。 |
| `CONFIG` | 构建组合名，格式为 `android版本-内核版本-子版本`，例如 `android14-6.1-162`。 |
| `KERNEL_ROOT` | 内核源码同步目录，例如 `$GITHUB_WORKSPACE/$CONFIG`。 |
| `DEFCONFIG` | 当前 GKI defconfig 路径：`$KERNEL_ROOT/common/arch/arm64/configs/gki_defconfig`。 |
| `ZZH_PATCHES` | ABK 仓库根目录，等同 `$GITHUB_WORKSPACE`。 |
| `SUSFS4KSU` | SUSFS 仓库预期路径；只有启用 SUSFS 时目录一定存在。 |
| `KERNEL_PATCHES` | `WildKernels/kernel_patches` 克隆目录。 |
| `SUKISU_PATCHES` | `ShirkNeko/SukiSU_patch` 克隆目录。 |
| `ANYKERNEL3` | AnyKernel3 克隆目录。 |
| `ACTION_BUILD` | `Numbersf/Action-Build` 克隆目录。 |
| `CUSTOM_EXTERNAL_MODULES_MANIFEST` | 已解析的自定义模块清单 TSV 文件。 |
| `CUSTOM_EXTERNAL_MODULE_STAGE` | 当前执行阶段，值为 `after_patch` 或 `before_build`。 |
| `REPO` | Android `repo` 工具路径。 |
| `REMOTE_BRANCH` | `kernel/common` 目标分支查询结果。 |
| `ACTUAL_SUBLEVEL` | 从内核 `Makefile` 提取到的实际子版本号。 |
| `BRANCH` | KernelSU setup 使用的分支参数，例如 `-s main`。 |
| `KSU_LATEST_COMMIT_DATE` | 当前 KernelSU 仓库最新提交时间；未知时为 `未知`。 |
| `SUSFS_LATEST_COMMIT_DATE` | 当前 SUSFS 仓库最新提交时间；禁用时为 `禁用`。 |
| `ABK_MANAGER_PACKAGE` | 内核信任的 ABK 管理器包名，默认来自 `app/signing/abk-manager-cert.env`。 |
| `ABK_MANAGER_CERT_SIZE` | 内核信任的 ABK 管理器签名证书 DER 大小。 |
| `ABK_MANAGER_CERT_SHA256` | 内核信任的 ABK 管理器签名证书 SHA-256。 |
| `AVBTOOL` / `MKBOOTIMG` / `UNPACK_BOOTIMG` / `BOOT_SIGN_KEY_PATH` | 后续打包/签名工具路径。 |
| `CCACHE_DIR` | ccache 目录。 |

条件变量：

- `KSU_VERSION`：仅 KernelSU Official 分支会设置。
- `KBUILD_BUILD_TIMESTAMP`、`KBUILD_BUILD_VERSION`：只在 `before_build` 阶段可用，因为它们在“设置自定义构建时间”步骤后才写入环境。
- GitHub Actions 标准变量如 `GITHUB_REPOSITORY`、`GITHUB_REF`、`GITHUB_SHA`、`GITHUB_RUN_ID`、`RUNNER_OS`、`RUNNER_TEMP`、`HOME`、`PATH` 也可使用。

ABK Control 管理器识别说明：

- 如果使用 `ABK_control_module` 让 ABK 直接作为 KernelSU / SukiSU / ReSukiSU 管理器，建议同时配置 `after_patch` 和 `before_build` 两个阶段。
- 手机上安装的 ABK APK 必须与内核构建日志中打印的 `ABK_MANAGER_PACKAGE` 和 `ABK_MANAGER_CERT_SHA256` 一致。默认 debug / 本地临时签名 APK 不会匹配仓库内的正式签名证书。
- 构建会在编译前校验 ABK Control 桥接标记和 `CONFIG_ABK_CONTROL=y`；失败时优先检查是否遗漏 `before_build` 阶段，或是否使用了与 APK 不一致的证书元数据。

最小模块结构：

```text
your-module/
└── setup.sh
```

最小 `setup.sh` 示例：

```bash
#!/usr/bin/env bash
set -euo pipefail

echo "Running custom module from: $PWD"
echo "Kernel root: $KERNEL_ROOT"

# 示例：向 defconfig 追加一个选项，实际模块应先确认目标内核支持该符号。
grep -q '^CONFIG_EXAMPLE_FEATURE=y$' "$DEFCONFIG" || echo 'CONFIG_EXAMPLE_FEATURE=y' >> "$DEFCONFIG"
```

开发建议：

- 保持脚本幂等：重复执行不应产生重复配置或破坏源码树。
- 明确失败：关键文件不存在、补丁未应用或版本不匹配时应直接 `exit 1`。
- 限定修改范围：优先只修改 `$KERNEL_ROOT`、`$DEFCONFIG` 或模块自己的临时目录。
- 不要假设固定内核版本：需要时读取 `${CONFIG}` 或 `${KERNEL_ROOT}/common/Makefile` 判断。
- 不要在脚本中提交密钥、token、隐私数据或不可审计的二进制逻辑。

## App

ABK App 使用 Material 3 Expressive 风格设计，面向手机端完成完整构建闭环：

- 启动后检查 Root 权限。
- 使用 GitHub Device Flow 登录并请求用户确认授权。
- 检查用户是否 fork 了本仓库，必要时创建 fork。
- 检查 fork 是否落后上游，并提示同步。
- 根据当前内核版本生成推荐构建参数。
- 触发 GitHub Actions 工作流并同步进度。
- 构建完成后下载产物并提供刷写/安装入口。

App 编译由 [`Build ABK App`](.github/workflows/build-abk-app.yml) 工作流完成。

### 内置 ksud

- APK 构建工作流会在构建时从 `SukiSU-Ultra/SukiSU-Ultra` 源码编译 `userspace/ksud`，并把生成的 `ksud` 二进制打包进 APK。
- 当前工作流默认打包 `arm64-v8a`、`armeabi-v7a`、`x86_64` 三个 ABI；运行时会优先使用 APK 内置 `ksud`，不可用时再回退到 `/data/adb/ksud` 或系统 `ksud`。
- 仓库不直接提交预编译 `ksud` 二进制；来源、构建方式和许可证说明见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。

### Self-hosted Runner（可选）

App 编译工作流（`Build ABK App` / `Build ABK App (dev)`）通过仓库变量 `APP_RUNNER` 选择 runner。**未设置时默认使用 GitHub 托管的 `ubuntu-latest`**，Fork 无需任何配置即可工作。需要在自己的服务器上构建时，请参考 [`docs/self-hosted-runner.md`](docs/self-hosted-runner.md)。

## 贡献者

以下列表按当前 git 历史统计，仅展示可识别的 GitHub 用户名/链接，并过滤自动化账号：

[@TheWildJames](https://github.com/TheWildJames)、[@zzh20188](https://github.com/zzh20188)、[@xingguangcuican6666](https://github.com/xingguangcuican6666)、[@ShirkNeko](https://github.com/ShirkNeko)、[@huime180](https://github.com/huime180)、[@MiRinChan](https://github.com/MiRinChan)、[@FunLay123](https://github.com/FunLay123)、[@guruji-byte](https://github.com/guruji-byte)、[@Xiaomichael](https://github.com/Xiaomichael)、[@DreamFerry](https://github.com/DreamFerry)、[@liqideqq](https://github.com/liqideqq)、[@elysias123](https://github.com/elysias123)、[@Fede2782](https://github.com/Fede2782)、[@ReeViiS69](https://github.com/ReeViiS69)、[@TheSillyOk](https://github.com/TheSillyOk)、[@prpjzz](https://github.com/prpjzz)、[@ukriu](https://github.com/ukriu)、[@wrnxr233](https://github.com/wrnxr233)、[@Tools-cx-app](https://github.com/Tools-cx-app)、[@Akuma-Noko](https://github.com/Akuma-Noko)、[@DebugBoard](https://github.com/DebugBoard)、[@FixeQyt](https://github.com/FixeQyt)、[@LX200944](https://github.com/LX200944)、[@Starsun](https://github.com/Starsun)、[@yx1234587](https://github.com/yx1234587)。

## 开放源代码许可

完整清单同步维护在 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)，App 的“开放源代码许可”页面也使用同一口径。许可证文本和额外义务以上游项目为准。

### 本仓库和内置代码

| 组件 | 来源 | 许可证 |
| --- | --- | --- |
| AnyBase Kernel | [`LICENSE`](LICENSE) | GPL-2.0 |
| ABK Control native bridge | `app/src/main/cpp/uapi/abk_control.h` | GPL-2.0 |
| xingguang DDK module | `ddk/xingguang-ddk/xingguang_ddk.c` | GPL |
| DDK kernel API patch | `ddk/patches/xingguang-ddk/0001-xingguang-ddk-api.patch` | GPL-2.0 |
| ZRAM LZ4 kernel glue | `zram/lz4/Makefile` | GPL-2.0-only |
| LZ4 sources and headers | `zram/lz4`, `zram/include/linux/lz4.h` | BSD-2-Clause |

### 上游项目与工作流引用

| 项目 | 地址 | 许可证 |
| --- | --- | --- |
| zzh20188/GKI_KernelSU_SUSFS | <https://github.com/zzh20188/GKI_KernelSU_SUSFS> | 上游仓库许可证 |
| WildKernels/GKI_KernelSU_SUSFS | <https://github.com/WildKernels/GKI_KernelSU_SUSFS> | 上游仓库许可证 |
| CodeLinaro CLO LA | <https://git.codelinaro.org/clo/la> | 顶层上游各项目许可证 |
| OnePlusOSS/kernel_manifest | <https://github.com/OnePlusOSS/kernel_manifest> | 上游仓库许可证 / 未检测到 SPDX |
| Xiaomichael/kernel_manifest | <https://github.com/Xiaomichael/kernel_manifest> | 上游仓库许可证 / 未检测到 SPDX |
| Xiaomichael/kernel_patches | <https://github.com/Xiaomichael/kernel_patches> | 上游仓库许可证 / 未检测到 SPDX |
| KernelSU | <https://github.com/tiann/KernelSU> | GPL-3.0 |
| KernelSU Next | <https://github.com/KernelSU-Next/KernelSU-Next> | GPL-3.0 |
| SukiSU Ultra | <https://github.com/SukiSU-Ultra/SukiSU-Ultra> | GPL-3.0 |
| ReSukiSU | <https://github.com/ReSukiSU/ReSukiSU> | GPL-3.0 |
| SUSFS | <https://gitlab.com/simonpunk/susfs4ksu> | GPL-2.0 |
| ShirkNeko/susfs4ksu | <https://github.com/ShirkNeko/susfs4ksu> | GPL-2.0 |
| SukiSU_patch | <https://github.com/ShirkNeko/SukiSU_patch> | GPL-2.0 |
| AnyKernel3 | <https://github.com/WildKernels/AnyKernel3> | GPL-2.0 |
| Xiaomichael/AnyKernel3 | <https://github.com/Xiaomichael/AnyKernel3> | 上游仓库许可证 / NOASSERTION |
| WildKernels/kernel_patches | <https://github.com/WildKernels/kernel_patches> | GPL-2.0 |
| cctv18/susfs4oki | <https://github.com/cctv18/susfs4oki> | GPL-3.0 |
| SukiSU_KernelPatch_patch | <https://github.com/SukiSU-Ultra/SukiSU_KernelPatch_patch> | 上游仓库许可证 |
| Action-Build | <https://github.com/Numbersf/Action-Build> | 上游仓库许可证 |
| SUSFS 模块构建来源 | <https://github.com/sidex15/susfs4ksu-module> | 上游仓库许可证 |
| GCC prebuilts | <https://github.com/LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-gnu-6.4.1> | GPL-family toolchain notices |
| Baseband Guard | <https://github.com/vc-teahouse/Baseband-guard> | 上游仓库许可证 |
| Re-Kernel | <https://github.com/Sakion-Team/Re-Kernel> | 上游仓库许可证 |
| Droidspaces / 虚拟化支持补丁来源 | <https://github.com/ravindu644/Droidspaces-OSS> | 上游仓库许可证 |
| ABK_repo 模块仓库 | <https://github.com/xingguangcuican6666/ABK_repo> | 上游仓库许可证 |
| AOSP kernel/common、manifest、mkbootimg、build-tools | <https://android.googlesource.com/> | GPL-2.0 / Apache-2.0 / AOSP notices |
| Android GKI certified boot images / command line tools | <https://dl.google.com/android/> | Android 分发条款 / Android SDK License |

### Android / Gradle 依赖

Android 依赖来自 `gradle/libs.versions.toml` 和 `app/build.gradle.kts`。当前环境中 Gradle native-platform 初始化失败，因此这里记录直接声明依赖；传递依赖以实际 Gradle 解析结果为准。

| 许可证 | 依赖 |
| --- | --- |
| Apache-2.0 | Android Gradle Plugin, Kotlin Gradle/Compose plugin, AndroidX Core/Lifecycle/Activity/Compose/Material3/Navigation/Work/DataStore/Test, Google Material Components, Retrofit, OkHttp, Gson, kotlinx-serialization-json, libsu, Coil |
| EPL-1.0 | JUnit 4.13.2 |

### Web npm 传递依赖

Web 依赖来自 `web/package-lock.json`。

| 许可证 | 包 |
| --- | --- |
| Apache-2.0 | `@webassemblyjs/leb128`, `@xtuc/long`, `baseline-browser-mapping`, `detect-libc` |
| BSD-2-Clause | `eslint-scope`, `esrecurse`, `estraverse`, `glob-to-regexp`, `terser` |
| BSD-3-Clause | `@xtuc/ieee754`, `fast-uri`, `flat`, `source-map`, `source-map-js` |
| CC-BY-4.0 | `caniuse-lite` |
| ISC | `electron-to-chromium`, `graceful-fs`, `icss-utils`, `isexe`, `picocolors`, `postcss-modules-extract-imports`, `postcss-modules-scope`, `postcss-modules-values`, `semver`, `which` |
| MIT | 其余 npm 传递依赖，包括 `webpack`, `webpack-cli`, `sass`, `sass-loader`, `css-loader`, `mini-css-extract-plugin`, `postcss`, `ajv`, `browserslist`, `chokidar`, `@jridgewell/*`, `@parcel/watcher*`, `@webassemblyjs/*` 的 MIT 包等；完整包名见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。 |

## License

ABK 本仓库按 GPL-2.0 发布。使用、分发或修改仓库中的第三方项目、补丁、二进制来源和依赖包前，请分别遵守对应上游项目的许可证和使用条款。使用 ABK、工作流、自定义模块或构建产物造成的设备损坏、数据丢失、账号风险、服务中断、合规问题或任何直接/间接损失，均由使用者自行承担。
