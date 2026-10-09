import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

const officialBuildModuleCatalogUrl =
    'https://github.com/xingguangcuican6666/ABK_repo';
const _defaultCustomModuleStage = 'after_patch';
const _moduleKind = 'module';
const _moduleSetKind = 'module_set';
const _moduleSetChildKind = 'module_set_child';

class BuildModuleCatalogItem {
  const BuildModuleCatalogItem({
    required this.name,
    required this.version,
    required this.description,
    required this.kind,
    required this.moduleSetId,
    required this.repoUrl,
    required this.defaultStage,
    required this.supportedStages,
    required this.recommendedStages,
  });

  final String name;
  final String version;
  final String description;
  final String kind;
  final String moduleSetId;
  final String repoUrl;
  final String defaultStage;
  final List<String> supportedStages;
  final List<String> recommendedStages;

  bool get isModuleSet => kind == _moduleSetKind;

  factory BuildModuleCatalogItem.fromJson(Map<String, dynamic> json) {
    final repoUrl = normalizeRepositoryUrl(_readString(json['repoUrl']));
    final kind = normalizeCatalogKind(
      _readString(json['kind']).ifEmpty(_readString(json['type'])),
    );
    final supportedStages = _readStringOrCsvList(
      json['supportedStages'],
    ).map(normalizeCustomModuleStage).toList(growable: false);
    final effectiveSupported = supportedStages.isEmpty
        ? const <String>[_defaultCustomModuleStage]
        : supportedStages;
    final defaultStage = normalizeCustomModuleStage(
      _readString(
        json['defaultStage'],
        fallback: _readString(
          json['recommendedStage'],
          fallback: effectiveSupported.first,
        ),
      ),
    );
    final recommendedStages = _readStringOrCsvList(json['recommendedStages'])
        .followedBy(_readStringOrCsvList(json['recommend']))
        .followedBy(_readStringOrCsvList(json['recommendedStage']))
        .followedBy(_readStringOrCsvList(json['recommendStage']))
        .map(normalizeCustomModuleStage)
        .where(effectiveSupported.contains)
        .toSet()
        .toList(growable: false);

    return BuildModuleCatalogItem(
      name: _readString(json['name']).trim().isEmpty
          ? repoNameFromUrl(repoUrl)
          : _readString(json['name']).trim(),
      version: _readString(json['version']).trim(),
      description: _readString(json['description']).trim(),
      kind: kind,
      moduleSetId: _readString(json['moduleSetId'])
          .ifEmpty(_readString(json['module_set_id']))
          .trim()
          .ifEmpty(kind == _moduleSetKind ? repoNameFromUrl(repoUrl) : ''),
      repoUrl: repoUrl,
      defaultStage: effectiveSupported.contains(defaultStage)
          ? defaultStage
          : effectiveSupported.first,
      supportedStages: effectiveSupported,
      recommendedStages: recommendedStages.isEmpty
          ? <String>[effectiveSupported.first]
          : recommendedStages,
    );
  }
}

class BuildModuleRepository {
  const BuildModuleRepository({
    required this.url,
    required this.name,
    required this.modules,
    required this.error,
    required this.indexUrl,
  });

  final String url;
  final String name;
  final List<BuildModuleCatalogItem> modules;
  final String? error;
  final String? indexUrl;

  bool get isReady => error == null;
}

class BuildModuleSetChildMetadata {
  const BuildModuleSetChildMetadata({
    required this.id,
    required this.name,
    required this.description,
    required this.repoUrl,
    required this.supportedStages,
    required this.defaultStage,
    required this.recommendedStages,
    required this.groupRole,
    required this.controllable,
    required this.hasWebUi,
    required this.magiskModuleName,
    required this.magiskModuleDownloadUrl,
  });

  final String id;
  final String name;
  final String description;
  final String repoUrl;
  final List<String> supportedStages;
  final String defaultStage;
  final List<String> recommendedStages;
  final String groupRole;
  final bool controllable;
  final bool hasWebUi;
  final String magiskModuleName;
  final String magiskModuleDownloadUrl;
}

class BuildExternalModuleMetadata {
  const BuildExternalModuleMetadata({
    required this.name,
    required this.version,
    required this.description,
    required this.kind,
    required this.moduleSetId,
    required this.supportedStages,
    required this.defaultStage,
    required this.recommendedStages,
    required this.children,
    required this.magiskModuleName,
    required this.magiskModuleDownloadUrl,
  });

