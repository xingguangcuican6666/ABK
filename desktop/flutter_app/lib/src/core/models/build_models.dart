class GitHubSessionStatus {
  const GitHubSessionStatus({
    required this.ok,
    required this.loggedIn,
    required this.repo,
    required this.needsFork,
    required this.needsSync,
    required this.behindBy,
    required this.aheadBy,
    required this.userLogin,
    required this.forkFullName,
    required this.signingKeyAvailable,
    required this.signingKeySource,
    required this.downloadDir,
  });

  final bool ok;
  final bool loggedIn;
  final String repo;
  final bool needsFork;
  final bool needsSync;
  final int behindBy;
  final int aheadBy;
  final String? userLogin;
  final String? forkFullName;
  final bool signingKeyAvailable;
  final String? signingKeySource;
  final String? downloadDir;

  factory GitHubSessionStatus.fromJson(Map<String, dynamic> json) {
    final fork = _readMap(json['fork']);
    final user = _readMap(json['user']);
    return GitHubSessionStatus(
      ok: json['ok'] == true,
      loggedIn: json['loggedIn'] == true,
      repo: _readString(json['repo']),
      needsFork: json['needsFork'] == true,
      needsSync: json['needsSync'] == true,
      behindBy: _readInt(json['behindBy']),
      aheadBy: _readInt(json['aheadBy']),
      userLogin: _nullableString(user['login']),
      forkFullName: _nullableString(fork['fullName']),
      signingKeyAvailable: json['signingKeyAvailable'] == true,
      signingKeySource: _nullableString(json['signingKeySource']),
      downloadDir: _nullableString(json['downloadDir']),
    );
  }
}

class GitHubLoginChallenge {
  const GitHubLoginChallenge({
    required this.deviceCode,
    required this.userCode,
    required this.verificationUri,
    required this.verificationUriComplete,
    required this.expiresIn,
    required this.interval,
  });

  final String deviceCode;
  final String userCode;
  final String verificationUri;
  final String? verificationUriComplete;
  final int expiresIn;
  final int interval;

  factory GitHubLoginChallenge.fromJson(Map<String, dynamic> json) {
    return GitHubLoginChallenge(
      deviceCode: _readString(json['deviceCode']),
      userCode: _readString(json['userCode']),
      verificationUri: _readString(json['verificationUri']),
      verificationUriComplete: _nullableString(json['verificationUriComplete']),
      expiresIn: _readInt(json['expiresIn'], fallback: 900),
      interval: _readInt(json['interval'], fallback: 5),
    );
  }
}

class GitHubLoginResult {
  const GitHubLoginResult({
    required this.state,
    required this.session,
    required this.error,
  });

  final String state;
  final GitHubSessionStatus? session;
  final String? error;

  factory GitHubLoginResult.fromJson(Map<String, dynamic> json) {
    return GitHubLoginResult(
      state: _readString(json['state'], fallback: 'unknown'),
      session: json['session'] is Map<String, dynamic>
          ? GitHubSessionStatus.fromJson(
              Map<String, dynamic>.from(json['session'] as Map),
            )
          : null,
      error: _nullableString(json['error']),
    );
  }
}

class RuntimeBuildSummary {
  const RuntimeBuildSummary({
    required this.androidVersion,
    required this.kernelVersion,
    required this.subLevel,
    required this.osPatchLevel,
    required this.revision,
  });

  final String androidVersion;
  final String kernelVersion;
  final String subLevel;
  final String osPatchLevel;
  final String revision;

  factory RuntimeBuildSummary.fromJson(Map<String, dynamic> json) {
    return RuntimeBuildSummary(
      androidVersion: _readString(json['androidVersion']),
      kernelVersion: _readString(json['kernelVersion']),
      subLevel: _readString(json['subLevel']),
      osPatchLevel: _readString(json['osPatchLevel']),
      revision: _readString(json['revision']),
    );
  }
}

class LocalBuildTemplate {
  const LocalBuildTemplate({
    required this.name,
    required this.androidVersion,
    required this.kernelVersion,
    required this.templatePath,
  });

  final String name;
  final String androidVersion;
  final String kernelVersion;
  final String templatePath;

  String get displayLabel => '$androidVersion / $kernelVersion';

