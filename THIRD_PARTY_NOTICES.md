# Third-Party and Open Source Notices

This document records the open source projects, embedded code, generated binaries, download sources, and package dependencies referenced by ABK. License texts and extra obligations from upstream projects remain authoritative.

## ABK Repository

| Component | Source | License |
| --- | --- | --- |
| AnyBase Kernel repository | `LICENSE` | GPL-3.0 |
| ABK Control native bridge | `app/src/main/cpp/uapi/abk_control.h` | GPL-2.0 |
| xingguang DDK module | `ddk/xingguang-ddk/xingguang_ddk.c` | GPL |
| DDK kernel API patch | `ddk/patches/xingguang-ddk/0001-xingguang-ddk-api.patch` | GPL-2.0 |
| ZRAM LZ4 kernel glue | `zram/lz4/Makefile` | GPL-2.0-only |
| LZ4 sources and headers | `zram/lz4`, `zram/include/linux/lz4.h` | BSD-2-Clause |

## Upstream Repositories and Workflow References

| Project | Reference | License |
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
| sidex15/susfs4ksu-module | <https://github.com/sidex15/susfs4ksu-module> | Upstream repository license |
| LineageOS GCC prebuilts | <https://github.com/LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-gnu-6.4.1> | GPL-family toolchain notices |
| Baseband Guard | <https://github.com/vc-teahouse/Baseband-guard> | Upstream repository license |
| Re-Kernel | <https://github.com/Sakion-Team/Re-Kernel> | Upstream repository license |
| Droidspaces-OSS | <https://github.com/ravindu644/Droidspaces-OSS> | Upstream repository license |
| ABK_repo module catalog | <https://github.com/xingguangcuican6666/ABK_repo> | Upstream repository license |
| AOSP kernel/common | <https://android.googlesource.com/kernel/common> | GPL-2.0 WITH Linux-syscall-note and AOSP notices |
| AOSP kernel manifest | <https://android.googlesource.com/kernel/manifest> | AOSP project notices |
| AOSP mkbootimg | <https://android.googlesource.com/platform/system/tools/mkbootimg> | Apache-2.0 |
| AOSP kernel build-tools | <https://android.googlesource.com/kernel/prebuilts/build-tools> | AOSP project notices |
| Android GKI certified boot images | <https://dl.google.com/android/gki/> | Android image distribution terms |
| Android command line tools | `Dockerfile.test` | Android SDK License |

## APK-Bundled SukiSU Components

The APK build workflows compile `userspace/ksud` from `SukiSU-Ultra/SukiSU-Ultra` for supported ABIs. Upstream ksud patches boot images with the in-process `android_bootimg` crate and no longer ships or requires `libmagiskboot.so`. Prebuilt `ksud` binaries are not committed to this repository. The upstream ref is declared in `.github/workflows/build-abk-app.yml` and `.github/workflows/build-abk-app-dev.yml`.

## Android / Gradle Dependencies

The Android dependency list is derived from `gradle/libs.versions.toml` and `app/build.gradle.kts`. Gradle dependency resolution could not be executed in this local environment because Gradle failed to initialize its native platform library, so this section records direct declared dependencies.

| Dependency | License |
| --- | --- |
| Android Gradle Plugin 9.1.1 | Apache-2.0 |
| Kotlin Gradle / Compose plugin 2.3.21 | Apache-2.0 |
| AndroidX Core KTX 1.15.0 | Apache-2.0 |
| AndroidX Lifecycle Runtime KTX 2.8.7 | Apache-2.0 |
| AndroidX Lifecycle ViewModel Compose 2.8.7 | Apache-2.0 |
| AndroidX Activity Compose 1.9.3 | Apache-2.0 |
| AndroidX Compose BOM 2026.05.00 | Apache-2.0 |
| AndroidX Compose UI / Graphics / Tooling Preview | Apache-2.0 |
| AndroidX Material3 1.5.0-alpha19 | Apache-2.0 |
| AndroidX Material Icons Extended | Apache-2.0 |
| Google Material Components 1.12.0 | Apache-2.0 |
| AndroidX Navigation Compose 2.8.5 | Apache-2.0 |
| Retrofit 2.11.0 and Gson converter | Apache-2.0 |
| OkHttp 4.12.0 and logging-interceptor | Apache-2.0 |
| Gson 2.11.0 | Apache-2.0 |
| kotlinx-serialization-json 1.7.3 | Apache-2.0 |
| libsu core / io 5.2.2 | Apache-2.0 |
| Coil Compose 2.7.0 | Apache-2.0 |
| WorkManager Runtime KTX 2.10.0 | Apache-2.0 |
| DataStore Preferences 1.1.2 | Apache-2.0 |
| JUnit 4.13.2 | EPL-1.0 |
| AndroidX Test JUnit 1.2.1 | Apache-2.0 |
| Espresso Core 3.6.1 | Apache-2.0 |

## Web npm Dependencies

The web dependency list is derived from `web/package-lock.json`.

