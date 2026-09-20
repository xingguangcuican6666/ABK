import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:http/http.dart' as http;

import '../models/build_models.dart';
import '../models/device_models.dart';
import '../models/sidecar_models.dart';

abstract interface class AbkSidecarApi {
  Future<SidecarHealth> getHealth();

  Future<DeviceConnectionState> getDeviceState();

  Future<RuntimeBuildSummary?> getRuntimeBuildSummary();

  Future<GitHubSessionStatus> getGitHubSession();

  Future<GitHubLoginChallenge> startGitHubLogin();

  Future<GitHubLoginResult> pollGitHubLogin(String deviceCode);

  Future<GitHubSessionStatus> ensureGitHubFork();

  Future<GitHubSessionStatus> syncGitHubFork();

  Future<GitHubSessionStatus> logoutGitHub();

  Future<ProxySettings> getProxySettings();

  Future<ProxySettings> saveProxySettings(Map<String, dynamic> request);

  Future<String?> setDownloadDirectory(String path);

  Future<DeviceDetectionResult> detectDevices();

  Future<ConnectResult> connectDevice(String serial);

  Future<DeviceConnectionState> disconnectDevice();

  Future<AbkRuntimeEnvelope> getRuntime();

  Future<RootGrantsEnvelope> getRootGrants();

  Future<KernelFeaturesEnvelope> getKernelFeatures();

  Future<PackageInfoSummary?> getPackageInfo(String packageName);

  Future<ShellOperationResult> setRootGrantAllowed(
    String packageName,
    bool allowed,
  );

  Future<Uint8List?> getRootGrantIcon(String packageName);

  Future<SusfsEnvelope> getSusfs();

  Future<DesktopTaskSnapshot> applySusfs(Map<String, dynamic> config);

  Future<ShellOperationResult> setKernelFeatureEnabled(
    String featureId,
    bool enabled,
  );

  Future<ShellOperationResult> setRuntimeModuleEnabled(
    String moduleId,
    bool enabled,
  );

  Future<ShellOperationResult> setRuntimeModulePendingUninstall(
    String moduleId,
    bool pending,
  );

  Future<DesktopTaskSnapshot> runRuntimeModuleAction(String moduleId);

  Future<DesktopTaskSnapshot> installModule(String zipPath);

  Uri runtimeModuleWebUiUri(String moduleId, {String? relativePath});

  Future<DesktopTaskSnapshot> startGkiBuild(Map<String, dynamic> request);

  Future<List<LocalBuildBackendDescriptor>> getLocalBuildBackends();

  Future<DesktopTaskSnapshot> installLocalBuildBackend(
    LocalBuildBackendKind kind,
    Map<String, dynamic> request,
  );

  Future<List<SupportedKernelLine>> getLocalBuildCatalog();

  Future<LocalBuildSettings> updateLocalBuildSettings(
    Map<String, dynamic> request,
  );

  Future<LocalBuildSourceInstancesResponse> getLocalBuildSourceInstances();

  Future<LocalBuildSourceInstance> createLocalBuildSourceInstance(
    Map<String, dynamic> request,
  );

  Future<DesktopTaskSnapshot> syncLocalBuildSourceInstance(
    String sourceInstanceId,
    Map<String, dynamic> request,
  );

  Future<LocalBuildProfilesResponse> getLocalBuildProfiles();

  Future<LocalBuildProfile> saveLocalBuildProfile(Map<String, dynamic> request);

  Future<DesktopTaskSnapshot> buildLocalBuildProfile(
    String profileId,
    Map<String, dynamic> request,
  );

  Future<List<LocalBuildArtifactEntry>> getLocalBuildArtifacts();

  Future<List<LocalBuildLogEntry>> getLocalBuildLogs();

  Future<LocalBuildStatus> getLocalBuildStatus();

  Future<DesktopTaskSnapshot> initLocalBuild(Map<String, dynamic> request);

