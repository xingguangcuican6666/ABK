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

ABK exists to turn the manual workflow of forking, enabling Actions, filling GKI or OnePlus/Oplus parameters, starting builds, downloading artifacts, and flashing/installing outputs into a more direct process.

The repository provides GitHub Actions kernel build workflows. The Android app handles root checks, GitHub authorization, fork checks/sync, build dispatch, progress notifications, artifact downloads, and flashing/install entry points.

## Quick Links

- Repository: https://github.com/xingguangcuican6666/ABK
- Releases: https://github.com/xingguangcuican6666/ABK/releases
- Actions: https://github.com/xingguangcuican6666/ABK/actions
- Pages: https://xingguangcuican6666.github.io/ABK/
- ABK App CI: https://github.com/xingguangcuican6666/ABK/actions/workflows/build-abk-app.yml

## Scope

- Android 12 / 13 / 14 / 15 / 16 GKI build workflows, plus OnePlus/Oplus device build workflows.
- KernelSU Official, KernelSU Next, SukiSU, and ReSukiSU variants.
- Optional SUSFS, ZRAM, BBG, KPM, Re-Kernel, lz4kd, BBR, proxy optimization, Unicode bypass, and OnePlus 8E support.
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

## OnePlus/Oplus Device Builds

The app's Build tab can switch between `GKI` and `OnePlus` targets. Selecting `OnePlus` dispatches [`oneplus-custom.yml`](.github/workflows/oneplus-custom.yml), which syncs the selected CPU branch and device XML from the OnePlus/Oplus manifest.
ABK no longer uses `_b/_v/_u/_t` as the user-facing selection rule; the app, workflow summaries, and matrix job names show the device, ColorOS/OxygenOS system line, Android KMI, and CPU directly, while the upstream XML name stays only as a repo-init parameter.

The first OnePlus build target supports `android12/5.10`, `android13/5.15`, `android14/6.1`, and `android15/6.6`, with KernelSU Official, KernelSU Next, SukiSU, ReSukiSU, or rootless builds. OnePlus-specific switches include SUSFS, KPM, lz4kd, BBG, BBR, proxy optimization, and the Unicode zero-width bypass fix; SUSFS only applies to `android14/6.1` and `android15/6.6`, while `android12/5.10` and `android13/5.15` disable it automatically; MTK CPU branches force proxy optimization off.

To batch-build every currently supported OnePlus/Oplus device, manually run [`oneplus-full-feature-matrix.yml`](.github/workflows/oneplus-full-feature-matrix.yml) from GitHub Actions. The matrix reads the upstream manifest and generates jobs by CPU branch and KMI line.
To trigger a full matrix across all manager variants for both GKI and OnePlus in one place, use [`all-managers-full-feature-matrix.yml`](.github/workflows/all-managers-full-feature-matrix.yml). Its inputs let you choose which variants to include, whether to run GKI or OnePlus, and the common build customizations.

## Risk Notice

## 🧪 Virtualization Support (Experimental)

> **Experimental feature:** Successful build and boot is not guaranteed across all GKI versions. Always back up your boot image before flashing.
>
> **TIPS:** The workflow uses upstream virtualization patches. If you have better patches, feel free to open an issue. Since there are three patch variants, you may need to test them repeatedly to find one that fits your device. Choose based on other users' feedback or your own experience.

Virtualization support enables the kernel features needed by Linux container environments, including IPC and PID namespaces, SysV IPC, and POSIX mqueue, so Android devices can run full Linux environments for development or services.

**Supported versions:** 5.10 / 5.15 / 6.1 / 6.6 / 6.12

**Usage:** When triggering a build manually, select the `Virtualization Support` option:

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
- OnePlus ColorOS/OxygenOS 13 / 14 / 15 / 16 compatibility still needs device-side validation and may require data wiping in failure cases.
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

## KSU branch `Latest(最新)` (GKI only)

