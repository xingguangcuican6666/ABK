import 'dart:convert';

const String susfsHideMountsOff = 'off';
const String susfsHideMountsAll = 'all';
const String susfsHideMountsNonSu = 'non_su';
const String susfsSpoofUnameOff = 'off';
const String susfsSpoofUnamePostFsData = 'post_fs_data';
const String susfsSpoofUnameBootCompleted = 'boot_completed';
const String susfsOpenRedirectBootCompleted = 'boot_completed';
const String susfsOpenRedirectService = 'service';

class SusfsFormData {
  const SusfsFormData({
    required this.schemaVersion,
    required this.autoReplayEnabled,
    required this.logEnabled,
    required this.avcLogSpoofing,
    required this.hideSusMountsMode,
    required this.spoofUnameStage,
    required this.unameValue,
    required this.buildTimeValue,
    required this.sdcardRootPath,
    required this.androidDataRootPath,
    required this.pathRules,
    required this.loopPathRules,
    required this.maps,
    required this.mounts,
    required this.tryUmounts,
    required this.legitMounts,
    required this.openRedirects,
    required this.kstatEntries,
    required this.presets,
  });

  factory SusfsFormData.defaults() {
    return SusfsFormData(
      schemaVersion: 1,
      autoReplayEnabled: true,
      logEnabled: true,
      avcLogSpoofing: false,
      hideSusMountsMode: susfsHideMountsOff,
      spoofUnameStage: susfsSpoofUnameOff,
      unameValue: 'default',
      buildTimeValue: 'default',
      sdcardRootPath: '/sdcard',
      androidDataRootPath: '/sdcard/Android/data',
      pathRules: const <SusfsPathRuleData>[],
      loopPathRules: const <SusfsPathRuleData>[],
      maps: const <String>[],
      mounts: const <String>[],
      tryUmounts: const <String>[],
      legitMounts: defaultSusfsLegitMounts,
      openRedirects: const <SusfsOpenRedirectRuleData>[],
      kstatEntries: const <SusfsKstatEntryData>[],
      presets: SusfsPresetFormData.defaults(),
    );
  }

  factory SusfsFormData.fromJsonMap(Map<String, dynamic> json) {
    final presetsJson = _readMap(json['presets']);
    return SusfsFormData(
      schemaVersion: _clampInt(
        _readInt(json['schemaVersion'], fallback: 1),
        1,
        999,
      ),
      autoReplayEnabled: _readBool(json['autoReplayEnabled'], fallback: true),
      logEnabled: _readBool(json['logEnabled'], fallback: true),
      avcLogSpoofing: _readBool(json['avcLogSpoofing']),
      hideSusMountsMode: _normalizeHideSusMountsMode(
        _readString(json['hideSusMountsMode']),
      ),
      spoofUnameStage: _normalizeSpoofUnameStage(
        _readString(json['spoofUnameStage']),
      ),
      unameValue: _readString(
        json['unameValue'],
        fallback: 'default',
      ).ifEmpty('default'),
      buildTimeValue: _readString(
        json['buildTimeValue'],
        fallback: 'default',
      ).ifEmpty('default'),
      sdcardRootPath: _readString(
        json['sdcardRootPath'],
        fallback: '/sdcard',
      ).ifEmpty('/sdcard'),
      androidDataRootPath: _readString(
        json['androidDataRootPath'],
        fallback: '/sdcard/Android/data',
      ).ifEmpty('/sdcard/Android/data'),
      pathRules: _readMapList(json['pathRules'])
          .map(SusfsPathRuleData.fromJsonMap)
          .where((entry) => entry.path.isNotEmpty)
          .toList(growable: false),
      loopPathRules: _readMapList(json['loopPathRules'])
          .map(SusfsPathRuleData.fromJsonMap)
          .where((entry) => entry.path.isNotEmpty)
          .toList(growable: false),
      maps: _readStringList(json['maps']),
      mounts: _readStringList(json['mounts']),
      tryUmounts: _readStringList(json['tryUmounts']),
      legitMounts: _readStringList(
        json['legitMounts'],
      ).ifEmpty(defaultSusfsLegitMounts),
      openRedirects: _readMapList(json['openRedirects'])
          .map(SusfsOpenRedirectRuleData.fromJsonMap)
          .where(
            (entry) =>
                entry.originalPath.isNotEmpty &&
                entry.redirectedPath.isNotEmpty,
          )
          .toList(growable: false),
      kstatEntries: _readMapList(json['kstatEntries'])
          .map(SusfsKstatEntryData.fromJsonMap)
          .where((entry) => entry.path.isNotEmpty)
          .toList(growable: false),
      presets: SusfsPresetFormData.fromJsonMap(presetsJson),
    );
  }

