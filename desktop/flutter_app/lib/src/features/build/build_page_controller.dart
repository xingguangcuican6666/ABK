import 'dart:async';
import 'dart:ui';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/abk_sidecar_api.dart';
import '../../core/localization/app_strings.dart';
import '../../core/models/build_models.dart';
import '../../core/state/dashboard_controller.dart';
import 'build_form_state.dart';
import 'build_module_catalog.dart';
import 'kernel_support.dart';

const _missing = Object();

final buildPageControllerProvider =
    StateNotifierProvider<BuildPageController, BuildPageState>((ref) {
      final controller = BuildPageController(api: ref.read(sidecarApiProvider));
      return controller;
    });

enum BuildFormScope { remote, local }

class BuildPageState {
  const BuildPageState({
    required this.isBootstrapping,
    required this.isRefreshing,
    required this.isSubmitting,
    required this.isLoadingLogin,
    required this.isPollingLogin,
    required this.isForkBusy,
    required this.session,
    required this.loginChallenge,
    required this.runtime,
    required this.form,
    required this.formDirty,
    required this.localBuildStatus,
    required this.localBuildStatusLoading,
    required this.localBuildBranchMonth,
    required this.localBuildForceInit,
    required this.localBuildSkipDeps,
    required this.localBuildCleanOut,
    required this.localBuildReseed,
    required this.localBuildNoPackage,
    required this.localSettingsSaving,
    required this.localBackends,
    required this.localCatalog,
    required this.localSettings,
    required this.localScriptRootDirDraft,
    required this.localWorkspaceDirDraft,
    required this.localProfileStoreDirDraft,
    required this.localSourceInstances,
    required this.localProfiles,
    required this.selectedLocalSourceInstanceId,
    required this.selectedLocalProfileId,
    required this.localSourceKernelLineId,
    required this.localProfileNameDraft,
    required this.localProfileBackendKind,
    required this.localForm,
    required this.localFormDirty,
    required this.localSelectedModules,
    required this.localArtifacts,
    required this.localLogs,
    required this.selectedModules,
    required this.runs,
    required this.selectedRunId,
    required this.artifacts,
    required this.artifactsByRunId,
    required this.moduleRepositories,
    required this.moduleCatalogLoading,
    required this.moduleCatalogError,
    required this.repositoryUrlDraft,
    required this.manualModuleUrl,
    required this.manualModuleStage,
    required this.tasks,
    required this.taskOrder,
    required this.lastError,
    required this.infoMessage,
  });

  factory BuildPageState.initial() {
    return BuildPageState(
      isBootstrapping: true,
      isRefreshing: false,
      isSubmitting: false,
      isLoadingLogin: false,
      isPollingLogin: false,
      isForkBusy: false,
      session: null,
      loginChallenge: null,
      runtime: null,
      form: BuildFormState.defaults(),
      formDirty: false,
      localBuildStatus: null,
      localBuildStatusLoading: false,
      localBuildBranchMonth: '',
      localBuildForceInit: false,
      localBuildSkipDeps: false,
      localBuildCleanOut: false,
      localBuildReseed: false,
      localBuildNoPackage: false,
      localSettingsSaving: false,
      localBackends: const <LocalBuildBackendDescriptor>[],
      localCatalog: const <SupportedKernelLine>[],
      localSettings: null,
      localScriptRootDirDraft: '',
      localWorkspaceDirDraft: '',
      localProfileStoreDirDraft: '',
      localSourceInstances: const <LocalBuildSourceInstance>[],
      localProfiles: const <LocalBuildProfile>[],
      selectedLocalSourceInstanceId: null,
      selectedLocalProfileId: null,
      localSourceKernelLineId: '',
      localProfileNameDraft: '',
      localProfileBackendKind: null,
      localForm: BuildFormState.defaults(),
      localFormDirty: false,
      localSelectedModules: const <SelectedBuildModule>[],
      localArtifacts: const <LocalBuildArtifactEntry>[],
      localLogs: const <LocalBuildLogEntry>[],
      selectedModules: const <SelectedBuildModule>[],
      runs: const <BuildRunSummary>[],
      selectedRunId: null,
      artifacts: const <BuildArtifactSummary>[],
      artifactsByRunId: const <int, List<BuildArtifactSummary>>{},
      moduleRepositories: const <BuildModuleRepository>[],
      moduleCatalogLoading: false,
      moduleCatalogError: null,
      repositoryUrlDraft: '',
      manualModuleUrl: '',
      manualModuleStage: 'after_patch',
      tasks: const <DesktopTaskSnapshot>[],
      taskOrder: const <String>[],
      lastError: null,
      infoMessage: null,
    );
  }

  final bool isBootstrapping;
  final bool isRefreshing;
  final bool isSubmitting;
  final bool isLoadingLogin;
  final bool isPollingLogin;
  final bool isForkBusy;
  final GitHubSessionStatus? session;
  final GitHubLoginChallenge? loginChallenge;
  final RuntimeBuildSummary? runtime;
  final BuildFormState form;
  final bool formDirty;
  final LocalBuildStatus? localBuildStatus;
  final bool localBuildStatusLoading;
  final String localBuildBranchMonth;
  final bool localBuildForceInit;
  final bool localBuildSkipDeps;
  final bool localBuildCleanOut;
  final bool localBuildReseed;
  final bool localBuildNoPackage;
  final bool localSettingsSaving;
  final List<LocalBuildBackendDescriptor> localBackends;
  final List<SupportedKernelLine> localCatalog;
  final LocalBuildSettings? localSettings;
  final String localScriptRootDirDraft;
  final String localWorkspaceDirDraft;
  final String localProfileStoreDirDraft;
  final List<LocalBuildSourceInstance> localSourceInstances;
  final List<LocalBuildProfile> localProfiles;
  final String? selectedLocalSourceInstanceId;
  final String? selectedLocalProfileId;
  final String localSourceKernelLineId;
  final String localProfileNameDraft;
  final LocalBuildBackendKind? localProfileBackendKind;
  final BuildFormState localForm;
  final bool localFormDirty;
  final List<SelectedBuildModule> localSelectedModules;
  final List<LocalBuildArtifactEntry> localArtifacts;
  final List<LocalBuildLogEntry> localLogs;
  final List<SelectedBuildModule> selectedModules;
  final List<BuildRunSummary> runs;
  final int? selectedRunId;
  final List<BuildArtifactSummary> artifacts;
  final Map<int, List<BuildArtifactSummary>> artifactsByRunId;
  final List<BuildModuleRepository> moduleRepositories;
  final bool moduleCatalogLoading;
  final String? moduleCatalogError;
  final String repositoryUrlDraft;
  final String manualModuleUrl;
  final String manualModuleStage;
  final List<DesktopTaskSnapshot> tasks;
  final List<String> taskOrder;
  final String? lastError;
  final String? infoMessage;

  bool get loggedIn => session?.loggedIn ?? false;
  bool get needsFork => session?.needsFork ?? true;
  bool get needsSync => session?.needsSync ?? false;
  bool get canBuild => loggedIn && !needsFork && !needsSync && !isSubmitting;

  List<DesktopTaskSnapshot> get localBuildTasks => taskOrder
      .map(taskById)
      .whereType<DesktopTaskSnapshot>()
      .where(
        (task) =>
            task.kind.startsWith('local.build') ||
            task.kind.startsWith('local.backend'),
      )
      .toList(growable: false);

  DesktopTaskSnapshot? get activeLocalTask {
    for (final task in localBuildTasks) {
      if (!task.isTerminal) {
        return task;
      }
    }
    return null;
  }

  LocalBuildSourceInstance? get selectedLocalSourceInstance {
    final targetId = selectedLocalSourceInstanceId;
    if (targetId == null) {
      return null;
    }
    for (final source in localSourceInstances) {
      if (source.id == targetId) {
        return source;
      }
    }
    return null;
  }

  LocalBuildProfile? get selectedLocalProfile {
    final targetId = selectedLocalProfileId;
    if (targetId == null) {
      return null;
    }
    for (final profile in localProfiles) {
      if (profile.id == targetId) {
        return profile;
      }
    }
    return null;
  }

  LocalBuildBackendDescriptor? get effectiveLocalBackendDescriptor {
    final kind =
        selectedLocalProfile?.backendKind ??
        localProfileBackendKind ??
        localSettings?.globalDefaultBackendKind;
    if (kind == null) {
      return null;
    }
    for (final backend in localBackends) {
      if (backend.kind == kind) {
        return backend;
      }
    }
    return null;
  }

