import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

import '../models/build_models.dart';
import '../models/sidecar_models.dart';
import '../state/dashboard_controller.dart';

enum AppLocale { zhCn, en }

class AppStrings {
  const AppStrings._(this._locale);

  final AppLocale _locale;

  static const LocalizationsDelegate<AppStrings> delegate =
      _AppStringsDelegate();

  static const supportedLocales = <Locale>[Locale('zh', 'CN'), Locale('en')];

  static AppStrings of(BuildContext context) {
    final strings = Localizations.of<AppStrings>(context, AppStrings);
    assert(strings != null, 'AppStrings is not available in the widget tree.');
    return strings!;
  }

  bool get isChinese => _locale == AppLocale.zhCn;

  String get appTitle => isChinese ? 'ABK 桌面端' : 'ABK Desktop';
  String get brandWordmark => 'ABK';
  String get shellCompactSubtitle =>
      isChinese ? 'Linux 桌面客户端' : 'Linux desktop client';
  String get navHome => isChinese ? '主页' : 'Home';
  String get navDetection => isChinese ? '应用探测' : 'Application Detection';
  String get navDevice => isChinese ? '设备' : 'Device';
  String get navSettings => isChinese ? '设置' : 'Settings';
  String get openSidebar => isChinese ? '展开侧栏' : 'Expand sidebar';
  String get collapseSidebar => isChinese ? '收起侧栏' : 'Collapse sidebar';
  String get refreshPipeline => isChinese ? '刷新连接流水线' : 'Refresh pipeline';
  String get refreshing => isChinese ? '刷新中' : 'Refreshing';
  String get refreshDevices => isChinese ? '刷新设备' : 'Refresh devices';
  String get scanning => isChinese ? '扫描中' : 'Scanning';
  String get disconnect => isChinese ? '断开连接' : 'Disconnect';
  String get openDetectionPage => isChinese ? '打开探测页' : 'Open detection page';
  String get currentSelected => isChinese ? '当前已选设备' : 'Currently selected';

  String get homeTitle => isChinese ? '主页' : 'Home';
  String get homeIntro => isChinese
      ? '桌面端继续沿用 ABK 的产品语言，但信息组织要更适合宽屏和排障场景。'
      : 'Keep the ABK product language, but reorganize the desktop surface for wide-screen diagnostics.';
  String get homeNarrativeTitle => isChinese ? '连接路径' : 'Connection narrative';
  String get homeNarrativeSubtitle => isChinese
      ? '桌面端需要把探测、ADB、ABK 协议提升这条链路讲清楚，而不是把状态藏在报错里。'
      : 'The desktop should make the handshake path explicit instead of hiding it in transient errors.';

  String get detectionTitle => isChinese ? '应用探测' : 'Application Detection';
  String get detectionIntro => isChinese
      ? 'ADB 是底线可观测层。桌面端先确认设备可见，再尝试把单一可用设备提升到 ABK 私有协议。'
      : 'ADB is the baseline observability layer. The desktop verifies device visibility first, then attempts to promote one ready device into ABK.';
  String get detectionSummaryTitle => isChinese ? '探测摘要' : 'Detection summary';
  String get detectionSummarySubtitle => isChinese
      ? '这一页只做发现和连接，不承载完整运行时管理。'
      : 'This page stays focused on discovery and connection, not full runtime management.';
  String get noDetectedDevicesTitle =>
      isChinese ? '没有探测到设备' : 'No detected devices';
  String get noDetectedDevicesSubtitle => isChinese
      ? '连接打开 ADB 的设备后再刷新。主页上的自动连接流水线也会继续重试。'
      : 'Connect a device with ADB enabled, then refresh this page. The startup pipeline on home will retry.';
  String get nothingVisibleOverAdb =>
      isChinese ? '当前还没有任何设备通过 ADB 可见。' : 'Nothing is visible over ADB yet.';
  String get deviceEligibleForAbk => isChinese
      ? '这个设备已经满足发起 ABK 协议提升尝试的条件。'
      : 'This device is eligible for the ABK promotion attempt.';
  String get deviceNotReadyForAbk => isChinese
      ? 'ADB 已经看到这个设备，但它还没准备好进入 ABK 握手。'
      : 'ADB sees the device, but it is not ready for an ABK handshake yet.';
  String get connectThisDevice => isChinese ? '连接这个设备' : 'Connect this device';
  String get reconnectThisDevice =>
      isChinese ? '重新连接这个设备' : 'Reconnect this device';
  String get noExtraAdbDetail =>
      isChinese ? '没有更多 ADB 细节' : 'No extra ADB detail';

  String get errorCardTitle =>
      isChinese ? '当前为什么没有进入 ABK 模式' : 'Why you are not in ABK mode';
  String get errorCardSubtitle => isChinese
      ? '桌面端必须把降级状态说清楚，让用户知道自己正处在哪一层能力上。'
      : 'The desktop should keep fallback explicit so the user understands the current capability tier.';

  String get metricMode => isChinese ? '连接模式' : 'Mode';
  String get metricReadyDevices => isChinese ? '可用设备' : 'Ready devices';
  String get metricProtocol => isChinese ? '协议版本' : 'Protocol';
  String get metricTargetPort => isChinese ? '目标端口' : 'Target port';
  String get unknownValue => isChinese ? '未知' : 'Unknown';

  String get timelineDesktopSidecar =>
      isChinese ? '1. 桌面桥接服务' : '1. Desktop sidecar';
  String get timelineAdbDetection =>
      isChinese ? '2. ADB 探测' : '2. ADB detection';
  String get timelineAbkHandshake =>
      isChinese ? '3. ABK 握手' : '3. ABK handshake';
  String sidecarResponding(String host, int port) =>
      isChinese ? '桥接服务正在响应于 $host:$port' : 'Responding on $host:$port';
  String get sidecarNotResponding =>
      isChinese ? '桥接服务当前还没有响应' : 'Not responding yet';
  String readyDeviceCount(int count) =>
      isChinese ? '$count 台设备可用' : '$count ready device(s)';
  String get noVisibleDevices => isChinese ? '当前没有可见设备' : 'No visible devices';
  String sidecarAddress(String host, int port) =>
      isChinese ? '桥接服务：$host:$port' : 'Sidecar: $host:$port';

  String get detectTotalLabel => isChinese ? '已探测设备' : 'Detected';
  String get readyLabel => isChinese ? '可用设备' : 'Ready';
  String get readyStateLabel => isChinese ? '可用' : 'Ready';

  String deviceStatusLabel(String status) {
    final normalized = status.trim().toLowerCase();
    return switch (normalized) {
      'device' => readyStateLabel,
      'offline' => isChinese ? '离线' : 'offline',
      'unauthorized' => isChinese ? '未授权' : 'unauthorized',
      'recovery' => isChinese ? '恢复模式' : 'recovery',
      'sideload' => 'sideload',
      'no permissions' => isChinese ? '无权限' : 'no permissions',
      _ => status,
    };
  }

  String connectionModeLabel(DeviceConnectionMode mode) {
    return switch (mode) {
      DeviceConnectionMode.abk =>
        isChinese ? '已连接 ABK 协议' : 'Connected over ABK',
      DeviceConnectionMode.adbFallback =>
        isChinese ? 'ADB 降级模式' : 'ADB fallback mode',
      DeviceConnectionMode.disconnected => isChinese ? '空闲' : 'Idle',
    };
  }

  String connectionStatusLabel(ConnectionFlow flow) {
    return switch (flow) {
      ConnectionFlow.connectedAbk => isChinese ? 'ABK 已在线' : 'ABK active',
      ConnectionFlow.connectedAdbFallback =>
        isChinese ? 'ADB 降级中' : 'ADB fallback',
      ConnectionFlow.sidecarUnavailable =>
        isChinese ? '桥接服务离线' : 'Sidecar offline',
      ConnectionFlow.connecting => isChinese ? '连接中' : 'Connecting',
      ConnectionFlow.detecting => isChinese ? '扫描中' : 'Scanning',
      ConnectionFlow.failed => isChinese ? '需要处理' : 'Needs attention',
      ConnectionFlow.idle => isChinese ? '待扫描' : 'Ready to scan',
    };
  }

  String heroHeadline(ConnectionFlow flow) {
    return switch (flow) {
      ConnectionFlow.connectedAbk =>
        isChinese ? '已进入 ABK 协议' : 'Connected over ABK',
      ConnectionFlow.connectedAdbFallback =>
        isChinese ? 'ABK 握手失败' : 'ABK handshake failed',
      ConnectionFlow.connecting =>
        isChinese ? '正在连接设备' : 'Connecting to device',
      ConnectionFlow.detecting =>
        isChinese ? '正在扫描 ADB 设备' : 'Scanning for ADB devices',
      ConnectionFlow.sidecarUnavailable =>
        isChinese ? '桌面桥接服务当前不可用' : 'Desktop sidecar unavailable',
      ConnectionFlow.failed => isChinese ? '需要手动处理' : 'Manual action required',
      ConnectionFlow.idle => isChinese ? '准备开始连接' : 'Ready to connect',
    };
  }

