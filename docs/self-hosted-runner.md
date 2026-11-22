# Build runner configuration

## 内核构建 / Kernel builds

在仓库的 `Settings → Secrets and variables → Actions → Variables` 中配置以下 **Repository variables**，以后切换机器或托管方式只需修改变量。不要创建同名 Secret 或 Environment variable。

Configure these **repository variables** under `Settings → Secrets and variables → Actions → Variables`. Switch machines or hosting by editing the variable, without changing workflows or the App. Do not use secrets or environment-level variables for runner selection.

### 自托管开关 / Self-hosted toggle

先把自托管标签保存在 `KERNEL_RUNNER`（例如 `["self-hosted","abk"]`），以后只需编辑 `KERNEL_SELF_HOSTED`：

| 开关值 / Toggle value | 行为 / Behavior |
| --- | --- |
| `true` | 使用保存的 `KERNEL_RUNNER` / Use the saved `KERNEL_RUNNER` |
| `false` | 强制使用 GitHub 托管的 `ubuntu-latest`，保留原标签 / Force GitHub-hosted `ubuntu-latest` without changing saved labels |
| 未设置或为空 / Unset or empty | 保持原有行为，按 `KERNEL_RUNNER` 选择 / Preserve existing `KERNEL_RUNNER` selection |

变量值直接填写小写 `true` 或 `false`，不要加引号或空格。GitHub Variables 页面是文本编辑框；这里把这两个值用作开关。`KERNEL_RUNNER` 未配置时仍默认使用 `ubuntu-latest`，所以启用自托管前应先保存机器标签。

OnePlus/Oplus 对应的独立开关是 `ONEPLUS_SELF_HOSTED`，机器标签保存在 `ONEPLUS_RUNNER`。这些开关不影响 APK 的 `APP_RUNNER`，也不占用 `workflow_dispatch` 的输入名额，继续使用上游 APK 即可。修改后发起新构建；已排队或正在运行的任务不会因此迁移。

Save self-hosted labels in `KERNEL_RUNNER` first, then toggle `KERNEL_SELF_HOSTED` between the plain text values `true` and `false`, without quotes or whitespace. `false` forces GitHub-hosted `ubuntu-latest`; `true` restores the saved runner selection. An unset toggle preserves previous behavior. If no runner labels are configured, the default remains `ubuntu-latest`. OnePlus/Oplus uses the independent `ONEPLUS_SELF_HOSTED` toggle and `ONEPLUS_RUNNER` labels. APK builds are unaffected. No dispatch inputs or modified APK are required. Changes apply to new builds, not jobs already queued or running.

### Runner 标签配置 / Runner label configuration

| 变量 / Variable | 作用范围 / Scope | 未设置或为空 / Unset or empty |
| --- | --- | --- |
| `KERNEL_RUNNER` | 所有调用 `build.yml` 的 GKI 构建：自定义参数、自定义源码、各 Android 版本、全功能矩阵 / All GKI callers of `build.yml`: custom parameters, custom sources, Android version workflows and the feature matrix | `ubuntu-latest` |
| `ONEPLUS_RUNNER` | 调用 `oneplus-build.yml` 的 OnePlus/Oplus 内核构建 / OnePlus/Oplus kernel builds calling `oneplus-build.yml` | `ubuntu-latest` |
| `APP_RUNNER` | APK 构建，保持原有的单标签格式 / APK builds, retaining the existing single-label format | `ubuntu-latest` |

`KERNEL_RUNNER` 和 `ONEPLUS_RUNNER` 相互独立，也不继承 `APP_RUNNER`。预检查和产物整理等独立任务仍使用各自原有的 runner。GitHub Actions 页面、App、CLI 发起的构建只要调用相应工作流，都会使用这项设置，无需新增构建参数。

