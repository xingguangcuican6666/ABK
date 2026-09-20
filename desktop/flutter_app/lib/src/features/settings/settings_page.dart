import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/build_models.dart';
import '../../core/localization/app_strings.dart';
import '../../core/models/sidecar_models.dart';
import '../../core/state/dashboard_controller.dart';
import '../../widgets/panel_card.dart';
import '../../widgets/status_pill.dart';
import 'settings_page_controller.dart';

class SettingsPage extends ConsumerStatefulWidget {
  const SettingsPage({super.key});

  @override
  ConsumerState<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends ConsumerState<SettingsPage> {
  late final TextEditingController _downloadDirController;
  late final TextEditingController _httpProxyController;
  late final TextEditingController _httpsProxyController;
  late final TextEditingController _allProxyController;
  late final TextEditingController _noProxyController;
  bool _requestedInitialLoad = false;

  @override
  void initState() {
    super.initState();
    _downloadDirController = TextEditingController();
    _httpProxyController = TextEditingController();
    _httpsProxyController = TextEditingController();
    _allProxyController = TextEditingController();
    _noProxyController = TextEditingController();
  }

  @override
  void dispose() {
    _downloadDirController.dispose();
    _httpProxyController.dispose();
    _httpsProxyController.dispose();
    _allProxyController.dispose();
    _noProxyController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final settingsState = ref.watch(settingsPageControllerProvider);
    final settings = ref.read(settingsPageControllerProvider.notifier);
    final dashboard = ref.watch(dashboardControllerProvider);
    final strings = context.strings;
    final scheme = Theme.of(context).colorScheme;

    if (!_requestedInitialLoad) {
      _requestedInitialLoad = true;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        settings.refresh();
      });
    }
    if (_downloadDirController.text != settingsState.downloadDirDraft) {
      _downloadDirController.value = _downloadDirController.value.copyWith(
        text: settingsState.downloadDirDraft,
        selection: TextSelection.collapsed(
          offset: settingsState.downloadDirDraft.length,
        ),
        composing: TextRange.empty,
      );
    }
    _syncController(_httpProxyController, settingsState.httpProxyDraft);
    _syncController(_httpsProxyController, settingsState.httpsProxyDraft);
    _syncController(_allProxyController, settingsState.allProxyDraft);
    _syncController(_noProxyController, settingsState.noProxyDraft);

    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(28, 24, 28, 32),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(
                      strings.settingsTitle,
                      style: Theme.of(context).textTheme.headlineLarge,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      strings.settingsIntro,
                      style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              FilledButton.tonalIcon(
                onPressed: settingsState.isRefreshing ? null : settings.refresh,
                icon: const Icon(Icons.refresh_rounded),
                label: Text(strings.settingsRefresh),
              ),
            ],
          ),
          if (settingsState.lastError != null) ...<Widget>[
            const SizedBox(height: 16),
            _SettingsMessageBanner(
              title: strings.settingsErrorTitle,
              message: settingsState.lastError!,
              color: scheme.errorContainer,
              foreground: scheme.onErrorContainer,
            ),
          ],
          if (settingsState.infoMessage != null) ...<Widget>[
            const SizedBox(height: 16),
            _SettingsMessageBanner(
              title: strings.settingsTitle,
              message: settingsState.infoMessage!,
              color: scheme.primaryContainer,
              foreground: scheme.onPrimaryContainer,
            ),
          ],
          const SizedBox(height: 16),
          _SettingsAccountCard(state: settingsState, controller: settings),
          const SizedBox(height: 16),
          _SettingsBuildCard(
            state: settingsState,
            controller: settings,
            downloadDirController: _downloadDirController,
          ),
          const SizedBox(height: 16),
          _SettingsProxyCard(
            state: settingsState,
            controller: settings,
            httpProxyController: _httpProxyController,
            httpsProxyController: _httpsProxyController,
            allProxyController: _allProxyController,
            noProxyController: _noProxyController,
          ),
          const SizedBox(height: 16),
          _SettingsDiagnosticsCard(
            state: settingsState,
            controller: settings,
            dashboardState: dashboard,
          ),
          const SizedBox(height: 16),
          _SettingsAboutCard(
            settingsState: settingsState,
            dashboardState: dashboard,
          ),
        ],
      ),
    );
  }

  void _syncController(TextEditingController controller, String value) {
    if (controller.text == value) return;
    controller.value = controller.value.copyWith(
      text: value,
      selection: TextSelection.collapsed(offset: value.length),
      composing: TextRange.empty,
    );
  }
}