  factory LocalBuildTemplate.fromJson(Map<String, dynamic> json) {
    return LocalBuildTemplate(
      name: _readString(json['name']),
      androidVersion: _readString(json['androidVersion']),
      kernelVersion: _readString(json['kernelVersion']),
      templatePath: _readString(json['templatePath']),
    );
  }
}

class LocalBuildStatus {
  const LocalBuildStatus({
    required this.available,
    required this.scriptRoot,
    required this.initScriptPath,
    required this.rebuildScriptPath,
    required this.envFilePath,
    required this.stateDir,
    required this.sourcesDir,
    required this.workspaceDir,
    required this.artifactsDir,
    required this.logsDir,
    required this.cacheDir,
    required this.kernelRoot,
    required this.hasEnvFile,
    required this.workspaceReady,
    required this.templateRoot,
    required this.templateName,
    required this.templateAndroidVersion,
    required this.templateKernelVersion,
    required this.subLevel,
    required this.osPatchLevel,
    required this.templateBranch,
    required this.templateCommonBranch,
    required this.branchMonth,
    required this.customExternalModulesRoot,
    required this.customExternalModulesManifest,
    required this.latestLogPath,
    required this.supportedTemplates,
  });

  final bool available;
  final String scriptRoot;
  final String initScriptPath;
  final String rebuildScriptPath;
  final String envFilePath;
  final String stateDir;
  final String sourcesDir;
  final String workspaceDir;
  final String artifactsDir;
  final String logsDir;
  final String cacheDir;
  final String kernelRoot;
  final bool hasEnvFile;
  final bool workspaceReady;
  final String? templateRoot;
  final String? templateName;
  final String? templateAndroidVersion;
  final String? templateKernelVersion;
  final String? subLevel;
  final String? osPatchLevel;
  final String? templateBranch;
  final String? templateCommonBranch;
  final String? branchMonth;
  final String? customExternalModulesRoot;
  final String? customExternalModulesManifest;
  final String? latestLogPath;
  final List<LocalBuildTemplate> supportedTemplates;

  bool get isInitialized => hasEnvFile && workspaceReady;

  bool supportsSelection(String androidVersion, String kernelVersion) {
    return supportedTemplates.any(
      (template) =>
          template.androidVersion == androidVersion &&
          template.kernelVersion == kernelVersion,
    );
  }

  factory LocalBuildStatus.fromJson(Map<String, dynamic> json) {
    return LocalBuildStatus(
      available: json['available'] == true,
      scriptRoot: _readString(json['scriptRoot']),
      initScriptPath: _readString(json['initScriptPath']),
      rebuildScriptPath: _readString(json['rebuildScriptPath']),
      envFilePath: _readString(json['envFilePath']),
      stateDir: _readString(json['stateDir']),
      sourcesDir: _readString(json['sourcesDir']),
      workspaceDir: _readString(json['workspaceDir']),
      artifactsDir: _readString(json['artifactsDir']),
      logsDir: _readString(json['logsDir']),
      cacheDir: _readString(json['cacheDir']),
      kernelRoot: _readString(json['kernelRoot']),
      hasEnvFile: json['hasEnvFile'] == true,
      workspaceReady: json['workspaceReady'] == true,
      templateRoot: _nullableString(json['templateRoot']),
      templateName: _nullableString(json['templateName']),
      templateAndroidVersion: _nullableString(json['templateAndroidVersion']),
      templateKernelVersion: _nullableString(json['templateKernelVersion']),
      subLevel: _nullableString(json['subLevel']),
      osPatchLevel: _nullableString(json['osPatchLevel']),
      templateBranch: _nullableString(json['templateBranch']),
      templateCommonBranch: _nullableString(json['templateCommonBranch']),
      branchMonth: _nullableString(json['branchMonth']),
      customExternalModulesRoot: _nullableString(
        json['customExternalModulesRoot'],
      ),
      customExternalModulesManifest: _nullableString(
        json['customExternalModulesManifest'],
      ),
      latestLogPath: _nullableString(json['latestLogPath']),
      supportedTemplates: _readMapList(
        json['supportedTemplates'],
      ).map(LocalBuildTemplate.fromJson).toList(growable: false),
    );
  }
}

enum LocalBuildBackendKind { docker, podman, wsl, script }