  Future<DesktopTaskSnapshot> rebuildLocalBuild(Map<String, dynamic> request);

  Future<BuildDispatchResult> listBuildRuns({int limit = 20});

  Future<BuildDispatchResult> getBuildRun(int runId);

  Future<List<BuildArtifactSummary>> listBuildArtifacts(int runId);

  Future<DesktopTaskSnapshot> downloadBuildArtifact({
    required int runId,
    required int artifactId,
    String? outputDir,
  });

  Future<DesktopTaskSnapshot> getTask(String taskId);

  Future<DesktopTaskSnapshot> cancelTask(String taskId);

  Future<DesktopTaskSnapshot> exportDiagnostics();

  Uri taskDownloadUri(String taskId);

  void close();
}

class SidecarException implements Exception {
  const SidecarException(
    this.message, {
    this.statusCode,
    this.isNetwork = false,
  });

  final String message;
  final int? statusCode;
  final bool isNetwork;

  @override
  String toString() => message;
}

class HttpAbkSidecarClient implements AbkSidecarApi {
  HttpAbkSidecarClient({required String baseUrl, http.Client? client})
    : _baseUri = _normalizeBaseUri(baseUrl),
      _client = client ?? http.Client();

  factory HttpAbkSidecarClient.fromEnvironment() {
    const compileTimeBaseUrl = String.fromEnvironment(
      'ABK_DESKTOP_BASE_URL',
      defaultValue: '',
    );
    final runtimeBaseUrl = Platform.environment['ABK_DESKTOP_BASE_URL'];
    final baseUrl = runtimeBaseUrl?.trim().isNotEmpty == true
        ? runtimeBaseUrl!.trim()
        : (compileTimeBaseUrl.isNotEmpty
              ? compileTimeBaseUrl
              : 'http://127.0.0.1:38765');
    return HttpAbkSidecarClient(baseUrl: baseUrl);
  }

  final Uri _baseUri;
  final http.Client _client;

  @override
  Future<SidecarHealth> getHealth() async {
    final json = await _requestJson('GET', 'api/v1/health');
    return SidecarHealth.fromJson(json);
  }

  @override
  Future<DeviceConnectionState> getDeviceState() async {
    final json = await _requestJson('GET', 'api/v1/device');
    return DeviceConnectionState.fromJson(json);
  }

  @override
  Future<RuntimeBuildSummary?> getRuntimeBuildSummary() async {
    try {
      final json = await _requestJson('GET', 'api/v1/runtime');
      final runtimeStatus = _readNestedMap(json, 'runtimeStatus');
      final build = _readNestedMap(runtimeStatus, 'build');
      if (build.isEmpty) {
        return null;
      }
      return RuntimeBuildSummary.fromJson(build);
    } on SidecarException {
      return null;
    }
  }

  @override
  Future<GitHubSessionStatus> getGitHubSession() async {
    final json = await _requestJson('GET', 'api/v1/github/session');
    return GitHubSessionStatus.fromJson(json);
  }

  @override
  Future<GitHubLoginChallenge> startGitHubLogin() async {
    final json = await _requestJson('POST', 'api/v1/github/login/start');
    return GitHubLoginChallenge.fromJson(json);
  }

  @override
  Future<GitHubLoginResult> pollGitHubLogin(String deviceCode) async {
    final json = await _requestJson(
      'POST',
      'api/v1/github/login/poll',
      body: <String, dynamic>{'deviceCode': deviceCode},
    );
    return GitHubLoginResult.fromJson(json);
  }

  @override
  Future<GitHubSessionStatus> ensureGitHubFork() async {
    final json = await _requestJson('POST', 'api/v1/github/fork/ensure');
    return GitHubSessionStatus.fromJson(json);
  }

  @override
  Future<GitHubSessionStatus> syncGitHubFork() async {
    final json = await _requestJson('POST', 'api/v1/github/fork/sync');
    return GitHubSessionStatus.fromJson(json);
  }

