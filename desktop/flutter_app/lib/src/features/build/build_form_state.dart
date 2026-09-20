import '../../core/models/build_models.dart';
import 'kernel_support.dart';

class BuildFormState {
  const BuildFormState({
    required this.target,
    required this.ksuVariant,
    required this.ksuBranch,
    required this.version,
    required this.revision,
    required this.customRef,
    required this.buildTime,
    required this.customModules,
    required this.kpmPassword,
    required this.virt,
    required this.zram,
    required this.bbg,
    required this.ddk,
    required this.kpm,
    required this.susfs,
    required this.rekernel,
    required this.ntsync,
    required this.networking,
    required this.zramFullAlgo,
    required this.zramExtraAlgos,
    required this.androidVersion,
    required this.kernelVersion,
    required this.subLevel,
    required this.osPatchLevel,
    required this.advancedOpen,
  });

  factory BuildFormState.defaults({RuntimeBuildSummary? runtime}) {
    final inferred = _inferFromRuntime(runtime);
    return BuildFormState(
      target: 'custom',
      ksuVariant: 'ReSukiSU',
      ksuBranch: 'Stable',
      version: '',
      revision: inferred.revision,
      customRef: '',
      buildTime: '',
      customModules: '',
      kpmPassword: '',
      virt: DesktopKernelSupport.normalizeVirtualizationSupport(
        inferred.kernelVersion,
        'off',
      ),
      zram: false,
      bbg: false,
      ddk: false,
      kpm: false,
      susfs: true,
      rekernel: false,
      ntsync: false,
      networking: false,
      zramFullAlgo: false,
      zramExtraAlgos: '',
      androidVersion: inferred.androidVersion,
      kernelVersion: inferred.kernelVersion,
      subLevel: inferred.subLevel,
      osPatchLevel: inferred.osPatchLevel,
      advancedOpen: false,
    ).normalized();
  }

  final String target;
  final String ksuVariant;
  final String ksuBranch;
  final String version;
  final String revision;
  final String customRef;
  final String buildTime;
  final String customModules;
  final String kpmPassword;
  final String virt;
  final bool zram;
  final bool bbg;
  final bool ddk;
  final bool kpm;
  final bool susfs;
  final bool rekernel;
  final bool ntsync;
  final bool networking;
  final bool zramFullAlgo;
  final String zramExtraAlgos;
  final String androidVersion;
  final String kernelVersion;
  final String subLevel;
  final String osPatchLevel;
  final bool advancedOpen;

  BuildFormState copyWith({
    String? target,
    String? ksuVariant,
    String? ksuBranch,
    String? version,
    String? revision,
    String? customRef,
    String? buildTime,
    String? customModules,
    String? kpmPassword,
    String? virt,
    bool? zram,
    bool? bbg,
    bool? ddk,
    bool? kpm,
    bool? susfs,
    bool? rekernel,
    bool? ntsync,
    bool? networking,
    bool? zramFullAlgo,
    String? zramExtraAlgos,
    String? androidVersion,
    String? kernelVersion,
    String? subLevel,
    String? osPatchLevel,
    bool? advancedOpen,
  }) {
    return BuildFormState(
      target: target ?? this.target,
      ksuVariant: ksuVariant ?? this.ksuVariant,
      ksuBranch: ksuBranch ?? this.ksuBranch,
      version: version ?? this.version,
      revision: revision ?? this.revision,
      customRef: customRef ?? this.customRef,
      buildTime: buildTime ?? this.buildTime,
      customModules: customModules ?? this.customModules,
      kpmPassword: kpmPassword ?? this.kpmPassword,
      virt: virt ?? this.virt,
      zram: zram ?? this.zram,
      bbg: bbg ?? this.bbg,
      ddk: ddk ?? this.ddk,
      kpm: kpm ?? this.kpm,
      susfs: susfs ?? this.susfs,
      rekernel: rekernel ?? this.rekernel,
      ntsync: ntsync ?? this.ntsync,
      networking: networking ?? this.networking,
      zramFullAlgo: zramFullAlgo ?? this.zramFullAlgo,
      zramExtraAlgos: zramExtraAlgos ?? this.zramExtraAlgos,
      androidVersion: androidVersion ?? this.androidVersion,
      kernelVersion: kernelVersion ?? this.kernelVersion,
      subLevel: subLevel ?? this.subLevel,
      osPatchLevel: osPatchLevel ?? this.osPatchLevel,
      advancedOpen: advancedOpen ?? this.advancedOpen,
    );
  }

