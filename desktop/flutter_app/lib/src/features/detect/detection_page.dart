import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/localization/app_strings.dart';
import '../../core/models/sidecar_models.dart';
import '../../core/state/dashboard_controller.dart';
import '../../widgets/panel_card.dart';
import '../../widgets/status_pill.dart';

class DetectionPage extends ConsumerWidget {
  const DetectionPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(dashboardControllerProvider);
    final controller = ref.read(dashboardControllerProvider.notifier);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final strings = context.strings;

    return CustomScrollView(
      slivers: <Widget>[
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(28, 24, 28, 24),
          sliver: SliverList(
            delegate: SliverChildListDelegate(<Widget>[
              Row(
                children: <Widget>[
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        Text(
                          strings.detectionTitle,
                          style: theme.textTheme.headlineLarge,
                        ),
                        const SizedBox(height: 8),
                        Text(
                          strings.detectionIntro,
                          style: theme.textTheme.bodyLarge?.copyWith(
                            color: scheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                  FilledButton.tonalIcon(
                    onPressed: state.isRefreshing
                        ? null
                        : controller.detectOnly,
                    icon: const Icon(Icons.radar_rounded),
                    label: Text(
                      state.isRefreshing
                          ? strings.scanning
                          : strings.refreshDevices,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 20),
              PanelCard(
                title: strings.detectionSummaryTitle,
                subtitle: strings.detectionSummarySubtitle,
                icon: Icons.dataset_linked_rounded,
                child: Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children: <Widget>[
                    StatusPill(
                      label:
                          '${state.devices.length} ${strings.detectTotalLabel}',
                      color: scheme.primary,
                      icon: Icons.devices_rounded,
                    ),
                    StatusPill(
                      label: '${state.readyDeviceCount} ${strings.readyLabel}',
                      color: scheme.secondary,
                      icon: Icons.usb_rounded,
                    ),
                    StatusPill(
                      label: strings.connectionModeLabel(
                        state.connection?.mode ??
                            DeviceConnectionMode.disconnected,
                      ),
                      color: _modeColor(state.connection?.mode, scheme),
                      icon: _modeIcon(state.connection?.mode),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              if (state.devices.isEmpty)
                PanelCard(
                  title: strings.noDetectedDevicesTitle,
                  subtitle: strings.noDetectedDevicesSubtitle,
                  icon: Icons.usb_off_rounded,
                  child: Text(
                    strings.detectionErrorSummary(
                      flow: state.flow,
                      readyDeviceCount: state.readyDeviceCount,
                      rawError: state.lastError,
                    ),
                    style: theme.textTheme.bodyLarge,
                  ),
                )
              else
                ...state.devices.map(
                  (device) => Padding(
                    padding: const EdgeInsets.only(bottom: 16),
                    child: _DetectedDeviceCard(
                      device: device,
                      isSelected: state.selectedSerial == device.serial,
                      isBusy: state.isRefreshing,
                      onConnect: device.isReady
                          ? () => controller.connectSelected(device.serial)
                          : null,
                    ),
                  ),
                ),
            ]),
          ),
        ),
      ],
    );
  }
}

class _DetectedDeviceCard extends StatelessWidget {
  const _DetectedDeviceCard({
    required this.device,
    required this.isSelected,
    required this.isBusy,
    this.onConnect,
  });

  final DetectedDevice device;
  final bool isSelected;
  final bool isBusy;
  final VoidCallback? onConnect;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final strings = context.strings;
    final ready = device.isReady;

    return PanelCard(
      title: device.serial,
      subtitle: device.detail.isEmpty
          ? strings.noExtraAdbDetail
          : device.detail,
      icon: ready ? Icons.phone_android_rounded : Icons.report_problem_rounded,
      actions: <Widget>[
        StatusPill(
          label: ready
              ? strings.readyStateLabel
              : strings.deviceStatusLabel(device.status),
          color: ready ? scheme.primary : scheme.error,
          icon: ready
              ? Icons.check_circle_rounded
              : Icons.error_outline_rounded,
        ),
      ],
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(
            ready ? strings.deviceEligibleForAbk : strings.deviceNotReadyForAbk,
            style: Theme.of(context).textTheme.bodyLarge,
          ),
          const SizedBox(height: 16),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: <Widget>[
              FilledButton(
                onPressed: isBusy ? null : onConnect,
                child: Text(
                  isSelected
                      ? strings.reconnectThisDevice
                      : strings.connectThisDevice,
                ),
              ),
              if (isSelected)
                OutlinedButton.icon(
                  onPressed: null,
                  icon: const Icon(Icons.radio_button_checked_rounded),
                  label: Text(strings.currentSelected),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

Color _modeColor(DeviceConnectionMode? mode, ColorScheme scheme) {
  return switch (mode) {
    DeviceConnectionMode.abk => scheme.primary,
    DeviceConnectionMode.adbFallback => scheme.secondary,
    DeviceConnectionMode.disconnected || null => scheme.onSurfaceVariant,
  };
}

IconData _modeIcon(DeviceConnectionMode? mode) {
  return switch (mode) {
    DeviceConnectionMode.abk => Icons.verified_rounded,
    DeviceConnectionMode.adbFallback => Icons.usb_rounded,
    DeviceConnectionMode.disconnected ||
    null => Icons.pause_circle_outline_rounded,
  };
}