  String heroSubtitle(ConnectionFlow flow) {
    return switch (flow) {
      ConnectionFlow.connectedAbk =>
        isChinese
            ? '手机侧代理已经健康，桌面端现在可以把这台设备当作一等 ABK 端点处理。'
            : 'The phone agent is healthy, so the desktop can now treat this device as a first-class ABK endpoint.',
      ConnectionFlow.connectedAdbFallback =>
        isChinese
            ? 'ADB 仍然看得到设备，但桌面端没能把会话提升到 ABK 私有协议。'
            : 'ADB still sees the device, but the desktop could not lift the session into ABK mode.',
      ConnectionFlow.connecting =>
        isChinese
            ? 'Sidecar 正在转发 ADB，并等待手机代理进入健康状态。'
            : 'The sidecar is forwarding ADB and waiting for the phone agent to become healthy.',
      ConnectionFlow.detecting =>
        isChinese
            ? '这一轮会先刷新 ADB 可见性，再决定是否发起 ABK 协议升级。'
            : 'This pass refreshes ADB visibility before deciding whether to attempt the ABK promotion.',
      ConnectionFlow.sidecarUnavailable =>
        isChinese
            ? '请先启动 Rust 桥接服务，然后重新运行桌面端连接流水线。'
            : 'Start the Rust sidecar first, then rerun the desktop pipeline.',
      ConnectionFlow.failed =>
        isChinese
            ? '请先解决当前探测歧义，再重新尝试连接。'
            : 'Resolve the current detection ambiguity, then retry the connection.',
      ConnectionFlow.idle =>
        isChinese
            ? '桌面端会保持轻量，直到出现值得提升到 ABK 的设备。'
            : 'The app stays shallow until there is a device worth promoting into ABK.',
    };
  }

  String heroPrimaryAction(DeviceConnectionMode mode) {
    return switch (mode) {
      DeviceConnectionMode.abk => isChinese ? '重新执行握手' : 'Re-run handshake',
      DeviceConnectionMode.adbFallback =>
        isChinese ? '重试 ABK 握手' : 'Retry ABK handshake',
      DeviceConnectionMode.disconnected =>
        isChinese ? '启动连接流水线' : 'Start pipeline',
    };
  }

  String sidecarAvailabilityLabel(bool available) {
    return available
        ? (isChinese ? '桥接服务已就绪' : 'Sidecar ready')
        : (isChinese ? '桥接服务缺失' : 'Sidecar missing');
  }

  String sidecarAvailabilityDescription(bool available) {
    return available
        ? (isChinese ? '本地桥接服务正在响应。' : 'The local sidecar is responding.')
        : (isChinese
              ? '当前桌面壳没有连上本地 Rust 桥接服务。'
              : 'The Flutter shell is not currently connected to the local Rust sidecar.');
  }

  String selectedDeviceHeadline(String? serial) {
    if (serial != null && serial.isNotEmpty) {
      return serial;
    }
    return isChinese ? '还没有选中设备' : 'No device selected';
  }

  String detectionErrorSummary({
    required ConnectionFlow flow,
    required int readyDeviceCount,
    String? rawError,
  }) {
    if (flow == ConnectionFlow.connectedAdbFallback) {
      return isChinese
          ? 'ABK 协议握手失败，但 ADB 仍然可见。这台设备现在处于降级模式。'
          : 'The ABK handshake failed, but ADB is still visible. The device is now in fallback mode.';
    }
    if (flow == ConnectionFlow.sidecarUnavailable) {
      return isChinese
          ? '当前没有可连接的桌面桥接服务。请先启动 Rust 进程。'
          : 'The desktop sidecar is not available yet. Start the Rust process first.';
    }
    if (flow == ConnectionFlow.failed && readyDeviceCount > 1) {
      return isChinese
          ? '探测到了多台可用设备。请先在探测页中明确选择要连接的序列号。'
          : 'Multiple ready ADB devices were detected. Pick the intended serial on the detection page.';
    }
    if (rawError != null && rawError.isNotEmpty) {
      return rawError;
    }
    return nothingVisibleOverAdb;
  }