`KERNEL_RUNNER` and `ONEPLUS_RUNNER` are independent and do not inherit `APP_RUNNER`. Separate preflight and artifact-processing jobs retain their existing runners. Builds launched through Actions, the App or CLI use the setting when they call the corresponding workflow; no extra dispatch input is needed.

### 内核 Runner 标签变量的取值 / Kernel runner label values

| 变量值 / Value | 行为 / Behavior |
| --- | --- |
| 不设置、删除变量 / Unset or delete | GitHub-hosted `ubuntu-latest` |
| `ubuntu-24.04` | 指定 GitHub 托管镜像 / Pin a GitHub-hosted image |
| `abk` | 使用具有该标签的 runner / Use a runner with this label |
| `["self-hosted","abk"]` | 同时匹配两个标签，保持本 Fork 原来的 GKI 调度方式 / Match both labels, preserving this fork's previous GKI routing |
| `["self-hosted","linux","x64","abk"]` | 同时匹配全部标签 / Match all listed labels |

单个标签直接填写，不加引号；多个标签使用非空 JSON 字符串数组，以 `[` 开头，标签用双引号包围，不要在数组外再加引号或前导空格。所有标签必须由同一台 runner 同时满足；这不是按顺序尝试的候选机器列表。选择可运行现有构建脚本的 Linux x86_64 / Ubuntu 环境。

Enter a single label without quotes. For multiple labels, use a non-empty JSON array of strings starting with `[`, with double-quoted labels and no outer quotes or leading whitespace. One runner must match every label; the array is not a fallback list. Choose a Linux x86_64 / Ubuntu environment compatible with the existing build scripts.

### 从固定自托管配置迁移 / Migrate from hardcoded self-hosting

在合并 runner 配置改动前，将仓库变量 `KERNEL_RUNNER` 设为 `["self-hosted","abk"]`、`KERNEL_SELF_HOSTED` 设为 `true`，即可继续使用原来的 GKI runner。`ONEPLUS_RUNNER` 不设置则维持 GitHub 托管。以后要切回 GitHub，只需将 `KERNEL_SELF_HOSTED` 改为 `false`；改回 `true` 即恢复保存的机器标签。要换机器时才需要修改 `KERNEL_RUNNER`。

Before merging the runner configuration change, set `KERNEL_RUNNER` to `["self-hosted","abk"]` and `KERNEL_SELF_HOSTED` to `true` to retain the existing GKI runner. Leave `ONEPLUS_RUNNER` unset to retain GitHub hosting for OnePlus. Set `KERNEL_SELF_HOSTED` to `false` to switch to GitHub and back to `true` to restore the saved labels. Only edit `KERNEL_RUNNER` when changing machines.

匹配的自托管 runner 离线或标签不匹配时，任务会排队，不会自动切回 GitHub。修改变量后请新建一次构建；已有任务不会迁移。GKI 构建的磁盘清理步骤仅在 GitHub 托管环境执行，避免在自托管机器上卸载工具链、删除 SDK 或关闭 swap。自托管构建也会跳过自动删除 Chrome APT 源的逻辑，但仍需具备原有依赖安装步骤所需的 sudo 权限。

If no matching self-hosted runner is online, jobs queue rather than falling back to GitHub. Start a new build after changing variables; existing jobs are not migrated. The GKI disk cleanup step only runs on GitHub-hosted runners, preserving toolchains, SDKs and swap on self-hosted machines. Self-hosted builds also skip automatic Chrome APT source deletion but still require sudo for the existing dependency installation steps.

### 自托管下载缓存 / Local download cache

GKI 自托管构建默认将下载缓存放在 `~/.cache/abk-downloads/<仓库标识>/v1`，位于 Actions 工作目录之外。`actions/checkout` 继续清理每轮工作目录，缓存不会被一起删除。