class _SettingsAccountCard extends StatelessWidget {
  const _SettingsAccountCard({required this.state, required this.controller});

  final SettingsPageState state;
  final SettingsPageController controller;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final session = state.session;
    final restoringSession = state.isRefreshing && session == null;
    return PanelCard(
      title: strings.settingsAccountTitle,
      subtitle: strings.settingsAccountSubtitle,
      icon: Icons.account_circle_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          if (restoringSession) ...<Widget>[
            Row(
              children: <Widget>[
                const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2.2),
                ),
                const SizedBox(width: 12),
                Text(strings.buildSessionRestoring),
              ],
            ),
            const SizedBox(height: 14),
          ],
          if (!restoringSession) ...<Widget>[
            Wrap(
              spacing: 10,
              runSpacing: 10,
              children: <Widget>[
                StatusPill(
                  label: session?.loggedIn == true
                      ? '${strings.buildLoggedInAs} ${session?.userLogin ?? ''}'
                      : strings.settingsNotLoggedIn,
                  color: session?.loggedIn == true
                      ? Theme.of(context).colorScheme.primary
                      : Theme.of(context).colorScheme.outline,
                  icon: session?.loggedIn == true
                      ? Icons.person_rounded
                      : Icons.person_off_rounded,
                ),
                if (session?.forkFullName != null)
                  StatusPill(
                    label: session!.forkFullName!,
                    color: Theme.of(context).colorScheme.secondary,
                    icon: Icons.fork_right_rounded,
                  ),
              ],
            ),
            const SizedBox(height: 14),
          ],
          if (!restoringSession && session?.loggedIn == true)
            Wrap(
              spacing: 12,
              runSpacing: 12,
              children: <Widget>[
                if (session?.forkFullName != null)
                  FilledButton.tonalIcon(
                    onPressed: () => _openUrl(
                      'https://github.com/${session!.forkFullName!}',
                    ),
                    icon: const Icon(Icons.open_in_browser_rounded),
                    label: Text(strings.settingsOpenFork),
                  ),
                FilledButton(
                  onPressed: state.logoutBusy ? null : controller.logout,
                  child: state.logoutBusy
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2.2),
                        )
                      : Text(strings.settingsLogout),
                ),
              ],
            ),
        ],
      ),
    );
  }
}

class _SettingsBuildCard extends StatefulWidget {
  const _SettingsBuildCard({
    required this.state,
    required this.controller,
    required this.downloadDirController,
  });

  final SettingsPageState state;
  final SettingsPageController controller;
  final TextEditingController downloadDirController;

  @override
  State<_SettingsBuildCard> createState() => _SettingsBuildCardState();
}

class _SettingsBuildCardState extends State<_SettingsBuildCard> {
  Future<void> _pickDirectory() async {
    final path = await _pickDirectoryPath();
    if (path == null || path.isEmpty) return;
    widget.controller.updateDownloadDirDraft(path);
  }

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    return PanelCard(
      title: strings.settingsBuildTitle,
      subtitle: strings.settingsBuildSubtitle,
      icon: Icons.download_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          TextField(
            controller: widget.downloadDirController,
            onChanged: widget.controller.updateDownloadDirDraft,
            decoration: InputDecoration(labelText: strings.settingsDownloadDir),
          ),
          const SizedBox(height: 12),
          Row(
            children: <Widget>[
              FilledButton.tonalIcon(
                onPressed: _pickDirectory,
                icon: const Icon(Icons.folder_open_rounded),
                label: Text(strings.settingsChooseDirectory),
              ),
              const SizedBox(width: 12),
              FilledButton(
                onPressed: widget.state.saveDownloadDirBusy
                    ? null
                    : widget.controller.saveDownloadDirectory,
                child: widget.state.saveDownloadDirBusy
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2.2),
                      )
                    : Text(strings.settingsSaveDirectory),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _SettingsProxyCard extends StatelessWidget {
  const _SettingsProxyCard({
    required this.state,
    required this.controller,
    required this.httpProxyController,
    required this.httpsProxyController,
    required this.allProxyController,
    required this.noProxyController,
  });

  final SettingsPageState state;
  final SettingsPageController controller;
  final TextEditingController httpProxyController;
  final TextEditingController httpsProxyController;
  final TextEditingController allProxyController;
  final TextEditingController noProxyController;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    return PanelCard(
      title: strings.settingsProxyTitle,
      subtitle: strings.settingsProxySubtitle,
      icon: Icons.settings_ethernet_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          TextField(
            controller: httpProxyController,
            onChanged: controller.updateHttpProxyDraft,
            decoration: InputDecoration(labelText: strings.settingsHttpProxy),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: httpsProxyController,
            onChanged: controller.updateHttpsProxyDraft,
            decoration: InputDecoration(labelText: strings.settingsHttpsProxy),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: allProxyController,
            onChanged: controller.updateAllProxyDraft,
            decoration: InputDecoration(labelText: strings.settingsAllProxy),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: noProxyController,
            onChanged: controller.updateNoProxyDraft,
            decoration: InputDecoration(labelText: strings.settingsNoProxy),
          ),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: state.saveProxyBusy ? null : controller.saveProxySettings,
            child: state.saveProxyBusy
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2.2),
                  )
                : Text(strings.settingsSaveProxy),
          ),
        ],
      ),
    );
  }
}

