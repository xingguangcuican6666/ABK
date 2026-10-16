# Kernel runner configuration and GKI download cache

通过仓库设置选择构建机器，并减少自托管 GKI 构建的重复下载。

## 使用前准备

1. Fork 仓库并启用 Actions。默认分支需要包含 runner 变量配置和自托管环境保护：磁盘清理、构建空间扩展等操作应只在 GitHub 托管环境运行。
2. 在 `Settings → Actions → Runners` 注册 Linux x86_64 runner，并为它添加一个专用标签，例如 `kernel-builder`。确认页面显示 `Online`。
3. 自托管机器需要具备现有工作流要求的编译环境、磁盘空间和依赖安装权限。选择 ARM64 主机标签不会自动适配面向 x86_64 的预编译工具链。

## 从 App 设置

此入口需要使用包含 runner 设置功能的 App 版本。

1. 登录 GitHub，确认 App 已识别自己的 Fork。
2. 打开 **设置 → 内核构建环境**，读取当前仓库设置。
3. 在 GKI 或 OnePlus/Oplus 项下打开自托管开关，填写 runner 标签，例如 `kernel-builder`，或 `["self-hosted","linux","x64","kernel-builder"]`。
4. 点击 **保存**。保存成功后，再提交新的内核构建。
5. 要切回 GitHub 托管，关闭对应开关并保存；已保存的机器标签可以继续保留。

设置属于当前 Fork 的仓库配置，对随后从 App、网页或 CLI 提交的构建共同生效。它不是当前手机独有的偏好，也不会移动已经排队或运行中的任务。

App 会检查 Fork 默认分支的工作流是否支持这些变量。如果提示不支持，先更新工作流再刷新。读取或保存失败时不要按“已切换”处理，修复错误后刷新确认。

## 从 GitHub 网页设置

在 `Settings → Secrets and variables → Actions → Variables` 中创建 **Repository variables**。这些配置不占用 workflow dispatch 参数，App/CLI 无需额外传参。

表中的变量名是工作流识别的固定配置键，请按原名填写。`kernel-builder` 是通用示例标签，可替换成自己的 runner 标签；它不是必须使用的名称。

| 变量 | 用途 | 未配置时 |
| --- | --- | --- |
| `KERNEL_RUNNER` | GKI runner 标签，例如 `kernel-builder` 或 `["self-hosted","linux","x64","kernel-builder"]` | `ubuntu-latest` |
| `KERNEL_SELF_HOSTED` | `false` 强制 GitHub 托管，`true` 恢复保存的 GKI 标签 | 使用原标签配置 |
| `ONEPLUS_RUNNER` | 独立的 OnePlus/Oplus runner 标签 | `ubuntu-latest` |
| `ONEPLUS_SELF_HOSTED` | OnePlus/Oplus 对应开关 | 使用原标签配置 |
| `KERNEL_LOCAL_CACHE` | GKI 自托管下载缓存；设为 `off` 可关闭 | 自托管启用、GitHub 托管关闭 |
| `KERNEL_LOCAL_CACHE_DIR` | 缓存根目录，必须是工作目录之外的绝对路径 | `~/.cache/abk-downloads` |

单标签直接填文本；多标签使用以 `[` 开头的非空 JSON 字符串数组，不加外层引号或前导空格。一台 runner 必须同时满足全部标签。开关值填写 `true` 或 `false`，不加引号；未保存机器标签时，即使开关为 `true` 也仍使用默认 GitHub runner。离线或没有匹配标签时任务排队，不会自动回退。变量只影响新提交的构建。

例如，要让 GKI 使用带 `kernel-builder` 标签的自托管机器，创建：

```text
KERNEL_RUNNER = ["self-hosted","kernel-builder"]
KERNEL_SELF_HOSTED = true
```

切回 GitHub 时仅将 `KERNEL_SELF_HOSTED` 改为 `false`。OnePlus/Oplus 使用独立的 `ONEPLUS_RUNNER` 和 `ONEPLUS_SELF_HOSTED`，操作相同。

## 从 GitHub CLI 设置

将 `OWNER/REPOSITORY` 替换为自己的 Fork：

```bash
gh variable set KERNEL_RUNNER -R OWNER/REPOSITORY --body '["self-hosted","kernel-builder"]'
gh variable set KERNEL_SELF_HOSTED -R OWNER/REPOSITORY --body true

# 切回 GitHub 托管
gh variable set KERNEL_SELF_HOSTED -R OWNER/REPOSITORY --body false

# 查看已保存的开关
gh variable get KERNEL_SELF_HOSTED -R OWNER/REPOSITORY
```

## 权限与构建参数