  final String name;
  final String version;
  final String description;
  final String kind;
  final String moduleSetId;
  final List<String> supportedStages;
  final String defaultStage;
  final List<String> recommendedStages;
  final List<BuildModuleSetChildMetadata> children;
  final String magiskModuleName;
  final String magiskModuleDownloadUrl;

  bool get isModuleSet => kind == _moduleSetKind;
}

class SelectedBuildModule {
  const SelectedBuildModule({
    required this.label,
    required this.repoUrl,
    required this.stage,
    required this.workflowEntry,
    required this.fromCatalog,
    this.kind = _moduleKind,
    this.groupRepoUrl,
    this.childId,
    this.childName,
    this.groupName,
  });

  final String label;
  final String repoUrl;
  final String stage;
  final String workflowEntry;
  final bool fromCatalog;
  final String kind;
  final String? groupRepoUrl;
  final String? childId;
  final String? childName;
  final String? groupName;

  bool get isModuleSetChild => kind == _moduleSetChildKind;

  bool matchesModuleSetGroup(String rawGroupRepoUrl) {
    if (!isModuleSetChild) return false;
    return normalizeRepositoryUrl(groupRepoUrl ?? repoUrl).toLowerCase() ==
        normalizeRepositoryUrl(rawGroupRepoUrl).toLowerCase();
  }
}

class BuildModuleCatalogClient {
  BuildModuleCatalogClient({
    http.Client? client,
    this.requestTimeout = const Duration(seconds: 12),
  }) : _client = client ?? http.Client();

  final http.Client _client;
  final Duration requestTimeout;

  Future<BuildModuleRepository> fetchRepository(String repositoryUrl) async {
    final cleanUrl = normalizeRepositoryUrl(repositoryUrl);
    if (cleanUrl.isEmpty) {
      return const BuildModuleRepository(
        url: '',
        name: '',
        modules: <BuildModuleCatalogItem>[],
        error: 'empty repository url',
        indexUrl: null,
      );
    }

    var lastError = 'catalog unreadable';
    for (final candidate in catalogIndexCandidates(cleanUrl)) {
      try {
        final response = await _getCandidate(
          candidate,
          headers: const <String, String>{
            'accept': 'application/json,text/plain,*/*',
          },
        );
        if (response.statusCode < 200 || response.statusCode >= 300) {
          lastError = 'HTTP ${response.statusCode}';
          continue;
        }

        final parsed = parseBuildModuleCatalogDocument(response.body, cleanUrl);
        return BuildModuleRepository(
          url: cleanUrl,
          name: parsed.name,
          modules: parsed.modules,
          error: null,
          indexUrl: candidate,
        );
      } on TimeoutException {
        lastError = 'request timed out';
      } catch (error) {
        lastError = error.toString();
      }
    }

    return BuildModuleRepository(
      url: cleanUrl,
      name: repoNameFromUrl(cleanUrl),
      modules: const <BuildModuleCatalogItem>[],
      error: lastError,
      indexUrl: null,
    );
  }

  Future<BuildExternalModuleMetadata> fetchModuleMetadata(
    String repositoryUrl,
  ) async {
    final cleanUrl = normalizeRepositoryUrl(repositoryUrl);
    if (cleanUrl.isEmpty) {
      throw const FormatException('empty repository url');
    }

    var lastError = 'module.conf unreadable';
    for (final candidate in externalModuleConfCandidates(cleanUrl)) {
      try {
        final response = await _getCandidate(
          candidate,
          headers: const <String, String>{'accept': 'text/plain,*/*'},
        );
        if (response.statusCode < 200 || response.statusCode >= 300) {
          lastError = 'HTTP ${response.statusCode}';
          continue;
        }
        return parseExternalModuleConf(response.body);
      } on TimeoutException {
        lastError = 'request timed out';
      } catch (error) {
        lastError = error.toString();
      }
    }

    throw FormatException(lastError);
  }

  void close() {
    _client.close();
  }

  Future<http.Response> _getCandidate(
    String candidate, {
    required Map<String, String> headers,
  }) {
    return _client
        .get(Uri.parse(candidate), headers: headers)
        .timeout(requestTimeout);
  }
}

class ParsedBuildModuleCatalog {
  const ParsedBuildModuleCatalog({required this.name, required this.modules});

  final String name;
  final List<BuildModuleCatalogItem> modules;
}

