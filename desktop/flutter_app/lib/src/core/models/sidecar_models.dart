const _missing = Object();

enum DeviceConnectionMode {
  disconnected,
  abk,
  adbFallback;

  factory DeviceConnectionMode.fromJson(String? raw) {
    return switch (raw) {
      'abk' => DeviceConnectionMode.abk,
      'adbFallback' => DeviceConnectionMode.adbFallback,
      _ => DeviceConnectionMode.disconnected,
    };
  }

  String get label {
    return switch (this) {
      DeviceConnectionMode.abk => 'Connected over ABK',
      DeviceConnectionMode.adbFallback => 'ADB fallback mode',
      DeviceConnectionMode.disconnected => 'Idle',
    };
  }
}

class DetectedDevice {
  const DetectedDevice({
    required this.serial,
    required this.status,
    required this.detail,
  });

  final String serial;
  final String status;
  final String detail;

  bool get isReady => status.toLowerCase() == 'device';

  factory DetectedDevice.fromJson(Map<String, dynamic> json) {
    return DetectedDevice(
      serial: _readString(json['serial']),
      status: _readString(json['status']),
      detail: _readString(json['detail']),
    );
  }
}

class DeviceConnectionState {
  const DeviceConnectionState({
    required this.serial,
    required this.agentHost,
    required this.agentPort,
    required this.connected,
    required this.mode,
    required this.lastError,
    required this.lastDetected,
    required this.lastDetectRaw,
  });

  final String? serial;
  final String agentHost;
  final int agentPort;
  final bool connected;
  final DeviceConnectionMode mode;
  final String? lastError;
  final List<DetectedDevice> lastDetected;
  final String lastDetectRaw;

  factory DeviceConnectionState.fromJson(Map<String, dynamic> json) {
    return DeviceConnectionState(
      serial: _nullableString(json['serial']),
      agentHost: _readString(json['agentHost'], fallback: '127.0.0.1'),
      agentPort: _readInt(json['agentPort'], fallback: 48765),
      connected: json['connected'] == true,
      mode: DeviceConnectionMode.fromJson(_nullableString(json['mode'])),
      lastError: _nullableString(json['lastError']),
      lastDetected: _readDeviceList(json['lastDetected']),
      lastDetectRaw: _readString(json['lastDetectRaw']),
    );
  }

  factory DeviceConnectionState.disconnected({
    List<DetectedDevice> lastDetected = const <DetectedDevice>[],
    String? lastError,
  }) {
    return DeviceConnectionState(
      serial: null,
      agentHost: '127.0.0.1',
      agentPort: 48765,
      connected: false,
      mode: DeviceConnectionMode.disconnected,
      lastError: lastError,
      lastDetected: lastDetected,
      lastDetectRaw: '',
    );
  }

  factory DeviceConnectionState.fallback({
    required String serial,
    required String lastError,
    required List<DetectedDevice> lastDetected,
  }) {
    return DeviceConnectionState(
      serial: serial,
      agentHost: '127.0.0.1',
      agentPort: 48765,
      connected: false,
      mode: DeviceConnectionMode.adbFallback,
      lastError: lastError,
      lastDetected: lastDetected,
      lastDetectRaw: '',
    );
  }

  DeviceConnectionState copyWith({
    Object? serial = _missing,
    String? agentHost,
    int? agentPort,
    bool? connected,
    DeviceConnectionMode? mode,
    Object? lastError = _missing,
    List<DetectedDevice>? lastDetected,
    String? lastDetectRaw,
  }) {
    return DeviceConnectionState(
      serial: identical(serial, _missing) ? this.serial : serial as String?,
      agentHost: agentHost ?? this.agentHost,
      agentPort: agentPort ?? this.agentPort,
      connected: connected ?? this.connected,
      mode: mode ?? this.mode,
      lastError: identical(lastError, _missing)
          ? this.lastError
          : lastError as String?,
      lastDetected: lastDetected ?? this.lastDetected,
      lastDetectRaw: lastDetectRaw ?? this.lastDetectRaw,
    );
  }
}

class SidecarEndpoint {
  const SidecarEndpoint({required this.host, required this.port});

  final String host;
  final int port;

  factory SidecarEndpoint.fromJson(Map<String, dynamic> json) {
    return SidecarEndpoint(
      host: _readString(json['host'], fallback: '127.0.0.1'),
      port: _readInt(json['port'], fallback: 38765),
    );
  }
}