  final int schemaVersion;
  final bool autoReplayEnabled;
  final bool logEnabled;
  final bool avcLogSpoofing;
  final String hideSusMountsMode;
  final String spoofUnameStage;
  final String unameValue;
  final String buildTimeValue;
  final String sdcardRootPath;
  final String androidDataRootPath;
  final List<SusfsPathRuleData> pathRules;
  final List<SusfsPathRuleData> loopPathRules;
  final List<String> maps;
  final List<String> mounts;
  final List<String> tryUmounts;
  final List<String> legitMounts;
  final List<SusfsOpenRedirectRuleData> openRedirects;
  final List<SusfsKstatEntryData> kstatEntries;
  final SusfsPresetFormData presets;

  SusfsFormData copyWith({
    int? schemaVersion,
    bool? autoReplayEnabled,
    bool? logEnabled,
    bool? avcLogSpoofing,
    String? hideSusMountsMode,
    String? spoofUnameStage,
    String? unameValue,
    String? buildTimeValue,
    String? sdcardRootPath,
    String? androidDataRootPath,
    List<SusfsPathRuleData>? pathRules,
    List<SusfsPathRuleData>? loopPathRules,
    List<String>? maps,
    List<String>? mounts,
    List<String>? tryUmounts,
    List<String>? legitMounts,
    List<SusfsOpenRedirectRuleData>? openRedirects,
    List<SusfsKstatEntryData>? kstatEntries,
    SusfsPresetFormData? presets,
  }) {
    return SusfsFormData(
      schemaVersion: schemaVersion ?? this.schemaVersion,
      autoReplayEnabled: autoReplayEnabled ?? this.autoReplayEnabled,
      logEnabled: logEnabled ?? this.logEnabled,
      avcLogSpoofing: avcLogSpoofing ?? this.avcLogSpoofing,
      hideSusMountsMode: hideSusMountsMode == null
          ? this.hideSusMountsMode
          : _normalizeHideSusMountsMode(hideSusMountsMode),
      spoofUnameStage: spoofUnameStage == null
          ? this.spoofUnameStage
          : _normalizeSpoofUnameStage(spoofUnameStage),
      unameValue: unameValue ?? this.unameValue,
      buildTimeValue: buildTimeValue ?? this.buildTimeValue,
      sdcardRootPath: sdcardRootPath ?? this.sdcardRootPath,
      androidDataRootPath: androidDataRootPath ?? this.androidDataRootPath,
      pathRules: pathRules ?? this.pathRules,
      loopPathRules: loopPathRules ?? this.loopPathRules,
      maps: maps ?? this.maps,
      mounts: mounts ?? this.mounts,
      tryUmounts: tryUmounts ?? this.tryUmounts,
      legitMounts: legitMounts ?? this.legitMounts,
      openRedirects: openRedirects ?? this.openRedirects,
      kstatEntries: kstatEntries ?? this.kstatEntries,
      presets: presets ?? this.presets,
    );
  }

  Map<String, dynamic> toJsonMap() {
    return <String, dynamic>{
      'schemaVersion': schemaVersion,
      'autoReplayEnabled': autoReplayEnabled,
      'logEnabled': logEnabled,
      'avcLogSpoofing': avcLogSpoofing,
      'hideSusMountsMode': _normalizeHideSusMountsMode(hideSusMountsMode),
      'spoofUnameStage': _normalizeSpoofUnameStage(spoofUnameStage),
      'unameValue': unameValue.trim().ifEmpty('default'),
      'buildTimeValue': buildTimeValue.trim().ifEmpty('default'),
      'sdcardRootPath': sdcardRootPath.trim().ifEmpty('/sdcard'),
      'androidDataRootPath': androidDataRootPath.trim().ifEmpty(
        '/sdcard/Android/data',
      ),
      'pathRules': pathRules
          .where((entry) => entry.path.trim().isNotEmpty)
          .map((entry) => entry.toJsonMap())
          .toList(growable: false),
      'loopPathRules': loopPathRules
          .where((entry) => entry.path.trim().isNotEmpty)
          .map((entry) => entry.toJsonMap())
          .toList(growable: false),
      'maps': maps
          .map((entry) => entry.trim())
          .where((entry) => entry.isNotEmpty)
          .toList(growable: false),
      'mounts': mounts
          .map((entry) => entry.trim())
          .where((entry) => entry.isNotEmpty)
          .toList(growable: false),
      'tryUmounts': tryUmounts
          .map((entry) => entry.trim())
          .where((entry) => entry.isNotEmpty)
          .toList(growable: false),
      'legitMounts': legitMounts
          .map((entry) => entry.trim())
          .where((entry) => entry.isNotEmpty)
          .toList(growable: false),
      'openRedirects': openRedirects
          .where(
            (entry) =>
                entry.originalPath.trim().isNotEmpty &&
                entry.redirectedPath.trim().isNotEmpty,
          )
          .map((entry) => entry.toJsonMap())
          .toList(growable: false),
      'kstatEntries': kstatEntries
          .where((entry) => entry.path.trim().isNotEmpty)
          .map((entry) => entry.toJsonMap())
          .toList(growable: false),
      'presets': presets.toJsonMap(),
    };
  }

