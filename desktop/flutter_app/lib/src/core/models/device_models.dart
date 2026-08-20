import 'dart:convert';

class AbkRuntimeEnvelope {
  const AbkRuntimeEnvelope({
    required this.rootGranted,
    required this.managerAccessKind,
    required this.managerDiagnostic,
    required this.runtimeStatus,
  });

  final bool rootGranted;
  final String managerAccessKind;
  final String? managerDiagnostic;
  final AbkRuntimeStatus? runtimeStatus;

  factory AbkRuntimeEnvelope.fromJson(Map<String, dynamic> json) {
    final runtimeRaw = _readMap(json['runtimeStatus']);
    return AbkRuntimeEnvelope(
      rootGranted: json['rootGranted'] == true,
      managerAccessKind: _readString(json['managerAccessKind']),
      managerDiagnostic: _nullableString(json['managerDiagnostic']),
      runtimeStatus: runtimeRaw.isEmpty
          ? null
          : AbkRuntimeStatus.fromJson(runtimeRaw),
    );
  }
}

class AbkRuntimeStatus {
  const AbkRuntimeStatus({
    required this.schema,
    required this.abkVersion,
    required this.abkCommit,
    required this.workMode,
    required this.manager,
    required this.runtimeBackend,
    required this.build,
    required this.modules,
    required this.extensionModules,
  });

  final int schema;
  final String abkVersion;
  final String abkCommit;
  final String workMode;
  final AbkRuntimeManagerInfo? manager;
  final AbkRuntimeManagerInfo? runtimeBackend;
  final AbkRuntimeBuildInfo? build;
  final List<AbkRuntimeModule> modules;
  final List<AbkRuntimeModule> extensionModules;

  factory AbkRuntimeStatus.fromJson(Map<String, dynamic> json) {
    final managerRaw = _readMap(json['manager']);
    final backendRaw = _readMapAlias(json, <String>[
      'runtimeBackend',
      'runtime_backend',
    ]);
    final buildRaw = _readMap(json['build']);
    return AbkRuntimeStatus(
      schema: _readInt(json['schema'], fallback: 1),
      abkVersion: _readStringAlias(json, <String>['abkVersion', 'abk_version']),
      abkCommit: _readStringAlias(json, <String>['abkCommit', 'abk_commit']),
      workMode: _readStringAlias(json, <String>['workMode', 'work_mode']),
      manager: managerRaw.isEmpty
          ? null
          : AbkRuntimeManagerInfo.fromJson(managerRaw),
      runtimeBackend: backendRaw.isEmpty
          ? null
          : AbkRuntimeManagerInfo.fromJson(backendRaw),
      build: buildRaw.isEmpty ? null : AbkRuntimeBuildInfo.fromJson(buildRaw),
      modules: _readMapList(
        json['modules'],
      ).map(AbkRuntimeModule.fromJson).toList(growable: false),
      extensionModules: _readMapListAlias(json, <String>[
        'extensionModules',
        'extension_modules',
      ]).map(AbkRuntimeModule.fromJson).toList(growable: false),
    );
  }
}

class AbkRuntimeManagerInfo {
  const AbkRuntimeManagerInfo({
    required this.displayName,
    required this.variant,
    required this.backend,
    required this.version,
    required this.active,
    required this.capabilities,
    required this.diagnostics,
  });

  final String displayName;
  final String variant;
  final String backend;
  final String version;
  final bool active;
  final List<String> capabilities;
  final List<String> diagnostics;

  factory AbkRuntimeManagerInfo.fromJson(Map<String, dynamic> json) {
    return AbkRuntimeManagerInfo(
      displayName: _readStringAlias(json, <String>[
        'displayName',
        'display_name',
      ]),
      variant: _readString(json['variant']),
      backend: _readString(json['backend']),
      version: _readString(json['version']),
      active: json['active'] == true,
      capabilities: _readStringList(json['capabilities']),
      diagnostics: _readStringList(json['diagnostics']),
    );
  }
}

class AbkRuntimeBuildInfo {
  const AbkRuntimeBuildInfo({
    required this.androidVersion,
    required this.kernelVersion,
    required this.subLevel,
    required this.osPatchLevel,
    required this.revision,
    required this.kernelsuVariant,
    required this.kernelsuBranch,
    required this.version,
    required this.buildTime,
    required this.virtualizationSupport,
    required this.zramExtraAlgos,
    required this.features,
  });