  BuildFormState normalized() {
    final line = DesktopKernelSupport.lineFor(androidVersion, kernelVersion);
    final normalizedAndroid = line.androidVersion;
    final normalizedKernel = line.kernelVersion;

    final subLevelOptions = DesktopKernelSupport.subLevelOptions(
      normalizedAndroid,
      normalizedKernel,
    );
    final normalizedSubLevel = subLevelOptions.contains(subLevel)
        ? subLevel
        : DesktopKernelSupport.latestEntry(
            normalizedAndroid,
            normalizedKernel,
          ).subLevel;

    final patchOptions = DesktopKernelSupport.patchLevelOptions(
      normalizedAndroid,
      normalizedKernel,
      normalizedSubLevel,
    );
    final normalizedPatch = patchOptions.contains(osPatchLevel)
        ? osPatchLevel
        : (patchOptions.isEmpty
              ? DesktopKernelSupport.latestEntry(
                  normalizedAndroid,
                  normalizedKernel,
                ).osPatchLevel
              : patchOptions.last);

    final normalizedVariant = DesktopKernelSupport.normalizeKsuVariant(
      ksuVariant,
    );
    final normalizedBranch = normalizedVariant == 'None'
        ? 'Stable'
        : DesktopKernelSupport.normalizeKsuBranch(ksuBranch);
    final kpmSupported = DesktopKernelSupport.isKpmSupported(
      ksuVariant: normalizedVariant,
      ksuBranch: normalizedBranch,
    );
    final normalizedVirt = DesktopKernelSupport.normalizeVirtualizationSupport(
      normalizedKernel,
      virt,
    );

    return copyWith(
      target: 'custom',
      androidVersion: normalizedAndroid,
      kernelVersion: normalizedKernel,
      subLevel: normalizedSubLevel,
      osPatchLevel: normalizedPatch,
      revision: normalizedKernel == '5.10'
          ? (revision.trim().isEmpty ? 'r11' : revision.trim())
          : '',
      ksuVariant: normalizedVariant,
      ksuBranch: normalizedBranch,
      customRef: normalizedBranch == 'Custom' ? customRef.trim() : '',
      kpm: kpmSupported && kpm,
      susfs: normalizedVariant == 'None' ? false : susfs,
      virt: normalizedVirt,
      kpmPassword: kpmSupported ? kpmPassword : '',
      zramExtraAlgos: zramFullAlgo ? '' : zramExtraAlgos,
    );
  }

  Map<String, dynamic> toRequest() {
    final normalizedForm = normalized();
    return <String, dynamic>{
      'target': 'custom',
      'ksuVariant': normalizedForm.ksuVariant,
      'ksuBranch': normalizedForm.ksuBranch,
      'version': normalizedForm.version.trim(),
      'revision': normalizedForm.revision.trim(),
      'customRef': normalizedForm.customRef.trim(),
      'buildTime': normalizedForm.buildTime.trim(),
      'customModules': normalizedForm.customModules.trim(),
      'kpmPassword': normalizedForm.kpmPassword.trim(),
      'virt': normalizedForm.virt,
      'zram': normalizedForm.zram,
      'bbg': normalizedForm.bbg,
      'ddk': normalizedForm.ddk,
      'kpm': normalizedForm.kpm,
      'susfs': normalizedForm.susfs,
      'rekernel': normalizedForm.rekernel,
      'ntsync': normalizedForm.ntsync,
      'networking': normalizedForm.networking,
      'zramFullAlgo': normalizedForm.zramFullAlgo,
      'zramExtraAlgos': normalizedForm.zramExtraAlgos.trim(),
      'androidVersion': normalizedForm.androidVersion,
      'kernelVersion': normalizedForm.kernelVersion,
      'subLevel': normalizedForm.subLevel,
      'osPatchLevel': normalizedForm.osPatchLevel,
    };
  }