  String toPrettyJson() {
    return const JsonEncoder.withIndent('  ').convert(toJsonMap());
  }
}

class SusfsEditorDraft {
  const SusfsEditorDraft({
    required this.schemaVersion,
    required this.autoReplayEnabled,
    required this.logEnabled,
    required this.avcLogSpoofing,
    required this.hideSusMountsMode,
    required this.spoofUnameStage,
    required this.unameValue,
    required this.buildTimeValue,
    required this.sdcardRootPath,
    required this.androidDataRootPath,
    required this.hideCustomRomLevel,
    required this.emulateVoldAppDataMode,
    required this.hideVendorSepolicy,
    required this.hideCompatMatrix,
    required this.hideGapps,
    required this.hideRevanced,
    required this.spoofCmdline,
    required this.hideLoops,
    required this.forceHideLsposed,
    required this.autoTryUmount,
    required this.skipLegitMounts,
    required this.umountForZygoteIsoService,
    required this.pathRulesText,
    required this.loopPathRulesText,
    required this.mapsText,
    required this.mountsText,
    required this.tryUmountText,
    required this.legitMountsText,
    required this.openRedirectText,
    required this.kstatJsonText,
  });

  factory SusfsEditorDraft.defaults() {
    return SusfsEditorDraft.fromFormData(SusfsFormData.defaults());
  }

  factory SusfsEditorDraft.fromFormData(SusfsFormData data) {
    return SusfsEditorDraft(
      schemaVersion: data.schemaVersion,
      autoReplayEnabled: data.autoReplayEnabled,
      logEnabled: data.logEnabled,
      avcLogSpoofing: data.avcLogSpoofing,
      hideSusMountsMode: data.hideSusMountsMode,
      spoofUnameStage: data.spoofUnameStage,
      unameValue: data.unameValue,
      buildTimeValue: data.buildTimeValue,
      sdcardRootPath: data.sdcardRootPath,
      androidDataRootPath: data.androidDataRootPath,
      hideCustomRomLevel: data.presets.hideCustomRomLevel,
      emulateVoldAppDataMode: data.presets.emulateVoldAppDataMode,
      hideVendorSepolicy: data.presets.hideVendorSepolicy,
      hideCompatMatrix: data.presets.hideCompatMatrix,
      hideGapps: data.presets.hideGapps,
      hideRevanced: data.presets.hideRevanced,
      spoofCmdline: data.presets.spoofCmdline,
      hideLoops: data.presets.hideLoops,
      forceHideLsposed: data.presets.forceHideLsposed,
      autoTryUmount: data.presets.autoTryUmount,
      skipLegitMounts: data.presets.skipLegitMounts,
      umountForZygoteIsoService: data.presets.umountForZygoteIsoService,
      pathRulesText: renderSusfsPathRules(data.pathRules),
      loopPathRulesText: renderSusfsPathRules(data.loopPathRules),
      mapsText: renderSusfsStringList(data.maps),
      mountsText: renderSusfsStringList(data.mounts),
      tryUmountText: renderSusfsStringList(data.tryUmounts),
      legitMountsText: renderSusfsStringList(data.legitMounts),
      openRedirectText: renderSusfsOpenRedirects(data.openRedirects),
      kstatJsonText: renderSusfsKstatJson(data.kstatEntries),
    );
  }

  final int schemaVersion;
  final bool autoReplayEnabled;
  final bool logEnabled;
  final bool avcLogSpoofing;
  final String hideSusMountsMode;
  final String spoofUnameStage;
  final String unameValue;
  final String buildTimeValue;
  final String sdcardRootPath;
  final String androidDataRootPath;
  final int hideCustomRomLevel;
  final int emulateVoldAppDataMode;
  final bool hideVendorSepolicy;
  final bool hideCompatMatrix;
  final bool hideGapps;
  final bool hideRevanced;
  final bool spoofCmdline;
  final bool hideLoops;
  final bool forceHideLsposed;
  final bool autoTryUmount;
  final bool skipLegitMounts;
  final bool umountForZygoteIsoService;
  final String pathRulesText;
  final String loopPathRulesText;
  final String mapsText;
  final String mountsText;
  final String tryUmountText;
  final String legitMountsText;
  final String openRedirectText;
  final String kstatJsonText;