class LocalBuildBackendCapabilities {
  const LocalBuildBackendCapabilities({
    required this.family,
    required this.hostOwnedPaths,
    required this.supportsSourceSync,
    required this.supportsBuildExecution,
    required this.supportsProfileProjection,
    required this.notes,
  });

  final String family;
  final bool hostOwnedPaths;
  final bool supportsSourceSync;
  final bool supportsBuildExecution;
  final bool supportsProfileProjection;
  final List<String> notes;

  factory LocalBuildBackendCapabilities.fromJson(Map<String, dynamic> json) {
    return LocalBuildBackendCapabilities(
      family: _readString(json['family']),
      hostOwnedPaths: json['hostOwnedPaths'] == true,
      supportsSourceSync: json['supportsSourceSync'] == true,
      supportsBuildExecution: json['supportsBuildExecution'] == true,
      supportsProfileProjection: json['supportsProfileProjection'] == true,
      notes: _readStringList(json['notes']),
    );
  }
}

class LocalBuildBackendDescriptor {
  const LocalBuildBackendDescriptor({
    required this.kind,
    required this.label,
    required this.available,
    required this.isGlobalDefault,
    required this.installSupported,
    required this.installLabel,
    required this.installDetail,
    required this.authorizationRequired,
    required this.authorizationKind,
    required this.authorizationMessage,
    required this.capabilities,
    required this.detail,
  });

  final LocalBuildBackendKind kind;
  final String label;
  final bool available;
  final bool isGlobalDefault;
  final bool installSupported;
  final String? installLabel;
  final String? installDetail;
  final bool authorizationRequired;
  final String? authorizationKind;
  final String? authorizationMessage;
  final LocalBuildBackendCapabilities capabilities;
  final String? detail;

  factory LocalBuildBackendDescriptor.fromJson(Map<String, dynamic> json) {
    return LocalBuildBackendDescriptor(
      kind: _readLocalBuildBackendKind(json['kind']),
      label: _readString(json['label']),
      available: json['available'] == true,
      isGlobalDefault: json['isGlobalDefault'] == true,
      installSupported: json['installSupported'] == true,
      installLabel: _nullableString(json['installLabel']),
      installDetail: _nullableString(json['installDetail']),
      authorizationRequired: json['authorizationRequired'] == true,
      authorizationKind: _nullableString(json['authorizationKind']),
      authorizationMessage: _nullableString(json['authorizationMessage']),
      capabilities: LocalBuildBackendCapabilities.fromJson(
        _readMap(json['capabilities']),
      ),
      detail: _nullableString(json['detail']),
    );
  }
}

class SupportedKernelLine {
  const SupportedKernelLine({
    required this.id,
    required this.androidVersion,
    required this.kernelVersion,
    required this.displayName,
    required this.branchMonthFormat,
    required this.scriptTemplatePath,
    required this.scriptTemplateAvailable,
  });

  final String id;
  final String androidVersion;
  final String kernelVersion;
  final String displayName;
  final String branchMonthFormat;
  final String scriptTemplatePath;
  final bool scriptTemplateAvailable;

  factory SupportedKernelLine.fromJson(Map<String, dynamic> json) {
    return SupportedKernelLine(
      id: _readString(json['id']),
      androidVersion: _readString(json['androidVersion']),
      kernelVersion: _readString(json['kernelVersion']),
      displayName: _readString(json['displayName']),
      branchMonthFormat: _readString(
        json['branchMonthFormat'],
        fallback: 'YYYY-MM',
      ),
      scriptTemplatePath: _readString(json['scriptTemplatePath']),
      scriptTemplateAvailable: json['scriptTemplateAvailable'] == true,
    );
  }
}

class LocalBuildSettings {
  const LocalBuildSettings({
    required this.globalDefaultBackendKind,
    required this.activeSourceInstanceId,
    required this.scriptRootDir,
    required this.workspaceDir,
    required this.profileStoreDir,
  });

  final LocalBuildBackendKind globalDefaultBackendKind;
  final String? activeSourceInstanceId;
  final String? scriptRootDir;
  final String? workspaceDir;
  final String? profileStoreDir;

