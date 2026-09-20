class KernelVersionLine {
  const KernelVersionLine(this.androidVersion, this.kernelVersion);

  final String androidVersion;
  final String kernelVersion;
}

class KernelSupportEntry {
  const KernelSupportEntry(
    this.androidVersion,
    this.kernelVersion,
    this.subLevel,
    this.osPatchLevel,
  );

  final String androidVersion;
  final String kernelVersion;
  final String subLevel;
  final String osPatchLevel;
}

class DesktopKernelSupport {
  static const List<KernelVersionLine> lines = <KernelVersionLine>[
    KernelVersionLine('android12', '5.10'),
    KernelVersionLine('android13', '5.15'),
    KernelVersionLine('android14', '6.1'),
    KernelVersionLine('android15', '6.6'),
    KernelVersionLine('android16', '6.12'),
  ];

  static const List<KernelSupportEntry> entries = <KernelSupportEntry>[
    KernelSupportEntry('android12', '5.10', '43', '2021-08'),
    KernelSupportEntry('android12', '5.10', '43', '2021-09'),
    KernelSupportEntry('android12', '5.10', '43', '2021-10'),
    KernelSupportEntry('android12', '5.10', '66', '2021-11'),
    KernelSupportEntry('android12', '5.10', '66', '2021-12'),
    KernelSupportEntry('android12', '5.10', '66', '2022-01'),
    KernelSupportEntry('android12', '5.10', '81', '2022-02'),
    KernelSupportEntry('android12', '5.10', '81', '2022-03'),
    KernelSupportEntry('android12', '5.10', '101', '2022-04'),
    KernelSupportEntry('android12', '5.10', '101', '2022-05'),
    KernelSupportEntry('android12', '5.10', '110', '2022-06'),
    KernelSupportEntry('android12', '5.10', '110', '2022-07'),
    KernelSupportEntry('android12', '5.10', '117', '2022-08'),
    KernelSupportEntry('android12', '5.10', '117', '2022-09'),
    KernelSupportEntry('android12', '5.10', '136', '2022-10'),
    KernelSupportEntry('android12', '5.10', '136', '2022-11'),
    KernelSupportEntry('android12', '5.10', '149', '2022-12'),
    KernelSupportEntry('android12', '5.10', '149', '2023-01'),
    KernelSupportEntry('android12', '5.10', '160', '2023-02'),
    KernelSupportEntry('android12', '5.10', '160', '2023-03'),
    KernelSupportEntry('android12', '5.10', '168', '2023-04'),
    KernelSupportEntry('android12', '5.10', '168', '2023-05'),
    KernelSupportEntry('android12', '5.10', '177', '2023-06'),
    KernelSupportEntry('android12', '5.10', '177', '2023-07'),
    KernelSupportEntry('android12', '5.10', '185', '2023-09'),
    KernelSupportEntry('android12', '5.10', '198', '2023-11'),
    KernelSupportEntry('android12', '5.10', '198', '2024-01'),
    KernelSupportEntry('android12', '5.10', '205', '2024-03'),
    KernelSupportEntry('android12', '5.10', '209', '2024-05'),
    KernelSupportEntry('android12', '5.10', '218', '2024-08'),
    KernelSupportEntry('android12', '5.10', '226', '2024-11'),
    KernelSupportEntry('android12', '5.10', '233', '2025-02'),
    KernelSupportEntry('android12', '5.10', '236', '2025-05'),
    KernelSupportEntry('android12', '5.10', '237', '2025-06'),
    KernelSupportEntry('android12', '5.10', '240', '2025-09'),
    KernelSupportEntry('android12', '5.10', '246', '2025-12'),
    KernelSupportEntry('android13', '5.15', '41', '2022-06'),
    KernelSupportEntry('android13', '5.15', '41', '2022-07'),
    KernelSupportEntry('android13', '5.15', '41', '2022-08'),
    KernelSupportEntry('android13', '5.15', '41', '2022-09'),
    KernelSupportEntry('android13', '5.15', '41', '2022-10'),
    KernelSupportEntry('android13', '5.15', '41', '2022-11'),
    KernelSupportEntry('android13', '5.15', '74', '2022-12'),
    KernelSupportEntry('android13', '5.15', '74', '2023-01'),
    KernelSupportEntry('android13', '5.15', '78', '2023-02'),
    KernelSupportEntry('android13', '5.15', '78', '2023-03'),
    KernelSupportEntry('android13', '5.15', '94', '2023-04'),
    KernelSupportEntry('android13', '5.15', '94', '2023-05'),
    KernelSupportEntry('android13', '5.15', '104', '2023-06'),
    KernelSupportEntry('android13', '5.15', '104', '2023-07'),
    KernelSupportEntry('android13', '5.15', '119', '2023-08'),
    KernelSupportEntry('android13', '5.15', '119', '2023-09'),
    KernelSupportEntry('android13', '5.15', '123', '2023-10'),
    KernelSupportEntry('android13', '5.15', '123', '2023-11'),
    KernelSupportEntry('android13', '5.15', '137', '2023-12'),
    KernelSupportEntry('android13', '5.15', '137', '2024-01'),
    KernelSupportEntry('android13', '5.15', '144', '2024-02'),
    KernelSupportEntry('android13', '5.15', '144', '2024-03'),
    KernelSupportEntry('android13', '5.15', '148', '2024-04'),
    KernelSupportEntry('android13', '5.15', '148', '2024-05'),
    KernelSupportEntry('android13', '5.15', '149', '2024-06'),
    KernelSupportEntry('android13', '5.15', '149', '2024-07'),
    KernelSupportEntry('android13', '5.15', '151', '2024-08'),
    KernelSupportEntry('android13', '5.15', '153', '2024-09'),
    KernelSupportEntry('android13', '5.15', '167', '2024-11'),
    KernelSupportEntry('android13', '5.15', '170', '2025-01'),
    KernelSupportEntry('android13', '5.15', '178', '2025-03'),
    KernelSupportEntry('android13', '5.15', '180', '2025-05'),
    KernelSupportEntry('android13', '5.15', '185', '2025-07'),
    KernelSupportEntry('android13', '5.15', '189', '2025-09'),
    KernelSupportEntry('android13', '5.15', '194', '2025-12'),
    KernelSupportEntry('android14', '6.1', '25', '2023-06'),
    KernelSupportEntry('android14', '6.1', '25', '2023-07'),
    KernelSupportEntry('android14', '6.1', '25', '2023-08'),
    KernelSupportEntry('android14', '6.1', '25', '2023-09'),
    KernelSupportEntry('android14', '6.1', '25', '2023-10'),
    KernelSupportEntry('android14', '6.1', '43', '2023-11'),
    KernelSupportEntry('android14', '6.1', '57', '2023-12'),
    KernelSupportEntry('android14', '6.1', '57', '2024-01'),
    KernelSupportEntry('android14', '6.1', '68', '2024-02'),
    KernelSupportEntry('android14', '6.1', '68', '2024-03'),
    KernelSupportEntry('android14', '6.1', '75', '2024-04'),
    KernelSupportEntry('android14', '6.1', '75', '2024-05'),
    KernelSupportEntry('android14', '6.1', '78', '2024-06'),
    KernelSupportEntry('android14', '6.1', '84', '2024-07'),
    KernelSupportEntry('android14', '6.1', '90', '2024-08'),
    KernelSupportEntry('android14', '6.1', '93', '2024-09'),
    KernelSupportEntry('android14', '6.1', '99', '2024-10'),
    KernelSupportEntry('android14', '6.1', '112', '2024-11'),
    KernelSupportEntry('android14', '6.1', '115', '2024-12'),
    KernelSupportEntry('android14', '6.1', '118', '2025-01'),
    KernelSupportEntry('android14', '6.1', '124', '2025-02'),
    KernelSupportEntry('android14', '6.1', '128', '2025-03'),
    KernelSupportEntry('android14', '6.1', '129', '2025-04'),
    KernelSupportEntry('android14', '6.1', '134', '2025-05'),
    KernelSupportEntry('android14', '6.1', '138', '2025-06'),
    KernelSupportEntry('android14', '6.1', '141', '2025-07'),
    KernelSupportEntry('android14', '6.1', '145', '2025-08'),
    KernelSupportEntry('android14', '6.1', '145', '2025-09'),
    KernelSupportEntry('android14', '6.1', '157', '2025-12'),
    KernelSupportEntry('android14', '6.1', '162', '2026-03'),
    KernelSupportEntry('android15', '6.6', '50', '2024-10'),
    KernelSupportEntry('android15', '6.6', '56', '2024-11'),
    KernelSupportEntry('android15', '6.6', '57', '2024-12'),
    KernelSupportEntry('android15', '6.6', '58', '2025-01'),
    KernelSupportEntry('android15', '6.6', '66', '2025-02'),
    KernelSupportEntry('android15', '6.6', '77', '2025-03'),
    KernelSupportEntry('android15', '6.6', '82', '2025-04'),
    KernelSupportEntry('android15', '6.6', '87', '2025-05'),
    KernelSupportEntry('android15', '6.6', '89', '2025-06'),
    KernelSupportEntry('android15', '6.6', '92', '2025-07'),
    KernelSupportEntry('android15', '6.6', '98', '2025-08'),
    KernelSupportEntry('android15', '6.6', '98', '2025-09'),
    KernelSupportEntry('android15', '6.6', '102', '2025-10'),
    KernelSupportEntry('android15', '6.6', '118', '2026-01'),
    KernelSupportEntry('android15', '6.6', '127', '2026-04'),
    KernelSupportEntry('android16', '6.12', '23', '2025-06'),
    KernelSupportEntry('android16', '6.12', '30', '2025-07'),
    KernelSupportEntry('android16', '6.12', '38', '2025-08'),
    KernelSupportEntry('android16', '6.12', '38', '2025-09'),
    KernelSupportEntry('android16', '6.12', '58', '2025-12'),
    KernelSupportEntry('android16', '6.12', '69', '2026-03'),
  ];