  DesktopTaskSnapshot? taskById(String taskId) {
    for (final task in tasks) {
      if (task.id == taskId) {
        return task;
      }
    }
    return null;
  }

  BuildPageState copyWith({
    bool? isBootstrapping,
    bool? isRefreshing,
    bool? isSubmitting,
    bool? isLoadingLogin,
    bool? isPollingLogin,
    bool? isForkBusy,
    Object? session = _missing,
    Object? loginChallenge = _missing,
    Object? runtime = _missing,
    BuildFormState? form,
    bool? formDirty,
    Object? localBuildStatus = _missing,
    bool? localBuildStatusLoading,
    String? localBuildBranchMonth,
    bool? localBuildForceInit,
    bool? localBuildSkipDeps,
    bool? localBuildCleanOut,
    bool? localBuildReseed,
    bool? localBuildNoPackage,
    bool? localSettingsSaving,
    List<LocalBuildBackendDescriptor>? localBackends,
    List<SupportedKernelLine>? localCatalog,
    Object? localSettings = _missing,
    String? localScriptRootDirDraft,
    String? localWorkspaceDirDraft,
    String? localProfileStoreDirDraft,
    List<LocalBuildSourceInstance>? localSourceInstances,
    List<LocalBuildProfile>? localProfiles,
    Object? selectedLocalSourceInstanceId = _missing,
    Object? selectedLocalProfileId = _missing,
    String? localSourceKernelLineId,
    String? localProfileNameDraft,
    Object? localProfileBackendKind = _missing,
    BuildFormState? localForm,
    bool? localFormDirty,
    List<SelectedBuildModule>? localSelectedModules,
    List<LocalBuildArtifactEntry>? localArtifacts,
    List<LocalBuildLogEntry>? localLogs,
    List<SelectedBuildModule>? selectedModules,
    List<BuildRunSummary>? runs,
    Object? selectedRunId = _missing,
    List<BuildArtifactSummary>? artifacts,
    Map<int, List<BuildArtifactSummary>>? artifactsByRunId,
    List<BuildModuleRepository>? moduleRepositories,
    bool? moduleCatalogLoading,
    Object? moduleCatalogError = _missing,
    String? repositoryUrlDraft,
    String? manualModuleUrl,
    String? manualModuleStage,
    List<DesktopTaskSnapshot>? tasks,
    List<String>? taskOrder,
    Object? lastError = _missing,
    Object? infoMessage = _missing,
  }) {
    return BuildPageState(
      isBootstrapping: isBootstrapping ?? this.isBootstrapping,
      isRefreshing: isRefreshing ?? this.isRefreshing,
      isSubmitting: isSubmitting ?? this.isSubmitting,
      isLoadingLogin: isLoadingLogin ?? this.isLoadingLogin,
      isPollingLogin: isPollingLogin ?? this.isPollingLogin,
      isForkBusy: isForkBusy ?? this.isForkBusy,
      session: identical(session, _missing)
          ? this.session
          : session as GitHubSessionStatus?,
      loginChallenge: identical(loginChallenge, _missing)
          ? this.loginChallenge
          : loginChallenge as GitHubLoginChallenge?,
      runtime: identical(runtime, _missing)
          ? this.runtime
          : runtime as RuntimeBuildSummary?,
      form: form ?? this.form,
      formDirty: formDirty ?? this.formDirty,
      localBuildStatus: identical(localBuildStatus, _missing)
          ? this.localBuildStatus
          : localBuildStatus as LocalBuildStatus?,
      localBuildStatusLoading:
          localBuildStatusLoading ?? this.localBuildStatusLoading,
      localBuildBranchMonth:
          localBuildBranchMonth ?? this.localBuildBranchMonth,
      localBuildForceInit: localBuildForceInit ?? this.localBuildForceInit,
      localBuildSkipDeps: localBuildSkipDeps ?? this.localBuildSkipDeps,
      localBuildCleanOut: localBuildCleanOut ?? this.localBuildCleanOut,
      localBuildReseed: localBuildReseed ?? this.localBuildReseed,
      localBuildNoPackage: localBuildNoPackage ?? this.localBuildNoPackage,
      localSettingsSaving: localSettingsSaving ?? this.localSettingsSaving,
      localBackends: localBackends ?? this.localBackends,
      localCatalog: localCatalog ?? this.localCatalog,
      localSettings: identical(localSettings, _missing)
          ? this.localSettings
          : localSettings as LocalBuildSettings?,
      localScriptRootDirDraft:
          localScriptRootDirDraft ?? this.localScriptRootDirDraft,
      localWorkspaceDirDraft:
          localWorkspaceDirDraft ?? this.localWorkspaceDirDraft,
      localProfileStoreDirDraft:
          localProfileStoreDirDraft ?? this.localProfileStoreDirDraft,
      localSourceInstances: localSourceInstances ?? this.localSourceInstances,
      localProfiles: localProfiles ?? this.localProfiles,
      selectedLocalSourceInstanceId:
          identical(selectedLocalSourceInstanceId, _missing)
          ? this.selectedLocalSourceInstanceId
          : selectedLocalSourceInstanceId as String?,
      selectedLocalProfileId: identical(selectedLocalProfileId, _missing)
          ? this.selectedLocalProfileId
          : selectedLocalProfileId as String?,
      localSourceKernelLineId:
          localSourceKernelLineId ?? this.localSourceKernelLineId,
      localProfileNameDraft:
          localProfileNameDraft ?? this.localProfileNameDraft,
      localProfileBackendKind: identical(localProfileBackendKind, _missing)
          ? this.localProfileBackendKind
          : localProfileBackendKind as LocalBuildBackendKind?,
      localForm: localForm ?? this.localForm,
      localFormDirty: localFormDirty ?? this.localFormDirty,
      localSelectedModules: localSelectedModules ?? this.localSelectedModules,
      localArtifacts: localArtifacts ?? this.localArtifacts,
      localLogs: localLogs ?? this.localLogs,
      selectedModules: selectedModules ?? this.selectedModules,
      runs: runs ?? this.runs,
      selectedRunId: identical(selectedRunId, _missing)
          ? this.selectedRunId
          : selectedRunId as int?,
      artifacts: artifacts ?? this.artifacts,
      artifactsByRunId: artifactsByRunId ?? this.artifactsByRunId,
      moduleRepositories: moduleRepositories ?? this.moduleRepositories,
      moduleCatalogLoading: moduleCatalogLoading ?? this.moduleCatalogLoading,
      moduleCatalogError: identical(moduleCatalogError, _missing)
          ? this.moduleCatalogError
          : moduleCatalogError as String?,
      repositoryUrlDraft: repositoryUrlDraft ?? this.repositoryUrlDraft,
      manualModuleUrl: manualModuleUrl ?? this.manualModuleUrl,
      manualModuleStage: manualModuleStage ?? this.manualModuleStage,
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

class BuildPageController extends StateNotifier<BuildPageState> {
  BuildPageController({
    required this.api,
    this.bootstrapOnInit = true,
    BuildModuleCatalogClient? catalogClient,
  }) : _catalogClient = catalogClient ?? BuildModuleCatalogClient(),
       super(BuildPageState.initial()) {
    if (bootstrapOnInit) {
      unawaited(bootstrap());
    }
  }

  final AbkSidecarApi api;
  final bool bootstrapOnInit;
  final BuildModuleCatalogClient _catalogClient;
  final Map<String, BuildExternalModuleMetadata> _moduleMetadataCache =
      <String, BuildExternalModuleMetadata>{};

  AppStrings get _strings =>
      AppStrings.fromLocale(PlatformDispatcher.instance.locale);

  Future<void> bootstrap() async {
    if (state.isBootstrapping) {
      await _refreshAll(prefillFromRuntime: true);
    }
  }

  Future<void> refreshAll({bool prefillFromRuntime = false}) async {
    await _refreshAll(prefillFromRuntime: prefillFromRuntime);
  }

  Future<void> _refreshAll({required bool prefillFromRuntime}) async {
    if (state.isRefreshing) {
      return;
    }
    if (!mounted) return;

    final loadModuleCatalog = state.moduleRepositories.isEmpty;
    state = state.copyWith(
      isRefreshing: true,
      lastError: null,
      infoMessage: null,
      moduleCatalogLoading: loadModuleCatalog ? true : state.moduleCatalogLoading,
      moduleCatalogError: loadModuleCatalog ? null : state.moduleCatalogError,
    );

    String? remoteError;
    try {
      RuntimeBuildSummary? runtime;
      final Future<RuntimeBuildSummary?> runtimeFuture = api
          .getRuntimeBuildSummary()
          .then<RuntimeBuildSummary?>((value) => value)
          .catchError((Object error) => null);
      final localBuildStatusFuture = api.getLocalBuildStatus();
      final localBackendsFuture = api.getLocalBuildBackends();
      final localCatalogFuture = api.getLocalBuildCatalog();
      final localSourcesFuture = api.getLocalBuildSourceInstances();
      final localProfilesFuture = api.getLocalBuildProfiles();
      final localArtifactsFuture = api.getLocalBuildArtifacts();
      final localLogsFuture = api.getLocalBuildLogs();

      final localBuildStatus = await localBuildStatusFuture;
      final localBackends = await localBackendsFuture;
      final localCatalog = await localCatalogFuture;
      final localSources = await localSourcesFuture;
      final localProfiles = await localProfilesFuture;
      final localArtifacts = await localArtifactsFuture;
      final localLogs = await localLogsFuture;
      final form = state.form;
      final selectedModules = form.customModules == state.form.customModules
          ? state.selectedModules
          : parseSelectedBuildModules(form.customModules);

      final selectedLocalSourceInstanceId = _resolveLocalSourceInstanceId(
        candidates: localSources.sourceInstances,
        preferredId: state.selectedLocalSourceInstanceId,
        activeId: localSources.settings.activeSourceInstanceId,
      );
      final selectedLocalSourceInstance = localSources.sourceInstances
          .where((source) => source.id == selectedLocalSourceInstanceId)
          .firstOrNull;
      final selectedLocalProfileId = _resolveLocalProfileId(
        candidates: localProfiles.profiles,
        preferredId: state.selectedLocalProfileId,
        selectedSourceInstanceId: selectedLocalSourceInstanceId,
      );
      final selectedLocalProfile = localProfiles.profiles
          .where((profile) => profile.id == selectedLocalProfileId)
          .firstOrNull;
      final localFormSeed = state.localFormDirty
          ? _syncFormToSource(state.localForm, selectedLocalSourceInstance)
          : selectedLocalProfile == null
          ? _syncFormToSource(state.localForm, selectedLocalSourceInstance)
          : _syncFormToSource(
              BuildFormState.fromRequest(selectedLocalProfile.build),
              selectedLocalSourceInstance,
            );
      final localSelectedModules = state.localFormDirty
          ? parseSelectedBuildModules(localFormSeed.customModules)
          : selectedLocalProfile != null ||
                localFormSeed.customModules != state.localForm.customModules
          ? parseSelectedBuildModules(localFormSeed.customModules)
          : state.localSelectedModules;
      if (!mounted) return;

      state = state.copyWith(
        runtime: runtime ?? state.runtime,
        localBuildStatus: localBuildStatus,
        localBuildStatusLoading: false,
        localBuildBranchMonth: state.localBuildBranchMonth.trim().isNotEmpty
            ? state.localBuildBranchMonth
            : (localBuildStatus.branchMonth ?? form.osPatchLevel),
        form: form,
        formDirty: state.formDirty,
        localBackends: localBackends,
        localCatalog: localCatalog,
        localSettings: localSources.settings,
        localScriptRootDirDraft: state.localScriptRootDirDraft.isEmpty
            ? (localSources.settings.scriptRootDir ?? '')
            : state.localScriptRootDirDraft,
        localWorkspaceDirDraft: state.localWorkspaceDirDraft.isEmpty
            ? (localSources.settings.workspaceDir ?? '')
            : state.localWorkspaceDirDraft,
        localProfileStoreDirDraft: state.localProfileStoreDirDraft.isEmpty
            ? (localSources.settings.profileStoreDir ?? '')
            : state.localProfileStoreDirDraft,
        localSourceInstances: localSources.sourceInstances,
        localProfiles: localProfiles.profiles,
        selectedLocalSourceInstanceId: selectedLocalSourceInstanceId,
        selectedLocalProfileId: selectedLocalProfileId,
        localSourceKernelLineId:
            selectedLocalSourceInstance?.kernelLineId ??
            state.localSourceKernelLineId,
        localProfileNameDraft:
            selectedLocalProfile?.name ?? state.localProfileNameDraft,
        localProfileBackendKind:
            selectedLocalProfile?.backendKind ?? state.localProfileBackendKind,
        localForm: localFormSeed,
        localFormDirty: state.localFormDirty,
        localSelectedModules: localSelectedModules,
        localArtifacts: localArtifacts,
        localLogs: localLogs,
        selectedModules: selectedModules,
        isBootstrapping: false,
      );
      runtime = await runtimeFuture;

      if (runtime != null && mounted) {
        final runtimeForm = prefillFromRuntime && !state.formDirty
            ? BuildFormState.defaults(runtime: runtime)
            : null;
        final runtimeSelectedModules = runtimeForm == null
            ? state.selectedModules
            : runtimeForm.customModules == state.form.customModules
            ? state.selectedModules
            : parseSelectedBuildModules(runtimeForm.customModules);
        state = state.copyWith(
          runtime: runtime,
          form: runtimeForm ?? state.form,
          selectedModules: runtimeSelectedModules,
        );
      }
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(
        isRefreshing: false,
        isBootstrapping: false,
        lastError: error.message,
      );
      return;
    }

    GitHubSessionStatus? session;
    List<BuildRunSummary>? filteredRuns;
    List<BuildModuleRepository>? repositories;
    String? moduleCatalogError;

    try {
      session = await api.getGitHubSession();
    } on SidecarException catch (error) {
      remoteError ??= error.message;
    }

    try {
      final runsResult = await api.listBuildRuns(limit: 20);
      filteredRuns = runsResult.runs
          .where((run) => run.looksLikeKernelBuild)
          .toList(growable: false);
    } on SidecarException catch (error) {
      remoteError ??= error.message;
    }

    try {
      repositories = await _loadModuleRepositories();
      moduleCatalogError = repositories
          .firstWhere(
            (repository) => repository.error != null,
            orElse: () => const BuildModuleRepository(
              url: '',
              name: '',
              modules: <BuildModuleCatalogItem>[],
              error: null,
              indexUrl: null,
            ),
          )
          .error;
    } catch (error) {
      moduleCatalogError = error.toString();
    }

    if (!mounted) return;
    state = state.copyWith(
      session: session ?? state.session,
      runs: filteredRuns ?? state.runs,
      moduleRepositories: repositories ?? state.moduleRepositories,
      moduleCatalogLoading: false,
      moduleCatalogError: moduleCatalogError,
      isRefreshing: false,
      isBootstrapping: false,
      lastError: remoteError,
    );

    if (filteredRuns != null) {
      final selectedRunId = _resolveSelectedRunId(filteredRuns);
      if (selectedRunId != null) {
        await selectRun(selectedRunId);
      } else {
        if (!mounted) return;
        state = state.copyWith(selectedRunId: null, artifacts: const []);
      }
    }
  }

  void updateForm(BuildFormState form) {
    final normalized = form.normalized();
    state = state.copyWith(
      form: normalized,
      formDirty: true,
      selectedModules: normalized.customModules == state.form.customModules
          ? state.selectedModules
          : parseSelectedBuildModules(normalized.customModules),
    );
  }

  void updateLocalForm(BuildFormState form) {
    final normalized = form.normalized();
    state = state.copyWith(
      localForm: normalized,
      localFormDirty: true,
      localSelectedModules:
          normalized.customModules == state.localForm.customModules
          ? state.localSelectedModules
          : parseSelectedBuildModules(normalized.customModules),
    );
  }

  Future<void> refreshLocalBuildStatus() async {
    if (!mounted || state.localBuildStatusLoading) return;
    state = state.copyWith(
      localBuildStatusLoading: true,
      lastError: null,
      infoMessage: null,
    );
    await _refreshAll(prefillFromRuntime: false);
    if (!mounted) return;
    state = state.copyWith(localBuildStatusLoading: false);
  }

  void updateLocalBuildBranchMonth(String value) {
    _applyLocalSourceDraft(branchMonth: value);
  }

  void updateLocalSourceKernelLineId(String value) {
    _applyLocalSourceDraft(kernelLineId: value);
  }

  void updateLocalBuildForceInit(bool value) {
    state = state.copyWith(localBuildForceInit: value);
  }

  void updateLocalBuildSkipDeps(bool value) {
    state = state.copyWith(localBuildSkipDeps: value);
  }

  void updateLocalBuildCleanOut(bool value) {
    state = state.copyWith(localBuildCleanOut: value);
  }

  void updateLocalBuildReseed(bool value) {
    state = state.copyWith(localBuildReseed: value);
  }

  void updateLocalBuildNoPackage(bool value) {
    state = state.copyWith(localBuildNoPackage: value);
  }

  void updateLocalProfileNameDraft(String value) {
    state = state.copyWith(localProfileNameDraft: value);
  }

  void updateLocalProfileBackendKind(LocalBuildBackendKind? value) {
    state = state.copyWith(localProfileBackendKind: value);
  }

  void updateLocalScriptRootDirDraft(String value) {
    state = state.copyWith(localScriptRootDirDraft: value);
  }

  void updateLocalWorkspaceDirDraft(String value) {
    state = state.copyWith(localWorkspaceDirDraft: value);
  }

  void updateLocalProfileStoreDirDraft(String value) {
    state = state.copyWith(localProfileStoreDirDraft: value);
  }

  Future<void> updateLocalDefaultBackendKind(
    LocalBuildBackendKind value,
  ) async {
    try {
      final settings = await api.updateLocalBuildSettings(<String, dynamic>{
        'globalDefaultBackendKind': value.name,
        'scriptRootDir': state.localScriptRootDirDraft.trim().isEmpty
            ? null
            : state.localScriptRootDirDraft.trim(),
        'workspaceDir': state.localWorkspaceDirDraft.trim().isEmpty
            ? null
            : state.localWorkspaceDirDraft.trim(),
        'profileStoreDir': state.localProfileStoreDirDraft.trim().isEmpty
            ? null
            : state.localProfileStoreDirDraft.trim(),
      });
      if (!mounted) return;
      state = state.copyWith(
        localSettings: settings,
        localScriptRootDirDraft: settings.scriptRootDir ?? '',
        localWorkspaceDirDraft: settings.workspaceDir ?? '',
        localProfileStoreDirDraft: settings.profileStoreDir ?? '',
      );
      await refreshLocalBuildStatus();
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(lastError: error.message);
    }
  }

  Future<void> installLocalBackend(
    LocalBuildBackendDescriptor backend, {
    String? sudoPassword,
  }) async {
    if (!mounted || state.isSubmitting || !backend.installSupported) return;
    state = state.copyWith(
      isSubmitting: true,
      lastError: null,
      infoMessage: null,
    );
    try {
      final accepted = await api.installLocalBuildBackend(backend.kind, <String, dynamic>{
        'sudoPassword': sudoPassword,
      });
      if (!mounted) return;
      _upsertTask(accepted);
      state = state.copyWith(
        isSubmitting: false,
        infoMessage: _strings.buildLocalBackendInstallQueued(backend.label),
      );
      unawaited(_trackTask(accepted.id));
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(isSubmitting: false, lastError: error.message);
    }
  }

  Future<void> saveLocalDirectorySettings() async {
    if (!mounted || state.localSettingsSaving) return;
    state = state.copyWith(
      localSettingsSaving: true,
      lastError: null,
      infoMessage: null,
    );
    try {
      final settings = await api.updateLocalBuildSettings(<String, dynamic>{
        'globalDefaultBackendKind':
            state.localSettings?.globalDefaultBackendKind.name,
        'scriptRootDir': state.localScriptRootDirDraft.trim().isEmpty
            ? null
            : state.localScriptRootDirDraft.trim(),
        'workspaceDir': state.localWorkspaceDirDraft.trim().isEmpty
            ? null
            : state.localWorkspaceDirDraft.trim(),
        'profileStoreDir': state.localProfileStoreDirDraft.trim().isEmpty
            ? null
            : state.localProfileStoreDirDraft.trim(),
      });
      if (!mounted) return;
      state = state.copyWith(
        localSettingsSaving: false,
        localSettings: settings,
        localScriptRootDirDraft: settings.scriptRootDir ?? '',
        localWorkspaceDirDraft: settings.workspaceDir ?? '',
        localProfileStoreDirDraft: settings.profileStoreDir ?? '',
        infoMessage: _strings.buildLocalDirectorySettingsSaved,
      );
      await refreshLocalBuildStatus();
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(
        localSettingsSaving: false,
        lastError: error.message,
      );
    }
  }

  Future<void> restoreLocalDirectorySettingsDefaults() async {
    if (!mounted || state.localSettingsSaving) return;
    state = state.copyWith(
      localSettingsSaving: true,
      lastError: null,
      infoMessage: null,
    );
    try {
      final settings = await api.updateLocalBuildSettings(<String, dynamic>{
        'globalDefaultBackendKind':
            state.localSettings?.globalDefaultBackendKind.name,
        'scriptRootDir': null,
        'workspaceDir': null,
        'profileStoreDir': null,
      });
      if (!mounted) return;
      state = state.copyWith(
        localSettingsSaving: false,
        localSettings: settings,
        localScriptRootDirDraft: '',
        localWorkspaceDirDraft: '',
        localProfileStoreDirDraft: '',
        infoMessage: _strings.buildLocalDirectorySettingsSaved,
      );
      await refreshLocalBuildStatus();
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(
        localSettingsSaving: false,
        lastError: error.message,
      );
    }
  }

  void selectLocalSourceInstance(String? sourceInstanceId) {
    final source = _findLocalSourceById(sourceInstanceId);
    final profile = source == null
        ? null
        : state.localProfiles
              .where((item) => item.sourceInstanceId == source.id)
              .firstOrNull;
    final nextForm = profile != null
        ? _syncFormToSource(BuildFormState.fromRequest(profile.build), source)
        : _syncFormToSourceDraft(
            state.localForm,
            kernelLineId: source?.kernelLineId ?? state.localSourceKernelLineId,
            branchMonth: source?.branchMonth ?? state.localBuildBranchMonth,
          );
    state = state.copyWith(
      selectedLocalSourceInstanceId: source?.id,
      selectedLocalProfileId: profile?.id,
      localSourceKernelLineId:
          source?.kernelLineId ?? state.localSourceKernelLineId,
      localBuildBranchMonth: source?.branchMonth ?? state.localBuildBranchMonth,
      localProfileNameDraft:
          profile?.name ??
          _defaultLocalProfileName(
            source?.kernelLineId ?? state.localSourceKernelLineId,
            source?.branchMonth ?? state.localBuildBranchMonth,
          ),
      localProfileBackendKind: profile?.backendKind,
      localForm: nextForm,
      localFormDirty: false,
      localSelectedModules: parseSelectedBuildModules(nextForm.customModules),
    );
  }

  void selectLocalProfile(String? profileId) {
    final profile = _findLocalProfileById(profileId);
    if (profile == null) {
      final selectedSource = state.selectedLocalSourceInstance;
      final nextForm = _syncFormToSourceDraft(
        state.localForm,
        kernelLineId:
            selectedSource?.kernelLineId ?? state.localSourceKernelLineId,
        branchMonth: selectedSource?.branchMonth ?? state.localBuildBranchMonth,
      );
      state = state.copyWith(
        selectedLocalProfileId: null,
        localProfileNameDraft: _defaultLocalProfileName(
          selectedSource?.kernelLineId ?? state.localSourceKernelLineId,
          selectedSource?.branchMonth ?? state.localBuildBranchMonth,
        ),
        localProfileBackendKind: null,
        localForm: nextForm,
        localFormDirty: false,
        localSelectedModules: parseSelectedBuildModules(nextForm.customModules),
      );
      return;
    }
    final source = _findLocalSourceById(profile.sourceInstanceId);
    final sourceKey = _sourceKeyFromSourceInstanceId(profile.sourceInstanceId);
    final nextForm = _syncFormToSource(
      BuildFormState.fromRequest(profile.build),
      source,
    );
    state = state.copyWith(
      selectedLocalProfileId: profile.id,
      selectedLocalSourceInstanceId: source?.id,
      localProfileNameDraft: profile.name,
      localProfileBackendKind: profile.backendKind,
      localSourceKernelLineId:
          source?.kernelLineId ??
          sourceKey?.kernelLineId ??
          state.localSourceKernelLineId,
      localBuildBranchMonth:
          source?.branchMonth ??
          sourceKey?.branchMonth ??
          state.localBuildBranchMonth,
      localForm: nextForm,
      localFormDirty: false,
      localSelectedModules: parseSelectedBuildModules(nextForm.customModules),
    );
  }

  Future<LocalBuildSourceInstance?> createLocalSourceInstance() async {
    final kernelLineId = state.localSourceKernelLineId.trim();
    final branchMonth = state.localBuildBranchMonth.trim();
    if (kernelLineId.isEmpty || branchMonth.isEmpty) {
      state = state.copyWith(lastError: _strings.buildLocalBranchMonthRequired);
      return null;
    }
    try {
      final source = await api.createLocalBuildSourceInstance(<String, dynamic>{
        'kernelLineId': kernelLineId,
        'branchMonth': branchMonth,
      });
      if (!mounted) return null;
      state = state.copyWith(
        selectedLocalSourceInstanceId: source.id,
        localBuildBranchMonth: source.branchMonth,
      );
      await refreshLocalBuildStatus();
      selectLocalSourceInstance(source.id);
      return _findLocalSourceById(source.id);
    } on SidecarException catch (error) {
      if (!mounted) return null;
      state = state.copyWith(lastError: error.message);
      return null;
    }
  }

  Future<void> syncSelectedLocalSourceInstance({String? sudoPassword}) async {
    if (state.isSubmitting) return;
    final selected = _findLocalSourceById(state.selectedLocalSourceInstanceId);
    final effectiveSource = selected != null && _sourceMatchesDraft(selected)
        ? selected
        : await createLocalSourceInstance();
    if (effectiveSource == null) {
      return;
    }
    state = state.copyWith(
      isSubmitting: true,
      localBuildStatusLoading: true,
      lastError: null,
      infoMessage: null,
    );
    try {
      final accepted = await api
          .syncLocalBuildSourceInstance(effectiveSource.id, <String, dynamic>{
            'backendKind':
                (state.localProfileBackendKind ??
                        state.localSettings?.globalDefaultBackendKind)
                    ?.name,
            'force': state.localBuildForceInit,
            'skipDeps': state.localBuildSkipDeps,
            'sudoPassword': sudoPassword,
          });
      if (!mounted) return;
      _upsertTask(accepted);
      state = state.copyWith(
        isSubmitting: false,
        localBuildStatusLoading: false,
        infoMessage: _strings.buildLocalInitQueued,
      );
      unawaited(_trackTask(accepted.id));
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(
        isSubmitting: false,
        localBuildStatusLoading: false,
        lastError: error.message,
      );
    }
  }

  Future<LocalBuildProfile?> saveLocalProfile() async {
    final source = _findLocalSourceById(state.selectedLocalSourceInstanceId);
    if (source == null) {
      state = state.copyWith(lastError: _strings.buildLocalBranchMonthRequired);
      return null;
    }
    final payload = <String, dynamic>{
      'id': state.selectedLocalProfileId,
      'name': state.localProfileNameDraft.trim().isEmpty
          ? null
          : state.localProfileNameDraft.trim(),
      'sourceInstanceId': source.id,
      'backendKind': state.localProfileBackendKind?.name,
      'build': _syncFormToSource(state.localForm, source).toRequest(),
    };
    try {
      final profile = await api.saveLocalBuildProfile(payload);
      if (!mounted) return null;
      state = state.copyWith(
        selectedLocalProfileId: profile.id,
        localProfileNameDraft: profile.name,
        localProfileBackendKind: profile.backendKind,
        localFormDirty: false,
      );
      await refreshLocalBuildStatus();
      selectLocalProfile(profile.id);
      return _findLocalProfileById(profile.id);
    } on SidecarException catch (error) {
      if (!mounted) return null;
      state = state.copyWith(lastError: error.message);
      return null;
    }
  }

  void updateRepositoryUrlDraft(String value) {
    state = state.copyWith(repositoryUrlDraft: value);
  }

  void updateManualModuleUrl(String value) {
    state = state.copyWith(manualModuleUrl: value);
  }

  void updateManualModuleStage(String value) {
    state = state.copyWith(manualModuleStage: value);
  }

  Future<GitHubLoginChallenge?> startLogin() async {
    if (state.isLoadingLogin || state.isPollingLogin) {
      return state.loginChallenge;
    }
    if (!mounted) return null;
    state = state.copyWith(
      isLoadingLogin: true,
      lastError: null,
      infoMessage: null,
    );
    try {
      final challenge = await api.startGitHubLogin();
      if (!mounted) return null;
      state = state.copyWith(
        isLoadingLogin: false,
        loginChallenge: challenge,
        infoMessage: _strings.buildInfoLoginStarted,
      );
      return challenge;
    } on SidecarException catch (error) {
      if (!mounted) return null;
      state = state.copyWith(isLoadingLogin: false, lastError: error.message);
      return null;
    }
  }

  Future<void> pollLoginUntilAuthorized() async {
    final challenge = state.loginChallenge;
    if (challenge == null || state.isPollingLogin) {
      return;
    }

    state = state.copyWith(
      isPollingLogin: true,
      lastError: null,
      infoMessage: null,
    );
    final expiresAt = DateTime.now().add(
      Duration(seconds: challenge.expiresIn),
    );
    var interval = Duration(seconds: challenge.interval);

    try {
      while (DateTime.now().isBefore(expiresAt)) {
        await Future<void>.delayed(interval);
        if (!mounted) return;
        final result = await api.pollGitHubLogin(challenge.deviceCode);
        if (!mounted) return;
        switch (result.state) {
          case 'authorized':
            state = state.copyWith(
              isPollingLogin: false,
              loginChallenge: null,
              session: result.session ?? state.session,
              infoMessage: _strings.buildInfoLoginComplete,
            );
            await _refreshAll(prefillFromRuntime: false);
            return;
          case 'pending':
          case 'authorization_pending':
            continue;
          case 'slow_down':
            interval += const Duration(seconds: 5);
            continue;
          case 'expired_token':
          case 'access_denied':
            state = state.copyWith(
              isPollingLogin: false,
              lastError: result.error ?? result.state,
            );
            return;
          default:
            state = state.copyWith(
              isPollingLogin: false,
              lastError: result.error ?? result.state,
            );
            return;
        }
      }
      if (!mounted) return;
      state = state.copyWith(
        isPollingLogin: false,
        lastError: _strings.buildErrorLoginTimedOut,
      );
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(isPollingLogin: false, lastError: error.message);
    }
  }

  Future<void> ensureFork() async {
    if (state.isForkBusy) return;
    if (!mounted) return;
    state = state.copyWith(
      isForkBusy: true,
      lastError: null,
      infoMessage: null,
    );
    try {
      final session = await api.ensureGitHubFork();
      if (!mounted) return;
      state = state.copyWith(
        isForkBusy: false,
        session: session,
        infoMessage: _strings.buildInfoForkReady,
      );
      await _refreshAll(prefillFromRuntime: false);
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(isForkBusy: false, lastError: error.message);
    }
  }

  Future<void> syncFork() async {
    if (state.isForkBusy) return;
    if (!mounted) return;
    state = state.copyWith(
      isForkBusy: true,
      lastError: null,
      infoMessage: null,
    );
    try {
      final session = await api.syncGitHubFork();
      if (!mounted) return;
      state = state.copyWith(
        isForkBusy: false,
        session: session,
        infoMessage: _strings.buildInfoForkSynced,
      );
      await _refreshAll(prefillFromRuntime: false);
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(isForkBusy: false, lastError: error.message);
    }
  }

  Future<void> refreshRuns() async {
    try {
      final runsResult = await api.listBuildRuns(limit: 20);
      final filteredRuns = runsResult.runs
          .where((run) => run.looksLikeKernelBuild)
          .toList(growable: false);
      if (!mounted) return;
      state = state.copyWith(runs: filteredRuns);
      final selectedRunId = _resolveSelectedRunId(filteredRuns);
      if (selectedRunId != null) {
        await selectRun(selectedRunId);
      } else {
        if (!mounted) return;
        state = state.copyWith(selectedRunId: null, artifacts: const []);
      }
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(lastError: error.message);
    }
  }

  Future<void> selectRun(int runId) async {
    if (!mounted) return;
    state = state.copyWith(
      selectedRunId: runId,
      lastError: null,
      artifacts: state.artifactsByRunId[runId] ?? const [],
    );
    try {
      final artifacts = await ensureArtifactsForRun(runId, forceRefresh: true);
      if (!mounted) return;
      state = state.copyWith(artifacts: artifacts);
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(lastError: error.message, artifacts: const []);
    }
  }

  Future<List<BuildArtifactSummary>> ensureArtifactsForRun(
    int runId, {
    bool forceRefresh = false,
  }) async {
    final cached = state.artifactsByRunId[runId];
    if (!forceRefresh && cached != null && cached.isNotEmpty) {
      return cached;
    }
    final artifacts = await api.listBuildArtifacts(runId);
    if (!mounted) return artifacts;
    state = state.copyWith(
      artifactsByRunId: <int, List<BuildArtifactSummary>>{
        ...state.artifactsByRunId,
        runId: artifacts,
      },
      artifacts: state.selectedRunId == runId ? artifacts : state.artifacts,
    );
    return artifacts;
  }

  Future<void> addModuleRepository() async {
    final url = state.repositoryUrlDraft.trim();
    if (url.isEmpty) return;
    if (!mounted) return;
    state = state.copyWith(
      moduleCatalogLoading: true,
      moduleCatalogError: null,
    );
    final repository = await _catalogClient.fetchRepository(url);
    if (!mounted) return;
    final repositories = <BuildModuleRepository>[
      repository,
      ...state.moduleRepositories.where(
        (item) => item.url.toLowerCase() != repository.url.toLowerCase(),
      ),
    ];
    state = state.copyWith(
      moduleRepositories: repositories,
      moduleCatalogLoading: false,
      repositoryUrlDraft: '',
      moduleCatalogError: repository.error,
    );
  }

  Future<void> refreshModuleRepository(String url) async {
    if (!mounted) return;
    state = state.copyWith(
      moduleCatalogLoading: true,
      moduleCatalogError: null,
    );
    final repository = await _catalogClient.fetchRepository(url);
    if (!mounted) return;
    final repositories = state.moduleRepositories
        .map(
          (item) => item.url.toLowerCase() == repository.url.toLowerCase()
              ? repository
              : item,
        )
        .toList(growable: false);
    state = state.copyWith(
      moduleRepositories: repositories,
      moduleCatalogLoading: false,
      moduleCatalogError: repository.error,
    );
  }

  void addCatalogModule(
    BuildModuleCatalogItem module, {
    BuildFormScope scope = BuildFormScope.remote,
  }) {
    final currentModules = _selectedModulesFor(scope);
    final modules = <SelectedBuildModule>[
      ...currentModules,
      ...module.recommendedStages
          .map((stage) => selectedBuildModuleFromCatalog(module, stage: stage))
          .where(
            (selection) => !currentModules.any(
              (existing) =>
                  existing.workflowEntry.toLowerCase() ==
                  selection.workflowEntry.toLowerCase(),
            ),
          ),
    ];
    _replaceSelectedModules(modules, scope: scope);
  }

  Future<BuildExternalModuleMetadata> fetchModuleMetadata(
    String repositoryUrl,
  ) async {
    final cleanUrl = normalizeRepositoryUrl(repositoryUrl);
    if (cleanUrl.isEmpty) {
      throw const FormatException('empty repository url');
    }
    final cacheKey = cleanUrl.toLowerCase();
    final cached = _moduleMetadataCache[cacheKey];
    if (cached != null) {
      return cached;
    }
    final metadata = await _catalogClient.fetchModuleMetadata(cleanUrl);
    _moduleMetadataCache[cacheKey] = metadata;
    return metadata;
  }

  Future<BuildExternalModuleMetadata?> addManualModule({
    BuildFormScope scope = BuildFormScope.remote,
  }) async {
    final url = state.manualModuleUrl.trim();
    if (url.isEmpty) return null;

    BuildExternalModuleMetadata? metadata;
    try {
      final fetchedMetadata = await fetchModuleMetadata(url);
      metadata = fetchedMetadata;
      if (fetchedMetadata.isModuleSet) {
        state = state.copyWith(moduleCatalogError: null);
        return fetchedMetadata;
      }
    } catch (_) {
      metadata = null;
    }

    final effectiveStage =
        metadata != null &&
            !metadata.supportedStages.contains(state.manualModuleStage)
        ? metadata.defaultStage
        : state.manualModuleStage;
    final selection = selectedBuildModuleFromManualUrl(
      url,
      stage: effectiveStage,
    );
    final currentModules = _selectedModulesFor(scope);
    final modules = <SelectedBuildModule>[
      ...currentModules.where(
        (existing) =>
            existing.workflowEntry.toLowerCase() !=
            selection.workflowEntry.toLowerCase(),
      ),
      selection,
    ];
    state = state.copyWith(manualModuleUrl: '', moduleCatalogError: null);
    _replaceSelectedModules(modules, scope: scope);
    return null;
  }

  void removeSelectedModule(
    String workflowEntry, {
    BuildFormScope scope = BuildFormScope.remote,
  }) {
    final modules = _selectedModulesFor(scope)
        .where(
          (module) =>
              module.workflowEntry.toLowerCase() != workflowEntry.toLowerCase(),
        )
        .toList(growable: false);
    _replaceSelectedModules(modules, scope: scope);
  }

  void setRegularModuleStages({
    required SelectedBuildModule seed,
    required List<String> stages,
    BuildFormScope scope = BuildFormScope.remote,
  }) {
    final cleanRepoUrl = normalizeRepositoryUrl(seed.repoUrl);
    final normalizedStages = stages
        .map(normalizeCustomModuleStage)
        .toSet()
        .toList(growable: false);
    final remaining = _selectedModulesFor(scope)
        .where(
          (module) =>
              module.isModuleSetChild ||
              normalizeRepositoryUrl(module.repoUrl).toLowerCase() !=
                  cleanRepoUrl.toLowerCase(),
        )
        .toList(growable: true);
    final additions = normalizedStages
        .map(
          (stage) => SelectedBuildModule(
            label: seed.label,
            repoUrl: cleanRepoUrl,
            stage: stage,
            workflowEntry: 'module:$cleanRepoUrl;$stage',
            fromCatalog: seed.fromCatalog,
          ),
        )
        .toList(growable: false);
    _replaceSelectedModules(<SelectedBuildModule>[
      ...remaining,
      ...additions,
    ], scope: scope);
  }

  void removeModuleSetSelection(
    String groupRepoUrl, {
    BuildFormScope scope = BuildFormScope.remote,
  }) {
    final cleanGroupRepoUrl = normalizeRepositoryUrl(groupRepoUrl);
    final remaining = _selectedModulesFor(scope)
        .where((module) => !module.matchesModuleSetGroup(cleanGroupRepoUrl))
        .toList(growable: false);
    _replaceSelectedModules(remaining, scope: scope);
  }

  Future<void> submitBuild() async {
    if (state.isSubmitting || !state.canBuild) return;
    if (!mounted) return;

    state = state.copyWith(
      isSubmitting: true,
      lastError: null,
      infoMessage: null,
    );
    try {
      final accepted = await api.startGkiBuild(state.form.toRequest());
      if (!mounted) return;
      _upsertTask(accepted);
      state = state.copyWith(
        isSubmitting: false,
        infoMessage: _strings.buildInfoBuildAccepted,
      );
      unawaited(_trackTask(accepted.id));
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(isSubmitting: false, lastError: error.message);
    }
  }

  Future<void> startLocalBuildInit({String? sudoPassword}) async {
    await syncSelectedLocalSourceInstance(sudoPassword: sudoPassword);
  }

  Future<void> startLocalBuildRebuild({String? sudoPassword}) async {
    if (!mounted || state.isSubmitting) return;
    final profile = await saveLocalProfile();
    if (profile == null) {
      return;
    }
    state = state.copyWith(
      isSubmitting: true,
      lastError: null,
      infoMessage: null,
    );
    try {
      final accepted = await api
          .buildLocalBuildProfile(profile.id, <String, dynamic>{
            'cleanOut': state.localBuildCleanOut,
            'reseed': state.localBuildReseed,
            'noPackage': state.localBuildNoPackage,
            'sudoPassword': sudoPassword,
          });
      if (!mounted) return;
      _upsertTask(accepted);
      state = state.copyWith(
        isSubmitting: false,
        infoMessage: _strings.buildLocalRebuildQueued,
      );
      unawaited(_trackTask(accepted.id));
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(isSubmitting: false, lastError: error.message);
    }
  }

  Future<void> startArtifactDownload(
    BuildRunSummary run,
    BuildArtifactSummary artifact,
  ) async {
    if (state.isSubmitting) return;
    if (!mounted) return;
    state = state.copyWith(
      isSubmitting: true,
      lastError: null,
      infoMessage: null,
    );
    try {
      final accepted = await api.downloadBuildArtifact(
        runId: run.id,
        artifactId: artifact.id,
      );
      if (!mounted) return;
      _upsertTask(accepted);
      state = state.copyWith(
        isSubmitting: false,
        infoMessage: _strings.buildArtifactQueuedSingle,
      );
      unawaited(_trackTask(accepted.id));
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(isSubmitting: false, lastError: error.message);
    }
  }

  Future<void> startArtifactDownloads(
    BuildRunSummary run,
    Iterable<BuildArtifactSummary> artifacts, {
    String? completionMessage,
  }) async {
    final list = artifacts.toList(growable: false);
    if (list.isEmpty) {
      if (run.isRunning) {
        await _queuePendingWorkflowDownload(run);
      } else if (mounted) {
        state = state.copyWith(infoMessage: _strings.buildNoArtifacts);
      }
      return;
    }
    if (state.isSubmitting) return;
    if (!mounted) return;
    state = state.copyWith(
      isSubmitting: true,
      lastError: null,
      infoMessage: null,
    );
    try {
      for (final artifact in list) {
        final accepted = await api.downloadBuildArtifact(
          runId: run.id,
          artifactId: artifact.id,
        );
        if (!mounted) return;
        _upsertTask(accepted);
        unawaited(_trackTask(accepted.id));
      }
      if (!mounted) return;
      state = state.copyWith(
        isSubmitting: false,
        infoMessage:
            completionMessage ?? _strings.buildArtifactQueuedMany(list.length),
      );
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(isSubmitting: false, lastError: error.message);
    }
  }

  Future<void> cancelTask(String taskId) async {
    final cleanTaskId = taskId.trim();
    if (cleanTaskId.isEmpty) {
      return;
    }
    try {
      final snapshot = await api.cancelTask(cleanTaskId);
      if (!mounted) return;
      _upsertTask(snapshot);
      state = state.copyWith(
        infoMessage: _strings.buildTaskMessageLabel(
          snapshot.message ?? 'cancellation requested',
        ),
      );
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(lastError: error.message);
    }
  }

  Future<void> _queuePendingWorkflowDownload(BuildRunSummary run) async {
    if (!mounted) return;
    final taskId =
        'workflow-download-${run.id}-${DateTime.now().microsecondsSinceEpoch}';
    _upsertTask(
      DesktopTaskSnapshot(
        id: taskId,
        kind: 'workflow.download',
        state: 'pending',
        message: _strings.buildTaskWorkflowPending,
        output: <String>['## ${_strings.buildTaskWorkflowPending}'],
        result: <String, dynamic>{'runId': run.id, 'bundle': true},
        downloadName: null,
        downloadContentType: null,
      ),
    );
    unawaited(_watchPendingWorkflowDownload(taskId, run));
  }

  Future<void> _watchPendingWorkflowDownload(
    String taskId,
    BuildRunSummary run,
  ) async {
    try {
      final deadline = DateTime.now().add(const Duration(minutes: 15));
      while (DateTime.now().isBefore(deadline)) {
        await Future<void>.delayed(const Duration(seconds: 5));
        if (!mounted) return;
        final artifacts = await ensureArtifactsForRun(
          run.id,
          forceRefresh: true,
        );
        if (!mounted) return;
        if (artifacts.isEmpty) {
          continue;
        }
        _upsertTask(
          DesktopTaskSnapshot(
            id: taskId,
            kind: 'workflow.download',
            state: 'running',
            message: _strings.buildArtifactQueuedMany(artifacts.length),
            output: <String>[
              '## ${_strings.buildArtifactQueuedMany(artifacts.length)}',
            ],
            result: <String, dynamic>{'runId': run.id, 'bundle': true},
            downloadName: null,
            downloadContentType: null,
          ),
        );
        await startArtifactDownloads(
          run,
          artifacts,
          completionMessage: _strings.buildArtifactQueuedMany(artifacts.length),
        );
        if (!mounted) return;
        _upsertTask(
          DesktopTaskSnapshot(
            id: taskId,
            kind: 'workflow.download',
            state: 'succeeded',
            message: _strings.buildArtifactQueuedMany(artifacts.length),
            output: <String>[
              '## ${_strings.buildArtifactQueuedMany(artifacts.length)}',
            ],
            result: <String, dynamic>{'runId': run.id, 'bundle': true},
            downloadName: null,
            downloadContentType: null,
          ),
        );
        return;
      }
      if (!mounted) return;
      _upsertTask(
        DesktopTaskSnapshot(
          id: taskId,
          kind: 'workflow.download',
          state: 'failed',
          message: _strings.buildNoArtifacts,
          output: <String>['## ${_strings.buildNoArtifacts}'],
          result: <String, dynamic>{
            'runId': run.id,
            'bundle': true,
            'error': _strings.buildNoArtifacts,
          },
          downloadName: null,
          downloadContentType: null,
        ),
      );
    } on SidecarException catch (error) {
      if (!mounted) return;
      _upsertTask(
        DesktopTaskSnapshot(
          id: taskId,
          kind: 'workflow.download',
          state: 'failed',
          message: error.message,
          output: error.message.split('\n'),
          result: <String, dynamic>{
            'runId': run.id,
            'bundle': true,
            'error': error.message,
          },
          downloadName: null,
          downloadContentType: null,
        ),
      );
    }
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
          if (task.kind == 'build.gki') {
            await refreshRuns();
          }
          if (task.kind.startsWith('local.build')) {
            await refreshLocalBuildStatus();
          } else if (task.kind == 'local.backend.install') {
            await refreshAll(prefillFromRuntime: false);
          }
          return;
        }
      }
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(lastError: error.message);
    }
  }

