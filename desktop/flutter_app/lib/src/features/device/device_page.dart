import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api/abk_sidecar_api.dart';
import '../../core/localization/app_strings.dart';
import '../../core/models/build_models.dart';
import '../../core/models/device_models.dart';
import '../../core/models/sidecar_models.dart';
import '../../core/platform/desktop_webui_api.dart';
import '../../core/state/dashboard_controller.dart';
import '../../widgets/monaco_code_view.dart';
import '../../widgets/panel_card.dart';
import '../../widgets/status_pill.dart';
import 'device_page_controller.dart';
import 'runtime_module_catalog.dart';
import 'susfs_form.dart';

class DevicePage extends ConsumerStatefulWidget {
  const DevicePage({super.key});

  @override
  ConsumerState<DevicePage> createState() => _DevicePageState();
}

class _DevicePageState extends ConsumerState<DevicePage> {
  bool _requestedInitialLoad = false;

  @override
  Widget build(BuildContext context) {
    final dashboard = ref.watch(dashboardControllerProvider);
    final deviceState = ref.watch(devicePageControllerProvider);
    final controller = ref.read(devicePageControllerProvider.notifier);
    final strings = context.strings;
    final scheme = Theme.of(context).colorScheme;
    final abkReady =
        dashboard.connection?.connected == true &&
        dashboard.connection?.mode == DeviceConnectionMode.abk;

    if (abkReady && !_requestedInitialLoad) {
      _requestedInitialLoad = true;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        controller.refreshAll();
      });
    }
    if (!abkReady) {
      _requestedInitialLoad = false;
    }

    return DefaultTabController(
      length: 3,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Padding(
            padding: const EdgeInsets.fromLTRB(28, 24, 28, 0),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text(
                        strings.deviceTitle,
                        style: Theme.of(context).textTheme.headlineLarge,
                      ),
                      const SizedBox(height: 8),
                      Text(
                        strings.deviceIntro,
                        style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                          color: scheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                FilledButton.tonalIcon(
                  onPressed: abkReady && !deviceState.isRefreshing
                      ? controller.refreshAll
                      : null,
                  icon: const Icon(Icons.refresh_rounded),
                  label: Text(strings.deviceRefreshAll),
                ),
              ],
            ),
          ),
          if (deviceState.lastError != null) ...<Widget>[
            Padding(
              padding: const EdgeInsets.fromLTRB(28, 16, 28, 0),
              child: _MessageBanner(
                title: strings.errorCardTitle,
                message: deviceState.lastError!,
                color: scheme.errorContainer,
                foreground: scheme.onErrorContainer,
              ),
            ),
          ],
          if (deviceState.infoMessage != null) ...<Widget>[
            Padding(
              padding: const EdgeInsets.fromLTRB(28, 16, 28, 0),
              child: _MessageBanner(
                title: strings.deviceTaskTitle,
                message: deviceState.infoMessage!,
                color: scheme.primaryContainer,
                foreground: scheme.onPrimaryContainer,
              ),
            ),
          ],
          const SizedBox(height: 16),
          if (!abkReady)
            Expanded(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(28, 0, 28, 28),
                child: _BlockedDeviceState(
                  dashboard: dashboard,
                  onOpenDetection: () => context.go('/detect'),
                ),
              ),
            )
          else
            Expanded(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(28, 0, 28, 28),
                child: Column(
                  children: <Widget>[
                    Align(
                      alignment: Alignment.centerLeft,
                      child: TabBar(
                        isScrollable: true,
                        tabAlignment: TabAlignment.start,
                        tabs: <Tab>[
                          Tab(text: strings.deviceTabRoot),
                          Tab(text: strings.deviceTabModules),
                          Tab(text: strings.deviceTabKernel),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),
                    Expanded(
                      child: TabBarView(
                        children: <Widget>[
                          _RootGrantsTab(
                            state: deviceState,
                            controller: controller,
                          ),
                          _ModulesTab(
                            state: deviceState,
                            controller: controller,
                          ),
                          _KernelTab(
                            state: deviceState,
                            controller: controller,
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _BlockedDeviceState extends StatelessWidget {
  const _BlockedDeviceState({
    required this.dashboard,
    required this.onOpenDetection,
  });

  final DashboardState dashboard;
  final VoidCallback onOpenDetection;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final scheme = Theme.of(context).colorScheme;
    return PanelCard(
      title: strings.deviceBlockedTitle,
      subtitle: strings.deviceBlockedSubtitle,
      icon: Icons.phonelink_erase_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: <Widget>[
              StatusPill(
                label: strings.connectionStatusLabel(dashboard.flow),
                color: scheme.primary,
                icon: Icons.route_rounded,
              ),
              StatusPill(
                label: strings.connectionModeLabel(
                  dashboard.connection?.mode ??
                      DeviceConnectionMode.disconnected,
                ),
                color: scheme.secondary,
                icon: Icons.usb_rounded,
              ),
            ],
          ),
          const SizedBox(height: 16),
          FilledButton.tonalIcon(
            onPressed: onOpenDetection,
            icon: const Icon(Icons.open_in_new_rounded),
            label: Text(strings.deviceOpenDetection),
          ),
        ],
      ),
    );
  }
}

class _MessageBanner extends StatelessWidget {
  const _MessageBanner({
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

class _RootGrantsTab extends ConsumerStatefulWidget {
  const _RootGrantsTab({required this.state, required this.controller});

  final DevicePageState state;
  final DevicePageController controller;

  @override
  ConsumerState<_RootGrantsTab> createState() => _RootGrantsTabState();
}

class _RootGrantsTabState extends ConsumerState<_RootGrantsTab> {
  String _query = '';
  bool _showSystemApps = false;
  String? _selectedPackage;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final api = ref.read(sidecarApiProvider);
    final rootGrants = widget.state.rootGrants;
    final apps =
        rootGrants?.apps
            .where((app) {
              if (!_showSystemApps && app.isSystemApp) {
                return false;
              }
              final needle = _query.trim().toLowerCase();
              if (needle.isEmpty) return true;
              return app.label.toLowerCase().contains(needle) ||
                  app.packageName.toLowerCase().contains(needle) ||
                  app.uid.toString().contains(needle);
            })
            .toList(growable: false) ??
        const <RootGrantApp>[];
    final selectedApp = _selectedPackage == null
        ? null
        : apps.where((app) => app.packageName == _selectedPackage).firstOrNull;
    if (selectedApp != null &&
        !widget.state.packageInfoByPackage.containsKey(
          selectedApp.packageName,
        ) &&
        widget.state.packageInfoLoadingPackage != selectedApp.packageName) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        widget.controller.loadPackageInfo(selectedApp.packageName);
      });
    }
    final packageInfo = selectedApp == null
        ? null
        : widget.state.packageInfoByPackage[selectedApp.packageName];
    final wide = MediaQuery.sizeOf(context).width >= 1200;

    final listPanel = PanelCard(
      title: strings.deviceRootListTitle,
      subtitle: strings.deviceRootListSubtitle,
      icon: Icons.admin_panel_settings_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          TextField(
            decoration: InputDecoration(
              labelText: strings.deviceRootSearch,
              prefixIcon: const Icon(Icons.search_rounded),
            ),
            onChanged: (value) => setState(() => _query = value),
          ),
          const SizedBox(height: 12),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(strings.deviceRootShowSystem),
            value: _showSystemApps,
            onChanged: (value) => setState(() => _showSystemApps = value),
          ),
          const SizedBox(height: 8),
          if (widget.state.rootGrantLoading && apps.isEmpty)
            const Center(child: CircularProgressIndicator())
          else if (rootGrants == null || apps.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 16),
              child: Text(strings.deviceRootNoApps),
            )
          else
            Column(
              children: apps
                  .map(
                    (app) => Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: _RootGrantListTile(
                        api: api,
                        app: app,
                        selected: app.packageName == _selectedPackage,
                        onTap: () => setState(() {
                          _selectedPackage = app.packageName;
                        }),
                      ),
                    ),
                  )
                  .toList(growable: false),
            ),
        ],
      ),
    );

    final detailPanel = PanelCard(
      title: strings.deviceRootDetailTitle,
      subtitle: selectedApp?.label ?? strings.deviceRootDetailEmpty,
      icon: Icons.account_circle_rounded,
      child: selectedApp == null
          ? Text(strings.deviceRootDetailEmpty)
          : Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children: <Widget>[
                    StatusPill(
                      label: selectedApp.packageName,
                      color: Theme.of(context).colorScheme.primary,
                      icon: Icons.apps_rounded,
                    ),
                    StatusPill(
                      label: 'UID ${selectedApp.uid}',
                      color: Theme.of(context).colorScheme.secondary,
                      icon: Icons.tag_rounded,
                    ),
                    if (selectedApp.isSystemApp)
                      StatusPill(
                        label: strings.deviceRootShowSystem,
                        color: Theme.of(context).colorScheme.tertiary,
                        icon: Icons.security_rounded,
                      ),
                  ],
                ),
                if (packageInfo != null) ...<Widget>[
                  const SizedBox(height: 14),
                  Text(
                    '${packageInfo.appLabel} · ${packageInfo.versionName} (${packageInfo.versionCode})',
                  ),
                ],
                const SizedBox(height: 14),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(strings.deviceRootAllow),
                  value: selectedApp.profile.allowSu,
                  onChanged:
                      widget.state.rootGrantSavingPackage ==
                          selectedApp.packageName
                      ? null
                      : (value) => widget.controller.setRootGrantAllowed(
                          selectedApp.packageName,
                          value,
                        ),
                ),
                if (widget.state.packageInfoLoadingPackage ==
                    selectedApp.packageName)
                  const Padding(
                    padding: EdgeInsets.only(top: 8),
                    child: CircularProgressIndicator(),
                  ),
              ],
            ),
    );

    if (!wide) {
      return SingleChildScrollView(
        child: Column(
          children: <Widget>[
            listPanel,
            const SizedBox(height: 16),
            detailPanel,
          ],
        ),
      );
    }
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Expanded(flex: 6, child: SingleChildScrollView(child: listPanel)),
        const SizedBox(width: 16),
        Expanded(flex: 4, child: SingleChildScrollView(child: detailPanel)),
      ],
    );
  }
}

