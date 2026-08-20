import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ui';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;

import '../../core/api/abk_sidecar_api.dart';
import '../../core/localization/app_strings.dart';
import '../../core/models/build_models.dart';
import '../../core/models/device_models.dart';
import '../../core/state/dashboard_controller.dart';
import 'runtime_module_catalog.dart';

const _missing = Object();

final devicePageControllerProvider =
    StateNotifierProvider<DevicePageController, DevicePageState>((ref) {
      return DevicePageController(api: ref.read(sidecarApiProvider));
    });

class DevicePageState {
  const DevicePageState({
    required this.isRefreshing,
    required this.runtime,
    required this.runtimeError,
    required this.rootGrants,
    required this.rootGrantError,
    required this.rootGrantLoading,
    required this.packageInfoByPackage,
    required this.packageInfoLoadingPackage,
    required this.rootGrantSavingPackage,
    required this.kernelFeatures,
    required this.kernelFeatureError,
    required this.kernelFeatureBusyIds,
    required this.susfs,
    required this.susfsError,
    required this.susfsConfigDraft,
    required this.susfsDraftDirty,
    required this.susfsSaving,
    required this.runtimeModuleRepositories,
    required this.repositoryUrlDraft,
    required this.repositoryLoading,
    required this.repositoryError,
    required this.refreshingRepositoryIds,
    required this.installingCatalogModuleIds,
    required this.localModulePath,
    required this.localInstallBusy,
    required this.moduleBusyIds,
    required this.modulePendingBusyIds,
    required this.moduleActionBusyIds,
    required this.tasks,
    required this.taskOrder,
    required this.lastError,
    required this.infoMessage,
  });

  factory DevicePageState.initial() {
    return const DevicePageState(
      isRefreshing: false,
      runtime: null,
      runtimeError: null,
      rootGrants: null,
      rootGrantError: null,
      rootGrantLoading: false,
      packageInfoByPackage: <String, PackageInfoSummary>{},
      packageInfoLoadingPackage: null,
      rootGrantSavingPackage: null,
      kernelFeatures: null,
      kernelFeatureError: null,
      kernelFeatureBusyIds: <String>{},
      susfs: null,
      susfsError: null,
      susfsConfigDraft: '',
      susfsDraftDirty: false,
      susfsSaving: false,
      runtimeModuleRepositories: <RuntimeModuleRepository>[],
      repositoryUrlDraft: '',
      repositoryLoading: false,
      repositoryError: null,
      refreshingRepositoryIds: <String>{},
      installingCatalogModuleIds: <String>{},
      localModulePath: null,
      localInstallBusy: false,
      moduleBusyIds: <String>{},
      modulePendingBusyIds: <String>{},
      moduleActionBusyIds: <String>{},
      tasks: <DesktopTaskSnapshot>[],
      taskOrder: <String>[],
      lastError: null,
      infoMessage: null,
    );
  }

  final bool isRefreshing;
  final AbkRuntimeEnvelope? runtime;
  final String? runtimeError;
  final RootGrantsEnvelope? rootGrants;
  final String? rootGrantError;
  final bool rootGrantLoading;
  final Map<String, PackageInfoSummary> packageInfoByPackage;
  final String? packageInfoLoadingPackage;
  final String? rootGrantSavingPackage;
  final KernelFeaturesEnvelope? kernelFeatures;
  final String? kernelFeatureError;
  final Set<String> kernelFeatureBusyIds;
  final SusfsEnvelope? susfs;
  final String? susfsError;
  final String susfsConfigDraft;
  final bool susfsDraftDirty;
  final bool susfsSaving;
  final List<RuntimeModuleRepository> runtimeModuleRepositories;
  final String repositoryUrlDraft;
  final bool repositoryLoading;
  final String? repositoryError;
  final Set<String> refreshingRepositoryIds;
  final Set<String> installingCatalogModuleIds;
  final String? localModulePath;
  final bool localInstallBusy;
  final Set<String> moduleBusyIds;
  final Set<String> modulePendingBusyIds;
  final Set<String> moduleActionBusyIds;
  final List<DesktopTaskSnapshot> tasks;
  final List<String> taskOrder;
  final String? lastError;
  final String? infoMessage;

  List<AbkRuntimeModule> get installedModules =>
      runtime?.runtimeStatus?.modules ?? const <AbkRuntimeModule>[];