  factory LocalBuildSettings.fromJson(Map<String, dynamic> json) {
    return LocalBuildSettings(
      globalDefaultBackendKind: _readLocalBuildBackendKind(
        json['globalDefaultBackendKind'],
      ),
      activeSourceInstanceId: _nullableString(json['activeSourceInstanceId']),
      scriptRootDir: _nullableString(json['scriptRootDir']),
      workspaceDir: _nullableString(json['workspaceDir']),
      profileStoreDir: _nullableString(json['profileStoreDir']),
    );
  }
}

class LocalBuildMaterializedState {
  const LocalBuildMaterializedState({
    required this.scriptRoot,
    required this.envFilePath,
    required this.stateDir,
    required this.sourcesDir,
    required this.workspaceDir,
    required this.artifactsDir,
    required this.logsDir,
    required this.cacheDir,
    required this.kernelRoot,
    required this.templateName,
    required this.templateRoot,
    required this.templateBranch,
    required this.templateCommonBranch,
    required this.subLevel,
    required this.osPatchLevel,
    required this.latestLogPath,
  });

  final String? scriptRoot;
  final String? envFilePath;
  final String? stateDir;
  final String? sourcesDir;
  final String? workspaceDir;
  final String? artifactsDir;
  final String? logsDir;
  final String? cacheDir;
  final String? kernelRoot;
  final String? templateName;
  final String? templateRoot;
  final String? templateBranch;
  final String? templateCommonBranch;
  final String? subLevel;
  final String? osPatchLevel;
  final String? latestLogPath;

  factory LocalBuildMaterializedState.fromJson(Map<String, dynamic> json) {
    return LocalBuildMaterializedState(
      scriptRoot: _nullableString(json['scriptRoot']),
      envFilePath: _nullableString(json['envFilePath']),
      stateDir: _nullableString(json['stateDir']),
      sourcesDir: _nullableString(json['sourcesDir']),
      workspaceDir: _nullableString(json['workspaceDir']),
      artifactsDir: _nullableString(json['artifactsDir']),
      logsDir: _nullableString(json['logsDir']),
      cacheDir: _nullableString(json['cacheDir']),
      kernelRoot: _nullableString(json['kernelRoot']),
      templateName: _nullableString(json['templateName']),
      templateRoot: _nullableString(json['templateRoot']),
      templateBranch: _nullableString(json['templateBranch']),
      templateCommonBranch: _nullableString(json['templateCommonBranch']),
      subLevel: _nullableString(json['subLevel']),
      osPatchLevel: _nullableString(json['osPatchLevel']),
      latestLogPath: _nullableString(json['latestLogPath']),
    );
  }
}

class LocalBuildSourceInstance {
  const LocalBuildSourceInstance({
    required this.id,
    required this.displayName,
    required this.kernelLineId,
    required this.androidVersion,
    required this.kernelVersion,
    required this.branchMonth,
    required this.cacheRoot,
    required this.workingTreeRoot,
    required this.state,
    required this.createdAtMs,
    required this.updatedAtMs,
    required this.lastSyncedAtMs,
    required this.activeBackendKind,
    required this.lastTaskId,
    required this.lastError,
    required this.materialized,
  });

  final String id;
  final String displayName;
  final String kernelLineId;
  final String androidVersion;
  final String kernelVersion;
  final String branchMonth;
  final String cacheRoot;
  final String workingTreeRoot;
  final String state;
  final int createdAtMs;
  final int updatedAtMs;
  final int? lastSyncedAtMs;
  final LocalBuildBackendKind? activeBackendKind;
  final String? lastTaskId;
  final String? lastError;
  final LocalBuildMaterializedState? materialized;

  bool get isReady => state == 'ready';