ParsedBuildModuleCatalog parseBuildModuleCatalogDocument(
  String body,
  String repositoryUrl,
) {
  final root = jsonDecode(body);
  if (root is! Map) {
    throw const FormatException('module catalog root must be an object');
  }
  final document = Map<String, dynamic>.from(root);
  final rawModules = document['modules'];
  final modules = rawModules is List
      ? rawModules
            .whereType<Map>()
            .map(
              (item) => BuildModuleCatalogItem.fromJson(
                Map<String, dynamic>.from(item),
              ),
            )
            .where((item) => item.repoUrl.isNotEmpty)
            .toList(growable: false)
      : const <BuildModuleCatalogItem>[];

  return ParsedBuildModuleCatalog(
    name: _readString(document['name']).trim().isEmpty
        ? repoNameFromUrl(repositoryUrl)
        : _readString(document['name']).trim(),
    modules: modules,
  );
}

BuildExternalModuleMetadata parseExternalModuleConf(String body) {
  final values = parseShellLikeConf(body);
  final kind = normalizeCatalogKind(values['ABK_MODULE_KIND'] ?? '');
  final name =
      (kind == _moduleSetKind
              ? values['ABK_MODULE_SET_NAME']
              : values['ABK_MODULE_NAME'])
          .orEmpty
          .trim();
  if (name.isEmpty) {
    throw const FormatException('missing module name');
  }

  final supportedStages = (values['ABK_MODULE_SUPPORTED_STAGES'] ?? '')
      .split(',')
      .map((value) => value.trim())
      .where((value) => value.isNotEmpty)
      .map(normalizeCustomModuleStage)
      .toSet()
      .toList(growable: false)
      .ifEmpty(const <String>[_defaultCustomModuleStage]);
  final defaultStage = normalizeCustomModuleStage(
    values['ABK_MODULE_DEFAULT_STAGE'] ??
        values['ABK_MODULE_RECOMMENDED_STAGE'] ??
        values['ABK_MODULE_STAGE'] ??
        '',
  );
  final recommendedStages =
      (values['ABK_MODULE_RECOMMENDED_STAGES'] ??
              values['ABK_MODULE_RECOMMEND_STAGES'] ??
              values['ABK_MODULE_RECOMMENDED_STAGE'] ??
              values['ABK_MODULE_RECOMMEND_STAGE'] ??
              values['ABK_MODULE_RECOMMEND'] ??
              values['ABK_MODULE_DEFAULT_STAGE'] ??
              values['ABK_MODULE_STAGE'] ??
              '')
          .split(',')
          .map((value) => value.trim())
          .where((value) => value.isNotEmpty)
          .map(normalizeCustomModuleStage)
          .where(supportedStages.contains)
          .toSet()
          .toList(growable: false)
          .ifEmpty(<String>[
            supportedStages.contains(defaultStage)
                ? defaultStage
                : supportedStages.first,
          ]);
  final children = kind == _moduleSetKind
      ? parseModuleSetChildren(values['ABK_MODULE_SET_ITEMS'] ?? '')
      : const <BuildModuleSetChildMetadata>[];
  if (kind == _moduleSetKind && children.isEmpty) {
    throw const FormatException('module_set has no children');
  }

  return BuildExternalModuleMetadata(
    name: name,
    version:
        (kind == _moduleSetKind
                ? values['ABK_MODULE_SET_VERSION']
                : values['ABK_MODULE_VERSION'])
            .orEmpty
            .trim(),
    description:
        (kind == _moduleSetKind
                ? values['ABK_MODULE_SET_DESCRIPTION']
                : values['ABK_MODULE_DESCRIPTION'])
            .orEmpty
            .trim(),
    kind: kind,
    moduleSetId: (values['ABK_MODULE_SET_ID'] ?? '').trim(),
    supportedStages: supportedStages,
    defaultStage: supportedStages.contains(defaultStage)
        ? defaultStage
        : supportedStages.first,
    recommendedStages: recommendedStages,
    children: children,
    magiskModuleName: (values['ABK_MAGISK_MODULE_NAME'] ?? '').trim(),
    magiskModuleDownloadUrl: (values['ABK_MAGISK_MODULE_DOWNLOAD_URL'] ?? '')
        .trim(),
  );
}