  final String androidVersion;
  final String kernelVersion;
  final String subLevel;
  final String osPatchLevel;
  final String revision;
  final String kernelsuVariant;
  final String kernelsuBranch;
  final String version;
  final String buildTime;
  final String virtualizationSupport;
  final String zramExtraAlgos;
  final Map<String, bool> features;

  factory AbkRuntimeBuildInfo.fromJson(Map<String, dynamic> json) {
    return AbkRuntimeBuildInfo(
      androidVersion: _readStringAlias(json, <String>[
        'androidVersion',
        'android_version',
      ]),
      kernelVersion: _readStringAlias(json, <String>[
        'kernelVersion',
        'kernel_version',
      ]),
      subLevel: _readStringAlias(json, <String>['subLevel', 'sub_level']),
      osPatchLevel: _readStringAlias(json, <String>[
        'osPatchLevel',
        'os_patch_level',
      ]),
      revision: _readString(json['revision']),
      kernelsuVariant: _readStringAlias(json, <String>[
        'kernelsuVariant',
        'kernelsu_variant',
      ]),
      kernelsuBranch: _readStringAlias(json, <String>[
        'kernelsuBranch',
        'kernelsu_branch',
      ]),
      version: _readString(json['version']),
      buildTime: _readStringAlias(json, <String>['buildTime', 'build_time']),
      virtualizationSupport: _readStringAlias(json, <String>[
        'virtualizationSupport',
        'virtualization_support',
      ]),
      zramExtraAlgos: _readStringAlias(json, <String>[
        'zramExtraAlgos',
        'zram_extra_algos',
      ]),
      features: _readBoolMap(json['features']),
    );
  }
}

class AbkRuntimeModule {
  const AbkRuntimeModule({
    required this.id,
    required this.name,
    required this.author,
    required this.type,
    required this.version,
    required this.versionCode,
    required this.description,
    required this.repoUrl,
    required this.stage,
    required this.entryKind,
    required this.source,
    required this.extensionId,
    required this.companionPackage,
    required this.companionDisplayName,
    required this.companionAssetName,
    required this.companionDownloadUrl,
    required this.serviceActivity,
    required this.moduleDir,
    required this.webRoot,
    required this.readonly,
    required this.controllable,
    required this.enabled,
    required this.update,
    required this.remove,
    required this.hasWebUi,
    required this.hasActionScript,
    required this.actionSupported,
    required this.requiresCompanionApp,
    required this.settingsSupported,
    required this.perAppSupported,
    required this.oobePriority,
    required this.kpmArgs,
    required this.groupId,
    required this.groupName,
    required this.groupRole,
    required this.groupDescription,
    required this.groupRepoUrl,
  });

  final String id;
  final String name;
  final String author;
  final String type;
  final String version;
  final int versionCode;
  final String description;
  final String repoUrl;
  final String stage;
  final String entryKind;
  final String source;
  final String extensionId;
  final String companionPackage;
  final String companionDisplayName;
  final String companionAssetName;
  final String companionDownloadUrl;
  final String serviceActivity;
  final String moduleDir;
  final String webRoot;
  final bool readonly;
  final bool controllable;
  final bool enabled;
  final bool update;
  final bool remove;
  final bool hasWebUi;
  final bool hasActionScript;
  final bool actionSupported;
  final bool requiresCompanionApp;
  final bool settingsSupported;
  final bool perAppSupported;
  final int oobePriority;
  final String kpmArgs;
  final String groupId;
  final String groupName;
  final String groupRole;
  final String groupDescription;
  final String groupRepoUrl;

  String get displayName => name.trim().isNotEmpty ? name.trim() : id;
  String get normalizedType {
    final explicit = type.trim().toLowerCase();
    if (explicit.isNotEmpty) return explicit;
    final sources = source
        .split(',')
        .map((value) => value.trim().toLowerCase())
        .where((value) => value.isNotEmpty);
    if (sources.contains('kpm')) return 'kpm';
    if (sources.contains('ksud')) return 'standard';
    return 'builtin';
  }