  factory LocalBuildSourceInstance.fromJson(Map<String, dynamic> json) {
    return LocalBuildSourceInstance(
      id: _readString(json['id']),
      displayName: _readString(json['displayName']),
      kernelLineId: _readString(json['kernelLineId']),
      androidVersion: _readString(json['androidVersion']),
      kernelVersion: _readString(json['kernelVersion']),
      branchMonth: _readString(json['branchMonth']),
      cacheRoot: _readString(json['cacheRoot']),
      workingTreeRoot: _readString(json['workingTreeRoot']),
      state: _readString(json['state']),
      createdAtMs: _readInt(json['createdAtMs']),
      updatedAtMs: _readInt(json['updatedAtMs']),
      lastSyncedAtMs: _nullableInt(json['lastSyncedAtMs']),
      activeBackendKind: _nullableLocalBuildBackendKind(
        json['activeBackendKind'],
      ),
      lastTaskId: _nullableString(json['lastTaskId']),
      lastError: _nullableString(json['lastError']),
      materialized: json['materialized'] is Map<String, dynamic>
          ? LocalBuildMaterializedState.fromJson(
              Map<String, dynamic>.from(json['materialized'] as Map),
            )
          : json['materialized'] is Map
          ? LocalBuildMaterializedState.fromJson(
              Map<String, dynamic>.from(json['materialized'] as Map),
            )
          : null,
    );
  }
}

class LocalBuildSourceInstancesResponse {
  const LocalBuildSourceInstancesResponse({
    required this.settings,
    required this.sourceInstances,
  });

  final LocalBuildSettings settings;
  final List<LocalBuildSourceInstance> sourceInstances;

  factory LocalBuildSourceInstancesResponse.fromJson(
    Map<String, dynamic> json,
  ) {
    return LocalBuildSourceInstancesResponse(
      settings: LocalBuildSettings.fromJson(_readMap(json['settings'])),
      sourceInstances: _readMapList(
        json['sourceInstances'],
      ).map(LocalBuildSourceInstance.fromJson).toList(growable: false),
    );
  }
}

class LocalBuildProfile {
  const LocalBuildProfile({
    required this.id,
    required this.name,
    required this.sourceInstanceId,
    required this.backendKind,
    required this.build,
    required this.createdAtMs,
    required this.updatedAtMs,
    required this.lastBuiltAtMs,
    required this.lastTaskId,
    required this.lastError,
  });

  final String id;
  final String name;
  final String sourceInstanceId;
  final LocalBuildBackendKind? backendKind;
  final Map<String, dynamic> build;
  final int createdAtMs;
  final int updatedAtMs;
  final int? lastBuiltAtMs;
  final String? lastTaskId;
  final String? lastError;

  factory LocalBuildProfile.fromJson(Map<String, dynamic> json) {
    return LocalBuildProfile(
      id: _readString(json['id']),
      name: _readString(json['name']),
      sourceInstanceId: _readString(json['sourceInstanceId']),
      backendKind: _nullableLocalBuildBackendKind(json['backendKind']),
      build: _readMap(json['build']),
      createdAtMs: _readInt(json['createdAtMs']),
      updatedAtMs: _readInt(json['updatedAtMs']),
      lastBuiltAtMs: _nullableInt(json['lastBuiltAtMs']),
      lastTaskId: _nullableString(json['lastTaskId']),
      lastError: _nullableString(json['lastError']),
    );
  }
}

class LocalBuildProfilesResponse {
  const LocalBuildProfilesResponse({
    required this.settings,
    required this.profiles,
  });

  final LocalBuildSettings settings;
  final List<LocalBuildProfile> profiles;

  factory LocalBuildProfilesResponse.fromJson(Map<String, dynamic> json) {
    return LocalBuildProfilesResponse(
      settings: LocalBuildSettings.fromJson(_readMap(json['settings'])),
      profiles: _readMapList(
        json['profiles'],
      ).map(LocalBuildProfile.fromJson).toList(growable: false),
    );
  }
}

class LocalBuildArtifactEntry {
  const LocalBuildArtifactEntry({
    required this.id,
    required this.taskId,
    required this.profileId,
    required this.sourceInstanceId,
    required this.backendKind,
    required this.path,
    required this.fileName,
    required this.exists,
    required this.createdAtMs,
  });

  final String id;
  final String taskId;
  final String? profileId;
  final String sourceInstanceId;
  final LocalBuildBackendKind backendKind;
  final String path;
  final String fileName;
  final bool exists;
  final int createdAtMs;

  factory LocalBuildArtifactEntry.fromJson(Map<String, dynamic> json) {
    return LocalBuildArtifactEntry(
      id: _readString(json['id']),
      taskId: _readString(json['taskId']),
      profileId: _nullableString(json['profileId']),
      sourceInstanceId: _readString(json['sourceInstanceId']),
      backendKind: _readLocalBuildBackendKind(json['backendKind']),
      path: _readString(json['path']),
      fileName: _readString(json['fileName']),
      exists: json['exists'] == true,
      createdAtMs: _readInt(json['createdAtMs']),
    );
  }
}

