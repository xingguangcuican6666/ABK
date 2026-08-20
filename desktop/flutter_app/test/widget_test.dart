import 'package:abk_desktop/src/app.dart';
import 'package:abk_desktop/src/core/api/abk_sidecar_api.dart';
import 'package:abk_desktop/src/core/localization/app_strings.dart';
import 'package:abk_desktop/src/core/models/build_models.dart';
import 'package:abk_desktop/src/core/models/device_models.dart';
import 'package:abk_desktop/src/core/models/sidecar_models.dart';
import 'package:abk_desktop/src/core/state/dashboard_controller.dart';
import 'package:abk_desktop/src/features/build/build_page.dart';
import 'package:abk_desktop/src/features/device/device_page.dart';
import 'package:abk_desktop/src/features/settings/settings_page.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'dart:typed_data';

void main() {
  testWidgets('shows ABK connected home after bootstrap', (tester) async {
    final api = _FakeSidecarApi();

    await tester.pumpWidget(
      ProviderScope(
        overrides: <Override>[sidecarApiProvider.overrideWithValue(api)],
        child: const AbkDesktopApp(),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('ABK'), findsWidgets);
    expect(find.text('主页'), findsAtLeastNWidgets(2));
    expect(find.text('应用探测'), findsOneWidget);
  });

  testWidgets('shows build page content before github session loads', (
    tester,
  ) async {
    final api = _FakeSidecarApi(
      sessionDelay: const Duration(milliseconds: 400),
    );

    await _pumpBuildPage(tester, api, settle: false);
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('构建'), findsAtLeastNWidgets(1));

    await tester.pump(const Duration(milliseconds: 450));
    await tester.pumpAndSettle();
  });

  testWidgets(
    'keeps sidecar available when device detection fails',
    (tester) async {
      final api = _FakeSidecarApi(
        detectError: const SidecarException('adb is not installed'),
      );

      await tester.pumpWidget(
        ProviderScope(
          overrides: <Override>[sidecarApiProvider.overrideWithValue(api)],
          child: const AbkDesktopApp(),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('桌面桥接服务当前不可用'), findsNothing);
      expect(find.text('桥接服务缺失'), findsNothing);
      expect(find.text('adb is not installed'), findsOneWidget);
      expect(find.text('需要手动处理'), findsWidgets);
    },
  );

  testWidgets('does not show stale serial when no adb devices are present', (
    tester,
  ) async {
    final api = _FakeSidecarApi(
      deviceState: DeviceConnectionState(
        serial: '34c4c788',
        agentHost: '127.0.0.1',
        agentPort: 48765,
        connected: false,
        mode: DeviceConnectionMode.disconnected,
        lastError: null,
        lastDetected: const <DetectedDevice>[],
        lastDetectRaw: '',
      ),
      detectionResult: const DeviceDetectionResult(
        devices: <DetectedDevice>[],
        raw: 'List of devices attached',
      ),
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: <Override>[sidecarApiProvider.overrideWithValue(api)],
        child: const AbkDesktopApp(),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('34c4c788'), findsNothing);
    expect(find.text('还没有选中设备'), findsOneWidget);
  });

  testWidgets('shows only sync fork when fork exists but is behind', (
    tester,
  ) async {
    final api = _FakeSidecarApi(
      session: const GitHubSessionStatus(
        ok: true,
        loggedIn: true,
        repo: 'foo/bar',
        needsFork: false,
        needsSync: true,
        behindBy: 2,
        aheadBy: 0,
        userLogin: 'tester',
        forkFullName: 'tester/ABK',
        signingKeyAvailable: true,
        signingKeySource: 'config',
        downloadDir: '/tmp',
      ),
    );

    await _pumpBuildPage(tester, api);

    expect(find.text('同步 fork'), findsOneWidget);
    expect(find.text('创建 fork'), findsNothing);
    expect(find.text('登录 GitHub'), findsNothing);
  });

  testWidgets('shows only ensure fork when logged in without fork', (
    tester,
  ) async {
    final api = _FakeSidecarApi(
      session: const GitHubSessionStatus(
        ok: true,
        loggedIn: true,
        repo: 'foo/bar',
        needsFork: true,
        needsSync: false,
        behindBy: 0,
        aheadBy: 0,
        userLogin: 'tester',
        forkFullName: null,
        signingKeyAvailable: false,
        signingKeySource: null,
        downloadDir: null,
      ),
    );

    await _pumpBuildPage(tester, api);

    expect(find.text('创建 fork'), findsOneWidget);
    expect(find.text('同步 fork'), findsNothing);
    expect(find.text('登录 GitHub'), findsNothing);
  });

  testWidgets('takes over active kernel workflows into the queue list', (
    tester,
  ) async {
    final api = _FakeSidecarApi(
      runs: const <BuildRunSummary>[
        BuildRunSummary(
          id: 4242,
          name: 'Kernel Workflow',
          displayTitle: 'Android 14 / 6.1',
          status: 'in_progress',
          conclusion: null,
          event: 'workflow_dispatch',
          headBranch: 'main',
          htmlUrl: 'https://github.com/foo/bar/actions/runs/4242',
          createdAt: '2026-07-15T02:00:00Z',
          updatedAt: '2026-07-15T02:10:00Z',
          runNumber: 128,
        ),
      ],
    );

    await _pumpBuildPage(tester, api);

    expect(find.textContaining('#128 · Kernel Workflow'), findsOneWidget);
    expect(find.textContaining('当前步骤 · 进行中'), findsOneWidget);
  });

  testWidgets('shows blocked device page when ABK is not connected', (
    tester,
  ) async {
    final api = _FakeSidecarApi(
      deviceState: DeviceConnectionState.disconnected(),
      detectionResult: const DeviceDetectionResult(
        devices: <DetectedDevice>[],
        raw: 'List of devices attached',
      ),
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: <Override>[sidecarApiProvider.overrideWithValue(api)],
        child: MaterialApp(
          locale: const Locale('zh', 'CN'),
          supportedLocales: AppStrings.supportedLocales,
          localizationsDelegates: const <LocalizationsDelegate<dynamic>>[
            AppStrings.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          home: const Scaffold(body: DevicePage()),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('设备页需要 ABK 在线'), findsOneWidget);
    expect(find.text('打开应用探测'), findsOneWidget);
  });

  testWidgets('shows settings page sections', (tester) async {
    final api = _FakeSidecarApi();

    await tester.pumpWidget(
      ProviderScope(
        overrides: <Override>[sidecarApiProvider.overrideWithValue(api)],
        child: MaterialApp(
          locale: const Locale('zh', 'CN'),
          supportedLocales: AppStrings.supportedLocales,
          localizationsDelegates: const <LocalizationsDelegate<dynamic>>[
            AppStrings.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          home: const Scaffold(body: SettingsPage()),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('设置'), findsAtLeastNWidgets(1));
    expect(find.text('账户'), findsOneWidget);
    expect(find.text('构建'), findsOneWidget);
    expect(find.text('诊断'), findsOneWidget);
    expect(find.text('关于'), findsOneWidget);
  });

  testWidgets('shows restoring session state on settings page before load', (
    tester,
  ) async {
    final api = _FakeSidecarApi(
      sessionDelay: const Duration(milliseconds: 400),
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: <Override>[sidecarApiProvider.overrideWithValue(api)],
        child: MaterialApp(
          locale: const Locale('zh', 'CN'),
          supportedLocales: AppStrings.supportedLocales,
          localizationsDelegates: const <LocalizationsDelegate<dynamic>>[
            AppStrings.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          home: const Scaffold(body: SettingsPage()),
        ),
      ),
    );
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('正在恢复 GitHub 登录态'), findsOneWidget);
    expect(find.text('未登录'), findsNothing);

    await tester.pump(const Duration(milliseconds: 450));
    await tester.pump();
  });

  testWidgets('shows diagnostics upgrade hint for legacy agent', (
    tester,
  ) async {
    final connectedDevice = DeviceConnectionState(
      serial: 'ABC123',
      agentHost: '127.0.0.1',
      agentPort: 48765,
      connected: true,
      mode: DeviceConnectionMode.abk,
      lastError: null,
      lastDetected: const <DetectedDevice>[
        DetectedDevice(
          serial: 'ABC123',
          status: 'device',
          detail: 'model:zorn product:abk',
        ),
      ],
      lastDetectRaw: '',
    );
    final api = _FakeSidecarApi(
      deviceState: connectedDevice,
      health: SidecarHealth(
        status: 'ok',
        protocolVersion: 'abk-desktop-sidecar-v1',
        sidecar: const SidecarEndpoint(host: '127.0.0.1', port: 38765),
        device: connectedDevice,
        agent: const AgentHealth(
          status: 'ok',
          protocolVersion: 'abk-agent-v1',
          port: 48765,
          appVersion: '1.0.0',
          appVersionCode: 1,
          rootGranted: true,
          managerAccessKind: 'native_manager',
          managerDiagnostic: null,
          capabilities: <String>[],
        ),
      ),
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: <Override>[sidecarApiProvider.overrideWithValue(api)],
        child: MaterialApp(
          locale: const Locale('zh', 'CN'),
          supportedLocales: AppStrings.supportedLocales,
          localizationsDelegates: const <LocalizationsDelegate<dynamic>>[
            AppStrings.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          home: const Scaffold(body: SettingsPage()),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('当前连接的设备侧 ABK 不支持诊断导出，请升级设备侧 ABK 并重新连接。'), findsOneWidget);
    final button = tester.widget<FilledButton>(
      find.widgetWithText(FilledButton, '导出诊断包'),
    );
    expect(button.onPressed, isNull);
  });

  testWidgets('enables diagnostics export when agent advertises capability', (
    tester,
  ) async {
    final connectedDevice = DeviceConnectionState(
      serial: 'ABC123',
      agentHost: '127.0.0.1',
      agentPort: 48765,
      connected: true,
      mode: DeviceConnectionMode.abk,
      lastError: null,
      lastDetected: const <DetectedDevice>[
        DetectedDevice(
          serial: 'ABC123',
          status: 'device',
          detail: 'model:zorn product:abk',
        ),
      ],
      lastDetectRaw: '',
    );
    final api = _FakeSidecarApi(
      deviceState: connectedDevice,
      health: SidecarHealth(
        status: 'ok',
        protocolVersion: 'abk-desktop-sidecar-v1',
        sidecar: const SidecarEndpoint(host: '127.0.0.1', port: 38765),
        device: connectedDevice,
        agent: const AgentHealth(
          status: 'ok',
          protocolVersion: 'abk-agent-v1',
          port: 48765,
          appVersion: '1.0.0',
          appVersionCode: 1,
          rootGranted: true,
          managerAccessKind: 'native_manager',
          managerDiagnostic: null,
          capabilities: <String>['diagnostics.export'],
        ),
      ),
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: <Override>[sidecarApiProvider.overrideWithValue(api)],
        child: MaterialApp(
          locale: const Locale('zh', 'CN'),
          supportedLocales: AppStrings.supportedLocales,
          localizationsDelegates: const <LocalizationsDelegate<dynamic>>[
            AppStrings.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          home: const Scaffold(body: SettingsPage()),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final button = tester.widget<FilledButton>(
      find.widgetWithText(FilledButton, '导出诊断包'),
    );
    expect(button.onPressed, isNotNull);
  });
}

Future<void> _pumpBuildPage(
  WidgetTester tester,
  _FakeSidecarApi api, {
  bool settle = true,
}) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: <Override>[sidecarApiProvider.overrideWithValue(api)],
      child: MaterialApp(
        locale: const Locale('zh', 'CN'),
        supportedLocales: AppStrings.supportedLocales,
        localizationsDelegates: const <LocalizationsDelegate<dynamic>>[
          AppStrings.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        home: const Scaffold(body: BuildPage()),
      ),
    ),
  );
  if (settle) {
    await tester.pumpAndSettle();
  }
}

class _FakeSidecarApi implements AbkSidecarApi {
  _FakeSidecarApi({
    this._deviceState,
    this._detectionResult,
    this.detectError,
    this._health,
    GitHubSessionStatus? session,
    List<BuildRunSummary>? runs,
    Map<int, List<BuildArtifactSummary>>? artifactsByRunId,
    this.sessionDelay = Duration.zero,
  }) : _session = session ?? _defaultSession,
       _runs = runs ?? const <BuildRunSummary>[],
       _artifactsByRunId =
           artifactsByRunId ?? const <int, List<BuildArtifactSummary>>{};

  final DeviceConnectionState? _deviceState;
  final DeviceDetectionResult? _detectionResult;
  final SidecarException? detectError;
  final SidecarHealth? _health;
  final GitHubSessionStatus _session;
  final List<BuildRunSummary> _runs;
  final Map<int, List<BuildArtifactSummary>> _artifactsByRunId;
  final Duration sessionDelay;

  @override
  Future<DesktopTaskSnapshot> downloadBuildArtifact({
    required int runId,
    required int artifactId,
    String? outputDir,
  }) async {
    return const DesktopTaskSnapshot(
      id: 'task-download',
      kind: 'artifact.download',
      state: 'pending',
      message: 'pending',
      output: <String>[],
      result: <String, dynamic>{},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Future<GitHubSessionStatus> ensureGitHubFork() async {
    return _session;
  }

  @override
  Future<ConnectResult> connectDevice(String serial) async {
    return ConnectResult(
      connected: true,
      mode: DeviceConnectionMode.abk,
      device: DeviceConnectionState(
        serial: serial,
        agentHost: '127.0.0.1',
        agentPort: 48765,
        connected: true,
        mode: DeviceConnectionMode.abk,
        lastError: null,
        lastDetected: const <DetectedDevice>[
          DetectedDevice(
            serial: 'ABC123',
            status: 'device',
            detail: 'model:zorn product:abk',
          ),
        ],
        lastDetectRaw: '',
      ),
      agent: const AgentHealth(
        status: 'ok',
        protocolVersion: 'abk-agent-v1',
        port: 48765,
        appVersion: '1.0.0',
        appVersionCode: 1,
        rootGranted: true,
        managerAccessKind: 'native_manager',
        managerDiagnostic: null,
        capabilities: <String>['diagnostics.export'],
      ),
    );
  }

  @override
  void close() {}

  @override
  Future<DeviceDetectionResult> detectDevices() async {
    if (detectError != null) {
      throw detectError!;
    }
    return _detectionResult ??
        const DeviceDetectionResult(
          devices: <DetectedDevice>[
            DetectedDevice(
              serial: 'ABC123',
              status: 'device',
              detail: 'model:zorn product:abk',
            ),
          ],
          raw: 'List of devices attached',
        );
  }

  @override
  Future<DeviceConnectionState> disconnectDevice() async {
    return DeviceConnectionState.disconnected();
  }

  @override
  Future<DeviceConnectionState> getDeviceState() async {
    return _deviceState ??
        DeviceConnectionState.disconnected(
          lastDetected: const <DetectedDevice>[
            DetectedDevice(
              serial: 'ABC123',
              status: 'device',
              detail: 'model:zorn product:abk',
            ),
          ],
        );
  }

  @override
  Future<BuildDispatchResult> getBuildRun(int runId) async {
    return const BuildDispatchResult(
      ok: true,
      repo: 'foo/bar',
      dryRun: false,
      total: 0,
      run: null,
      runs: <BuildRunSummary>[],
      dispatches: <BuildDispatchItem>[],
      warnings: <String>[],
      error: null,
    );
  }

  @override
  Future<GitHubSessionStatus> getGitHubSession() async {
    if (sessionDelay > Duration.zero) {
      await Future<void>.delayed(sessionDelay);
    }
    return _session;
  }

  @override
  Future<SidecarHealth> getHealth() async {
    return _health ??
        SidecarHealth(
          status: 'ok',
          protocolVersion: 'abk-desktop-sidecar-v1',
          sidecar: const SidecarEndpoint(host: '127.0.0.1', port: 38765),
          device: await getDeviceState(),
          agent: null,
        );
  }

  @override
  Future<DesktopTaskSnapshot> getTask(String taskId) async {
    return const DesktopTaskSnapshot(
      id: 'task',
      kind: 'build.gki',
      state: 'succeeded',
      message: 'done',
      output: <String>[],
      result: <String, dynamic>{},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Future<DesktopTaskSnapshot> cancelTask(String taskId) async {
    return DesktopTaskSnapshot(
      id: taskId,
      kind: 'local.build.source.sync',
      state: 'cancelled',
      message: 'cancelled',
      output: const <String>[],
      result: const <String, dynamic>{'cancelable': false},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Future<DesktopTaskSnapshot> exportDiagnostics() async {
    return const DesktopTaskSnapshot(
      id: 'task-diagnostics',
      kind: 'diagnostics.export',
      state: 'pending',
      message: 'pending',
      output: <String>[],
      result: <String, dynamic>{},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Uri taskDownloadUri(String taskId) =>
      Uri.parse('http://127.0.0.1:38765/api/v1/tasks/$taskId/download');

  @override
  Future<List<BuildArtifactSummary>> listBuildArtifacts(int runId) async {
    return _artifactsByRunId[runId] ?? const <BuildArtifactSummary>[];
  }

  @override
  Future<BuildDispatchResult> listBuildRuns({int limit = 20}) async {
    return BuildDispatchResult(
      ok: true,
      repo: 'foo/bar',
      dryRun: false,
      total: _runs.length,
      run: null,
      runs: _runs,
      dispatches: const <BuildDispatchItem>[],
      warnings: const <String>[],
      error: null,
    );
  }

  @override
  Future<GitHubLoginResult> pollGitHubLogin(String deviceCode) async {
    return GitHubLoginResult(
      state: 'authorized',
      session: _session,
      error: null,
    );
  }

  @override
  Future<ProxySettings> getProxySettings() async => const ProxySettings.empty();

  @override
  Future<ProxySettings> saveProxySettings(
    Map<String, dynamic> request,
  ) async {
    return ProxySettings(
      httpProxy: request['httpProxy'] as String?,
      httpsProxy: request['httpsProxy'] as String?,
      allProxy: request['allProxy'] as String?,
      noProxy: request['noProxy'] as String?,
    );
  }

  @override
  Future<RuntimeBuildSummary?> getRuntimeBuildSummary() async {
    return const RuntimeBuildSummary(
      androidVersion: 'android14',
      kernelVersion: '6.1',
      subLevel: '162',
      osPatchLevel: '2025-05',
      revision: 'r11',
    );
  }

  @override
  Future<GitHubLoginChallenge> startGitHubLogin() async {
    return const GitHubLoginChallenge(
      deviceCode: 'device',
      userCode: 'user',
      verificationUri: 'https://github.com/login/device',
      verificationUriComplete: null,
      expiresIn: 900,
      interval: 5,
    );
  }

  @override
  Future<DesktopTaskSnapshot> startGkiBuild(
    Map<String, dynamic> request,
  ) async {
    return const DesktopTaskSnapshot(
      id: 'task-build',
      kind: 'build.gki',
      state: 'pending',
      message: 'pending',
      output: <String>[],
      result: <String, dynamic>{},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Future<List<LocalBuildBackendDescriptor>> getLocalBuildBackends() async {
    return const <LocalBuildBackendDescriptor>[
      LocalBuildBackendDescriptor(
        kind: LocalBuildBackendKind.script,
        label: 'Script adapter',
        available: true,
        isGlobalDefault: true,
        installSupported: false,
        installLabel: null,
        installDetail: null,
        authorizationRequired: false,
        authorizationKind: null,
        authorizationMessage: null,
        capabilities: LocalBuildBackendCapabilities(
          family: 'script',
          hostOwnedPaths: true,
          supportsSourceSync: true,
          supportsBuildExecution: true,
          supportsProfileProjection: true,
          notes: <String>[],
        ),
        detail: null,
      ),
    ];
  }

  @override
  Future<DesktopTaskSnapshot> installLocalBuildBackend(
    LocalBuildBackendKind kind,
    Map<String, dynamic> request,
  ) async {
    return const DesktopTaskSnapshot(
      id: 'task-local-backend-install',
      kind: 'local.backend.install',
      state: 'pending',
      message: 'pending',
      output: <String>[],
      result: <String, dynamic>{},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Future<List<SupportedKernelLine>> getLocalBuildCatalog() async {
    return const <SupportedKernelLine>[
      SupportedKernelLine(
        id: 'android14/6.1',
        androidVersion: 'android14',
        kernelVersion: '6.1',
        displayName: 'android14 / 6.1',
        branchMonthFormat: 'YYYY-MM',
        scriptTemplatePath: '/tmp/local-build/AOSP_Kernel_A14_6.1',
        scriptTemplateAvailable: true,
      ),
    ];
  }

  @override
  Future<LocalBuildSettings> updateLocalBuildSettings(
    Map<String, dynamic> request,
  ) async {
    return const LocalBuildSettings(
      globalDefaultBackendKind: LocalBuildBackendKind.script,
      activeSourceInstanceId: null,
      scriptRootDir: null,
      workspaceDir: null,
      profileStoreDir: null,
    );
  }

  @override
  Future<LocalBuildSourceInstancesResponse> getLocalBuildSourceInstances() async {
    return const LocalBuildSourceInstancesResponse(
      settings: LocalBuildSettings(
        globalDefaultBackendKind: LocalBuildBackendKind.script,
        activeSourceInstanceId: null,
        scriptRootDir: null,
        workspaceDir: null,
        profileStoreDir: null,
      ),
      sourceInstances: <LocalBuildSourceInstance>[],
    );
  }

  @override
  Future<LocalBuildSourceInstance> createLocalBuildSourceInstance(
    Map<String, dynamic> request,
  ) async {
    return const LocalBuildSourceInstance(
      id: 'android14-6.1@2026-07',
      displayName: 'android14/6.1@2026-07',
      kernelLineId: 'android14/6.1',
      androidVersion: 'android14',
      kernelVersion: '6.1',
      branchMonth: '2026-07',
      cacheRoot: '/tmp/local-build/cache/android14-6.1@2026-07',
      workingTreeRoot: '/tmp/local-build/worktrees/android14-6.1@2026-07',
      state: 'draft',
      createdAtMs: 1,
      updatedAtMs: 1,
      lastSyncedAtMs: null,
      activeBackendKind: null,
      lastTaskId: null,
      lastError: null,
      materialized: null,
    );
  }

  @override
  Future<DesktopTaskSnapshot> syncLocalBuildSourceInstance(
    String sourceInstanceId,
    Map<String, dynamic> request,
  ) async {
    return const DesktopTaskSnapshot(
      id: 'task-local-source-sync',
      kind: 'local.build.source.sync',
      state: 'pending',
      message: 'pending',
      output: <String>[],
      result: <String, dynamic>{},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Future<LocalBuildProfilesResponse> getLocalBuildProfiles() async {
    return const LocalBuildProfilesResponse(
      settings: LocalBuildSettings(
        globalDefaultBackendKind: LocalBuildBackendKind.script,
        activeSourceInstanceId: null,
        scriptRootDir: null,
        workspaceDir: null,
        profileStoreDir: null,
      ),
      profiles: <LocalBuildProfile>[],
    );
  }

  @override
  Future<LocalBuildProfile> saveLocalBuildProfile(
    Map<String, dynamic> request,
  ) async {
    return LocalBuildProfile(
      id: 'profile-1',
      name: (request['name'] as String?) ?? 'Profile 1',
      sourceInstanceId: (request['sourceInstanceId'] as String?) ?? '',
      backendKind: LocalBuildBackendKind.script,
      build: Map<String, dynamic>.from(
        request['build'] as Map? ?? const <String, dynamic>{},
      ),
      createdAtMs: 1,
      updatedAtMs: 1,
      lastBuiltAtMs: null,
      lastTaskId: null,
      lastError: null,
    );
  }

  @override
  Future<DesktopTaskSnapshot> buildLocalBuildProfile(
    String profileId,
    Map<String, dynamic> request,
  ) async {
    return const DesktopTaskSnapshot(
      id: 'task-local-profile-build',
      kind: 'local.build.profile.build',
      state: 'pending',
      message: 'pending',
      output: <String>[],
      result: <String, dynamic>{},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Future<List<LocalBuildArtifactEntry>> getLocalBuildArtifacts() async =>
      const <LocalBuildArtifactEntry>[];

  @override
  Future<List<LocalBuildLogEntry>> getLocalBuildLogs() async =>
      const <LocalBuildLogEntry>[];

  @override
  Future<LocalBuildStatus> getLocalBuildStatus() async {
    return const LocalBuildStatus(
      available: true,
      scriptRoot: '/tmp/local-build',
      initScriptPath: '/tmp/local-build/init.sh',
      rebuildScriptPath: '/tmp/local-build/rebuild.sh',
      envFilePath: '/tmp/local-build/.local-build/env.sh',
      stateDir: '/tmp/local-build/.local-build',
      sourcesDir: '/tmp/local-build/.local-build/sources',
      workspaceDir: '/tmp/local-build/.local-build/workspace',
      artifactsDir: '/tmp/local-build/.local-build/workspace/artifacts',
      logsDir: '/tmp/local-build/.local-build/workspace/logs',
      cacheDir: '/tmp/local-build/.local-build/workspace/cache',
      kernelRoot: '/tmp/local-build/.local-build/workspace/kernel',
      hasEnvFile: false,
      workspaceReady: false,
      templateRoot: null,
      templateName: null,
      templateAndroidVersion: null,
      templateKernelVersion: null,
      subLevel: null,
      osPatchLevel: null,
      templateBranch: null,
      templateCommonBranch: null,
      branchMonth: null,
      customExternalModulesRoot: null,
      customExternalModulesManifest: null,
      latestLogPath: null,
      supportedTemplates: <LocalBuildTemplate>[],
    );
  }

  @override
  Future<DesktopTaskSnapshot> initLocalBuild(
    Map<String, dynamic> request,
  ) async {
    return const DesktopTaskSnapshot(
      id: 'task-local-init',
      kind: 'local.build.init',
      state: 'pending',
      message: 'pending',
      output: <String>[],
      result: <String, dynamic>{},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Future<DesktopTaskSnapshot> rebuildLocalBuild(
    Map<String, dynamic> request,
  ) async {
    return const DesktopTaskSnapshot(
      id: 'task-local-rebuild',
      kind: 'local.build.rebuild',
      state: 'pending',
      message: 'pending',
      output: <String>[],
      result: <String, dynamic>{},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Future<GitHubSessionStatus> syncGitHubFork() async {
    return _session;
  }

  @override
  Future<GitHubSessionStatus> logoutGitHub() async {
    return const GitHubSessionStatus(
      ok: true,
      loggedIn: false,
      repo: 'foo/bar',
      needsFork: true,
      needsSync: false,
      behindBy: 0,
      aheadBy: 0,
      userLogin: null,
      forkFullName: null,
      signingKeyAvailable: false,
      signingKeySource: null,
      downloadDir: '/tmp',
    );
  }

  @override
  Future<String?> setDownloadDirectory(String path) async => path;

  @override
  Future<AbkRuntimeEnvelope> getRuntime() async {
    return const AbkRuntimeEnvelope(
      rootGranted: false,
      managerAccessKind: 'no_root',
      managerDiagnostic: null,
      runtimeStatus: null,
    );
  }

  @override
  Future<RootGrantsEnvelope> getRootGrants() async {
    return const RootGrantsEnvelope(
      rootGranted: false,
      managerAccessKind: 'no_root',
      managerDiagnostic: null,
      apps: <RootGrantApp>[],
    );
  }

  @override
  Future<KernelFeaturesEnvelope> getKernelFeatures() async {
    return const KernelFeaturesEnvelope(
      rootGranted: false,
      managerAccessKind: 'no_root',
      managerDiagnostic: null,
      items: <KernelFeatureItem>[
        KernelFeatureItem(
          id: 'adb_root',
          checked: false,
          enabled: true,
          status: 'supported',
        ),
      ],
    );
  }

  @override
  Future<PackageInfoSummary?> getPackageInfo(String packageName) async => null;

  @override
  Future<ShellOperationResult> setRootGrantAllowed(
    String packageName,
    bool allowed,
  ) async {
    return const ShellOperationResult(success: true, output: <String>['ok']);
  }

  @override
  Future<Uint8List?> getRootGrantIcon(String packageName) async => null;

  @override
  Future<SusfsEnvelope> getSusfs() async {
    return const SusfsEnvelope(
      rootGranted: false,
      status: null,
      config: <String, dynamic>{},
      error: null,
    );
  }

  @override
  Future<DesktopTaskSnapshot> applySusfs(Map<String, dynamic> config) async {
    return const DesktopTaskSnapshot(
      id: 'task-susfs',
      kind: 'susfs.apply',
      state: 'pending',
      message: 'pending',
      output: <String>[],
      result: <String, dynamic>{},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Future<ShellOperationResult> setKernelFeatureEnabled(
    String featureId,
    bool enabled,
  ) async {
    return const ShellOperationResult(success: true, output: <String>['ok']);
  }

  @override
  Future<ShellOperationResult> setRuntimeModuleEnabled(
    String moduleId,
    bool enabled,
  ) async {
    return const ShellOperationResult(success: true, output: <String>['ok']);
  }

  @override
  Future<ShellOperationResult> setRuntimeModulePendingUninstall(
    String moduleId,
    bool pending,
  ) async {
    return const ShellOperationResult(success: true, output: <String>['ok']);
  }

  @override
  Future<DesktopTaskSnapshot> runRuntimeModuleAction(String moduleId) async {
    return const DesktopTaskSnapshot(
      id: 'task-module-action',
      kind: 'runtime.module.action',
      state: 'pending',
      message: 'pending',
      output: <String>[],
      result: <String, dynamic>{},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Future<DesktopTaskSnapshot> installModule(String zipPath) async {
    return const DesktopTaskSnapshot(
      id: 'task-install-module',
      kind: 'install.module',
      state: 'pending',
      message: 'pending',
      output: <String>[],
      result: <String, dynamic>{},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Uri runtimeModuleWebUiUri(String moduleId, {String? relativePath}) {
    return Uri.parse('http://127.0.0.1/$moduleId');
  }

  static const GitHubSessionStatus _defaultSession = GitHubSessionStatus(
    ok: true,
    loggedIn: true,
    repo: 'foo/bar',
    needsFork: false,
    needsSync: false,
    behindBy: 0,
    aheadBy: 0,
    userLogin: 'tester',
    forkFullName: 'tester/ABK',
    signingKeyAvailable: true,
    signingKeySource: 'config',
    downloadDir: '/tmp',
  );
}