| License | Packages |
| --- | --- |
| Apache-2.0 | `@webassemblyjs/leb128`, `@xtuc/long`, `baseline-browser-mapping`, `detect-libc` |
| BSD-2-Clause | `eslint-scope`, `esrecurse`, `estraverse`, `glob-to-regexp`, `terser` |
| BSD-3-Clause | `@xtuc/ieee754`, `fast-uri`, `flat`, `source-map`, `source-map-js` |
| CC-BY-4.0 | `caniuse-lite` |
| ISC | `electron-to-chromium`, `graceful-fs`, `icss-utils`, `isexe`, `picocolors`, `postcss-modules-extract-imports`, `postcss-modules-scope`, `postcss-modules-values`, `semver`, `which` |
| MIT | `@discoveryjs/json-ext`, `@jridgewell/gen-mapping`, `@jridgewell/resolve-uri`, `@jridgewell/source-map`, `@jridgewell/sourcemap-codec`, `@jridgewell/trace-mapping`, `@parcel/watcher`, `@parcel/watcher-*`, `@types/eslint`, `@types/eslint-scope`, `@types/estree`, `@types/json-schema`, `@types/node`, `@webassemblyjs/ast`, `@webassemblyjs/floating-point-hex-parser`, `@webassemblyjs/helper-api-error`, `@webassemblyjs/helper-buffer`, `@webassemblyjs/helper-numbers`, `@webassemblyjs/helper-wasm-bytecode`, `@webassemblyjs/helper-wasm-section`, `@webassemblyjs/ieee754`, `@webassemblyjs/utf8`, `@webassemblyjs/wasm-edit`, `@webassemblyjs/wasm-gen`, `@webassemblyjs/wasm-opt`, `@webassemblyjs/wasm-parser`, `@webassemblyjs/wast-printer`, `@webpack-cli/configtest`, `@webpack-cli/info`, `@webpack-cli/serve`, `acorn`, `acorn-import-phases`, `ajv`, `ajv-formats`, `ajv-keywords`, `browserslist`, `buffer-from`, `chokidar`, `chrome-trace-event`, `clone-deep`, `colorette`, `commander`, `cross-spawn`, `css-loader`, `cssesc`, `enhanced-resolve`, `envinfo`, `es-module-lexer`, `escalade`, `events`, `fast-deep-equal`, `fastest-levenshtein`, `find-up`, `function-bind`, `has-flag`, `hasown`, `immutable`, `import-local`, `interpret`, `is-core-module`, `is-extglob`, `is-glob`, `is-plain-object`, `isobject`, `jest-worker`, `json-parse-even-better-errors`, `json-schema-traverse`, `kind-of`, `loader-runner`, `locate-path`, `merge-stream`, `mime-db`, `mime-types`, `mini-css-extract-plugin`, `nanoid`, `neo-async`, `node-addon-api`, `node-releases`, `p-limit`, `p-locate`, `p-try`, `path-exists`, `path-key`, `path-parse`, `picomatch`, `pkg-dir`, `postcss`, `postcss-modules-local-by-default`, `postcss-selector-parser`, `postcss-value-parser`, `readdirp`, `rechoir`, `require-from-string`, `resolve`, `resolve-cwd`, `resolve-from`, `sass`, `sass-loader`, `schema-utils`, `shallow-clone`, `shebang-command`, `shebang-regex`, `source-map-support`, `supports-color`, `supports-preserve-symlinks-flag`, `tapable`, `terser-webpack-plugin`, `undici-types`, `update-browserslist-db`, `util-deprecate`, `watchpack`, `webpack`, `webpack-cli`, `webpack-merge`, `webpack-sources`, `wildcard` |

## Contributors

Contributor data is normalized from local git history to identifiable GitHub usernames/links and sorted by username. Automation accounts and identities without a reliable mapping are filtered out.

[@Akuma-Noko](https://github.com/Akuma-Noko), [@DebugBoard](https://github.com/DebugBoard), [@DreamFerry](https://github.com/DreamFerry), [@elysias123](https://github.com/elysias123), [@Fede2782](https://github.com/Fede2782), [@FixeQyt](https://github.com/FixeQyt), [@FunLay123](https://github.com/FunLay123), [@gsf114](https://github.com/gsf114), [@guruji-byte](https://github.com/guruji-byte), [@huime180](https://github.com/huime180), [@liqideqq](https://github.com/liqideqq), [@LX200944](https://github.com/LX200944), [@Mazha0309](https://github.com/Mazha0309), [@MiRinChan](https://github.com/MiRinChan), [@prpjzz](https://github.com/prpjzz), [@ReeViiS69](https://github.com/ReeViiS69), [@ShirkNeko](https://github.com/ShirkNeko), [@Starsun](https://github.com/Starsun), [@TheSillyOk](https://github.com/TheSillyOk), [@TheWildJames](https://github.com/TheWildJames), [@Tools-cx-app](https://github.com/Tools-cx-app), [@ukriu](https://github.com/ukriu), [@wrnxr233](https://github.com/wrnxr233), [@Xiaomichael](https://github.com/Xiaomichael), [@xingguangcuican6666](https://github.com/xingguangcuican6666), [@yx1234587](https://github.com/yx1234587), [@zzh20188](https://github.com/zzh20188).