class LocalBuildLogEntry {
  const LocalBuildLogEntry({
    required this.id,
    required this.taskId,
    required this.profileId,
    required this.sourceInstanceId,
    required this.backendKind,
    required this.path,
    required this.fileName,
    required this.exists,
    required this.createdAtMs,
  });

  final String id;
  final String taskId;
  final String? profileId;
  final String sourceInstanceId;
  final LocalBuildBackendKind backendKind;
  final String path;
  final String fileName;
  final bool exists;
  final int createdAtMs;

  factory LocalBuildLogEntry.fromJson(Map<String, dynamic> json) {
    return LocalBuildLogEntry(
      id: _readString(json['id']),
      taskId: _readString(json['taskId']),
      profileId: _nullableString(json['profileId']),
      sourceInstanceId: _readString(json['sourceInstanceId']),
      backendKind: _readLocalBuildBackendKind(json['backendKind']),
      path: _readString(json['path']),
      fileName: _readString(json['fileName']),
      exists: json['exists'] == true,
      createdAtMs: _readInt(json['createdAtMs']),
    );
  }
}

class BuildRunSummary {
  const BuildRunSummary({
    required this.id,
    required this.name,
    required this.displayTitle,
    required this.status,
    required this.conclusion,
    required this.event,
    required this.headBranch,
    required this.htmlUrl,
    required this.createdAt,
    required this.updatedAt,
    required this.runNumber,
  });

  final int id;
  final String name;
  final String displayTitle;
  final String status;
  final String? conclusion;
  final String? event;
  final String? headBranch;
  final String? htmlUrl;
  final String? createdAt;
  final String? updatedAt;
  final int runNumber;

  bool get isRunning => status == 'queued' || status == 'in_progress';
  bool get isSuccess => status == 'completed' && conclusion == 'success';
  bool get isFailure => status == 'completed' && conclusion == 'failure';
  bool get looksLikeKernelBuild {
    final haystack = '${name.toLowerCase()} ${displayTitle.toLowerCase()}';
    return (haystack.contains('kernel') || haystack.contains('内核')) &&
        !haystack.contains('build abk app');
  }

  factory BuildRunSummary.fromJson(Map<String, dynamic> json) {
    return BuildRunSummary(
      id: _readInt(json['id']),
      name: _readString(json['name']),
      displayTitle: _readString(
        json['displayTitle'],
        fallback: _readString(json['name']),
      ),
      status: _readString(json['status']),
      conclusion: _nullableString(json['conclusion']),
      event: _nullableString(json['event']),
      headBranch: _nullableString(json['headBranch']),
      htmlUrl: _nullableString(json['htmlUrl']),
      createdAt: _nullableString(json['createdAt']),
      updatedAt: _nullableString(json['updatedAt']),
      runNumber: _readInt(json['runNumber']),
    );
  }
}

class BuildArtifactSummary {
  const BuildArtifactSummary({
    required this.id,
    required this.name,
    required this.sizeBytes,
    required this.expired,
    required this.archiveDownloadUrl,
  });

  final int id;
  final String name;
  final int sizeBytes;
  final bool expired;
  final String? archiveDownloadUrl;

  factory BuildArtifactSummary.fromJson(Map<String, dynamic> json) {
    return BuildArtifactSummary(
      id: _readInt(json['id']),
      name: _readString(json['name']),
      sizeBytes: _readInt(json['sizeBytes']),
      expired: json['expired'] == true,
      archiveDownloadUrl: _nullableString(json['archiveDownloadUrl']),
    );
  }
}

enum BuildArtifactType {
  kernelPackage,
  kernelImage,
  anyKernel3,
  abkManager,
  ksuManager,
  susfsModule,
  other,
}

enum BuildArtifactCategory { kernel, manager, module }