  @override
  Future<GitHubSessionStatus> logoutGitHub() async {
    final json = await _requestJson('POST', 'api/v1/github/logout');
    return GitHubSessionStatus.fromJson(json);
  }

  @override
  Future<ProxySettings> getProxySettings() async {
    final json = await _requestJson('GET', 'api/v1/settings/proxy');
    return ProxySettings.fromJson(json);
  }

  @override
  Future<ProxySettings> saveProxySettings(Map<String, dynamic> request) async {
    final json = await _requestJson(
      'POST',
      'api/v1/settings/proxy',
      body: request,
    );
    return ProxySettings.fromJson(json);
  }

  @override
  Future<String?> setDownloadDirectory(String path) async {
    final json = await _requestJson(
      'POST',
      'api/v1/settings/download-dir',
      body: <String, dynamic>{'path': path},
    );
    return json['downloadDir'] as String?;
  }

  @override
  Future<DeviceDetectionResult> detectDevices() async {
    final json = await _requestJson('POST', 'api/v1/device/detect');
    return DeviceDetectionResult.fromJson(json);
  }

  @override
  Future<ConnectResult> connectDevice(String serial) async {
    final json = await _requestJson(
      'POST',
      'api/v1/device/connect',
      body: <String, dynamic>{'serial': serial},
    );
    return ConnectResult.fromJson(json);
  }

  @override
  Future<DeviceConnectionState> disconnectDevice() async {
    final json = await _requestJson('POST', 'api/v1/device/disconnect');
    return DeviceConnectionState.fromJson(_readNestedMap(json, 'device'));
  }

  @override
  Future<AbkRuntimeEnvelope> getRuntime() async {
    final json = await _requestJson('GET', 'api/v1/runtime');
    return AbkRuntimeEnvelope.fromJson(json);
  }

  @override
  Future<RootGrantsEnvelope> getRootGrants() async {
    final json = await _requestJson('GET', 'api/v1/root-grants');
    return RootGrantsEnvelope.fromJson(json);
  }

  @override
  Future<KernelFeaturesEnvelope> getKernelFeatures() async {
    final json = await _requestJson('GET', 'api/v1/kernel-features');
    return KernelFeaturesEnvelope.fromJson(json);
  }

  @override
  Future<PackageInfoSummary?> getPackageInfo(String packageName) async {
    final json = await _requestJson(
      'POST',
      'api/v1/packages/info',
      body: <String, dynamic>{
        'packages': <String>[packageName],
      },
    );
    return _readMapList(
      json['packages'],
    ).map(PackageInfoSummary.fromJson).toList(growable: false).firstOrNull;
  }

  @override
  Future<ShellOperationResult> setRootGrantAllowed(
    String packageName,
    bool allowed,
  ) async {
    final json = await _requestJson(
      'POST',
      'api/v1/root-grants/${Uri.encodeComponent(packageName)}/allow',
      body: <String, dynamic>{'allowed': allowed},
    );
    return ShellOperationResult.fromJson(json);
  }

  @override
  Future<Uint8List?> getRootGrantIcon(String packageName) async {
    try {
      final response = await _client.get(
        _baseUri.resolve(
          'api/v1/root-grants/${Uri.encodeComponent(packageName)}/icon',
        ),
      );
      if (response.statusCode < 200 || response.statusCode >= 300) {
        return null;
      }
      return response.bodyBytes;
    } on http.ClientException {
      return null;
    } on SocketException {
      return null;
    }
  }

  @override
  Future<SusfsEnvelope> getSusfs() async {
    final json = await _requestJson('GET', 'api/v1/susfs');
    return SusfsEnvelope.fromJson(json);
  }

  @override
  Future<DesktopTaskSnapshot> applySusfs(Map<String, dynamic> config) async {
    final json = await _requestJson('POST', 'api/v1/susfs/apply', body: config);
    return DesktopTaskSnapshot.fromJson(json);
  }