  static const List<String> ksuVariantOptions = <String>[
    'Official',
    'SukiSU',
    'ReSukiSU',
    'None',
  ];

  static const List<String> ksuBranchOptions = <String>[
    'Stable',
    'Dev',
    'Custom',
  ];

  static List<String> androidVersions() =>
      lines.map((line) => line.androidVersion).toList(growable: false);

  static List<String> kernelVersions() =>
      lines.map((line) => line.kernelVersion).toList(growable: false);

  static String kernelForAndroid(String androidVersion) {
    return lines
        .firstWhere(
          (line) => line.androidVersion == androidVersion,
          orElse: () => lines.first,
        )
        .kernelVersion;
  }

  static String androidForKernel(String kernelVersion) {
    return lines
        .firstWhere(
          (line) => line.kernelVersion == kernelVersion,
          orElse: () => lines.first,
        )
        .androidVersion;
  }

  static List<String> subLevelOptions(
    String androidVersion,
    String kernelVersion,
  ) {
    final values =
        entries
            .where(
              (entry) =>
                  entry.androidVersion ==
                      lineFor(androidVersion, kernelVersion).androidVersion &&
                  entry.kernelVersion ==
                      lineFor(androidVersion, kernelVersion).kernelVersion,
            )
            .map((entry) => entry.subLevel)
            .toSet()
            .toList()
          ..sort(_numericCompare);
    return <String>[...values, 'X'];
  }