class AgentHealth {
  const AgentHealth({
    required this.status,
    required this.protocolVersion,
    required this.port,
    required this.appVersion,
    required this.appVersionCode,
    required this.rootGranted,
    required this.managerAccessKind,
    required this.managerDiagnostic,
    required this.capabilities,
  });

  final String status;
  final String protocolVersion;
  final int port;
  final String appVersion;
  final int appVersionCode;
  final bool rootGranted;
  final String managerAccessKind;
  final String? managerDiagnostic;
  final List<String> capabilities;

  bool supports(String capability) => capabilities.contains(capability);

  factory AgentHealth.fromJson(Map<String, dynamic> json) {
    return AgentHealth(
      status: _readString(json['status'], fallback: 'unknown'),
      protocolVersion: _readString(json['protocolVersion']),
      port: _readInt(json['port'], fallback: 48765),
      appVersion: _readString(json['appVersion']),
      appVersionCode: _readInt(json['appVersionCode']),
      rootGranted: json['rootGranted'] == true,
      managerAccessKind: _readString(json['managerAccessKind']),
      managerDiagnostic: _nullableString(json['managerDiagnostic']),
      capabilities: _readStringList(json['capabilities']),
    );
  }
}

class SidecarHealth {
  const SidecarHealth({
    required this.status,
    required this.protocolVersion,
    required this.sidecar,
    required this.device,
    required this.agent,
  });

  final String status;
  final String protocolVersion;
  final SidecarEndpoint sidecar;
  final DeviceConnectionState device;
  final AgentHealth? agent;

  factory SidecarHealth.fromJson(Map<String, dynamic> json) {
    final agent = _readMap(json['agent']);
    return SidecarHealth(
      status: _readString(json['status'], fallback: 'unknown'),
      protocolVersion: _readString(json['protocolVersion']),
      sidecar: SidecarEndpoint.fromJson(_readMap(json['sidecar'])),
      device: DeviceConnectionState.fromJson(_readMap(json['device'])),
      agent: agent.isEmpty ? null : AgentHealth.fromJson(agent),
    );
  }
}

class DeviceDetectionResult {
  const DeviceDetectionResult({required this.devices, required this.raw});

  final List<DetectedDevice> devices;
  final String raw;

  factory DeviceDetectionResult.fromJson(Map<String, dynamic> json) {
    return DeviceDetectionResult(
      devices: _readDeviceList(json['devices']),
      raw: _readString(json['raw']),
    );
  }
}

class ConnectResult {
  const ConnectResult({
    required this.connected,
    required this.mode,
    required this.device,
    required this.agent,
  });

  final bool connected;
  final DeviceConnectionMode mode;
  final DeviceConnectionState device;
  final AgentHealth? agent;

  factory ConnectResult.fromJson(Map<String, dynamic> json) {
    final device = DeviceConnectionState.fromJson(_readMap(json['device']));
    final agent = _readMap(json['agent']);
    return ConnectResult(
      connected: json['connected'] == true,
      mode: DeviceConnectionMode.fromJson(
        _nullableString(json['mode']) ?? device.mode.name,
      ),
      device: device,
      agent: agent.isEmpty ? null : AgentHealth.fromJson(agent),
    );
  }
}

class ProxySettings {
  const ProxySettings({
    required this.httpProxy,
    required this.httpsProxy,
    required this.allProxy,
    required this.noProxy,
  });

  const ProxySettings.empty()
    : httpProxy = null,
      httpsProxy = null,
      allProxy = null,
      noProxy = null;

  final String? httpProxy;
  final String? httpsProxy;
  final String? allProxy;
  final String? noProxy;

  factory ProxySettings.fromJson(Map<String, dynamic> json) {
    return ProxySettings(
      httpProxy: _nullableString(json['httpProxy']),
      httpsProxy: _nullableString(json['httpsProxy']),
      allProxy: _nullableString(json['allProxy']),
      noProxy: _nullableString(json['noProxy']),
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

Map<String, dynamic> _readMap(dynamic value) {
  if (value is Map<String, dynamic>) {
    return value;
  }
  if (value is Map) {
    return Map<String, dynamic>.from(value);
  }
  return const <String, dynamic>{};
}

List<DetectedDevice> _readDeviceList(dynamic value) {
  if (value is! List) {
    return const <DetectedDevice>[];
  }
  return value
      .whereType<Map>()
      .map((item) => DetectedDevice.fromJson(Map<String, dynamic>.from(item)))
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