  String get buildTitle => isChinese ? '构建' : 'Build';
  String get buildIntro => isChinese
      ? '用表单配置并触发 GKI 构建，桌面端会把 GitHub 登录、fork 状态、构建任务和产物下载串成一个闭环。'
      : 'Use a form to configure and trigger GKI builds. The desktop keeps GitHub login, fork state, build tasks, and artifact downloads in one flow.';
  String get buildErrorTitle => isChinese ? '构建状态异常' : 'Build status issue';
  String get buildAuthTitle =>
      isChinese ? 'GitHub 认证' : 'GitHub authentication';
  String get buildAuthSubtitle => isChinese
      ? '先完成登录和 fork 状态检查，再提交构建。'
      : 'Finish login and fork checks before submitting a build.';
  String get buildLogin => isChinese ? '登录 GitHub' : 'Log in to GitHub';
  String get buildLoginPolling => isChinese ? '正在验证登录' : 'Verifying login';
  String get buildLoginOpenBrowser =>
      isChinese ? '打开浏览器并输入验证码' : 'Open browser and enter the code';
  String get buildLoginCodeTitle => isChinese ? '设备验证码' : 'Device code';
  String get buildLoginCodeHint => isChinese
      ? '如果浏览器没有自动带上验证码，请手动复制下面这串 code 到 GitHub。'
      : 'If the browser does not autofill the code, copy the value below into GitHub manually.';
  String get buildLoginCopyCode => isChinese ? '复制验证码' : 'Copy code';
  String get buildLoginCopied =>
      isChinese ? '验证码已复制到剪贴板' : 'Device code copied';
  String get buildLoginOpenGitHub =>
      isChinese ? '打开 GitHub 验证页' : 'Open GitHub verification';
  String get buildSessionRestoring =>
      isChinese ? '正在恢复 GitHub 登录态' : 'Restoring GitHub session';
  String get buildForkEnsure => isChinese ? '创建 fork' : 'Ensure fork';
  String get buildForkSync => isChinese ? '同步 fork' : 'Sync fork';
  String get buildRefreshAll => isChinese ? '刷新全部' : 'Refresh all';
  String get buildRefreshRuns => isChinese ? '刷新构建列表' : 'Refresh builds';
  String get buildTabRemote => isChinese ? '远程 CI 编译' : 'Remote CI build';
  String get buildTabLocal => isChinese ? '本地编译' : 'Local build';
  String get buildLocalRefresh => isChinese ? '刷新本地状态' : 'Refresh local status';
  String get buildLocalInitAction =>
      isChinese ? '初始化 AOSP 源码' : 'Initialize AOSP sources';
  String get buildLocalRebuildAction =>
      isChinese ? '开始本地编译' : 'Start local build';
  String get buildLocalSourceTitle =>
      isChinese ? 'Backend、源码与工作区' : 'Backend, sources, and workspace';
  String get buildLocalSourceSubtitle => isChinese
      ? '这里统一管理 backend 选择、支持线、source instance 和当前 materialized 工作区。'
      : 'Manage backend selection, supported kernel lines, source instances, and the current materialized working tree here.';
  String get buildLocalScriptsReady =>
      isChinese ? 'Backend 可用' : 'Backend available';
  String get buildLocalScriptsMissing =>
      isChinese ? 'Backend 不可用' : 'Backend unavailable';
  String get buildLocalWorkspaceReady =>
      isChinese ? '工作区已初始化' : 'Workspace ready';
  String get buildLocalWorkspaceMissing =>
      isChinese ? '工作区未初始化' : 'Workspace not initialized';
  String get buildLocalSupportedLinesTitle =>
      isChinese ? '可用 AOSP 内核线' : 'Supported AOSP lines';
  String get buildLocalNoSupportedTemplates => isChinese
      ? '当前没有发现可用的 AOSP 模板目录。'
      : 'No supported AOSP template directories were found.';
  String get buildLocalSelectionUnsupported => isChinese
      ? '当前表单里的 Android / kernel 组合没有对应的本地 AOSP 模板，请先选择上方支持的内核线。'
      : 'The current Android / kernel selection does not match any local AOSP template. Pick one of the supported lines above first.';
  String get buildLocalBranchMonthLabel =>
      isChinese ? 'AOSP 分支月份' : 'AOSP branch month';
  String get buildLocalBranchMonthHint => isChinese
      ? '格式为 YYYY-MM，例如 2026-07。这里用于 `common-a14-6.1-YYYY-MM` 这类 AOSP manifest 分支。'
      : 'Use YYYY-MM, for example 2026-07. This is used for AOSP manifest branches such as `common-a14-6.1-YYYY-MM`.';
  String get buildLocalBranchMonthRequired => isChinese
      ? '本地源码初始化需要填写分支月份。'
      : 'A branch month is required before local source initialization.';
  String get buildLocalForceInit =>
      isChinese ? '强制重建环境' : 'Force recreate environment';
  String get buildLocalForceInitSubtitle => isChinese
      ? '重新 materialize 当前 source instance，并覆盖已有 working tree。'
      : 'Re-materialize the current source instance and replace the existing working tree.';
  String get buildLocalSkipDeps =>
      isChinese ? '跳过依赖同步' : 'Skip dependency sync';
  String get buildLocalSkipDepsSubtitle => isChinese
      ? '只初始化 AOSP 模板与工作区，不更新 AnyKernel3、patches、SUSFS 等依赖仓库。'
      : 'Initialize only the AOSP template and workspace without updating AnyKernel3, patches, SUSFS, and other dependency repositories.';
  String get buildLocalDirectoriesTitle =>
      isChinese ? '本地目录设置' : 'Local directory settings';
  String get buildLocalDirectoriesSubtitle => isChinese
      ? '可在这里改模板脚本目录、工作区目录和 profile 存储目录。留空时回退到默认路径。'
      : 'Override the template/script root, workspace directory, and profile storage directory here. Leave a field empty to use the default path.';
  String get buildLocalGlobalBackendLabel =>
      isChinese ? '全局默认 backend' : 'Global default backend';
  String get buildLocalBackendIssuesTitle =>
      isChinese ? 'Backend 问题与操作' : 'Backend issues and actions';
  String get buildLocalBackendInstallAction =>
      isChinese ? '安装 backend 资产' : 'Install backend asset';
  String buildLocalBackendInstallQueued(String label) => isChinese
      ? '$label 安装任务已加入队列。'
      : '$label install task was queued.';
  String get buildLocalScriptRootLabel =>
      isChinese ? '模板 / 脚本目录' : 'Template / script root';
  String get buildLocalWorkspaceDirSettingLabel =>
      isChinese ? '工作区目录' : 'Workspace directory';
  String get buildLocalProfileStoreDirLabel =>
      isChinese ? 'Profile 存储目录' : 'Profile storage directory';
  String get buildLocalSaveDirectories =>
      isChinese ? '保存目录设置' : 'Save directory settings';
  String get buildLocalRestoreDirectories =>
      isChinese ? '恢复默认' : 'Restore defaults';
  String get buildLocalDirectorySettingsSaved =>
      isChinese ? '本地目录设置已保存。' : 'The local directory settings were saved.';
  String get buildLocalTemplateLabel => isChinese ? '当前模板' : 'Current template';
  String get buildLocalBranchLabel => isChinese ? '模板分支' : 'Template branch';
  String get buildLocalWorkspaceLabel => isChinese ? '工作区路径' : 'Workspace path';
  String get buildLocalOpenWorkspace => isChinese ? '打开工作区' : 'Open workspace';
  String get buildLocalOpenArtifacts => isChinese ? '打开产物目录' : 'Open artifacts';
  String get buildLocalOpenLogs => isChinese ? '打开日志目录' : 'Open logs';
  String get buildLocalAddSourceInstance =>
      isChinese ? '添加源码实例' : 'Add source instance';
  String get buildLocalFormTitle =>
      isChinese ? '本地构建参数' : 'Local build parameters';
  String get buildLocalFormSubtitle => isChinese
      ? '这里沿用远程构建的大部分参数，但源码线与 patch 月份由上面的本地工作区卡片负责。'
      : 'This reuses most remote build parameters, while the source line and patch month are managed by the local workspace card above.';
  String get buildLocalActionTitle =>
      isChinese ? '本地编译动作' : 'Local build actions';
  String get buildLocalActionSubtitle => isChinese
      ? '在这里选择 profile、backend 覆盖和构建动作，桌面端会把这些编排成统一的本地任务。'
      : 'Choose the profile, backend override, and build actions here. The desktop orchestrates them as unified local tasks.';
  String get buildLocalCleanOut =>
      isChinese ? '编译前清空输出' : 'Clean outputs before build';
  String get buildLocalCleanOutSubtitle => isChinese
      ? '先清理 out / bazel 产物，再开始本地构建。'
      : 'Clean out and bazel artifacts before starting the local build.';
  String get buildLocalReseed => isChinese ? '按模板重播源码' : 'Reseed from template';
  String get buildLocalReseedSubtitle => isChinese
      ? '把工作区重新同步回模板基线，再套用 ABK 构建流程。'
      : 'Resync the workspace back to the template baseline before applying the ABK build flow.';
  String get buildLocalNoPackage =>
      isChinese ? '仅编译，不打包' : 'Compile only, skip packaging';
  String get buildLocalNoPackageSubtitle => isChinese
      ? '跳过 AnyKernel3 和 boot image 打包，只停在内核编译阶段。'
      : 'Skip AnyKernel3 and boot image packaging, and stop at kernel compilation only.';
  String get buildLocalLatestLogLabel => isChinese ? '最近日志' : 'Latest log';
  String get buildLocalQueueTitle => isChinese ? '本地构建任务' : 'Local build tasks';
  String get buildLocalQueueSubtitle => isChinese
      ? '这里只展示本地源码初始化和本地重建任务，不混入 GitHub workflow。'
      : 'This list shows only local source initialization and local rebuild tasks, without mixing in GitHub workflows.';
  String get buildLocalTaskScope => isChinese ? '本地工作区' : 'Local workspace';
  String get buildLocalInitQueued => isChinese
      ? '本地源码初始化任务已加入队列。'
      : 'The local source initialization task was queued.';
  String get buildLocalRebuildQueued =>
      isChinese ? '本地重建任务已加入队列。' : 'The local rebuild task was queued.';
  String get buildLocalActivityTitle =>
      isChinese ? '本地任务进行中' : 'Local task in progress';
  String get buildLocalActivitySubtitle => isChinese
      ? '任务提交后，这里会立刻显示当前步骤，并随着轮询自动刷新。'
      : 'After a local task is submitted, this area updates immediately and refreshes with polling.';
  String get buildLocalInitRunningAction =>
      isChinese ? '正在初始化源码' : 'Initializing sources';
  String get buildLocalBuildRunningAction =>
      isChinese ? '正在执行本地编译' : 'Running local build';
  String get buildLocalRefreshRunningAction =>
      isChinese ? '正在刷新本地状态' : 'Refreshing local status';
  String get buildLocalAuthorizationTitle =>
      isChinese ? '请求本地提权' : 'Request local elevation';
  String get buildLocalAuthorizationSubtitle => isChinese
      ? '当前 backend 需要 sudo 才能访问容器引擎。输入本机密码后，这次任务会以提权方式执行。'
      : 'The current backend needs sudo to access the container engine. Enter your local password to elevate this task.';
  String get buildLocalAuthorizationPasswordLabel =>
      isChinese ? '本机 sudo 密码' : 'Local sudo password';
  String get buildLocalAuthorizationAction =>
      isChinese ? '授权并继续' : 'Authorize and continue';
  String get buildTargetTitle => isChinese ? '构建目标' : 'Build target';
  String get buildKsuTitle => isChinese ? 'KernelSU' : 'KernelSU';
  String get buildVersionTitle => isChinese ? '版本信息' : 'Version info';
  String get buildKsuBranchLabel => isChinese ? 'KSU 分支' : 'KSU branch';
  String get buildRevisionLabel => isChinese ? '修订版本' : 'Revision';
  String get buildAndroidVersionLabel =>
      isChinese ? 'Android 版本' : 'Android version';
  String get buildKernelVersionLabel => isChinese ? '内核版本' : 'Kernel version';
  String get buildSubLevelLabel => isChinese ? '子版本号' : 'Sub level';
  String get buildPatchLevelLabel =>
      isChinese ? '安全补丁级别' : 'Security patch level';
  String get buildVirtLabel => isChinese ? '虚拟化支持' : 'Virtualization support';
  String get buildCustomRefLabel => isChinese ? '自定义 KSU 引用' : 'Custom KSU ref';
  String get buildBuildTimeLabel => isChinese ? '自定义构建时间' : 'Custom build time';
  String get buildCustomModulesLabel => isChinese ? '外部模块清单' : 'Custom modules';
  String get buildKpmPasswordLabel => isChinese ? 'KPM 密码' : 'KPM password';
  String get buildZramExtraAlgosLabel =>
      isChinese ? 'ZRAM 额外算法' : 'ZRAM extra algos';
  String get buildFeatureTitle => isChinese ? '功能开关' : 'Feature flags';
  String get buildAdvancedTitle => isChinese ? '高级选项' : 'Advanced options';
  String get buildCustomModulesTitle =>
      isChinese ? '自定义外部模块' : 'Custom external modules';
  String get buildCustomModulesSubtitle => isChinese
      ? '可以从 ABK 模块仓库选择模块，也可以手动填写 GitHub 仓库 URL。'
      : 'Choose modules from the ABK module catalog or enter a GitHub repository URL manually.';
  String get buildModuleSetOpen =>
      isChinese ? '选择模块集内容' : 'Choose module set entries';
  String get buildSelectedModulesTitle =>
      isChinese ? '已选模块' : 'Selected modules';
  String get buildAddFromModuleRepo =>
      isChinese ? '从 ABK 模块仓库添加' : 'Add from ABK module catalog';
  String get buildManualModuleAdd =>
      isChinese ? '手动添加 GitHub URL' : 'Add GitHub URL manually';
  String get buildModuleRepositoryUrl =>
      isChinese ? '模块仓库 URL' : 'Module repository URL';
  String get buildModuleRepositoryAdd =>
      isChinese ? '添加模块仓库' : 'Add module repository';
  String get buildManualModuleUrl =>
      isChinese ? '模块 GitHub URL' : 'Module GitHub URL';
  String get buildManualModuleAddButton => isChinese ? '添加模块' : 'Add module';
  String get buildModuleStageLabel => isChinese ? '注入阶段' : 'Injection stage';
  String get buildModuleStageAfterPatch => isChinese ? '打补丁后' : 'After patch';
  String get buildModuleStageBeforeBuild => isChinese ? '编译前' : 'Before build';
  String get buildModuleSetChildrenTitle =>
      isChinese ? '模块集子项' : 'Module set entries';
  String get buildModuleSetEmpty =>
      isChinese ? '这个模块集当前没有可选子项' : 'This module set has no selectable entries';
  String get buildModuleSetLoadFailed =>
      isChinese ? '模块集元数据读取失败' : 'Failed to load module set metadata';
  String get buildModuleSetSave =>
      isChinese ? '保存模块集选择' : 'Save module set selection';
  String get buildRecommendedSuffix => isChinese ? '（推荐）' : ' (recommended)';
  String get buildModuleRemove => isChinese ? '移除模块' : 'Remove module';
  String get buildNoSelectedModules =>
      isChinese ? '当前还没有选中的外部模块' : 'No external modules selected yet';
  String get buildNoCatalogModules =>
      isChinese ? '当前仓库没有可用模块' : 'No modules available in this repository';
  String get buildAllCatalogModulesAdded => isChinese
      ? '这个仓库里的模块已经全部加入到下方列表了'
      : 'All modules from this repository are already in the selected list.';
  String get buildQueueTitle => isChinese ? '构建队列' : 'Build queue';
  String get buildQueueSubtitle => isChinese
      ? '最新提交的构建任务会先显示在这里，状态来自桌面本地任务与 GitHub run。'
      : 'The latest submitted build tasks appear here first. Status comes from local tasks and GitHub runs.';
  String get buildTaskOpenLogs => isChinese ? '查看日志' : 'View logs';
  String get buildTaskCancelAction => isChinese ? '取消任务' : 'Cancel task';
  String get buildTaskDetailsTitle => isChinese ? '任务日志' : 'Task logs';
  String get buildTaskOverviewTitle => isChinese ? '任务概览' : 'Task overview';
  String get buildTaskConsoleTitle => isChinese ? '控制台输出' : 'Console output';
  String get buildTaskResultTitle => isChinese ? '结果负载' : 'Result payload';
  String get buildTaskNoOutput => isChinese ? '当前还没有日志输出' : 'No log output yet';
  String get buildTaskNoResult =>
      isChinese ? '当前没有额外结果数据' : 'No extra result payload';
  String get buildTaskCopyLogs => isChinese ? '复制日志' : 'Copy logs';
  String get buildTaskLogsCopied =>
      isChinese ? '任务日志已复制到剪贴板' : 'Task logs copied';
  String get buildTaskIdentifier => isChinese ? '任务 ID' : 'Task ID';
  String get buildTaskLiveHint => isChinese
      ? '任务仍在运行时，这里的日志会随着轮询自动刷新。'
      : 'While the task is still running, this log view refreshes automatically.';
  String get buildWorkflowCenterTitle => isChinese ? '工作流列表' : 'Workflow list';
  String get buildWorkflowCenterSubtitle => isChinese
      ? '把 GitHub 上真实存在的内核工作流单独拉出来看，不和本地任务状态混在一起。'
      : 'Show the real kernel workflows from GitHub separately from local task state.';
  String get buildArtifactCenterTitle => isChinese ? '产物中心' : 'Artifact center';
  String get buildArtifactCenterSubtitle => isChinese
      ? '按工作流查看产物，不再把下载入口塞在同一张概览卡里。'
      : 'Browse artifacts by workflow instead of packing downloads into one overview card.';
  String get buildOpenWorkflowCenter => isChinese ? '打开工作流页' : 'Open workflows';
  String get buildOpenArtifactCenter =>
      isChinese ? '打开产物中心' : 'Open artifact center';
  String get buildActiveWorkflowsLabel =>
      isChinese ? '活跃工作流' : 'Active workflows';
  String get buildTotalWorkflowsLabel =>
      isChinese ? '工作流总数' : 'Total workflows';
  String get buildTaskWorkflowPending =>
      isChinese ? '工作流待分配' : 'Workflow pending';
  String get buildTaskCurrentStep => isChinese ? '当前步骤' : 'Current step';
  String get buildTaskOpenWorkflow => isChinese ? '查看工作流' : 'Open workflow';
  String get buildTaskNoWorkflowLink => isChinese
      ? '这个活动工作流还没有可打开的链接'
      : 'This active workflow does not have an openable link yet.';
  String get buildOpenRunArtifacts => isChinese ? '查看产物' : 'Open artifacts';
  String get buildDownloadBundle => isChinese ? '下载整组' : 'Download bundle';
  String get buildArtifactCategoryKernel =>
      isChinese ? '内核刷写包' : 'Kernel bundle';
  String get buildArtifactCategoryManager => isChinese ? '管理器' : 'Manager';
  String get buildArtifactCategoryModule => isChinese ? '模块' : 'Module';
  String get buildArtifactRawList => isChinese ? '原始产物' : 'Raw artifacts';
  String get buildArtifactRecommended => isChinese ? '推荐集合' : 'Recommended set';
  String get buildArtifactQueuedSingle =>
      isChinese ? '下载任务已加入队列。' : 'The download task was queued.';
  String buildArtifactQueuedMany(int count) =>
      isChinese ? '已加入 $count 个下载任务。' : 'Queued $count download tasks.';
  String get buildArtifactGroupedHint => isChinese
      ? '左侧选择工作流，右侧查看该工作流的产物。'
      : 'Pick a workflow on the left to inspect its artifacts on the right.';
  String get buildRunsTitle => isChinese ? '最近构建' : 'Recent builds';
  String get buildRunsSubtitle => isChinese
      ? '这里展示 GitHub 侧真实的 workflow run。'
      : 'These are the real GitHub workflow runs.';
  String get buildArtifactsTitle => isChinese ? '产物' : 'Artifacts';
  String get buildArtifactsSubtitle => isChinese
      ? '先下载，再校验，再打开目录。'
      : 'Download, verify, then open the directory.';
  String get buildSubmit => isChinese ? '提交构建' : 'Submit build';
  String get buildSubmitting => isChinese ? '正在提交' : 'Submitting';
  String get buildDownload => isChinese ? '下载产物' : 'Download artifact';
  String get buildOpenDirectory => isChinese ? '打开目录' : 'Open directory';
  String get buildNoRuns => isChinese ? '暂无构建记录' : 'No build runs yet';
  String get buildNoArtifacts =>
      isChinese ? '当前 run 没有产物' : 'This run has no artifacts';
  String get buildNoTasks => isChinese ? '当前没有任务' : 'No tasks yet';
  String get buildNoSession =>
      isChinese ? '尚未完成 GitHub 登录' : 'GitHub login not completed';
  String get buildNeedsFork =>
      isChinese ? '需要先创建 fork' : 'A fork is required first';
  String get buildNeedsSync =>
      isChinese ? 'fork 需要先同步' : 'The fork needs syncing first';
  String get buildLoggedInAs => isChinese ? '已登录为' : 'Logged in as';
  String get buildRepo => isChinese ? '仓库' : 'Repo';
  String get buildFork => isChinese ? 'Fork' : 'Fork';
  String get buildBehind => isChinese ? '落后' : 'Behind';
  String get buildSignKey => isChinese ? '签名公钥' : 'Signing key';
  String get buildSignKeyGitHub =>
      isChinese ? 'GitHub fork 公钥' : 'GitHub fork key';
  String get buildSignKeyUnknown => isChinese ? '未知来源' : 'Unknown source';
  String get buildRuntimePrefill => isChinese ? '自动预填' : 'Auto fill';
  String get buildRuntimePrefillSubtitle => isChinese
      ? '如果当前设备可读，桌面端会把 Android / kernel / sublevel / patch 信息带进表单。'
      : 'When available, the desktop brings Android/kernel/sublevel/patch values into the form.';
  String get buildCustomGroup => isChinese ? '自定义构建' : 'Custom build';
  String get buildCustomGroupSubtitle => isChinese
      ? '仅在 target = custom 时显示。'
      : 'Shown only when target = custom.';
  String get buildTaskGroup => isChinese ? '任务' : 'Tasks';
  String get buildTaskGroupSubtitle => isChinese
      ? '这里显示刚提交的构建和下载任务。'
      : 'Recently submitted build and download tasks appear here.';
  String get buildInfoLoginStarted => isChinese
      ? '已打开 GitHub 验证流程，请在浏览器中完成授权。'
      : 'The GitHub verification flow has started. Finish the authorization in your browser.';
  String get buildInfoLoginComplete =>
      isChinese ? 'GitHub 登录已完成。' : 'GitHub login complete.';
  String get buildInfoForkReady =>
      isChinese ? 'fork 已就绪。' : 'The fork is ready.';
  String get buildInfoForkSynced =>
      isChinese ? 'fork 已同步。' : 'The fork has been synced.';
  String get buildInfoBuildAccepted =>
      isChinese ? '构建任务已提交。' : 'The build request was accepted.';
  String get buildErrorLoginTimedOut =>
      isChinese ? 'GitHub 登录已超时。' : 'GitHub login timed out.';
  String get deviceTitle => isChinese ? '设备' : 'Device';
  String get deviceIntro => isChinese
      ? '这一页承载设备已经进入 ABK 后的联动能力：Root 授权、模块管理，以及进入内核功能与 SUSFS 的入口。'
      : 'This page holds the ABK-linked capabilities after the device enters ABK: root grants, module management, and entry points into kernel features and SUSFS.';
  String get deviceRefreshAll => isChinese ? '刷新设备页' : 'Refresh device page';
  String get deviceBlockedTitle =>
      isChinese ? '设备页需要 ABK 在线' : 'ABK must be online';
  String get deviceBlockedSubtitle => isChinese
      ? '先在主页或应用探测页把设备连接到 ABK，再回到这里管理授权、模块和内核功能。'
      : 'Connect the device into ABK from Home or Detection first, then come back here to manage grants, modules, and kernel features.';
  String get deviceOpenDetection => isChinese ? '打开应用探测' : 'Open detection';
  String get deviceTabRoot => isChinese ? 'Root 授权' : 'Root grants';
  String get deviceTabModules => isChinese ? '模块管理' : 'Modules';
  String get deviceTabKernel => isChinese ? '内核功能' : 'Kernel';
  String get deviceRootSearch =>
      isChinese ? '搜索应用 / 包名 / UID' : 'Search app / package / UID';
  String get deviceRootShowSystem => isChinese ? '显示系统应用' : 'Show system apps';
  String get deviceRootListTitle => isChinese ? 'Root 授权列表' : 'Root grants';
  String get deviceRootListSubtitle => isChinese
      ? '这里展示已进入原生管理器授权视野的应用。'
      : 'These are the apps currently visible to the native root grant manager.';
  String get deviceRootNoApps =>
      isChinese ? '当前没有可展示的 Root 授权应用' : 'No root-grant apps to show';
  String get deviceRootDetailTitle => isChinese ? '应用详情' : 'Application detail';
  String get deviceRootDetailEmpty => isChinese
      ? '从左侧列表选中一个应用，再查看详情和授权状态。'
      : 'Select an app from the list to inspect its detail and grant state.';
  String get deviceRootAllow => isChinese ? '允许 Root' : 'Allow root';
  String get deviceRootDenied => isChinese ? '未允许' : 'Not allowed';
  String get deviceRootUpdated =>
      isChinese ? 'Root 授权状态已更新。' : 'The root grant state was updated.';
  String get deviceModuleTabInstalled => isChinese ? '已安装' : 'Installed';
  String get deviceModuleTabRepository =>
      isChinese ? '运行时模块仓库' : 'Runtime repositories';
  String get deviceModuleTabLocalInstall =>
      isChinese ? '本地安装' : 'Local install';
  String get deviceModuleOfficialRepo =>
      isChinese ? '官方运行时模块仓库' : 'Official runtime module repo';
  String get deviceModuleRepoDefault =>
      isChinese ? '运行时模块仓库' : 'Runtime module repo';
  String get deviceModuleRepoUrl =>
      isChinese ? '运行时模块仓库 JSON URL' : 'Runtime module repository JSON URL';
  String get deviceModuleAddRepo =>
      isChinese ? '添加运行时仓库' : 'Add runtime repository';
  String get deviceModuleOpenRepo => isChinese ? '打开模块页' : 'Open module page';
  String get deviceModuleNoRepositories =>
      isChinese ? '当前没有运行时模块仓库' : 'No runtime module repositories';
  String get deviceModuleNoCatalogModules =>
      isChinese ? '当前仓库没有可用模块' : 'No modules available in this repository';
  String get deviceModuleSearch =>
      isChinese ? '搜索模块 / 作者 / 描述' : 'Search module / author / description';
  String get deviceModuleNoInstalled =>
      isChinese ? '当前没有已安装运行时模块' : 'No installed runtime modules';
  String get deviceModuleInstalledSubtitle => isChinese
      ? '把设备当前运行时模块拆成普通模块、自定义模块和自定义模块集三类看。'
      : 'Split the currently installed runtime modules into standard modules, custom modules, and custom module sets.';
  String get deviceModuleStandardTitle =>
      isChinese ? '普通模块' : 'Standard modules';
  String get deviceModuleStandardSubtitle => isChinese
      ? '常规运行时模块，包括标准 KernelSU / KPM / 内建模块。'
      : 'Regular runtime modules, including standard KernelSU, KPM, and built-in modules.';
  String get deviceModuleCustomTitle => isChinese ? '自定义模块' : 'Custom modules';
  String get deviceModuleCustomSubtitle => isChinese
      ? 'ABK 自定义外部模块会单独列在这里，不和普通模块混排。'
      : 'ABK custom external modules are listed here instead of being mixed into the standard module list.';
  String get deviceModuleSetTitle =>
      isChinese ? '自定义模块集' : 'Custom module sets';
  String get deviceModuleSetSubtitle => isChinese
      ? '同一个模块集的子模块会聚合展示，便于统一看 WebUI、action 和启停状态。'
      : 'Child modules from the same custom module set are grouped together so WebUI, action, and enable state stay readable.';
  String get deviceModuleNoStandard =>
      isChinese ? '当前没有普通运行时模块' : 'No standard runtime modules';
  String get deviceModuleNoCustom =>
      isChinese ? '当前没有自定义模块' : 'No custom modules';
  String get deviceModuleNoModuleSets =>
      isChinese ? '当前没有自定义模块集' : 'No custom module sets';
  String get deviceModuleRuntimeRepoTitle =>
      isChinese ? '运行时模块仓库' : 'Runtime module repositories';
  String get deviceModuleRuntimeRepoSubtitle => isChinese
      ? '这里只管理设备运行时模块仓库，不是构建页里的 ABK 模块仓库。'
      : 'This area manages runtime module repositories for the connected device, not the ABK build-module catalog from the Build page.';
  String get deviceModuleNoCatalogResults =>
      isChinese ? '没有匹配的仓库模块' : 'No matching repository modules';
  String get deviceModuleEnable => isChinese ? '启用' : 'Enable';
  String get deviceModulePendingUninstall =>
      isChinese ? '待卸载' : 'Pending uninstall';
  String get deviceModuleAction => isChinese ? '执行动作' : 'Run action';
  String get deviceModuleWebUi => isChinese ? '打开 WebUI' : 'Open WebUI';
  String get deviceModuleWebUiDesktop =>
      isChinese ? '在桌面独立窗口打开 WebUI' : 'Open WebUI in a separate desktop window';
  String get deviceModuleInstall => isChinese ? '安装模块' : 'Install module';
  String get deviceModuleChooseZip => isChinese ? '选择 ZIP' : 'Choose ZIP';
  String get deviceModuleNoLocalZip =>
      isChinese ? '当前还没有选中模块 ZIP' : 'No module ZIP selected yet';
  String get deviceModuleUpdated =>
      isChinese ? '模块状态已更新。' : 'The module state was updated.';
  String get deviceKernelSummaryTitle =>
      isChinese ? '运行时摘要' : 'Runtime summary';
  String get deviceKernelSummarySubtitle => isChinese
      ? '桌面端直接展示 agent 已返回的运行时信息，不在这里发明新的解释层。'
      : 'Render the runtime information returned by the agent directly instead of inventing a new interpretation layer here.';
  String get deviceKernelNoRuntime =>
      isChinese ? '当前没有可用的运行时摘要' : 'No runtime summary is available right now';
  String get deviceKernelEntryTitle =>
      isChinese ? '内核功能入口' : 'Kernel feature entry';
  String get deviceKernelEntrySubtitle => isChinese
      ? 'ADB Root、SULog、内核卸载模块等开关移到单独页面；这里保留入口和摘要。'
      : 'ADB Root, SU log, kernel unmount, and related toggles live on a dedicated page; this tab keeps the entry and summary.';
  String get deviceKernelOpenFeatures =>
      isChinese ? '打开内核功能页' : 'Open kernel features';
  String get deviceKernelFeaturesTitle =>
      isChinese ? '内核功能' : 'Kernel features';
  String get deviceKernelFeaturesIntro => isChinese
      ? '把 ADB Root、SULog、SELinux 隐藏与默认卸载模块等开关单独拎出来，按 Android ABK 的管理方式展示。'
      : 'ADB Root, SU log, SELinux hide, default unmount, and related toggles are surfaced here in an Android-ABK-style management page.';
  String get deviceKernelFeaturesUnsupported => isChinese
      ? '当前连接的设备侧 ABK 还没有暴露内核功能接口，请升级设备侧 ABK 并重新连接。'
      : 'The connected device-side ABK does not expose kernel feature controls yet. Upgrade the device-side ABK and reconnect.';
  String get deviceKernelFeatureUpdated =>
      isChinese ? '内核功能状态已更新。' : 'The kernel feature state was updated.';
  String get deviceKernelFeatureAdbRootTitle =>
      isChinese ? 'ADB Root' : 'ADB Root';
  String get deviceKernelFeatureAdbRootSubtitle =>
      isChinese ? '以 root 权限运行 adbd 守护进程。' : 'Run the adbd daemon as root.';
  String get deviceKernelFeatureSulogTitle =>
      isChinese ? '超级用户访问日志' : 'Superuser access log';
  String get deviceKernelFeatureSulogSubtitle => isChinese
      ? '记录与 Root 有关的事件到 KernelSU 的超级用户访问日志。'
      : 'Record root-related events to the KernelSU superuser access log.';
  String get deviceKernelFeatureKernelUmountTitle =>
      isChinese ? '卸载模块（内核级）' : 'Unmount modules (kernel level)';
  String get deviceKernelFeatureKernelUmountSubtitle => isChinese
      ? '让内核为需要的应用处理模块卸载。'
      : 'Let the kernel handle module unmount for apps that need it.';
  String get deviceKernelFeatureSelinuxHideTitle =>
      isChinese ? '隐藏 SELinux 修改' : 'Hide SELinux changes';
  String get deviceKernelFeatureSelinuxHideSubtitle => isChinese
      ? '阻止应用检测 SELinux 修改。'
      : 'Prevent apps from detecting SELinux changes.';
  String get deviceKernelFeatureDefaultUmountTitle =>
      isChinese ? '默认卸载模块' : 'Default unmount modules';
  String get deviceKernelFeatureDefaultUmountSubtitle => isChinese
      ? '作为 App Profile 里“卸载模块”的全局默认值。'
      : 'Use this as the global default for “Unmount modules” in App Profile.';
  String get deviceKernelFeatureStatusSupported =>
      isChinese ? '支持' : 'Supported';
  String get deviceKernelFeatureStatusManaged => isChinese ? '受管' : 'Managed';
  String get deviceKernelFeatureStatusUnsupported =>
      isChinese ? '不支持' : 'Unsupported';
  String get deviceSusfsTitle => isChinese ? 'SUSFS 控制' : 'SUSFS control';
  String get deviceSusfsSubtitle => isChinese
      ? '默认先走表单化配置，原始 JSON 只作为高级模式保留。'
      : 'The default path is form-driven configuration. Raw JSON stays as an advanced mode.';
  String get deviceSusfsPageTitle => isChinese ? 'SUSFS' : 'SUSFS';
  String get deviceSusfsPageIntro => isChinese
      ? 'SUSFS 单独落成一页，先给出状态和常用配置，再把规则与原始 JSON 往后收。'
      : 'SUSFS has its own page. Status and common controls come first, while rules and raw JSON stay further down.';
  String get deviceSusfsOpenPage =>
      isChinese ? '打开 SUSFS 页面' : 'Open SUSFS page';
  String get deviceSusfsApply =>
      isChinese ? '应用 SUSFS 配置' : 'Apply SUSFS config';
  String get deviceSusfsReset => isChinese ? '重置草稿' : 'Reset draft';
  String get deviceSusfsDraftInvalid =>
      isChinese ? 'SUSFS 配置 JSON 无效' : 'The SUSFS config JSON is invalid';
  String get deviceSusfsDraftEmpty =>
      isChinese ? 'SUSFS 配置草稿为空' : 'The SUSFS config draft is empty';
  String get deviceSusfsFormErrorTitle => isChinese ? '表单错误' : 'Form error';
  String get deviceSusfsOverviewTitle =>
      isChinese ? '运行状态' : 'Runtime overview';
  String get deviceSusfsOverviewSubtitle => isChinese
      ? '先确认当前内核、二进制和能力矩阵，再决定要不要改规则。'
      : 'Check the kernel, bundled binary, and support matrix before changing rules.';
  String get deviceSusfsStatusAvailable => isChinese ? '可用' : 'Available';
  String get deviceSusfsStatusUnavailable => isChinese ? '不可用' : 'Unavailable';
  String deviceSusfsFeatureFlagCount(int count) =>
      isChinese ? '$count 个特性标志' : '$count feature flags';
  String get deviceSusfsKernelVersionLabel =>
      isChinese ? '内核版本' : 'Kernel version';
  String get deviceSusfsBinaryLabel => isChinese ? '打包二进制' : 'Bundled binary';
  String get deviceSusfsConfigPathLabel => isChinese ? '配置路径' : 'Config path';
  String get deviceSusfsDiagnosticsTitle => isChinese ? '诊断信息' : 'Diagnostics';
  String get deviceSusfsActionTitle =>
      isChinese ? '应用配置' : 'Apply configuration';
  String get deviceSusfsActionSubtitle => isChinese
      ? '常用路径应该是改表单、应用配置、观察任务队列，而不是直接改 JSON。'
      : 'The common path should be editing the form, applying the config, and watching the task queue instead of editing JSON directly.';
  String get deviceSusfsDraftEdited => isChinese ? '表单已编辑' : 'Form edited';
  String get deviceSusfsDraftClean => isChinese ? '与设备一致' : 'Matches device';
  String get deviceSusfsReadyToApply => isChinese ? '可直接应用' : 'Ready to apply';
  String get deviceSusfsActionHint => isChinese
      ? '先完成表单，再提交到设备。原始 JSON 放在页面下半部分。'
      : 'Finish the form first, then submit it to the device. Raw JSON is lower on the page.';
  String get deviceSusfsApplyForm => isChinese ? '应用表单配置' : 'Apply form config';
  String get deviceSusfsResetToDevice =>
      isChinese ? '恢复为设备当前配置' : 'Reset to device config';
  String get deviceSusfsSyncJsonFromForm =>
      isChinese ? '用表单生成 JSON 草稿' : 'Generate JSON draft from form';
  String get deviceSusfsLoadFormFromJson =>
      isChinese ? '从 JSON 载入表单' : 'Load form from JSON';
  String get deviceSusfsApplyRawJson =>
      isChinese ? '按原始 JSON 应用' : 'Apply raw JSON';
  String get deviceSusfsBasicTitle =>
      isChinese ? '基础配置' : 'Basic configuration';
  String get deviceSusfsBasicSubtitle => isChinese
      ? '把开关、挂载隐藏策略和 uname 伪装放在一层。'
      : 'Keep the primary toggles, mount hiding policy, and uname spoofing in one place.';
  String get deviceSusfsAutoReplayTitle => isChinese ? '自动回放' : 'Auto replay';
  String get deviceSusfsAutoReplaySubtitle => isChinese
      ? '开机后自动重放 SUSFS 规则。'
      : 'Replay SUSFS rules automatically after boot.';
  String get deviceSusfsLogTitle => isChinese ? '启用日志' : 'Enable logging';
  String get deviceSusfsLogSubtitle => isChinese
      ? '让 SUSFS 输出运行日志，便于排障。'
      : 'Let SUSFS emit runtime logs for diagnostics.';
  String get deviceSusfsAvcSpoofTitle =>
      isChinese ? '伪装 AVC 日志' : 'Spoof AVC logs';
  String get deviceSusfsAvcSpoofSubtitle => isChinese
      ? '隐藏与内核修改相关的 AVC 线索。'
      : 'Hide AVC hints related to kernel changes.';
  String get deviceSusfsHideMountModeTitle =>
      isChinese ? '挂载隐藏模式' : 'Mount hiding mode';
  String get deviceSusfsSpoofUnameStageTitle =>
      isChinese ? 'uname 伪装时机' : 'uname spoof stage';
  String get deviceSusfsOptionOff => isChinese ? '关闭' : 'Off';
  String get deviceSusfsOptionAllProcesses =>
      isChinese ? '所有进程' : 'All processes';
  String get deviceSusfsOptionNonSuProcesses =>
      isChinese ? '仅非 SU 进程' : 'Non-SU processes';
  String get deviceSusfsOptionPostFsData =>
      isChinese ? 'post-fs-data' : 'post-fs-data';
  String get deviceSusfsOptionBootCompleted =>
      isChinese ? 'boot-completed' : 'boot-completed';
  String get deviceSusfsUnameValueLabel =>
      isChinese ? 'uname 伪装值' : 'uname value';
  String get deviceSusfsBuildTimeValueLabel =>
      isChinese ? '构建时间伪装值' : 'Build time value';
  String get deviceSusfsSdcardRootLabel =>
      isChinese ? 'sdcard 根路径' : 'sdcard root path';
  String get deviceSusfsAndroidDataRootLabel =>
      isChinese ? 'Android/data 根路径' : 'Android/data root path';
  String get deviceSusfsPresetTitle =>
      isChinese ? '兼容预设' : 'Compatibility presets';
  String get deviceSusfsPresetSubtitle => isChinese
      ? '这些开关是常见 ROM / App 场景的快捷预设。'
      : 'These toggles are shortcuts for common ROM and app compatibility cases.';
  String get deviceSusfsHideCustomRomLevelLabel =>
      isChinese ? '隐藏定制 ROM 等级' : 'Hide custom ROM level';
  String get deviceSusfsEmulateVoldLabel =>
      isChinese ? '模拟 vold app data' : 'Emulate vold app data';
  String deviceSusfsEmulateVoldOption(int value) {
    return switch (value) {
      1 => 'sus_path',
      2 => 'sus_path_loop',
      _ => deviceSusfsOptionOff,
    };
  }