  SusfsEditorDraft copyWith({
    int? schemaVersion,
    bool? autoReplayEnabled,
    bool? logEnabled,
    bool? avcLogSpoofing,
    String? hideSusMountsMode,
    String? spoofUnameStage,
    String? unameValue,
    String? buildTimeValue,
    String? sdcardRootPath,
    String? androidDataRootPath,
    int? hideCustomRomLevel,
    int? emulateVoldAppDataMode,
    bool? hideVendorSepolicy,
    bool? hideCompatMatrix,
    bool? hideGapps,
    bool? hideRevanced,
    bool? spoofCmdline,
    bool? hideLoops,
    bool? forceHideLsposed,
    bool? autoTryUmount,
    bool? skipLegitMounts,
    bool? umountForZygoteIsoService,
    String? pathRulesText,
    String? loopPathRulesText,
    String? mapsText,
    String? mountsText,
    String? tryUmountText,
    String? legitMountsText,
    String? openRedirectText,
    String? kstatJsonText,
  }) {
    return SusfsEditorDraft(
      schemaVersion: schemaVersion ?? this.schemaVersion,
      autoReplayEnabled: autoReplayEnabled ?? this.autoReplayEnabled,
      logEnabled: logEnabled ?? this.logEnabled,
      avcLogSpoofing: avcLogSpoofing ?? this.avcLogSpoofing,
      hideSusMountsMode: hideSusMountsMode == null
          ? this.hideSusMountsMode
          : _normalizeHideSusMountsMode(hideSusMountsMode),
      spoofUnameStage: spoofUnameStage == null
          ? this.spoofUnameStage
          : _normalizeSpoofUnameStage(spoofUnameStage),
      unameValue: unameValue ?? this.unameValue,
      buildTimeValue: buildTimeValue ?? this.buildTimeValue,
      sdcardRootPath: sdcardRootPath ?? this.sdcardRootPath,
      androidDataRootPath: androidDataRootPath ?? this.androidDataRootPath,
      hideCustomRomLevel: hideCustomRomLevel == null
          ? this.hideCustomRomLevel
          : _clampInt(hideCustomRomLevel, 0, 5),
      emulateVoldAppDataMode: emulateVoldAppDataMode == null
          ? this.emulateVoldAppDataMode
          : _clampInt(emulateVoldAppDataMode, 0, 2),
      hideVendorSepolicy: hideVendorSepolicy ?? this.hideVendorSepolicy,
      hideCompatMatrix: hideCompatMatrix ?? this.hideCompatMatrix,
      hideGapps: hideGapps ?? this.hideGapps,
      hideRevanced: hideRevanced ?? this.hideRevanced,
      spoofCmdline: spoofCmdline ?? this.spoofCmdline,
      hideLoops: hideLoops ?? this.hideLoops,
      forceHideLsposed: forceHideLsposed ?? this.forceHideLsposed,
      autoTryUmount: autoTryUmount ?? this.autoTryUmount,
      skipLegitMounts: skipLegitMounts ?? this.skipLegitMounts,
      umountForZygoteIsoService:
          umountForZygoteIsoService ?? this.umountForZygoteIsoService,
      pathRulesText: pathRulesText ?? this.pathRulesText,
      loopPathRulesText: loopPathRulesText ?? this.loopPathRulesText,
      mapsText: mapsText ?? this.mapsText,
      mountsText: mountsText ?? this.mountsText,
      tryUmountText: tryUmountText ?? this.tryUmountText,
      legitMountsText: legitMountsText ?? this.legitMountsText,
      openRedirectText: openRedirectText ?? this.openRedirectText,
      kstatJsonText: kstatJsonText ?? this.kstatJsonText,
    );
  }