  String get normalizedEntryKind {
    final clean = entryKind.trim().toLowerCase();
    return switch (clean) {
      'set' ||
      'module_set' ||
      'module-set' ||
      'moduleset' ||
      'group_child' => 'module_set_child',
      'module' || 'single' || '' => clean,
      _ => clean,
    };
  }

  Set<String> get normalizedSources => source
      .split(',')
      .map((value) => value.trim().toLowerCase())
      .where((value) => value.isNotEmpty)
      .toSet();

  bool get hasModuleSetPresentation =>
      normalizedSources.contains('abk') &&
      (normalizedEntryKind == 'module_set_child' ||
          groupRepoUrl.trim().isNotEmpty ||
          groupId.trim().isNotEmpty ||
          groupName.trim().isNotEmpty);
  bool get isCustomModuleSetChild => hasModuleSetPresentation;

  bool get isStandardRuntimeModule {
    if (hasModuleSetPresentation) return false;
    return normalizedType == 'standard' ||
        normalizedType == 'kpm' ||
        normalizedSources.contains('ksud') ||
        normalizedSources.contains('kpm');
  }

  bool get isCustomModule =>
      !hasModuleSetPresentation && !isStandardRuntimeModule;

  String get repoName {
    final cleanRepo = repoUrl.trim();
    if (cleanRepo.isEmpty) return '';
    return cleanRepo
        .trimRight()
        .replaceAll(RegExp(r'/+$'), '')
        .replaceAll(RegExp(r'\.git$'), '')
        .split('/')
        .last;
  }

  String get moduleSetDisplayName {
    final cleanGroupName = groupName.trim();
    if (cleanGroupName.isNotEmpty) return cleanGroupName;
    final cleanRepoName = repoName.trim();
    if (cleanRepoName.isNotEmpty) return cleanRepoName;
    return displayName;
  }

  String get moduleGroupKey {
    final cleanRepo = groupRepoUrl.trim();
    if (cleanRepo.isNotEmpty) return 'repo:${cleanRepo.toLowerCase()}';
    final cleanGroupId = groupId.trim();
    if (cleanGroupId.isNotEmpty) return 'group:${cleanGroupId.toLowerCase()}';
    final cleanGroupName = groupName.trim();
    if (cleanGroupName.isNotEmpty) {
      return 'group-name:${cleanGroupName.toLowerCase()}';
    }
    final cleanModuleRepo = repoUrl.trim();
    if (hasModuleSetPresentation && cleanModuleRepo.isNotEmpty) {
      return 'repo:${cleanModuleRepo.toLowerCase()}';
    }
    return 'single:${id.toLowerCase()}';
  }