  static List<String> patchLevelOptions(
    String androidVersion,
    String kernelVersion,
    String subLevel,
  ) {
    final line = lineFor(androidVersion, kernelVersion);
    final values =
        entries
            .where(
              (entry) =>
                  entry.androidVersion == line.androidVersion &&
                  entry.kernelVersion == line.kernelVersion &&
                  (subLevel == 'X' || entry.subLevel == subLevel),
            )
            .map((entry) => entry.osPatchLevel)
            .toSet()
            .toList()
          ..sort();
    return values;
  }

  static List<String> virtualizationSupportOptions(String kernelVersion) {
    return kernelVersion == '6.12'
        ? const <String>['off', 'on']
        : const <String>['off', '678', '123', '345'];
  }

  static KernelSupportEntry? entryForPatchLevel(
    String androidVersion,
    String kernelVersion,
    String osPatchLevel,
  ) {
    final line = lineFor(androidVersion, kernelVersion);
    final matches = entries
        .where(
          (entry) =>
              entry.androidVersion == line.androidVersion &&
              entry.kernelVersion == line.kernelVersion &&
              entry.osPatchLevel == osPatchLevel,
        )
        .toList(growable: false);
    if (matches.isEmpty) {
      return null;
    }
    final sorted = matches.toList(growable: true)
      ..sort((left, right) => _numericCompare(left.subLevel, right.subLevel));
    return sorted.last;
  }