  @override
  Future<ShellOperationResult> setKernelFeatureEnabled(
    String featureId,
    bool enabled,
  ) async {
    final json = await _requestJson(
      'POST',
      'api/v1/kernel-features/${Uri.encodeComponent(featureId)}',
      body: <String, dynamic>{'enabled': enabled},
    );
    return ShellOperationResult.fromJson(json);
  }

  @override
  Future<ShellOperationResult> setRuntimeModuleEnabled(
    String moduleId,
    bool enabled,
  ) async {
    final json = await _requestJson(
      'POST',
      'api/v1/runtime/modules/${Uri.encodeComponent(moduleId)}/enable',
      body: <String, dynamic>{'enabled': enabled},
    );
    return ShellOperationResult.fromJson(json);
  }

  @override
  Future<ShellOperationResult> setRuntimeModulePendingUninstall(
    String moduleId,
    bool pending,
  ) async {
    final json = await _requestJson(
      'POST',
      'api/v1/runtime/modules/${Uri.encodeComponent(moduleId)}/pending-uninstall',
      body: <String, dynamic>{'pending': pending},
    );
    return ShellOperationResult.fromJson(json);
  }

  @override
  Future<DesktopTaskSnapshot> runRuntimeModuleAction(String moduleId) async {
    final json = await _requestJson(
      'POST',
      'api/v1/runtime/modules/${Uri.encodeComponent(moduleId)}/action',
      body: const <String, dynamic>{},
    );
    return DesktopTaskSnapshot.fromJson(json);
  }

  @override
  Future<DesktopTaskSnapshot> installModule(String zipPath) async {
    final json = await _requestJson(
      'POST',
      'api/v1/install/module',
      body: <String, dynamic>{'zipPath': zipPath},
    );
    return DesktopTaskSnapshot.fromJson(json);
  }

  @override
  Uri runtimeModuleWebUiUri(String moduleId, {String? relativePath}) {
    final basePath =
        'api/v1/runtime/modules/${Uri.encodeComponent(moduleId)}/webui/files';
    if (relativePath == null || relativePath.trim().isEmpty) {
      return _baseUri.resolve(basePath);
    }
    return _baseUri.resolve(
      '$basePath/${relativePath.split('/').map(Uri.encodeComponent).join('/')}',
    );
  }

  @override
  Future<DesktopTaskSnapshot> startGkiBuild(
    Map<String, dynamic> request,
  ) async {
    final json = await _requestJson('POST', 'api/v1/builds/gki', body: request);
    return DesktopTaskSnapshot.fromJson(json);
  }

  @override
  Future<List<LocalBuildBackendDescriptor>> getLocalBuildBackends() async {
    final json = await _requestJson('GET', 'api/v1/local-build/backends');
    return _readMapList(
      json['backends'],
    ).map(LocalBuildBackendDescriptor.fromJson).toList(growable: false);
  }

  @override
  Future<DesktopTaskSnapshot> installLocalBuildBackend(
    LocalBuildBackendKind kind,
    Map<String, dynamic> request,
  ) async {
    final json = await _requestJson(
      'POST',
      'api/v1/local-build/backends/${Uri.encodeComponent(kind.name)}/install',
      body: request,
    );
    return DesktopTaskSnapshot.fromJson(json);
  }

  @override
  Future<List<SupportedKernelLine>> getLocalBuildCatalog() async {
    final json = await _requestJson('GET', 'api/v1/local-build/catalog');
    return _readMapList(
      json['kernelLines'],
    ).map(SupportedKernelLine.fromJson).toList(growable: false);
  }

  @override
  Future<LocalBuildSettings> updateLocalBuildSettings(
    Map<String, dynamic> request,
  ) async {
    final json = await _requestJson(
      'POST',
      'api/v1/local-build/settings',
      body: request,
    );
    return LocalBuildSettings.fromJson(json);
  }