List<BuildModuleSetChildMetadata> parseModuleSetChildren(String raw) {
  final children = <BuildModuleSetChildMetadata>[];
  for (final line in raw.split('\n')) {
    final clean = line.trim();
    if (clean.isEmpty || clean.startsWith('#')) {
      continue;
    }
    final parts = clean.split('|');
    if (parts.length < 6) {
      continue;
    }
    final id = parts[0].trim();
    final name = parts[1].trim();
    final repoUrl = normalizeRepositoryUrl(parts[3]);
    if (id.isEmpty || name.isEmpty || repoUrl.isEmpty) {
      continue;
    }
    final supportedStages = parts[4]
        .split(',')
        .map((value) => value.trim())
        .where((value) => value.isNotEmpty)
        .map(normalizeCustomModuleStage)
        .toSet()
        .toList(growable: false)
        .ifEmpty(const <String>[_defaultCustomModuleStage]);
    final defaultStage = normalizeCustomModuleStage(parts[5]);
    final recommendedStages = (parts.length > 6 ? parts[6] : '')
        .split(',')
        .map((value) => value.trim())
        .where((value) => value.isNotEmpty)
        .map(normalizeCustomModuleStage)
        .where(supportedStages.contains)
        .toSet()
        .toList(growable: false)
        .ifEmpty(<String>[
          supportedStages.contains(defaultStage)
              ? defaultStage
              : supportedStages.first,
        ]);
    if (children.any(
      (existing) => existing.id.toLowerCase() == id.toLowerCase(),
    )) {
      continue;
    }
    children.add(
      BuildModuleSetChildMetadata(
        id: id,
        name: name,
        description: parts[2].trim(),
        repoUrl: repoUrl,
        supportedStages: supportedStages,
        defaultStage: supportedStages.contains(defaultStage)
            ? defaultStage
            : supportedStages.first,
        recommendedStages: recommendedStages,
        groupRole: parts.length > 7 ? parts[7].trim() : '',
        controllable: parts.length > 8 ? _isTruthy(parts[8]) : false,
        hasWebUi: parts.length > 9 ? _isTruthy(parts[9]) : false,
        magiskModuleName: parts.length > 10 ? parts[10].trim() : '',
        magiskModuleDownloadUrl: parts.length > 11 ? parts[11].trim() : '',
      ),
    );
  }
  return children;
}

Map<String, String> parseShellLikeConf(String body) {
  final result = <String, String>{};
  final lines = body.split('\n');
  var index = 0;
  while (index < lines.length) {
    final clean = lines[index].split('#').first.trim();
    if (clean.isEmpty || !clean.contains('=')) {
      index += 1;
      continue;
    }
    final key = clean.substring(0, clean.indexOf('=')).trim();
    var value = clean.substring(clean.indexOf('=') + 1).trim();
    if (key.isEmpty) {
      index += 1;
      continue;
    }
    if ((value == '"' || value == '\'') && index + 1 < lines.length) {
      final quote = value;
      final collected = <String>[];
      index += 1;
      while (index < lines.length) {
        final rawLine = lines[index];
        if (rawLine.trim() == quote) {
          break;
        }
        collected.add(rawLine);
        index += 1;
      }
      value = collected.join('\n');
    } else {
      value = _trimShellQuotes(value);
    }
    result[key] = value;
    index += 1;
  }
  return result;
}

List<String> catalogIndexCandidates(String repositoryUrl) {
  final clean = normalizeRepositoryUrl(repositoryUrl);
  if (clean.isEmpty) return const <String>[];
  if (clean.endsWith('.json')) return <String>[clean];
  if (clean.startsWith('https://raw.githubusercontent.com/')) {
    return <String>['$clean/abk-modules.json'];
  }
  final repo = parseGitHubRepository(clean);
  if (repo == null) return const <String>[];
  final branches = repo.branch == null
      ? const <String>['main', 'master']
      : <String>[repo.branch!];
  return branches
      .map(
        (branch) =>
            'https://raw.githubusercontent.com/${repo.owner}/${repo.repo}/$branch/abk-modules.json',
      )
      .toList(growable: false);
}

List<String> externalModuleConfCandidates(String repositoryUrl) {
  final clean = normalizeRepositoryUrl(repositoryUrl);
  if (clean.isEmpty) return const <String>[];
  if (clean.endsWith('/module.conf')) return <String>[clean];
  if (clean.startsWith('https://raw.githubusercontent.com/')) {
    return <String>['$clean/module.conf'];
  }
  final repo = parseGitHubRepository(clean);
  if (repo == null) return const <String>[];
  final branches = repo.branch == null
      ? const <String>['main', 'master']
      : <String>[repo.branch!];
  return branches
      .map(
        (branch) =>
            'https://raw.githubusercontent.com/${repo.owner}/${repo.repo}/$branch/module.conf',
      )
      .toList(growable: false);
}

