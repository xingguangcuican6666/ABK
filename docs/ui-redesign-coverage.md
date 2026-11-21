# App UI 重设计覆盖与验收

> 进行中。接入组件库、源码通过编译和完成原生界面验收是三个不同阶段。本表不把共享主题变化算作页面重铺完成，也不把 instrumentation 源码编译算作设备测试执行。

## 来源与兼容边界

- 自包含 `:abk-ui` 来源：ABK UI 设计仓库提交 `2ad9b29f3f9e0853d53b2f080a40c5462819c040`，来源及许可见 [`abk-ui/README.md`](../abk-ui/README.md)。不需要同级设计仓库、Maven Local 或 catalog 示例业务。
- App 工具链：Gradle 9.3.1、AGP 9.1.1 内置 Kotlin、Kotlin/Compose 2.3.21、Compose BOM 2026.05.00、Material3 1.5.0-alpha19、compileSdk 37。App 保持 minSdk 26、targetSdk 35；库 minSdk 23。两模块 Java/Kotlin 字节码均为 11。
- 构建命令使用 JDK 17；组件库 Robolectric 测试单独用 Java 21 launcher，不修改机器默认 Java。
- Android 12+ 动态色与固定种子 `#496A78` 回退、霞鹜文楷正文和技术等宽字体；完整字体打包前约 47.7 MB，不能把该数值当作最终 APK 增量。
- 宿主继续持有偏好、背景图、透明度、模糊、insets、凭据、队列及设备操作。组件库不持久化业务状态，不复制 catalog 假数据。
- Lucide 几何来源仍为注册表与生成器，保留语义角色及 RTL 镜像；普通卡片不使用整卡状态色。

## 页面清单

“已重铺”仅指本轮迁移源码；各行仍受下文设备验收边界约束。

