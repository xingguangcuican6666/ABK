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
| 运行态首页：状态、管理器、构建参数、功能/能力摘要 | 已重铺；两版目标 CI 成功 | 普通卡面、纵向技术字段和换行能力徽标；非空状态不等于原生管理权限，修补入口为显式操作 |
| 已安装模块：搜索/分组/开关/更新/WebUI/action/卸载 | 已重铺；两版目标 CI 成功 | 保留类型、只读、卸载及 WebUI/action 能力边界；4 项纯展示逻辑单测直接 JUnit 执行通过，标准 CI 主机测试通过 |
| 运行态模块文件权限、安装/更新/卸载确认、变更日志、执行弹层 | 已重铺；两版目标 CI 成功 | 7 项假回调 UI 测试已在目标环境编译，未在设备执行；安装进行中不可关闭，成功结束才可重启；独立 action 输出仍可关闭且不会取消后台任务 |
| Root 授权：列表、搜索、加载/刷新、应用详情与 profile | 已重铺；两版目标 CI 成功 | 详情入口与授权开关分离，纵向配置选项、外置字段和换行徽标；应用图标加载、模板、保存 payload、隐藏 flags 和既有解析回退保持原边界；6 项假回调 UI 测试已在目标环境编译，设备未执行 |
| SUSFS 控制：能力、各控制表单及执行反馈 | 已重铺；两版目标 CI 成功 | 能力条件和配置同步/解析不变，保存期间禁用编辑与操作；5 项纯配置测试实际通过，7 项假回调 UI 测试已在目标环境编译，设备未执行 |
| 设置偏好：下载目录/镜像、下载线程、清理确认、更新线路/稳定性、前台刷新间隔、语言 | 控件已重铺；两版目标 CI 成功 | 保留目录选择器与权限、失焦/IME Done 提交及宿主语言切换；标准主机测试通过，6 项假回调 UI 测试已在目标环境编译，设备未执行 |
| 设置主页：账号/退出、更新反馈、诊断导出及主要分组 | 已重铺；两版目标 CI 成功 | 账号头像仍由宿主 Coil 加载，登录/退出/Fork/Runner/下载/分享保持真实入口；7 项假回调 UI 测试已在目标环境编译，设备未执行 |
| 设置主页：签名/源码凭据安全区 | 已重铺；两版目标 CI 成功 | 独立签名/凭据忙碌条件不变，敏感草稿仅留宿主内存；8 项假回调 UI 测试已在目标环境编译，设备未执行 |
| 设置主页：能力注入管理器分组 | 已重铺；两版目标 CI 成功 | 普通卡面、独立导航/开关、外置模式选择；8 项假回调 UI 测试在两版 instrumentation 中编译，标准主机测试通过，设备未执行 |
| 设置子页：主题/背景/透明度/模糊、预测返回偏好 | 已重铺；本批目标 CI 待验证 | 普通卡面、纵向主题/颜色选项、受控开关和透明度滑杆；9 项假回调 UI 测试有界编译，设备未执行；图片选择器、权限与离页同步留在宿主 |
| 设置子页：管理器工具、内核能力、profile 模板 | 待页面重铺 | 保留能力决定的入口与预测返回 |
| 设置子页：扩展管理、关于仓库、贡献者、开源许可 | 待页面重铺 | 第三方头像、许可证及链接不替换为示例数据 |
| 设置弹层：签名密钥导入、计时安全确认、源码凭据编辑/删除 | 已重铺；两版目标 CI 成功 | 两次独立 5 秒关闭确认及重置计时不变；私钥/PAT 掩码显示，导入/凭据操作中限制编辑与关闭 |
| 设置弹层：Runner 设置 | 已重铺；两版目标 CI 成功 | 保留 fork/配置快照与保存版本隔离草稿、标签 trim、支持/忙碌门控及显式保存；13 项假回调 UI 测试在两版 instrumentation 中编译，标准主机测试通过，设备未执行 |
| 设置选择器：主题模式/颜色、下载目录 | 主题选择已在外观页重铺；下载目录使用原系统流程 | 主题/颜色为页内纵向选择，不新增弹层；系统图片和目录选择器不是 App 自有 UI，继续保留 ActivityResult 与 URI 权限流程 |
| OOBE：介绍、Root 检查、OAuth/PAT、设备码、Fork 检查/错误 | 待页面重铺 | 不因重组布局重复发起登录、授权、Fork 或同步 |
| 宿主：条款、偏好重置、Root 授权恢复、同步提示、通知权限 | 待迁移 | 条款与权限流程保留；不得以关闭弹层绕过条款 |
| 独立 `AbkExtensionManagerActivity` 及嵌入式扩展管理 | 待页面重铺 | 扩展引导、条目、安装/删除/能力确认保持生命周期和授权边界 |
| 独立 `AbkExtensionBootstrapActivity` | 待自有 UI 审查/迁移 | 不改扩展执行或启动参数协议 |
| 独立 `ModuleWebUiActivity` 自有壳、加载/错误/工具栏 | 待迁移 | 第三方 HTML 内容不在重设计范围；桥接权限不扩大 |