  DesktopTaskSnapshot? taskById(String taskId) {
    for (final task in tasks) {
      if (task.id == taskId) {
        return task;
      }
    }
    return null;
  }

  DevicePageState copyWith({
    bool? isRefreshing,
    Object? runtime = _missing,
    Object? runtimeError = _missing,
    Object? rootGrants = _missing,
    Object? rootGrantError = _missing,
    bool? rootGrantLoading,
    Map<String, PackageInfoSummary>? packageInfoByPackage,
    Object? packageInfoLoadingPackage = _missing,
    Object? rootGrantSavingPackage = _missing,
    Object? kernelFeatures = _missing,
    Object? kernelFeatureError = _missing,
    Set<String>? kernelFeatureBusyIds,
    Object? susfs = _missing,
    Object? susfsError = _missing,
    String? susfsConfigDraft,
    bool? susfsDraftDirty,
    bool? susfsSaving,
    List<RuntimeModuleRepository>? runtimeModuleRepositories,
    String? repositoryUrlDraft,
    bool? repositoryLoading,
    Object? repositoryError = _missing,
    Set<String>? refreshingRepositoryIds,
    Set<String>? installingCatalogModuleIds,
    Object? localModulePath = _missing,
    bool? localInstallBusy,
    Set<String>? moduleBusyIds,
    Set<String>? modulePendingBusyIds,
    Set<String>? moduleActionBusyIds,
    List<DesktopTaskSnapshot>? tasks,
    List<String>? taskOrder,
    Object? lastError = _missing,
    Object? infoMessage = _missing,
  }) {
    return DevicePageState(
      isRefreshing: isRefreshing ?? this.isRefreshing,
      runtime: identical(runtime, _missing)
          ? this.runtime
          : runtime as AbkRuntimeEnvelope?,
      runtimeError: identical(runtimeError, _missing)
          ? this.runtimeError
          : runtimeError as String?,
      rootGrants: identical(rootGrants, _missing)
          ? this.rootGrants
          : rootGrants as RootGrantsEnvelope?,
      rootGrantError: identical(rootGrantError, _missing)
          ? this.rootGrantError
          : rootGrantError as String?,
      rootGrantLoading: rootGrantLoading ?? this.rootGrantLoading,
      packageInfoByPackage: packageInfoByPackage ?? this.packageInfoByPackage,
      packageInfoLoadingPackage: identical(packageInfoLoadingPackage, _missing)
          ? this.packageInfoLoadingPackage
          : packageInfoLoadingPackage as String?,
      rootGrantSavingPackage: identical(rootGrantSavingPackage, _missing)
          ? this.rootGrantSavingPackage
          : rootGrantSavingPackage as String?,
      kernelFeatures: identical(kernelFeatures, _missing)
          ? this.kernelFeatures
          : kernelFeatures as KernelFeaturesEnvelope?,
      kernelFeatureError: identical(kernelFeatureError, _missing)
          ? this.kernelFeatureError
          : kernelFeatureError as String?,
      kernelFeatureBusyIds: kernelFeatureBusyIds ?? this.kernelFeatureBusyIds,
      susfs: identical(susfs, _missing) ? this.susfs : susfs as SusfsEnvelope?,
      susfsError: identical(susfsError, _missing)
          ? this.susfsError
          : susfsError as String?,
      susfsConfigDraft: susfsConfigDraft ?? this.susfsConfigDraft,
      susfsDraftDirty: susfsDraftDirty ?? this.susfsDraftDirty,
      susfsSaving: susfsSaving ?? this.susfsSaving,
      runtimeModuleRepositories:
          runtimeModuleRepositories ?? this.runtimeModuleRepositories,
      repositoryUrlDraft: repositoryUrlDraft ?? this.repositoryUrlDraft,
      repositoryLoading: repositoryLoading ?? this.repositoryLoading,
      repositoryError: identical(repositoryError, _missing)
          ? this.repositoryError
          : repositoryError as String?,
      refreshingRepositoryIds:
          refreshingRepositoryIds ?? this.refreshingRepositoryIds,
      installingCatalogModuleIds:
          installingCatalogModuleIds ?? this.installingCatalogModuleIds,
      localModulePath: identical(localModulePath, _missing)
          ? this.localModulePath
          : localModulePath as String?,
      localInstallBusy: localInstallBusy ?? this.localInstallBusy,
      moduleBusyIds: moduleBusyIds ?? this.moduleBusyIds,
      modulePendingBusyIds: modulePendingBusyIds ?? this.modulePendingBusyIds,
      moduleActionBusyIds: moduleActionBusyIds ?? this.moduleActionBusyIds,
      tasks: tasks ?? this.tasks,
      taskOrder: taskOrder ?? this.taskOrder,
      lastError: identical(lastError, _missing)
          ? this.lastError
          : lastError as String?,
      infoMessage: identical(infoMessage, _missing)
          ? this.infoMessage
          : infoMessage as String?,
    );
  }
}

