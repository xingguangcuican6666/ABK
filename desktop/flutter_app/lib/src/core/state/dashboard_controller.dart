import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/abk_sidecar_api.dart';
import '../models/sidecar_models.dart';

const _missing = Object();

final sidecarBaseUrlOverrideProvider = Provider<String?>((ref) => null);

final sidecarApiProvider = Provider<AbkSidecarApi>((ref) {
  final overrideBaseUrl = ref.watch(sidecarBaseUrlOverrideProvider);
  final client =
      overrideBaseUrl?.trim().isNotEmpty == true
      ? HttpAbkSidecarClient(baseUrl: overrideBaseUrl!.trim())
      : HttpAbkSidecarClient.fromEnvironment();
  ref.onDispose(client.close);
  return client;
});

final dashboardControllerProvider =
    StateNotifierProvider<DashboardController, DashboardState>((ref) {
      final controller = DashboardController(ref.read(sidecarApiProvider));
      return controller;
    });

enum ConnectionFlow {
  idle,
  detecting,
  connecting,
  connectedAbk,
  connectedAdbFallback,
  sidecarUnavailable,
  failed,
}

class DashboardState {
  const DashboardState({
    required this.flow,
    required this.isRefreshing,
    required this.sidecarAvailable,
    required this.health,
    required this.connection,
    required this.devices,
    required this.selectedSerial,
    required this.lastError,
    required this.lastUpdatedAt,
  });

  const DashboardState.initial()
    : flow = ConnectionFlow.idle,
      isRefreshing = false,
      sidecarAvailable = false,
      health = null,
      connection = null,
      devices = const <DetectedDevice>[],
      selectedSerial = null,
      lastError = null,
      lastUpdatedAt = null;

  final ConnectionFlow flow;
  final bool isRefreshing;
  final bool sidecarAvailable;
  final SidecarHealth? health;
  final DeviceConnectionState? connection;
  final List<DetectedDevice> devices;
  final String? selectedSerial;
  final String? lastError;
  final DateTime? lastUpdatedAt;

  int get readyDeviceCount => devices.where((device) => device.isReady).length;

  DashboardState copyWith({
    ConnectionFlow? flow,
    bool? isRefreshing,
    bool? sidecarAvailable,
    Object? health = _missing,
    Object? connection = _missing,
    List<DetectedDevice>? devices,
    Object? selectedSerial = _missing,
    Object? lastError = _missing,
    Object? lastUpdatedAt = _missing,
  }) {
    return DashboardState(
      flow: flow ?? this.flow,
      isRefreshing: isRefreshing ?? this.isRefreshing,
      sidecarAvailable: sidecarAvailable ?? this.sidecarAvailable,
      health: identical(health, _missing)
          ? this.health
          : health as SidecarHealth?,
      connection: identical(connection, _missing)
          ? this.connection
          : connection as DeviceConnectionState?,
      devices: devices ?? this.devices,
      selectedSerial: identical(selectedSerial, _missing)
          ? this.selectedSerial
          : selectedSerial as String?,
      lastError: identical(lastError, _missing)
          ? this.lastError
          : lastError as String?,
      lastUpdatedAt: identical(lastUpdatedAt, _missing)
          ? this.lastUpdatedAt
          : lastUpdatedAt as DateTime?,
    );
  }
}

class DashboardController extends StateNotifier<DashboardState> {
  DashboardController(this._api) : super(const DashboardState.initial()) {
    unawaited(bootstrap());
  }

  final AbkSidecarApi _api;

  Future<void> bootstrap() async {
    await refreshPipeline(autoConnect: true);
  }

  Future<void> refreshPipeline({bool autoConnect = true}) async {
    if (state.isRefreshing) {
      return;
    }

    state = state.copyWith(
      flow: ConnectionFlow.detecting,
      isRefreshing: true,
      lastError: null,
    );

    try {
      final health = await _api.getHealth();
      state = state.copyWith(
        sidecarAvailable: true,
        health: health,
        lastUpdatedAt: DateTime.now(),
      );

      final detection = await _api.detectDevices();
      final connection = _normalizeDisconnectedConnection(
        await _api.getDeviceState(),
        detection.devices,
      );

      state = state.copyWith(
        connection: connection,
        devices: detection.devices,
        selectedSerial: connection.serial,
        lastUpdatedAt: DateTime.now(),
      );

      if (connection.connected && connection.mode == DeviceConnectionMode.abk) {
        state = state.copyWith(
          flow: ConnectionFlow.connectedAbk,
          isRefreshing: false,
          lastError: connection.lastError,
        );
        return;
      }

      if (!autoConnect) {
        state = state.copyWith(
          flow: _passiveFlowFor(connection),
          isRefreshing: false,
          lastError: connection.lastError,
        );
        return;
      }

      final readyDevices = detection.devices
          .where((device) => device.isReady)
          .toList();
      if (readyDevices.length == 1) {
        await _connectSelectedInternal(
          readyDevices.first.serial,
          devices: detection.devices,
        );
        return;
      }

      state = state.copyWith(
        flow: readyDevices.length > 1
            ? ConnectionFlow.failed
            : _passiveFlowFor(connection),
        isRefreshing: false,
        lastError: readyDevices.length > 1
            ? 'Multiple ADB devices detected. Pick one from the detection page.'
          : connection.lastError,
      );
    } on SidecarException catch (error) {
      final hasHealth = state.health != null;
      state = state.copyWith(
        flow: hasHealth
            ? ConnectionFlow.failed
            : ConnectionFlow.sidecarUnavailable,
        isRefreshing: false,
        sidecarAvailable: hasHealth ? true : false,
        health: hasHealth ? state.health : null,
        connection: hasHealth
            ? _normalizeDisconnectedConnection(
                state.connection ??
                    DeviceConnectionState.disconnected(
                      lastDetected: state.devices,
                    ),
                state.devices,
              )
            : null,
        devices: hasHealth ? state.devices : const <DetectedDevice>[],
        lastError: error.message,
        lastUpdatedAt: DateTime.now(),
      );
    }
  }