  SusfsFormData toFormData() {
    return SusfsFormData(
      schemaVersion: _clampInt(schemaVersion, 1, 999),
      autoReplayEnabled: autoReplayEnabled,
      logEnabled: logEnabled,
      avcLogSpoofing: avcLogSpoofing,
      hideSusMountsMode: _normalizeHideSusMountsMode(hideSusMountsMode),
      spoofUnameStage: _normalizeSpoofUnameStage(spoofUnameStage),
      unameValue: unameValue.trim().ifEmpty('default'),
      buildTimeValue: buildTimeValue.trim().ifEmpty('default'),
      sdcardRootPath: sdcardRootPath.trim().ifEmpty('/sdcard'),
      androidDataRootPath: androidDataRootPath.trim().ifEmpty(
        '/sdcard/Android/data',
      ),
      pathRules: parseSusfsPathRules(pathRulesText),
      loopPathRules: parseSusfsPathRules(loopPathRulesText),
      maps: parseSusfsStringList(mapsText),
      mounts: parseSusfsStringList(mountsText),
      tryUmounts: parseSusfsStringList(tryUmountText),
      legitMounts: parseSusfsStringList(
        legitMountsText,
      ).ifEmpty(defaultSusfsLegitMounts),
      openRedirects: parseSusfsOpenRedirects(openRedirectText),
      kstatEntries: parseSusfsKstatJson(kstatJsonText),
      presets: SusfsPresetFormData(
        hideCustomRomLevel: _clampInt(hideCustomRomLevel, 0, 5),
        hideVendorSepolicy: hideVendorSepolicy,
        hideCompatMatrix: hideCompatMatrix,
        hideGapps: hideGapps,
        hideRevanced: hideRevanced,
        spoofCmdline: spoofCmdline,
        hideLoops: hideLoops,
        forceHideLsposed: forceHideLsposed,
        autoTryUmount: autoTryUmount,
        skipLegitMounts: skipLegitMounts,
        emulateVoldAppDataMode: _clampInt(emulateVoldAppDataMode, 0, 2),
        umountForZygoteIsoService: umountForZygoteIsoService,
      ),
    );
  }
}

class SusfsPresetFormData {
  const SusfsPresetFormData({
    required this.hideCustomRomLevel,
    required this.hideVendorSepolicy,
    required this.hideCompatMatrix,
    required this.hideGapps,
    required this.hideRevanced,
    required this.spoofCmdline,
    required this.hideLoops,
    required this.forceHideLsposed,
    required this.autoTryUmount,
    required this.skipLegitMounts,
    required this.emulateVoldAppDataMode,
    required this.umountForZygoteIsoService,
  });

  factory SusfsPresetFormData.defaults() {
    return const SusfsPresetFormData(
      hideCustomRomLevel: 0,
      hideVendorSepolicy: false,
      hideCompatMatrix: false,
      hideGapps: false,
      hideRevanced: false,
      spoofCmdline: false,
      hideLoops: true,
      forceHideLsposed: false,
      autoTryUmount: false,
      skipLegitMounts: false,
      emulateVoldAppDataMode: 0,
      umountForZygoteIsoService: false,
    );
  }

  factory SusfsPresetFormData.fromJsonMap(Map<String, dynamic> json) {
    final defaults = SusfsPresetFormData.defaults();
    return SusfsPresetFormData(
      hideCustomRomLevel: _clampInt(
        _readInt(
          json['hideCustomRomLevel'],
          fallback: defaults.hideCustomRomLevel,
        ),
        0,
        5,
      ),
      hideVendorSepolicy: _readBool(
        json['hideVendorSepolicy'],
        fallback: defaults.hideVendorSepolicy,
      ),
      hideCompatMatrix: _readBool(
        json['hideCompatMatrix'],
        fallback: defaults.hideCompatMatrix,
      ),
      hideGapps: _readBool(json['hideGapps'], fallback: defaults.hideGapps),
      hideRevanced: _readBool(
        json['hideRevanced'],
        fallback: defaults.hideRevanced,
      ),
      spoofCmdline: _readBool(
        json['spoofCmdline'],
        fallback: defaults.spoofCmdline,
      ),
      hideLoops: _readBool(json['hideLoops'], fallback: defaults.hideLoops),
      forceHideLsposed: _readBool(
        json['forceHideLsposed'],
        fallback: defaults.forceHideLsposed,
      ),
      autoTryUmount: _readBool(
        json['autoTryUmount'],
        fallback: defaults.autoTryUmount,
      ),
      skipLegitMounts: _readBool(
        json['skipLegitMounts'],
        fallback: defaults.skipLegitMounts,
      ),
      emulateVoldAppDataMode: _clampInt(
        _readInt(
          json['emulateVoldAppDataMode'],
          fallback: defaults.emulateVoldAppDataMode,
        ),
        0,
        2,
      ),
      umountForZygoteIsoService: _readBool(
        json['umountForZygoteIsoService'],
        fallback: defaults.umountForZygoteIsoService,
      ),
    );
  }

  final int hideCustomRomLevel;
  final bool hideVendorSepolicy;
  final bool hideCompatMatrix;
  final bool hideGapps;
  final bool hideRevanced;
  final bool spoofCmdline;
  final bool hideLoops;
  final bool forceHideLsposed;
  final bool autoTryUmount;
  final bool skipLegitMounts;
  final int emulateVoldAppDataMode;
  final bool umountForZygoteIsoService;