分支和标签先查询远端提交；提交未变时直接复用 Git 对象，有更新时只获取缺少的对象。固定 SHA 已缓存时无需再次访问该仓库。AOSP manifest 和各源码项目由 `repo sync` 增量更新，随后记录每个项目的固定 SHA，复制干净的源码及独立 Git 元数据，并逐一核验构建目录的提交。仅不可变 Git 包可使用本地硬链接，源码文件不会与缓存共用可写文件。日志和运行摘要会列出每个项目的旧、新提交及复用情况。补丁只应用到本轮构建源码，缓存中的源码不参与打补丁。

覆盖 AOSP 源码及 Clang/Rust/JDK 等预编译资源、GCC、打包工具、SUSFS、公共补丁仓库和指定提交的自定义源码。KernelSU 上游安装脚本、APT 索引等少量请求仍按原流程执行。自托管缓存启用时复用本机 ccache，并跳过工具和 ccache 的云端缓存下载；GitHub 托管构建继续使用原云端缓存。

| Repository variable | 默认值 | 用途 |
| --- | --- | --- |
| `KERNEL_LOCAL_CACHE` | `auto` | 自托管启用；设为 `off` 可关闭 |
| `KERNEL_LOCAL_CACHE_DIR` | `~/.cache/abk-downloads` | 可指定工作目录之外的绝对路径，脚本会添加仓库隔离子目录 |

第一次缓存仍需要下载。后续仍会进行小量远端版本查询；网络查询失败会终止，不会悄悄使用旧分支。不同仓库、manifest 分支分开存储，同一缓存的更新使用文件锁。缓存会占用额外磁盘空间；需要清理时先等待使用它的构建结束。

启用 `config/config` 的 SUSFS 固定提交时，缓存会保留对应分支的完整历史，再检出配置的提交，支持短 SHA 和完整 SHA。首次使用或从浅缓存切换时需要下载历史，之后复用；仍会查询分支更新，但最终检出的版本始终是指定提交。私有自定义源码的认证仅对拉取子进程生效，不再写入 runner 的全局或源码仓库 Git 配置；此改动不会自动清除旧版本构建已经留下的配置。

Pinned SUSFS builds cache the selected branch's full history before checking out the configured short or full SHA. The first build (or an upgrade from a shallow cache) downloads history; later builds reuse it. Branch updates are still checked, but the checkout remains pinned. Private custom-source authentication is scoped to the checkout process and is not written into global or repository Git configuration. This does not remove configuration left by older workflow versions.

Self-hosted GKI builds keep repository-scoped downloads outside the Actions workspace. Git refs are compared before fetching; cached immutable commits need no remote lookup. A canonical, unpatched AOSP client is synced incrementally, and each build gets a clean copy with independent source files and shallow-clone metadata, checked against every resolved commit. Only immutable Git packs may be hard-linked locally. Logs and step summaries show commit changes and reuse. Set `KERNEL_LOCAL_CACHE=off` to opt out, or set `KERNEL_LOCAL_CACHE_DIR` to an absolute base path outside the workspace. Hosted runners retain their cloud-cache behavior. Small requests from upstream KernelSU setup scripts and APT remain unchanged. Allow the first download and additional disk space; stop cache users before removing the cache.

### 服务运行但 Runner 离线 / Service running but runner offline

本地服务处于 `active` 不代表它能收到任务。请同时检查 GitHub 的 `Settings → Actions → Runners` 是否显示 `Online`，以及 runner 日志中是否反复出现 `Runner connect error` 或 `broker.actions.githubusercontent.com/message` 超时。

An `active` local service does not guarantee that it can receive jobs. Check that GitHub's `Settings → Actions → Runners` shows `Online`, and inspect the runner logs for repeated `Runner connect error` messages or timeouts from `broker.actions.githubusercontent.com/message`.

如果网络需要代理，请在 runner 安装目录的 `.env` 中设置小写代理变量，然后重启 runner 服务。systemd 服务不会自动继承交互式终端的代理设置。以下示例假定代理在该 Linux 环境的 `127.0.0.1:7897` 可访问；请按实际地址调整。