class DevicePageController extends StateNotifier<DevicePageState> {
  DevicePageController({
    required this.api,
    RuntimeModuleCatalogClient? catalogClient,
    http.Client? downloadClient,
  }) : _catalogClient = catalogClient ?? RuntimeModuleCatalogClient(),
       _downloadClient = downloadClient ?? http.Client(),
       super(DevicePageState.initial()) {
    _seedRuntimeModuleRepositories();
  }

  final AbkSidecarApi api;
  final RuntimeModuleCatalogClient _catalogClient;
  final http.Client _downloadClient;

  AppStrings get _strings =>
      AppStrings.fromLocale(PlatformDispatcher.instance.locale);

  void _seedRuntimeModuleRepositories() {
    if (state.runtimeModuleRepositories.isNotEmpty) {
      return;
    }
    state = state.copyWith(
      runtimeModuleRepositories: <RuntimeModuleRepository>[
        RuntimeModuleRepository(
          id: officialRuntimeModuleRepositoryId,
          url: officialRuntimeModuleRepositoryUrl,
          indexJsonUrl: '',
          name: _strings.deviceModuleOfficialRepo,
          modules: const <RuntimeModuleCatalogItem>[],
          lastUpdated: 0,
          error: null,
          skippedCount: 0,
        ),
      ],
    );
  }

  Future<void> refreshAll() async {
    if (state.isRefreshing) return;
    state = state.copyWith(
      isRefreshing: true,
      lastError: null,
      infoMessage: null,
    );
    try {
      final results = await Future.wait<Object?>(<Future<Object?>>[
        api.getRuntime(),
        api.getRootGrants(),
        api.getSusfs(),
      ]);
      if (!mounted) return;
      final runtime = results[0] as AbkRuntimeEnvelope;
      final rootGrants = results[1] as RootGrantsEnvelope;
      final susfs = results[2] as SusfsEnvelope;
      state = state.copyWith(
        isRefreshing: false,
        runtime: runtime,
        runtimeError: runtime.managerDiagnostic,
        rootGrants: rootGrants,
        rootGrantError: rootGrants.managerDiagnostic,
        susfs: susfs,
        susfsError: susfs.error,
        susfsConfigDraft: state.susfsDraftDirty
            ? state.susfsConfigDraft
            : susfs.prettyConfig(),
      );
      await _refreshKernelFeaturesOnly();
      await refreshAllRuntimeRepositories();
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(isRefreshing: false, lastError: error.message);
    }
  }

  Future<void> loadPackageInfo(String packageName) async {
    final cleanPackage = packageName.trim();
    if (cleanPackage.isEmpty) return;
    if (state.packageInfoLoadingPackage == cleanPackage) return;
    state = state.copyWith(
      packageInfoLoadingPackage: cleanPackage,
      lastError: null,
    );
    try {
      final info = await api.getPackageInfo(cleanPackage);
      if (!mounted) return;
      final updated = <String, PackageInfoSummary>{
        ...state.packageInfoByPackage,
      };
      if (info != null) {
        updated[cleanPackage] = info;
      }
      state = state.copyWith(
        packageInfoLoadingPackage: null,
        packageInfoByPackage: updated,
      );
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(
        packageInfoLoadingPackage: null,
        lastError: error.message,
      );
    }
  }