## 已确认的目标 CI

截至 2026-11-22：

| 源码提交 | 标准 App | Dev App | 能证明什么 |
| --- | --- | --- | --- |
| 构建页批次 `ad1a4120` | [44901000092](https://github.com/xingguangcuican6666/ABK/actions/runs/44901000092) 成功 | [44901000054](https://github.com/xingguangcuican6666/ABK/actions/runs/44901000054) 成功 | 标准主机测试、两版 instrumentation 编译及 APK 构建 |
| 产物/刷写批次 `083de40e` | [44904030939](https://github.com/xingguangcuican6666/ABK/actions/runs/44904030939) 成功 | [44904030963](https://github.com/xingguangcuican6666/ABK/actions/runs/44904030963) 成功 | 标准测试步骤、两版 instrumentation 编译、Debug/Release APK 构建及签名验证步骤均成功；两版整条运行已结束 |
| 本地安装/修补批次 `5bbfe961` | [44906681144](https://github.com/xingguangcuican6666/ABK/actions/runs/44906681144) 成功 | [44906681048](https://github.com/xingguangcuican6666/ABK/actions/runs/44906681048) 成功 | 标准主机测试、两版 instrumentation 编译、Debug/Release APK 构建、签名验证和工件上传均成功 |
| 运行态批次 `6e7a94b4` | [44910178872](https://github.com/xingguangcuican6666/ABK/actions/runs/44910178872) 成功 | [44910178821](https://github.com/xingguangcuican6666/ABK/actions/runs/44910178821) 成功 | 标准主机测试、两版完整 App 与 instrumentation 编译、Debug/Release APK 构建、签名验证和工件上传均成功 |
| Root 授权批次 `fb95a93b` | [44912344483](https://github.com/xingguangcuican6666/ABK/actions/runs/44912344483) 成功 | [44912344479](https://github.com/xingguangcuican6666/ABK/actions/runs/44912344479) 成功 | 标准主机测试、两版完整 App 与 instrumentation 编译、Debug/Release APK 构建、签名验证和工件上传均成功 |
| SUSFS 控制批次 `c2415cee` | [44915201214](https://github.com/xingguangcuican6666/ABK/actions/runs/44915201214) 成功 | [44915201208](https://github.com/xingguangcuican6666/ABK/actions/runs/44915201208) 成功 | 核对了两版提交 SHA；标准主机测试、两版 instrumentation 编译、Debug/Release APK 构建、签名验证和工件上传均成功 |
| 设置偏好批次 `498a2de8` | [44917628141](https://github.com/xingguangcuican6666/ABK/actions/runs/44917628141) 成功 | [44917628210](https://github.com/xingguangcuican6666/ABK/actions/runs/44917628210) 成功 | 核对了两版提交 SHA 与终态；标准主机测试、两版 instrumentation 编译、Debug/Release APK 构建、签名验证和工件上传均成功 |
| 设置主页批次 `658855ff` | [44920126437](https://github.com/xingguangcuican6666/ABK/actions/runs/44920126437) 成功 | [44920126445](https://github.com/xingguangcuican6666/ABK/actions/runs/44920126445) 成功 | 核对了两版提交 SHA 与 `completed/success`；标准主机测试、两版 instrumentation 编译、Debug/Release APK 构建、签名验证和工件上传均成功 |
| 设置安全区批次 `a8a5a31f` | [44922327474](https://github.com/xingguangcuican6666/ABK/actions/runs/44922327474) 成功 | [44922327504](https://github.com/xingguangcuican6666/ABK/actions/runs/44922327504) 成功 | 核对了两版提交 SHA 与 `completed/success`；标准主机测试、两版 instrumentation 编译、Debug/Release APK 构建、签名验证和工件上传均成功，不覆盖之后的管理器分组修改 |
| 设置管理器分组批次 `90504f36` | [44923756158](https://github.com/xingguangcuican6666/ABK/actions/runs/44923756158) 成功 | [44923756140](https://github.com/xingguangcuican6666/ABK/actions/runs/44923756140) 成功 | 核对了两版提交 SHA 与 `completed/success`；标准主机测试、两版 instrumentation 编译、Debug/Release APK 构建、签名验证和工件上传均成功，不覆盖之后的 Runner 弹层修改 |
| 设置 Runner 弹层批次 `ab338923` | [44928063393](https://github.com/xingguangcuican6666/ABK/actions/runs/44928063393) 成功 | [44928063452](https://github.com/xingguangcuican6666/ABK/actions/runs/44928063452) 成功 | 核对了两版完整提交 SHA 与 `completed/success`；标准主机测试、两版 instrumentation 编译、Debug/Release APK 构建、签名验证和工件上传均成功，不覆盖之后的外观设置修改 |

标准测试步骤运行 `:app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :abk-ui:testDebugUnitTest`。Dev 的测试步骤只编译 instrumentation，不能写成 Dev 主机测试已通过。上述步骤均未执行设备端 UI 测试。其他自动内核工作流的状态不混入 App CI 结论，也未手工触发内核构建。

- 上表每次 CI 结论仅覆盖对应提交，不覆盖之后的修改。外观设置本批仅完成本地有界编译和下文列明的有限主机测试，仍待目标 CI；所有设备端 UI 测试均未执行。

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
- 本机目标 Gradle 离线重试仍在根项目配置阶段失败：缺少 AGP 9.1.1 和 analytics protos 32.1.1 工件；本机没有执行到目标 instrumentation 编译或主机测试。随后两版目标 CI 已完成完整宿主及 instrumentation 编译，标准 CI 主机测试通过。临时脚本、替身、编译目录及日志均在仓库外。

Root 授权批次已完成：

- 列表卡拆开详情点击区和授权开关，保留系统应用过滤、应用名/包名/UID 搜索与列表 key。实际应用图标仍由宿主通过 PackageManager/Coil 加载，再以 slot 注入；不替换为示例图标。
- profile 的 default/template/custom 改为纵向选项，技术字段使用等宽字体与外置标签，保存期间禁用编辑及保存。既有 `rememberSaveable(app.packageName)` 草稿初始化和显式保存 payload 经同一 Kotlin formatter 规范化后全文一致，保留未显式覆盖的 flags、整数回退、列表去重及默认 SELinux context；不新增模型没有的作用域或时长配置。
- 宿主过滤、刷新副作用、生命周期及返回 gate、图标加载块与批次起点逐字一致，四个核心 ViewModel 调用各保留一次；完整宿主临时副本通过 Kotlin formatter 语法解析。此项不等于目标依赖编译。
- 全 App 资源经 AAPT2 编译并用临时最小 manifest 链接，`javac --release 11` 生成资源类；新 profile 标题及组件/测试引用在默认中文、英文、俄文均存在，key 无重复。临时资源包不是正式 manifest 合并或可安装 App。
- 真实库源码、受控授权组件和 6 项 `RootAuthorizationUiTest` 在缓存 Kotlin/Compose 2.2.21、JVM 11 环境有界编译通过；模型取自真实定义，blur、间距及 `RootUtils` 枚举使用仅编译用替身。不覆盖完整授权宿主、正式 blur/Coil 集成、目标 Kotlin/Material3 或 APK。
- 6 项假回调 UI 测试覆盖详情/授权分离、320dp/2×字体/RTL、显式保存及 flags、模板与原解析回退、非 Root 配置、保存期禁用和草稿状态恢复；尚未设备执行，不能写成 UI 测试通过。
- Lucide 角色引用与 `git diff --check` 通过；所有检查脚本、替身、资源包与日志留在仓库外。随后两版目标 CI 均完成完整编译和 APK 构建，标准主机测试通过；6 项设备端测试仍仅编译。

SUSFS 控制批次已完成以下本地检查：

- 保留草稿初始化、与已保存配置同步、能力条件和显式提交 payload，包括不显示的配置字段。迁移前后的草稿/同步/提交代码经同一 Kotlin formatter 规范化后核对；探测、执行器和 ViewModel 不变。保存期间禁用表单与动作，设置宿主的顶栏刷新也使用同一禁用条件。
- 纵向选项、外置字段标签、可选取的等宽技术值和输出、带文字的刷新提示，以及独立的状态/格式错误。AAPT2 全 App 资源编译和临时最小 manifest 链接通过；中英俄引用、唯一 key 和格式化占位符通过检查。底层解析器的原异常文本不变。
- 真实库、页面适配器、表单和测试在缓存 Kotlin/Compose 2.2.21、JVM 11 下有界编译通过。编译用 blur、间距、状态模型及槽位枚举替身，不覆盖完整设置宿主、正式 blur、目标 Kotlin/Material3、manifest 合并或 APK。
- 5 项 `SusfsFormParsingTest` 已用 JUnitCore 执行通过，检查规则读写、重复项及原有回退。7 项 `SusfsControlUiTest` 使用假状态和回调，覆盖 320dp/2×/RTL、显式操作、格式错误和修正、不支持字段、保存期禁用与配置同步；只验证了源码编译，未在设备执行。
- `git diff --check` 通过；检查脚本、替身和临时编译产物均留在仓库外。

设置偏好批次已完成以下本地检查：

- 下载目录和镜像改为外置标签、等宽输入，继续使用原草稿比较规则：编辑时不被宿主更新覆盖，失焦或 IME Done 才提交，保留空白字符且不重复提交相同的已保存值。目录选择与重置仍为显式操作，清理仍需确认；未修改下载、偏好仓库、语言工具或 ViewModel。
- 目录的 `OpenDocumentTree` 回调、持久 URI grant、不可转换提示、全文件权限设置页 fallback，以及语言持久化/通知 ViewModel/重建 Activity 回调均与批次起点核对一致。线程范围仍为 1–64、62 个中间步长，更新选项继续归一化，刷新间隔仍取仓库允许的 10/20/30 秒；语言只由宿主处理真实切换。
- 全 App 资源经 AAPT2 编译并用临时最小 manifest 链接，`javac --release 11` 生成资源类；组件/测试在中英俄的引用、唯一 key 和格式化占位符通过检查，补齐了下载线程标题及说明的俄文。临时资源包不是可安装 App。
- 真实库、偏好组件、下载目录/语言工具及 6 项 `SettingsPreferenceUiTest` 在缓存 Kotlin/Compose 2.2.21、JVM 11 下有界编译通过；槽位枚举使用仅编译替身。原有 4 项 `SettingsScreenTextFieldStateTest` 已用 JUnitCore 实际执行通过。6 项 UI 测试覆盖失焦/IME Done、320dp/2×/RTL、目录入口和显式提交、线程语义/范围/回调、受控语言选择以及取消/关闭/确认分离，未在设备执行。
- 完整设置文件的临时副本通过 Kotlin formatter 语法解析；抽取当前目录宿主、镜像及选项函数，使用真实 Android/Compose API 和库进行额外有界编译。模型 helper 和偏好间隔取自源码的有限摘录，此项不覆盖完整设置宿主、目标依赖、ActivityResult 往返或实际 IME。
- 本机重试目标 Gradle 的 instrumentation 编译与两模块主机测试，仍在根项目依赖解析阶段失败：离线缓存缺少 AGP 9.1.1 与 analytics protos 32.1.1，未执行到编译或测试任务。随后此批次两版目标 App CI 均完成完整编译和 APK 构建，标准主机测试通过；设备端测试仍未执行。
- `git diff --check` 通过；临时检查脚本、日志、替身和编译输出留在仓库外。

设置主页批次已完成以下本地检查：

- 账号、构建偏好、更新、通知、语言、外观入口、扩展入口和关于入口使用普通卡面、纵向说明及全宽操作；账号头像仍由宿主 Coil 注入，不替换为示例图标。退出只在确认后调用；更新检查、下载和诊断导出只走显式操作，加载中禁用重复点击，下载仍要求有更新及非空链接。
- 与设置偏好提交逐字比对，除主布局、退出弹层及两条导入外，设置文件其余部分保持不变，包括生命周期/返回与子页导航、安装 launcher、诊断导出/分享、目录权限、语言回调、安全组及管理器组；主页面相关 ViewModel 入口仍各保留一次。未修改 ViewModel、模型、偏好仓库、工具或工作流。
- 全 App 资源经 AAPT2 编译和临时最小 manifest 链接；组件与测试引用在中英俄均存在，key 唯一、格式化占位符一致。临时资源包不是正式 manifest 合并或可安装 App。
- 真实库、主页/偏好组件及其共 13 项 UI 测试在缓存 Kotlin/Compose 2.2.21、JVM 11 环境有界编译通过；blur 和槽位枚举使用仅编译替身。原 4 项纯文本状态测试再次由 JUnitCore 实际执行通过。新增 7 项 `SettingsMainUiTest` 覆盖 320dp/2×/RTL、账号操作分离、退出确认、更新缺链接/无更新/加载/进度、错误语义、导出和禁用入口，未在设备执行。
- 完整设置文件的临时副本通过 Kotlin formatter 语法解析；主页面宿主摘录使用真实 Android/Compose/库 API、账号模型及源码字段类型完成额外有界编译。Coil、ViewModel、未改动的其他分组和产物大小模型仅为编译用替身，不证明完整设置宿主、正式 Coil/blur、目标依赖或 APK 已通过。
- 本机目标 Gradle 重试仍因缺少 AGP 9.1.1 和 analytics protos 32.1.1 缓存失败，未执行到 instrumentation 编译或主机测试；随后本批次两版目标 App CI 均完成完整编译和 APK 构建，标准主机测试通过。`git diff --check` 通过，临时脚本、日志、替身和产物均留仓库外。

设置安全区批次已完成以下本地检查：

- 签名开关、导入/重置和私仓源码凭据使用普通卡面、纵向说明及全宽操作，签名与凭据继续采用各自独立的忙碌条件。密钥与 PAT 草稿仍由宿主用 `remember` 持有，不新增 `rememberSaveable`、本地持久化、日志或自动上传。
- 与设置主页提交逐字比对，安全组前的文件选择器、异步读取、草稿初始化、Fork 条件和状态刷新，以及导入结果分支、成功清空、两次关闭确认链、重置确认和 `readTextFromUri` 均不变；其余设置宿主源码逐字相同。PAT 保存仍传入未裁剪值，然后清空草稿并关闭；删除仍由原 ViewModel 执行。未修改模型、ViewModel、偏好仓库、加密/签名实现或工作流。
- 计时确认仍使用原 `remember` / `LaunchedEffect(Unit)` / 每秒 `delay`，两阶段各自等待默认 5 秒；取消与关闭不确认。计时文字新增中英俄格式化资源。私钥新增密码变换、PAT 保持密码变换，不改变真实导入和提交内容；导入中禁用字段、选择器、确认与取消，并通过库 `dismissible` 同时限制关闭按钮、返回及外部点击；凭据忙碌时也限制编辑、保存/删除和关闭。
- 全 App 资源 AAPT2 编译、临时最小 manifest 链接和 `javac --release 11` 资源类生成通过。新组件/测试及安全宿主引用的中英俄资源完整，key 唯一、格式化占位符一致；补齐既有英文/俄文安全提示和只写 Secret/细粒度只读 PAT 说明。临时资源包不是可安装 App。
- 真实库、安全/主页/偏好组件及共 21 项 UI 测试在缓存 Kotlin/Compose 2.2.21、JVM 11 环境有界编译通过，blur 和槽位枚举仅为编译替身；原 4 项纯文本状态测试再次用 JUnitCore 执行通过。新增 8 项 `SettingsSecurityUiTest` 覆盖无 Fork、签名开关和独立忙碌限制、两次计时及重开、掩码、选择器、未裁剪提交、取消/关闭/返回和删除确认，仅编译而未设备执行；弹层使用 2×字体/RTL，卡片额外使用 320dp 宽度。
- 完整设置临时副本通过 Kotlin formatter 语法解析。安全宿主摘录另用真实 Android/Compose/库 API、账号及 `Result` 模型、源码宿主字段类型完成有界编译；ViewModel 为仅编译替身，不覆盖真实后端执行、完整设置宿主、目标依赖或 ActivityResult 往返。
- 本批次本机目标 Gradle 离线重试仍在根项目配置阶段失败，缺少 AGP 9.1.1 和 analytics protos 32.1.1 缓存，未执行到编译或测试；随后两版目标 App CI 均完成完整编译和 APK 构建，标准主机测试通过。8 项 UI 测试仍未在设备执行。Lucide 角色与 `git diff --check` 通过；检查脚本、替身、资源包和日志留在仓库外。

设置管理器分组批次已完成以下本地检查：

- 能力注入分组改用普通卡面、纵向说明、全宽导航与重试操作，开关使用受控库组件，模式使用外置标签的 `AbkSelect`。错误带图标、可见文本和错误语义，加载及单项执行提供进度语义；不把后端传来的非空条目当作可操作权限，继续采用条目 `enabled` 与原单项忙碌条件。
- 与安全区提交逐字比对，仅管理器分组、移出的模式呈现与旧图标映射及两条 import 改变；安全区、其他子页、ActivityResult 和生命周期保持不变。空状态隐藏条件、交互刷新 helper 与启动顺序、五个目的地 ID 到真实导航的映射、强制刷新和两个设置回调保持一致，未改模型、ViewModel、Root/管理器实现或工作流。
- 模式仍按原规则 trim、过滤空项、把选中索引限制在有效范围、空选项不可操作、重复选择不提交。选项 ID 使用归一化后的索引而非文字，重复标签不会冲突；选择仅传宿主原索引，不持久化偏好、不自动调用 Root 或刷新。
- 全 App 资源经 AAPT2 编译、临时最小 manifest 链接和 `javac --release 11` 资源类生成；组件/测试及安全宿主的中英俄引用、唯一 key 和占位符检查通过。本批未增加文案或改资源；临时资源包不是可安装 App。
- 真实库、四组设置组件及共 29 项 UI 测试在缓存 Kotlin/Compose 2.2.21、JVM 11 下有界编译通过，blur 和槽位枚举使用仅编译替身；原 4 项纯文本状态单测再次用 JUnitCore 实际执行通过。新增 8 项 `SettingsManagerUiTest` 使用 320dp/2×/RTL 和假回调，覆盖五个导航 ID、无权限与逐项忙碌限制、受控开关、模式归一化/索引/重复标签/同项不提交/宿主更新、菜单返回、刷新隐藏、错误重试和初次加载语义；尚未在设备执行。
- 完整设置临时副本通过 Kotlin formatter 语法解析；管理器宿主摘录以真实 Android/Compose/库 API、管理器模型、原刷新 helper 摘录及源码宿主字段类型完成额外有界编译。ViewModel 仅为编译替身，不证明完整设置宿主、真实 Root 行为、目标依赖或原生菜单布局已通过。
- 本批次本机目标 Gradle 离线重试仍在根项目依赖解析阶段失败，缺少 AGP 9.1.1 和 analytics protos 32.1.1 缓存，未执行到 instrumentation 编译或主机测试；两版目标 CI 均完成完整编译和 APK 构建，标准主机测试通过。8 项 UI 测试仍未在设备执行。Lucide 角色与 `git diff --check` 通过；临时检查脚本、日志、替身及产物留在仓库外。

设置 Runner 弹层批次已完成以下本地检查：

- 弹层改用库 Dialog、受控开关和外置标签字段，技术文本使用等宽字体，保存使用全宽操作，错误带图标、文字及错误语义，加载/保存提供文字和进度语义。GKI 与 OnePlus 独立呈现；忙碌时禁止编辑、保存和刷新，但保持原本可关闭的行为，关闭不会取消宿主请求。
- 全 App 资源经 AAPT2 编译、临时最小 manifest 链接和 `javac --release 11` 资源类生成；Runner 与既有设置组件/测试引用的中英俄资源、唯一 key 和格式化占位符检查通过。本批没有修改字符串资源；临时资源包不是正式 manifest 合并或可安装 App。
- 真实库、设置安全/主页/偏好/管理器组件及 13 项 Runner UI 测试与既有 29 项设置 UI 测试，在缓存 Kotlin/Compose 2.2.21、JVM 11 下有界源码编译通过；blur 与 Root 槽位类型仅为编译替身。4 项原设置文本状态测试由 JUnitCore 实际执行通过。13 项 `KernelRunnerSettingsUiTest` 使用 2× 字体、RTL 和假回调，独立 editor 使用 320dp 宽度；覆盖无 Fork、context 刷新、加载/错误、支持与忙碌门控、显式 trim 保存、关闭 self-hosted 后保留标签及空草稿保存、revision/fork/config 草稿重置、双目标隔离、宿主清除快照及忙碌时关闭。UI 测试只编译，尚未设备执行；假回调不证明远端变量写入行为。
- Runner 草稿生命周期、工作流目标映射、显式保存 payload 和原关闭接线与管理器批次 `90504f36` 核对一致；协调器真实源码、模型、Result 与既有 7 项 `KernelRunnerSettingsCoordinatorTest` 在有限 JVM 环境编译并由 JUnitCore 执行通过。GitHub repository base 为仅编译签名替身，没有读取/写入远端变量；此项不等于完整 App Gradle 主机测试，也未本地执行依赖 MockWebServer 的 store 测试。
- 目标宿主 wiring、SettingsScreen、ViewModel、协调器、store、模型、偏好仓库及工作流与 Runner 批次起点无差异，repository variable 网络协议未改变。Lucide 角色与 `git diff --check` 通过。完整设置宿主在 Runner 本地检查中未重新做 formatter 或目标依赖编译；本机离线目标 Gradle 因缺少 AGP 9.1.1 和 analytics protos 32.1.1 在根配置阶段失败，未执行 Gradle instrumentation/主机测试。随后 `ab338923` 两版目标 CI 均完成完整编译和 APK 构建，标准主机测试通过，13 项 UI 测试仍未在设备执行。临时脚本、替身、资源包及日志留在仓库外。

设置外观子页批次已完成以下本地检查：

- 主题模式、动态色、自定义主色/强调色、预测返回、背景图及模糊偏好改用普通卡面、纵向选项、受控开关和全宽显式操作。颜色选项显示本地化名称、勾选及 radio 语义，选项行至少 48dp；非预设当前色仍只展示，不自动提交或增加任意颜色编辑功能。原六组预设、忽略 alpha 的匹配规则和色块前景对比规则保持不变。
- 与 Runner 提交 `ab338923` 核对，外观范围之外的设置宿主逐字一致（移除仅用于原外观组件的五条 import 除外）；真实图片选择器、持久 URI 读取权限回调及 `DisposableEffect` 离页同步代码保持原样。动态色关闭仍提交当前主题两角色快照，开启提交 null；修改一种自定义色保留另一种。透明度保持本地拖动状态、每次预览与结束后保存分离，离页仅同步原宿主预览，不新增持久化副作用。
- 全 App 资源经 AAPT2 编译、临时最小 manifest 链接及 `javac --release 11` 资源类生成；新组件/测试引用在中英俄均存在，key 唯一、格式化占位符一致。本批未修改字符串资源；临时资源包不是正式 manifest 合并或可安装 App。
- 真实库、外观组件、真实外观宿主摘录和新增 9 项 `SettingsAppearanceUiTest` 连同既有 42 项设置 UI 测试，在缓存 Kotlin/Compose 2.2.21、JVM 11 下有界编译通过。blur、Root 槽位类型及页面间距使用仅编译替身，未覆盖完整设置宿主、正式 blur、目标 Kotlin/Material3、ActivityResult 往返或 APK；完整设置临时副本通过 Kotlin formatter 语法解析。
- 9 项假回调 UI 测试使用 320dp/2×字体/RTL，覆盖宿主主题选择、动态色支持及快照、自定义色搭配和 alpha 匹配、图片/模糊/预测返回门控、显式选择/移除、透明度范围及预览/提交次序、外观宿主离页同步及重开无自动操作。仅验证源码编译，未在设备执行。原 4 项设置文本状态测试通过 JUnitCore 实际执行。
- ViewModel、偏好仓库、主题/背景/模糊实现、Runner 和工作流与批次起点无差异，Lucide 角色及 `git diff --check` 通过。本机已知目标 Gradle 依赖缓存阻断未解决，本批没有把有界检查当成目标 Gradle 构建；目标 CI 待验证。检查脚本、替身、日志及产物均留在仓库外。

## 未完成的验收

- 仍需完成表中待迁移页面，以及按真实目标依赖运行 lint 和剩余自动化回归。
- 没有使用设备或模拟器执行此轮 UI 测试；明暗主题、窄手机/平板、1×/2×字体、中文/英文/俄文、RTL、IME、旋转恢复、TalkBack 和像素截图均未完成原生验收。
- 执行中弹层的外部点击、长日志内外滚动交接、2×字体下操作区空间仍需实际渲染验收，不能用源码编译替代。
- 不执行真实设备刷写、模块安装、Root 授权或重启来“验证 UI”；相关自动化使用假状态和假回调。
- 当前交付为独立 CI 分支上的阶段性迁移，不代表全量 App 重设计完成、合并或正式发布。