class _SettingsDiagnosticsCard extends StatelessWidget {
  const _SettingsDiagnosticsCard({
    required this.state,
    required this.controller,
    required this.dashboardState,
  });

  final SettingsPageState state;
  final SettingsPageController controller;
  final DashboardState dashboardState;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final task = state.latestDiagnosticsTask;
    final availability = _resolveAvailability(strings, dashboardState);
    return PanelCard(
      title: strings.settingsDiagnosticsTitle,
      subtitle: strings.settingsDiagnosticsSubtitle,
      icon: Icons.bug_report_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          if (availability.message != null) ...<Widget>[
            Container(
              decoration: BoxDecoration(
                color: Theme.of(
                  context,
                ).colorScheme.surfaceContainerHighest.withValues(alpha: 0.24),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(
                  color: Theme.of(
                    context,
                  ).colorScheme.outlineVariant.withValues(alpha: 0.28),
                ),
              ),
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              child: Row(
                children: <Widget>[
                  availability.showSpinner
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2.2),
                        )
                      : Icon(
                          availability.icon,
                          size: 18,
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                  const SizedBox(width: 12),
                  Expanded(child: Text(availability.message!)),
                ],
              ),
            ),
            const SizedBox(height: 14),
          ],
          FilledButton.tonalIcon(
            onPressed: state.exportDiagnosticsBusy || !availability.canExport
                ? null
                : controller.exportDiagnostics,
            icon: state.exportDiagnosticsBusy
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2.2),
                  )
                : const Icon(Icons.archive_rounded),
            label: Text(strings.settingsExportDiagnostics),
          ),
          const SizedBox(height: 14),
          if (task == null)
            Text(strings.settingsNoDiagnosticsTask)
          else
            _SettingsTaskTile(task: task),
          if (task != null && task.isTerminal) ...<Widget>[
            const SizedBox(height: 12),
            FilledButton.tonalIcon(
              onPressed: () =>
                  _openUrl(controller.api.taskDownloadUri(task.id).toString()),
              icon: const Icon(Icons.download_rounded),
              label: Text(strings.settingsDownloadDiagnostic),
            ),
          ],
        ],
      ),
    );
  }

  _SettingsDiagnosticsAvailability _resolveAvailability(
    AppStrings strings,
    DashboardState dashboard,
  ) {
    if (dashboard.isRefreshing && dashboard.health == null) {
      return _SettingsDiagnosticsAvailability(
        canExport: false,
        message: strings.settingsDiagnosticsChecking,
        showSpinner: true,
        icon: Icons.sync_rounded,
      );
    }

    final connection = dashboard.connection;
    final connectedToAbk =
        connection != null &&
        connection.connected &&
        connection.mode == DeviceConnectionMode.abk;
    if (!connectedToAbk) {
      return _SettingsDiagnosticsAvailability(
        canExport: false,
        message: strings.settingsDiagnosticsRequiresAbk,
        showSpinner: false,
        icon: Icons.usb_off_rounded,
      );
    }

    final agent = dashboard.health?.agent;
    if (agent == null) {
      return _SettingsDiagnosticsAvailability(
        canExport: false,
        message: strings.settingsDiagnosticsChecking,
        showSpinner: true,
        icon: Icons.sync_rounded,
      );
    }

    if (!agent.supports('diagnostics.export')) {
      return _SettingsDiagnosticsAvailability(
        canExport: false,
        message: strings.settingsDiagnosticsUnsupported,
        showSpinner: false,
        icon: Icons.system_update_alt_rounded,
      );
    }

    return const _SettingsDiagnosticsAvailability(
      canExport: true,
      message: null,
      showSpinner: false,
      icon: Icons.archive_rounded,
    );
  }
}