  Future<void> setRootGrantAllowed(String packageName, bool allowed) async {
    final cleanPackage = packageName.trim();
    if (cleanPackage.isEmpty) return;
    if (state.rootGrantSavingPackage != null) return;
    state = state.copyWith(
      rootGrantSavingPackage: cleanPackage,
      lastError: null,
      infoMessage: null,
    );
    try {
      final result = await api.setRootGrantAllowed(cleanPackage, allowed);
      if (!mounted) return;
      final apps = state.rootGrants?.apps
          .map((app) {
            if (app.packageName != cleanPackage) return app;
            return RootGrantApp(
              packageName: app.packageName,
              label: app.label,
              uid: app.uid,
              userName: app.userName,
              isSystemApp: app.isSystemApp,
              profile: RootGrantProfile(
                name: app.profile.name,
                currentUid: app.profile.currentUid,
                allowSu: allowed,
                rootUseDefault: app.profile.rootUseDefault,
                rootTemplate: app.profile.rootTemplate,
                uid: app.profile.uid,
                gid: app.profile.gid,
                groups: app.profile.groups,
                capabilities: app.profile.capabilities,
                context: app.profile.context,
                namespace: app.profile.namespace,
                flags: app.profile.flags,
                nonRootUseDefault: app.profile.nonRootUseDefault,
                umountModules: app.profile.umountModules,
                rules: app.profile.rules,
              ),
              profileLoaded: app.profileLoaded,
            );
          })
          .toList(growable: false);
      state = state.copyWith(
        rootGrantSavingPackage: null,
        rootGrants: state.rootGrants == null
            ? null
            : RootGrantsEnvelope(
                rootGranted: state.rootGrants!.rootGranted,
                managerAccessKind: state.rootGrants!.managerAccessKind,
                managerDiagnostic: state.rootGrants!.managerDiagnostic,
                apps: apps ?? const <RootGrantApp>[],
              ),
        infoMessage: result.summary ?? _strings.deviceRootUpdated,
      );
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(
        rootGrantSavingPackage: null,
        lastError: error.message,
      );
    }
  }

  Future<void> setKernelFeatureEnabled(String featureId, bool enabled) async {
    final cleanId = featureId.trim();
    if (cleanId.isEmpty) return;
    if (state.kernelFeatureBusyIds.contains(cleanId)) return;
    state = state.copyWith(
      kernelFeatureBusyIds: <String>{...state.kernelFeatureBusyIds, cleanId},
      lastError: null,
      infoMessage: null,
    );
    try {
      final result = await api.setKernelFeatureEnabled(cleanId, enabled);
      if (!mounted) return;
      state = state.copyWith(
        kernelFeatureBusyIds: <String>{...state.kernelFeatureBusyIds}
          ..remove(cleanId),
        infoMessage: result.summary ?? _strings.deviceKernelFeatureUpdated,
      );
      await _refreshDeviceStatusAfterKernelFeatureChange();
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(
        kernelFeatureBusyIds: <String>{...state.kernelFeatureBusyIds}
          ..remove(cleanId),
        kernelFeatureError: error.statusCode == 404
            ? _strings.deviceKernelFeaturesUnsupported
            : error.message,
        lastError: error.statusCode == 404 ? null : error.message,
      );
    }
  }

  void updateSusfsDraft(String value) {
    state = state.copyWith(susfsConfigDraft: value, susfsDraftDirty: true);
  }

  void resetSusfsDraft() {
    state = state.copyWith(
      susfsConfigDraft: state.susfs?.prettyConfig() ?? '',
      susfsDraftDirty: false,
      susfsError: null,
    );
  }

  Future<void> applySusfsConfig(Map<String, dynamic> config) async {
    final pretty = const JsonEncoder.withIndent('  ').convert(config);
    state = state.copyWith(
      susfsConfigDraft: pretty,
      susfsDraftDirty: true,
      susfsError: null,
    );
    await _applySusfsConfig(config);
  }

  Future<void> applySusfsDraft() async {
    if (state.susfsSaving) return;
    final draft = state.susfsConfigDraft.trim();
    if (draft.isEmpty) {
      state = state.copyWith(susfsError: _strings.deviceSusfsDraftEmpty);
      return;
    }
    final dynamic decoded;
    try {
      decoded = jsonDecode(draft);
    } catch (_) {
      state = state.copyWith(susfsError: _strings.deviceSusfsDraftInvalid);
      return;
    }
    if (decoded is! Map<String, dynamic>) {
      state = state.copyWith(susfsError: _strings.deviceSusfsDraftInvalid);
      return;
    }
    await _applySusfsConfig(decoded);
  }