extension BuildArtifactSummaryClassify on BuildArtifactSummary {
  BuildArtifactType get artifactType {
    final lower = name.trim().toLowerCase();
    if (lower.contains('reject') || lower.contains('-rej')) {
      return BuildArtifactType.other;
    }
    if (lower.contains('_kernel-android') || lower.contains('kernel-android')) {
      return BuildArtifactType.kernelPackage;
    }
    if (lower.endsWith('.img') &&
        (lower.contains('boot') ||
            lower.contains('kernel') ||
            lower.contains('gki'))) {
      return BuildArtifactType.kernelImage;
    }
    if (lower.contains('boot-img') ||
        lower.contains('boot_img') ||
        lower.contains('kernel-img')) {
      return BuildArtifactType.kernelImage;
    }
    if (lower.contains('raw-image') || lower.contains('raw_image')) {
      return BuildArtifactType.kernelImage;
    }
    if (lower.contains('anykernel') || lower.contains('ak3')) {
      return BuildArtifactType.anyKernel3;
    }
    if (lower.endsWith('.zip') && _isLikelyModuleZipName(lower)) {
      return BuildArtifactType.susfsModule;
    }
    if (_isLikelyModuleZipName(lower) && !lower.contains('anykernel')) {
      return BuildArtifactType.susfsModule;
    }
    if (lower == 'abk-apks' || lower.contains('abk-apks')) {
      return BuildArtifactType.abkManager;
    }
    if (lower.contains('abk') && lower.endsWith('.apk')) {
      return BuildArtifactType.abkManager;
    }
    if (lower.endsWith('.apk') &&
        (lower.contains('manager') ||
            lower.contains('kernelsu') ||
            lower.contains('ksu') ||
            lower.contains('suki'))) {
      return BuildArtifactType.ksuManager;
    }
    if (lower.contains('manager') &&
        (lower.contains('kernelsu') ||
            lower.contains('ksu') ||
            lower.contains('suki'))) {
      return BuildArtifactType.ksuManager;
    }
    if (lower.contains('sukisu-ultra') || lower.contains('sukisu_ultra')) {
      return BuildArtifactType.ksuManager;
    }
    return BuildArtifactType.other;
  }

  BuildArtifactCategory? get artifactCategory {
    return switch (artifactType) {
      BuildArtifactType.kernelPackage ||
      BuildArtifactType.kernelImage ||
      BuildArtifactType.anyKernel3 => BuildArtifactCategory.kernel,
      BuildArtifactType.abkManager ||
      BuildArtifactType.ksuManager => BuildArtifactCategory.manager,
      BuildArtifactType.susfsModule => BuildArtifactCategory.module,
      BuildArtifactType.other => null,
    };
  }
}

bool _isLikelyModuleZipName(String lower) =>
    lower.contains('susfs') ||
    lower.contains('module') ||
    lower.contains('magisk') ||
    lower.contains('zygisk') ||
    lower.contains('kpm');

class DesktopTaskSnapshot {
  const DesktopTaskSnapshot({
    required this.id,
    required this.kind,
    required this.state,
    required this.message,
    required this.output,
    required this.result,
    required this.downloadName,
    required this.downloadContentType,
  });

  final String id;
  final String kind;
  final String state;
  final String? message;
  final List<String> output;
  final Map<String, dynamic> result;
  final String? downloadName;
  final String? downloadContentType;

  bool get isTerminal =>
      state == 'succeeded' || state == 'failed' || state == 'cancelled';
  bool get isRunning => state == 'pending' || state == 'running';
  bool get isCancelable =>
      kind.startsWith('local.build') && isRunning;
  String? get primaryDownloadPath {
    final downloads = result['downloads'];
    if (downloads is List && downloads.isNotEmpty) {
      final first = downloads.first;
      if (first is Map) {
        final path = first['path'];
        if (path is String && path.trim().isNotEmpty) {
          return path;
        }
      }
    }
    final outputDir = result['outputDir'];
    if (outputDir is String && outputDir.trim().isNotEmpty) {
      return outputDir;
    }
    return null;
  }

  factory DesktopTaskSnapshot.fromJson(Map<String, dynamic> json) {
    return DesktopTaskSnapshot(
      id: _readString(json['id']),
      kind: _readString(json['kind']),
      state: _readString(json['state']),
      message: _nullableString(json['message']),
      output: _readStringList(json['output']),
      result: _readMap(json['result']),
      downloadName: _nullableString(json['downloadName']),
      downloadContentType: _nullableString(json['downloadContentType']),
    );
  }
}

