<div align="center">

# MekeABK
> 仓库地址：https://github.com/Mocheng778/MekeABK
> 本仓库为ABK衍生分支，**仅个人研究自用、不对外分发**，仅增加 ApkeSU 内核变体与管理器适配。
> 如果你们不小心进入了我仓库链接，原版项目请访问原作者仓库下载，希望你们理解一下。

**AnyBase Kernel**

用于构建、分发和管理 GKI KernelSU / SUSFS 内核的自动化仓库与 Android 应用。

[![Release](https://img.shields.io/github/v/release/xingguangcuican6666/ABK?label=Release&style=flat-square&logo=github&logoColor=white&color=2ea44f)](https://github.com/xingguangcuican6666/ABK/releases)
[![ABK App](https://img.shields.io/github/actions/workflow/status/xingguangcuican6666/ABK/build-abk-app.yml?label=ABK%20App&style=flat-square&logo=android&logoColor=white)](https://github.com/xingguangcuican6666/ABK/actions/workflows/build-abk-app.yml)
[![KernelSU](https://img.shields.io/badge/KernelSU-Supported-5AA300?style=flat-square)](https://kernelsu.org/)
[![SUSFS](https://img.shields.io/badge/SUSFS-Integrated-E67E22?style=flat-square)](https://gitlab.com/simonpunk/susfs4ksu)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/xingguangcuican6666/ABK)

简体中文 | [English](README-EN.md)

</div>

## 支持我的工作（原作者）

如果你喜欢这个项目，欢迎在 Ko‑fi 上为原作者点一杯咖啡喵

[![Ko‑fi](https://img.shields.io/badge/Ko--fi-F16061?style=for‑the‑badge&logo=ko‑fi&logoColor=white)](https://ko‑fi.com/xingguangcuican)

## 项目定位

ABK 的目标是把手动 fork、启用 Actions、填写 GKI 或 OnePlus/Oplus 参数、触发构建、下载产物和刷写安装这些步骤收敛到一个更顺手的流程里。

仓库侧提供 GitHub Actions 构建工作流；App 侧提供 Root 检查、GitHub 授权、fork 检查/同步、构建提交、进度通知、产物下载和刷写/安装入口。

> 🔖本 MekeABK 分支新增：**ApkeSU 变体适配，支持 GKI、SUSFS、KPM 构建，来源：https://github.com/fixz232/ApkeSU**

## 快速入口

- 上游原版仓库主页：https://github.com/xingguangcuican6666/ABK
- 本衍生分支仓库：https://github.com/Mocheng778/MekeABK
- Releases：https://github.com/xingguangcuican6666/ABK/releases
- Actions：https://github.com/xingguangcuican6666/ABK/actions
- Pages：https://xingguangcuican6666.github.io/ABK/
- ABK App CI：https://github.com/xingguangcuican6666/ABK/actions/workflows/build-abk-app.yml

## 支持范围

- Android 12 / 13 / 14 / 15 / 16 GKI 构建流程，以及 OnePlus/Oplus 机型构建流程。
- KernelSU Official、KernelSU Next、SukiSU、ReSukiSU、**ApkeSU（本分支新增）** 构建分支。
- SUSFS、ZRAM、BBG、**KPM（Kernel Patch Modules）**、Re‑Kernel、lz4kd、BBR、代理优化、Unicode 绕过和一加 8E 支持等可选功能。
- AnyKernel3 包、kernel img、KernelSU 管理器和 SUSFS 模块产物整理。

> 📜组件说明声明
> 1. **KPM**：内核补丁模块，用于在内核运行时加载内核模块，不同SU变体兼容性存在差异；
> 2. **SUSFS / susfs4ksu**：内核级root隐藏补丁项目，项目地址：https://gitlab.com/simonpunk/susfs4ksu，提供内核补丁与用户态工具；
> 3. **susfs4oki**：面向OnePlus/Oplus设备的SUSFS适配补丁；
> 4. **AnyKernel3**：通用安卓内核刷写打包工具，用于打包boot镜像实现无刷机分区刷写内核。

实际可用性取决于目标设备、内核版本、上游分支状态和当前补丁兼容性。

## 使用方式

1. 使用本仓库对应 `forABK` 分支。
2. 首次进入 fork 仓库的 Actions 页面并启用工作流。
3. 使用修改后的ABK App登录GitHub，指向本fork仓库，分支选择`forABK`。
4. 在 App 的“构建内核”页确认或调整设备推荐参数，变体可选择`ApkeSU`。
5. 提交构建后等待通知栏和 App 内进度更新。
6. 构建完成后下载需要的 img、AnyKernel3、管理器或 SUSFS 模块。
7. 在确认风险后按需刷写 boot 镜像或安装模块/APK。

也可以直接在 GitHub Actions 中手动运行对应工作流。

## OnePlus/Oplus 机型构建

App 的“构建内核”页可在 `GKI` 和 `OnePlus` 两种目标间切换。选择 `OnePlus` 后，App 会派发 [`oneplus‑custom.yml`](.github/workflows/oneplus‑custom.yml)，并通过 OnePlus/Oplus manifest 拉取对应 CPU 分支和机型 XML。
ABK 不再把 `_b/_v/_u/_t` 当作用户选择规则；App、工作流摘要和矩阵任务名会直接显示机型、ColorOS/OxygenOS 系统线、Android KMI 和 CPU，上游 XML 名称只保留为仓库初始化参数。

OnePlus 构建支持 `android12/5.10`、`android13/5.15`、`android14/6.1`、`android15/6.6`、`android16/6.12`，其中 OnePlus 15/15T 使用 `sm8850` 的 `android16/6.12` manifest。可选 KernelSU Official、SukiSU、ReSukiSU、ApkeSU 或无 Root 内核。OnePlus 专用开关包括 SUSFS、KPM、lz4kd、BBG、BBR、代理优化和 Unicode 零宽绕过修复；SUSFS 在 `android14/6.1`、`android15/6.6` 与 `android16/6.12` 生效，6.12 会自动关闭不兼容的 legacy lz4kd，MTK CPU 分支会强制关闭代理优化。

需要批量构建当前支持的全部 OnePlus/Oplus 机型时，可在 GitHub Actions 手动触发 [`oneplus‑full‑feature‑matrix.yml`](.github/workflows/oneplus‑full‑feature‑matrix.yml)。矩阵会读取上游 manifest，按 CPU 分支和 KMI 线生成构建任务。
如果要一次性触发 GKI 与 OnePlus 的全部管理器类型全矩阵编译，可使用 [`all‑managers‑full‑feature‑matrix.yml`](.github/workflows/all‑managers‑full‑feature‑matrix.yml)，并通过输入项控制是否包含某个变体、是否跑 GKI 或 OnePlus，以及常用构建自定义项。

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
- 如果构建失败，优先检查 SukiSU / SUSFS / ReSukiSU / ApkeSU 等上游分支是否刚更新且尚未互相适配。
- 自定义外部模块会执行第三方仓库根目录的 `setup.sh`。启用前请审查脚本内容和来源可信度，避免执行未知或恶意代码。
- ABK 仅面向合法授权设备和合法研究/自用场景。禁止用于灰黑产、未授权访问、绕过风控、作弊、窃取数据、破坏服务或其他违法违规用途。

## 自定义提交固定

[`config/config`](config/config) 可用于固定 SUSFS 和 SukiSU 的 commit，适合在上游最新提交临时不可用时回退到稳定版本。

```ini
custom=true

gki‑android12‑5.10=
gki‑android13‑5.15=
gki‑android14‑6.1=
gki‑android15‑6.6=

sukisu=
# apkesu= 可在这里固定ApkeSU上游commit
 [`docs/self-hosted-runner.md`](docs/self-hosted-runner.md)。

## 贡献者

以下列表按当前 git 历史归一化到可识别的 GitHub 用户名/链接，并按用户名排序；自动化账号与无法可靠映射的身份已过滤：

[@Akuma-Noko](https://github.com/Akuma-Noko)、[@DebugBoard](https://github.com/DebugBoard)、[@DreamFerry](https://github.com/DreamFerry)、[@elysias123](https://github.com/elysias123)、[@fanziyun](https://github.com/fanziyun)、[@Fede2782](https://github.com/Fede2782)、[@FixeQyt](https://github.com/FixeQyt)、[@FunLay123](https://github.com/FunLay123)、[@gsf114](https://github.com/gsf114)、[@guruji-byte](https://github.com/guruji-byte)、[@huime180](https://github.com/huime180)、[@liqideqq](https://github.com/liqideqq)、[@LX200944](https://github.com/LX200944)、[@Mazha0309](https://github.com/Mazha0309)、[@MiRinChan](https://github.com/MiRinChan)、[@prpjzz](https://github.com/prpjzz)、[@ReeViiS69](https://github.com/ReeViiS69)、[@ShirkNeko](https://github.com/ShirkNeko)、[@Starsun](https://github.com/Starsun)、[@TheSillyOk](https://github.com/TheSillyOk)、[@TheWildJames](https://github.com/TheWildJames)、[@Tools-cx-app](https://github.com/Tools-cx-app)、[@ukriu](https://github.com/ukriu)、[@wrnxr233](https://github.com/wrnxr233)、[@Xiaomichael](https://github.com/Xiaomichael)、[@xingguangcuican6666](https://github.com/xingguangcuican6666)、[@yx1234587](https://github.com/yx1234587)、[@zzh20188](https://github.com/zzh20188)。

## 开放源代码许可

完整清单同步维护在 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)，App 的“开放源代码许可”页面也使用同一口径。许可证文本和额外义务以上游项目为准。

### 本仓库和内置代码

| 组件 | 来源 | 许可证 |
| --- | --- | --- |
| AnyBase Kernel | [`LICENSE`](LICENSE) | GPL-3.0 |
| ABK Control native bridge | `app/src/main/cpp/uapi/abk_control.h` | GPL-2.0 |
| xingguang DDK module | `ddk/xingguang-ddk/xingguang_ddk.c` | GPL |
| DDK kernel API patch | `ddk/patches/xingguang-ddk/0001-xingguang-ddk-api.patch` | GPL-2.0 |
| ZRAM LZ4 kernel glue | `zram/lz4/Makefile` | GPL-2.0-only |
| LZ4 sources and headers | `zram/lz4`, `zram/include/linux/lz4.h` | BSD-2-Clause |

### 上游项目与工作流引用

| 项目 | 地址 | 许可证 |
| --- | --- | --- |
| zzh20188/GKI_KernelSU_SUSFS | <https://github.com/zzh20188/GKI_KernelSU_SUSFS> | GPL-2.0 |
| WildKernels/GKI_KernelSU_SUSFS | <https://github.com/WildKernels/GKI_KernelSU_SUSFS> | 上游仓库许可证 / 未检测到 SPDX |
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
| Xiaomichael/AnyKernel3 | <https://github.com/Xiaomichael/AnyKernel3> | [Custom License](https://github.com/Xiaomichael/AnyKernel3/blob/master/LICENSE) |
| WildKernels/kernel_patches | <https://github.com/WildKernels/kernel_patches> | GPL-2.0 |
| cctv18/susfs4oki | <https://github.com/cctv18/susfs4oki> | GPL-3.0 |
| SukiSU_KernelPatch_patch | <https://github.com/SukiSU-Ultra/SukiSU_KernelPatch_patch> | GPL-2.0 |
| Action-Build | <https://github.com/Numbersf/Action-Build> | [Custom License](https://github.com/Numbersf/Action-Build/blob/SukiSU-Ultra/LICENSE) |
| SUSFS 模块构建来源 | <https://github.com/sidex15/susfs4ksu-module> | AGPL-3.0 |
| GCC prebuilts | <https://github.com/LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-gnu-6.4.1> | GPL-family toolchain notices |
| Baseband Guard | <https://github.com/vc-teahouse/Baseband-guard> | GPL-2.0 |
| Re-Kernel | <https://github.com/Sakion-Team/Re-Kernel> | GPL-2.0 |
| Droidspaces / 虚拟化支持补丁来源 | <https://github.com/ravindu644/Droidspaces-OSS> | GPL-3.0 |
| ABK_repo 模块仓库 | <https://github.com/xingguangcuican6666/ABK_repo> | GPL-3.0 |
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

ABK 本仓库按 GPL-3.0 发布。使用、分发或修改仓库中的第三方项目、补丁、二进制来源和依赖包前，请分别遵守对应上游项目的许可证和使用条款。使用 ABK、工作流、自定义模块或构建产物造成的设备损坏、数据丢失、账号风险、服务中断、合规问题或任何直接/间接损失，均由使用者自行承担。