String normalizeRepositoryUrl(String url) =>
    url.trim().trimRight().replaceAll(RegExp(r'/+$'), '');

String normalizeCatalogKind(String kind) {
  return switch (kind.trim().toLowerCase()) {
    'module_set' || 'module-set' || 'set' || 'moduleset' => _moduleSetKind,
    _ => _moduleKind,
  };
}

String normalizeCustomModuleStage(String value) {
  final normalized = value
      .trim()
      .toLowerCase()
      .replaceAll('-', '_')
      .replaceAll(' ', '_');
  return switch (normalized) {
    'before_build' || 'befor_build' => 'before_build',
    _ => _defaultCustomModuleStage,
  };
}

SelectedBuildModule selectedBuildModuleFromCatalog(
  BuildModuleCatalogItem item, {
  String? stage,
}) {
  final effectiveStage = normalizeCustomModuleStage(
    stage ?? item.recommendedStages.firstOrNull ?? item.defaultStage,
  );
  return SelectedBuildModule(
    label: item.name,
    repoUrl: item.repoUrl,
    stage: effectiveStage,
    workflowEntry: 'module:${item.repoUrl};$effectiveStage',
    fromCatalog: true,
    kind: _moduleKind,
  );
}

SelectedBuildModule selectedBuildModuleFromManualUrl(
  String url, {
  required String stage,
}) {
  final cleanUrl = normalizeRepositoryUrl(url);
  final effectiveStage = normalizeCustomModuleStage(stage);
  return SelectedBuildModule(
    label: repoNameFromUrl(cleanUrl),
    repoUrl: cleanUrl,
    stage: effectiveStage,
    workflowEntry: 'module:$cleanUrl;$effectiveStage',
    fromCatalog: false,
    kind: _moduleKind,
  );
}

List<SelectedBuildModule> selectedBuildModulesFromModuleSet({
  required String groupRepoUrl,
  required BuildExternalModuleMetadata metadata,
  required BuildModuleSetChildMetadata child,
  required Iterable<String> stages,
  required bool fromCatalog,
}) {
  final cleanGroupRepoUrl = normalizeRepositoryUrl(groupRepoUrl);
  if (cleanGroupRepoUrl.isEmpty || child.id.trim().isEmpty) {
    return const <SelectedBuildModule>[];
  }
  final normalizedStages = stages
      .map(normalizeCustomModuleStage)
      .where(child.supportedStages.contains)
      .toSet()
      .toList(growable: false)
      .ifEmpty(
        child.recommendedStages
            .where(child.supportedStages.contains)
            .toList(growable: false)
            .ifEmpty(<String>[child.defaultStage]),
      );
  return normalizedStages
      .map(
        (stage) => SelectedBuildModule(
          label: child.name,
          repoUrl: normalizeRepositoryUrl(child.repoUrl),
          stage: stage,
          workflowEntry: 'set:$cleanGroupRepoUrl#${child.id};$stage',
          fromCatalog: fromCatalog,
          kind: _moduleSetChildKind,
          groupRepoUrl: cleanGroupRepoUrl,
          childId: child.id,
          childName: child.name,
          groupName: metadata.name,
        ),
      )
      .toList(growable: false);
}

List<SelectedBuildModule> parseSelectedBuildModules(String value) {
  if (value.trim().isEmpty) {
    return const <SelectedBuildModule>[];
  }
  return value
      .split('|')
      .map((entry) => entry.trim())
      .where((entry) => entry.isNotEmpty)
      .map((entry) {
        final left = entry.split(';');
        final rawModule = left.firstOrNull ?? entry;
        final stage = left.length > 1
            ? normalizeCustomModuleStage(left[1])
            : _defaultCustomModuleStage;
        if (rawModule.startsWith('set:')) {
          final rawSet = rawModule.substring(4);
          final groupRepoUrl = rawSet.contains('#')
              ? rawSet.substring(0, rawSet.indexOf('#'))
              : rawSet;
          final childId = rawSet.contains('#')
              ? rawSet.substring(rawSet.indexOf('#') + 1)
              : '';
          final cleanGroupRepoUrl = normalizeRepositoryUrl(groupRepoUrl);
          final fallbackGroupName = repoNameFromUrl(cleanGroupRepoUrl);
          return SelectedBuildModule(
            label: childId.ifEmpty(fallbackGroupName),
            repoUrl: cleanGroupRepoUrl,
            stage: stage,
            workflowEntry: entry,
            fromCatalog: true,
            kind: _moduleSetChildKind,
            groupRepoUrl: cleanGroupRepoUrl,
            childId: childId.isEmpty ? null : childId,
            childName: childId.isEmpty ? null : childId,
            groupName: fallbackGroupName,
          );
        }
        final rawUrl = rawModule.contains(':')
            ? rawModule.substring(rawModule.indexOf(':') + 1)
            : rawModule;
        final repoUrl = normalizeRepositoryUrl(rawUrl);
        return SelectedBuildModule(
          label: repoNameFromUrl(repoUrl),
          repoUrl: repoUrl,
          stage: stage,
          workflowEntry: entry,
          fromCatalog: entry.startsWith('module:'),
          kind: _moduleKind,
        );
      })
      .toList(growable: false);
}