  Future<void> _applySusfsConfig(Map<String, dynamic> config) async {
    if (state.susfsSaving) return;
    state = state.copyWith(susfsSaving: true, susfsError: null);
    try {
      final accepted = await api.applySusfs(config);
      if (!mounted) return;
      _upsertTask(accepted);
      state = state.copyWith(
        susfsSaving: false,
        infoMessage: _strings.deviceTaskQueued,
      );
      unawaited(_trackTask(accepted.id));
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(susfsSaving: false, susfsError: error.message);
    }
  }

  Future<void> setRuntimeModuleEnabled(String moduleId, bool enabled) async {
    final cleanId = moduleId.trim();
    if (cleanId.isEmpty) return;
    if (state.moduleBusyIds.contains(cleanId)) return;
    state = state.copyWith(
      moduleBusyIds: <String>{...state.moduleBusyIds, cleanId},
      lastError: null,
    );
    try {
      final result = await api.setRuntimeModuleEnabled(cleanId, enabled);
      if (!mounted) return;
      state = state.copyWith(
        moduleBusyIds: <String>{...state.moduleBusyIds}..remove(cleanId),
        infoMessage: result.summary ?? _strings.deviceModuleUpdated,
      );
      await refreshRuntimeOnly();
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(
        moduleBusyIds: <String>{...state.moduleBusyIds}..remove(cleanId),
        lastError: error.message,
      );
    }
  }

  Future<void> setRuntimeModulePendingUninstall(
    String moduleId,
    bool pending,
  ) async {
    final cleanId = moduleId.trim();
    if (cleanId.isEmpty) return;
    if (state.modulePendingBusyIds.contains(cleanId)) return;
    state = state.copyWith(
      modulePendingBusyIds: <String>{...state.modulePendingBusyIds, cleanId},
      lastError: null,
    );
    try {
      final result = await api.setRuntimeModulePendingUninstall(
        cleanId,
        pending,
      );
      if (!mounted) return;
      state = state.copyWith(
        modulePendingBusyIds: <String>{...state.modulePendingBusyIds}
          ..remove(cleanId),
        infoMessage: result.summary ?? _strings.deviceModuleUpdated,
      );
      await refreshRuntimeOnly();
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(
        modulePendingBusyIds: <String>{...state.modulePendingBusyIds}
          ..remove(cleanId),
        lastError: error.message,
      );
    }
  }

  Future<void> runRuntimeModuleAction(String moduleId) async {
    final cleanId = moduleId.trim();
    if (cleanId.isEmpty) return;
    if (state.moduleActionBusyIds.contains(cleanId)) return;
    state = state.copyWith(
      moduleActionBusyIds: <String>{...state.moduleActionBusyIds, cleanId},
      lastError: null,
    );
    try {
      final accepted = await api.runRuntimeModuleAction(cleanId);
      if (!mounted) return;
      _upsertTask(accepted);
      state = state.copyWith(
        moduleActionBusyIds: <String>{...state.moduleActionBusyIds}
          ..remove(cleanId),
        infoMessage: _strings.deviceTaskQueued,
      );
      unawaited(_trackTask(accepted.id));
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(
        moduleActionBusyIds: <String>{...state.moduleActionBusyIds}
          ..remove(cleanId),
        lastError: error.message,
      );
    }
  }

  void updateRepositoryUrlDraft(String value) {
    state = state.copyWith(repositoryUrlDraft: value);
  }

  Future<void> addRuntimeModuleRepository() async {
    final cleanUrl = normalizeModuleCatalogUrl(state.repositoryUrlDraft);
    if (cleanUrl.isEmpty) return;
    if (state.repositoryLoading) return;
    final existing = state.runtimeModuleRepositories.firstWhere(
      (repo) => repo.url.toLowerCase() == cleanUrl.toLowerCase(),
      orElse: () => const RuntimeModuleRepository(
        id: '',
        url: '',
        indexJsonUrl: '',
        name: '',
        modules: <RuntimeModuleCatalogItem>[],
        lastUpdated: 0,
        error: null,
        skippedCount: 0,
      ),
    );
    if (existing.id.isNotEmpty) {
      await refreshRuntimeModuleRepository(existing.id);
      state = state.copyWith(repositoryUrlDraft: '');
      return;
    }
    final repository = RuntimeModuleRepository(
      id: DateTime.now().microsecondsSinceEpoch.toString(),
      url: cleanUrl,
      indexJsonUrl: '',
      name: moduleCatalogFallbackName(
        cleanUrl,
        _strings.deviceModuleRepoDefault,
      ),
      modules: const <RuntimeModuleCatalogItem>[],
      lastUpdated: 0,
      error: null,
      skippedCount: 0,
    );
    state = state.copyWith(
      runtimeModuleRepositories: <RuntimeModuleRepository>[
        repository,
        ...state.runtimeModuleRepositories,
      ],
      repositoryUrlDraft: '',
    );
    await refreshRuntimeModuleRepository(repository.id);
  }