If your network requires a proxy, set lowercase proxy variables in `.env` in the runner installation directory, then restart the runner service. A systemd service does not automatically inherit proxy settings from an interactive shell. This example assumes the proxy is reachable from Linux at `127.0.0.1:7897`; adjust the address for your environment.

```dotenv
http_proxy=http://127.0.0.1:7897
https_proxy=http://127.0.0.1:7897
no_proxy=localhost,127.0.0.1,::1
```

代理变量在 runner 启动时读取，详见 [GitHub 代理配置文档](https://docs.github.com/en/actions/how-tos/manage-runners/use-proxy-servers)。

Proxy variables are read when the runner starts; see [GitHub's proxy configuration documentation](https://docs.github.com/en/actions/how-tos/manage-runners/use-proxy-servers).

## APK 自托管环境 / APK self-hosted setup

## Kernel workflow safety / 内核工作流安全处理

GKI 的磁盘空间清理与 OnePlus/Oplus 的构建空间扩展仅在 GitHub 托管 runner 上执行；自托管机器保留已有工具链、SDK、磁盘布局和 swap。两种工作流在自托管环境中都不会自动删除 Chrome APT 源。虚拟化补丁使用 `$RUNNER_TEMP` 下的独立目录，每次拉取前清理该目录，避免连续构建因旧 checkout 失败；拉取走 `cached-downloads.py`，因此自托管下载缓存对 Droidspaces 同样生效，且始终检出 `config/config` 中 `droidspaces=` 指定的 tag（默认 `v7.1.0`，可用仓库变量 `ABK_DROIDSPACES_REF` 覆盖），不会跟随上游 HEAD 漂移。依赖安装仍需要原有的 sudo 权限。

私有自定义源码的认证仅在拉取子进程中生效，不写入全局或源码仓库 Git 配置；拉取失败后也不会新增持久凭据。旧工作流已经留下的配置不会被自动删除。

GKI disk cleanup and OnePlus/Oplus build-space expansion run only on GitHub-hosted runners, preserving installed toolchains, SDKs, disk layouts and swap on self-hosted machines. Both workflows also preserve self-hosted Chrome APT sources. Virtualization patches use a fresh checkout under `$RUNNER_TEMP` on each build; the fetch goes through `cached-downloads.py`, so the self-hosted download cache covers Droidspaces too, and it always checks out the tag named by `droidspaces=` in `config/config` (default `v7.1.0`, overridable with the `ABK_DROIDSPACES_REF` repository variable) instead of tracking upstream HEAD. Existing dependency installation still requires sudo. Private custom-source authentication is scoped to the checkout process rather than saved in global or repository Git configuration, including on failure; older persistent settings are not automatically removed.

## APK runner setup / APK 运行环境

ABK 管理器（APK）的两个工作流——`build-abk-app.yml` 和 `build-abk-app-dev.yml`——通过仓库变量 `APP_RUNNER` 选择运行环境。未设置或为空时使用 GitHub 托管的 `ubuntu-latest`；设置后，该值会直接作为 `runs-on`（例如 `self-hosted` 或自定义标签 `abk-builder`）。Fork 默认无需任何配置即可正常工作。

The two ABK manager (APK) workflows — `build-abk-app.yml` and `build-abk-app-dev.yml` — pick their runner via the repository variable `APP_RUNNER`. When unset or empty, both run on the GitHub-hosted `ubuntu-latest`; when set, the value is passed straight through to `runs-on` (e.g. `self-hosted` or a custom label like `abk-builder`). A fresh fork works out of the box with no configuration.

---

## 中文

### 何时需要自托管 Runner

仅在你想要更快的构建、复用本地缓存或避免消耗 GitHub Actions 免费额度时再考虑。否则保留 `APP_RUNNER` 为空即可。

### 系统要求

Linux x86_64（推荐 Ubuntu 24.04 LTS），≥ 16 GB RAM，≥ 80 GB 可用磁盘。

### 安装步骤

1. 系统依赖：

   ```bash
   sudo apt update
   sudo apt install -y build-essential git curl unzip jq python3 python3-pip ca-certificates
   ```

2. JDK 21（Temurin）：

   ```bash
   wget -qO- https://packages.adoptium.net/artifactory/api/gpg/key/public \
     | sudo gpg --dearmor -o /usr/share/keyrings/adoptium.gpg
   echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo $VERSION_CODENAME) main" \
     | sudo tee /etc/apt/sources.list.d/adoptium.list
   sudo apt update && sudo apt install -y temurin-21-jdk
   ```

3. Android SDK 与所需包（命令行工具自行安装并配置 `ANDROID_HOME` / `PATH`）：

   ```bash
   yes | sdkmanager --licenses
   sdkmanager "platforms;android-37.0" "build-tools;37.0.0" "cmake;3.22.1" "ndk;28.2.13676358"
   ```

4. Rust 与目标平台：

   ```bash
   curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
   source "$HOME/.cargo/env"
   rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android \
                     aarch64-unknown-linux-musl x86_64-unknown-linux-musl
   ```

5. 注册 Runner：在仓库的 `Settings → Actions → Runners → New self-hosted runner` 中按页面给出的 `download` 与 `config` 命令操作，然后 `./run.sh` 启动。完整流程参考 [GitHub 官方文档](https://docs.github.com/en/actions/hosting-your-own-runners/managing-self-hosted-runners/adding-self-hosted-runners)。

6. 在仓库的 `Settings → Secrets and variables → Actions → Variables` 创建变量 `APP_RUNNER`，值为 `self-hosted` 或你为 runner 设置的标签。

7. 在 Actions 页面手动触发 `Build ABK App`，确认任务被你的 runner 接收。

---

## English

### When you need a self-hosted runner

Only when you want faster builds, want to reuse local caches, or want to avoid burning GitHub Actions minutes. Otherwise leave `APP_RUNNER` empty.

### System requirements

Linux x86_64 (Ubuntu 24.04 LTS recommended), ≥ 16 GB RAM, ≥ 80 GB free disk.

### Setup

1. System packages:

   ```bash
   sudo apt update
   sudo apt install -y build-essential git curl unzip jq python3 python3-pip ca-certificates
   ```

2. JDK 21 (Temurin):

   ```bash
   wget -qO- https://packages.adoptium.net/artifactory/api/gpg/key/public \
     | sudo gpg --dearmor -o /usr/share/keyrings/adoptium.gpg
   echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo $VERSION_CODENAME) main" \
     | sudo tee /etc/apt/sources.list.d/adoptium.list
   sudo apt update && sudo apt install -y temurin-21-jdk
   ```

3. Android SDK + required packages (install the command-line tools yourself and export `ANDROID_HOME` / `PATH`):

   ```bash
   yes | sdkmanager --licenses
   sdkmanager "platforms;android-37.0" "build-tools;37.0.0" "cmake;3.22.1" "ndk;28.2.13676358"
   ```

4. Rust + targets:

   ```bash
   curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
   source "$HOME/.cargo/env"
   rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android \
                     aarch64-unknown-linux-musl x86_64-unknown-linux-musl
   ```

5. Register the runner: in the repo, `Settings → Actions → Runners → New self-hosted runner` gives you `download` and `config` commands; follow them, then start with `./run.sh`. Full walkthrough: [GitHub docs](https://docs.github.com/en/actions/hosting-your-own-runners/managing-self-hosted-runners/adding-self-hosted-runners).

6. In the repo, create `APP_RUNNER` under `Settings → Secrets and variables → Actions → Variables`. Value: `self-hosted` or the custom label you assigned to your runner.

7. Manually trigger `Build ABK App` from the Actions tab and confirm your runner picks up the job.