  String get deviceSusfsHideVendorSepolicyTitle =>
      isChinese ? '隐藏 vendor sepolicy' : 'Hide vendor sepolicy';
  String get deviceSusfsHideCompatMatrixTitle =>
      isChinese ? '隐藏兼容矩阵' : 'Hide compatibility matrix';
  String get deviceSusfsHideGappsTitle =>
      isChinese ? '隐藏 GApps 痕迹' : 'Hide GApps traces';
  String get deviceSusfsHideRevancedTitle =>
      isChinese ? '隐藏 ReVanced 痕迹' : 'Hide ReVanced traces';
  String get deviceSusfsSpoofCmdlineTitle =>
      isChinese ? '伪装 cmdline / bootconfig' : 'Spoof cmdline / bootconfig';
  String get deviceSusfsHideLoopsTitle =>
      isChinese ? '隐藏 loop 设备' : 'Hide loop devices';
  String get deviceSusfsForceHideLsposedTitle =>
      isChinese ? '强制隐藏 LSPosed' : 'Force hide LSPosed';
  String get deviceSusfsAutoTryUmountTitle =>
      isChinese ? '自动尝试卸载' : 'Auto try umount';
  String get deviceSusfsSkipLegitMountsTitle =>
      isChinese ? '跳过合法挂载点' : 'Skip legit mounts';
  String get deviceSusfsUmountForZygoteTitle =>
      isChinese ? 'zygote 隔离服务执行卸载' : 'Umount for zygote iso service';
  String get deviceSusfsRulesTitle => isChinese ? '规则配置' : 'Rule configuration';
  String get deviceSusfsRulesSubtitle => isChinese
      ? '规则项按行编辑，比直接改 JSON 更容易定位问题。'
      : 'Edit rule entries line by line instead of patching raw JSON directly.';
  String deviceSusfsRuleCount(int count) =>
      isChinese ? '$count 条 path 规则' : '$count path rules';
  String deviceSusfsMountCount(int count) =>
      isChinese ? '$count 条 mount 规则' : '$count mount rules';
  String deviceSusfsMapCount(int count) =>
      isChinese ? '$count 条 map 规则' : '$count map rules';
  String get deviceSusfsPathRulesLabel =>
      isChinese ? 'sus_path 规则' : 'sus_path rules';
  String get deviceSusfsLoopPathRulesLabel =>
      isChinese ? 'sus_path_loop 规则' : 'sus_path_loop rules';
  String get deviceSusfsPathRulesHint => isChinese
      ? '每行一个路径，可在后面追加重试次数，例如 `/system/bin 3`。'
      : 'One path per line. Optionally append a retry count, for example `/system/bin 3`.';
  String get deviceSusfsMapsLabel =>
      isChinese ? 'sus_maps 规则' : 'sus_maps rules';
  String get deviceSusfsMountsLabel =>
      isChinese ? 'sus_mount 规则' : 'sus_mount rules';
  String get deviceSusfsTryUmountLabel =>
      isChinese ? 'try_umount 规则' : 'try_umount rules';
  String get deviceSusfsLegitMountsLabel =>
      isChinese ? '合法挂载点白名单' : 'Legit mounts allowlist';
  String get deviceSusfsAdvancedTitle => isChinese ? '高级规则' : 'Advanced rules';
  String get deviceSusfsAdvancedSubtitle => isChinese
      ? 'open redirect 和 kstat 保留在高级层，避免默认路径过重。'
      : 'Open redirect and kstat stay in the advanced layer so the default path stays light.';
  String get deviceSusfsOpenRedirectLabel =>
      isChinese ? 'open redirect 规则' : 'Open redirect rules';
  String get deviceSusfsOpenRedirectHint => isChinese
      ? '每行 `original redirected stage [uid_scheme]`。'
      : 'Each line is `original redirected stage [uid_scheme]`.';
  String get deviceSusfsKstatLabel =>
      isChinese ? '静态 kstat JSON' : 'Static kstat JSON';
  String get deviceSusfsRawJsonTitle => isChinese ? '原始 JSON' : 'Raw JSON';
  String get deviceSusfsRawJsonSubtitle => isChinese
      ? '这里只保留高级模式。通常先改表单，再决定要不要落到原始 JSON。'
      : 'This is the advanced mode. Most of the time you should edit the form first and only drop to raw JSON when needed.';
  String get deviceSusfsRawJsonHint => isChinese
      ? '这里是完整草稿；如果手改过 JSON，可以再把它载回上面的表单。'
      : 'This is the full draft. If you edit the JSON manually, you can load it back into the form above.';
  String get deviceTaskQueued =>
      isChinese ? '任务已加入队列。' : 'The task was queued.';
  String get deviceTaskTitle => isChinese ? '设备任务' : 'Device tasks';
  String get deviceTaskSubtitle => isChinese
      ? '这里展示模块安装、模块动作、SUSFS 应用等设备侧任务。'
      : 'This list shows device-side tasks such as module installs, module actions, and SUSFS apply runs.';
  String get deviceTaskNoTasks =>
      isChinese ? '当前没有设备任务' : 'No device tasks yet';
  String get settingsTitle => isChinese ? '设置' : 'Settings';
  String get settingsIntro => isChinese
      ? '桌面端设置只承载账户、下载、诊断和关于等应用级能力；设备联动能力已经留在设备页。'
      : 'Desktop settings cover account, downloads, diagnostics, and about-level app settings. Device-linked capabilities stay on the Device page.';
  String get settingsRefresh => isChinese ? '刷新设置页' : 'Refresh settings';
  String get settingsAccountTitle => isChinese ? '账户' : 'Account';
  String get settingsAccountSubtitle => isChinese
      ? '这里展示 GitHub 登录态、fork 与下载目录等构建前提。'
      : 'This section shows the GitHub session, fork state, and other build prerequisites.';
  String get settingsNotLoggedIn => isChinese ? '未登录' : 'Not logged in';
  String get settingsLogout => isChinese ? '退出登录' : 'Log out';
  String get settingsLoggedOut =>
      isChinese ? 'GitHub 登录态已移除。' : 'The GitHub session was cleared.';
  String get settingsBuildTitle => isChinese ? '构建' : 'Build';
  String get settingsBuildSubtitle => isChinese
      ? '桌面端在这里承载下载目录和构建相关的基础偏好。'
      : 'This section carries the basic build-side preferences such as the download directory.';
  String get settingsDownloadDir =>
      isChinese ? '默认下载目录' : 'Default download directory';
  String get settingsChooseDirectory => isChinese ? '选择目录' : 'Choose directory';
  String get settingsSaveDirectory => isChinese ? '保存目录' : 'Save directory';
  String get settingsDirectorySaved =>
      isChinese ? '默认下载目录已保存。' : 'The default download directory was saved.';
  String get settingsProxyTitle => isChinese ? '代理' : 'Proxy';
  String get settingsProxySubtitle => isChinese
      ? '为 GitHub、上游源码同步和其他桌面侧网络请求配置代理。'
      : 'Configure a proxy for GitHub, upstream source sync, and other desktop-side network requests.';
  String get settingsHttpProxy => isChinese ? 'HTTP_PROXY' : 'HTTP_PROXY';
  String get settingsHttpsProxy => isChinese ? 'HTTPS_PROXY' : 'HTTPS_PROXY';
  String get settingsAllProxy => isChinese ? 'ALL_PROXY' : 'ALL_PROXY';
  String get settingsNoProxy => isChinese ? 'NO_PROXY' : 'NO_PROXY';
  String get settingsSaveProxy => isChinese ? '保存代理' : 'Save proxy';
  String get settingsProxySaved =>
      isChinese ? '代理设置已保存。' : 'The proxy settings were saved.';
  String get settingsDiagnosticsTitle => isChinese ? '诊断' : 'Diagnostics';
  String get settingsDiagnosticsSubtitle => isChinese
      ? '导出桌面壳与设备代理的诊断包，排障时直接从这里拿。'
      : 'Export a diagnostics bundle for the desktop shell and device agent from here.';
  String get settingsExportDiagnostics =>
      isChinese ? '导出诊断包' : 'Export diagnostics';
  String get settingsDownloadDiagnostic =>
      isChinese ? '下载诊断包' : 'Download diagnostics';
  String get settingsAboutTitle => isChinese ? '关于' : 'About';
  String get settingsAboutSubtitle => isChinese
      ? '展示桌面壳、sidecar 与连接状态的基础信息。'
      : 'Show the basic desktop shell, sidecar, and connection information.';
  String get settingsOpenFork => isChinese ? '打开 fork' : 'Open fork';
  String get settingsOpenRepo => isChinese ? '打开仓库' : 'Open repo';
  String get settingsNoDiagnosticsTask =>
      isChinese ? '当前没有诊断导出任务' : 'No diagnostics export task is available yet';
  String get settingsErrorTitle =>
      isChinese ? '设置页当前不可用' : 'Settings are currently unavailable';
  String get settingsDiagnosticsChecking => isChinese
      ? '正在检查设备侧 ABK 是否支持诊断导出。'
      : 'Checking whether the device-side ABK supports diagnostics export.';
  String get settingsDiagnosticsRequiresAbk => isChinese
      ? '需要先让设备进入 ABK 模式，才能导出设备侧诊断包。'
      : 'Move the device into ABK mode before exporting a device-side diagnostics bundle.';
  String get settingsDiagnosticsUnsupported => isChinese
      ? '当前连接的设备侧 ABK 不支持诊断导出，请升级设备侧 ABK 并重新连接。'
      : 'The connected device-side ABK does not support diagnostics export yet. Upgrade the device-side ABK and reconnect.';
  String get commonEdit => isChinese ? '编辑' : 'Edit';
  String get commonCount => isChinese ? '数量' : 'Count';
  String get commonSave => isChinese ? '保存' : 'Save';
  String get commonCancel => isChinese ? '取消' : 'Cancel';
  String artifactCategoryLabel(BuildArtifactCategory category) {
    return switch (category) {
      BuildArtifactCategory.kernel => buildArtifactCategoryKernel,
      BuildArtifactCategory.manager => buildArtifactCategoryManager,
      BuildArtifactCategory.module => buildArtifactCategoryModule,
    };
  }