  void removeRuntimeModuleRepository(String id) {
    state = state.copyWith(
      runtimeModuleRepositories: state.runtimeModuleRepositories
          .where((repo) => repo.id != id)
          .toList(growable: false),
    );
  }

  Future<void> refreshAllRuntimeRepositories() async {
    final repositories = state.runtimeModuleRepositories;
    for (final repository in repositories) {
      await refreshRuntimeModuleRepository(repository.id);
    }
  }

  Future<void> refreshRuntimeModuleRepository(String id) async {
    final repository = state.runtimeModuleRepositories.firstWhere(
      (repo) => repo.id == id,
      orElse: () => const RuntimeModuleRepository(
        id: '',
        url: '',
        indexJsonUrl: '',
        name: '',
        modules: <RuntimeModuleCatalogItem>[],
        lastUpdated: 0,
        error: null,
        skippedCount: 0,
      ),
    );
    if (repository.id.isEmpty) return;
    state = state.copyWith(
      refreshingRepositoryIds: <String>{...state.refreshingRepositoryIds, id},
      repositoryLoading: true,
      repositoryError: null,
    );
    final refreshed = await _catalogClient.fetchRepository(repository);
    if (!mounted) return;
    final remainingRefreshing = <String>{...state.refreshingRepositoryIds}
      ..remove(id);
    state = state.copyWith(
      runtimeModuleRepositories: state.runtimeModuleRepositories
          .map((repo) => repo.id == id ? refreshed : repo)
          .toList(growable: false),
      refreshingRepositoryIds: remainingRefreshing,
      repositoryLoading: remainingRefreshing.isNotEmpty,
      repositoryError: refreshed.error,
    );
  }

  void updateLocalModulePath(String? path) {
    state = state.copyWith(localModulePath: path);
  }

  Future<void> installLocalModule() async {
    final path = state.localModulePath?.trim();
    if (path == null || path.isEmpty || state.localInstallBusy) return;
    state = state.copyWith(localInstallBusy: true, lastError: null);
    try {
      final accepted = await api.installModule(path);
      if (!mounted) return;
      _upsertTask(accepted);
      state = state.copyWith(
        localInstallBusy: false,
        infoMessage: _strings.deviceTaskQueued,
      );
      unawaited(_trackTask(accepted.id));
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(localInstallBusy: false, lastError: error.message);
    }
  }