  void _upsertTask(DesktopTaskSnapshot task) {
    if (!mounted) return;
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

  void _replaceSelectedModules(
    List<SelectedBuildModule> modules, {
    BuildFormScope scope = BuildFormScope.remote,
  }) {
    final dedupedModules = _dedupeSelectedModules(modules);
    final workflowValue = buildSelectedModulesWorkflowValue(dedupedModules);
    if (scope == BuildFormScope.remote) {
      final updatedForm = state.form.copyWith(customModules: workflowValue);
      state = state.copyWith(
        form: updatedForm.normalized(),
        formDirty: true,
        selectedModules: dedupedModules,
      );
      return;
    }
    final updatedForm = state.localForm.copyWith(customModules: workflowValue);
    state = state.copyWith(
      localForm: updatedForm.normalized(),
      localSelectedModules: dedupedModules,
    );
  }

  void replaceModuleSetSelection({
    required String groupRepoUrl,
    required BuildExternalModuleMetadata metadata,
    required Map<BuildModuleSetChildMetadata, List<String>> selections,
    required bool fromCatalog,
    BuildFormScope scope = BuildFormScope.remote,
  }) {
    final cleanGroupRepoUrl = normalizeRepositoryUrl(groupRepoUrl);
    final remaining = _selectedModulesFor(scope)
        .where((module) => !module.matchesModuleSetGroup(cleanGroupRepoUrl))
        .toList(growable: true);
    final additions = selections.entries
        .expand(
          (entry) => selectedBuildModulesFromModuleSet(
            groupRepoUrl: cleanGroupRepoUrl,
            metadata: metadata,
            child: entry.key,
            stages: entry.value,
            fromCatalog: fromCatalog,
          ),
        )
        .toList(growable: false);
    _replaceSelectedModules(<SelectedBuildModule>[
      ...remaining,
      ...additions,
    ], scope: scope);
  }

  List<SelectedBuildModule> _selectedModulesFor(BuildFormScope scope) {
    return scope == BuildFormScope.remote
        ? state.selectedModules
        : state.localSelectedModules;
  }

  LocalBuildSourceInstance? _findLocalSourceById(String? sourceInstanceId) {
    if (sourceInstanceId == null || sourceInstanceId.isEmpty) {
      return null;
    }
    for (final source in state.localSourceInstances) {
      if (source.id == sourceInstanceId) {
        return source;
      }
    }
    return null;
  }

  LocalBuildProfile? _findLocalProfileById(String? profileId) {
    if (profileId == null || profileId.isEmpty) {
      return null;
    }
    for (final profile in state.localProfiles) {
      if (profile.id == profileId) {
        return profile;
      }
    }
    return null;
  }

  String? _resolveLocalSourceInstanceId({
    required List<LocalBuildSourceInstance> candidates,
    required String? preferredId,
    required String? activeId,
  }) {
    if (preferredId != null &&
        candidates.any((source) => source.id == preferredId)) {
      return preferredId;
    }
    if (activeId != null && candidates.any((source) => source.id == activeId)) {
      return activeId;
    }
    return candidates.isEmpty ? null : candidates.first.id;
  }

  String? _resolveLocalProfileId({
    required List<LocalBuildProfile> candidates,
    required String? preferredId,
    required String? selectedSourceInstanceId,
  }) {
    if (preferredId != null) {
      final preferred = candidates
          .where((profile) => profile.id == preferredId)
          .firstOrNull;
      if (preferred != null &&
          (selectedSourceInstanceId == null ||
              preferred.sourceInstanceId == selectedSourceInstanceId)) {
        return preferredId;
      }
    }
    if (selectedSourceInstanceId != null) {
      final match = candidates
          .where(
            (profile) => profile.sourceInstanceId == selectedSourceInstanceId,
          )
          .firstOrNull;
      if (match != null) {
        return match.id;
      }
    }
    return null;
  }

  BuildFormState _syncFormToSource(
    BuildFormState form,
    LocalBuildSourceInstance? source,
  ) {
    if (source == null) {
      return form.normalized();
    }
    final sourceEntry = source.materialized?.osPatchLevel == null
        ? DesktopKernelSupport.entryForPatchLevel(
            source.androidVersion,
            source.kernelVersion,
            source.branchMonth,
          )
        : null;
    final patchLevel =
        source.materialized?.osPatchLevel ??
        (source.branchMonth.trim().isNotEmpty
            ? source.branchMonth.trim()
            : form.osPatchLevel);
    final subLevel =
        source.materialized?.subLevel ?? sourceEntry?.subLevel ?? form.subLevel;
    return form
        .copyWith(
          androidVersion: source.androidVersion,
          kernelVersion: source.kernelVersion,
          subLevel: subLevel,
          osPatchLevel: patchLevel,
        )
        .normalized();
  }

  BuildFormState _syncFormToSourceDraft(
    BuildFormState form, {
    required String kernelLineId,
    required String branchMonth,
  }) {
    final sourceKey = _sourceKeyFromKernelLineAndBranchMonth(
      kernelLineId,
      branchMonth,
    );
    if (sourceKey == null) {
      return form.normalized();
    }
    final sourceEntry = DesktopKernelSupport.entryForPatchLevel(
      sourceKey.androidVersion,
      sourceKey.kernelVersion,
      sourceKey.branchMonth,
    );
    return form
        .copyWith(
          androidVersion: sourceKey.androidVersion,
          kernelVersion: sourceKey.kernelVersion,
          subLevel: sourceEntry?.subLevel ?? form.subLevel,
          osPatchLevel: sourceKey.branchMonth,
        )
        .normalized();
  }

  void _applyLocalSourceDraft({String? kernelLineId, String? branchMonth}) {
    final nextKernelLineId = kernelLineId ?? state.localSourceKernelLineId;
    final nextBranchMonth = branchMonth ?? state.localBuildBranchMonth;
    final matchingSource = state.localSourceInstances
        .where(
          (source) =>
              source.kernelLineId == nextKernelLineId &&
              source.branchMonth == nextBranchMonth.trim(),
        )
        .firstOrNull;
    final retainedProfile = matchingSource == null
        ? null
        : state.localProfiles
                  .where(
                    (profile) =>
                        profile.sourceInstanceId == matchingSource.id &&
                        profile.id == state.selectedLocalProfileId,
                  )
                  .firstOrNull ??
              state.localProfiles
                  .where(
                    (profile) => profile.sourceInstanceId == matchingSource.id,
                  )
                  .firstOrNull;
    final nextForm = retainedProfile != null
        ? _syncFormToSource(
            BuildFormState.fromRequest(retainedProfile.build),
            matchingSource,
          )
        : _syncFormToSourceDraft(
            state.localForm,
            kernelLineId: nextKernelLineId,
            branchMonth: nextBranchMonth,
          );
    state = state.copyWith(
      localSourceKernelLineId: nextKernelLineId,
      localBuildBranchMonth: nextBranchMonth,
      selectedLocalSourceInstanceId: matchingSource?.id,
      selectedLocalProfileId: retainedProfile?.id,
      localProfileNameDraft:
          retainedProfile?.name ??
          _defaultLocalProfileName(nextKernelLineId, nextBranchMonth),
      localProfileBackendKind: retainedProfile?.backendKind,
      localForm: nextForm,
      localSelectedModules: parseSelectedBuildModules(nextForm.customModules),
    );
  }

  bool _sourceMatchesDraft(LocalBuildSourceInstance source) {
    return source.kernelLineId == state.localSourceKernelLineId &&
        source.branchMonth == state.localBuildBranchMonth.trim();
  }

  String _defaultLocalProfileName(String kernelLineId, String branchMonth) {
    final sourceKey = _sourceKeyFromKernelLineAndBranchMonth(
      kernelLineId,
      branchMonth,
    );
    if (sourceKey == null) {
      return state.localProfileNameDraft;
    }
    return 'Profile ${sourceKey.androidVersion}/${sourceKey.kernelVersion}@${sourceKey.branchMonth}';
  }

  _SourceKey? _sourceKeyFromSourceInstanceId(String sourceInstanceId) {
    final trimmed = sourceInstanceId.trim();
    if (trimmed.isEmpty) {
      return null;
    }
    final atIndex = trimmed.indexOf('@');
    if (atIndex <= 0 || atIndex + 1 >= trimmed.length) {
      return null;
    }
    final kernelLineId = trimmed.substring(0, atIndex).replaceFirst('-', '/');
    final branchMonth = trimmed.substring(atIndex + 1);
    return _sourceKeyFromKernelLineAndBranchMonth(kernelLineId, branchMonth);
  }

  _SourceKey? _sourceKeyFromKernelLineAndBranchMonth(
    String kernelLineId,
    String branchMonth,
  ) {
    final cleanKernelLineId = kernelLineId.trim();
    final cleanBranchMonth = branchMonth.trim();
    final slashIndex = cleanKernelLineId.indexOf('/');
    if (slashIndex <= 0 || slashIndex + 1 >= cleanKernelLineId.length) {
      return null;
    }
    final androidVersion = cleanKernelLineId.substring(0, slashIndex);
    final kernelVersion = cleanKernelLineId.substring(slashIndex + 1);
    if (androidVersion.isEmpty ||
        kernelVersion.isEmpty ||
        cleanBranchMonth.isEmpty) {
      return null;
    }
    return _SourceKey(
      kernelLineId: cleanKernelLineId,
      androidVersion: androidVersion,
      kernelVersion: kernelVersion,
      branchMonth: cleanBranchMonth,
    );
  }

  Future<List<BuildModuleRepository>> _loadModuleRepositories() async {
    if (state.moduleRepositories.isNotEmpty) {
      return state.moduleRepositories;
    }
    final repository = await _catalogClient.fetchRepository(
      officialBuildModuleCatalogUrl,
    );
    return <BuildModuleRepository>[repository];
  }

  int? _resolveSelectedRunId(List<BuildRunSummary> runs) {
    if (runs.isEmpty) {
      return null;
    }
    final current = state.selectedRunId;
    if (current != null && runs.any((run) => run.id == current)) {
      return current;
    }
    return runs.first.id;
  }

  List<SelectedBuildModule> _dedupeSelectedModules(
    List<SelectedBuildModule> modules,
  ) {
    final seen = <String>{};
    final deduped = <SelectedBuildModule>[];
    for (final module in modules) {
      final key = module.workflowEntry.trim().toLowerCase();
      if (key.isEmpty || seen.contains(key)) {
        continue;
      }
      seen.add(key);
      deduped.add(module);
    }
    return deduped;
  }

  @override
  void dispose() {
    _catalogClient.close();
    super.dispose();
  }
}

extension<T> on Iterable<T> {
  T? get firstOrNull => isEmpty ? null : first;
}

class _SourceKey {
  const _SourceKey({
    required this.kernelLineId,
    required this.androidVersion,
    required this.kernelVersion,
    required this.branchMonth,
  });

  final String kernelLineId;
  final String androidVersion;
  final String kernelVersion;
  final String branchMonth;
}