  factory AbkRuntimeModule.fromJson(Map<String, dynamic> json) {
    return AbkRuntimeModule(
      id: _readString(json['id']),
      name: _readString(json['name']),
      author: _readString(json['author']),
      type: _readString(json['type']),
      version: _readString(json['version']),
      versionCode: _readIntAlias(json, <String>['versionCode', 'version_code']),
      description: _readString(json['description']),
      repoUrl: _readStringAlias(json, <String>['repoUrl', 'repo_url']),
      stage: _readString(json['stage']),
      entryKind: _readStringAlias(json, <String>['entryKind', 'entry_kind']),
      source: _readString(json['source']),
      extensionId: _readStringAlias(json, <String>[
        'extensionId',
        'extension_id',
      ]),
      companionPackage: _readStringAlias(json, <String>[
        'companionPackage',
        'companion_package',
      ]),
      companionDisplayName: _readStringAlias(json, <String>[
        'companionDisplayName',
        'companion_display_name',
      ]),
      companionAssetName: _readStringAlias(json, <String>[
        'companionAssetName',
        'companion_asset_name',
      ]),
      companionDownloadUrl: _readStringAlias(json, <String>[
        'companionDownloadUrl',
        'companion_download_url',
      ]),
      serviceActivity: _readStringAlias(json, <String>[
        'serviceActivity',
        'service_activity',
      ]),
      moduleDir: _readStringAlias(json, <String>['moduleDir', 'module_dir']),
      webRoot: _readStringAlias(json, <String>['webRoot', 'web_root']),
      readonly: json['readonly'] == true,
      controllable: json['controllable'] == true,
      enabled: json['enabled'] != false,
      update: json['update'] == true,
      remove: json['remove'] == true,
      hasWebUi: _readBoolAlias(json, <String>['hasWebUi', 'has_web_ui']),
      hasActionScript: _readBoolAlias(json, <String>[
        'hasActionScript',
        'has_action_script',
      ]),
      actionSupported: _readBoolAlias(json, <String>[
        'actionSupported',
        'action_supported',
      ]),
      requiresCompanionApp: _readBoolAlias(json, <String>[
        'requiresCompanionApp',
        'requires_companion_app',
      ]),
      settingsSupported: _readBoolAlias(json, <String>[
        'settingsSupported',
        'settings_supported',
      ]),
      perAppSupported: _readBoolAlias(json, <String>[
        'perAppSupported',
        'per_app_supported',
      ]),
      oobePriority: _readIntAlias(json, <String>[
        'oobePriority',
        'oobe_priority',
      ]),
      kpmArgs: _readStringAlias(json, <String>['kpmArgs', 'kpm_args']),
      groupId: _readStringAlias(json, <String>['groupId', 'group_id']),
      groupName: _readStringAlias(json, <String>['groupName', 'group_name']),
      groupRole: _readStringAlias(json, <String>['groupRole', 'group_role']),
      groupDescription: _readStringAlias(json, <String>[
        'groupDescription',
        'group_description',
      ]),
      groupRepoUrl: _readStringAlias(json, <String>[
        'groupRepoUrl',
        'group_repo_url',
      ]),
    );
  }
}

class KernelFeaturesEnvelope {
  const KernelFeaturesEnvelope({
    required this.rootGranted,
    required this.managerAccessKind,
    required this.managerDiagnostic,
    required this.items,
  });

  final bool rootGranted;
  final String managerAccessKind;
  final String? managerDiagnostic;
  final List<KernelFeatureItem> items;

  factory KernelFeaturesEnvelope.fromJson(Map<String, dynamic> json) {
    return KernelFeaturesEnvelope(
      rootGranted: json['rootGranted'] == true,
      managerAccessKind: _readString(json['managerAccessKind']),
      managerDiagnostic: _nullableString(json['managerDiagnostic']),
      items: _readMapList(
        json['items'],
      ).map(KernelFeatureItem.fromJson).toList(growable: false),
    );
  }
}

class KernelFeatureItem {
  const KernelFeatureItem({
    required this.id,
    required this.checked,
    required this.enabled,
    required this.status,
  });

  final String id;
  final bool checked;
  final bool enabled;
  final String status;

  bool get isSupported => status == 'supported' || status == 'managed';
  bool get isManaged => status == 'managed';

  factory KernelFeatureItem.fromJson(Map<String, dynamic> json) {
    return KernelFeatureItem(
      id: _readString(json['id']),
      checked: json['checked'] == true,
      enabled: json['enabled'] == true,
      status: _readString(json['status'], fallback: 'unsupported'),
    );
  }
}

class RootGrantsEnvelope {
  const RootGrantsEnvelope({
    required this.rootGranted,
    required this.managerAccessKind,
    required this.managerDiagnostic,
    required this.apps,
  });

  final bool rootGranted;
  final String managerAccessKind;
  final String? managerDiagnostic;
  final List<RootGrantApp> apps;

  factory RootGrantsEnvelope.fromJson(Map<String, dynamic> json) {
    return RootGrantsEnvelope(
      rootGranted: json['rootGranted'] == true,
      managerAccessKind: _readString(json['managerAccessKind']),
      managerDiagnostic: _nullableString(json['managerDiagnostic']),
      apps: _readMapList(
        json['apps'],
      ).map(RootGrantApp.fromJson).toList(growable: false),
    );
  }
}

class RootGrantApp {
  const RootGrantApp({
    required this.packageName,
    required this.label,
    required this.uid,
    required this.userName,
    required this.isSystemApp,
    required this.profile,
    required this.profileLoaded,
  });

  final String packageName;
  final String label;
  final int uid;
  final String userName;
  final bool isSystemApp;
  final RootGrantProfile profile;
  final bool profileLoaded;