  @override
  Future<LocalBuildSourceInstancesResponse> getLocalBuildSourceInstances() async {
    final json = await _requestJson('GET', 'api/v1/local-build/source-instances');
    return LocalBuildSourceInstancesResponse.fromJson(json);
  }

  @override
  Future<LocalBuildSourceInstance> createLocalBuildSourceInstance(
    Map<String, dynamic> request,
  ) async {
    final json = await _requestJson(
      'POST',
      'api/v1/local-build/source-instances',
      body: request,
    );
    return LocalBuildSourceInstance.fromJson(json);
  }

  @override
  Future<DesktopTaskSnapshot> syncLocalBuildSourceInstance(
    String sourceInstanceId,
    Map<String, dynamic> request,
  ) async {
    final json = await _requestJson(
      'POST',
      'api/v1/local-build/source-instances/${Uri.encodeComponent(sourceInstanceId)}/sync',
      body: request,
    );
    return DesktopTaskSnapshot.fromJson(json);
  }

  @override
  Future<LocalBuildProfilesResponse> getLocalBuildProfiles() async {
    final json = await _requestJson('GET', 'api/v1/local-build/profiles');
    return LocalBuildProfilesResponse.fromJson(json);
  }

  @override
  Future<LocalBuildProfile> saveLocalBuildProfile(
    Map<String, dynamic> request,
  ) async {
    final json = await _requestJson(
      'POST',
      'api/v1/local-build/profiles',
      body: request,
    );
    return LocalBuildProfile.fromJson(json);
  }

  @override
  Future<DesktopTaskSnapshot> buildLocalBuildProfile(
    String profileId,
    Map<String, dynamic> request,
  ) async {
    final json = await _requestJson(
      'POST',
      'api/v1/local-build/profiles/${Uri.encodeComponent(profileId)}/build',
      body: request,
    );
    return DesktopTaskSnapshot.fromJson(json);
  }

  @override
  Future<List<LocalBuildArtifactEntry>> getLocalBuildArtifacts() async {
    final json = await _requestJson('GET', 'api/v1/local-build/artifacts');
    return _readMapList(
      json['artifacts'],
    ).map(LocalBuildArtifactEntry.fromJson).toList(growable: false);
  }

  @override
  Future<List<LocalBuildLogEntry>> getLocalBuildLogs() async {
    final json = await _requestJson('GET', 'api/v1/local-build/logs');
    return _readMapList(
      json['logs'],
    ).map(LocalBuildLogEntry.fromJson).toList(growable: false);
  }

  @override
  Future<LocalBuildStatus> getLocalBuildStatus() async {
    final json = await _requestJson('GET', 'api/v1/local-build/status');
    return LocalBuildStatus.fromJson(json);
  }

  @override
  Future<DesktopTaskSnapshot> initLocalBuild(
    Map<String, dynamic> request,
  ) async {
    final json = await _requestJson(
      'POST',
      'api/v1/local-build/init',
      body: request,
    );
    return DesktopTaskSnapshot.fromJson(json);
  }

  @override
  Future<DesktopTaskSnapshot> rebuildLocalBuild(
    Map<String, dynamic> request,
  ) async {
    final json = await _requestJson(
      'POST',
      'api/v1/local-build/rebuild',
      body: request,
    );
    return DesktopTaskSnapshot.fromJson(json);
  }

  @override
  Future<BuildDispatchResult> listBuildRuns({int limit = 20}) async {
    final json = await _requestJson('GET', 'api/v1/builds/runs?limit=$limit');
    return BuildDispatchResult.fromJson(json);
  }

  @override
  Future<BuildDispatchResult> getBuildRun(int runId) async {
    final json = await _requestJson('GET', 'api/v1/builds/runs/$runId');
    return BuildDispatchResult.fromJson(json);
  }

