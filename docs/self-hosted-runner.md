# Self-hosted runner setup

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