  String buildTargetLabel(String target) {
    return switch (target) {
      'a12' => isChinese ? 'Android 12 / 5.10' : 'Android 12 / 5.10',
      'a13' => isChinese ? 'Android 13 / 5.15' : 'Android 13 / 5.15',
      'a14' => isChinese ? 'Android 14 / 6.1' : 'Android 14 / 6.1',
      'a15' => isChinese ? 'Android 15 / 6.6' : 'Android 15 / 6.6',
      'a16' => isChinese ? 'Android 16 / 6.12' : 'Android 16 / 6.12',
      'custom' => isChinese ? '自定义' : 'Custom',
      _ => target,
    };
  }

  String buildTaskLabel(String kind) {
    return switch (kind) {
      'build.gki' => isChinese ? 'GKI 构建' : 'GKI build',
      'artifact.download' => isChinese ? '产物下载' : 'Artifact download',
      'diagnostics.export' => isChinese ? '诊断导出' : 'Diagnostics export',
      'workflow.download' => isChinese ? '工作流下载' : 'Workflow download',
      'local.build.init' => isChinese ? 'AOSP 源码初始化' : 'AOSP source init',
      'local.build.source.sync' => isChinese ? '源码同步' : 'Source sync',
      'local.build.rebuild' => isChinese ? '本地内核编译' : 'Local kernel build',
      'local.build.profile.build' => isChinese ? 'Profile 构建' : 'Profile build',
      'local.backend.install' => isChinese ? 'Backend 安装' : 'Backend install',
      _ => kind,
    };
  }