  factory RootGrantApp.fromJson(Map<String, dynamic> json) {
    return RootGrantApp(
      packageName: _readString(json['packageName']),
      label: _readString(json['label']),
      uid: _readInt(json['uid']),
      userName: _readString(json['userName']),
      isSystemApp: json['isSystemApp'] == true,
      profile: RootGrantProfile.fromJson(_readMap(json['profile'])),
      profileLoaded: json['profileLoaded'] == true,
    );
  }
}

class RootGrantProfile {
  const RootGrantProfile({
    required this.name,
    required this.currentUid,
    required this.allowSu,
    required this.rootUseDefault,
    required this.rootTemplate,
    required this.uid,
    required this.gid,
    required this.groups,
    required this.capabilities,
    required this.context,
    required this.namespace,
    required this.flags,
    required this.nonRootUseDefault,
    required this.umountModules,
    required this.rules,
  });

  final String name;
  final int currentUid;
  final bool allowSu;
  final bool rootUseDefault;
  final String rootTemplate;
  final int uid;
  final int gid;
  final List<int> groups;
  final List<int> capabilities;
  final String context;
  final int namespace;
  final int flags;
  final bool nonRootUseDefault;
  final bool umountModules;
  final String rules;

  factory RootGrantProfile.fromJson(Map<String, dynamic> json) {
    return RootGrantProfile(
      name: _readString(json['name']),
      currentUid: _readInt(json['currentUid']),
      allowSu: json['allowSu'] == true,
      rootUseDefault: json['rootUseDefault'] != false,
      rootTemplate: _readString(json['rootTemplate']),
      uid: _readInt(json['uid']),
      gid: _readInt(json['gid']),
      groups: _readIntList(json['groups']),
      capabilities: _readIntList(json['capabilities']),
      context: _readString(json['context'], fallback: 'u:r:ksu:s0'),
      namespace: _readInt(json['namespace']),
      flags: _readInt(json['flags']),
      nonRootUseDefault: json['nonRootUseDefault'] != false,
      umountModules: json['umountModules'] != false,
      rules: _readString(json['rules']),
    );
  }
}

class PackageInfoSummary {
  const PackageInfoSummary({
    required this.packageName,
    required this.versionName,
    required this.versionCode,
    required this.appLabel,
    required this.isSystem,
    required this.uid,
  });

  final String packageName;
  final String versionName;
  final int versionCode;
  final String appLabel;
  final bool isSystem;
  final int uid;

  factory PackageInfoSummary.fromJson(Map<String, dynamic> json) {
    return PackageInfoSummary(
      packageName: _readString(json['packageName']),
      versionName: _readString(json['versionName']),
      versionCode: _readInt(json['versionCode']),
      appLabel: _readString(json['appLabel']),
      isSystem: json['isSystem'] == true,
      uid: _readInt(json['uid']),
    );
  }
}

class ShellOperationResult {
  const ShellOperationResult({required this.success, required this.output});

  final bool success;
  final List<String> output;

  factory ShellOperationResult.fromJson(Map<String, dynamic> json) {
    return ShellOperationResult(
      success: json['success'] == true,
      output: _readStringList(json['output']),
    );
  }

  String? get summary => output.isEmpty ? null : output.last.trim();
}

class SusfsEnvelope {
  const SusfsEnvelope({
    required this.rootGranted,
    required this.status,
    required this.config,
    required this.error,
  });

  final bool rootGranted;
  final SusfsRuntimeStatus? status;
  final Map<String, dynamic> config;
  final String? error;

  factory SusfsEnvelope.fromJson(Map<String, dynamic> json) {
    final statusRaw = _readMap(json['status']);
    return SusfsEnvelope(
      rootGranted: json['rootGranted'] == true,
      status: statusRaw.isEmpty ? null : SusfsRuntimeStatus.fromJson(statusRaw),
      config: _readMap(json['config']),
      error: _nullableString(json['error']),
    );
  }

  String prettyConfig() {
    return const JsonEncoder.withIndent('  ').convert(config);
  }
}