  static bool isKpmSupported({
    required String ksuVariant,
    required String ksuBranch,
  }) {
    final normalizedVariant = normalizeKsuVariant(ksuVariant);
    final normalizedBranch = normalizeKsuBranch(ksuBranch);
    if (normalizedVariant == 'None') return false;
    if (normalizedVariant == 'Official') return false;
    if (normalizedVariant == 'ReSukiSU' &&
        !const <String>{'Stable', 'Custom'}.contains(normalizedBranch)) {
      return false;
    }
    return true;
  }

  static String normalizeKsuVariant(String value) {
    return ksuVariantOptions.contains(value) ? value : 'ReSukiSU';
  }

  static String normalizeKsuBranch(String value) {
    return ksuBranchOptions.contains(value) ? value : 'Stable';
  }

  static String normalizeVirtualizationSupport(
    String kernelVersion,
    String value,
  ) {
    final normalized = value.trim().toLowerCase();
    if (virtualizationSupportOptions(kernelVersion).contains(normalized)) {
      return normalized;
    }
    if (kernelVersion == '6.12' &&
        const <String>{'678', '123', '345'}.contains(normalized)) {
      return 'on';
    }
    return 'off';
  }

  static KernelVersionLine lineFor(
    String androidVersion,
    String kernelVersion,
  ) {
    return lines.firstWhere(
      (line) =>
          line.androidVersion == androidVersion &&
          line.kernelVersion == kernelVersion,
      orElse: () {
        return lines.firstWhere(
          (line) => line.androidVersion == androidVersion,
          orElse: () {
            return lines.firstWhere(
              (line) => line.kernelVersion == kernelVersion,
              orElse: () => lines.first,
            );
          },
        );
      },
    );
  }

  static KernelSupportEntry latestEntry(
    String androidVersion,
    String kernelVersion,
  ) {
    final line = lineFor(androidVersion, kernelVersion);
    final matches = entries
        .where(
          (entry) =>
              entry.androidVersion == line.androidVersion &&
              entry.kernelVersion == line.kernelVersion,
        )
        .toList(growable: false);
    matches.sort((left, right) {
      final sub = _numericCompare(left.subLevel, right.subLevel);
      if (sub != 0) return sub;
      return left.osPatchLevel.compareTo(right.osPatchLevel);
    });
    return matches.isEmpty ? entries.first : matches.last;
  }
}

int _numericCompare(String left, String right) {
  final leftNum = int.tryParse(left) ?? -1;
  final rightNum = int.tryParse(right) ?? -1;
  return leftNum.compareTo(rightNum);
}