  Future<void> detectOnly() async {
    await refreshPipeline(autoConnect: false);
  }

  Future<void> connectSelected(String serial) async {
    await _connectSelectedInternal(serial, devices: state.devices);
  }

  Future<void> disconnect() async {
    state = state.copyWith(isRefreshing: true, lastError: null);
    try {
      final connection = await _api.disconnectDevice();
      final normalizedConnection = _normalizeDisconnectedConnection(
        connection,
        state.devices,
      );
      state = state.copyWith(
        flow: ConnectionFlow.idle,
        isRefreshing: false,
        sidecarAvailable: true,
        connection: normalizedConnection,
        selectedSerial: normalizedConnection.serial,
        lastUpdatedAt: DateTime.now(),
      );
      await detectOnly();
    } on SidecarException catch (error) {
      state = state.copyWith(
        flow: ConnectionFlow.failed,
        isRefreshing: false,
        lastError: error.message,
        lastUpdatedAt: DateTime.now(),
      );
    }
  }

  Future<void> _connectSelectedInternal(
    String serial, {
    required List<DetectedDevice> devices,
  }) async {
    state = state.copyWith(
      flow: ConnectionFlow.connecting,
      isRefreshing: true,
      selectedSerial: serial,
      lastError: null,
    );

    try {
      final result = await _api.connectDevice(serial);
      final normalizedConnection = _normalizeDisconnectedConnection(
        result.device.copyWith(mode: result.mode),
        devices,
      );
      state = state.copyWith(
        flow: ConnectionFlow.connectedAbk,
        isRefreshing: false,
        sidecarAvailable: true,
        connection: normalizedConnection,
        devices: devices,
        selectedSerial: normalizedConnection.serial,
        lastUpdatedAt: DateTime.now(),
      );
    } on SidecarException catch (error) {
      final fallback = await _safeDeviceState();
      final normalizedFallback = _normalizeDisconnectedConnection(
        fallback ??
            DeviceConnectionState.fallback(
              serial: serial,
              lastError: error.message,
              lastDetected: devices,
            ),
        devices,
      );
      state = state.copyWith(
        flow: ConnectionFlow.connectedAdbFallback,
        isRefreshing: false,
        sidecarAvailable: true,
        connection: normalizedFallback,
        devices: devices,
        selectedSerial: normalizedFallback.serial,
        lastError: error.message,
        lastUpdatedAt: DateTime.now(),
      );
    }
  }

  Future<DeviceConnectionState?> _safeDeviceState() async {
    try {
      return await _api.getDeviceState();
    } on SidecarException {
      return null;
    }
  }

  ConnectionFlow _passiveFlowFor(DeviceConnectionState connection) {
    return switch (connection.mode) {
      DeviceConnectionMode.abk => ConnectionFlow.connectedAbk,
      DeviceConnectionMode.adbFallback => ConnectionFlow.connectedAdbFallback,
      DeviceConnectionMode.disconnected => ConnectionFlow.idle,
    };
  }

  DeviceConnectionState _normalizeDisconnectedConnection(
    DeviceConnectionState connection,
    List<DetectedDevice> devices,
  ) {
    if (connection.connected) {
      return connection;
    }

    final readySerials = devices
        .where((device) => device.isReady)
        .map((device) => device.serial)
        .toSet();
    final serial = connection.serial?.trim();
    if (serial == null || serial.isEmpty) {
      return connection.copyWith(serial: null);
    }
    if (readySerials.contains(serial)) {
      return connection;
    }
    return connection.copyWith(serial: null);
  }

  @override
  void dispose() {
    _api.close();
    super.dispose();
  }
}