  String buildTaskMessageLabel(String message) {
    final normalized = message.trim().toLowerCase();
    return switch (normalized) {
      'syncing local source instance' =>
        isChinese ? '正在同步本地源码实例' : 'Syncing local source instance',
      'initializing local aosp workspace' =>
        isChinese ? '正在初始化本地 AOSP 工作区' : 'Initializing local AOSP workspace',
      'local build workspace initialized' =>
        isChinese ? '本地工作区初始化完成' : 'Local workspace initialized',
      'local build init failed' =>
        isChinese ? '本地初始化失败' : 'Local initialization failed',
      'local kernel rebuild running' =>
        isChinese ? '正在执行本地内核编译' : 'Running local kernel build',
      'local kernel rebuild finished' =>
        isChinese ? '本地内核编译完成' : 'Local kernel build finished',
      'local kernel rebuild failed' =>
        isChinese ? '本地内核编译失败' : 'Local kernel build failed',
      'running local build profile' =>
        isChinese ? '正在执行本地 profile 构建' : 'Running local build profile',
      'local build profile finished' =>
        isChinese ? '本地 profile 构建完成' : 'Local build profile finished',
      'local build profile failed' =>
        isChinese ? '本地 profile 构建失败' : 'Local build profile failed',
      'cancellation requested' =>
        isChinese ? '已请求取消' : 'Cancellation requested',
      'local source sync cancelled' =>
        isChinese ? '本地源码同步已取消' : 'Local source sync cancelled',
      'local build task cancelled' =>
        isChinese ? '本地构建任务已取消' : 'Local build task cancelled',
      _ => message,
    };
  }