Applies to every GKI `workflow_dispatch` workflow ([`kernel-custom.yml`](.github/workflows/kernel-custom.yml), [`kernel-a12-5-10.yml`](.github/workflows/kernel-a12-5-10.yml), [`kernel-a13-5-15.yml`](.github/workflows/kernel-a13-5-15.yml), [`kernel-a14-6-1.yml`](.github/workflows/kernel-a14-6-1.yml), [`kernel-a15-6-6.yml`](.github/workflows/kernel-a15-6-6.yml), [`kernel-a16-6-12.yml`](.github/workflows/kernel-a16-6-12.yml), and [`kernel-full-feature-matrix.yml`](.github/workflows/kernel-full-feature-matrix.yml)). The app dispatches [`kernel-custom.yml`](.github/workflows/kernel-custom.yml); on github.com you can also pick **Latest(最新)** when manually running a fixed-version workflow.

On GitHub Actions and the app GKI build screen, **Latest(最新)** sits between **Dev** and **Custom**. [`resolve-ksu-ref.sh`](.github/scripts/resolve-ksu-ref.sh) resolves upstream KernelSU sources at run time:

- **Official / SukiSU / ReSukiSU (GKI):** kernel and manager share the `head_sha` of the latest successful upstream `build-manager` run on `main` (not raw branch HEAD). Manager APK via [nightly.link](https://nightly.link/) (`manager.zip` or `Manager-release.zip`). If there is no green `build-manager` on `main`, Latest resolution fails early.

If manager download fails, the manager job step fails but **the kernel build continues**. Latest does not fall back to `releases/latest` (the Stable/Dev release path).

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
| `ABK_MANAGER_PACKAGE` | Trusted ABK manager package name, loaded from `app/signing/abk-manager-cert.env` by default. |
| `ABK_MANAGER_CERT_SIZE` | Trusted ABK manager signing certificate DER size. |
| `ABK_MANAGER_CERT_SHA256` | Trusted ABK manager signing certificate SHA-256. |
| `AVBTOOL` / `MKBOOTIMG` / `UNPACK_BOOTIMG` / `BOOT_SIGN_KEY_PATH` | Tool paths used later for packaging/signing. |
| `CCACHE_DIR` | ccache directory. |

Conditional variables:

- `KSU_VERSION`: set only for the KernelSU Official branch.
- `KBUILD_BUILD_TIMESTAMP` and `KBUILD_BUILD_VERSION`: available only in `before_build`, because they are written after the "set custom build time" step.
- Standard GitHub Actions variables such as `GITHUB_REPOSITORY`, `GITHUB_REF`, `GITHUB_SHA`, `GITHUB_RUN_ID`, `RUNNER_OS`, `RUNNER_TEMP`, `HOME`, and `PATH` are also available.

ABK Control manager recognition notes:

- When using `ABK_control_module` to make ABK work directly as a KernelSU / SukiSU / ReSukiSU manager, configure both `after_patch` and `before_build`.
- The ABK APK installed on the phone must match the `ABK_MANAGER_PACKAGE` and `ABK_MANAGER_CERT_SHA256` printed in the kernel build log. Default debug or locally ad-hoc signed APKs do not match the checked-in release certificate metadata.
- The kernel build validates the ABK Control bridge markers and `CONFIG_ABK_CONTROL=y` before compiling. If it fails, first check for a missing `before_build` stage or certificate metadata that does not match the installed APK.

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

### Bundled ksud

- The APK build workflow compiles `userspace/ksud` from `SukiSU-Ultra/SukiSU-Ultra` during the app build and packages the resulting `ksud` binaries into the APK.
- The workflow currently bundles `arm64-v8a`, `armeabi-v7a`, and `x86_64`; at runtime ABK prefers the APK-bundled `ksud` and falls back to `/data/adb/ksud` or a system `ksud` only when needed.
- This repository does not check in prebuilt `ksud` binaries. Source, build provenance, and license notes are documented in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

### Self-hosted runner (optional)

The app build workflows (`Build ABK App` / `Build ABK App (dev)`) pick their runner from the repository variable `APP_RUNNER`. **When unset, both workflows default to the GitHub-hosted `ubuntu-latest`**, so a fork works with no configuration. To build on your own hardware, see [`docs/self-hosted-runner.md`](docs/self-hosted-runner.md).

## Contributors

The following list is generated from the current git history, showing only identifiable GitHub usernames/links and filtering automation accounts:

[@TheWildJames](https://github.com/TheWildJames), [@zzh20188](https://github.com/zzh20188), [@xingguangcuican6666](https://github.com/xingguangcuican6666), [@ShirkNeko](https://github.com/ShirkNeko), [@huime180](https://github.com/huime180), [@MiRinChan](https://github.com/MiRinChan), [@FunLay123](https://github.com/FunLay123), [@guruji-byte](https://github.com/guruji-byte), [@Xiaomichael](https://github.com/Xiaomichael), [@DreamFerry](https://github.com/DreamFerry), [@liqideqq](https://github.com/liqideqq), [@elysias123](https://github.com/elysias123), [@Fede2782](https://github.com/Fede2782), [@ReeViiS69](https://github.com/ReeViiS69), [@TheSillyOk](https://github.com/TheSillyOk), [@prpjzz](https://github.com/prpjzz), [@ukriu](https://github.com/ukriu), [@wrnxr233](https://github.com/wrnxr233), [@Tools-cx-app](https://github.com/Tools-cx-app), [@Akuma-Noko](https://github.com/Akuma-Noko), [@DebugBoard](https://github.com/DebugBoard), [@FixeQyt](https://github.com/FixeQyt), [@LX200944](https://github.com/LX200944), [@Starsun](https://github.com/Starsun), [@yx1234587](https://github.com/yx1234587).

## Open Source Licenses

The full notice list is maintained in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md), and the app's Open source licenses page follows the same scope. Upstream license texts and extra obligations remain authoritative.

### Repository and Bundled Code

| Component | Source | License |
| --- | --- | --- |
| AnyBase Kernel | [`LICENSE`](LICENSE) | GPL-2.0 |
| ABK Control native bridge | `app/src/main/cpp/uapi/abk_control.h` | GPL-2.0 |
| xingguang DDK module | `ddk/xingguang-ddk/xingguang_ddk.c` | GPL |
| DDK kernel API patch | `ddk/patches/xingguang-ddk/0001-xingguang-ddk-api.patch` | GPL-2.0 |
| ZRAM LZ4 kernel glue | `zram/lz4/Makefile` | GPL-2.0-only |
| LZ4 sources and headers | `zram/lz4`, `zram/include/linux/lz4.h` | BSD-2-Clause |

### Upstream Projects and Workflow References

| Project | URL | License |
| --- | --- | --- |
| zzh20188/GKI_KernelSU_SUSFS | <https://github.com/zzh20188/GKI_KernelSU_SUSFS> | Upstream repository license |
| WildKernels/GKI_KernelSU_SUSFS | <https://github.com/WildKernels/GKI_KernelSU_SUSFS> | Upstream repository license |
| CodeLinaro CLO LA | <https://git.codelinaro.org/clo/la> | Top-level upstream project licenses |
| OnePlusOSS/kernel_manifest | <https://github.com/OnePlusOSS/kernel_manifest> | Upstream repository license / no SPDX detected |
| Xiaomichael/kernel_manifest | <https://github.com/Xiaomichael/kernel_manifest> | Upstream repository license / no SPDX detected |
| Xiaomichael/kernel_patches | <https://github.com/Xiaomichael/kernel_patches> | Upstream repository license / no SPDX detected |
| KernelSU | <https://github.com/tiann/KernelSU> | GPL-3.0 |
| KernelSU Next | <https://github.com/KernelSU-Next/KernelSU-Next> | GPL-3.0 |
| SukiSU Ultra | <https://github.com/SukiSU-Ultra/SukiSU-Ultra> | GPL-3.0 |
| ReSukiSU | <https://github.com/ReSukiSU/ReSukiSU> | GPL-3.0 |
| SUSFS | <https://gitlab.com/simonpunk/susfs4ksu> | GPL-2.0 |
| ShirkNeko/susfs4ksu | <https://github.com/ShirkNeko/susfs4ksu> | GPL-2.0 |
| SukiSU_patch | <https://github.com/ShirkNeko/SukiSU_patch> | GPL-2.0 |
| AnyKernel3 | <https://github.com/WildKernels/AnyKernel3> | GPL-2.0 |
| Xiaomichael/AnyKernel3 | <https://github.com/Xiaomichael/AnyKernel3> | Upstream repository license / NOASSERTION |
| WildKernels/kernel_patches | <https://github.com/WildKernels/kernel_patches> | GPL-2.0 |
| cctv18/susfs4oki | <https://github.com/cctv18/susfs4oki> | GPL-3.0 |
| SukiSU_KernelPatch_patch | <https://github.com/SukiSU-Ultra/SukiSU_KernelPatch_patch> | Upstream repository license |
| Action-Build | <https://github.com/Numbersf/Action-Build> | Upstream repository license |
| SUSFS module build source | <https://github.com/sidex15/susfs4ksu-module> | Upstream repository license |
| GCC prebuilts | <https://github.com/LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-gnu-6.4.1> | GPL-family toolchain notices |
| Baseband Guard | <https://github.com/vc-teahouse/Baseband-guard> | Upstream repository license |
| Re-Kernel | <https://github.com/Sakion-Team/Re-Kernel> | Upstream repository license |
| Droidspaces / virtualization patch source | <https://github.com/ravindu644/Droidspaces-OSS> | Upstream repository license |
| ABK_repo module catalog | <https://github.com/xingguangcuican6666/ABK_repo> | Upstream repository license |
| AOSP kernel/common, manifest, mkbootimg, build-tools | <https://android.googlesource.com/> | GPL-2.0 / Apache-2.0 / AOSP notices |
| Android GKI certified boot images / command line tools | <https://dl.google.com/android/> | Android distribution terms / Android SDK License |

### Android / Gradle Dependencies

Android dependencies are derived from `gradle/libs.versions.toml` and `app/build.gradle.kts`. Gradle native-platform initialization fails in this local environment, so this records direct declared dependencies; transitive dependencies follow the actual Gradle resolution result.

| License | Dependencies |
| --- | --- |
| Apache-2.0 | Android Gradle Plugin, Kotlin Gradle/Compose plugin, AndroidX Core/Lifecycle/Activity/Compose/Material3/Navigation/Work/DataStore/Test, Google Material Components, Retrofit, OkHttp, Gson, kotlinx-serialization-json, libsu, Coil |
| EPL-1.0 | JUnit 4.13.2 |

### Web npm Transitive Dependencies

Web dependencies are derived from `web/package-lock.json`.

| License | Packages |
| --- | --- |
| Apache-2.0 | `@webassemblyjs/leb128`, `@xtuc/long`, `baseline-browser-mapping`, `detect-libc` |
| BSD-2-Clause | `eslint-scope`, `esrecurse`, `estraverse`, `glob-to-regexp`, `terser` |
| BSD-3-Clause | `@xtuc/ieee754`, `fast-uri`, `flat`, `source-map`, `source-map-js` |
| CC-BY-4.0 | `caniuse-lite` |
| ISC | `electron-to-chromium`, `graceful-fs`, `icss-utils`, `isexe`, `picocolors`, `postcss-modules-extract-imports`, `postcss-modules-scope`, `postcss-modules-values`, `semver`, `which` |
| MIT | Remaining npm transitive dependencies, including `webpack`, `webpack-cli`, `sass`, `sass-loader`, `css-loader`, `mini-css-extract-plugin`, `postcss`, `ajv`, `browserslist`, `chokidar`, `@jridgewell/*`, `@parcel/watcher*`, and MIT-licensed `@webassemblyjs/*` packages. See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for the full package list. |

## License

ABK is released under GPL-2.0. This repository also references third-party projects, patches, binary sources, and package dependencies. Before using, redistributing, or modifying them, follow the license and terms of each upstream project. Users are responsible for any device damage, data loss, account risk, service interruption, compliance issue, or direct/indirect loss caused by using ABK, its workflows, custom modules, or generated artifacts.