App 使用 GitHub Actions Variables API 读取、创建或更新仓库变量，不向 `workflow_dispatch.inputs` 添加字段。当前 `kernel-custom.yml` 和 `kernel-source.yml` 各有 25 个输入，`oneplus-custom.yml` 有 12 个，使用此开关不会改变这些数量。

App 的 GitHub Device Flow 已请求 `repo` scope，仍需要当前账号具有目标仓库的相应访问权限。使用 fine-grained token 时需要仓库 `Variables` 读写权限，私有仓库还需要读取工作流文件的权限。权限不足、授权失效或组织限制可能导致请求失败，应重新授权或通过 GitHub 网页配置。参见 [GitHub Variables API 权限说明](https://docs.github.com/en/rest/actions/variables#create-a-repository-variable)。

变量写入不是多字段原子事务。保存中途失败时，部分字段可能已更新；应刷新读取实际状态后再重试，不要在未确认的状态下提交构建。

## 验证与常见情况

- **确认配置生效**：新提交一轮构建，在 Actions 的内核编译 job 中查看选中的 runner。
- **任务一直排队**：检查 runner 是否在线，以及标签数组中的每个标签是否都属于同一台机器。
- **开启后仍走 GitHub**：检查是否保存了自托管标签、工作流是否支持变量，以及实际运行的仓库和分支是否正确。
- **App 报权限错误**：检查账号访问权限和 token 授权。保存失败不会被视为成功。
- **第一次下载没有变快**：首次仍需建立缓存，后续构建才复用对象。
- **需要禁用下载缓存**：将 `KERNEL_LOCAL_CACHE` 设为 `off`。这不会改变 runner 的选择。

## 缓存范围与隔离

下载缓存覆盖 GKI 的 AOSP/Clang/Rust/JDK 源码与预编译资源、GCC、打包工具、AnyKernel3、SUSFS 和公共补丁资源。OnePlus 的 runner 可独立配置。

缓存按仓库 URL、manifest 分支和调用方仓库隔离，放在 Actions 工作目录之外。分支和标签每次查询上游，提交不变则复用 Git 对象；变化时只获取缺少的对象。SUSFS 固定提交支持短/完整 SHA，首次保存分支完整历史，之后从该历史检出指定提交。

每轮构建使用独立源码文件和 Git 元数据，补丁不会写入缓存。仅不可变 Git pack/index 文件可能使用硬链接；跨文件系统时退回复制。缓存更新使用文件锁，网络查询失败会终止，不会静默使用旧分支。GitHub 托管构建继续使用原云端缓存；自托管缓存启用时复用本机 ccache。

首次下载仍需要时间和额外磁盘空间；清理缓存前应等待使用它的构建结束。默认适用于现有 Linux x86_64/Ubuntu 工具链环境。

## English

Variable names in the table are fixed workflow configuration keys. `kernel-builder` and `OWNER/REPOSITORY` are examples to replace with your own runner label and repository.

Register an online Linux x86_64 runner with a dedicated label such as `kernel-builder`, and ensure the workflow has self-hosted cleanup protection. In an App version with runner settings, open **Settings → Kernel build environment**, configure GKI or OnePlus/Oplus labels, select self-hosting and save before starting a new build. Disable and save to return to GitHub hosting. Settings are shared by subsequent builds in the selected fork, regardless of whether they are submitted from the App, web UI or CLI.

Existing clients can also use runner settings configured through repository Actions Variables, without additional dispatch inputs. `KERNEL_RUNNER` and `ONEPLUS_RUNNER` accept a plain label or a non-empty JSON array of labels; unset values use `ubuntu-latest`. Set the corresponding `*_SELF_HOSTED` variable to `false` to force GitHub hosting, or `true` to restore saved labels. Unset toggles preserve existing selection. Offline self-hosted runners queue jobs rather than falling back.

The App uses the repository Variables API rather than workflow dispatch inputs. The existing GKI/custom-source input counts remain 25 each; OnePlus remains 12. OAuth/classic tokens require the `repo` scope and appropriate repository access; fine-grained tokens require Variables read/write permission. Unsupported workflows or failed requests are reported. Variable updates are not atomic across fields, so refresh after a partial failure before retrying.

The persistent download cache applies to GKI AOSP manifests, toolchains, SUSFS, AnyKernel3 and common patch repositories. Set `KERNEL_LOCAL_CACHE=off` to opt out, or provide an absolute `KERNEL_LOCAL_CACHE_DIR` outside the workspace. Each build receives independent source files and Git metadata; only immutable Git packs may be hard-linked. Remote refs are checked on every branch build, fixed SUSFS revisions remain pinned, and errors do not silently reuse stale branch data. Cache directories are repository-isolated and locked during updates.