  String buildTaskStateLabel(String state) {
    return switch (state) {
      'pending' => isChinese ? '排队中' : 'Pending',
      'running' => isChinese ? '进行中' : 'Running',
      'succeeded' => isChinese ? '已完成' : 'Succeeded',
      'failed' => isChinese ? '失败' : 'Failed',
      'cancelled' => isChinese ? '已取消' : 'Cancelled',
      _ => state,
    };
  }

  String buildRunStatusLabel(BuildRunSummary run) {
    if (run.isSuccess) {
      return isChinese ? '成功' : 'Success';
    }
    if (run.isFailure) {
      return isChinese ? '失败' : 'Failure';
    }
    if (run.isRunning) {
      return isChinese ? '进行中' : 'Running';
    }
    return run.status;
  }

  String buildModuleStageLabelForValue(String stage) {
    return switch (stage) {
      'before_build' => buildModuleStageBeforeBuild,
      _ => buildModuleStageAfterPatch,
    };
  }

  String deviceKernelFeatureTitle(String id) {
    return switch (id) {
      'adb_root' => deviceKernelFeatureAdbRootTitle,
      'sulog' => deviceKernelFeatureSulogTitle,
      'kernel_umount' => deviceKernelFeatureKernelUmountTitle,
      'selinux_hide' => deviceKernelFeatureSelinuxHideTitle,
      'default_umount' => deviceKernelFeatureDefaultUmountTitle,
      _ => id,
    };
  }

