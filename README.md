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

ABK 的目标是把手动 fork、启用 Actions、填写 GKI 参数、触发构建、下载产物和刷写安装这些步骤收敛到一个更顺手的流程里。

仓库侧提供 GitHub Actions 构建工作流；App 侧提供 Root 检查、GitHub 授权、fork 检查/同步、构建提交、进度通知、产物下载和刷写/安装入口。

## 快速入口

- 仓库主页：https://github.com/xingguangcuican6666/ABK
- Releases：https://github.com/xingguangcuican6666/ABK/releases
- Actions：https://github.com/xingguangcuican6666/ABK/actions
- Pages：https://xingguangcuican6666.github.io/ABK/
- ABK App CI：https://github.com/xingguangcuican6666/ABK/actions/workflows/build-abk-app.yml

## 支持范围

- Android 12 / 13 / 14 / 15 / 16 GKI 构建流程。
- KernelSU Official、KernelSU Next、SukiSU、ReSukiSU 构建分支。
- SUSFS、ZRAM、BBG、KPM、Re-Kernel、一加 8E 支持等可选功能。
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

## 风险提示

## 🧪 Droidspaces 容器支持（实验性）

> **实验性功能：** 不保证所有 GKI 版本均能成功构建或启动，刷入前请务必备份 Boot 镜像。
>
> **TIPS：** 工作流使用的是 [Droidspaces](https://github.com/ravindu644/Droidspaces-OSS) 的 [官方补丁](https://github.com/ravindu644/Droidspaces-OSS/tree/main/Documentation/resources/kernel-patches/GKI) ，如有更好的补丁可以提个issues，此外由于存在三个补丁，或许需要反复试验以确保其中一个适配你的机型，请根据他人或实际经验来选择。

[Droidspaces](https://github.com/ravindu644/Droidspaces-OSS) 是一个轻量级的 Linux 容器工具，可以在 Android 上运行完整的 Linux 环境（支持 systemd、OpenRC 等），用于搭建开发环境、运行服务器等场景。

**支持范围：** 5.10 / 5.15 / 6.1 / 6.6 / 6.12

**使用方式：** 在手动触发构建时，选择 `Droidspaces 容器支持` 选项：

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
- 一加 ColorOS 14 / 15 等设备兼容性仍需自行验证，异常情况下可能需要清除数据。
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
| `AVBTOOL` / `MKBOOTIMG` / `UNPACK_BOOTIMG` / `BOOT_SIGN_KEY_PATH` | 后续打包/签名工具路径。 |
| `CCACHE_DIR` | ccache 目录。 |

条件变量：

- `KSU_VERSION`：仅 KernelSU Official 分支会设置。
- `KBUILD_BUILD_TIMESTAMP`、`KBUILD_BUILD_VERSION`：只在 `before_build` 阶段可用，因为它们在“设置自定义构建时间”步骤后才写入环境。
- GitHub Actions 标准变量如 `GITHUB_REPOSITORY`、`GITHUB_REF`、`GITHUB_SHA`、`GITHUB_RUN_ID`、`RUNNER_OS`、`RUNNER_TEMP`、`HOME`、`PATH` 也可使用。

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

## 致谢

ABK 基于以下项目、仓库和社区工作继续开发。这里集中列出所有在 README、网页、工作流或产物说明中引用到的主要仓库和项目：

- 上游仓库：[zzh20188/GKI_KernelSU_SUSFS](https://github.com/zzh20188/GKI_KernelSU_SUSFS)
- KernelSU：[tiann/KernelSU](https://github.com/tiann/KernelSU)
- KernelSU Next：[KernelSU-Next/KernelSU-Next](https://github.com/KernelSU-Next/KernelSU-Next)
- SukiSU Ultra：[SukiSU-Ultra/SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra)
- ReSukiSU：[ReSukiSU/ReSukiSU](https://github.com/ReSukiSU/ReSukiSU)
- SUSFS：[simonpunk/susfs4ksu](https://gitlab.com/simonpunk/susfs4ksu)
- SUSFS GitHub 镜像/补丁来源：[ShirkNeko/susfs4ksu](https://github.com/ShirkNeko/susfs4ksu)
- SukiSU patch：[ShirkNeko/SukiSU_patch](https://github.com/ShirkNeko/SukiSU_patch)
- AnyKernel3：[WildKernels/AnyKernel3](https://github.com/WildKernels/AnyKernel3)
- Kernel patches：[WildKernels/kernel_patches](https://github.com/WildKernels/kernel_patches)
- Action-Build：[Numbersf/Action-Build](https://github.com/Numbersf/Action-Build)
- SUSFS 模块构建来源：[sidex15/susfs4ksu-module](https://github.com/sidex15/susfs4ksu-module)
- GCC prebuilts：[LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-gnu-6.4.1](https://github.com/LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-gnu-6.4.1)
- Baseband Guard：[vc-teahouse/Baseband-guard](https://github.com/vc-teahouse/Baseband-guard)
- Re-Kernel：[Sakion-Team/Re-Kernel](https://github.com/Sakion-Team/Re-Kernel)
- KernelSU 官方站点：https://kernelsu.org/

## License

本仓库包含多个第三方项目、补丁和构建产物引用。使用、分发或修改前请分别遵守对应上游项目的许可证和使用条款。使用 ABK、工作流、自定义模块或构建产物造成的设备损坏、数据丢失、账号风险、服务中断、合规问题或任何直接/间接损失，均由使用者自行承担。