| 入口或子页 | 当前状态 | 已有验证或下一步 |
| --- | --- | --- |
| `MainActivity` 经典/运行态目的地、手机底栏、平板侧栏 | 导航已迁移；宿主提示弹层待迁移 | 保留按模式/能力筛选和子页隐藏；等权导航、恢复、预测返回仍待设备验收 |
| 共享主题、背景/模糊宿主、通用卡片、顶栏、Snackbar | 已接入；兼容包装仍需逐页清理 | 库主机测试已在标准 CI 通过；旧包装部分颜色参数仍需后续审查 |
| 状态首页：设备/账号、活动任务、最近构建 | 已重铺 | 标准/Dev 编译；加载/空态/错误/长列表的独立交互回归待补 |
| 构建主配置：GKI、OnePlus、自定义源码 | 已重铺 | `BuildConfigurationUiTest` 源码编译；真实业务仍走原 ViewModel |
| 构建方案列表、保存/导入/导出、构建队列 | 已重铺 | 保留快照与派发入口；主机测试在标准 CI 执行 |
| 自定义源码/凭据/defconfig、提交参数预览 | 已重铺 | 受控输入、真实失焦、脱敏、恢复及 320dp/2×/RTL 的 UI 测试已编译，尚未在设备执行 |
| KernelSU 构建期补丁配置子页 | 已接入 | 独立于外部构建模块、运行态 ZIP 和 App 扩展；保留能力检测、旧字段封套与方案 v9 兼容测试 |
| 模块仓库：外部构建模块、模块集、运行态仓库、搜索/筛选/详情 | 已重铺 | 编译通过；刷新清空旧条目造成闪动的问题尚未处理 |
| 刷写主列表、下载管理、工作流详情 | 已重铺 | `FlashUiTest` 已编译；列表取消与详情取消保留原有不同交互 |
| 预构建来源、分类/条件筛选、产物下载卡和本地文件卡 | 已重铺 | 保留刷新、离线及筛选归一化；操作按产物类型和 Root 能力显示 |
| 参数摘要、来源提示、刷写/槽位确认、执行日志、安装/删除/取消弹层 | 已重铺 | 假回调测试覆盖执行期关闭/重启限制、槽位选择与确认分离、能力相关操作；设备未执行 |
| 本地安装/修补：文件、直接安装、OTA、AnyKernel3 四模式 | 已重铺；两版目标 CI 成功 | 执行、签名校验、文件暂存、KMI 策略保持原样；受控假回调 UI 测试已在目标环境编译，未在设备执行 |
| 本地安装：分区/槽位、内置/本地 LKM、变体/KMI、高级选项、来源确认、结果/日志 | 已重铺；两版目标 CI 成功 | 模式能力与执行期禁用、高级项单次回调、结果操作、提示/确认分离测试已编译；标准主机测试通过，原生渲染仍待验收 |
| 运行态首页：状态、管理器、构建参数、功能/能力摘要 | 已重铺；本批目标 CI 待验证 | 普通卡面、纵向技术字段和换行能力徽标；非空状态不等于原生管理权限，修补入口为显式操作 |
| 已安装模块：搜索/分组/开关/更新/WebUI/action/卸载 | 已重铺；本批目标 CI 待验证 | 保留类型、只读、卸载及 WebUI/action 能力边界；4 项纯展示逻辑单测直接 JUnit 执行通过 |
| 运行态模块文件权限、安装/更新/卸载确认、变更日志、执行弹层 | 已重铺；本批目标 CI 待验证 | 7 项假回调 UI 测试在缓存环境编译；安装进行中不可关闭，成功结束才可重启；独立 action 输出仍可关闭且不会取消后台任务 |
| Root 授权：列表、搜索、加载/刷新、应用详情与 profile | 待页面重铺 | 授权作用域/时长、应用图标、模板及编辑校验保持原边界 |
| SUSFS 控制：能力、各控制表单及执行反馈 | 待页面重铺 | 不改变后端探测或控制执行器 |
| 设置主页面、账号/退出、下载目录、镜像、语言、更新、诊断导出 | 待页面重铺 | 保留文本草稿提交时机与偏好；不展示或记录凭据 |
| 设置子页：主题/背景/透明度/模糊、管理器工具、内核能力、profile 模板 | 待页面重铺 | 保留能力决定的入口与预测返回 |
| 设置子页：扩展管理、关于仓库、贡献者、开源许可 | 待页面重铺 | 第三方头像、许可证及链接不替换为示例数据 |
| 设置弹层：Runner、签名密钥导入、计时安全确认及其他设置选择器 | 待迁移 | 保留敏感输入、计时、授权及关闭限制 |
| OOBE：介绍、Root 检查、OAuth/PAT、设备码、Fork 检查/错误 | 待页面重铺 | 不因重组布局重复发起登录、授权、Fork 或同步 |
| 宿主：条款、偏好重置、Root 授权恢复、同步提示、通知权限 | 待迁移 | 条款与权限流程保留；不得以关闭弹层绕过条款 |
| 独立 `AbkExtensionManagerActivity` 及嵌入式扩展管理 | 待页面重铺 | 扩展引导、条目、安装/删除/能力确认保持生命周期和授权边界 |
| 独立 `AbkExtensionBootstrapActivity` | 待自有 UI 审查/迁移 | 不改扩展执行或启动参数协议 |
| 独立 `ModuleWebUiActivity` 自有壳、加载/错误/工具栏 | 待迁移 | 第三方 HTML 内容不在重设计范围；桥接权限不扩大 |

## 已确认的目标 CI

截至 2026-11-21：