class SusfsRuntimeStatus {
  const SusfsRuntimeStatus({
    required this.available,
    required this.kernelVersion,
    required this.rawFeatureText,
    required this.featureFlags,
    required this.support,
    required this.bundledBinaryRef,
    required this.bundledBinaryVersion,
    required this.bundledBinaryPublishedAt,
    required this.bundledBinaryPath,
    required this.installedBinaryPath,
    required this.runtimeModuleId,
    required this.runtimeModuleDir,
    required this.configPath,
    required this.diagnostics,
  });

  final bool available;
  final String kernelVersion;
  final String rawFeatureText;
  final List<String> featureFlags;
  final SusfsSupportMatrix support;
  final String bundledBinaryRef;
  final String bundledBinaryVersion;
  final String bundledBinaryPublishedAt;
  final String bundledBinaryPath;
  final String installedBinaryPath;
  final String runtimeModuleId;
  final String runtimeModuleDir;
  final String configPath;
  final List<String> diagnostics;

  factory SusfsRuntimeStatus.fromJson(Map<String, dynamic> json) {
    return SusfsRuntimeStatus(
      available: json['available'] == true,
      kernelVersion: _readString(json['kernelVersion']),
      rawFeatureText: _readString(json['rawFeatureText']),
      featureFlags: _readStringList(json['featureFlags']),
      support: SusfsSupportMatrix.fromJson(_readMap(json['support'])),
      bundledBinaryRef: _readString(json['bundledBinaryRef']),
      bundledBinaryVersion: _readString(json['bundledBinaryVersion']),
      bundledBinaryPublishedAt: _readString(json['bundledBinaryPublishedAt']),
      bundledBinaryPath: _readString(json['bundledBinaryPath']),
      installedBinaryPath: _readString(json['installedBinaryPath']),
      runtimeModuleId: _readString(json['runtimeModuleId']),
      runtimeModuleDir: _readString(json['runtimeModuleDir']),
      configPath: _readString(json['configPath']),
      diagnostics: _readStringList(json['diagnostics']),
    );
  }
}

class SusfsSupportMatrix {
  const SusfsSupportMatrix({
    required this.log,
    required this.hideSusMountsForAll,
    required this.hideSusMountsForNonSu,
    required this.susPath,
    required this.susPathLoop,
    required this.susMap,
    required this.susMount,
    required this.tryUmount,
    required this.ksudKernelUmountFallback,
    required this.openRedirect,
    required this.staticKstat,
    required this.dynamicKstat,
    required this.setUname,
    required this.setCmdlineOrBootconfig,
    required this.setProcCmdline,
    required this.sdcardRootPath,
    required this.androidDataRootPath,
    required this.avcLogSpoofing,
    required this.spoofCmdlinePreset,
    required this.hideVendorSepolicyPreset,
    required this.hideCompatMatrixPreset,
    required this.hideGappsPreset,
    required this.hideRevancedPreset,
    required this.hideLoopsPreset,
    required this.autoTryUmountPreset,
    required this.forceHideLsposedPreset,
    required this.umountForZygoteIsoService,
  });

  final bool log;
  final bool hideSusMountsForAll;
  final bool hideSusMountsForNonSu;
  final bool susPath;
  final bool susPathLoop;
  final bool susMap;
  final bool susMount;
  final bool tryUmount;
  final bool ksudKernelUmountFallback;
  final bool openRedirect;
  final bool staticKstat;
  final bool dynamicKstat;
  final bool setUname;
  final bool setCmdlineOrBootconfig;
  final bool setProcCmdline;
  final bool sdcardRootPath;
  final bool androidDataRootPath;
  final bool avcLogSpoofing;
  final bool spoofCmdlinePreset;
  final bool hideVendorSepolicyPreset;
  final bool hideCompatMatrixPreset;
  final bool hideGappsPreset;
  final bool hideRevancedPreset;
  final bool hideLoopsPreset;
  final bool autoTryUmountPreset;
  final bool forceHideLsposedPreset;
  final bool umountForZygoteIsoService;