class BuildDispatchItem {
  const BuildDispatchItem({
    required this.workflowFile,
    required this.workflowName,
    required this.target,
    required this.ksuVariant,
    required this.ref,
    required this.inputs,
  });

  final String workflowFile;
  final String workflowName;
  final String target;
  final String? ksuVariant;
  final String ref;
  final Map<String, dynamic> inputs;

  factory BuildDispatchItem.fromJson(Map<String, dynamic> json) {
    return BuildDispatchItem(
      workflowFile: _readString(json['workflowFile']),
      workflowName: _readString(json['workflowName']),
      target: _readString(json['target']),
      ksuVariant: _nullableString(json['ksuVariant']),
      ref: _readString(json['ref']),
      inputs: _readMap(json['inputs']),
    );
  }
}

class BuildDispatchResult {
  const BuildDispatchResult({
    required this.ok,
    required this.repo,
    required this.dryRun,
    required this.total,
    required this.run,
    required this.runs,
    required this.dispatches,
    required this.warnings,
    required this.error,
  });

  final bool ok;
  final String? repo;
  final bool dryRun;
  final int total;
  final BuildRunSummary? run;
  final List<BuildRunSummary> runs;
  final List<BuildDispatchItem> dispatches;
  final List<String> warnings;
  final String? error;

  factory BuildDispatchResult.fromJson(Map<String, dynamic> json) {
    return BuildDispatchResult(
      ok: json['ok'] == true,
      repo: _nullableString(json['repo']),
      dryRun: json['dryRun'] == true,
      total: _readInt(json['total']),
      run: json['run'] is Map<String, dynamic>
          ? BuildRunSummary.fromJson(
              Map<String, dynamic>.from(json['run'] as Map),
            )
          : null,
      runs: _readMapList(
        json['runs'],
      ).map(BuildRunSummary.fromJson).toList(growable: false),
      dispatches: _readMapList(
        json['dispatches'],
      ).map(BuildDispatchItem.fromJson).toList(growable: false),
      warnings: _readStringList(json['warnings']),
      error: _nullableString(json['error']),
    );
  }
}

String _readString(dynamic value, {String fallback = ''}) {
  if (value is String) {
    return value;
  }
  return fallback;
}

String? _nullableString(dynamic value) {
  if (value is String && value.isNotEmpty) {
    return value;
  }
  return null;
}

int _readInt(dynamic value, {int fallback = 0}) {
  if (value is int) {
    return value;
  }
  if (value is num) {
    return value.toInt();
  }
  if (value is String) {
    return int.tryParse(value) ?? fallback;
  }
  return fallback;
}

int? _nullableInt(dynamic value) {
  if (value == null) {
    return null;
  }
  final parsed = _readInt(value, fallback: -1);
  return parsed >= 0 ? parsed : null;
}

Map<String, dynamic> _readMap(dynamic value) {
  if (value is Map<String, dynamic>) {
    return value;
  }
  if (value is Map) {
    return Map<String, dynamic>.from(value);
  }
  return const <String, dynamic>{};
}

List<Map<String, dynamic>> _readMapList(dynamic value) {
  if (value is! List) {
    return const <Map<String, dynamic>>[];
  }
  return value
      .whereType<Map>()
      .map((item) => Map<String, dynamic>.from(item))
      .toList(growable: false);
}

List<String> _readStringList(dynamic value) {
  if (value is! List) {
    return const <String>[];
  }
  return value
      .whereType<String>()
      .map((item) => item.trim())
      .where((item) => item.isNotEmpty)
      .toList(growable: false);
}

LocalBuildBackendKind _readLocalBuildBackendKind(dynamic value) {
  final raw = value is String ? value.trim().toLowerCase() : '';
  return switch (raw) {
    'docker' => LocalBuildBackendKind.docker,
    'podman' => LocalBuildBackendKind.podman,
    'wsl' => LocalBuildBackendKind.wsl,
    'script' => LocalBuildBackendKind.script,
    _ => LocalBuildBackendKind.script,
  };
}

LocalBuildBackendKind? _nullableLocalBuildBackendKind(dynamic value) {
  if (value == null) {
    return null;
  }
  return _readLocalBuildBackendKind(value);
}