  String deviceKernelFeatureSubtitle(String id) {
    return switch (id) {
      'adb_root' => deviceKernelFeatureAdbRootSubtitle,
      'sulog' => deviceKernelFeatureSulogSubtitle,
      'kernel_umount' => deviceKernelFeatureKernelUmountSubtitle,
      'selinux_hide' => deviceKernelFeatureSelinuxHideSubtitle,
      'default_umount' => deviceKernelFeatureDefaultUmountSubtitle,
      _ => '',
    };
  }

  String deviceKernelFeatureStatusLabel(String status) {
    return switch (status) {
      'supported' => deviceKernelFeatureStatusSupported,
      'managed' => deviceKernelFeatureStatusManaged,
      _ => deviceKernelFeatureStatusUnsupported,
    };
  }

  static AppStrings fromLocale(Locale locale) {
    if (locale.languageCode.toLowerCase().startsWith('zh')) {
      return const AppStrings._(AppLocale.zhCn);
    }
    return const AppStrings._(AppLocale.en);
  }
}

extension AppStringsContext on BuildContext {
  AppStrings get strings => AppStrings.of(this);
}

class _AppStringsDelegate extends LocalizationsDelegate<AppStrings> {
  const _AppStringsDelegate();

  @override
  bool isSupported(Locale locale) {
    return AppStrings.supportedLocales.any(
      (supported) => supported.languageCode == locale.languageCode,
    );
  }

  @override
  Future<AppStrings> load(Locale locale) {
    return SynchronousFuture<AppStrings>(AppStrings.fromLocale(locale));
  }

  @override
  bool shouldReload(_AppStringsDelegate old) => false;
}