  SusfsPresetFormData copyWith({
    int? hideCustomRomLevel,
    bool? hideVendorSepolicy,
    bool? hideCompatMatrix,
    bool? hideGapps,
    bool? hideRevanced,
    bool? spoofCmdline,
    bool? hideLoops,
    bool? forceHideLsposed,
    bool? autoTryUmount,
    bool? skipLegitMounts,
    int? emulateVoldAppDataMode,
    bool? umountForZygoteIsoService,
  }) {
    return SusfsPresetFormData(
      hideCustomRomLevel: hideCustomRomLevel == null
          ? this.hideCustomRomLevel
          : _clampInt(hideCustomRomLevel, 0, 5),
      hideVendorSepolicy: hideVendorSepolicy ?? this.hideVendorSepolicy,
      hideCompatMatrix: hideCompatMatrix ?? this.hideCompatMatrix,
      hideGapps: hideGapps ?? this.hideGapps,
      hideRevanced: hideRevanced ?? this.hideRevanced,
      spoofCmdline: spoofCmdline ?? this.spoofCmdline,
      hideLoops: hideLoops ?? this.hideLoops,
      forceHideLsposed: forceHideLsposed ?? this.forceHideLsposed,
      autoTryUmount: autoTryUmount ?? this.autoTryUmount,
      skipLegitMounts: skipLegitMounts ?? this.skipLegitMounts,
      emulateVoldAppDataMode: emulateVoldAppDataMode == null
          ? this.emulateVoldAppDataMode
          : _clampInt(emulateVoldAppDataMode, 0, 2),
      umountForZygoteIsoService:
          umountForZygoteIsoService ?? this.umountForZygoteIsoService,
    );
  }

  Map<String, dynamic> toJsonMap() {
    return <String, dynamic>{
      'hideCustomRomLevel': _clampInt(hideCustomRomLevel, 0, 5),
      'hideVendorSepolicy': hideVendorSepolicy,
      'hideCompatMatrix': hideCompatMatrix,
      'hideGapps': hideGapps,
      'hideRevanced': hideRevanced,
      'spoofCmdline': spoofCmdline,
      'hideLoops': hideLoops,
      'forceHideLsposed': forceHideLsposed,
      'autoTryUmount': autoTryUmount,
      'skipLegitMounts': skipLegitMounts,
      'emulateVoldAppDataMode': _clampInt(emulateVoldAppDataMode, 0, 2),
      'umountForZygoteIsoService': umountForZygoteIsoService,
    };
  }
}

class SusfsPathRuleData {
  const SusfsPathRuleData({required this.path, required this.maxTries});

  factory SusfsPathRuleData.fromJsonMap(Map<String, dynamic> json) {
    return SusfsPathRuleData(
      path: _readString(json['path']).trim(),
      maxTries: _readNullableInt(json['maxTries']),
    );
  }

  final String path;
  final int? maxTries;

  Map<String, dynamic> toJsonMap() {
    return <String, dynamic>{
      'path': path.trim(),
      if (maxTries != null) 'maxTries': maxTries,
    };
  }
}

class SusfsOpenRedirectRuleData {
  const SusfsOpenRedirectRuleData({
    required this.originalPath,
    required this.redirectedPath,
    required this.stage,
    required this.uidScheme,
  });

  factory SusfsOpenRedirectRuleData.fromJsonMap(Map<String, dynamic> json) {
    return SusfsOpenRedirectRuleData(
      originalPath: _readStringAlias(json, const <String>[
        'originalPath',
        'original_path',
      ]).trim(),
      redirectedPath: _readStringAlias(json, const <String>[
        'redirectedPath',
        'redirected_path',
      ]).trim(),
      stage: _normalizeOpenRedirectStage(
        _readString(json['stage'], fallback: susfsOpenRedirectBootCompleted),
      ),
      uidScheme:
          _readNullableIntAlias(json, const <String>[
                'uidScheme',
                'uid_scheme',
              ]) ==
              null
          ? null
          : _clampInt(
              _readNullableIntAlias(json, const <String>[
                'uidScheme',
                'uid_scheme',
              ])!,
              0,
              4,
            ),
    );
  }

  final String originalPath;
  final String redirectedPath;
  final String stage;
  final int? uidScheme;

  Map<String, dynamic> toJsonMap() {
    return <String, dynamic>{
      'originalPath': originalPath.trim(),
      'redirectedPath': redirectedPath.trim(),
      'stage': _normalizeOpenRedirectStage(stage),
      if (uidScheme != null) 'uidScheme': _clampInt(uidScheme!, 0, 4),
    };
  }
}

class SusfsKstatEntryData {
  const SusfsKstatEntryData({
    required this.path,
    required this.ino,
    required this.dev,
    required this.nlink,
    required this.size,
    required this.atime,
    required this.atimeNsec,
    required this.mtime,
    required this.mtimeNsec,
    required this.ctime,
    required this.ctimeNsec,
    required this.blocks,
    required this.blksize,
  });

