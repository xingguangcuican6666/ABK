import 'dart:convert';

import 'package:http/http.dart' as http;

const officialRuntimeModuleRepositoryId = 'official-runtime-module-repository';
const officialRuntimeModuleRepositoryUrl =
    'https://raw.githubusercontent.com/Magisk-Modules-Alt-Repo/json-v2/refs/heads/main/json/modules.json';

class RuntimeModuleCatalogItem {
  const RuntimeModuleCatalogItem({
    required this.id,
    required this.name,
    required this.version,
    required this.versionCode,
    required this.author,
    required this.description,
    required this.zipUrl,
    required this.changelog,
    required this.support,
    required this.donate,
    required this.website,
    required this.cover,
    required this.icon,
    required this.verified,
    required this.minApi,
    required this.maxApi,
  });

  final String id;
  final String name;
  final String version;
  final int versionCode;
  final String author;
  final String description;
  final String zipUrl;
  final String changelog;
  final String support;
  final String donate;
  final String website;
  final String cover;
  final String icon;
  final bool verified;
  final int? minApi;
  final int? maxApi;

  String metaLine() {
    final parts = <String>[
      if (version.isNotEmpty) 'v$version',
      if (author.isNotEmpty) author,
    ];
    return parts.join(' · ');
  }

  factory RuntimeModuleCatalogItem.fromJson(Map<String, dynamic> json) {
    return RuntimeModuleCatalogItem(
      id: _readString(json['id']),
      name: _readString(json['name']),
      version: _readString(json['version']),
      versionCode: _readInt(json['versionCode']),
      author: _readString(json['author']),
      description: _readString(json['description']),
      zipUrl: _readString(json['zipUrl']),
      changelog: _readString(json['changelog']),
      support: _readString(json['support']),
      donate: _readString(json['donate']),
      website: _readString(json['website']),
      cover: _readString(json['cover']),
      icon: _readString(json['icon']),
      verified: json['verified'] == true,
      minApi: _nullableInt(json['minApi']),
      maxApi: _nullableInt(json['maxApi']),
    );
  }
}

class RuntimeModuleRepository {
  const RuntimeModuleRepository({
    required this.id,
    required this.url,
    required this.indexJsonUrl,
    required this.name,
    required this.modules,
    required this.lastUpdated,
    required this.error,
    required this.skippedCount,
  });

  final String id;
  final String url;
  final String indexJsonUrl;
  final String name;
  final List<RuntimeModuleCatalogItem> modules;
  final int lastUpdated;
  final String? error;
  final int skippedCount;

  bool get isReady => error == null;
}

class MergedRuntimeCatalogModule {
  const MergedRuntimeCatalogModule({
    required this.module,
    required this.sources,
  });

  final RuntimeModuleCatalogItem module;
  final List<String> sources;

  bool matchesQuery(String query) {
    final needle = query.trim().toLowerCase();
    if (needle.isEmpty) return true;
    final haystack = <String>[
      module.id,
      module.name,
      module.version,
      module.author,
      module.description,
      module.support,
      module.website,
      module.zipUrl,
      ...sources,
    ].join(' ').toLowerCase();
    return haystack.contains(needle);
  }
}

class RuntimeModuleCatalogFetchResult {
  const RuntimeModuleCatalogFetchResult({
    required this.name,
    required this.indexUrl,
    required this.modules,
    required this.skippedCount,
  });

  final String name;
  final String indexUrl;
  final List<RuntimeModuleCatalogItem> modules;
  final int skippedCount;
}

class ParsedRuntimeModuleCatalogDocument {
  const ParsedRuntimeModuleCatalogDocument({
    required this.name,
    required this.modules,
    required this.skippedCount,
  });

  final String name;
  final List<RuntimeModuleCatalogItem> modules;
  final int skippedCount;
}

class RuntimeModuleCatalogClient {
  RuntimeModuleCatalogClient({http.Client? client})
    : _client = client ?? http.Client();

  final http.Client _client;

  Future<RuntimeModuleRepository> fetchRepository(
    RuntimeModuleRepository repository,
  ) async {
    final cleanUrl = normalizeModuleCatalogUrl(repository.url);
    if (cleanUrl.isEmpty) {
      return repository.copyWith(error: 'repository url is empty');
    }

    var lastError = 'catalog unreadable';
    for (final candidate in runtimeModuleCatalogCandidates(cleanUrl)) {
      try {
        final response = await _client.get(
          Uri.parse(candidate),
          headers: const <String, String>{
            'accept': 'application/json,text/plain,*/*',
          },
        );
        if (response.statusCode < 200 || response.statusCode >= 300) {
          lastError = 'HTTP ${response.statusCode}';
          continue;
        }
        final parsed = parseRuntimeModuleCatalogDocument(response.body, cleanUrl);
        return repository.copyWith(
          url: cleanUrl,
          indexJsonUrl: candidate,
          name: parsed.name,
          modules: parsed.modules,
          skippedCount: parsed.skippedCount,
          lastUpdated: DateTime.now().millisecondsSinceEpoch,
          error: null,
        );
      } catch (error) {
        lastError = error.toString();
      }
    }

    return repository.copyWith(
      url: cleanUrl,
      name: repository.name.isEmpty
          ? moduleCatalogFallbackName(cleanUrl, 'Standard Module Repo')
          : repository.name,
      error: lastError,
    );
  }

  void close() {
    _client.close();
  }
}