  factory BuildFormState.fromRequest(
    Map<String, dynamic> json, {
    RuntimeBuildSummary? runtime,
  }) {
    final defaults = BuildFormState.defaults(runtime: runtime);
    return defaults.copyWith(
      target: _readStringValue(json['target'], defaults.target),
      ksuVariant: _readStringValue(json['ksuVariant'], defaults.ksuVariant),
      ksuBranch: _readStringValue(json['ksuBranch'], defaults.ksuBranch),
      version: _readStringValue(json['version'], defaults.version),
      revision: _readStringValue(json['revision'], defaults.revision),
      customRef: _readStringValue(json['customRef'], defaults.customRef),
      buildTime: _readStringValue(json['buildTime'], defaults.buildTime),
      customModules: _readStringValue(
        json['customModules'],
        defaults.customModules,
      ),
      kpmPassword: _readStringValue(json['kpmPassword'], defaults.kpmPassword),
      virt: _readStringValue(json['virt'], defaults.virt),
      zram: _readBoolValue(json['zram'], defaults.zram),
      bbg: _readBoolValue(json['bbg'], defaults.bbg),
      ddk: _readBoolValue(json['ddk'], defaults.ddk),
      kpm: _readBoolValue(json['kpm'], defaults.kpm),
      susfs: _readBoolValue(json['susfs'], defaults.susfs),
      rekernel: _readBoolValue(json['rekernel'], defaults.rekernel),
      ntsync: _readBoolValue(json['ntsync'], defaults.ntsync),
      networking: _readBoolValue(json['networking'], defaults.networking),
      zramFullAlgo: _readBoolValue(
        json['zramFullAlgo'],
        defaults.zramFullAlgo,
      ),
      zramExtraAlgos: _readStringValue(
        json['zramExtraAlgos'],
        defaults.zramExtraAlgos,
      ),
      androidVersion: _readStringValue(
        json['androidVersion'],
        defaults.androidVersion,
      ),
      kernelVersion: _readStringValue(
        json['kernelVersion'],
        defaults.kernelVersion,
      ),
      subLevel: _readStringValue(json['subLevel'], defaults.subLevel),
      osPatchLevel: _readStringValue(
        json['osPatchLevel'],
        defaults.osPatchLevel,
      ),
    ).normalized();
  }
}

class _InferredBuildFields {
  const _InferredBuildFields({
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
}

String _readStringValue(dynamic value, String fallback) {
  if (value is String) {
    return value;
  }
  return fallback;
}

bool _readBoolValue(dynamic value, bool fallback) {
  if (value is bool) {
    return value;
  }
  return fallback;
}

_InferredBuildFields _inferFromRuntime(RuntimeBuildSummary? runtime) {
  if (runtime == null) {
    final latest = DesktopKernelSupport.latestEntry('android14', '6.1');
    return _InferredBuildFields(
      androidVersion: latest.androidVersion,
      kernelVersion: latest.kernelVersion,
      subLevel: latest.subLevel,
      osPatchLevel: latest.osPatchLevel,
      revision: '',
    );
  }

  final android = runtime.androidVersion.trim();
  final kernel = runtime.kernelVersion.trim();
  final line = DesktopKernelSupport.lineFor(
    android.isEmpty ? 'android14' : android,
    kernel.isEmpty ? '6.1' : kernel,
  );
  final latest = DesktopKernelSupport.latestEntry(
    line.androidVersion,
    line.kernelVersion,
  );
  return _InferredBuildFields(
    androidVersion: line.androidVersion,
    kernelVersion: line.kernelVersion,
    subLevel: runtime.subLevel.trim().isEmpty
        ? latest.subLevel
        : runtime.subLevel.trim(),
    osPatchLevel: runtime.osPatchLevel.trim().isEmpty
        ? latest.osPatchLevel
        : runtime.osPatchLevel.trim(),
    revision: line.kernelVersion == '5.10'
        ? (runtime.revision.trim().isEmpty ? 'r11' : runtime.revision.trim())
        : '',
  );
}