  factory SusfsKstatEntryData.fromJsonMap(Map<String, dynamic> json) {
    return SusfsKstatEntryData(
      path: _readString(json['path']).trim(),
      ino: _readString(json['ino'], fallback: 'default'),
      dev: _readString(json['dev'], fallback: 'default'),
      nlink: _readString(json['nlink'], fallback: 'default'),
      size: _readString(json['size'], fallback: 'default'),
      atime: _readString(json['atime'], fallback: '0'),
      atimeNsec: _readStringAlias(json, const <String>[
        'atimeNsec',
        'atime_nsec',
      ], fallback: '0'),
      mtime: _readString(json['mtime'], fallback: '0'),
      mtimeNsec: _readStringAlias(json, const <String>[
        'mtimeNsec',
        'mtime_nsec',
      ], fallback: '0'),
      ctime: _readString(json['ctime'], fallback: '0'),
      ctimeNsec: _readStringAlias(json, const <String>[
        'ctimeNsec',
        'ctime_nsec',
      ], fallback: '0'),
      blocks: _readString(json['blocks'], fallback: '0'),
      blksize: _readString(json['blksize'], fallback: '0'),
    );
  }

  final String path;
  final String ino;
  final String dev;
  final String nlink;
  final String size;
  final String atime;
  final String atimeNsec;
  final String mtime;
  final String mtimeNsec;
  final String ctime;
  final String ctimeNsec;
  final String blocks;
  final String blksize;

  Map<String, dynamic> toJsonMap() {
    return <String, dynamic>{
      'path': path.trim(),
      'ino': ino,
      'dev': dev,
      'nlink': nlink,
      'size': size,
      'atime': atime,
      'atimeNsec': atimeNsec,
      'mtime': mtime,
      'mtimeNsec': mtimeNsec,
      'ctime': ctime,
      'ctimeNsec': ctimeNsec,
      'blocks': blocks,
      'blksize': blksize,
    };
  }
}

List<SusfsPathRuleData> parseSusfsPathRules(String raw) {
  return raw
      .split('\n')
      .map((line) => line.trim())
      .where((line) => line.isNotEmpty && !line.startsWith('#'))
      .map((line) {
        final parts = line.split(RegExp(r'\s+'));
        final path = parts.isEmpty ? '' : parts.first.trim();
        if (path.isEmpty) {
          throw const FormatException('存在空路径规则');
        }
        int? maxTries;
        if (parts.length > 1) {
          maxTries = int.tryParse(parts.sublist(1).join(' ').trim());
          if (maxTries == null) {
            throw FormatException('路径规则重试次数无效: $line');
          }
        }
        return SusfsPathRuleData(path: path, maxTries: maxTries);
      })
      .toList(growable: false);
}

String renderSusfsPathRules(List<SusfsPathRuleData> rules) {
  return rules
      .map(
        (rule) => rule.maxTries == null
            ? rule.path.trim()
            : '${rule.path.trim()} ${rule.maxTries}',
      )
      .join('\n');
}

List<String> parseSusfsStringList(String raw) {
  return raw
      .split('\n')
      .map((line) => line.trim())
      .where((line) => line.isNotEmpty && !line.startsWith('#'))
      .toList(growable: false);
}

String renderSusfsStringList(List<String> values) {
  return values.map((entry) => entry.trim()).join('\n');
}

int countVisibleRuleLines(String raw) {
  return raw
      .split('\n')
      .map((line) => line.trim())
      .where((line) => line.isNotEmpty && !line.startsWith('#'))
      .length;
}

List<SusfsOpenRedirectRuleData> parseSusfsOpenRedirects(String raw) {
  return raw
      .split('\n')
      .map((line) => line.trim())
      .where((line) => line.isNotEmpty && !line.startsWith('#'))
      .map((line) {
        final parts = line.split(RegExp(r'\s+'));
        if (parts.length < 3) {
          throw FormatException(
            'Open redirect 行至少需要 original redirected stage: $line',
          );
        }
        final uidScheme = parts.length >= 4 ? int.tryParse(parts[3]) : null;
        if (parts.length >= 4 && uidScheme == null) {
          throw FormatException('Open redirect uid_scheme 无效: $line');
        }
        return SusfsOpenRedirectRuleData(
          originalPath: parts[0].trim(),
          redirectedPath: parts[1].trim(),
          stage: _normalizeOpenRedirectStage(parts[2]),
          uidScheme: uidScheme == null ? null : _clampInt(uidScheme, 0, 4),
        );
      })
      .toList(growable: false);
}

