<div align="center">

# ABK

**AnyBase Kernel**

An automation repository and Android app for building, distributing, and managing GKI KernelSU / SUSFS kernels.

[![Release](https://img.shields.io/github/v/release/xingguangcuican6666/ABK?label=Release&style=flat-square&logo=github&logoColor=white&color=2ea44f)](https://github.com/xingguangcuican6666/ABK/releases)
[![ABK App](https://img.shields.io/github/actions/workflow/status/xingguangcuican6666/ABK/build-abk-app.yml?label=ABK%20App&style=flat-square&logo=android&logoColor=white)](https://github.com/xingguangcuican6666/ABK/actions/workflows/build-abk-app.yml)
[![KernelSU](https://img.shields.io/badge/KernelSU-Supported-5AA300?style=flat-square)](https://kernelsu.org/)
[![SUSFS](https://img.shields.io/badge/SUSFS-Integrated-E67E22?style=flat-square)](https://gitlab.com/simonpunk/susfs4ksu)

[简体中文](README.md) | English

</div>

## Purpose

ABK exists to turn the manual workflow of forking, enabling Actions, filling GKI parameters, starting builds, downloading artifacts, and flashing/installing outputs into a more direct process.

The repository provides GitHub Actions kernel build workflows. The Android app handles root checks, GitHub authorization, fork checks/sync, build dispatch, progress notifications, artifact downloads, and flashing/install entry points.

## Quick Links

- Repository: https://github.com/xingguangcuican6666/ABK
- Releases: https://github.com/xingguangcuican6666/ABK/releases
- Actions: https://github.com/xingguangcuican6666/ABK/actions
- Pages: https://xingguangcuican6666.github.io/ABK/
- ABK App CI: https://github.com/xingguangcuican6666/ABK/actions/workflows/build-abk-app.yml

## Scope

- Android 12 / 13 / 14 / 15 / 16 GKI build workflows.
- KernelSU Official, KernelSU Next, SukiSU, and ReSukiSU variants.
- Optional SUSFS, ZRAM, BBG, KPM, Re-Kernel, and OnePlus 8E support.
- Artifact handling for AnyKernel3 packages, kernel images, KernelSU managers, and SUSFS modules.

Actual compatibility depends on the device, kernel version, upstream branch state, and current patch compatibility.

## Usage

1. Fork this repository to your own GitHub account.
2. Open the Actions page in your fork and enable workflows once.
3. Use the ABK app to sign in to GitHub and authorize access.
4. Let the app check your fork and upstream sync state.
5. Confirm or adjust the recommended build parameters on the Build tab.
6. Dispatch the build and monitor progress from notifications or the app.
7. Download the required img, AnyKernel3 package, manager, or SUSFS module.
8. Flash or install only after confirming the risk.

You can also run the workflows manually from GitHub Actions.

## Risk Notice

## 🧪 Droidspaces Container Support (Experimental)

> **Experimental feature:** Successful build and boot is not guaranteed across all GKI versions. Always back up your boot image before flashing.
>
> **TIPS:** The workflow uses the [official Droidspaces patches](https://github.com/ravindu644/Droidspaces-OSS/tree/main/Documentation/resources/kernel-patches/GKI) from [Droidspaces](https://github.com/ravindu644/Droidspaces-OSS). If you have better patches, feel free to open an issue. Since there are three patch variants, you may need to test them repeatedly to find one that fits your device. Choose based on other users' feedback or your own experience.

[Droidspaces](https://github.com/ravindu644/Droidspaces-OSS) is a lightweight Linux containerization tool that lets you run full Linux environments (with systemd, OpenRC, etc.) on Android — useful for development, running servers, and more.

**Supported versions:** 5.10 / 5.15 / 6.1 / 6.6 / 6.12

**Usage:** When triggering a build manually, select the `Droidspaces` option:

| Option | Description |
|:---:|:---|
| `off` | Disabled (default) |
| `678` | Use 6_7_8 slot patch (recommended) |
| `123` | Use 1_2_3 slot patch (fallback) |
| `345` | Use 3_4_5 slot patch (fallback) |

> **Note:** Kernel 6.12 has only one patch — any non-off option will use it.

**If the build fails or bootloops after flashing:** Try switching to a different slot patch (e.g. 678 → 123 or 345). Different kernel sub-levels may require different patches.
- Flashing kernels is high-risk and may cause boot failure, data loss, or require restoring a stock boot image.
- Do not build or flash if you are unsure about the target partition, kernel version, Android version, or security patch level.
- OnePlus ColorOS 14 / 15 compatibility still needs device-side validation and may require data wiping in failure cases.
- If a build fails, first check whether SukiSU / SUSFS / ReSukiSU upstream branches have recently changed and are temporarily out of sync.
- Custom external modules execute `setup.sh` from third-party repository roots. Review the script and source before enabling it.
- ABK is intended only for devices and repositories you own or are explicitly authorized to use. Do not use it for unauthorized access, fraud, abuse, anti-risk bypassing, cheating, data theft, service disruption, or other illegal purposes.

## Custom Commit Pinning

[`config/config`](config/config) can pin SUSFS and SukiSU commits. This is useful when the latest upstream commit is temporarily broken and you need a known stable revision.

```ini
custom=true

gki-android12-5.10=
gki-android13-5.15=
gki-android14-6.1=
gki-android15-6.6=

sukisu=
```

An empty value means the latest commit of that branch will be used.

## Stock Config

To make `/proc/config.gz` in the built kernel closer to your stock kernel configuration, export the stock kernel config from your device, decompress it, rename it to `stock_defconfig`, and commit it under [`config/`](config/).

The build workflow auto-detects and applies this file. If the file is absent, the step is skipped.

## Custom External Module Development

Custom external modules let you insert additional repository logic into the ABK kernel workflow. The feature is disabled by default. When enabled from the app or GitHub Actions, the workflow clones each configured external repository and runs `setup.sh` from that repository root.

Workflow input format:

```text
https://github.com/user/module-a;after_patch|https://github.com/user/module-b;before_build
```

- Separate modules with `|`.
- Each module is written as `repo_url;stage`.
- Supported stages:
  - `after_patch`: runs after built-in source integrations such as SUSFS, ZRAM, BBG, DDK, and Re-Kernel.
  - `before_build`: runs after final kernel name and build-time configuration, immediately before compilation.
- The workflow clones modules to `$GITHUB_WORKSPACE/custom_external_module_XX-name`, next to `$KERNEL_ROOT`, `susfs4ksu`, and `kernel_patches`.
- `setup.sh` runs with the module repository root as the current working directory.
- Scripts can use standard GitHub Actions environment variables plus variables ABK writes to `$GITHUB_ENV` in earlier steps. GitHub Actions expressions such as `${{ inputs.xxx }}` are not expanded directly inside module scripts.

Common variables available in both stages:

| Variable | Meaning |
| --- | --- |
| `GITHUB_WORKSPACE` | Current Actions workspace and ABK repository root. |
| `CONFIG` | Build combo name, formatted as `android-version-kernel-version-sublevel`, for example `android14-6.1-162`. |
| `KERNEL_ROOT` | Synced kernel source directory, for example `$GITHUB_WORKSPACE/$CONFIG`. |
| `DEFCONFIG` | Current GKI defconfig path: `$KERNEL_ROOT/common/arch/arm64/configs/gki_defconfig`. |
| `ZZH_PATCHES` | ABK repository root, same as `$GITHUB_WORKSPACE`. |
| `SUSFS4KSU` | Expected SUSFS repository path; the directory is guaranteed only when SUSFS is enabled. |
| `KERNEL_PATCHES` | `WildKernels/kernel_patches` clone directory. |
| `SUKISU_PATCHES` | `ShirkNeko/SukiSU_patch` clone directory. |
| `ANYKERNEL3` | AnyKernel3 clone directory. |
| `ACTION_BUILD` | `Numbersf/Action-Build` clone directory. |
| `CUSTOM_EXTERNAL_MODULES_MANIFEST` | Parsed custom-module manifest TSV file. |
| `CUSTOM_EXTERNAL_MODULE_STAGE` | Current execution stage, either `after_patch` or `before_build`. |
| `REPO` | Android `repo` tool path. |
| `REMOTE_BRANCH` | Query result for the target `kernel/common` branch. |
| `ACTUAL_SUBLEVEL` | Actual sublevel extracted from the kernel `Makefile`. |
| `BRANCH` | KernelSU setup branch argument, for example `-s main`. |
| `KSU_LATEST_COMMIT_DATE` | Latest commit time of the current KernelSU tree; `未知` when unknown. |
| `SUSFS_LATEST_COMMIT_DATE` | Latest commit time of the current SUSFS tree; `禁用` when disabled. |
| `AVBTOOL` / `MKBOOTIMG` / `UNPACK_BOOTIMG` / `BOOT_SIGN_KEY_PATH` | Tool paths used later for packaging/signing. |
| `CCACHE_DIR` | ccache directory. |

Conditional variables:

- `KSU_VERSION`: set only for the KernelSU Official branch.
- `KBUILD_BUILD_TIMESTAMP` and `KBUILD_BUILD_VERSION`: available only in `before_build`, because they are written after the "set custom build time" step.
- Standard GitHub Actions variables such as `GITHUB_REPOSITORY`, `GITHUB_REF`, `GITHUB_SHA`, `GITHUB_RUN_ID`, `RUNNER_OS`, `RUNNER_TEMP`, `HOME`, and `PATH` are also available.

Minimal module layout:

```text
your-module/
└── setup.sh
```

Minimal `setup.sh` example:

```bash
#!/usr/bin/env bash
set -euo pipefail

echo "Running custom module from: $PWD"
echo "Kernel root: $KERNEL_ROOT"

# Example: append a defconfig option. Real modules should first verify that the target kernel supports it.
grep -q '^CONFIG_EXAMPLE_FEATURE=y$' "$DEFCONFIG" || echo 'CONFIG_EXAMPLE_FEATURE=y' >> "$DEFCONFIG"
```

Development guidance:

- Keep scripts idempotent: repeated execution should not duplicate config or corrupt the source tree.
- Fail explicitly: missing files, patch mismatches, or unsupported versions should `exit 1`.
- Keep edits scoped: prefer changing only `$KERNEL_ROOT`, `$DEFCONFIG`, or the module's own temporary files.
- Do not assume a fixed kernel version. Read `${CONFIG}` or `${KERNEL_ROOT}/common/Makefile` when needed.
- Do not include secrets, tokens, private data, or unauditable binary logic in module scripts.

## App

The ABK app follows a Material 3 Expressive design direction and targets an end-to-end mobile build flow:

- Check root permission on startup.
- Use GitHub Device Flow and ask the user to confirm authorization.
- Check whether the user has forked this repository and create the fork if needed.
- Check whether the fork is behind upstream and prompt for sync.
- Detect the current kernel version and prefill recommended build parameters.
- Dispatch GitHub Actions workflows and sync progress.
- Download artifacts after successful builds and provide flashing/install entry points.

The app is built by the [`Build ABK App`](.github/workflows/build-abk-app.yml) workflow.

## Acknowledgements

ABK continues development on top of the following projects, repositories, and community work. This section centralizes the major repositories and projects referenced by the README, website, workflows, or release notes:

- Upstream repository: [zzh20188/GKI_KernelSU_SUSFS](https://github.com/zzh20188/GKI_KernelSU_SUSFS)
- KernelSU: [tiann/KernelSU](https://github.com/tiann/KernelSU)
- KernelSU Next: [KernelSU-Next/KernelSU-Next](https://github.com/KernelSU-Next/KernelSU-Next)
- SukiSU Ultra: [SukiSU-Ultra/SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra)
- ReSukiSU: [ReSukiSU/ReSukiSU](https://github.com/ReSukiSU/ReSukiSU)
- SUSFS: [simonpunk/susfs4ksu](https://gitlab.com/simonpunk/susfs4ksu)
- SUSFS GitHub mirror / patch source: [ShirkNeko/susfs4ksu](https://github.com/ShirkNeko/susfs4ksu)
- SukiSU patch: [ShirkNeko/SukiSU_patch](https://github.com/ShirkNeko/SukiSU_patch)
- AnyKernel3: [WildKernels/AnyKernel3](https://github.com/WildKernels/AnyKernel3)
- Kernel patches: [WildKernels/kernel_patches](https://github.com/WildKernels/kernel_patches)
- Action-Build: [Numbersf/Action-Build](https://github.com/Numbersf/Action-Build)
- SUSFS module build source: [sidex15/susfs4ksu-module](https://github.com/sidex15/susfs4ksu-module)
- GCC prebuilts: [LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-gnu-6.4.1](https://github.com/LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-gnu-6.4.1)
- Baseband Guard: [vc-teahouse/Baseband-guard](https://github.com/vc-teahouse/Baseband-guard)
- Re-Kernel: [Sakion-Team/Re-Kernel](https://github.com/Sakion-Team/Re-Kernel)
- KernelSU website: https://kernelsu.org/

## License

This repository references multiple third-party projects, patches, and generated artifacts. Before using, redistributing, or modifying them, follow the license and terms of each upstream project. Users are responsible for any device damage, data loss, account risk, service interruption, compliance issue, or direct/indirect loss caused by using ABK, its workflows, custom modules, or generated artifacts.