  Future<void> installRepositoryModule(
    MergedRuntimeCatalogModule module,
  ) async {
    final key = module.module.id.trim().ifEmpty(module.module.zipUrl);
    if (state.installingCatalogModuleIds.contains(key)) return;
    state = state.copyWith(
      installingCatalogModuleIds: <String>{
        ...state.installingCatalogModuleIds,
        key,
      },
      lastError: null,
    );
    try {
      final tempDir = await Directory.systemTemp.createTemp(
        'abk-runtime-module-',
      );
      final fileName = _downloadFileNameForModule(module.module);
      final targetFile = File('${tempDir.path}/$fileName');
      final response = await _downloadClient.get(
        Uri.parse(module.module.zipUrl),
      );
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw SidecarException(
          'Failed to download module: HTTP ${response.statusCode}',
        );
      }
      await targetFile.writeAsBytes(response.bodyBytes);
      final accepted = await api.installModule(targetFile.path);
      if (!mounted) return;
      _upsertTask(accepted);
      state = state.copyWith(
        installingCatalogModuleIds: <String>{
          ...state.installingCatalogModuleIds,
        }..remove(key),
        infoMessage: _strings.deviceTaskQueued,
      );
      unawaited(_trackTask(accepted.id));
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(
        installingCatalogModuleIds: <String>{
          ...state.installingCatalogModuleIds,
        }..remove(key),
        lastError: error.message,
      );
    } on http.ClientException catch (error) {
      if (!mounted) return;
      state = state.copyWith(
        installingCatalogModuleIds: <String>{
          ...state.installingCatalogModuleIds,
        }..remove(key),
        lastError: error.message,
      );
    }
  }

  Future<void> refreshRuntimeOnly() async {
    try {
      final runtime = await api.getRuntime();
      if (!mounted) return;
      state = state.copyWith(
        runtime: runtime,
        runtimeError: runtime.managerDiagnostic,
      );
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(lastError: error.message);
    }
  }

  void clearInfoMessage() {
    state = state.copyWith(infoMessage: null);
  }

  void clearLastError() {
    state = state.copyWith(lastError: null);
  }

  Future<void> _trackTask(String taskId) async {
    try {
      while (true) {
        await Future<void>.delayed(const Duration(seconds: 1));
        if (!mounted) return;
        final task = await api.getTask(taskId);
        if (!mounted) return;
        _upsertTask(task);
        if (task.isTerminal) {
          if (task.kind == 'install.module' ||
              task.kind == 'runtime.module.action') {
            await refreshRuntimeOnly();
          } else if (task.kind == 'susfs.apply') {
            await _refreshSusfsOnly();
          }
          return;
        }
      }
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(lastError: error.message);
    }
  }

  Future<void> _refreshSusfsOnly() async {
    try {
      final susfs = await api.getSusfs();
      if (!mounted) return;
      state = state.copyWith(
        susfs: susfs,
        susfsError: susfs.error,
        susfsConfigDraft: susfs.prettyConfig(),
        susfsDraftDirty: false,
      );
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(susfsError: error.message);
    }
  }

  Future<void> _refreshKernelFeaturesOnly() async {
    try {
      final kernelFeatures = await api.getKernelFeatures();
      if (!mounted) return;
      state = state.copyWith(
        kernelFeatures: kernelFeatures,
        kernelFeatureError: kernelFeatures.managerDiagnostic,
      );
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(
        kernelFeatureError: error.statusCode == 404
            ? _strings.deviceKernelFeaturesUnsupported
            : error.message,
      );
    }
  }

  Future<void> _refreshDeviceStatusAfterKernelFeatureChange() async {
    try {
      final results = await Future.wait<Object?>(<Future<Object?>>[
        api.getRuntime(),
        api.getRootGrants(),
        api.getSusfs(),
        api.getKernelFeatures(),
      ]);
      if (!mounted) return;
      final runtime = results[0] as AbkRuntimeEnvelope;
      final rootGrants = results[1] as RootGrantsEnvelope;
      final susfs = results[2] as SusfsEnvelope;
      final kernelFeatures = results[3] as KernelFeaturesEnvelope;
      state = state.copyWith(
        runtime: runtime,
        runtimeError: runtime.managerDiagnostic,
        rootGrants: rootGrants,
        rootGrantError: rootGrants.managerDiagnostic,
        susfs: susfs,
        susfsError: susfs.error,
        susfsConfigDraft: state.susfsDraftDirty
            ? state.susfsConfigDraft
            : susfs.prettyConfig(),
        kernelFeatures: kernelFeatures,
        kernelFeatureError: kernelFeatures.managerDiagnostic,
      );
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(
        lastError: error.message,
        kernelFeatureError: error.statusCode == 404
            ? _strings.deviceKernelFeaturesUnsupported
            : state.kernelFeatureError,
      );
    }
  }

  void _upsertTask(DesktopTaskSnapshot task) {
    final tasks = <DesktopTaskSnapshot>[
      task,
      ...state.tasks.where((existing) => existing.id != task.id),
    ];
    final order = <String>[
      task.id,
      ...state.taskOrder.where((id) => id != task.id),
    ];
    state = state.copyWith(tasks: tasks, taskOrder: order);
  }

  String _downloadFileNameForModule(RuntimeModuleCatalogItem module) {
    final uri = Uri.tryParse(module.zipUrl);
    final lastSegment = uri?.pathSegments.lastOrNull;
    final base = lastSegment?.trim();
    if (base != null && base.isNotEmpty) {
      return base;
    }
    final slug = module.name.trim().ifEmpty('module').replaceAll(' ', '-');
    return slug.endsWith('.zip') ? slug : '$slug-module.zip';
  }

  @override
  void dispose() {
    _catalogClient.close();
    _downloadClient.close();
    super.dispose();
  }
}

extension on String {
  String ifEmpty(String fallback) => isEmpty ? fallback : this;
}

extension<T> on Iterable<T> {
  T? get lastOrNull => isEmpty ? null : last;
}