  @override
  Future<List<BuildArtifactSummary>> listBuildArtifacts(int runId) async {
    final json = await _requestJson(
      'GET',
      'api/v1/builds/runs/$runId/artifacts',
    );
    return _readMapList(
      json['artifacts'],
    ).map(BuildArtifactSummary.fromJson).toList(growable: false);
  }

  @override
  Future<DesktopTaskSnapshot> downloadBuildArtifact({
    required int runId,
    required int artifactId,
    String? outputDir,
  }) async {
    final json = await _requestJson(
      'POST',
      'api/v1/builds/runs/$runId/artifacts/download',
      body: <String, dynamic>{'artifactId': artifactId},
    );
    return DesktopTaskSnapshot.fromJson(json);
  }

  @override
  Future<DesktopTaskSnapshot> getTask(String taskId) async {
    final json = await _requestJson('GET', 'api/v1/tasks/$taskId');
    return DesktopTaskSnapshot.fromJson(json);
  }

  @override
  Future<DesktopTaskSnapshot> cancelTask(String taskId) async {
    final json = await _requestJson(
      'POST',
      'api/v1/tasks/${Uri.encodeComponent(taskId)}/cancel',
    );
    return DesktopTaskSnapshot.fromJson(json);
  }

  @override
  Future<DesktopTaskSnapshot> exportDiagnostics() async {
    final json = await _requestJson('POST', 'api/v1/diagnostics/export');
    return DesktopTaskSnapshot.fromJson(json);
  }

  @override
  Uri taskDownloadUri(String taskId) {
    return _baseUri.resolve(
      'api/v1/tasks/${Uri.encodeComponent(taskId)}/download',
    );
  }

  @override
  void close() {
    _client.close();
  }

  Future<Map<String, dynamic>> _requestJson(
    String method,
    String path, {
    Map<String, dynamic>? body,
  }) async {
    try {
      final response = await switch (method) {
        'GET' => _client.get(_baseUri.resolve(path), headers: _jsonHeaders),
        'POST' => _client.post(
          _baseUri.resolve(path),
          headers: _jsonHeaders,
          body: jsonEncode(body ?? const <String, dynamic>{}),
        ),
        _ => throw ArgumentError.value(method, 'method', 'Unsupported method'),
      };
      return _decodeResponse(response);
    } on http.ClientException catch (error) {
      throw SidecarException(
        'Unable to reach the ABK desktop sidecar at ${_baseUri.toString()}. ${error.message}',
        isNetwork: true,
      );
    } on SocketException {
      throw SidecarException(
        'Unable to reach the ABK desktop sidecar at ${_baseUri.toString()}.',
        isNetwork: true,
      );
    } on HttpException catch (error) {
      throw SidecarException(error.message, isNetwork: true);
    }
  }

  Map<String, dynamic> _decodeResponse(http.Response response) {
    final body = response.body.trim();
    final json = body.isEmpty
        ? const <String, dynamic>{}
        : jsonDecode(body) as Object?;
    final map = json is Map
        ? Map<String, dynamic>.from(json)
        : <String, dynamic>{'stdout': body};
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return map;
    }

    throw SidecarException(
      (map['error'] as String?)?.trim().isNotEmpty == true
          ? map['error'] as String
          : 'Request failed with HTTP ${response.statusCode}.',
      statusCode: response.statusCode,
    );
  }
}

const _jsonHeaders = <String, String>{
  'accept': 'application/json',
  'content-type': 'application/json',
};

Uri _normalizeBaseUri(String raw) {
  final normalized = raw.endsWith('/') ? raw : '$raw/';
  return Uri.parse(normalized);
}

Map<String, dynamic> _readNestedMap(Map<String, dynamic> json, String key) {
  final nested = json[key];
  if (nested is Map<String, dynamic>) {
    return nested;
  }
  if (nested is Map) {
    return Map<String, dynamic>.from(nested);
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

extension<T> on List<T> {
  T? get firstOrNull => isEmpty ? null : first;
}