ParsedRuntimeModuleCatalogDocument parseRuntimeModuleCatalogDocument(
  String body,
  String repositoryUrl,
) {
  final root = jsonDecode(body);
  if (root is! Map) {
    throw const FormatException('runtime module catalog root must be an object');
  }
  final document = Map<String, dynamic>.from(root);
  final rawModules = document['modules'];
  final parsed = rawModules is List
      ? rawModules
            .whereType<Map>()
            .map(
              (item) => sanitizeRuntimeModuleCatalogItem(
                RuntimeModuleCatalogItem.fromJson(
                  Map<String, dynamic>.from(item),
                ),
              ),
            )
            .whereType<RuntimeModuleCatalogItem>()
            .toList(growable: false)
      : const <RuntimeModuleCatalogItem>[];

  final deduped = <RuntimeModuleCatalogItem>[];
  final seen = <String>{};
  for (final item in parsed) {
    final key = item.id.trim().toLowerCase().ifEmpty(
      item.name.trim().toLowerCase(),
    );
    if (key.isEmpty || seen.contains(key)) continue;
    seen.add(key);
    deduped.add(item);
  }

  return ParsedRuntimeModuleCatalogDocument(
    name: _readString(document['name']).trim().isEmpty
        ? moduleCatalogFallbackName(repositoryUrl, 'Standard Module Repo')
        : _readString(document['name']).trim(),
    modules: deduped,
    skippedCount: (rawModules is List ? rawModules.length : 0) - deduped.length,
  );
}

RuntimeModuleCatalogItem? sanitizeRuntimeModuleCatalogItem(
  RuntimeModuleCatalogItem item,
) {
  final name = item.name.trim();
  final zipUrl = item.zipUrl.trim();
  if (name.isEmpty || zipUrl.isEmpty) {
    return null;
  }
  return RuntimeModuleCatalogItem(
    id: item.id.trim().ifEmpty(name.toLowerCase().replaceAll(' ', '_')),
    name: name,
    version: item.version.trim(),
    versionCode: item.versionCode,
    author: item.author.trim(),
    description: item.description.trim(),
    zipUrl: zipUrl,
    changelog: item.changelog.trim(),
    support: item.support.trim(),
    donate: item.donate.trim(),
    website: item.website.trim(),
    cover: item.cover.trim(),
    icon: item.icon.trim(),
    verified: item.verified,
    minApi: item.minApi,
    maxApi: item.maxApi,
  );
}

List<MergedRuntimeCatalogModule> mergeRuntimeCatalogModules(
  List<RuntimeModuleRepository> repositories,
) {
  return repositories
      .expand(
        (repository) => repository.modules.map(
          (module) => (repository.name.isEmpty ? repository.url : repository.name, module),
        ),
      )
      .fold<Map<String, List<(String, RuntimeModuleCatalogItem)>>>(
        <String, List<(String, RuntimeModuleCatalogItem)>>{},
        (acc, entry) {
          final source = entry.$1;
          final module = entry.$2;
          final key = module.id.trim().toLowerCase().ifEmpty(
            module.name.trim().toLowerCase(),
          );
          acc.putIfAbsent(key, () => <(String, RuntimeModuleCatalogItem)>[]).add((source, module));
          return acc;
        },
      )
      .values
      .map(
        (entries) => MergedRuntimeCatalogModule(
          module: entries.first.$2,
          sources: entries.map((entry) => entry.$1).toSet().toList(growable: false),
        ),
      )
      .toList(growable: false)
    ..sort((left, right) => left.module.name.toLowerCase().compareTo(right.module.name.toLowerCase()));
}

List<String> runtimeModuleCatalogCandidates(String repositoryUrl) {
  final clean = normalizeModuleCatalogUrl(repositoryUrl);
  if (clean.isEmpty) return const <String>[];
  if (clean.endsWith('.json')) return <String>[clean];
  return const <String>[];
}

String normalizeModuleCatalogUrl(String url) =>
    url.trim().replaceAll(RegExp(r'/+$'), '');

String moduleCatalogFallbackName(String url, String fallback) {
  final clean = normalizeModuleCatalogUrl(url).replaceAll(RegExp(r'/+$'), '');
  final lastPart = clean.split('/').where((part) => part.isNotEmpty).lastOrNull;
  return lastPart?.replaceAll('.json', '').replaceAll('-', ' ').trim().ifEmpty(fallback) ?? fallback;
}

extension on RuntimeModuleRepository {
  RuntimeModuleRepository copyWith({
    String? id,
    String? url,
    String? indexJsonUrl,
    String? name,
    List<RuntimeModuleCatalogItem>? modules,
    int? lastUpdated,
    String? error,
    int? skippedCount,
  }) {
    return RuntimeModuleRepository(
      id: id ?? this.id,
      url: url ?? this.url,
      indexJsonUrl: indexJsonUrl ?? this.indexJsonUrl,
      name: name ?? this.name,
      modules: modules ?? this.modules,
      lastUpdated: lastUpdated ?? this.lastUpdated,
      error: error,
      skippedCount: skippedCount ?? this.skippedCount,
    );
  }
}

String _readString(dynamic value, {String fallback = ''}) {
  if (value is String) return value;
  return fallback;
}

int _readInt(dynamic value, {int fallback = 0}) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  if (value is String) return int.tryParse(value) ?? fallback;
  return fallback;
}

int? _nullableInt(dynamic value) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  if (value is String) return int.tryParse(value);
  return null;
}

extension<T> on Iterable<T> {
  T? get lastOrNull => isEmpty ? null : last;
}

extension on String {
  String ifEmpty(String fallback) => isEmpty ? fallback : this;
}