class _SettingsTaskTile extends StatelessWidget {
  const _SettingsTaskTile({required this.task});

  final DesktopTaskSnapshot task;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      decoration: BoxDecoration(
        color: scheme.surfaceContainerHighest.withValues(alpha: 0.28),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: scheme.outlineVariant.withValues(alpha: 0.32),
        ),
      ),
      padding: const EdgeInsets.all(12),
      child: Row(
        children: <Widget>[
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(
                  context.strings.buildTaskLabel(task.kind),
                  style: Theme.of(context).textTheme.titleSmall,
                ),
                const SizedBox(height: 4),
                Text(task.message ?? task.id),
              ],
            ),
          ),
          StatusPill(
            label: context.strings.buildTaskStateLabel(task.state),
            color: switch (task.state) {
              'succeeded' => scheme.primary,
              'failed' => scheme.error,
              'running' => scheme.secondary,
              _ => scheme.outline,
            },
            icon: task.isTerminal
                ? Icons.check_circle_rounded
                : Icons.sync_rounded,
          ),
        ],
      ),
    );
  }
}

class _SettingsDiagnosticsAvailability {
  const _SettingsDiagnosticsAvailability({
    required this.canExport,
    required this.message,
    required this.showSpinner,
    required this.icon,
  });

  final bool canExport;
  final String? message;
  final bool showSpinner;
  final IconData icon;
}

class _SettingsMessageBanner extends StatelessWidget {
  const _SettingsMessageBanner({
    required this.title,
    required this.message,
    required this.color,
    required this.foreground,
  });

  final String title;
  final String message;
  final Color color;
  final Color foreground;

  @override
  Widget build(BuildContext context) {
    return PanelCard(
      title: title,
      subtitle: message,
      icon: Icons.info_rounded,
      backgroundColor: color,
      foregroundColor: foreground,
      subtitleColor: foreground.withValues(alpha: 0.82),
      borderColor: color,
      iconBackgroundColor: foreground.withValues(alpha: 0.12),
      iconColor: foreground,
      child: const SizedBox.shrink(),
    );
  }
}

Future<void> _openUrl(String url) async {
  await Process.start('xdg-open', <String>[url]);
}

class _SettingsAboutCard extends StatelessWidget {
  const _SettingsAboutCard({
    required this.settingsState,
    required this.dashboardState,
  });

  final SettingsPageState settingsState;
  final DashboardState dashboardState;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final health = dashboardState.health;
    return PanelCard(
      title: strings.settingsAboutTitle,
      subtitle: strings.settingsAboutSubtitle,
      icon: Icons.info_outline_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: <Widget>[
              StatusPill(
                label: health == null
                    ? strings.sidecarNotResponding
                    : strings.sidecarResponding(
                        health.sidecar.host,
                        health.sidecar.port,
                      ),
                color: Theme.of(context).colorScheme.primary,
                icon: Icons.router_rounded,
              ),
              if (health != null)
                StatusPill(
                  label: health.protocolVersion,
                  color: Theme.of(context).colorScheme.secondary,
                  icon: Icons.memory_rounded,
                ),
            ],
          ),
          const SizedBox(height: 14),
          if (settingsState.session?.repo != null)
            FilledButton.tonalIcon(
              onPressed: () =>
                  _openUrl('https://github.com/${settingsState.session!.repo}'),
              icon: const Icon(Icons.open_in_browser_rounded),
              label: Text(strings.settingsOpenRepo),
            ),
        ],
      ),
    );
  }
}

Future<String?> _pickDirectoryPath() async {
  final zenity = await Process.run('sh', <String>[
    '-lc',
    'command -v zenity >/dev/null 2>&1 && zenity --file-selection --directory',
  ]);
  final zenityPath = (zenity.stdout as String).trim();
  if (zenity.exitCode == 0 && zenityPath.isNotEmpty) {
    return zenityPath;
  }

  final kdialog = await Process.run('sh', <String>[
    '-lc',
    'command -v kdialog >/dev/null 2>&1 && kdialog --getexistingdirectory .',
  ]);
  final kdialogPath = (kdialog.stdout as String).trim();
  if (kdialog.exitCode == 0 && kdialogPath.isNotEmpty) {
    return kdialogPath;
  }
  return null;
}