String buildSelectedModulesWorkflowValue(List<SelectedBuildModule> modules) {
  return modules
      .map((module) => module.workflowEntry)
      .where((entry) => entry.trim().isNotEmpty)
      .join('|');
}

GitHubRepositoryParts? parseGitHubRepository(String url) {
  final cleaned = url.trim().trimRight().replaceAll(RegExp(r'/+$'), '');
  final path = switch (true) {
    _ when cleaned.startsWith('git@github.com:') => cleaned.replaceFirst(
      'git@github.com:',
      '',
    ),
    _ when cleaned.startsWith('https://github.com/') => cleaned.replaceFirst(
      'https://github.com/',
      '',
    ),
    _ when cleaned.startsWith('http://github.com/') => cleaned.replaceFirst(
      'http://github.com/',
      '',
    ),
    _ when cleaned.startsWith('github.com/') => cleaned.replaceFirst(
      'github.com/',
      '',
    ),
    _ => null,
  };
  if (path == null) return null;
  final parts = path
      .split('/')
      .where((part) => part.isNotEmpty)
      .toList(growable: false);
  if (parts.length < 2) return null;
  final owner = parts[0];
  final repo = parts[1].replaceAll(RegExp(r'\.git$'), '');
  if (owner.isEmpty || repo.isEmpty) return null;
  final branch = parts.length >= 4 && parts[2] == 'tree'
      ? parts.sublist(3).join('/')
      : null;
  return GitHubRepositoryParts(owner: owner, repo: repo, branch: branch);
}

class GitHubRepositoryParts {
  const GitHubRepositoryParts({
    required this.owner,
    required this.repo,
    required this.branch,
  });

  final String owner;
  final String repo;
  final String? branch;
}

String repoNameFromUrl(String url) {
  final clean = normalizeRepositoryUrl(url);
  final lastPart = clean.split('/').where((part) => part.isNotEmpty).lastOrNull;
  return (lastPart
          ?.replaceAll(RegExp(r'\.git$'), '')
          .replaceAll('-', ' ')
          .trim()
          .ifEmpty('ABK module')) ??
      'ABK module';
}

String _readString(dynamic value, {String fallback = ''}) {
  if (value is String) return value;
  return fallback;
}

List<String> _readStringOrCsvList(dynamic value) {
  if (value is List) {
    return value.whereType<String>().toList(growable: false);
  }
  if (value is String) {
    return value
        .split(',')
        .map((item) => item.trim())
        .where((item) => item.isNotEmpty)
        .toList(growable: false);
  }
  return const <String>[];
}

bool _isTruthy(String value) {
  return switch (value.trim().toLowerCase()) {
    '1' || 'true' || 'yes' || 'on' => true,
    _ => false,
  };
}

String _trimShellQuotes(String value) {
  final clean = value.trim();
  if (clean.length < 2) {
    return clean;
  }
  final first = clean[0];
  final last = clean[clean.length - 1];
  if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
    return clean.substring(1, clean.length - 1);
  }
  return clean;
}

extension<T> on List<T> {
  T? get firstOrNull => isEmpty ? null : first;
}

extension on Iterable<String> {
  String? get lastOrNull => isEmpty ? null : last;

  List<String> ifEmpty(List<String> fallback) =>
      isEmpty ? fallback : toList(growable: false);
}

extension on String {
  String ifEmpty(String fallback) => isEmpty ? fallback : this;
}

extension on String? {
  String get orEmpty => this ?? '';
}