String renderSusfsOpenRedirects(List<SusfsOpenRedirectRuleData> values) {
  return values
      .map((rule) {
        final buffer = StringBuffer()
          ..write(rule.originalPath.trim())
          ..write(' ')
          ..write(rule.redirectedPath.trim())
          ..write(' ')
          ..write(_normalizeOpenRedirectStage(rule.stage));
        if (rule.uidScheme != null) {
          buffer
            ..write(' ')
            ..write(_clampInt(rule.uidScheme!, 0, 4));
        }
        return buffer.toString();
      })
      .join('\n');
}

List<SusfsKstatEntryData> parseSusfsKstatJson(String raw) {
  final clean = raw.trim().ifEmpty('[]');
  final decoded = jsonDecode(clean);
  if (decoded is! List) {
    throw const FormatException('KSTAT JSON 必须是数组');
  }
  return decoded
      .whereType<Map>()
      .map(
        (item) =>
            SusfsKstatEntryData.fromJsonMap(Map<String, dynamic>.from(item)),
      )
      .where((entry) => entry.path.isNotEmpty)
      .toList(growable: false);
}

String renderSusfsKstatJson(List<SusfsKstatEntryData> values) {
  return const JsonEncoder.withIndent(
    '  ',
  ).convert(values.map((item) => item.toJsonMap()).toList(growable: false));
}

const List<String> defaultSusfsLegitMounts = <String>[
  '/system',
  '/system_ext',
  '/vendor',
  '/odm',
  '/product',
  '/system_dlkm',
  '/vendor_dlkm',
  '/odm_dlkm',
  '/apex',
  '/system/app',
  '/system/priv-app',
  '/system/lib',
  '/system/lib64',
  '/vendor/app',
  '/vendor/priv-app',
  '/vendor/lib',
  '/vendor/lib64',
  '/product/app',
  '/product/priv-app',
  '/product/lib',
  '/product/lib64',
  '/system_ext/app',
  '/system_ext/priv-app',
  '/system_ext/lib',
  '/system_ext/lib64',
  '/data',
  '/cache',
  '/metadata',
  '/persist',
  '/mnt',
  '/storage',
  '/debug_ramdisk',
  '/dev',
  '/proc',
  '/sys',
  '/sys/fs/cgroup',
  '/my_product',
  '/my_engineering',
  '/my_company',
  '/my_carrier',
  '/my_region',
  '/my_heytap',
  '/my_stock',
  '/my_preload',
  '/my_bigball',
  '/my_manifest',
];

dynamic _readAliasValue(Map<String, dynamic> json, List<String> keys) {
  for (final key in keys) {
    if (json.containsKey(key)) {
      return json[key];
    }
  }
  return null;
}

String _readString(dynamic value, {String fallback = ''}) {
  if (value is String) {
    return value;
  }
  return fallback;
}

String _readStringAlias(
  Map<String, dynamic> json,
  List<String> keys, {
  String fallback = '',
}) {
  return _readString(_readAliasValue(json, keys), fallback: fallback);
}

bool _readBool(dynamic value, {bool fallback = false}) {
  if (value is bool) {
    return value;
  }
  return fallback;
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

int? _readNullableInt(dynamic value) {
  if (value == null) {
    return null;
  }
  if (value is int) {
    return value;
  }
  if (value is num) {
    return value.toInt();
  }
  if (value is String) {
    return int.tryParse(value);
  }
  return null;
}

int? _readNullableIntAlias(Map<String, dynamic> json, List<String> keys) {
  return _readNullableInt(_readAliasValue(json, keys));
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

String _normalizeHideSusMountsMode(String raw) {
  return switch (raw.trim().toLowerCase()) {
    susfsHideMountsAll => susfsHideMountsAll,
    susfsHideMountsNonSu => susfsHideMountsNonSu,
    _ => susfsHideMountsOff,
  };
}

String _normalizeSpoofUnameStage(String raw) {
  return switch (raw.trim().toLowerCase()) {
    susfsSpoofUnamePostFsData => susfsSpoofUnamePostFsData,
    susfsSpoofUnameBootCompleted => susfsSpoofUnameBootCompleted,
    _ => susfsSpoofUnameOff,
  };
}

String _normalizeOpenRedirectStage(String raw) {
  return switch (raw.trim().toLowerCase()) {
    '1' || susfsOpenRedirectService => susfsOpenRedirectService,
    _ => susfsOpenRedirectBootCompleted,
  };
}

int _clampInt(int value, int min, int max) {
  return value.clamp(min, max);
}

extension _StringFallback on String {
  String ifEmpty(String fallback) => isEmpty ? fallback : this;
}

extension _ListFallback<T> on List<T> {
  List<T> ifEmpty(List<T> fallback) => isEmpty ? fallback : this;
}