  factory SusfsSupportMatrix.fromJson(Map<String, dynamic> json) {
    bool read(String key, [bool fallback = false]) =>
        json[key] == null ? fallback : json[key] == true;
    return SusfsSupportMatrix(
      log: read('log', true),
      hideSusMountsForAll: read('hideSusMountsForAll'),
      hideSusMountsForNonSu: read('hideSusMountsForNonSu'),
      susPath: read('susPath', true),
      susPathLoop: read('susPathLoop'),
      susMap: read('susMap'),
      susMount: read('susMount'),
      tryUmount: read('tryUmount'),
      ksudKernelUmountFallback: read('ksudKernelUmountFallback'),
      openRedirect: read('openRedirect'),
      staticKstat: read('staticKstat'),
      dynamicKstat: read('dynamicKstat'),
      setUname: read('setUname'),
      setCmdlineOrBootconfig: read('setCmdlineOrBootconfig'),
      setProcCmdline: read('setProcCmdline'),
      sdcardRootPath: read('sdcardRootPath'),
      androidDataRootPath: read('androidDataRootPath'),
      avcLogSpoofing: read('avcLogSpoofing'),
      spoofCmdlinePreset: read('spoofCmdlinePreset'),
      hideVendorSepolicyPreset: read('hideVendorSepolicyPreset'),
      hideCompatMatrixPreset: read('hideCompatMatrixPreset'),
      hideGappsPreset: read('hideGappsPreset'),
      hideRevancedPreset: read('hideRevancedPreset'),
      hideLoopsPreset: read('hideLoopsPreset', true),
      autoTryUmountPreset: read('autoTryUmountPreset'),
      forceHideLsposedPreset: read('forceHideLsposedPreset'),
      umountForZygoteIsoService: read('umountForZygoteIsoService'),
    );
  }
}

String readPrettyJson(Map<String, dynamic> json) {
  return const JsonEncoder.withIndent('  ').convert(json);
}

dynamic _readAliasValue(Map<String, dynamic> json, List<String> keys) {
  for (final key in keys) {
    if (json.containsKey(key)) return json[key];
  }
  return null;
}

String _readStringAlias(
  Map<String, dynamic> json,
  List<String> keys, {
  String fallback = '',
}) {
  return _readString(_readAliasValue(json, keys), fallback: fallback);
}

int _readIntAlias(
  Map<String, dynamic> json,
  List<String> keys, {
  int fallback = 0,
}) {
  return _readInt(_readAliasValue(json, keys), fallback: fallback);
}

bool _readBoolAlias(
  Map<String, dynamic> json,
  List<String> keys, {
  bool fallback = false,
}) {
  final value = _readAliasValue(json, keys);
  if (value is bool) return value;
  return fallback;
}

Map<String, dynamic> _readMapAlias(
  Map<String, dynamic> json,
  List<String> keys,
) {
  return _readMap(_readAliasValue(json, keys));
}

List<Map<String, dynamic>> _readMapListAlias(
  Map<String, dynamic> json,
  List<String> keys,
) {
  return _readMapList(_readAliasValue(json, keys));
}

String _readString(dynamic value, {String fallback = ''}) {
  if (value is String) return value;
  return fallback;
}

String? _nullableString(dynamic value) {
  if (value is String && value.trim().isNotEmpty) return value;
  return null;
}

int _readInt(dynamic value, {int fallback = 0}) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  if (value is String) return int.tryParse(value) ?? fallback;
  return fallback;
}

Map<String, dynamic> _readMap(dynamic value) {
  if (value is Map<String, dynamic>) return value;
  if (value is Map) return Map<String, dynamic>.from(value);
  return const <String, dynamic>{};
}

List<Map<String, dynamic>> _readMapList(dynamic value) {
  if (value is! List) return const <Map<String, dynamic>>[];
  return value
      .whereType<Map>()
      .map((item) => Map<String, dynamic>.from(item))
      .toList(growable: false);
}

List<String> _readStringList(dynamic value) {
  if (value is! List) return const <String>[];
  return value.whereType<String>().toList(growable: false);
}

List<int> _readIntList(dynamic value) {
  if (value is! List) return const <int>[];
  return value
      .map((entry) => _readInt(entry, fallback: -1))
      .where((entry) => entry >= 0)
      .toList(growable: false);
}

Map<String, bool> _readBoolMap(dynamic value) {
  if (value is! Map) return const <String, bool>{};
  return Map<String, dynamic>.from(
    value,
  ).map((key, dynamic raw) => MapEntry(key, raw == true));
}
