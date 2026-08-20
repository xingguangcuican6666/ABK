import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/localization/app_strings.dart';
import '../../core/models/sidecar_models.dart';
import '../../core/state/dashboard_controller.dart';
import '../../widgets/panel_card.dart';
import '../../widgets/status_pill.dart';

class HomePage extends ConsumerWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(dashboardControllerProvider);
    final controller = ref.read(dashboardControllerProvider.notifier);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final strings = context.strings;
    final connection =
        state.connection ??
        DeviceConnectionState.disconnected(
          lastDetected: state.devices,
          lastError: state.lastError,
        );

    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(28, 24, 28, 32),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            children: <Widget>[
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(
                      strings.homeTitle,
                      style: theme.textTheme.headlineLarge,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      strings.homeIntro,
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
                    : () => controller.refreshPipeline(autoConnect: true),
                icon: const Icon(Icons.refresh_rounded),
                label: Text(
                  state.isRefreshing
                      ? strings.refreshing
                      : strings.refreshPipeline,
                ),
              ),
            ],
          ),
          const SizedBox(height: 22),
          LayoutBuilder(
            builder: (context, constraints) {
              final wide = constraints.maxWidth >= 1140;
              final hero = _HeroCard(
                state: state,
                connection: connection,
                onRetry: () => controller.refreshPipeline(autoConnect: true),
                onDisconnect: state.connection?.connected == true
                    ? controller.disconnect
                    : null,
                onOpenDetection: () => context.go('/detect'),
              );
              final rightColumn = Column(
                children: <Widget>[
                  _StatusGrid(state: state, connection: connection),
                  if (state.flow == ConnectionFlow.failed &&
                      state.readyDeviceCount > 1) ...<Widget>[
                    const SizedBox(height: 16),
                    _ErrorCard(
                      title: strings.errorCardTitle,
                      subtitle: strings.errorCardSubtitle,
                      message: strings.detectionErrorSummary(
                        flow: state.flow,
                        readyDeviceCount: state.readyDeviceCount,
                        rawError: state.lastError,
                      ),
                    ),
                  ] else if (state.flow ==
                          ConnectionFlow.connectedAdbFallback ||
                      state.flow == ConnectionFlow.sidecarUnavailable ||
                      state.lastError != null) ...<Widget>[
                    const SizedBox(height: 16),
                    _ErrorCard(
                      title: strings.errorCardTitle,
                      subtitle: strings.errorCardSubtitle,
                      message: strings.detectionErrorSummary(
                        flow: state.flow,
                        readyDeviceCount: state.readyDeviceCount,
                        rawError: state.lastError,
                      ),
                    ),
                  ],
                ],
              );

              if (wide) {
                return Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Expanded(flex: 6, child: hero),
                    const SizedBox(width: 16),
                    Expanded(flex: 4, child: rightColumn),
                  ],
                );
              }

              return Column(
                children: <Widget>[
                  hero,
                  const SizedBox(height: 16),
                  rightColumn,
                ],
              );
            },
          ),
          const SizedBox(height: 16),
          PanelCard(
            title: strings.homeNarrativeTitle,
            subtitle: strings.homeNarrativeSubtitle,
            icon: Icons.route_rounded,
            child: Wrap(
              spacing: 12,
              runSpacing: 12,
              children: <Widget>[
                _TimelineTile(
                  title: strings.timelineDesktopSidecar,
                  subtitle: state.sidecarAvailable
                      ? strings.sidecarResponding('127.0.0.1', 38765)
                      : strings.sidecarNotResponding,
                  color: state.sidecarAvailable ? scheme.primary : scheme.error,
                  icon: state.sidecarAvailable
                      ? Icons.dns_rounded
                      : Icons.cloud_off_rounded,
                ),
                _TimelineTile(
                  title: strings.timelineAdbDetection,
                  subtitle: state.devices.isEmpty
                      ? strings.noVisibleDevices
                      : strings.readyDeviceCount(state.readyDeviceCount),
                  color: state.devices.isEmpty
                      ? scheme.onSurfaceVariant
                      : scheme.secondary,
                  icon: Icons.usb_rounded,
                ),
                _TimelineTile(
                  title: strings.timelineAbkHandshake,
                  subtitle: strings.connectionModeLabel(connection.mode),
                  color: _modeColor(connection.mode, scheme),
                  icon: switch (connection.mode) {
                    DeviceConnectionMode.abk => Icons.verified_rounded,
                    DeviceConnectionMode.adbFallback =>
                      Icons.sync_problem_rounded,
                    DeviceConnectionMode.disconnected =>
                      Icons.pause_circle_outline_rounded,
                  },
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _HeroCard extends StatelessWidget {
  const _HeroCard({
    required this.state,
    required this.connection,
    required this.onRetry,
    required this.onOpenDetection,
    this.onDisconnect,
  });

  final DashboardState state;
  final DeviceConnectionState connection;
  final VoidCallback onRetry;
  final VoidCallback onOpenDetection;
  final VoidCallback? onDisconnect;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final strings = context.strings;
    final cardColor = _heroBackground(connection.mode, scheme);
    final textColor = _heroForeground(connection.mode, scheme);
    final statusColor = _heroStatusColor(connection.mode, scheme);

    return PanelCard(
      title: strings.heroHeadline(state.flow),
      subtitle: strings.heroSubtitle(state.flow),
      icon: Icons.hub_rounded,
      backgroundColor: cardColor,
      foregroundColor: textColor,
      subtitleColor: textColor.withValues(alpha: 0.86),
      borderColor: cardColor,
      iconBackgroundColor: textColor.withValues(alpha: 0.18),
      iconColor: textColor,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: <Widget>[
              StatusPill(
                label: strings.connectionModeLabel(connection.mode),
                color: statusColor,
                icon: switch (connection.mode) {
                  DeviceConnectionMode.abk => Icons.verified_rounded,
                  DeviceConnectionMode.adbFallback => Icons.usb_rounded,
                  DeviceConnectionMode.disconnected =>
                    Icons.pause_circle_outline_rounded,
                },
                backgroundOpacity: 0.16,
              ),
              StatusPill(
                label: strings.sidecarAvailabilityLabel(state.sidecarAvailable),
                color: state.sidecarAvailable ? scheme.primary : scheme.error,
                icon: state.sidecarAvailable
                    ? Icons.dns_rounded
                    : Icons.wifi_tethering_error_rounded,
                backgroundOpacity: 0.16,
              ),
            ],
          ),
          const SizedBox(height: 18),
          Text(
            strings.selectedDeviceHeadline(connection.serial),
            style: theme.textTheme.headlineMedium?.copyWith(
              color: textColor,
              fontSize: 32,
            ),
          ),
          const SizedBox(height: 22),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: <Widget>[
              FilledButton(
                onPressed: state.isRefreshing ? null : onRetry,
                style: FilledButton.styleFrom(
                  backgroundColor: textColor,
                  foregroundColor: cardColor,
                ),
                child: Text(strings.heroPrimaryAction(connection.mode)),
              ),
              OutlinedButton(
                onPressed: onOpenDetection,
                style: OutlinedButton.styleFrom(
                  foregroundColor: textColor,
                  side: BorderSide(color: textColor.withValues(alpha: 0.65)),
                ),
                child: Text(strings.openDetectionPage),
              ),
              if (onDisconnect != null)
                OutlinedButton(
                  onPressed: onDisconnect,
                  style: OutlinedButton.styleFrom(
                    foregroundColor: textColor,
                    side: BorderSide(color: textColor.withValues(alpha: 0.65)),
                  ),
                  child: Text(strings.disconnect),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class _StatusGrid extends StatelessWidget {
  const _StatusGrid({required this.state, required this.connection});

  final DashboardState state;
  final DeviceConnectionState connection;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final cards = <Widget>[
      _MetricCard(
        title: strings.metricMode,
        value: strings.connectionModeLabel(connection.mode),
        icon: Icons.layers_rounded,
      ),
      _MetricCard(
        title: strings.metricReadyDevices,
        value: '${state.readyDeviceCount}',
        icon: Icons.phone_android_rounded,
      ),
      _MetricCard(
        title: strings.metricProtocol,
        value: state.health?.protocolVersion ?? strings.unknownValue,
        icon: Icons.merge_type_rounded,
      ),
      _MetricCard(
        title: strings.metricTargetPort,
        value: '${connection.agentPort}',
        icon: Icons.settings_ethernet_rounded,
      ),
    ];

    return Wrap(
      spacing: 16,
      runSpacing: 16,
      children: cards.map((card) => SizedBox(width: 230, child: card)).toList(),
    );
  }
}

class _MetricCard extends StatelessWidget {
  const _MetricCard({
    required this.title,
    required this.value,
    required this.icon,
  });

  final String title;
  final String value;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return PanelCard(
      title: title,
      icon: icon,
      padding: const EdgeInsets.all(20),
      child: Text(
        value,
        style: Theme.of(context).textTheme.headlineMedium?.copyWith(
          fontSize: 24,
          color: scheme.onSurface,
        ),
      ),
    );
  }
}

class _ErrorCard extends StatelessWidget {
  const _ErrorCard({
    required this.title,
    required this.subtitle,
    required this.message,
  });

  final String title;
  final String subtitle;
  final String message;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return PanelCard(
      title: title,
      subtitle: subtitle,
      icon: Icons.error_outline_rounded,
      backgroundColor: scheme.errorContainer,
      foregroundColor: scheme.onErrorContainer,
      subtitleColor: scheme.onErrorContainer.withValues(alpha: 0.82),
      borderColor: scheme.errorContainer,
      iconBackgroundColor: scheme.onErrorContainer.withValues(alpha: 0.12),
      iconColor: scheme.onErrorContainer,
      child: Text(
        message,
        style: Theme.of(
          context,
        ).textTheme.bodyLarge?.copyWith(color: scheme.onErrorContainer),
      ),
    );
  }
}

class _TimelineTile extends StatelessWidget {
  const _TimelineTile({
    required this.title,
    required this.subtitle,
    required this.color,
    required this.icon,
  });

  final String title;
  final String subtitle;
  final Color color;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 280,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.08),
          borderRadius: BorderRadius.circular(24),
        ),
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Icon(icon, color: color),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(title, style: Theme.of(context).textTheme.labelLarge),
                    const SizedBox(height: 4),
                    Text(
                      subtitle,
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

Color _modeColor(DeviceConnectionMode mode, ColorScheme scheme) {
  return switch (mode) {
    DeviceConnectionMode.abk => scheme.primary,
    DeviceConnectionMode.adbFallback => scheme.secondary,
    DeviceConnectionMode.disconnected => scheme.onSurfaceVariant,
  };
}

Color _heroBackground(DeviceConnectionMode mode, ColorScheme scheme) {
  return switch (mode) {
    DeviceConnectionMode.abk => scheme.primaryContainer,
    DeviceConnectionMode.adbFallback => scheme.secondaryContainer,
    DeviceConnectionMode.disconnected => scheme.surfaceContainerHigh,
  };
}

Color _heroForeground(DeviceConnectionMode mode, ColorScheme scheme) {
  return switch (mode) {
    DeviceConnectionMode.abk => scheme.onPrimaryContainer,
    DeviceConnectionMode.adbFallback => scheme.onSecondaryContainer,
    DeviceConnectionMode.disconnected => scheme.onSurface,
  };
}

Color _heroStatusColor(DeviceConnectionMode mode, ColorScheme scheme) {
  return switch (mode) {
    DeviceConnectionMode.abk => scheme.primary,
    DeviceConnectionMode.adbFallback => scheme.secondary,
    DeviceConnectionMode.disconnected => scheme.onSurfaceVariant,
  };
}