| 源码提交 | 标准 App | Dev App | 能证明什么 |
| --- | --- | --- | --- |
| 构建页批次 `ad1a4120` | [44901000092](https://github.com/xingguangcuican6666/ABK/actions/runs/44901000092) 成功 | [44901000054](https://github.com/xingguangcuican6666/ABK/actions/runs/44901000054) 成功 | 标准主机测试、两版 instrumentation 编译及 APK 构建 |
| 产物/刷写批次 `083de40e` | [44904030939](https://github.com/xingguangcuican6666/ABK/actions/runs/44904030939) 成功 | [44904030963](https://github.com/xingguangcuican6666/ABK/actions/runs/44904030963) 成功 | 标准测试步骤、两版 instrumentation 编译、Debug/Release APK 构建及签名验证步骤均成功；两版整条运行已结束 |
| 本地安装/修补批次 `5bbfe961` | [44906681144](https://github.com/xingguangcuican6666/ABK/actions/runs/44906681144) 成功 | [44906681048](https://github.com/xingguangcuican6666/ABK/actions/runs/44906681048) 成功 | 标准主机测试、两版 instrumentation 编译、Debug/Release APK 构建、签名验证和工件上传均成功 |

标准测试步骤运行 `:app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :abk-ui:testDebugUnitTest`。Dev 的测试步骤只编译 instrumentation，不能写成 Dev 主机测试已通过。上述步骤均未执行设备端 UI 测试。其他自动内核工作流的状态不混入 App CI 结论，也未手工触发内核构建。

运行态批次尚未获得新的目标 CI 结果；以上成功不覆盖之后的运行态修改。

## 本地检查与构建命令

目标 checkout 当前不带 `gradlew`，使用本机已准备好的 Gradle 9.3.1，或 CI 的 Gradle setup，不复制设计仓库的旧 Wrapper。SDK 以经用户审阅的安装通过命令级 `ANDROID_HOME` 提供；不要提交 `local.properties`、自动接受许可或改变全局 Java。

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk gradle :abk-ui:assembleDebug :app:assembleDebug
JAVA_HOME=/usr/lib/jvm/java-17-openjdk gradle :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :abk-ui:testDebugUnitTest
JAVA_HOME=/usr/lib/jvm/java-17-openjdk gradle :abk-ui:lintDebug :app:lintDebug
```

这里的 `gradle` 必须为 9.3.1；命令列示不代表本机已完成这些任务。本机目标 Gradle 目前在依赖解析阶段受阻，缺少 AGP 9.1.1 及 analytics protos 32.1.1 工件，先前在线获取也超时。未据此安装额外 SDK 或接受许可。

本地安装批次已完成：

- AAPT2 全 App 资源编译和临时最小 manifest 链接、Java 17 `javac --release 11` 生成资源类。该临时资源包不是可安装 App，也不覆盖正式 manifest 合并。
- 新界面及测试引用在默认中文、英文、俄文中存在；资源 key 无重复、格式化占位符一致。补齐了原本缺失的来源提示/签名失败英文和俄文。
- 受控组件和 `RootPatchUiTest` 在缓存 Kotlin 2.2.21/Compose 环境的有界源码检查通过；校验使用仅编译用的 blur/模式/类型替身，不验证正式 blur 集成、目标 Kotlin/Material3 依赖或 APK。
- 与批次起点逐字比对，执行回调、签名验证、文件暂存和 KMI 选择策略保持不变；Lucide 角色引用存在。
- `git diff --check`。临时源码、资源包、日志及检查脚本均留在仓库外，不提交。

运行态批次已完成：

- `RuntimeComponents`、纯展示 helper、真实库源码及 7 项 `RuntimeUiTest` / 4 项 `RuntimeModulePresentationTest` 在缓存 Kotlin 2.2.21/Compose 环境编译通过，使用 JVM 11；不是目标 Kotlin 2.3.21、完整宿主或 APK 构建。blur 和 `RootUtils` 枚举使用仅编译用替身，资源类复用前一本地资源检查。
- 4 项纯展示逻辑测试通过 Java/JUnitCore 实际执行，覆盖卸载能力、搜索字段、名称回退和稳定分组；7 项 UI 测试尚未设备执行，覆盖 320dp/2×字体/RTL、只读与 action 能力、确认回调、安装关闭/重启限制、独立 action 可关闭及原生权限边界。
- 与批次起点逐字比对，安装、更新下载及 SHA256 校验、文件权限/选择器、URI 暂存、确认回调和变更日志网络解析块未变。Markdown 解析器经同一 Kotlin formatter 规范化后全文相同，仅调整排版。
- 当前运行态组件及测试引用的字符串在中英俄均存在，无重复 key；补齐原缺少的英文/俄文刷新状态。Lucide 角色检查和 `git diff --check` 通过。`ClickableText` 弃用警告保留，不改原链接行为。
- 目标 Gradle 离线重试仍在根项目配置阶段失败：缺少 AGP 9.1.1 和 analytics protos 32.1.1 工件；没有执行到目标 instrumentation 编译或主机测试。临时脚本、替身、编译目录及日志均在仓库外。

## 未完成的验收

- 仍需完成表中待迁移页面，以及按真实目标依赖运行 lint 和剩余自动化回归。
- 没有使用设备或模拟器执行此轮 UI 测试；明暗主题、窄手机/平板、1×/2×字体、中文/英文/俄文、RTL、IME、旋转恢复、TalkBack 和像素截图均未完成原生验收。
- 执行中弹层的外部点击、长日志内外滚动交接、2×字体下操作区空间仍需实际渲染验收，不能用源码编译替代。
- 不执行真实设备刷写、模块安装、Root 授权或重启来“验证 UI”；相关自动化使用假状态和假回调。
- 当前交付为独立 CI 分支上的阶段性迁移，不代表全量 App 重设计完成、合并或正式发布。