class _RootGrantListTile extends StatelessWidget {
  const _RootGrantListTile({
    required this.api,
    required this.app,
    required this.selected,
    required this.onTap,
  });

  final AbkSidecarApi api;
  final RootGrantApp app;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: onTap,
        child: Container(
          decoration: BoxDecoration(
            color: selected
                ? scheme.primaryContainer.withValues(alpha: 0.7)
                : scheme.surfaceContainerHighest.withValues(alpha: 0.28),
            borderRadius: BorderRadius.circular(18),
            border: Border.all(
              color: selected
                  ? scheme.primary
                  : scheme.outlineVariant.withValues(alpha: 0.34),
            ),
          ),
          padding: const EdgeInsets.all(12),
          child: Row(
            children: <Widget>[
              _RootGrantIcon(api: api, packageName: app.packageName),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(
                      app.label,
                      style: Theme.of(context).textTheme.titleSmall,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${app.packageName} · UID ${app.uid}',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              StatusPill(
                label: app.profile.allowSu
                    ? context.strings.deviceRootAllow
                    : context.strings.deviceRootDenied,
                color: app.profile.allowSu ? scheme.primary : scheme.outline,
                icon: app.profile.allowSu
                    ? Icons.check_circle_rounded
                    : Icons.circle_outlined,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _RootGrantIcon extends StatefulWidget {
  const _RootGrantIcon({required this.api, required this.packageName});

  final AbkSidecarApi api;
  final String packageName;

  @override
  State<_RootGrantIcon> createState() => _RootGrantIconState();
}

class _RootGrantIconState extends State<_RootGrantIcon> {
  Future<Uint8List?>? _future;

  @override
  void initState() {
    super.initState();
    _future = widget.api.getRootGrantIcon(widget.packageName);
  }

  @override
  void didUpdateWidget(covariant _RootGrantIcon oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.packageName != widget.packageName) {
      _future = widget.api.getRootGrantIcon(widget.packageName);
    }
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<Uint8List?>(
      future: _future,
      builder: (context, snapshot) {
        final bytes = snapshot.data;
        return ClipRRect(
          borderRadius: BorderRadius.circular(12),
          child: Container(
            width: 40,
            height: 40,
            color: Theme.of(
              context,
            ).colorScheme.primaryContainer.withValues(alpha: 0.8),
            child: bytes == null
                ? const Icon(Icons.apps_rounded)
                : Image.memory(bytes, fit: BoxFit.cover),
          ),
        );
      },
    );
  }
}

class _ModulesTab extends ConsumerStatefulWidget {
  const _ModulesTab({required this.state, required this.controller});

  final DevicePageState state;
  final DevicePageController controller;

  @override
  ConsumerState<_ModulesTab> createState() => _ModulesTabState();
}

class _ModulesTabState extends ConsumerState<_ModulesTab> {
  String _installedQuery = '';
  String _repositoryQuery = '';

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final api = ref.read(sidecarApiProvider);
    final filteredInstalledModules = widget.state.installedModules
        .where((module) {
          final needle = _installedQuery.trim().toLowerCase();
          if (needle.isEmpty) return true;
          return [
            module.id,
            module.name,
            module.author,
            module.description,
            module.groupName,
            module.groupDescription,
          ].join(' ').toLowerCase().contains(needle);
        })
        .toList(growable: false);
    final standardModules = filteredInstalledModules
        .where((module) => module.isStandardRuntimeModule)
        .toList(growable: false);
    final customModules = filteredInstalledModules
        .where((module) => module.isCustomModule)
        .toList(growable: false);
    final moduleSetGroups =
        filteredInstalledModules
            .where((module) => module.isCustomModuleSetChild)
            .fold<Map<String, List<AbkRuntimeModule>>>(
              <String, List<AbkRuntimeModule>>{},
              (groups, module) {
                groups.putIfAbsent(
                  module.moduleGroupKey,
                  () => <AbkRuntimeModule>[],
                );
                groups[module.moduleGroupKey]!.add(module);
                return groups;
              },
            )
            .values
            .map(
              (modules) => modules.toList(growable: false)
                ..sort(
                  (left, right) =>
                      left.displayName.compareTo(right.displayName),
                ),
            )
            .toList(growable: false)
          ..sort(
            (left, right) => left.first.groupName
                .trim()
                .toLowerCase()
                .compareTo(right.first.groupName.trim().toLowerCase()),
          );
    return DefaultTabController(
      length: 3,
      child: Column(
        children: <Widget>[
          Align(
            alignment: Alignment.centerLeft,
            child: TabBar(
              isScrollable: true,
              tabAlignment: TabAlignment.start,
              tabs: <Tab>[
                Tab(text: strings.deviceModuleTabInstalled),
                Tab(text: strings.deviceModuleTabRepository),
                Tab(text: strings.deviceModuleTabLocalInstall),
              ],
            ),
          ),
          const SizedBox(height: 16),
          Expanded(
            child: TabBarView(
              children: <Widget>[
                SingleChildScrollView(
                  child: Column(
                    children: <Widget>[
                      TextField(
                        decoration: InputDecoration(
                          labelText: strings.deviceModuleSearch,
                          prefixIcon: const Icon(Icons.search_rounded),
                        ),
                        onChanged: (value) =>
                            setState(() => _installedQuery = value),
                      ),
                      const SizedBox(height: 12),
                      _InstalledModuleSection(
                        title: strings.deviceModuleStandardTitle,
                        subtitle: strings.deviceModuleStandardSubtitle,
                        emptyLabel: strings.deviceModuleNoStandard,
                        modules: standardModules,
                        state: widget.state,
                        api: api,
                        controller: widget.controller,
                      ),
                      const SizedBox(height: 12),
                      _InstalledModuleSection(
                        title: strings.deviceModuleCustomTitle,
                        subtitle: strings.deviceModuleCustomSubtitle,
                        emptyLabel: strings.deviceModuleNoCustom,
                        modules: customModules,
                        state: widget.state,
                        api: api,
                        controller: widget.controller,
                      ),
                      const SizedBox(height: 12),
                      _ModuleSetSection(
                        title: strings.deviceModuleSetTitle,
                        subtitle: strings.deviceModuleSetSubtitle,
                        emptyLabel: strings.deviceModuleNoModuleSets,
                        groups: moduleSetGroups,
                        state: widget.state,
                        api: api,
                        controller: widget.controller,
                      ),
                      const SizedBox(height: 16),
                      _DeviceTasksCard(state: widget.state),
                    ],
                  ),
                ),
                SingleChildScrollView(
                  child: Column(
                    children: <Widget>[
                      PanelCard(
                        title: strings.deviceModuleRuntimeRepoTitle,
                        subtitle: strings.deviceModuleRuntimeRepoSubtitle,
                        icon: Icons.library_books_rounded,
                        child: Column(
                          children: <Widget>[
                            Row(
                              children: <Widget>[
                                Expanded(
                                  child: TextField(
                                    decoration: InputDecoration(
                                      labelText: strings.deviceModuleRepoUrl,
                                    ),
                                    onChanged: widget
                                        .controller
                                        .updateRepositoryUrlDraft,
                                    controller: TextEditingController(
                                      text: widget.state.repositoryUrlDraft,
                                    ),
                                  ),
                                ),
                                const SizedBox(width: 12),
                                FilledButton.tonal(
                                  onPressed: widget.state.repositoryLoading
                                      ? null
                                      : widget
                                            .controller
                                            .addRuntimeModuleRepository,
                                  child: Text(strings.deviceModuleAddRepo),
                                ),
                              ],
                            ),
                            const SizedBox(height: 12),
                            TextField(
                              decoration: InputDecoration(
                                labelText: strings.deviceModuleSearch,
                                prefixIcon: const Icon(Icons.search_rounded),
                              ),
                              onChanged: (value) =>
                                  setState(() => _repositoryQuery = value),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 12),
                      ...widget.state.runtimeModuleRepositories.map(
                        (repository) => Padding(
                          padding: const EdgeInsets.only(bottom: 12),
                          child: _RepositoryCard(
                            repository: repository,
                            state: widget.state,
                            query: _repositoryQuery,
                            onRefresh: () => widget.controller
                                .refreshRuntimeModuleRepository(repository.id),
                            onDelete:
                                repository.id ==
                                    officialRuntimeModuleRepositoryId
                                ? null
                                : () => widget.controller
                                      .removeRuntimeModuleRepository(
                                        repository.id,
                                      ),
                            onOpenModule: (module) {
                              final url = module.module.website.isNotEmpty
                                  ? module.module.website
                                  : (module.module.support.isNotEmpty
                                        ? module.module.support
                                        : module.module.zipUrl);
                              if (url.isNotEmpty) {
                                _openUrl(url);
                              }
                            },
                            onInstallModule: (module) => widget.controller
                                .installRepositoryModule(module),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                _LocalInstallTab(
                  controller: widget.controller,
                  state: widget.state,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _InstalledModuleSection extends StatelessWidget {
  const _InstalledModuleSection({
    required this.title,
    required this.subtitle,
    required this.emptyLabel,
    required this.modules,
    required this.state,
    required this.api,
    required this.controller,
  });

  final String title;
  final String subtitle;
  final String emptyLabel;
  final List<AbkRuntimeModule> modules;
  final DevicePageState state;
  final AbkSidecarApi api;
  final DevicePageController controller;

  @override
  Widget build(BuildContext context) {
    return PanelCard(
      title: title,
      subtitle: subtitle,
      icon: Icons.widgets_rounded,
      child: modules.isEmpty
          ? Text(emptyLabel)
          : Column(
              children: modules
                  .map(
                    (module) => Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: _InstalledModuleCard(
                        module: module,
                        state: state,
                        api: api,
                        onEnabledChange: (enabled) => controller
                            .setRuntimeModuleEnabled(module.id, enabled),
                        onPendingUninstallChange: (pending) =>
                            controller.setRuntimeModulePendingUninstall(
                              module.id,
                              pending,
                            ),
                        onRunAction:
                            module.actionSupported || module.hasActionScript
                            ? () => controller.runRuntimeModuleAction(module.id)
                            : null,
                      ),
                    ),
                  )
                  .toList(growable: false),
            ),
    );
  }
}

class _ModuleSetSection extends StatelessWidget {
  const _ModuleSetSection({
    required this.title,
    required this.subtitle,
    required this.emptyLabel,
    required this.groups,
    required this.state,
    required this.api,
    required this.controller,
  });

  final String title;
  final String subtitle;
  final String emptyLabel;
  final List<List<AbkRuntimeModule>> groups;
  final DevicePageState state;
  final AbkSidecarApi api;
  final DevicePageController controller;

  @override
  Widget build(BuildContext context) {
    return PanelCard(
      title: title,
      subtitle: subtitle,
      icon: Icons.view_module_rounded,
      child: groups.isEmpty
          ? Text(emptyLabel)
          : Column(
              children: groups
                  .map(
                    (group) => Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: _ModuleSetGroupCard(
                        modules: group,
                        state: state,
                        api: api,
                        controller: controller,
                      ),
                    ),
                  )
                  .toList(growable: false),
            ),
    );
  }
}

class _ModuleSetGroupCard extends StatelessWidget {
  const _ModuleSetGroupCard({
    required this.modules,
    required this.state,
    required this.api,
    required this.controller,
  });

  final List<AbkRuntimeModule> modules;
  final DevicePageState state;
  final AbkSidecarApi api;
  final DevicePageController controller;

  @override
  Widget build(BuildContext context) {
    final first = modules.first;
    final title = first.moduleSetDisplayName;
    final subtitle = first.groupDescription.trim().ifEmpty(first.groupRepoUrl);
    return Container(
      decoration: BoxDecoration(
        color: Theme.of(
          context,
        ).colorScheme.surfaceContainerHighest.withValues(alpha: 0.18),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(
          color: Theme.of(
            context,
          ).colorScheme.outlineVariant.withValues(alpha: 0.28),
        ),
      ),
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(title, style: Theme.of(context).textTheme.titleMedium),
          if (subtitle.trim().isNotEmpty) ...<Widget>[
            const SizedBox(height: 4),
            Text(subtitle, style: Theme.of(context).textTheme.bodySmall),
          ],
          const SizedBox(height: 12),
          ...modules.map(
            (module) => Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: _InstalledModuleCard(
                module: module,
                state: state,
                api: api,
                onEnabledChange: (enabled) =>
                    controller.setRuntimeModuleEnabled(module.id, enabled),
                onPendingUninstallChange: (pending) => controller
                    .setRuntimeModulePendingUninstall(module.id, pending),
                onRunAction: module.actionSupported || module.hasActionScript
                    ? () => controller.runRuntimeModuleAction(module.id)
                    : null,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _InstalledModuleCard extends StatelessWidget {
  const _InstalledModuleCard({
    required this.module,
    required this.state,
    required this.api,
    required this.onEnabledChange,
    required this.onPendingUninstallChange,
    required this.onRunAction,
  });

  final AbkRuntimeModule module;
  final DevicePageState state;
  final AbkSidecarApi api;
  final ValueChanged<bool> onEnabledChange;
  final ValueChanged<bool> onPendingUninstallChange;
  final VoidCallback? onRunAction;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final scheme = Theme.of(context).colorScheme;
    final canOpenWebUi =
        module.hasWebUi && module.enabled && !module.remove && !module.update;
    return PanelCard(
      title: module.displayName,
      subtitle: module.description.ifEmpty(module.id),
      icon: Icons.extension_rounded,
      actions: <Widget>[
        if (module.hasWebUi)
          IconButton(
            onPressed: canOpenWebUi
                ? () => _openModuleWebUi(api, module)
                : null,
            icon: const Icon(Icons.open_in_browser_rounded),
            tooltip: strings.deviceModuleWebUiDesktop,
          ),
      ],
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: <Widget>[
              if (module.version.isNotEmpty)
                StatusPill(
                  label: 'v${module.version}',
                  color: scheme.primary,
                  icon: Icons.tag_rounded,
                ),
              if (module.author.isNotEmpty)
                StatusPill(
                  label: module.author,
                  color: scheme.secondary,
                  icon: Icons.person_rounded,
                ),
              if (module.readonly)
                StatusPill(
                  label: 'readonly',
                  color: scheme.outline,
                  icon: Icons.lock_outline_rounded,
                ),
              StatusPill(
                label: module.normalizedType,
                color: scheme.secondary,
                icon: Icons.category_rounded,
              ),
              if (module.stage.trim().isNotEmpty)
                StatusPill(
                  label: module.stage.trim(),
                  color: scheme.tertiary,
                  icon: Icons.linear_scale_rounded,
                ),
              if (module.source.trim().isNotEmpty)
                StatusPill(
                  label: module.source.trim(),
                  color: scheme.surfaceTint,
                  icon: Icons.route_rounded,
                ),
              if (module.hasActionScript || module.actionSupported)
                StatusPill(
                  label: strings.deviceModuleAction,
                  color: scheme.tertiary,
                  icon: Icons.play_arrow_rounded,
                ),
            ],
          ),
          const SizedBox(height: 12),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(strings.deviceModuleEnable),
            value: module.enabled,
            onChanged:
                !module.controllable ||
                    module.readonly ||
                    state.moduleBusyIds.contains(module.id)
                ? null
                : onEnabledChange,
          ),
          if (module.remove)
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(strings.deviceModulePendingUninstall),
              value: true,
              onChanged: state.modulePendingBusyIds.contains(module.id)
                  ? null
                  : (value) => onPendingUninstallChange(value),
            ),
          Row(
            children: <Widget>[
              if (module.hasWebUi)
                FilledButton.tonalIcon(
                  onPressed: canOpenWebUi
                      ? () => _openModuleWebUi(api, module)
                      : null,
                  icon: const Icon(Icons.web_rounded),
                  label: Text(strings.deviceModuleWebUi),
                ),
              if (module.hasWebUi && onRunAction != null)
                const SizedBox(width: 12),
              if (onRunAction != null)
                FilledButton.tonalIcon(
                  onPressed: state.moduleActionBusyIds.contains(module.id)
                      ? null
                      : onRunAction,
                  icon: state.moduleActionBusyIds.contains(module.id)
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2.2),
                        )
                      : const Icon(Icons.play_arrow_rounded),
                  label: Text(strings.deviceModuleAction),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class _RepositoryCard extends StatelessWidget {
  const _RepositoryCard({
    required this.repository,
    required this.state,
    required this.query,
    required this.onRefresh,
    required this.onDelete,
    required this.onOpenModule,
    required this.onInstallModule,
  });

  final RuntimeModuleRepository repository;
  final DevicePageState state;
  final String query;
  final VoidCallback onRefresh;
  final VoidCallback? onDelete;
  final ValueChanged<MergedRuntimeCatalogModule> onOpenModule;
  final ValueChanged<MergedRuntimeCatalogModule> onInstallModule;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final modules = mergeRuntimeCatalogModules(<RuntimeModuleRepository>[
      repository,
    ]).where((module) => module.matchesQuery(query)).toList(growable: false);
    return PanelCard(
      title: repository.name,
      subtitle: repository.url,
      icon: Icons.library_books_rounded,
      actions: <Widget>[
        IconButton(
          onPressed: state.refreshingRepositoryIds.contains(repository.id)
              ? null
              : onRefresh,
          icon: state.refreshingRepositoryIds.contains(repository.id)
              ? const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2.2),
                )
              : const Icon(Icons.refresh_rounded),
        ),
        if (onDelete != null)
          IconButton(
            onPressed: onDelete,
            icon: const Icon(Icons.delete_outline_rounded),
          ),
      ],
      child: !repository.isReady
          ? Text(repository.error ?? strings.deviceModuleNoCatalogModules)
          : modules.isEmpty
          ? Text(strings.deviceModuleNoCatalogResults)
          : Column(
              children: modules
                  .map(
                    (module) => Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: Container(
                        decoration: BoxDecoration(
                          color: Theme.of(context)
                              .colorScheme
                              .surfaceContainerHighest
                              .withValues(alpha: 0.26),
                          borderRadius: BorderRadius.circular(16),
                        ),
                        padding: const EdgeInsets.all(12),
                        child: Row(
                          children: <Widget>[
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: <Widget>[
                                  Text(
                                    module.module.name,
                                    style: Theme.of(
                                      context,
                                    ).textTheme.titleSmall,
                                  ),
                                  if (module.module
                                      .metaLine()
                                      .isNotEmpty) ...<Widget>[
                                    const SizedBox(height: 4),
                                    Text(
                                      module.module.metaLine(),
                                      style: Theme.of(
                                        context,
                                      ).textTheme.bodySmall,
                                    ),
                                  ],
                                  if (module
                                      .module
                                      .description
                                      .isNotEmpty) ...<Widget>[
                                    const SizedBox(height: 4),
                                    Text(
                                      module.module.description,
                                      style: Theme.of(
                                        context,
                                      ).textTheme.bodySmall,
                                      maxLines: 2,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ],
                                ],
                              ),
                            ),
                            const SizedBox(width: 8),
                            IconButton(
                              onPressed: () => onOpenModule(module),
                              icon: const Icon(Icons.open_in_browser_rounded),
                              tooltip: strings.deviceModuleOpenRepo,
                            ),
                            FilledButton.tonal(
                              onPressed:
                                  state.installingCatalogModuleIds.contains(
                                    module.module.id.ifEmpty(
                                      module.module.zipUrl,
                                    ),
                                  )
                                  ? null
                                  : () => onInstallModule(module),
                              child:
                                  state.installingCatalogModuleIds.contains(
                                    module.module.id.ifEmpty(
                                      module.module.zipUrl,
                                    ),
                                  )
                                  ? const SizedBox(
                                      width: 18,
                                      height: 18,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2.2,
                                      ),
                                    )
                                  : Text(strings.deviceModuleInstall),
                            ),
                          ],
                        ),
                      ),
                    ),
                  )
                  .toList(growable: false),
            ),
    );
  }
}

class _LocalInstallTab extends StatefulWidget {
  const _LocalInstallTab({required this.controller, required this.state});

  final DevicePageController controller;
  final DevicePageState state;

  @override
  State<_LocalInstallTab> createState() => _LocalInstallTabState();
}

class _LocalInstallTabState extends State<_LocalInstallTab> {
  Future<void> _pickZip() async {
    final path = await _pickZipPath();
    if (path == null || path.isEmpty) return;
    widget.controller.updateLocalModulePath(path);
  }

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    return SingleChildScrollView(
      child: PanelCard(
        title: strings.deviceModuleTabLocalInstall,
        subtitle: strings.deviceModuleNoLocalZip,
        icon: Icons.upload_file_rounded,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            TextField(
              readOnly: true,
              controller: TextEditingController(
                text: widget.state.localModulePath ?? '',
              ),
              decoration: InputDecoration(
                labelText: strings.deviceModuleChooseZip,
              ),
            ),
            const SizedBox(height: 12),
            Row(
              children: <Widget>[
                FilledButton.tonalIcon(
                  onPressed: _pickZip,
                  icon: const Icon(Icons.folder_open_rounded),
                  label: Text(strings.deviceModuleChooseZip),
                ),
                const SizedBox(width: 12),
                FilledButton(
                  onPressed:
                      widget.state.localModulePath == null ||
                          widget.state.localInstallBusy
                      ? null
                      : widget.controller.installLocalModule,
                  child: widget.state.localInstallBusy
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2.2),
                        )
                      : Text(strings.deviceModuleInstall),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _KernelTab extends StatelessWidget {
  const _KernelTab({required this.state, required this.controller});

  final DevicePageState state;
  final DevicePageController controller;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final runtime = state.runtime?.runtimeStatus;
    final susfs = state.susfs;
    final kernelFeatures = state.kernelFeatures;
    return SingleChildScrollView(
      child: Column(
        children: <Widget>[
          PanelCard(
            title: strings.deviceKernelSummaryTitle,
            subtitle: strings.deviceKernelSummarySubtitle,
            icon: Icons.memory_rounded,
            child: runtime == null
                ? Text(strings.deviceKernelNoRuntime)
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Wrap(
                        spacing: 10,
                        runSpacing: 10,
                        children: <Widget>[
                          StatusPill(
                            label: runtime.abkVersion.isEmpty
                                ? strings.unknownValue
                                : runtime.abkVersion,
                            color: Theme.of(context).colorScheme.primary,
                            icon: Icons.info_rounded,
                          ),
                          StatusPill(
                            label:
                                runtime.manager?.displayName.isNotEmpty == true
                                ? runtime.manager!.displayName
                                : strings.unknownValue,
                            color: Theme.of(context).colorScheme.secondary,
                            icon: Icons.extension_rounded,
                          ),
                          StatusPill(
                            label: '${runtime.modules.length} modules',
                            color: Theme.of(context).colorScheme.tertiary,
                            icon: Icons.layers_rounded,
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      if (runtime.abkCommit.isNotEmpty) Text(runtime.abkCommit),
                      if (runtime.build != null) ...<Widget>[
                        const SizedBox(height: 8),
                        Text(
                          '${runtime.build!.androidVersion} · ${runtime.build!.kernelVersion} · ${runtime.build!.subLevel} · ${runtime.build!.osPatchLevel}',
                        ),
                      ],
                    ],
                  ),
          ),
          const SizedBox(height: 16),
          PanelCard(
            title: strings.deviceKernelEntryTitle,
            subtitle: strings.deviceKernelEntrySubtitle,
            icon: Icons.tune_rounded,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                if (kernelFeatures?.items.isNotEmpty == true) ...<Widget>[
                  Wrap(
                    spacing: 10,
                    runSpacing: 10,
                    children: <Widget>[
                      ...kernelFeatures!.items
                          .take(4)
                          .map(
                            (feature) => StatusPill(
                              label: strings.deviceKernelFeatureTitle(
                                feature.id,
                              ),
                              color: feature.checked
                                  ? Theme.of(context).colorScheme.primary
                                  : Theme.of(context).colorScheme.outline,
                              icon: feature.checked
                                  ? Icons.toggle_on_rounded
                                  : Icons.toggle_off_rounded,
                            ),
                          ),
                    ],
                  ),
                ] else if (state.kernelFeatureError != null) ...<Widget>[
                  const SizedBox(height: 12),
                  Text(
                    state.kernelFeatureError!,
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.error,
                    ),
                  ),
                ],
                const SizedBox(height: 12),
                Row(
                  children: <Widget>[
                    FilledButton.tonal(
                      onPressed: () => context.go('/device/kernel'),
                      child: Text(strings.deviceKernelOpenFeatures),
                    ),
                    const SizedBox(width: 12),
                    FilledButton(
                      onPressed: () => context.go('/device/susfs'),
                      child: Text(strings.deviceSusfsOpenPage),
                    ),
                  ],
                ),
                if (susfs?.status != null) ...<Widget>[
                  const SizedBox(height: 18),
                  Wrap(
                    spacing: 10,
                    runSpacing: 10,
                    children: <Widget>[
                      StatusPill(
                        label: susfs!.status!.available
                            ? 'available'
                            : 'unavailable',
                        color: Theme.of(context).colorScheme.primary,
                        icon: Icons.check_circle_rounded,
                      ),
                      StatusPill(
                        label: susfs.status!.kernelVersion.ifEmpty(
                          strings.unknownValue,
                        ),
                        color: Theme.of(context).colorScheme.secondary,
                        icon: Icons.memory_rounded,
                      ),
                    ],
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class KernelFeaturesPage extends ConsumerStatefulWidget {
  const KernelFeaturesPage({super.key});

  @override
  ConsumerState<KernelFeaturesPage> createState() => _KernelFeaturesPageState();
}

class _KernelFeaturesPageState extends ConsumerState<KernelFeaturesPage> {
  bool _requestedInitialLoad = false;

  @override
  Widget build(BuildContext context) {
    final dashboard = ref.watch(dashboardControllerProvider);
    final state = ref.watch(devicePageControllerProvider);
    final controller = ref.read(devicePageControllerProvider.notifier);
    final strings = context.strings;
    final scheme = Theme.of(context).colorScheme;
    final abkReady =
        dashboard.connection?.connected == true &&
        dashboard.connection?.mode == DeviceConnectionMode.abk;

    if (abkReady && !_requestedInitialLoad) {
      _requestedInitialLoad = true;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        controller.refreshAll();
      });
    }
    if (!abkReady) {
      _requestedInitialLoad = false;
    }

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
                      strings.deviceKernelFeaturesTitle,
                      style: Theme.of(context).textTheme.headlineLarge,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      strings.deviceKernelFeaturesIntro,
                      style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              Wrap(
                spacing: 12,
                runSpacing: 12,
                children: <Widget>[
                  FilledButton.tonalIcon(
                    onPressed: () => context.go('/device'),
                    icon: const Icon(Icons.arrow_back_rounded),
                    label: Text(strings.navDevice),
                  ),
                  FilledButton.tonalIcon(
                    onPressed: abkReady && !state.isRefreshing
                        ? controller.refreshAll
                        : null,
                    icon: const Icon(Icons.refresh_rounded),
                    label: Text(strings.deviceRefreshAll),
                  ),
                ],
              ),
            ],
          ),
          if (!abkReady) ...<Widget>[
            const SizedBox(height: 16),
            _BlockedDeviceState(
              dashboard: dashboard,
              onOpenDetection: () => context.go('/detect'),
            ),
          ] else ...<Widget>[
            if (state.kernelFeatureError != null) ...<Widget>[
              const SizedBox(height: 16),
              _MessageBanner(
                title: strings.deviceKernelFeaturesTitle,
                message: state.kernelFeatureError!,
                color: scheme.errorContainer,
                foreground: scheme.onErrorContainer,
              ),
            ],
            const SizedBox(height: 16),
            PanelCard(
              title: strings.deviceKernelFeaturesTitle,
              subtitle: strings.deviceKernelEntrySubtitle,
              icon: Icons.tune_rounded,
              child: state.kernelFeatures == null
                  ? Text(strings.deviceKernelFeaturesUnsupported)
                  : Column(
                      children: state.kernelFeatures!.items
                          .map(
                            (feature) => Padding(
                              padding: const EdgeInsets.only(bottom: 12),
                              child: _KernelFeatureTile(
                                feature: feature,
                                busy: state.kernelFeatureBusyIds.contains(
                                  feature.id,
                                ),
                                onChanged: feature.enabled
                                    ? (enabled) =>
                                          controller.setKernelFeatureEnabled(
                                            feature.id,
                                            enabled,
                                          )
                                    : null,
                              ),
                            ),
                          )
                          .toList(growable: false),
                    ),
            ),
          ],
        ],
      ),
    );
  }
}

class SusfsPage extends ConsumerStatefulWidget {
  const SusfsPage({super.key});

  @override
  ConsumerState<SusfsPage> createState() => _SusfsPageState();
}

class _SusfsPageState extends ConsumerState<SusfsPage> {
  bool _requestedInitialLoad = false;
  late final TextEditingController _susfsDraftController;
  late final FocusNode _susfsDraftFocusNode;
  SusfsEditorDraft _susfsEditor = SusfsEditorDraft.defaults();
  String _susfsConfigSignature = '';
  bool _susfsFormDirty = false;
  String? _susfsLocalError;

  @override
  void initState() {
    super.initState();
    _susfsDraftController = TextEditingController();
    _susfsDraftFocusNode = FocusNode();
  }

  @override
  void dispose() {
    _susfsDraftController.dispose();
    _susfsDraftFocusNode.dispose();
    super.dispose();
  }

  void _setSusfsDraftControllerText(String value) {
    _susfsDraftController.value = _susfsDraftController.value.copyWith(
      text: value,
      selection: TextSelection.collapsed(offset: value.length),
      composing: TextRange.empty,
    );
  }

  void _syncSusfsEditorFromEnvelope(
    SusfsEnvelope? susfs, {
    bool force = false,
  }) {
    final source = susfs?.config ?? const <String, dynamic>{};
    final signature = const JsonEncoder().convert(source);
    if (!force && _susfsFormDirty) {
      return;
    }
    if (!force && _susfsConfigSignature == signature) {
      return;
    }
    _susfsEditor = SusfsEditorDraft.fromFormData(
      SusfsFormData.fromJsonMap(source),
    );
    _susfsConfigSignature = signature;
    _susfsFormDirty = false;
    _susfsLocalError = null;
  }

  void _updateSusfsEditor(SusfsEditorDraft next) {
    setState(() {
      _susfsEditor = next;
      _susfsFormDirty = true;
      _susfsLocalError = null;
    });
  }

  Future<void> _applySusfsEditor(DevicePageController controller) async {
    try {
      final formData = _susfsEditor.toFormData();
      final pretty = formData.toPrettyJson();
      if (!_susfsDraftFocusNode.hasFocus) {
        _setSusfsDraftControllerText(pretty);
      }
      setState(() {
        _susfsLocalError = null;
        _susfsFormDirty = false;
      });
      await controller.applySusfsConfig(formData.toJsonMap());
    } on FormatException catch (error) {
      setState(() {
        _susfsLocalError = error.message;
      });
    } catch (error) {
      setState(() {
        _susfsLocalError = error.toString();
      });
    }
  }

  void _resetSusfsEditor(
    DevicePageController controller,
    SusfsEnvelope? susfs,
  ) {
    controller.resetSusfsDraft();
    final pretty = susfs?.prettyConfig() ?? '';
    setState(() {
      _syncSusfsEditorFromEnvelope(susfs, force: true);
      _susfsLocalError = null;
    });
    if (!_susfsDraftFocusNode.hasFocus) {
      _setSusfsDraftControllerText(pretty);
    }
  }

  void _loadSusfsFormFromRawJson(
    DevicePageController controller,
    AppStrings strings,
  ) {
    try {
      final decoded = jsonDecode(_susfsDraftController.text.trim());
      if (decoded is! Map) {
        throw const FormatException();
      }
      final formData = SusfsFormData.fromJsonMap(
        Map<String, dynamic>.from(decoded),
      );
      final pretty = formData.toPrettyJson();
      controller.updateSusfsDraft(pretty);
      setState(() {
        _susfsEditor = SusfsEditorDraft.fromFormData(formData);
        _susfsFormDirty = true;
        _susfsLocalError = null;
      });
      _setSusfsDraftControllerText(pretty);
    } catch (_) {
      setState(() {
        _susfsLocalError = strings.deviceSusfsDraftInvalid;
      });
    }
  }

  void _syncRawJsonFromForm(DevicePageController controller) {
    try {
      final pretty = _susfsEditor.toFormData().toPrettyJson();
      controller.updateSusfsDraft(pretty);
      _setSusfsDraftControllerText(pretty);
      setState(() {
        _susfsLocalError = null;
      });
    } on FormatException catch (error) {
      setState(() {
        _susfsLocalError = error.message;
      });
    } catch (error) {
      setState(() {
        _susfsLocalError = error.toString();
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final dashboard = ref.watch(dashboardControllerProvider);
    final state = ref.watch(devicePageControllerProvider);
    final controller = ref.read(devicePageControllerProvider.notifier);
    final strings = context.strings;
    final scheme = Theme.of(context).colorScheme;
    final abkReady =
        dashboard.connection?.connected == true &&
        dashboard.connection?.mode == DeviceConnectionMode.abk;

    if (abkReady && !_requestedInitialLoad) {
      _requestedInitialLoad = true;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        controller.refreshAll();
      });
    }
    if (!abkReady) {
      _requestedInitialLoad = false;
    }
    final susfs = state.susfs;
    if (!state.susfsDraftDirty) {
      _syncSusfsEditorFromEnvelope(susfs);
    }
    if (!_susfsDraftFocusNode.hasFocus &&
        _susfsDraftController.text != state.susfsConfigDraft) {
      _setSusfsDraftControllerText(state.susfsConfigDraft);
    }
    final support = susfs?.status?.support;
    final theme = Theme.of(context);

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
                      strings.deviceSusfsPageTitle,
                      style: Theme.of(context).textTheme.headlineLarge,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      strings.deviceSusfsPageIntro,
                      style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              Wrap(
                spacing: 12,
                runSpacing: 12,
                children: <Widget>[
                  FilledButton.tonalIcon(
                    onPressed: () => context.go('/device'),
                    icon: const Icon(Icons.arrow_back_rounded),
                    label: Text(strings.navDevice),
                  ),
                  FilledButton.tonalIcon(
                    onPressed: abkReady && !state.isRefreshing
                        ? controller.refreshAll
                        : null,
                    icon: const Icon(Icons.refresh_rounded),
                    label: Text(strings.deviceRefreshAll),
                  ),
                ],
              ),
            ],
          ),
          if (!abkReady) ...<Widget>[
            const SizedBox(height: 16),
            _BlockedDeviceState(
              dashboard: dashboard,
              onOpenDetection: () => context.go('/detect'),
            ),
          ] else ...<Widget>[
            if (state.susfsError != null) ...<Widget>[
              const SizedBox(height: 16),
              _MessageBanner(
                title: strings.deviceSusfsTitle,
                message: state.susfsError!,
                color: scheme.errorContainer,
                foreground: scheme.onErrorContainer,
              ),
            ],
            if (_susfsLocalError != null) ...<Widget>[
              const SizedBox(height: 16),
              _MessageBanner(
                title: strings.deviceSusfsFormErrorTitle,
                message: _susfsLocalError!,
                color: scheme.errorContainer,
                foreground: scheme.onErrorContainer,
              ),
            ],
            const SizedBox(height: 16),
            LayoutBuilder(
              builder: (BuildContext context, BoxConstraints constraints) {
                final wide = constraints.maxWidth >= 1180;
                final overviewCard = PanelCard(
                  title: strings.deviceSusfsOverviewTitle,
                  subtitle: strings.deviceSusfsOverviewSubtitle,
                  icon: Icons.extension_rounded,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      if (susfs?.status != null) ...<Widget>[
                        Wrap(
                          spacing: 10,
                          runSpacing: 10,
                          children: <Widget>[
                            StatusPill(
                              label: susfs!.status!.available
                                  ? strings.deviceSusfsStatusAvailable
                                  : strings.deviceSusfsStatusUnavailable,
                              color: theme.colorScheme.primary,
                              icon: susfs.status!.available
                                  ? Icons.check_circle_rounded
                                  : Icons.error_outline_rounded,
                            ),
                            StatusPill(
                              label: susfs.status!.kernelVersion.ifEmpty(
                                strings.unknownValue,
                              ),
                              color: theme.colorScheme.secondary,
                              icon: Icons.memory_rounded,
                            ),
                            StatusPill(
                              label: susfs.status!.bundledBinaryVersion.ifEmpty(
                                strings.unknownValue,
                              ),
                              color: theme.colorScheme.tertiary,
                              icon: Icons.inventory_2_rounded,
                            ),
                            StatusPill(
                              label: strings.deviceSusfsFeatureFlagCount(
                                susfs.status!.featureFlags.length,
                              ),
                              color: theme.colorScheme.outline,
                              icon: Icons.flag_rounded,
                            ),
                          ],
                        ),
                        const SizedBox(height: 16),
                        _SusfsInfoLine(
                          label: strings.deviceSusfsKernelVersionLabel,
                          value: susfs.status!.kernelVersion.ifEmpty(
                            strings.unknownValue,
                          ),
                        ),
                        _SusfsInfoLine(
                          label: strings.deviceSusfsBinaryLabel,
                          value:
                              '${susfs.status!.bundledBinaryVersion.ifEmpty(strings.unknownValue)} · ${susfs.status!.bundledBinaryRef.ifEmpty(strings.unknownValue)}',
                        ),
                        _SusfsInfoLine(
                          label: strings.deviceSusfsConfigPathLabel,
                          value: susfs.status!.configPath.ifEmpty(
                            strings.unknownValue,
                          ),
                        ),
                        if (susfs.status!.rawFeatureText
                            .trim()
                            .isNotEmpty) ...<Widget>[
                          const SizedBox(height: 12),
                          Text(
                            susfs.status!.rawFeatureText,
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: scheme.onSurfaceVariant,
                              fontFamily: 'monospace',
                            ),
                          ),
                        ],
                        if (susfs.status!.diagnostics.isNotEmpty) ...<Widget>[
                          const SizedBox(height: 16),
                          Container(
                            width: double.infinity,
                            padding: const EdgeInsets.all(14),
                            decoration: BoxDecoration(
                              color: scheme.surfaceContainerHighest.withValues(
                                alpha: 0.24,
                              ),
                              borderRadius: BorderRadius.circular(16),
                              border: Border.all(
                                color: scheme.outlineVariant.withValues(
                                  alpha: 0.32,
                                ),
                              ),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: <Widget>[
                                Text(
                                  strings.deviceSusfsDiagnosticsTitle,
                                  style: theme.textTheme.titleSmall,
                                ),
                                const SizedBox(height: 8),
                                ...susfs.status!.diagnostics.map(
                                  (line) => Padding(
                                    padding: const EdgeInsets.only(bottom: 4),
                                    child: Text(
                                      line,
                                      style: theme.textTheme.bodyMedium,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ],
                    ],
                  ),
                );
                final actionCard = PanelCard(
                  title: strings.deviceSusfsActionTitle,
                  subtitle: strings.deviceSusfsActionSubtitle,
                  icon: Icons.tune_rounded,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Wrap(
                        spacing: 10,
                        runSpacing: 10,
                        children: <Widget>[
                          StatusPill(
                            label: _susfsFormDirty
                                ? strings.deviceSusfsDraftEdited
                                : strings.deviceSusfsDraftClean,
                            color: _susfsFormDirty
                                ? scheme.primary
                                : scheme.outline,
                            icon: _susfsFormDirty
                                ? Icons.edit_note_rounded
                                : Icons.done_all_rounded,
                          ),
                          StatusPill(
                            label: state.susfsSaving
                                ? strings.refreshing
                                : strings.deviceSusfsReadyToApply,
                            color: state.susfsSaving
                                ? scheme.secondary
                                : scheme.tertiary,
                            icon: state.susfsSaving
                                ? Icons.sync_rounded
                                : Icons.bolt_rounded,
                          ),
                        ],
                      ),
                      const SizedBox(height: 16),
                      Text(
                        strings.deviceSusfsActionHint,
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: scheme.onSurfaceVariant,
                        ),
                      ),
                      const SizedBox(height: 16),
                      SizedBox(
                        width: double.infinity,
                        child: FilledButton(
                          onPressed: state.susfsSaving
                              ? null
                              : () => _applySusfsEditor(controller),
                          child: state.susfsSaving
                              ? const SizedBox(
                                  width: 18,
                                  height: 18,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2.2,
                                  ),
                                )
                              : Text(strings.deviceSusfsApplyForm),
                        ),
                      ),
                      const SizedBox(height: 12),
                      SizedBox(
                        width: double.infinity,
                        child: FilledButton.tonal(
                          onPressed: state.susfsSaving
                              ? null
                              : () => _resetSusfsEditor(controller, susfs),
                          child: Text(strings.deviceSusfsResetToDevice),
                        ),
                      ),
                      const SizedBox(height: 12),
                      SizedBox(
                        width: double.infinity,
                        child: FilledButton.tonal(
                          onPressed: () => _syncRawJsonFromForm(controller),
                          child: Text(strings.deviceSusfsSyncJsonFromForm),
                        ),
                      ),
                    ],
                  ),
                );
                if (!wide) {
                  return Column(
                    children: <Widget>[
                      overviewCard,
                      const SizedBox(height: 16),
                      actionCard,
                    ],
                  );
                }
                return Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Expanded(flex: 7, child: overviewCard),
                    const SizedBox(width: 16),
                    Expanded(flex: 5, child: actionCard),
                  ],
                );
              },
            ),
            const SizedBox(height: 16),
            PanelCard(
              title: strings.deviceSusfsBasicTitle,
              subtitle: strings.deviceSusfsBasicSubtitle,
              icon: Icons.settings_rounded,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  _SusfsSwitchTile(
                    title: strings.deviceSusfsAutoReplayTitle,
                    subtitle: strings.deviceSusfsAutoReplaySubtitle,
                    value: _susfsEditor.autoReplayEnabled,
                    onChanged: (value) => _updateSusfsEditor(
                      _susfsEditor.copyWith(autoReplayEnabled: value),
                    ),
                  ),
                  _SusfsSwitchTile(
                    title: strings.deviceSusfsLogTitle,
                    subtitle: strings.deviceSusfsLogSubtitle,
                    value: _susfsEditor.logEnabled,
                    onChanged: (value) => _updateSusfsEditor(
                      _susfsEditor.copyWith(logEnabled: value),
                    ),
                  ),
                  if (support?.avcLogSpoofing == true)
                    _SusfsSwitchTile(
                      title: strings.deviceSusfsAvcSpoofTitle,
                      subtitle: strings.deviceSusfsAvcSpoofSubtitle,
                      value: _susfsEditor.avcLogSpoofing,
                      onChanged: (value) => _updateSusfsEditor(
                        _susfsEditor.copyWith(avcLogSpoofing: value),
                      ),
                    ),
                  if (support?.hideSusMountsForAll == true ||
                      support?.hideSusMountsForNonSu == true) ...<Widget>[
                    const SizedBox(height: 12),
                    _SusfsChoiceField<String>(
                      title: strings.deviceSusfsHideMountModeTitle,
                      value: _susfsEditor.hideSusMountsMode,
                      options: <_SusfsChoiceOption<String>>[
                        _SusfsChoiceOption(
                          value: susfsHideMountsOff,
                          label: strings.deviceSusfsOptionOff,
                        ),
                        _SusfsChoiceOption(
                          value: susfsHideMountsAll,
                          label: strings.deviceSusfsOptionAllProcesses,
                        ),
                        _SusfsChoiceOption(
                          value: susfsHideMountsNonSu,
                          label: strings.deviceSusfsOptionNonSuProcesses,
                        ),
                      ],
                      onSelected: (value) => _updateSusfsEditor(
                        _susfsEditor.copyWith(hideSusMountsMode: value),
                      ),
                    ),
                  ],
                  if (support?.setUname == true) ...<Widget>[
                    const SizedBox(height: 12),
                    _SusfsChoiceField<String>(
                      title: strings.deviceSusfsSpoofUnameStageTitle,
                      value: _susfsEditor.spoofUnameStage,
                      options: <_SusfsChoiceOption<String>>[
                        _SusfsChoiceOption(
                          value: susfsSpoofUnameOff,
                          label: strings.deviceSusfsOptionOff,
                        ),
                        _SusfsChoiceOption(
                          value: susfsSpoofUnamePostFsData,
                          label: strings.deviceSusfsOptionPostFsData,
                        ),
                        _SusfsChoiceOption(
                          value: susfsSpoofUnameBootCompleted,
                          label: strings.deviceSusfsOptionBootCompleted,
                        ),
                      ],
                      onSelected: (value) => _updateSusfsEditor(
                        _susfsEditor.copyWith(spoofUnameStage: value),
                      ),
                    ),
                    const SizedBox(height: 12),
                    _SusfsTextField(
                      label: strings.deviceSusfsUnameValueLabel,
                      value: _susfsEditor.unameValue,
                      onChanged: (value) => _updateSusfsEditor(
                        _susfsEditor.copyWith(unameValue: value),
                      ),
                    ),
                    const SizedBox(height: 12),
                    _SusfsTextField(
                      label: strings.deviceSusfsBuildTimeValueLabel,
                      value: _susfsEditor.buildTimeValue,
                      onChanged: (value) => _updateSusfsEditor(
                        _susfsEditor.copyWith(buildTimeValue: value),
                      ),
                    ),
                  ],
                  if (support?.sdcardRootPath == true) ...<Widget>[
                    const SizedBox(height: 12),
                    _SusfsTextField(
                      label: strings.deviceSusfsSdcardRootLabel,
                      value: _susfsEditor.sdcardRootPath,
                      onChanged: (value) => _updateSusfsEditor(
                        _susfsEditor.copyWith(sdcardRootPath: value),
                      ),
                    ),
                    const SizedBox(height: 12),
                    _SusfsTextField(
                      label: strings.deviceSusfsAndroidDataRootLabel,
                      value: _susfsEditor.androidDataRootPath,
                      onChanged: (value) => _updateSusfsEditor(
                        _susfsEditor.copyWith(androidDataRootPath: value),
                      ),
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 16),
            PanelCard(
              title: strings.deviceSusfsPresetTitle,
              subtitle: strings.deviceSusfsPresetSubtitle,
              icon: Icons.auto_fix_high_rounded,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Row(
                    children: <Widget>[
                      Expanded(
                        child: _SusfsDropdownField<int>(
                          label: strings.deviceSusfsHideCustomRomLevelLabel,
                          value: _susfsEditor.hideCustomRomLevel,
                          items: List<int>.generate(6, (index) => index),
                          labelBuilder: (value) => value.toString(),
                          onChanged: (value) => _updateSusfsEditor(
                            _susfsEditor.copyWith(hideCustomRomLevel: value),
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: _SusfsDropdownField<int>(
                          label: strings.deviceSusfsEmulateVoldLabel,
                          value: _susfsEditor.emulateVoldAppDataMode,
                          items: const <int>[0, 1, 2],
                          labelBuilder: strings.deviceSusfsEmulateVoldOption,
                          onChanged: (value) => _updateSusfsEditor(
                            _susfsEditor.copyWith(
                              emulateVoldAppDataMode: value,
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  _SusfsSwitchTile(
                    title: strings.deviceSusfsHideVendorSepolicyTitle,
                    value: _susfsEditor.hideVendorSepolicy,
                    onChanged: (value) => _updateSusfsEditor(
                      _susfsEditor.copyWith(hideVendorSepolicy: value),
                    ),
                  ),
                  _SusfsSwitchTile(
                    title: strings.deviceSusfsHideCompatMatrixTitle,
                    value: _susfsEditor.hideCompatMatrix,
                    onChanged: (value) => _updateSusfsEditor(
                      _susfsEditor.copyWith(hideCompatMatrix: value),
                    ),
                  ),
                  _SusfsSwitchTile(
                    title: strings.deviceSusfsHideGappsTitle,
                    value: _susfsEditor.hideGapps,
                    onChanged: (value) => _updateSusfsEditor(
                      _susfsEditor.copyWith(hideGapps: value),
                    ),
                  ),
                  _SusfsSwitchTile(
                    title: strings.deviceSusfsHideRevancedTitle,
                    value: _susfsEditor.hideRevanced,
                    onChanged: (value) => _updateSusfsEditor(
                      _susfsEditor.copyWith(hideRevanced: value),
                    ),
                  ),
                  _SusfsSwitchTile(
                    title: strings.deviceSusfsSpoofCmdlineTitle,
                    value: _susfsEditor.spoofCmdline,
                    onChanged: (value) => _updateSusfsEditor(
                      _susfsEditor.copyWith(spoofCmdline: value),
                    ),
                  ),
                  _SusfsSwitchTile(
                    title: strings.deviceSusfsHideLoopsTitle,
                    value: _susfsEditor.hideLoops,
                    onChanged: (value) => _updateSusfsEditor(
                      _susfsEditor.copyWith(hideLoops: value),
                    ),
                  ),
                  _SusfsSwitchTile(
                    title: strings.deviceSusfsForceHideLsposedTitle,
                    value: _susfsEditor.forceHideLsposed,
                    onChanged: (value) => _updateSusfsEditor(
                      _susfsEditor.copyWith(forceHideLsposed: value),
                    ),
                  ),
                  if (support?.autoTryUmountPreset == true ||
                      support?.ksudKernelUmountFallback == true) ...<Widget>[
                    _SusfsSwitchTile(
                      title: strings.deviceSusfsAutoTryUmountTitle,
                      value: _susfsEditor.autoTryUmount,
                      onChanged: (value) => _updateSusfsEditor(
                        _susfsEditor.copyWith(autoTryUmount: value),
                      ),
                    ),
                    _SusfsSwitchTile(
                      title: strings.deviceSusfsSkipLegitMountsTitle,
                      value: _susfsEditor.skipLegitMounts,
                      onChanged: (value) => _updateSusfsEditor(
                        _susfsEditor.copyWith(skipLegitMounts: value),
                      ),
                    ),
                  ],
                  if (support?.umountForZygoteIsoService == true)
                    _SusfsSwitchTile(
                      title: strings.deviceSusfsUmountForZygoteTitle,
                      value: _susfsEditor.umountForZygoteIsoService,
                      onChanged: (value) => _updateSusfsEditor(
                        _susfsEditor.copyWith(umountForZygoteIsoService: value),
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            PanelCard(
              title: strings.deviceSusfsRulesTitle,
              subtitle: strings.deviceSusfsRulesSubtitle,
              icon: Icons.rule_folder_rounded,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Wrap(
                    spacing: 10,
                    runSpacing: 10,
                    children: <Widget>[
                      StatusPill(
                        label: strings.deviceSusfsRuleCount(
                          countVisibleRuleLines(_susfsEditor.pathRulesText),
                        ),
                        color: scheme.primary,
                        icon: Icons.alt_route_rounded,
                      ),
                      StatusPill(
                        label: strings.deviceSusfsMountCount(
                          countVisibleRuleLines(_susfsEditor.mountsText),
                        ),
                        color: scheme.secondary,
                        icon: Icons.storage_rounded,
                      ),
                      StatusPill(
                        label: strings.deviceSusfsMapCount(
                          countVisibleRuleLines(_susfsEditor.mapsText),
                        ),
                        color: scheme.tertiary,
                        icon: Icons.layers_rounded,
                      ),
                    ],
                  ),
                  if (support?.susPath == true) ...<Widget>[
                    const SizedBox(height: 12),
                    _SusfsTextField(
                      label: strings.deviceSusfsPathRulesLabel,
                      helper: strings.deviceSusfsPathRulesHint,
                      value: _susfsEditor.pathRulesText,
                      onChanged: (value) => _updateSusfsEditor(
                        _susfsEditor.copyWith(pathRulesText: value),
                      ),
                      minLines: 4,
                      maxLines: 8,
                      monospace: true,
                    ),
                  ],
                  if (support?.susPathLoop == true) ...<Widget>[
                    const SizedBox(height: 12),
                    _SusfsTextField(
                      label: strings.deviceSusfsLoopPathRulesLabel,
                      helper: strings.deviceSusfsPathRulesHint,
                      value: _susfsEditor.loopPathRulesText,
                      onChanged: (value) => _updateSusfsEditor(
                        _susfsEditor.copyWith(loopPathRulesText: value),
                      ),
                      minLines: 4,
                      maxLines: 8,
                      monospace: true,
                    ),
                  ],
                  if (support?.susMap == true) ...<Widget>[
                    const SizedBox(height: 12),
                    _SusfsTextField(
                      label: strings.deviceSusfsMapsLabel,
                      value: _susfsEditor.mapsText,
                      onChanged: (value) => _updateSusfsEditor(
                        _susfsEditor.copyWith(mapsText: value),
                      ),
                      minLines: 4,
                      maxLines: 8,
                      monospace: true,
                    ),
                  ],
                  if (support?.susMount == true) ...<Widget>[
                    const SizedBox(height: 12),
                    _SusfsTextField(
                      label: strings.deviceSusfsMountsLabel,
                      value: _susfsEditor.mountsText,
                      onChanged: (value) => _updateSusfsEditor(
                        _susfsEditor.copyWith(mountsText: value),
                      ),
                      minLines: 4,
                      maxLines: 8,
                      monospace: true,
                    ),
                  ],
                  if (support?.tryUmount == true ||
                      support?.ksudKernelUmountFallback == true) ...<Widget>[
                    const SizedBox(height: 12),
                    _SusfsTextField(
                      label: strings.deviceSusfsTryUmountLabel,
                      value: _susfsEditor.tryUmountText,
                      onChanged: (value) => _updateSusfsEditor(
                        _susfsEditor.copyWith(tryUmountText: value),
                      ),
                      minLines: 4,
                      maxLines: 8,
                      monospace: true,
                    ),
                  ],
                  const SizedBox(height: 12),
                  _SusfsTextField(
                    label: strings.deviceSusfsLegitMountsLabel,
                    value: _susfsEditor.legitMountsText,
                    onChanged: (value) => _updateSusfsEditor(
                      _susfsEditor.copyWith(legitMountsText: value),
                    ),
                    minLines: 6,
                    maxLines: 12,
                    monospace: true,
                  ),
                ],
              ),
            ),
            if (support?.openRedirect == true ||
                support?.staticKstat == true) ...<Widget>[
              const SizedBox(height: 16),
              PanelCard(
                title: strings.deviceSusfsAdvancedTitle,
                subtitle: strings.deviceSusfsAdvancedSubtitle,
                icon: Icons.data_object_rounded,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    if (support?.openRedirect == true) ...<Widget>[
                      _SusfsTextField(
                        label: strings.deviceSusfsOpenRedirectLabel,
                        helper: strings.deviceSusfsOpenRedirectHint,
                        value: _susfsEditor.openRedirectText,
                        onChanged: (value) => _updateSusfsEditor(
                          _susfsEditor.copyWith(openRedirectText: value),
                        ),
                        minLines: 4,
                        maxLines: 8,
                        monospace: true,
                      ),
                    ],
                    if (support?.staticKstat == true) ...<Widget>[
                      const SizedBox(height: 12),
                      _SusfsTextField(
                        label: strings.deviceSusfsKstatLabel,
                        value: _susfsEditor.kstatJsonText,
                        onChanged: (value) => _updateSusfsEditor(
                          _susfsEditor.copyWith(kstatJsonText: value),
                        ),
                        minLines: 8,
                        maxLines: 14,
                        monospace: true,
                      ),
                    ],
                  ],
                ),
              ),
            ],
            const SizedBox(height: 16),
            PanelCard(
              title: strings.deviceSusfsRawJsonTitle,
              subtitle: strings.deviceSusfsRawJsonSubtitle,
              icon: Icons.code_rounded,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  TextField(
                    controller: _susfsDraftController,
                    focusNode: _susfsDraftFocusNode,
                    onChanged: controller.updateSusfsDraft,
                    minLines: 10,
                    maxLines: 20,
                    decoration: InputDecoration(
                      border: const OutlineInputBorder(),
                      helperText: strings.deviceSusfsRawJsonHint,
                    ),
                    style: const TextStyle(fontFamily: 'monospace'),
                  ),
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 12,
                    runSpacing: 12,
                    children: <Widget>[
                      FilledButton.tonal(
                        onPressed: () =>
                            _loadSusfsFormFromRawJson(controller, strings),
                        child: Text(strings.deviceSusfsLoadFormFromJson),
                      ),
                      FilledButton.tonal(
                        onPressed: () => _syncRawJsonFromForm(controller),
                        child: Text(strings.deviceSusfsSyncJsonFromForm),
                      ),
                      FilledButton(
                        onPressed: state.susfsSaving
                            ? null
                            : controller.applySusfsDraft,
                        child: Text(strings.deviceSusfsApplyRawJson),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            _DeviceTasksCard(
              state: state,
              kinds: const <String>{'susfs.apply'},
            ),
          ],
        ],
      ),
    );
  }
}

class _SusfsInfoLine extends StatelessWidget {
  const _SusfsInfoLine({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: RichText(
        text: TextSpan(
          style: Theme.of(
            context,
          ).textTheme.bodyMedium?.copyWith(color: scheme.onSurfaceVariant),
          children: <InlineSpan>[
            TextSpan(
              text: '$label: ',
              style: Theme.of(
                context,
              ).textTheme.titleSmall?.copyWith(color: scheme.onSurface),
            ),
            TextSpan(text: value),
          ],
        ),
      ),
    );
  }
}

class _SusfsSwitchTile extends StatelessWidget {
  const _SusfsSwitchTile({
    required this.title,
    this.subtitle,
    required this.value,
    required this.onChanged,
  });

  final String title;
  final String? subtitle;
  final bool value;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    return SwitchListTile.adaptive(
      contentPadding: EdgeInsets.zero,
      title: Text(title),
      subtitle: subtitle == null ? null : Text(subtitle!),
      value: value,
      onChanged: onChanged,
    );
  }
}

class _SusfsChoiceOption<T> {
  const _SusfsChoiceOption({required this.value, required this.label});

  final T value;
  final String label;
}

class _SusfsChoiceField<T> extends StatelessWidget {
  const _SusfsChoiceField({
    required this.title,
    required this.value,
    required this.options,
    required this.onSelected,
  });

  final String title;
  final T value;
  final List<_SusfsChoiceOption<T>> options;
  final ValueChanged<T> onSelected;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Text(title, style: Theme.of(context).textTheme.titleSmall),
        const SizedBox(height: 8),
        Wrap(
          spacing: 10,
          runSpacing: 10,
          children: options
              .map((option) {
                return ChoiceChip(
                  label: Text(option.label),
                  selected: option.value == value,
                  onSelected: (_) => onSelected(option.value),
                );
              })
              .toList(growable: false),
        ),
      ],
    );
  }
}

class _SusfsTextField extends StatefulWidget {
  const _SusfsTextField({
    required this.label,
    required this.value,
    required this.onChanged,
    this.helper,
    this.minLines = 1,
    this.maxLines = 1,
    this.monospace = false,
  });

  final String label;
  final String value;
  final ValueChanged<String> onChanged;
  final String? helper;
  final int minLines;
  final int maxLines;
  final bool monospace;

  @override
  State<_SusfsTextField> createState() => _SusfsTextFieldState();
}

class _SusfsTextFieldState extends State<_SusfsTextField> {
  late final TextEditingController _controller;
  late final FocusNode _focusNode;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.value);
    _focusNode = FocusNode();
  }

  @override
  void didUpdateWidget(covariant _SusfsTextField oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!_focusNode.hasFocus &&
        widget.value != oldWidget.value &&
        _controller.text != widget.value) {
      _controller.value = _controller.value.copyWith(
        text: widget.value,
        selection: TextSelection.collapsed(offset: widget.value.length),
        composing: TextRange.empty,
      );
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: _controller,
      focusNode: _focusNode,
      onChanged: widget.onChanged,
      minLines: widget.minLines,
      maxLines: widget.maxLines,
      decoration: InputDecoration(
        labelText: widget.label,
        helperText: widget.helper,
        border: const OutlineInputBorder(),
      ),
      style: widget.monospace ? const TextStyle(fontFamily: 'monospace') : null,
    );
  }
}

class _SusfsDropdownField<T> extends StatelessWidget {
  const _SusfsDropdownField({
    required this.label,
    required this.value,
    required this.items,
    required this.labelBuilder,
    required this.onChanged,
  });

  final String label;
  final T value;
  final List<T> items;
  final String Function(T value) labelBuilder;
  final ValueChanged<T> onChanged;

  @override
  Widget build(BuildContext context) {
    return DropdownButtonFormField<T>(
      initialValue: value,
      decoration: InputDecoration(
        labelText: label,
        border: const OutlineInputBorder(),
      ),
      items: items
          .map(
            (item) => DropdownMenuItem<T>(
              value: item,
              child: Text(labelBuilder(item)),
            ),
          )
          .toList(growable: false),
      onChanged: (value) {
        if (value != null) {
          onChanged(value);
        }
      },
    );
  }
}

class _KernelFeatureTile extends StatelessWidget {
  const _KernelFeatureTile({
    required this.feature,
    required this.busy,
    required this.onChanged,
  });

  final KernelFeatureItem feature;
  final bool busy;
  final ValueChanged<bool>? onChanged;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final scheme = Theme.of(context).colorScheme;
    return Container(
      decoration: BoxDecoration(
        color: scheme.surfaceContainerHighest.withValues(alpha: 0.22),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: scheme.outlineVariant.withValues(alpha: 0.28),
        ),
      ),
      padding: const EdgeInsets.all(14),
      child: Row(
        children: <Widget>[
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(
                  strings.deviceKernelFeatureTitle(feature.id),
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 4),
                Text(
                  strings.deviceKernelFeatureSubtitle(feature.id),
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 10),
                StatusPill(
                  label: strings.deviceKernelFeatureStatusLabel(feature.status),
                  color: feature.isSupported ? scheme.primary : scheme.outline,
                  icon: feature.isSupported
                      ? Icons.verified_rounded
                      : Icons.block_rounded,
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          if (busy)
            const SizedBox(
              width: 22,
              height: 22,
              child: CircularProgressIndicator(strokeWidth: 2.2),
            )
          else
            Switch(
              value: feature.checked,
              onChanged: feature.enabled ? onChanged : null,
            ),
        ],
      ),
    );
  }
}

class _DeviceTasksCard extends StatelessWidget {
  const _DeviceTasksCard({required this.state, this.kinds});

  final DevicePageState state;
  final Set<String>? kinds;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final tasks = state.taskOrder
        .map((id) => state.taskById(id))
        .whereType<DesktopTaskSnapshot>()
        .where((task) => kinds == null || kinds!.contains(task.kind))
        .toList(growable: false);
    return PanelCard(
      title: strings.deviceTaskTitle,
      subtitle: strings.deviceTaskSubtitle,
      icon: Icons.terminal_rounded,
      child: tasks.isEmpty
          ? Text(strings.deviceTaskNoTasks)
          : Column(
              children: tasks
                  .map(
                    (task) => Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: _DeviceTaskTile(task: task),
                    ),
                  )
                  .toList(growable: false),
            ),
    );
  }
}

class _DeviceTaskTile extends StatelessWidget {
  const _DeviceTaskTile({required this.task});

  final DesktopTaskSnapshot task;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final scheme = Theme.of(context).colorScheme;
    return Material(
      color: scheme.surfaceContainerHighest.withValues(alpha: 0.28),
      borderRadius: BorderRadius.circular(18),
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: () => showDialog<void>(
          context: context,
          builder: (context) => _DeviceTaskLogDialog(task: task),
        ),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: <Widget>[
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(
                      strings.buildTaskLabel(task.kind),
                      style: Theme.of(context).textTheme.titleSmall,
                    ),
                    const SizedBox(height: 4),
                    Text(task.message ?? task.id),
                  ],
                ),
              ),
              StatusPill(
                label: strings.buildTaskStateLabel(task.state),
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
        ),
      ),
    );
  }
}

class _DeviceTaskLogDialog extends StatelessWidget {
  const _DeviceTaskLogDialog({required this.task});

  final DesktopTaskSnapshot task;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final output = <String>[
      if (task.message != null) '## ${task.message}',
      ...task.output,
      if (task.result.isNotEmpty) ...<String>[
        '',
        const JsonEncoder.withIndent('  ').convert(task.result),
      ],
    ];
    final content = output.join('\n');
    return AlertDialog(
      title: Text(context.strings.buildTaskDetailsTitle),
      content: SizedBox(
        width: 760,
        height: Platform.isLinux ? 520 : null,
        child: Platform.isLinux
            ? DecoratedBox(
                decoration: BoxDecoration(
                  color: scheme.surface,
                  borderRadius: BorderRadius.circular(18),
                  border: Border.all(color: scheme.outlineVariant),
                ),
                child: content.isEmpty
                    ? Padding(
                        padding: const EdgeInsets.all(16),
                        child: Text(
                          context.strings.buildTaskNoOutput,
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: scheme.onSurfaceVariant,
                          ),
                        ),
                      )
                    : MonacoLogView(
                        content: content,
                        language: MonacoCodeLanguage.plaintext,
                        nativeInsets: const EdgeInsets.fromLTRB(1, 0, 1, 1),
                        fallbackPadding: const EdgeInsets.all(16),
                        fallbackStyle: theme.textTheme.bodySmall?.copyWith(
                          fontFamily: 'monospace',
                          height: 1.45,
                        ),
                      ),
              )
            : SingleChildScrollView(
                child: SelectableText(
                  content,
                  style: const TextStyle(fontFamily: 'monospace'),
                ),
              ),
      ),
      actions: <Widget>[
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(context.strings.commonCancel),
        ),
      ],
    );
  }
}

Future<void> _openUrl(String url) async {
  await Process.start('xdg-open', <String>[url]);
}

Future<void> _openModuleWebUi(
  AbkSidecarApi api,
  AbkRuntimeModule module,
) async {
  final url = api.runtimeModuleWebUiUri(module.id).toString();
  final opened = await MethodChannelDesktopWebUiApi().openWebUiWindow(
    url: url,
    title: module.displayName,
  );
  if (!opened) {
    await _openUrl(url);
  }
}

Future<String?> _pickZipPath() async {
  final zenity = await Process.run('sh', <String>[
    '-lc',
    'command -v zenity >/dev/null 2>&1 && zenity --file-selection --file-filter="*.zip"',
  ]);
  final zenityPath = (zenity.stdout as String).trim();
  if (zenity.exitCode == 0 && zenityPath.isNotEmpty) {
    return zenityPath;
  }

  final kdialog = await Process.run('sh', <String>[
    '-lc',
    'command -v kdialog >/dev/null 2>&1 && kdialog --getopenfilename . "*.zip"',
  ]);
  final kdialogPath = (kdialog.stdout as String).trim();
  if (kdialog.exitCode == 0 && kdialogPath.isNotEmpty) {
    return kdialogPath;
  }
  return null;
}

extension<T> on Iterable<T> {
  T? get firstOrNull => isEmpty ? null : first;
}

extension on String {
  String ifEmpty(String fallback) => isEmpty ? fallback : this;
}
