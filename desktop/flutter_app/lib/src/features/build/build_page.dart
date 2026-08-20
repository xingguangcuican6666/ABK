import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
import 'package:window_manager/window_manager.dart';

import '../../core/api/abk_sidecar_api.dart';
import '../../core/localization/app_strings.dart';
import '../../core/models/build_models.dart';
import '../../core/state/dashboard_controller.dart';
import '../../widgets/monaco_code_view.dart';
import '../../widgets/panel_card.dart';
import '../../widgets/status_pill.dart';
import 'build_form_state.dart';
import 'build_page_controller.dart';
import 'build_module_catalog.dart';
import 'kernel_support.dart';

class BuildPage extends ConsumerStatefulWidget {
  const BuildPage({super.key});

  @override
  ConsumerState<BuildPage> createState() => _BuildPageState();
}

class _BuildPageState extends ConsumerState<BuildPage> {
  bool _taskSidebarExpanded = true;
  final List<String> _taskWorkspaceEntryIds = <String>[];
  String? _activeTaskWorkspaceEntryId;

  void _openTaskWorkspaceEntry(String entryId) {
    if (_taskWorkspaceEntryIds.contains(entryId)) {
      _activeTaskWorkspaceEntryId = entryId;
      return;
    }
    _taskWorkspaceEntryIds.add(entryId);
    _activeTaskWorkspaceEntryId = entryId;
  }

  Future<void> _openTaskWorkspaceEntryInWindow(String entryId) async {
    setState(() {
      _openTaskWorkspaceEntry(entryId);
    });
    await _syncTaskWorkspaceWindow();
  }

  Future<void> _syncTaskWorkspaceWindow() async {
    final stateFile = _taskWorkspaceStateFilePath();
    final sidecarBaseUrl =
        ref.read(sidecarBaseUrlOverrideProvider) ??
        Platform.environment['ABK_DESKTOP_BASE_URL'];
    await _writeTaskWorkspaceState(
      stateFile,
      _TaskWorkspaceStateFile(
        entryIds: _taskWorkspaceEntryIds,
        activeEntryId: _activeTaskWorkspaceEntryId,
      ),
    );
    await _ensureTaskWorkspaceWindowRunning(stateFile, sidecarBaseUrl);
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(buildPageControllerProvider);
    final controller = ref.read(buildPageControllerProvider.notifier);
    final strings = context.strings;
    final theme = Theme.of(context);
    final scheme = Theme.of(context).colorScheme;

    return DefaultTabController(
      length: 2,
      child: Builder(
        builder: (context) {
          final tabController = DefaultTabController.of(context);
          return AnimatedBuilder(
            animation: tabController,
            builder: (context, _) {
              final selectedTabIndex = tabController.index;
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Padding(
                    padding: const EdgeInsets.fromLTRB(28, 24, 28, 0),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        Text(
                          strings.buildTitle,
                          style: Theme.of(context).textTheme.headlineLarge,
                        ),
                        const SizedBox(height: 8),
                        Text(
                          strings.buildIntro,
                          style: Theme.of(context).textTheme.bodyLarge
                              ?.copyWith(color: scheme.onSurfaceVariant),
                        ),
                        const SizedBox(height: 20),
                        LayoutBuilder(
                          builder: (context, constraints) {
                            final wide = constraints.maxWidth >= 1100;
                            final tabs = TabBar(
                              isScrollable: true,
                              tabAlignment: TabAlignment.start,
                              tabs: <Tab>[
                                Tab(text: strings.buildTabRemote),
                                Tab(text: strings.buildTabLocal),
                              ],
                            );
                            final actions = _BuildTopActions(
                              state: state,
                              controller: controller,
                              selectedTabIndex: selectedTabIndex,
                            );
                            if (wide) {
                              return Row(
                                crossAxisAlignment: CrossAxisAlignment.center,
                                children: <Widget>[
                                  Expanded(child: tabs),
                                  const SizedBox(width: 16),
                                  actions,
                                ],
                              );
                            }
                            return Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: <Widget>[
                                tabs,
                                const SizedBox(height: 12),
                                Align(
                                  alignment: Alignment.centerRight,
                                  child: actions,
                                ),
                              ],
                            );
                          },
                        ),
                      ],
                    ),
                  ),
                  if (state.lastError != null)
                    Padding(
                      padding: const EdgeInsets.fromLTRB(28, 16, 28, 0),
                      child: _BannerCard(
                        title: strings.buildErrorTitle,
                        message: state.lastError!,
                        color: scheme.errorContainer,
                        foreground: scheme.onErrorContainer,
                      ),
                    ),
                  if (state.infoMessage != null)
                    Padding(
                      padding: const EdgeInsets.fromLTRB(28, 16, 28, 0),
                      child: _BannerCard(
                        title: strings.buildTaskGroup,
                        message: state.infoMessage!,
                        color: scheme.primaryContainer,
                        foreground: scheme.onPrimaryContainer,
                      ),
                    ),
                  if (state.isRefreshing)
                    Padding(
                      padding: const EdgeInsets.fromLTRB(28, 16, 28, 0),
                      child: DecoratedBox(
                        decoration: BoxDecoration(
                          color: scheme.secondaryContainer.withValues(alpha: 0.7),
                          borderRadius: BorderRadius.circular(20),
                          border: Border.all(color: scheme.outlineVariant),
                        ),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 20,
                            vertical: 14,
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: <Widget>[
                              Text(
                                strings.refreshing,
                                style: theme.textTheme.titleMedium?.copyWith(
                                  color: scheme.onSecondaryContainer,
                                ),
                              ),
                              const SizedBox(height: 10),
                              const LinearProgressIndicator(minHeight: 5),
                            ],
                          ),
                        ),
                      ),
                    ),
                  const SizedBox(height: 16),
                  Expanded(
                    child: LayoutBuilder(
                      builder: (context, constraints) {
                        final showSidebar = constraints.maxWidth >= 1180;
                        final content = TabBarView(
                          children: <Widget>[
                            _RemoteBuildTab(
                              state: state,
                              controller: controller,
                              showQueueCard: !showSidebar,
                            ),
                            _LocalBuildTab(
                              state: state,
                              controller: controller,
                              showQueueCard: !showSidebar,
                            ),
                          ],
                        );
                        if (!showSidebar) {
                          return content;
                        }
                        final queueSpec = _queueSpecForTab(
                          selectedTabIndex,
                          strings,
                        );
                        final entries = _buildQueueEntries(
                          state,
                          strings,
                          taskKinds: queueSpec.taskKinds,
                          includeTakeoverRuns: queueSpec.includeTakeoverRuns,
                        );
                        return Row(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: <Widget>[
                            Expanded(child: content),
                            _BuildTaskSidebar(
                              title: queueSpec.title,
                              subtitle: queueSpec.subtitle,
                              entries: entries,
                              expanded: _taskSidebarExpanded,
                              selectedEntryId: _activeTaskWorkspaceEntryId,
                              onToggleExpanded: () {
                                setState(() {
                                  _taskSidebarExpanded = !_taskSidebarExpanded;
                                });
                              },
                              onSelectEntry: (entryId) {
                                unawaited(
                                  _openTaskWorkspaceEntryInWindow(entryId),
                                );
                              },
                            ),
                          ],
                        );
                      },
                    ),
                  ),
                ],
              );
            },
          );
        },
      ),
    );
  }
}

class _QueueSpec {
  const _QueueSpec({
    required this.title,
    required this.subtitle,
    required this.taskKinds,
    required this.includeTakeoverRuns,
  });

  final String title;
  final String subtitle;
  final Set<String> taskKinds;
  final bool includeTakeoverRuns;
}

_QueueSpec _queueSpecForTab(int tabIndex, AppStrings strings) {
  if (tabIndex == 0) {
    return _QueueSpec(
      title: strings.buildQueueTitle,
      subtitle: strings.buildQueueSubtitle,
      taskKinds: const <String>{
        'build.gki',
        'artifact.download',
        'workflow.download',
      },
      includeTakeoverRuns: true,
    );
  }
  return _QueueSpec(
    title: strings.buildLocalQueueTitle,
    subtitle: strings.buildLocalQueueSubtitle,
    taskKinds: const <String>{
      'local.build.init',
      'local.build.source.sync',
      'local.build.rebuild',
      'local.build.profile.build',
      'local.backend.install',
    },
    includeTakeoverRuns: false,
  );
}

class _BuildTopActions extends StatelessWidget {
  const _BuildTopActions({
    required this.state,
    required this.controller,
    required this.selectedTabIndex,
  });

  final BuildPageState state;
  final BuildPageController controller;
  final int selectedTabIndex;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    if (selectedTabIndex == 0) {
      return Wrap(
        spacing: 12,
        runSpacing: 12,
        alignment: WrapAlignment.end,
        children: <Widget>[
          FilledButton.tonalIcon(
            onPressed: state.isRefreshing ? null : controller.refreshAll,
            icon: const Icon(Icons.refresh_rounded),
            label: Text(strings.buildRefreshAll),
          ),
          FilledButton(
            onPressed: state.canBuild ? controller.submitBuild : null,
            child: Text(
              state.isSubmitting
                  ? strings.buildSubmitting
                  : strings.buildSubmit,
            ),
          ),
        ],
      );
    }

    final selectedSource = state.selectedLocalSourceInstance;
    final activeLocalTask = state.activeLocalTask;
    final localBusy = state.localBuildStatusLoading || activeLocalTask != null;
    final initTaskRunning =
        activeLocalTask?.kind == 'local.build.init' ||
        activeLocalTask?.kind == 'local.build.source.sync' ||
        activeLocalTask?.kind == 'local.backend.install';
    final buildTaskRunning =
        activeLocalTask?.kind == 'local.build.rebuild' ||
        activeLocalTask?.kind == 'local.build.profile.build';

    Future<void> runWithAuthorization(
      Future<void> Function(String? sudoPassword) action,
    ) async {
      final backend = state.effectiveLocalBackendDescriptor;
      if (backend?.authorizationRequired == true) {
        final password = await _showLocalAuthorizationDialog(context, backend!);
        if (password == null) {
          return;
        }
        await action(password);
        return;
      }
      await action(null);
    }

    return Wrap(
      spacing: 12,
      runSpacing: 12,
      alignment: WrapAlignment.end,
      children: <Widget>[
        FilledButton.tonalIcon(
          onPressed: state.localBuildStatusLoading
              ? null
              : controller.refreshLocalBuildStatus,
          icon: Icon(localBusy ? Icons.sync_rounded : Icons.refresh_rounded),
          label: Text(
            state.localBuildStatusLoading
                ? strings.buildLocalRefreshRunningAction
                : strings.buildLocalRefresh,
          ),
        ),
        FilledButton.tonal(
          onPressed: state.isSubmitting || localBusy
              ? null
              : () => runWithAuthorization(
                  (password) =>
                      controller.startLocalBuildInit(sudoPassword: password),
                ),
          child: Text(
            initTaskRunning
                ? strings.buildLocalInitRunningAction
                : strings.buildLocalInitAction,
          ),
        ),
        FilledButton(
          onPressed:
              state.isSubmitting || localBusy || selectedSource?.isReady != true
              ? null
              : () => runWithAuthorization(
                  (password) =>
                      controller.startLocalBuildRebuild(sudoPassword: password),
                ),
          child: Text(
            buildTaskRunning
                ? strings.buildLocalBuildRunningAction
                : state.isSubmitting
                ? strings.buildSubmitting
                : strings.buildLocalRebuildAction,
          ),
        ),
      ],
    );
  }
}

class _BuildTaskSidebar extends StatelessWidget {
  const _BuildTaskSidebar({
    required this.title,
    required this.subtitle,
    required this.entries,
    required this.expanded,
    required this.selectedEntryId,
    required this.onToggleExpanded,
    required this.onSelectEntry,
  });

  final String title;
  final String subtitle;
  final List<_BuildQueueEntry> entries;
  final bool expanded;
  final String? selectedEntryId;
  final VoidCallback onToggleExpanded;
  final ValueChanged<String> onSelectEntry;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return AnimatedContainer(
      duration: const Duration(milliseconds: 220),
      curve: Curves.easeOutCubic,
      width: expanded ? 420 : 76,
      margin: const EdgeInsets.only(right: 28, bottom: 32),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: scheme.surface.withValues(alpha: 0.94),
          borderRadius: BorderRadius.circular(24),
          border: Border.all(
            color: scheme.outlineVariant.withValues(alpha: 0.4),
          ),
          boxShadow: <BoxShadow>[
            BoxShadow(
              color: scheme.shadow.withValues(alpha: 0.08),
              blurRadius: 24,
              offset: const Offset(0, 12),
            ),
          ],
        ),
        child: expanded
            ? Padding(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Row(
                      children: <Widget>[
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: <Widget>[
                              Text(title, style: theme.textTheme.titleMedium),
                              const SizedBox(height: 4),
                              Text(
                                subtitle,
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: scheme.onSurfaceVariant,
                                ),
                              ),
                            ],
                          ),
                        ),
                        StatusPill(
                          label: '${entries.length}',
                          color: scheme.primary,
                          icon: Icons.queue_rounded,
                        ),
                        const SizedBox(width: 8),
                        IconButton(
                          onPressed: onToggleExpanded,
                          icon: const Icon(
                            Icons.keyboard_double_arrow_right_rounded,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    Expanded(
                      child: entries.isEmpty
                          ? Center(
                              child: Text(
                                context.strings.buildNoTasks,
                                style: theme.textTheme.bodyMedium?.copyWith(
                                  color: scheme.onSurfaceVariant,
                                ),
                              ),
                            )
                          : Scrollbar(
                              thumbVisibility: true,
                              child: ListView.separated(
                                itemCount: entries.length,
                                separatorBuilder: (_, _) =>
                                    const SizedBox(height: 10),
                                itemBuilder: (context, index) {
                                  final entry = entries[index];
                                  return _SidebarQueueTile(
                                    entry: entry,
                                    selected: selectedEntryId == entry.id,
                                    onTap: () => onSelectEntry(entry.id),
                                  );
                                },
                              ),
                            ),
                    ),
                  ],
                ),
              )
            : Column(
                children: <Widget>[
                  const SizedBox(height: 12),
                  IconButton(
                    onPressed: onToggleExpanded,
                    icon: const Icon(Icons.keyboard_double_arrow_left_rounded),
                  ),
                  const SizedBox(height: 12),
                  Expanded(
                    child: entries.isEmpty
                        ? const SizedBox.shrink()
                        : Column(
                            children: entries
                                .take(6)
                                .map(
                                  (entry) => Padding(
                                    padding: const EdgeInsets.only(bottom: 10),
                                    child: Tooltip(
                                      message: entry.headline,
                                      child: Container(
                                        width: 42,
                                        height: 42,
                                        decoration: BoxDecoration(
                                          color: _taskStateColor(
                                            scheme,
                                            entry.state,
                                          ).withValues(alpha: 0.16),
                                          borderRadius: BorderRadius.circular(
                                            14,
                                          ),
                                        ),
                                        child: Icon(
                                          _taskStateIcon(entry.state),
                                          color: _taskStateColor(
                                            scheme,
                                            entry.state,
                                          ),
                                        ),
                                      ),
                                    ),
                                  ),
                                )
                                .toList(growable: false),
                          ),
                  ),
                ],
              ),
      ),
    );
  }
}

class _SidebarQueueTile extends StatelessWidget {
  const _SidebarQueueTile({
    required this.entry,
    required this.selected,
    required this.onTap,
  });

  final _BuildQueueEntry entry;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final stateColor = _taskStateColor(scheme, entry.state);
    return Material(
      color: Colors.transparent,
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: onTap,
        child: Container(
          decoration: BoxDecoration(
            color: selected
                ? scheme.primaryContainer.withValues(alpha: 0.68)
                : scheme.surfaceContainerHighest.withValues(alpha: 0.24),
            borderRadius: BorderRadius.circular(18),
            border: Border.all(
              color: selected
                  ? scheme.primary
                  : scheme.outlineVariant.withValues(alpha: 0.34),
            ),
          ),
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Row(
                children: <Widget>[
                  Expanded(
                    child: Text(
                      entry.headline,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.titleSmall,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Icon(Icons.open_in_new_rounded, size: 18, color: stateColor),
                ],
              ),
              const SizedBox(height: 6),
              Text(
                entry.currentStep,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: scheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _TaskWorkspaceWindowPanel extends StatelessWidget {
  const _TaskWorkspaceWindowPanel({
    required this.entries,
    required this.activeEntryId,
    required this.onSelectEntry,
    required this.onCloseEntry,
    required this.onCloseWindow,
  });

  final List<_BuildQueueEntry> entries;
  final String? activeEntryId;
  final ValueChanged<String> onSelectEntry;
  final ValueChanged<String> onCloseEntry;
  final VoidCallback onCloseWindow;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final activeEntry =
        entries.where((entry) => entry.id == activeEntryId).firstOrNull ??
        entries.firstOrNull;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: scheme.surface.withValues(alpha: 0.98),
        borderRadius: BorderRadius.circular(28),
        border: Border.all(
          color: scheme.outlineVariant.withValues(alpha: 0.42),
        ),
        boxShadow: <BoxShadow>[
          BoxShadow(
            color: scheme.shadow.withValues(alpha: 0.14),
            blurRadius: 36,
            offset: const Offset(0, 18),
          ),
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(18, 18, 18, 18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: entries
                    .map(
                      (entry) => Padding(
                        padding: const EdgeInsets.only(right: 10),
                        child: _TaskWorkspaceTab(
                          entry: entry,
                          selected: entry.id == activeEntry?.id,
                          onTap: () => onSelectEntry(entry.id),
                          onClose: () => onCloseEntry(entry.id),
                        ),
                      ),
                    )
                    .toList(growable: false),
              ),
            ),
            const SizedBox(height: 12),
            Expanded(
              child: activeEntry == null
                  ? const SizedBox.shrink()
                  : activeEntry.task != null
                  ? _TaskWorkspaceTaskBody(task: activeEntry.task!)
                  : _TaskWorkspaceRunBody(run: activeEntry.run!),
            ),
          ],
        ),
      ),
    );
  }
}

class _TaskWorkspaceTab extends StatelessWidget {
  const _TaskWorkspaceTab({
    required this.entry,
    required this.selected,
    required this.onTap,
    required this.onClose,
  });

  final _BuildQueueEntry entry;
  final bool selected;
  final VoidCallback onTap;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: onTap,
        child: Container(
          constraints: const BoxConstraints(minWidth: 180, maxWidth: 280),
          decoration: BoxDecoration(
            color: selected
                ? scheme.primaryContainer.withValues(alpha: 0.72)
                : scheme.surfaceContainerHighest.withValues(alpha: 0.3),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: selected
                  ? scheme.primary
                  : scheme.outlineVariant.withValues(alpha: 0.34),
            ),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          child: Row(
            children: <Widget>[
              Expanded(
                child: Text(
                  entry.headline,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const SizedBox(width: 8),
              InkWell(
                borderRadius: BorderRadius.circular(999),
                onTap: onClose,
                child: const Padding(
                  padding: EdgeInsets.all(2),
                  child: Icon(Icons.close_rounded, size: 16),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class TaskWorkspaceWindowPage extends ConsumerStatefulWidget {
  const TaskWorkspaceWindowPage({super.key, required this.stateFilePath});

  final String stateFilePath;

  @override
  ConsumerState<TaskWorkspaceWindowPage> createState() =>
      _TaskWorkspaceWindowPageState();
}

class _TaskWorkspaceWindowPageState
    extends ConsumerState<TaskWorkspaceWindowPage> {
  Timer? _pollTimer;
  _TaskWorkspaceStateFile _workspaceState = const _TaskWorkspaceStateFile();
  List<_BuildQueueEntry> _entries = const <_BuildQueueEntry>[];

  @override
  void initState() {
    super.initState();
    unawaited(windowManager.setTitle('ABK • 任务窗口'));
    unawaited(_refreshWorkspace());
    _pollTimer = Timer.periodic(
      const Duration(milliseconds: 800),
      (_) => unawaited(_refreshWorkspace()),
    );
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    unawaited(_releaseTaskWorkspaceWindowLock(widget.stateFilePath));
    super.dispose();
  }

  Future<void> _refreshWorkspace() async {
    if (widget.stateFilePath.trim().isEmpty) {
      return;
    }
    final strings = AppStrings.of(context);
    await _writeTaskWorkspaceWindowLock(widget.stateFilePath);
    final stateFile =
        await _readTaskWorkspaceState(widget.stateFilePath) ??
        const _TaskWorkspaceStateFile();
    final api = ref.read(sidecarApiProvider);
    final entries = <_BuildQueueEntry>[];
    for (final id in stateFile.entryIds) {
      final entry = await _resolveTaskWorkspaceEntryFromApi(api, strings, id);
      if (entry != null) {
        entries.add(entry);
      }
    }
    if (!mounted) return;
    setState(() {
      _workspaceState = stateFile;
      _entries = entries;
    });
  }

  Future<void> _selectEntry(String entryId) async {
    final next = _workspaceState.copyWith(activeEntryId: entryId);
    await _writeTaskWorkspaceState(widget.stateFilePath, next);
    if (!mounted) return;
    setState(() {
      _workspaceState = next;
    });
  }

  Future<void> _closeEntry(String entryId) async {
    final nextIds = _workspaceState.entryIds
        .where((id) => id != entryId)
        .toList(growable: false);
    final next = _workspaceState.copyWith(
      entryIds: nextIds,
      activeEntryId: nextIds.isEmpty
          ? null
          : (_workspaceState.activeEntryId == entryId
                ? nextIds.last
                : _workspaceState.activeEntryId),
    );
    await _writeTaskWorkspaceState(widget.stateFilePath, next);
    if (!mounted) return;
    setState(() {
      _workspaceState = next;
      _entries = _entries
          .where((entry) => entry.id != entryId)
          .toList(growable: false);
    });
    if (nextIds.isEmpty) {
      await _releaseTaskWorkspaceWindowLock(widget.stateFilePath);
      if (mounted) {
        unawaited(windowManager.close());
      }
    }
  }

  Future<void> _closeWindow() async {
    await _writeTaskWorkspaceState(
      widget.stateFilePath,
      const _TaskWorkspaceStateFile(),
    );
    await _releaseTaskWorkspaceWindowLock(widget.stateFilePath);
    if (mounted) {
      unawaited(windowManager.close());
    }
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final activeEntry =
        _entries
            .where((entry) => entry.id == _workspaceState.activeEntryId)
            .firstOrNull ??
        _entries.firstOrNull;
    return Scaffold(
      body: DecoratedBox(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: <Color>[
              scheme.surface,
              scheme.primaryContainer.withValues(alpha: 0.34),
              scheme.tertiaryContainer.withValues(alpha: 0.26),
            ],
          ),
        ),
        child: SafeArea(
          minimum: const EdgeInsets.fromLTRB(18, 10, 18, 18),
          child: Column(
            children: <Widget>[
              const _TaskWindowChrome(),
              const SizedBox(height: 14),
              Expanded(
                child: _TaskWindowSurface(
                  child: _entries.isEmpty
                      ? Center(
                          child: Text(
                            '暂无任务',
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                        )
                      : _TaskWorkspaceWindowPanel(
                          entries: _entries,
                          activeEntryId: activeEntry?.id,
                          onSelectEntry: (entryId) =>
                              unawaited(_selectEntry(entryId)),
                          onCloseEntry: (entryId) =>
                              unawaited(_closeEntry(entryId)),
                          onCloseWindow: () => unawaited(_closeWindow()),
                        ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _TaskWindowChrome extends StatefulWidget {
  const _TaskWindowChrome();

  @override
  State<_TaskWindowChrome> createState() => _TaskWindowChromeState();
}

class _TaskWindowChromeState extends State<_TaskWindowChrome>
    with WindowListener {
  bool _isMaximized = false;

  @override
  void initState() {
    super.initState();
    windowManager.addListener(this);
    _syncWindowState();
  }

  Future<void> _syncWindowState() async {
    try {
      final maximized = await windowManager.isMaximized();
      if (!mounted) return;
      setState(() {
        _isMaximized = maximized;
      });
    } catch (_) {}
  }

  @override
  void dispose() {
    windowManager.removeListener(this);
    super.dispose();
  }

  @override
  void onWindowMaximize() {
    setState(() {
      _isMaximized = true;
    });
  }

  @override
  void onWindowUnmaximize() {
    setState(() {
      _isMaximized = false;
    });
  }

  Future<void> _minimize() async {
    try {
      await windowManager.minimize();
    } catch (_) {}
  }

  Future<void> _toggleMaximize() async {
    try {
      if (_isMaximized) {
        await windowManager.unmaximize();
      } else {
        await windowManager.maximize();
      }
    } catch (_) {}
  }

  Future<void> _close() async {
    try {
      await windowManager.close();
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final strings = context.strings;
    return Container(
      height: 48,
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: <Color>[
            scheme.surface.withValues(alpha: 0.96),
            scheme.surfaceContainerHigh.withValues(alpha: 0.92),
          ],
        ),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(
          color: scheme.outlineVariant.withValues(alpha: 0.42),
        ),
        boxShadow: <BoxShadow>[
          BoxShadow(
            color: scheme.shadow.withValues(alpha: 0.08),
            blurRadius: 18,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      child: Row(
        children: <Widget>[
          const SizedBox(width: 12),
          Expanded(
            child: DragToMoveArea(
              child: Row(
                children: <Widget>[
                  Container(
                    width: 28,
                    height: 28,
                    padding: const EdgeInsets.all(4),
                    decoration: BoxDecoration(
                      color: scheme.primaryContainer.withValues(alpha: 0.92),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Image.asset(
                      'assets/images/android_abk_foreground.png',
                    ),
                  ),
                  const SizedBox(width: 10),
                  Text(
                    strings.brandWordmark,
                    style: Theme.of(context).textTheme.titleSmall?.copyWith(
                      color: scheme.onSurface,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Container(
                    width: 6,
                    height: 6,
                    decoration: BoxDecoration(
                      color: scheme.primary,
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Text(
                    '任务窗口',
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: scheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
          ),
          _TaskWindowButton(icon: Icons.minimize_rounded, onPressed: _minimize),
          const SizedBox(width: 4),
          _TaskWindowButton(
            icon: _isMaximized
                ? Icons.filter_none_rounded
                : Icons.crop_square_rounded,
            onPressed: _toggleMaximize,
          ),
          const SizedBox(width: 4),
          _TaskWindowButton(
            icon: Icons.close_rounded,
            onPressed: _close,
            danger: true,
          ),
          const SizedBox(width: 8),
        ],
      ),
    );
  }
}

class _TaskWindowButton extends StatelessWidget {
  const _TaskWindowButton({
    required this.icon,
    required this.onPressed,
    this.danger = false,
  });

  final IconData icon;
  final VoidCallback onPressed;
  final bool danger;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onPressed,
        borderRadius: BorderRadius.circular(10),
        child: Container(
          width: 34,
          height: 34,
          decoration: BoxDecoration(
            color: danger
                ? scheme.errorContainer.withValues(alpha: 0.82)
                : scheme.surfaceContainerHighest.withValues(alpha: 0.68),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Icon(
            icon,
            size: 18,
            color: danger ? scheme.onErrorContainer : scheme.onSurfaceVariant,
          ),
        ),
      ),
    );
  }
}

class _TaskWindowSurface extends StatelessWidget {
  const _TaskWindowSurface({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: scheme.surface.withValues(alpha: 0.76),
        borderRadius: BorderRadius.circular(36),
        border: Border.all(
          color: scheme.outlineVariant.withValues(alpha: 0.34),
        ),
      ),
      child: ClipRRect(borderRadius: BorderRadius.circular(36), child: child),
    );
  }
}

class _TaskWorkspaceTaskBody extends ConsumerWidget {
  const _TaskWorkspaceTaskBody({required this.task});

  final DesktopTaskSnapshot task;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final strings = context.strings;
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final controller = ref.read(buildPageControllerProvider.notifier);
    final consoleLines = _taskConsoleLines(task);
    final resultLines = _taskResultLines(task);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Wrap(
          spacing: 10,
          runSpacing: 10,
          children: <Widget>[
            Text(
              _taskCurrentStep(strings, task),
              style: theme.textTheme.bodyMedium?.copyWith(
                color: scheme.onSurfaceVariant,
              ),
            ),
            StatusPill(
              label: strings.buildTaskStateLabel(task.state),
              color: _taskStateColor(scheme, task.state),
              icon: _taskStateIcon(task.state),
            ),
            if (task.isCancelable)
              FilledButton.tonalIcon(
                onPressed: () => controller.cancelTask(task.id),
                icon: const Icon(Icons.stop_circle_outlined),
                label: Text(strings.buildTaskCancelAction),
              ),
            if (task.primaryDownloadPath != null)
              OutlinedButton.icon(
                onPressed: () => _openDirectory(task.primaryDownloadPath!),
                icon: const Icon(Icons.folder_open_rounded),
                label: Text(strings.buildOpenDirectory),
              ),
          ],
        ),
        const SizedBox(height: 12),
        Expanded(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              Expanded(
                flex: 3,
                child: _TaskLogPanel(
                  title: strings.buildTaskConsoleTitle,
                  lines: consoleLines,
                  emptyLabel: strings.buildTaskNoOutput,
                  language: MonacoCodeLanguage.plaintext,
                  followTail: true,
                  incrementalAppends: true,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                flex: 2,
                child: _TaskLogPanel(
                  title: strings.buildTaskResultTitle,
                  lines: resultLines,
                  emptyLabel: strings.buildTaskNoResult,
                  language: MonacoCodeLanguage.json,
                  followTail: false,
                  incrementalAppends: false,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _TaskWorkspaceRunBody extends StatelessWidget {
  const _TaskWorkspaceRunBody({required this.run});

  final BuildRunSummary run;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        StatusPill(
          label: strings.buildRunStatusLabel(run),
          color: _taskStateColor(scheme, _runState(run)),
          icon: _taskStateIcon(_runState(run)),
        ),
        const SizedBox(height: 10),
        Text(
          _runPreviewText(run),
          style: Theme.of(
            context,
          ).textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
        ),
        const SizedBox(height: 14),
        if (run.htmlUrl?.trim().isNotEmpty == true)
          FilledButton.tonalIcon(
            onPressed: () => _openUrl(run.htmlUrl!.trim()),
            icon: const Icon(Icons.open_in_new_rounded),
            label: Text(strings.buildTaskOpenWorkflow),
          ),
      ],
    );
  }
}

class _RemoteBuildTab extends StatelessWidget {
  const _RemoteBuildTab({
    required this.state,
    required this.controller,
    required this.showQueueCard,
  });

  final BuildPageState state;
  final BuildPageController controller;
  final bool showQueueCard;

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(28, 0, 28, 32),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          _GitHubCard(state: state, controller: controller),
          const SizedBox(height: 16),
          _BuildFormCard(state: state, controller: controller),
          if (showQueueCard) ...<Widget>[
            const SizedBox(height: 16),
            _QueueCard(
              state: state,
              controller: controller,
              taskKinds: const <String>{
                'build.gki',
                'artifact.download',
                'workflow.download',
              },
              includeTakeoverRuns: true,
            ),
          ],
        ],
      ),
    );
  }
}

class _LocalBuildTab extends StatelessWidget {
  const _LocalBuildTab({
    required this.state,
    required this.controller,
    required this.showQueueCard,
  });

  final BuildPageState state;
  final BuildPageController controller;
  final bool showQueueCard;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final localBusy =
        state.localBuildStatusLoading || state.activeLocalTask != null;
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(28, 0, 28, 32),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          if (showQueueCard && localBusy) ...<Widget>[
            _LocalBuildActivityCard(state: state),
            const SizedBox(height: 16),
          ],
          _LocalBuildSourceCard(state: state, controller: controller),
          const SizedBox(height: 16),
          _BuildFormCard(
            state: state,
            controller: controller,
            scope: BuildFormScope.local,
            showSourceTargetFields: false,
            title: strings.buildLocalFormTitle,
            subtitle: strings.buildLocalFormSubtitle,
            showRuntimePrefillPills: false,
            showRuntimeSummary: false,
          ),
          const SizedBox(height: 16),
          _LocalBuildActionCard(state: state, controller: controller),
          if (showQueueCard) ...<Widget>[
            const SizedBox(height: 16),
            _QueueCard(
              state: state,
              controller: controller,
              title: strings.buildLocalQueueTitle,
              subtitle: strings.buildLocalQueueSubtitle,
              taskKinds: const <String>{
                'local.build.init',
                'local.build.source.sync',
                'local.build.rebuild',
                'local.build.profile.build',
                'local.backend.install',
              },
              includeTakeoverRuns: false,
            ),
          ],
        ],
      ),
    );
  }
}

class _GitHubCard extends ConsumerWidget {
  const _GitHubCard({required this.state, required this.controller});

  final BuildPageState state;
  final BuildPageController controller;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final strings = context.strings;
    final scheme = Theme.of(context).colorScheme;
    final session = state.session;
    final challenge = state.loginChallenge;
    final restoringSession =
        state.isBootstrapping &&
        session == null &&
        challenge == null &&
        state.lastError == null;

    return PanelCard(
      title: strings.buildAuthTitle,
      subtitle: strings.buildAuthSubtitle,
      icon: Icons.verified_user_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: <Widget>[
              StatusPill(
                label: restoringSession
                    ? strings.buildSessionRestoring
                    : session?.loggedIn == true
                    ? '${strings.buildLoggedInAs} ${session?.userLogin ?? ''}'
                    : strings.buildNoSession,
                color: restoringSession
                    ? scheme.secondary
                    : session?.loggedIn == true
                    ? scheme.primary
                    : scheme.error,
                icon: restoringSession
                    ? Icons.sync_rounded
                    : session?.loggedIn == true
                    ? Icons.person_rounded
                    : Icons.person_off_rounded,
              ),
              StatusPill(
                label: session?.forkFullName ?? strings.buildNeedsFork,
                color: session?.needsFork == false
                    ? scheme.secondary
                    : scheme.error,
                icon: Icons.fork_right_rounded,
              ),
              StatusPill(
                label: session?.signingKeyAvailable == true
                    ? (session?.signingKeySource == 'config'
                          ? strings.buildSignKeyGitHub
                          : session?.signingKeySource ??
                                strings.buildSignKeyUnknown)
                    : strings.buildSignKeyUnknown,
                color: session?.signingKeyAvailable == true
                    ? scheme.tertiary
                    : scheme.onSurfaceVariant,
                icon: Icons.key_rounded,
              ),
              if (session?.needsSync == true)
                StatusPill(
                  label: strings.buildNeedsSync,
                  color: scheme.error,
                  icon: Icons.sync_problem_rounded,
                ),
            ],
          ),
          const SizedBox(height: 18),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: <Widget>[
              if (!restoringSession && session?.loggedIn != true)
                FilledButton(
                  onPressed: state.isLoadingLogin || state.isPollingLogin
                      ? null
                      : () async {
                          final challenge = await controller.startLogin();
                          if (challenge == null) return;
                          if (!context.mounted) return;
                          await _copyToClipboard(
                            context,
                            challenge.userCode,
                            strings.buildLoginCopied,
                          );
                          await _openUrl(
                            challenge.verificationUriComplete ??
                                challenge.verificationUri,
                          );
                          unawaited(controller.pollLoginUntilAuthorized());
                        },
                  child: Text(
                    state.isPollingLogin
                        ? strings.buildLoginPolling
                        : strings.buildLogin,
                  ),
                ),
              if (session?.loggedIn == true && session?.needsFork == true)
                OutlinedButton(
                  onPressed: state.isForkBusy ? null : controller.ensureFork,
                  child: Text(strings.buildForkEnsure),
                ),
              if (session?.loggedIn == true &&
                  session?.needsFork == false &&
                  session?.needsSync == true)
                OutlinedButton(
                  onPressed: state.isForkBusy ? null : controller.syncFork,
                  child: Text(strings.buildForkSync),
                ),
            ],
          ),
          if (challenge != null) ...<Widget>[
            const SizedBox(height: 18),
            DecoratedBox(
              decoration: BoxDecoration(
                color: scheme.surfaceContainerHighest.withValues(alpha: 0.38),
                borderRadius: BorderRadius.circular(22),
                border: Border.all(
                  color: scheme.outlineVariant.withValues(alpha: 0.34),
                ),
              ),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(
                      strings.buildLoginCodeTitle,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 6),
                    Text(
                      strings.buildLoginCodeHint,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 14),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 14,
                      ),
                      decoration: BoxDecoration(
                        color: scheme.surface,
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(
                          color: scheme.outlineVariant.withValues(alpha: 0.34),
                        ),
                      ),
                      child: SelectableText(
                        challenge.userCode,
                        style: Theme.of(context).textTheme.headlineSmall
                            ?.copyWith(
                              letterSpacing: 1.2,
                              fontFeatures: const <FontFeature>[
                                FontFeature.tabularFigures(),
                              ],
                            ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    Wrap(
                      spacing: 12,
                      runSpacing: 12,
                      children: <Widget>[
                        FilledButton.tonalIcon(
                          onPressed: () => _copyToClipboard(
                            context,
                            challenge.userCode,
                            strings.buildLoginCopied,
                          ),
                          icon: const Icon(Icons.content_copy_rounded),
                          label: Text(strings.buildLoginCopyCode),
                        ),
                        OutlinedButton.icon(
                          onPressed: () => _openUrl(
                            challenge.verificationUriComplete ??
                                challenge.verificationUri,
                          ),
                          icon: const Icon(Icons.open_in_browser_rounded),
                          label: Text(strings.buildLoginOpenGitHub),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _LocalBuildActivityCard extends StatelessWidget {
  const _LocalBuildActivityCard({required this.state});

  final BuildPageState state;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final task = state.activeLocalTask;
    final label = task == null
        ? strings.buildLocalRefreshRunningAction
        : strings.buildTaskLabel(task.kind);
    final stateLabel = task == null
        ? strings.buildTaskStateLabel('running')
        : strings.buildTaskStateLabel(task.state);
    final currentStep = task == null
        ? strings.buildLocalRefreshRunningAction
        : _taskCurrentStep(strings, task);
    final preview = task == null ? '' : _taskTaskPreview(strings, task);

    return PanelCard(
      title: strings.buildLocalActivityTitle,
      subtitle: strings.buildLocalActivitySubtitle,
      icon: Icons.sync_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: <Widget>[
              StatusPill(
                label: label,
                color: scheme.primary,
                icon: Icons.build_circle_rounded,
              ),
              StatusPill(
                label: stateLabel,
                color: scheme.tertiary,
                icon: Icons.timelapse_rounded,
              ),
            ],
          ),
          const SizedBox(height: 14),
          LinearProgressIndicator(
            minHeight: 7,
            borderRadius: BorderRadius.circular(999),
          ),
          const SizedBox(height: 14),
          Text(currentStep, style: theme.textTheme.titleSmall),
          if (preview.trim().isNotEmpty) ...<Widget>[
            const SizedBox(height: 6),
            Text(
              preview,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: scheme.onSurfaceVariant,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _BuildFormCard extends StatelessWidget {
  const _BuildFormCard({
    required this.state,
    required this.controller,
    this.scope = BuildFormScope.remote,
    this.showSourceTargetFields = true,
    this.title,
    this.subtitle,
    this.showRuntimePrefillPills = true,
    this.showRuntimeSummary = true,
  });

  final BuildPageState state;
  final BuildPageController controller;
  final BuildFormScope scope;
  final bool showSourceTargetFields;
  final String? title;
  final String? subtitle;
  final bool showRuntimePrefillPills;
  final bool showRuntimeSummary;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final form = scope == BuildFormScope.remote ? state.form : state.localForm;
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final subLevelOptions = DesktopKernelSupport.subLevelOptions(
      form.androidVersion,
      form.kernelVersion,
    );
    final patchOptions = DesktopKernelSupport.patchLevelOptions(
      form.androidVersion,
      form.kernelVersion,
      form.subLevel,
    );
    final virtualizationOptions =
        DesktopKernelSupport.virtualizationSupportOptions(form.kernelVersion);
    final kpmSupported = DesktopKernelSupport.isKpmSupported(
      ksuVariant: form.ksuVariant,
      ksuBranch: form.ksuBranch,
    );
    void update(BuildFormState next) {
      if (scope == BuildFormScope.remote) {
        controller.updateForm(next);
      } else {
        controller.updateLocalForm(next);
      }
    }

    return PanelCard(
      title: title ?? strings.buildTargetTitle,
      subtitle: subtitle ?? strings.buildRuntimePrefillSubtitle,
      icon: Icons.tune_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          if (showRuntimePrefillPills) ...<Widget>[
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: <Widget>[
                StatusPill(
                  label: '${form.androidVersion} · ${form.kernelVersion}',
                  color: scheme.primary,
                  icon: Icons.layers_rounded,
                ),
                StatusPill(
                  label: strings.buildRuntimePrefill,
                  color: scheme.tertiary,
                  icon: Icons.auto_awesome_rounded,
                ),
                if (state.runtime != null)
                  StatusPill(
                    label: _runtimePreviewLabel(state.runtime!),
                    color: scheme.onSurfaceVariant,
                    icon: Icons.phone_android_rounded,
                  ),
              ],
            ),
            const SizedBox(height: 16),
          ],
          LayoutBuilder(
            builder: (context, constraints) {
              final wide = constraints.maxWidth >= 860;
              final width = wide
                  ? (constraints.maxWidth - 16) / 2
                  : constraints.maxWidth;
              final headerFields = <Widget>[
                if (showSourceTargetFields)
                  SizedBox(
                    width: width,
                    child: DropdownButtonFormField<String>(
                      key: ValueKey<String>(
                        'build-${scope.name}-android-${form.androidVersion}',
                      ),
                      initialValue: form.androidVersion,
                      decoration: InputDecoration(
                        labelText: strings.buildAndroidVersionLabel,
                      ),
                      items: DesktopKernelSupport.androidVersions()
                          .map(
                            (value) => DropdownMenuItem<String>(
                              value: value,
                              child: Text(value),
                            ),
                          )
                          .toList(growable: false),
                      onChanged: (value) {
                        if (value == null) return;
                        update(
                          form.copyWith(
                            androidVersion: value,
                            kernelVersion:
                                DesktopKernelSupport.kernelForAndroid(value),
                          ),
                        );
                      },
                    ),
                  ),
                if (showSourceTargetFields)
                  SizedBox(
                    width: width,
                    child: DropdownButtonFormField<String>(
                      key: ValueKey<String>(
                        'build-${scope.name}-kernel-${form.kernelVersion}',
                      ),
                      initialValue: form.kernelVersion,
                      decoration: InputDecoration(
                        labelText: strings.buildKernelVersionLabel,
                      ),
                      items: DesktopKernelSupport.kernelVersions()
                          .map(
                            (value) => DropdownMenuItem<String>(
                              value: value,
                              child: Text(value),
                            ),
                          )
                          .toList(growable: false),
                      onChanged: (value) {
                        if (value == null) return;
                        update(
                          form.copyWith(
                            androidVersion:
                                DesktopKernelSupport.androidForKernel(value),
                            kernelVersion: value,
                          ),
                        );
                      },
                    ),
                  ),
                if (showSourceTargetFields)
                  SizedBox(
                    width: width,
                    child: DropdownButtonFormField<String>(
                      key: ValueKey<String>(
                        'build-${scope.name}-sub-${form.androidVersion}-${form.kernelVersion}-${form.subLevel}',
                      ),
                      initialValue: form.subLevel,
                      decoration: InputDecoration(
                        labelText: strings.buildSubLevelLabel,
                      ),
                      items: subLevelOptions
                          .map(
                            (value) => DropdownMenuItem<String>(
                              value: value,
                              child: Text(value),
                            ),
                          )
                          .toList(growable: false),
                      onChanged: (value) {
                        if (value == null) return;
                        update(form.copyWith(subLevel: value));
                      },
                    ),
                  ),
                if (showSourceTargetFields)
                  SizedBox(
                    width: width,
                    child: DropdownButtonFormField<String>(
                      key: ValueKey<String>(
                        'build-${scope.name}-patch-${form.androidVersion}-${form.kernelVersion}-${form.subLevel}-${form.osPatchLevel}',
                      ),
                      initialValue: form.osPatchLevel,
                      decoration: InputDecoration(
                        labelText: strings.buildPatchLevelLabel,
                      ),
                      items: patchOptions
                          .map(
                            (value) => DropdownMenuItem<String>(
                              value: value,
                              child: Text(value),
                            ),
                          )
                          .toList(growable: false),
                      onChanged: (value) {
                        if (value == null) return;
                        update(form.copyWith(osPatchLevel: value));
                      },
                    ),
                  ),
              ];
              return Wrap(
                spacing: 16,
                runSpacing: 12,
                children: <Widget>[
                  ...headerFields,
                  SizedBox(
                    width: width,
                    child: DropdownButtonFormField<String>(
                      key: ValueKey<String>(
                        'build-${scope.name}-ksu-variant-${form.ksuVariant}',
                      ),
                      initialValue: form.ksuVariant,
                      decoration: InputDecoration(
                        labelText: strings.buildKsuTitle,
                      ),
                      items: DesktopKernelSupport.ksuVariantOptions
                          .map(
                            (value) => DropdownMenuItem<String>(
                              value: value,
                              child: Text(value),
                            ),
                          )
                          .toList(growable: false),
                      onChanged: (value) {
                        if (value == null) return;
                        update(form.copyWith(ksuVariant: value));
                      },
                    ),
                  ),
                  SizedBox(
                    width: width,
                    child: DropdownButtonFormField<String>(
                      key: ValueKey<String>(
                        'build-${scope.name}-ksu-branch-${form.ksuVariant}-${form.ksuBranch}',
                      ),
                      initialValue: form.ksuBranch,
                      decoration: InputDecoration(
                        labelText: strings.buildKsuBranchLabel,
                      ),
                      items: DesktopKernelSupport.ksuBranchOptions
                          .map(
                            (value) => DropdownMenuItem<String>(
                              value: value,
                              child: Text(value),
                            ),
                          )
                          .toList(growable: false),
                      onChanged: form.ksuVariant == 'None'
                          ? null
                          : (value) {
                              if (value == null) return;
                              update(form.copyWith(ksuBranch: value));
                            },
                    ),
                  ),
                  if (form.ksuBranch == 'Custom' && form.ksuVariant != 'None')
                    SizedBox(
                      width: width,
                      child: _SyncedTextFormField(
                        fieldKey: ValueKey<String>(
                          'build-${scope.name}-custom-ref-${form.customRef}',
                        ),
                        value: form.customRef,
                        decoration: InputDecoration(
                          labelText: strings.buildCustomRefLabel,
                        ),
                        onChanged: (value) =>
                            update(form.copyWith(customRef: value)),
                      ),
                    ),
                  SizedBox(
                    width: width,
                    child: _SyncedTextFormField(
                      fieldKey: ValueKey<String>(
                        'build-${scope.name}-version-${form.version}',
                      ),
                      value: form.version,
                      decoration: InputDecoration(
                        labelText: strings.buildVersionTitle,
                      ),
                      onChanged: (value) =>
                          update(form.copyWith(version: value)),
                    ),
                  ),
                  if (form.kernelVersion == '5.10')
                    SizedBox(
                      width: width,
                      child: _SyncedTextFormField(
                        fieldKey: ValueKey<String>(
                          'build-${scope.name}-revision-${form.revision}',
                        ),
                        value: form.revision,
                        decoration: InputDecoration(
                          labelText: strings.buildRevisionLabel,
                        ),
                        onChanged: (value) =>
                            update(form.copyWith(revision: value)),
                      ),
                    ),
                  SizedBox(
                    width: width,
                    child: DropdownButtonFormField<String>(
                      key: ValueKey<String>(
                        'build-${scope.name}-virt-${form.kernelVersion}-${form.virt}',
                      ),
                      initialValue: form.virt,
                      decoration: InputDecoration(
                        labelText: strings.buildVirtLabel,
                      ),
                      items: virtualizationOptions
                          .map(
                            (value) => DropdownMenuItem<String>(
                              value: value,
                              child: Text(value),
                            ),
                          )
                          .toList(growable: false),
                      onChanged: (value) {
                        if (value == null) return;
                        update(form.copyWith(virt: value));
                      },
                    ),
                  ),
                ],
              );
            },
          ),
          const SizedBox(height: 18),
          Text(strings.buildFeatureTitle, style: theme.textTheme.titleMedium),
          const SizedBox(height: 8),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: <Widget>[
              _BoolChip(
                label: 'SUSFS',
                value: form.susfs,
                onChanged: (value) => update(form.copyWith(susfs: value)),
              ),
              _BoolChip(
                label: 'ZRAM',
                value: form.zram,
                onChanged: (value) => update(form.copyWith(zram: value)),
              ),
              _BoolChip(
                label: 'BBG',
                value: form.bbg,
                onChanged: (value) => update(form.copyWith(bbg: value)),
              ),
              _BoolChip(
                label: 'DDK',
                value: form.ddk,
                onChanged: (value) => update(form.copyWith(ddk: value)),
              ),
              _BoolChip(
                label: 'KPM',
                value: form.kpm,
                enabled: kpmSupported,
                onChanged: (value) => update(form.copyWith(kpm: value)),
              ),
              _BoolChip(
                label: 'ReKernel',
                value: form.rekernel,
                onChanged: (value) => update(form.copyWith(rekernel: value)),
              ),
              _BoolChip(
                label: 'NTSync',
                value: form.ntsync,
                onChanged: (value) => update(form.copyWith(ntsync: value)),
              ),
              _BoolChip(
                label: 'Networking',
                value: form.networking,
                onChanged: (value) => update(form.copyWith(networking: value)),
              ),
              _BoolChip(
                label: 'ZRAM algo',
                value: form.zramFullAlgo,
                onChanged: (value) =>
                    update(form.copyWith(zramFullAlgo: value)),
              ),
            ],
          ),
          const SizedBox(height: 14),
          Material(
            color: Colors.transparent,
            child: ExpansionTile(
              initiallyExpanded: form.advancedOpen,
              onExpansionChanged: (expanded) =>
                  update(form.copyWith(advancedOpen: expanded)),
              title: Text(strings.buildAdvancedTitle),
              children: <Widget>[
                const SizedBox(height: 12),
                _TextRow(
                  width: 420,
                  label: strings.buildBuildTimeLabel,
                  value: form.buildTime,
                  onChanged: (value) => update(form.copyWith(buildTime: value)),
                ),
                _TextRow(
                  width: 420,
                  label: strings.buildKpmPasswordLabel,
                  value: form.kpmPassword,
                  obscure: true,
                  onChanged: (value) =>
                      update(form.copyWith(kpmPassword: value)),
                ),
                _TextRow(
                  width: 420,
                  label: strings.buildZramExtraAlgosLabel,
                  value: form.zramExtraAlgos,
                  onChanged: (value) =>
                      update(form.copyWith(zramExtraAlgos: value)),
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          _CustomModulesCard(
            state: state,
            controller: controller,
            scope: scope,
          ),
          const SizedBox(height: 18),
          if (showRuntimeSummary && state.runtime != null)
            _RuntimeSummaryCard(runtime: state.runtime!),
        ],
      ),
    );
  }
}

class _LocalBuildSourceCard extends StatefulWidget {
  const _LocalBuildSourceCard({required this.state, required this.controller});

  final BuildPageState state;
  final BuildPageController controller;

  @override
  State<_LocalBuildSourceCard> createState() => _LocalBuildSourceCardState();
}

class _LocalBuildSourceCardState extends State<_LocalBuildSourceCard> {
  late final TextEditingController _branchMonthController;
  late final TextEditingController _scriptRootController;
  late final TextEditingController _workspaceDirController;
  late final TextEditingController _profileStoreDirController;

  @override
  void initState() {
    super.initState();
    _branchMonthController = TextEditingController(
      text: widget.state.localBuildBranchMonth,
    );
    _scriptRootController = TextEditingController(
      text: widget.state.localScriptRootDirDraft,
    );
    _workspaceDirController = TextEditingController(
      text: widget.state.localWorkspaceDirDraft,
    );
    _profileStoreDirController = TextEditingController(
      text: widget.state.localProfileStoreDirDraft,
    );
  }

  @override
  void didUpdateWidget(covariant _LocalBuildSourceCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    _syncController(_branchMonthController, widget.state.localBuildBranchMonth);
    _syncController(
      _scriptRootController,
      widget.state.localScriptRootDirDraft,
    );
    _syncController(
      _workspaceDirController,
      widget.state.localWorkspaceDirDraft,
    );
    _syncController(
      _profileStoreDirController,
      widget.state.localProfileStoreDirDraft,
    );
  }

  @override
  void dispose() {
    _branchMonthController.dispose();
    _scriptRootController.dispose();
    _workspaceDirController.dispose();
    _profileStoreDirController.dispose();
    super.dispose();
  }

  void _syncController(TextEditingController controller, String value) {
    if (controller.text == value) {
      return;
    }
    controller.value = controller.value.copyWith(
      text: value,
      selection: TextSelection.collapsed(offset: value.length),
      composing: TextRange.empty,
    );
  }

  Future<void> _pickDirectory(void Function(String value) updateDraft) async {
    final path = await _pickDirectoryPath();
    if (path == null || path.isEmpty) return;
    updateDraft(path);
  }

  Widget _directorySettingField({
    required BuildContext context,
    required TextEditingController controller,
    required ValueChanged<String> onChanged,
    required String label,
    required VoidCallback onPick,
  }) {
    final strings = context.strings;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Expanded(
          child: TextField(
            controller: controller,
            onChanged: onChanged,
            decoration: InputDecoration(
              labelText: label,
              border: const OutlineInputBorder(),
            ),
          ),
        ),
        const SizedBox(width: 12),
        FilledButton.tonalIcon(
          onPressed: onPick,
          icon: const Icon(Icons.folder_open_rounded),
          label: Text(strings.settingsChooseDirectory),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final selectedSource = widget.state.selectedLocalSourceInstance;
    final backends = widget.state.localBackends;
    final runtime = selectedSource?.materialized;
    final localBusy =
        widget.state.localBuildStatusLoading ||
        widget.state.isSubmitting ||
        widget.state.activeLocalTask != null;
    final backendIssues = backends
        .where(
          (backend) =>
              (backend.detail?.trim().isNotEmpty ?? false) ||
              backend.installSupported,
        )
        .toList(growable: false);

    Future<void> installBackend(LocalBuildBackendDescriptor backend) async {
      String? password;
      if (backend.authorizationRequired) {
        password = await _showLocalAuthorizationDialog(context, backend);
        if (password == null) {
          return;
        }
      }
      await widget.controller.installLocalBackend(
        backend,
        sudoPassword: password,
      );
    }

    return PanelCard(
      title: strings.buildLocalSourceTitle,
      subtitle: strings.buildLocalSourceSubtitle,
      icon: Icons.source_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text('Backend & environment', style: theme.textTheme.titleMedium),
          const SizedBox(height: 8),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: <Widget>[
              ...backends.map(
                (backend) => StatusPill(
                  label: backend.label,
                  color: backend.available ? scheme.primary : scheme.outline,
                  icon: backend.available
                      ? Icons.verified_rounded
                      : Icons.warning_amber_rounded,
                ),
              ),
            ],
          ),
          if (backendIssues.isNotEmpty) ...<Widget>[
            const SizedBox(height: 14),
            Text(
              strings.buildLocalBackendIssuesTitle,
              style: theme.textTheme.titleSmall,
            ),
            const SizedBox(height: 8),
            Column(
              children: backendIssues
                  .map(
                    (backend) => Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: Container(
                        width: double.infinity,
                        decoration: BoxDecoration(
                          color: scheme.surfaceContainerHighest.withValues(
                            alpha: 0.22,
                          ),
                          borderRadius: BorderRadius.circular(18),
                          border: Border.all(
                            color: scheme.outlineVariant.withValues(alpha: 0.35),
                          ),
                        ),
                        padding: const EdgeInsets.all(14),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: <Widget>[
                            Text(
                              backend.label,
                              style: theme.textTheme.titleSmall,
                            ),
                            if (backend.detail?.trim().isNotEmpty == true) ...<Widget>[
                              const SizedBox(height: 6),
                              Text(
                                backend.detail!,
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: scheme.onSurfaceVariant,
                                ),
                              ),
                            ],
                            if (backend.installDetail?.trim().isNotEmpty ==
                                true) ...<Widget>[
                              const SizedBox(height: 6),
                              Text(
                                backend.installDetail!,
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: scheme.onSurfaceVariant,
                                ),
                              ),
                            ],
                            if (backend.installSupported) ...<Widget>[
                              const SizedBox(height: 12),
                              FilledButton.tonalIcon(
                                onPressed: localBusy
                                    ? null
                                    : () => installBackend(backend),
                                icon: const Icon(Icons.download_rounded),
                                label: Text(
                                  backend.installLabel ??
                                      strings.buildLocalBackendInstallAction,
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                    ),
                  )
                  .toList(growable: false),
            ),
          ],
          const SizedBox(height: 12),
          DropdownButtonFormField<LocalBuildBackendKind>(
            initialValue: widget.state.localSettings?.globalDefaultBackendKind,
            decoration: InputDecoration(
              labelText: strings.buildLocalGlobalBackendLabel,
            ),
            items: backends
                .map(
                  (backend) => DropdownMenuItem<LocalBuildBackendKind>(
                    value: backend.kind,
                    child: Text(backend.label),
                  ),
                )
                .toList(growable: false),
            onChanged: (value) {
              if (value == null) return;
              widget.controller.updateLocalDefaultBackendKind(value);
            },
          ),
          const SizedBox(height: 16),
          Text(
            strings.buildLocalDirectoriesTitle,
            style: theme.textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          Text(
            strings.buildLocalDirectoriesSubtitle,
            style: theme.textTheme.bodySmall?.copyWith(
              color: scheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 12),
          _directorySettingField(
            context: context,
            controller: _scriptRootController,
            onChanged: widget.controller.updateLocalScriptRootDirDraft,
            label: strings.buildLocalScriptRootLabel,
            onPick: () =>
                _pickDirectory(widget.controller.updateLocalScriptRootDirDraft),
          ),
          const SizedBox(height: 12),
          _directorySettingField(
            context: context,
            controller: _workspaceDirController,
            onChanged: widget.controller.updateLocalWorkspaceDirDraft,
            label: strings.buildLocalWorkspaceDirSettingLabel,
            onPick: () =>
                _pickDirectory(widget.controller.updateLocalWorkspaceDirDraft),
          ),
          const SizedBox(height: 12),
          _directorySettingField(
            context: context,
            controller: _profileStoreDirController,
            onChanged: widget.controller.updateLocalProfileStoreDirDraft,
            label: strings.buildLocalProfileStoreDirLabel,
            onPick: () => _pickDirectory(
              widget.controller.updateLocalProfileStoreDirDraft,
            ),
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: <Widget>[
              FilledButton(
                onPressed: widget.state.localSettingsSaving
                    ? null
                    : widget.controller.saveLocalDirectorySettings,
                child: widget.state.localSettingsSaving
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2.2),
                      )
                    : Text(strings.buildLocalSaveDirectories),
              ),
              FilledButton.tonal(
                onPressed: widget.state.localSettingsSaving
                    ? null
                    : widget.controller.restoreLocalDirectorySettingsDefaults,
                child: Text(strings.buildLocalRestoreDirectories),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Text(
            strings.buildLocalSupportedLinesTitle,
            style: theme.textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          if (widget.state.localCatalog.isEmpty)
            Text(strings.buildLocalNoSupportedTemplates)
          else
            Wrap(
              spacing: 10,
              runSpacing: 10,
              children: widget.state.localCatalog
                  .map((line) {
                    final selected =
                        line.id == widget.state.localSourceKernelLineId;
                    return ChoiceChip(
                      label: Text(line.displayName),
                      selected: selected,
                      onSelected: (_) {
                        widget.controller.updateLocalSourceKernelLineId(
                          line.id,
                        );
                      },
                    );
                  })
                  .toList(growable: false),
            ),
          const SizedBox(height: 16),
          TextField(
            controller: _branchMonthController,
            onChanged: widget.controller.updateLocalBuildBranchMonth,
            decoration: InputDecoration(
              labelText: strings.buildLocalBranchMonthLabel,
              helperText: strings.buildLocalBranchMonthHint,
              border: const OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 12),
          SwitchListTile.adaptive(
            contentPadding: EdgeInsets.zero,
            title: Text(strings.buildLocalForceInit),
            subtitle: Text(strings.buildLocalForceInitSubtitle),
            value: widget.state.localBuildForceInit,
            onChanged: widget.controller.updateLocalBuildForceInit,
          ),
          SwitchListTile.adaptive(
            contentPadding: EdgeInsets.zero,
            title: Text(strings.buildLocalSkipDeps),
            subtitle: Text(strings.buildLocalSkipDepsSubtitle),
            value: widget.state.localBuildSkipDeps,
            onChanged: widget.controller.updateLocalBuildSkipDeps,
          ),
          const SizedBox(height: 12),
          Row(
            children: <Widget>[
              Expanded(
                child: Text(
                  'Source instances',
                  style: theme.textTheme.titleMedium,
                ),
              ),
              FilledButton.tonalIcon(
                onPressed: localBusy
                    ? null
                    : () => widget.controller.createLocalSourceInstance(),
                icon: const Icon(Icons.create_new_folder_rounded),
                label: Text(strings.buildLocalAddSourceInstance),
              ),
            ],
          ),
          const SizedBox(height: 8),
          if (widget.state.localSourceInstances.isEmpty)
            Text('No source instances yet')
          else
            Column(
              children: widget.state.localSourceInstances
                  .map(
                    (source) => Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: InkWell(
                        borderRadius: BorderRadius.circular(18),
                        onTap: () => widget.controller
                            .selectLocalSourceInstance(source.id),
                        child: Container(
                          decoration: BoxDecoration(
                            color:
                                source.id ==
                                    widget.state.selectedLocalSourceInstanceId
                                ? scheme.primaryContainer.withValues(alpha: 0.6)
                                : scheme.surfaceContainerHighest.withValues(
                                    alpha: 0.24,
                                  ),
                            borderRadius: BorderRadius.circular(18),
                            border: Border.all(
                              color:
                                  source.id ==
                                      widget.state.selectedLocalSourceInstanceId
                                  ? scheme.primary
                                  : scheme.outlineVariant.withValues(
                                      alpha: 0.34,
                                    ),
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
                                      source.displayName,
                                      style: theme.textTheme.titleSmall,
                                    ),
                                    const SizedBox(height: 4),
                                    Text(
                                      source.state,
                                      style: theme.textTheme.bodySmall
                                          ?.copyWith(
                                            color: scheme.onSurfaceVariant,
                                          ),
                                    ),
                                  ],
                                ),
                              ),
                              StatusPill(
                                label:
                                    source.activeBackendKind?.name ?? 'default',
                                color: source.isReady
                                    ? scheme.secondary
                                    : scheme.outline,
                                icon: source.isReady
                                    ? Icons.folder_special_rounded
                                    : Icons.schedule_rounded,
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  )
                  .toList(growable: false),
            ),
          if (selectedSource != null) ...<Widget>[
            const SizedBox(height: 8),
            _BuildInfoLine(label: 'Source', value: selectedSource.displayName),
            if (runtime?.templateName != null)
              _BuildInfoLine(
                label: strings.buildLocalTemplateLabel,
                value: runtime!.templateName!,
              ),
            if (runtime?.templateBranch != null)
              _BuildInfoLine(
                label: strings.buildLocalBranchLabel,
                value: runtime!.templateBranch!,
              ),
            if (runtime?.workspaceDir != null)
              _BuildInfoLine(
                label: strings.buildLocalWorkspaceLabel,
                value: runtime!.workspaceDir!,
              ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 12,
              runSpacing: 12,
              children: <Widget>[
                if (runtime?.workspaceDir != null)
                  FilledButton.tonalIcon(
                    onPressed: () => _openDirectory(runtime!.workspaceDir!),
                    icon: const Icon(Icons.folder_open_rounded),
                    label: Text(strings.buildLocalOpenWorkspace),
                  ),
                if (runtime?.artifactsDir != null)
                  FilledButton.tonalIcon(
                    onPressed: () => _openDirectory(runtime!.artifactsDir!),
                    icon: const Icon(Icons.inventory_2_rounded),
                    label: Text(strings.buildLocalOpenArtifacts),
                  ),
                if (runtime?.logsDir != null)
                  FilledButton.tonalIcon(
                    onPressed: () => _openDirectory(runtime!.logsDir!),
                    icon: const Icon(Icons.receipt_long_rounded),
                    label: Text(strings.buildLocalOpenLogs),
                  ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

class _LocalBuildActionCard extends StatelessWidget {
  const _LocalBuildActionCard({required this.state, required this.controller});

  final BuildPageState state;
  final BuildPageController controller;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final selectedProfile = state.selectedLocalProfile;
    final selectedSource = state.selectedLocalSourceInstance;
    final sourceProfiles = selectedSource == null
        ? const <LocalBuildProfile>[]
        : _dedupeLocalProfilesForSource(state.localProfiles, selectedSource.id);
    final profileDropdownValue =
        sourceProfiles.any(
          (profile) => profile.id == state.selectedLocalProfileId,
        )
        ? state.selectedLocalProfileId
        : '';
    final runtime = selectedSource?.materialized;
    final localBusy =
        state.localBuildStatusLoading || state.activeLocalTask != null;
    return PanelCard(
      title: strings.buildLocalActionTitle,
      subtitle: strings.buildLocalActionSubtitle,
      icon: Icons.memory_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          TextFormField(
            key: ValueKey<String>(
              'local-profile-name-${state.selectedLocalSourceInstanceId ?? 'draft'}-${state.selectedLocalProfileId ?? 'new'}',
            ),
            initialValue: state.localProfileNameDraft,
            decoration: const InputDecoration(labelText: 'Profile name'),
            onChanged: controller.updateLocalProfileNameDraft,
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            key: ValueKey<String>(
              'local-profile-picker-${selectedSource?.id ?? 'none'}-$profileDropdownValue',
            ),
            initialValue: profileDropdownValue,
            decoration: const InputDecoration(labelText: 'Profile'),
            items: <DropdownMenuItem<String>>[
              const DropdownMenuItem<String>(
                value: '',
                child: Text('New profile'),
              ),
              ...sourceProfiles.map(
                (profile) => DropdownMenuItem<String>(
                  value: profile.id,
                  child: Text(profile.name),
                ),
              ),
            ],
            onChanged: (value) => controller.selectLocalProfile(
              value == null || value.isEmpty ? null : value,
            ),
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<LocalBuildBackendKind?>(
            key: ValueKey<String>(
              'local-profile-backend-${state.selectedLocalProfileId ?? 'new'}-${state.localProfileBackendKind?.name ?? 'default'}',
            ),
            initialValue: state.localProfileBackendKind,
            decoration: const InputDecoration(labelText: 'Profile backend'),
            items: <DropdownMenuItem<LocalBuildBackendKind?>>[
              const DropdownMenuItem<LocalBuildBackendKind?>(
                value: null,
                child: Text('Use global default'),
              ),
              ...state.localBackends.map(
                (backend) => DropdownMenuItem<LocalBuildBackendKind?>(
                  value: backend.kind,
                  child: Text(backend.label),
                ),
              ),
            ],
            onChanged: controller.updateLocalProfileBackendKind,
          ),
          const SizedBox(height: 12),
          FilledButton.tonalIcon(
            onPressed: state.isSubmitting || localBusy || selectedSource == null
                ? null
                : controller.saveLocalProfile,
            icon: const Icon(Icons.save_rounded),
            label: Text(
              selectedProfile == null ? 'Save profile' : 'Update profile',
            ),
          ),
          const SizedBox(height: 16),
          SwitchListTile.adaptive(
            contentPadding: EdgeInsets.zero,
            title: Text(strings.buildLocalCleanOut),
            subtitle: Text(strings.buildLocalCleanOutSubtitle),
            value: state.localBuildCleanOut,
            onChanged: controller.updateLocalBuildCleanOut,
          ),
          SwitchListTile.adaptive(
            contentPadding: EdgeInsets.zero,
            title: Text(strings.buildLocalReseed),
            subtitle: Text(strings.buildLocalReseedSubtitle),
            value: state.localBuildReseed,
            onChanged: controller.updateLocalBuildReseed,
          ),
          SwitchListTile.adaptive(
            contentPadding: EdgeInsets.zero,
            title: Text(strings.buildLocalNoPackage),
            subtitle: Text(strings.buildLocalNoPackageSubtitle),
            value: state.localBuildNoPackage,
            onChanged: controller.updateLocalBuildNoPackage,
          ),
          if (runtime?.latestLogPath != null) ...<Widget>[
            const SizedBox(height: 8),
            _BuildInfoLine(
              label: strings.buildLocalLatestLogLabel,
              value: runtime!.latestLogPath!,
            ),
          ],
          if (state.localArtifacts.isNotEmpty) ...<Widget>[
            const SizedBox(height: 16),
            Text('Artifacts', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            ...state.localArtifacts
                .take(3)
                .map(
                  (artifact) => _BuildInfoLine(
                    label: artifact.fileName,
                    value: artifact.path,
                  ),
                ),
          ],
          if (state.localLogs.isNotEmpty) ...<Widget>[
            const SizedBox(height: 16),
            Text('Logs', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            ...state.localLogs
                .take(3)
                .map(
                  (log) => _BuildInfoLine(label: log.fileName, value: log.path),
                ),
          ],
        ],
      ),
    );
  }
}

class _BuildInfoLine extends StatelessWidget {
  const _BuildInfoLine({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
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

class _QueueCard extends StatelessWidget {
  const _QueueCard({
    required this.state,
    required this.controller,
    this.title,
    this.subtitle,
    this.taskKinds,
    this.includeTakeoverRuns = true,
  });

  final BuildPageState state;
  final BuildPageController controller;
  final String? title;
  final String? subtitle;
  final Set<String>? taskKinds;
  final bool includeTakeoverRuns;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final entries = _buildQueueEntries(
      state,
      strings,
      taskKinds: taskKinds,
      includeTakeoverRuns: includeTakeoverRuns,
    );

    return PanelCard(
      title: title ?? strings.buildQueueTitle,
      subtitle: subtitle ?? strings.buildQueueSubtitle,
      icon: Icons.queue_rounded,
      child: entries.isEmpty
          ? Text(strings.buildNoTasks)
          : Column(
              children: entries
                  .map(
                    (entry) => Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: _TaskTile(
                        entry: entry,
                        onPrimaryAction: () {
                          if (entry.task != null) {
                            _showTaskDetailsDialog(context, entry.task!.id);
                            return;
                          }
                          if (entry.run != null) {
                            controller.selectRun(entry.run!.id);
                          }
                        },
                        onOpenDirectory: entry.task?.primaryDownloadPath == null
                            ? null
                            : () => _openDirectory(
                                entry.task!.primaryDownloadPath!,
                              ),
                      ),
                    ),
                  )
                  .toList(),
            ),
    );
  }
}

class _BuildQueueEntry {
  const _BuildQueueEntry({
    required this.id,
    required this.headline,
    required this.currentStep,
    required this.preview,
    required this.state,
    this.task,
    this.run,
  });

  final String id;
  final String headline;
  final String currentStep;
  final String preview;
  final String state;
  final DesktopTaskSnapshot? task;
  final BuildRunSummary? run;
}

List<LocalBuildProfile> _dedupeLocalProfilesForSource(
  List<LocalBuildProfile> profiles,
  String sourceInstanceId,
) {
  final seen = <String>{};
  final filtered = <LocalBuildProfile>[];
  for (final profile in profiles) {
    if (profile.sourceInstanceId != sourceInstanceId) {
      continue;
    }
    if (!seen.add(profile.id)) {
      continue;
    }
    filtered.add(profile);
  }
  return filtered;
}

class _CustomModulesCard extends StatefulWidget {
  const _CustomModulesCard({
    required this.state,
    required this.controller,
    this.scope = BuildFormScope.remote,
  });

  final BuildPageState state;
  final BuildPageController controller;
  final BuildFormScope scope;

  @override
  State<_CustomModulesCard> createState() => _CustomModulesCardState();
}

class _CustomModulesCardState extends State<_CustomModulesCard> {
  late final TextEditingController _repositoryUrlController;
  late final TextEditingController _manualModuleUrlController;
  bool _manualAddBusy = false;
  String? _loadingModuleSetRepoUrl;

  bool get _moduleSetBusy => _loadingModuleSetRepoUrl != null;

  @override
  void initState() {
    super.initState();
    _repositoryUrlController = TextEditingController(
      text: widget.state.repositoryUrlDraft,
    );
    _manualModuleUrlController = TextEditingController(
      text: widget.state.manualModuleUrl,
    );
  }

  @override
  void didUpdateWidget(covariant _CustomModulesCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    _syncController(_repositoryUrlController, widget.state.repositoryUrlDraft);
    _syncController(_manualModuleUrlController, widget.state.manualModuleUrl);
  }

  @override
  void dispose() {
    _repositoryUrlController.dispose();
    _manualModuleUrlController.dispose();
    super.dispose();
  }

  void _syncController(TextEditingController controller, String value) {
    if (controller.text == value) return;
    controller.value = controller.value.copyWith(
      text: value,
      selection: TextSelection.collapsed(offset: value.length),
      composing: TextRange.empty,
    );
  }

  Future<void> _handleCatalogModuleTap(BuildModuleCatalogItem module) async {
    if (!module.isModuleSet) {
      widget.controller.addCatalogModule(module, scope: widget.scope);
      return;
    }
    setState(() {
      _loadingModuleSetRepoUrl = normalizeRepositoryUrl(module.repoUrl);
    });
    try {
      final metadata = await widget.controller.fetchModuleMetadata(
        module.repoUrl,
      );
      if (!mounted) return;
      await _showModuleSetDialog(
        groupRepoUrl: module.repoUrl,
        metadata: metadata,
        fromCatalog: true,
      );
    } catch (error) {
      if (!mounted) return;
      _showMessage(
        '${context.strings.buildModuleSetLoadFailed}: ${_formatError(error)}',
      );
    } finally {
      if (mounted) {
        setState(() {
          _loadingModuleSetRepoUrl = null;
        });
      }
    }
  }

  Future<void> _handleManualAdd() async {
    final pendingUrl = _manualModuleUrlController.text.trim();
    if (pendingUrl.isEmpty || _manualAddBusy) return;
    setState(() {
      _manualAddBusy = true;
    });
    widget.controller.updateManualModuleUrl(pendingUrl);
    try {
      final metadata = await widget.controller.addManualModule(
        scope: widget.scope,
      );
      if (!mounted) return;
      if (metadata != null && metadata.isModuleSet) {
        final saved = await _showModuleSetDialog(
          groupRepoUrl: pendingUrl,
          metadata: metadata,
          fromCatalog: false,
        );
        if (saved == true) {
          widget.controller.updateManualModuleUrl('');
        }
      }
    } catch (error) {
      if (!mounted) return;
      _showMessage(_formatError(error));
    } finally {
      if (mounted) {
        setState(() {
          _manualAddBusy = false;
        });
      }
    }
  }

  Future<bool?> _showModuleSetDialog({
    required String groupRepoUrl,
    required BuildExternalModuleMetadata metadata,
    required bool fromCatalog,
  }) {
    final selectedModules = widget.scope == BuildFormScope.remote
        ? widget.state.selectedModules
        : widget.state.localSelectedModules;
    final existingModules = selectedModules
        .where((module) => module.matchesModuleSetGroup(groupRepoUrl))
        .toList(growable: false);
    return showDialog<bool>(
      context: context,
      builder: (context) => _ModuleSetSelectionDialog(
        groupRepoUrl: groupRepoUrl,
        metadata: metadata,
        existingModules: existingModules,
        onSave: (selections) {
          widget.controller.replaceModuleSetSelection(
            groupRepoUrl: groupRepoUrl,
            metadata: metadata,
            selections: selections,
            fromCatalog: fromCatalog,
            scope: widget.scope,
          );
        },
      ),
    );
  }

  void _showMessage(String message) {
    final messenger = ScaffoldMessenger.maybeOf(context);
    messenger
      ?..clearSnackBars()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  String _formatError(Object error) {
    const prefix = 'FormatException: ';
    final raw = error.toString().trim();
    return raw.startsWith(prefix) ? raw.substring(prefix.length) : raw;
  }

  String _catalogModuleDescription(
    AppStrings strings,
    BuildModuleCatalogItem module,
  ) {
    final description = module.description.trim();
    if (description.isNotEmpty) {
      return description;
    }
    return module.isModuleSet ? strings.buildModuleSetOpen : module.repoUrl;
  }

  Future<void> _editSelectedModule(_SelectedModuleListItem item) async {
    if (item.isModuleSet) {
      setState(() {
        _loadingModuleSetRepoUrl = normalizeRepositoryUrl(item.repoUrl);
      });
      try {
        final metadata = await widget.controller.fetchModuleMetadata(
          item.repoUrl,
        );
        if (!mounted) return;
        await _showModuleSetDialog(
          groupRepoUrl: item.repoUrl,
          metadata: metadata,
          fromCatalog: item.fromCatalog,
        );
      } catch (error) {
        if (!mounted) return;
        _showMessage(
          '${context.strings.buildModuleSetLoadFailed}: ${_formatError(error)}',
        );
      } finally {
        if (mounted) {
          setState(() {
            _loadingModuleSetRepoUrl = null;
          });
        }
      }
      return;
    }

    final supportedStages = await _resolveRegularModuleSupportedStages(item);
    if (!mounted) return;
    final updatedStages = await _showRegularModuleStageDialog(
      title: item.title,
      repoUrl: item.repoUrl,
      supportedStages: supportedStages,
      initialStages: item.stages,
    );
    if (updatedStages == null || item.seed == null) {
      return;
    }
    widget.controller.setRegularModuleStages(
      seed: item.seed!,
      stages: updatedStages,
      scope: widget.scope,
    );
  }

  Future<List<String>> _resolveRegularModuleSupportedStages(
    _SelectedModuleListItem item,
  ) async {
    for (final repository in widget.state.moduleRepositories) {
      for (final module in repository.modules) {
        if (module.isModuleSet) {
          continue;
        }
        if (normalizeRepositoryUrl(module.repoUrl).toLowerCase() ==
            normalizeRepositoryUrl(item.repoUrl).toLowerCase()) {
          return _sortStages(module.supportedStages);
        }
      }
    }

    try {
      final metadata = await widget.controller.fetchModuleMetadata(
        item.repoUrl,
      );
      if (!metadata.isModuleSet) {
        return _sortStages(metadata.supportedStages);
      }
    } catch (_) {}

    return const <String>['after_patch', 'before_build'];
  }

  Future<List<String>?> _showRegularModuleStageDialog({
    required String title,
    required String repoUrl,
    required List<String> supportedStages,
    required List<String> initialStages,
  }) {
    final selectedStages = initialStages.toSet();
    final options = _sortStages(supportedStages);
    return showDialog<List<String>>(
      context: context,
      builder: (context) {
        final strings = context.strings;
        final theme = Theme.of(context);
        final scheme = theme.colorScheme;
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              title: Text(title),
              content: SizedBox(
                width: 420,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(
                      repoUrl,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 16),
                    ...options.map(
                      (stage) => CheckboxListTile(
                        value: selectedStages.contains(stage),
                        contentPadding: EdgeInsets.zero,
                        title: Text(
                          strings.buildModuleStageLabelForValue(stage),
                        ),
                        onChanged: (selected) {
                          setDialogState(() {
                            if (selected ?? false) {
                              selectedStages.add(stage);
                            } else {
                              selectedStages.remove(stage);
                            }
                          });
                        },
                      ),
                    ),
                  ],
                ),
              ),
              actions: <Widget>[
                TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: Text(strings.commonCancel),
                ),
                FilledButton(
                  onPressed: selectedStages.isEmpty
                      ? null
                      : () => Navigator.of(
                          context,
                        ).pop(_sortStages(selectedStages.toList())),
                  child: Text(strings.commonSave),
                ),
              ],
            );
          },
        );
      },
    );
  }

  String _selectedModuleDescription(
    AppStrings strings,
    _SelectedModuleListItem item,
  ) {
    if (!item.isModuleSet) {
      return '${strings.buildModuleStageLabel}：${_formatStageSummary(strings, item.stages)}';
    }
    final childDescriptions = item.children
        .map(
          (child) =>
              '${child.title}（${_formatStageSummary(strings, child.stages)}）',
        )
        .join(' · ');
    return '${strings.buildModuleSetChildrenTitle}：$childDescriptions';
  }

  String _formatStageSummary(AppStrings strings, List<String> stages) {
    return _sortStages(
      stages,
    ).map(strings.buildModuleStageLabelForValue).join(' / ');
  }

  List<String> _sortStages(Iterable<String> stages) {
    const order = <String>['after_patch', 'before_build'];
    final values = stages
        .map(normalizeCustomModuleStage)
        .toSet()
        .toList(growable: false);
    final sorted = values.toList(growable: true);
    sorted.sort((left, right) {
      final leftIndex = order.indexOf(left);
      final rightIndex = order.indexOf(right);
      if (leftIndex == -1 && rightIndex == -1) {
        return left.compareTo(right);
      }
      if (leftIndex == -1) {
        return 1;
      }
      if (rightIndex == -1) {
        return -1;
      }
      return leftIndex.compareTo(rightIndex);
    });
    return sorted;
  }

  List<_SelectedModuleListItem> _groupSelectedModules(
    List<SelectedBuildModule> modules,
  ) {
    final grouped = <String, List<SelectedBuildModule>>{};
    final order = <String>[];
    for (final module in modules) {
      final key = module.isModuleSetChild
          ? 'set:${normalizeRepositoryUrl(module.groupRepoUrl ?? module.repoUrl).toLowerCase()}'
          : 'module:${normalizeRepositoryUrl(module.repoUrl).toLowerCase()}';
      final bucket = grouped[key];
      if (bucket == null) {
        grouped[key] = <SelectedBuildModule>[module];
        order.add(key);
      } else {
        bucket.add(module);
      }
    }

    return order
        .map((key) {
          final bucket = grouped[key]!;
          final first = bucket.first;
          if (!first.isModuleSetChild) {
            return _SelectedModuleListItem(
              key: key,
              title: first.label,
              repoUrl: normalizeRepositoryUrl(first.repoUrl),
              isModuleSet: false,
              modules: bucket,
              stages: _sortStages(bucket.map((module) => module.stage)),
              seed: first,
              children: const <_SelectedModuleChildSummary>[],
            );
          }

          final groupRepoUrl = normalizeRepositoryUrl(
            first.groupRepoUrl ?? first.repoUrl,
          );
          final childGroups = <String, List<SelectedBuildModule>>{};
          final childOrder = <String>[];
          for (final module in bucket) {
            final childKey =
                module.childId?.trim().toLowerCase().isNotEmpty == true
                ? module.childId!.trim().toLowerCase()
                : module.label.trim().toLowerCase();
            final childBucket = childGroups[childKey];
            if (childBucket == null) {
              childGroups[childKey] = <SelectedBuildModule>[module];
              childOrder.add(childKey);
            } else {
              childBucket.add(module);
            }
          }

          final children = childOrder
              .map((childKey) {
                final childBucket = childGroups[childKey]!;
                final childFirst = childBucket.first;
                final title = childFirst.childName?.trim().isNotEmpty == true
                    ? childFirst.childName!.trim()
                    : (childFirst.childId?.trim().isNotEmpty == true
                          ? childFirst.childId!.trim()
                          : childFirst.label);
                return _SelectedModuleChildSummary(
                  title: title,
                  stages: _sortStages(
                    childBucket.map((module) => module.stage),
                  ),
                );
              })
              .toList(growable: false);

          return _SelectedModuleListItem(
            key: key,
            title: first.groupName?.trim().isNotEmpty == true
                ? first.groupName!.trim()
                : repoNameFromUrl(groupRepoUrl),
            repoUrl: groupRepoUrl,
            isModuleSet: true,
            modules: bucket,
            stages: const <String>[],
            seed: null,
            children: children,
          );
        })
        .toList(growable: false);
  }

  @override
  Widget build(BuildContext context) {
    final state = widget.state;
    final controller = widget.controller;
    final strings = context.strings;
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final selectedModules = widget.scope == BuildFormScope.remote
        ? state.selectedModules
        : state.localSelectedModules;
    final selectedModuleItems = _groupSelectedModules(selectedModules);
    final selectedRegularRepoKeys = selectedModuleItems
        .where((item) => !item.isModuleSet)
        .map((item) => normalizeRepositoryUrl(item.repoUrl).toLowerCase())
        .toSet();
    final selectedModuleSetRepoKeys = selectedModuleItems
        .where((item) => item.isModuleSet)
        .map((item) => normalizeRepositoryUrl(item.repoUrl).toLowerCase())
        .toSet();

    return PanelCard(
      title: strings.buildCustomModulesTitle,
      subtitle: strings.buildCustomModulesSubtitle,
      icon: Icons.extension_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(
            strings.buildSelectedModulesTitle,
            style: theme.textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          if (selectedModuleItems.isEmpty)
            Text(strings.buildNoSelectedModules)
          else
            Column(
              children: selectedModuleItems
                  .map(
                    (item) => Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: _SelectedModuleListTile(
                        item: item,
                        subtitle: _selectedModuleDescription(strings, item),
                        onEdit: () => _editSelectedModule(item),
                        onRemove: () {
                          if (item.isModuleSet) {
                            controller.removeModuleSetSelection(
                              item.repoUrl,
                              scope: widget.scope,
                            );
                          } else {
                            controller.setRegularModuleStages(
                              seed: item.seed!,
                              stages: const <String>[],
                              scope: widget.scope,
                            );
                          }
                        },
                      ),
                    ),
                  )
                  .toList(growable: false),
            ),
          const SizedBox(height: 18),
          Text(
            strings.buildAddFromModuleRepo,
            style: theme.textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          Row(
            children: <Widget>[
              Expanded(
                child: TextFormField(
                  controller: _repositoryUrlController,
                  decoration: InputDecoration(
                    labelText: strings.buildModuleRepositoryUrl,
                  ),
                  onChanged: controller.updateRepositoryUrlDraft,
                ),
              ),
              const SizedBox(width: 12),
              FilledButton.tonal(
                onPressed: state.moduleCatalogLoading || _moduleSetBusy
                    ? null
                    : controller.addModuleRepository,
                child: Text(strings.buildModuleRepositoryAdd),
              ),
            ],
          ),
          if (state.moduleCatalogError != null) ...<Widget>[
            const SizedBox(height: 8),
            Text(
              state.moduleCatalogError!,
              style: theme.textTheme.bodySmall?.copyWith(color: scheme.error),
            ),
          ],
          const SizedBox(height: 12),
          Column(
            children: state.moduleRepositories
                .map(
                  (repository) => Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        color: scheme.surfaceContainerHighest.withValues(
                          alpha: 0.28,
                        ),
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(
                          color: scheme.outlineVariant.withValues(alpha: 0.32),
                        ),
                      ),
                      child: Padding(
                        padding: const EdgeInsets.all(14),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: <Widget>[
                            Row(
                              children: <Widget>[
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: <Widget>[
                                      Text(
                                        repository.name,
                                        style: theme.textTheme.titleSmall,
                                      ),
                                      const SizedBox(height: 4),
                                      Text(
                                        repository.url,
                                        style: theme.textTheme.bodySmall,
                                      ),
                                    ],
                                  ),
                                ),
                                IconButton(
                                  onPressed: state.moduleCatalogLoading
                                      ? null
                                      : () =>
                                            controller.refreshModuleRepository(
                                              repository.url,
                                            ),
                                  icon: const Icon(Icons.refresh_rounded),
                                ),
                              ],
                            ),
                            const SizedBox(height: 10),
                            Builder(
                              builder: (context) {
                                final availableModules = repository.modules
                                    .where((module) {
                                      final key = normalizeRepositoryUrl(
                                        module.repoUrl,
                                      ).toLowerCase();
                                      return module.isModuleSet
                                          ? !selectedModuleSetRepoKeys.contains(
                                              key,
                                            )
                                          : !selectedRegularRepoKeys.contains(
                                              key,
                                            );
                                    })
                                    .toList(growable: false);

                                if (!repository.isReady) {
                                  return Text(
                                    repository.error ??
                                        strings.buildNoCatalogModules,
                                    style: theme.textTheme.bodySmall?.copyWith(
                                      color: scheme.error,
                                    ),
                                  );
                                }
                                if (availableModules.isEmpty) {
                                  return Text(
                                    repository.modules.isEmpty
                                        ? strings.buildNoCatalogModules
                                        : strings.buildAllCatalogModulesAdded,
                                  );
                                }
                                return Column(
                                  children: availableModules.indexed
                                      .map((entry) {
                                        final index = entry.$1;
                                        final module = entry.$2;
                                        final disabled =
                                            state.moduleCatalogLoading ||
                                            _moduleSetBusy;
                                        final moduleRepoKey =
                                            normalizeRepositoryUrl(
                                              module.repoUrl,
                                            ).toLowerCase();
                                        final loadingThisModuleSet =
                                            module.isModuleSet &&
                                            _loadingModuleSetRepoUrl
                                                    ?.toLowerCase() ==
                                                moduleRepoKey;
                                        return Padding(
                                          padding: EdgeInsets.only(
                                            bottom:
                                                index ==
                                                    availableModules.length - 1
                                                ? 0
                                                : 10,
                                          ),
                                          child: Material(
                                            color: scheme.surface.withValues(
                                              alpha: 0.72,
                                            ),
                                            borderRadius: BorderRadius.circular(
                                              18,
                                            ),
                                            child: InkWell(
                                              borderRadius:
                                                  BorderRadius.circular(18),
                                              onTap: disabled
                                                  ? null
                                                  : () =>
                                                        _handleCatalogModuleTap(
                                                          module,
                                                        ),
                                              child: Padding(
                                                padding:
                                                    const EdgeInsets.symmetric(
                                                      horizontal: 14,
                                                      vertical: 14,
                                                    ),
                                                child: Row(
                                                  crossAxisAlignment:
                                                      CrossAxisAlignment.start,
                                                  children: <Widget>[
                                                    Padding(
                                                      padding:
                                                          const EdgeInsets.only(
                                                            top: 2,
                                                          ),
                                                      child: Icon(
                                                        module.isModuleSet
                                                            ? Icons
                                                                  .widgets_rounded
                                                            : Icons
                                                                  .extension_rounded,
                                                        size: 20,
                                                        color: disabled
                                                            ? scheme
                                                                  .onSurfaceVariant
                                                            : scheme.primary,
                                                      ),
                                                    ),
                                                    const SizedBox(width: 12),
                                                    Expanded(
                                                      child: Column(
                                                        crossAxisAlignment:
                                                            CrossAxisAlignment
                                                                .start,
                                                        children: <Widget>[
                                                          Text(
                                                            module.name,
                                                            style: theme
                                                                .textTheme
                                                                .titleSmall
                                                                ?.copyWith(
                                                                  fontWeight:
                                                                      FontWeight
                                                                          .w700,
                                                                ),
                                                          ),
                                                          const SizedBox(
                                                            height: 4,
                                                          ),
                                                          Text(
                                                            _catalogModuleDescription(
                                                              strings,
                                                              module,
                                                            ),
                                                            style: theme
                                                                .textTheme
                                                                .bodySmall
                                                                ?.copyWith(
                                                                  color: scheme
                                                                      .onSurfaceVariant,
                                                                ),
                                                            maxLines: 2,
                                                            overflow:
                                                                TextOverflow
                                                                    .ellipsis,
                                                          ),
                                                        ],
                                                      ),
                                                    ),
                                                    const SizedBox(width: 12),
                                                    loadingThisModuleSet
                                                        ? SizedBox(
                                                            width: 20,
                                                            height: 20,
                                                            child:
                                                                CircularProgressIndicator(
                                                                  strokeWidth:
                                                                      2.2,
                                                                  color: scheme
                                                                      .primary,
                                                                ),
                                                          )
                                                        : Icon(
                                                            module.isModuleSet
                                                                ? Icons
                                                                      .chevron_right_rounded
                                                                : Icons
                                                                      .add_circle_outline_rounded,
                                                            color: disabled
                                                                ? scheme
                                                                      .onSurfaceVariant
                                                                : scheme
                                                                      .primary,
                                                          ),
                                                  ],
                                                ),
                                              ),
                                            ),
                                          ),
                                        );
                                      })
                                      .toList(growable: false),
                                );
                              },
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                )
                .toList(growable: false),
          ),
          const SizedBox(height: 18),
          Text(
            strings.buildManualModuleAdd,
            style: theme.textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          LayoutBuilder(
            builder: (context, constraints) {
              final wide = constraints.maxWidth >= 860;
              final width = wide
                  ? (constraints.maxWidth - 16) / 2
                  : constraints.maxWidth;
              return Wrap(
                spacing: 16,
                runSpacing: 12,
                children: <Widget>[
                  SizedBox(
                    width: width,
                    child: TextFormField(
                      controller: _manualModuleUrlController,
                      decoration: InputDecoration(
                        labelText: strings.buildManualModuleUrl,
                        hintText: 'https://github.com/user/module',
                      ),
                      onChanged: controller.updateManualModuleUrl,
                    ),
                  ),
                  SizedBox(
                    width: width,
                    child: DropdownButtonFormField<String>(
                      initialValue: state.manualModuleStage,
                      decoration: InputDecoration(
                        labelText: strings.buildModuleStageLabel,
                      ),
                      items: const <String>['after_patch', 'before_build']
                          .map(
                            (value) => DropdownMenuItem<String>(
                              value: value,
                              child: Text(
                                strings.buildModuleStageLabelForValue(value),
                              ),
                            ),
                          )
                          .toList(growable: false),
                      onChanged: (value) {
                        if (value == null) return;
                        controller.updateManualModuleStage(value);
                      },
                    ),
                  ),
                ],
              );
            },
          ),
          const SizedBox(height: 10),
          FilledButton.tonalIcon(
            onPressed: _manualAddBusy ? null : _handleManualAdd,
            icon: const Icon(Icons.add_link_rounded),
            label: Text(strings.buildManualModuleAddButton),
          ),
        ],
      ),
    );
  }
}

class _SelectedModuleListItem {
  const _SelectedModuleListItem({
    required this.key,
    required this.title,
    required this.repoUrl,
    required this.isModuleSet,
    required this.modules,
    required this.stages,
    required this.seed,
    required this.children,
  });

  final String key;
  final String title;
  final String repoUrl;
  final bool isModuleSet;
  final List<SelectedBuildModule> modules;
  final List<String> stages;
  final SelectedBuildModule? seed;
  final List<_SelectedModuleChildSummary> children;

  bool get fromCatalog => modules.any((module) => module.fromCatalog);
}

class _SelectedModuleChildSummary {
  const _SelectedModuleChildSummary({
    required this.title,
    required this.stages,
  });

  final String title;
  final List<String> stages;
}

class _SelectedModuleListTile extends StatelessWidget {
  const _SelectedModuleListTile({
    required this.item,
    required this.subtitle,
    required this.onEdit,
    required this.onRemove,
  });

  final _SelectedModuleListItem item;
  final String subtitle;
  final VoidCallback onEdit;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Material(
      color: scheme.surfaceContainerHighest.withValues(alpha: 0.26),
      borderRadius: BorderRadius.circular(18),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Padding(
              padding: const EdgeInsets.only(top: 2),
              child: Icon(
                item.isModuleSet
                    ? Icons.widgets_rounded
                    : Icons.extension_rounded,
                size: 20,
                color: item.isModuleSet ? scheme.tertiary : scheme.primary,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text(
                    item.title,
                    style: theme.textTheme.titleSmall?.copyWith(
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    subtitle,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: scheme.onSurfaceVariant,
                    ),
                    maxLines: 3,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Column(
              children: <Widget>[
                IconButton(
                  tooltip: strings.commonEdit,
                  onPressed: onEdit,
                  icon: const Icon(Icons.edit_rounded),
                ),
                IconButton(
                  tooltip: strings.buildModuleRemove,
                  onPressed: onRemove,
                  icon: const Icon(Icons.delete_outline_rounded),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ModuleSetSelectionDialog extends StatefulWidget {
  const _ModuleSetSelectionDialog({
    required this.groupRepoUrl,
    required this.metadata,
    required this.existingModules,
    required this.onSave,
  });

  final String groupRepoUrl;
  final BuildExternalModuleMetadata metadata;
  final List<SelectedBuildModule> existingModules;
  final ValueChanged<Map<BuildModuleSetChildMetadata, List<String>>> onSave;

  @override
  State<_ModuleSetSelectionDialog> createState() =>
      _ModuleSetSelectionDialogState();
}

class _ModuleSetSelectionDialogState extends State<_ModuleSetSelectionDialog> {
  late Set<String> _selectedChildIds;
  late Map<String, Set<String>> _stageSelections;

  @override
  void initState() {
    super.initState();
    final selectedChildIds = <String>{};
    final stageSelections = <String, Set<String>>{};
    for (final child in widget.metadata.children) {
      final existingStages = widget.existingModules
          .where((module) => module.childId == child.id)
          .map((module) => module.stage)
          .where(child.supportedStages.contains)
          .toSet();
      if (existingStages.isNotEmpty) {
        selectedChildIds.add(child.id);
        stageSelections[child.id] = existingStages;
      } else {
        final recommendedStages = child.recommendedStages
            .where(child.supportedStages.contains)
            .toSet();
        stageSelections[child.id] = recommendedStages.isEmpty
            ? <String>{child.defaultStage}
            : recommendedStages;
      }
    }
    _selectedChildIds = selectedChildIds;
    _stageSelections = stageSelections;
  }

  bool get _canSave {
    if (_selectedChildIds.isEmpty) {
      return false;
    }
    for (final child in widget.metadata.children) {
      if (!_selectedChildIds.contains(child.id)) {
        continue;
      }
      final stages = _stageSelections[child.id] ?? <String>{};
      if (!stages.any(child.supportedStages.contains)) {
        return false;
      }
    }
    return true;
  }

  void _toggleChild(BuildModuleSetChildMetadata child, bool selected) {
    setState(() {
      if (selected) {
        _selectedChildIds.add(child.id);
        final currentStages = (_stageSelections[child.id] ?? <String>{})
            .where(child.supportedStages.contains)
            .toSet();
        if (currentStages.isNotEmpty) {
          _stageSelections[child.id] = currentStages;
        } else {
          final recommendedStages = child.recommendedStages
              .where(child.supportedStages.contains)
              .toSet();
          _stageSelections[child.id] = recommendedStages.isEmpty
              ? <String>{child.defaultStage}
              : recommendedStages;
        }
      } else {
        _selectedChildIds.remove(child.id);
      }
    });
  }

  void _toggleStage(
    BuildModuleSetChildMetadata child,
    String stage,
    bool selected,
  ) {
    setState(() {
      final current = {...?_stageSelections[child.id]};
      if (selected) {
        current.add(stage);
      } else {
        current.remove(stage);
      }
      _stageSelections[child.id] = current;
    });
  }

  Map<BuildModuleSetChildMetadata, List<String>> _buildSelections() {
    final selections = <BuildModuleSetChildMetadata, List<String>>{};
    for (final child in widget.metadata.children) {
      if (!_selectedChildIds.contains(child.id)) {
        continue;
      }
      final selectedStages = child.supportedStages
          .where((_stageSelections[child.id] ?? <String>{}).contains)
          .toList(growable: false);
      if (selectedStages.isEmpty) {
        continue;
      }
      selections[child] = selectedStages;
    }
    return selections;
  }

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return AlertDialog(
      title: Text(widget.metadata.name),
      content: SizedBox(
        width: 560,
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              if (widget.metadata.version.isNotEmpty ||
                  widget.metadata.description.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: Text(
                    [
                      if (widget.metadata.version.isNotEmpty)
                        widget.metadata.version,
                      if (widget.metadata.description.isNotEmpty)
                        widget.metadata.description,
                    ].join('\n'),
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: scheme.onSurfaceVariant,
                    ),
                  ),
                ),
              Text(
                strings.buildModuleSetChildrenTitle,
                style: theme.textTheme.titleSmall,
              ),
              const SizedBox(height: 10),
              if (widget.metadata.children.isEmpty)
                Text(strings.buildModuleSetEmpty)
              else
                Column(
                  children: widget.metadata.children
                      .map(
                        (child) => Padding(
                          padding: const EdgeInsets.only(bottom: 10),
                          child: DecoratedBox(
                            decoration: BoxDecoration(
                              color: scheme.surfaceContainerHighest.withValues(
                                alpha: 0.24,
                              ),
                              borderRadius: BorderRadius.circular(18),
                              border: Border.all(
                                color: scheme.outlineVariant.withValues(
                                  alpha: 0.28,
                                ),
                              ),
                            ),
                            child: Padding(
                              padding: const EdgeInsets.all(14),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: <Widget>[
                                  Row(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: <Widget>[
                                      Checkbox(
                                        value: _selectedChildIds.contains(
                                          child.id,
                                        ),
                                        onChanged: (value) =>
                                            _toggleChild(child, value ?? false),
                                      ),
                                      Expanded(
                                        child: Column(
                                          crossAxisAlignment:
                                              CrossAxisAlignment.start,
                                          children: <Widget>[
                                            Text(
                                              child.name,
                                              style: theme.textTheme.titleSmall,
                                            ),
                                            if (child
                                                .description
                                                .isNotEmpty) ...<Widget>[
                                              const SizedBox(height: 4),
                                              Text(
                                                child.description,
                                                style: theme.textTheme.bodySmall
                                                    ?.copyWith(
                                                      color: scheme
                                                          .onSurfaceVariant,
                                                    ),
                                              ),
                                            ],
                                          ],
                                        ),
                                      ),
                                    ],
                                  ),
                                  if (_selectedChildIds.contains(
                                    child.id,
                                  )) ...<Widget>[
                                    const SizedBox(height: 8),
                                    Wrap(
                                      spacing: 8,
                                      runSpacing: 8,
                                      children: child.supportedStages
                                          .map(
                                            (stage) => FilterChip(
                                              selected:
                                                  _stageSelections[child.id]
                                                      ?.contains(stage) ??
                                                  false,
                                              onSelected: (selected) =>
                                                  _toggleStage(
                                                    child,
                                                    stage,
                                                    selected,
                                                  ),
                                              label: Text(
                                                '${strings.buildModuleStageLabelForValue(stage)}${child.recommendedStages.contains(stage) ? strings.buildRecommendedSuffix : ''}',
                                              ),
                                            ),
                                          )
                                          .toList(growable: false),
                                    ),
                                  ],
                                ],
                              ),
                            ),
                          ),
                        ),
                      )
                      .toList(growable: false),
                ),
            ],
          ),
        ),
      ),
      actions: <Widget>[
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(strings.commonCancel),
        ),
        FilledButton(
          onPressed: !_canSave
              ? null
              : () {
                  widget.onSave(_buildSelections());
                  Navigator.of(context).pop(true);
                },
          child: Text(strings.buildModuleSetSave),
        ),
      ],
    );
  }
}

class _RuntimeSummaryCard extends StatelessWidget {
  const _RuntimeSummaryCard({required this.runtime});

  final RuntimeBuildSummary runtime;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final scheme = Theme.of(context).colorScheme;
    return PanelCard(
      title: strings.buildRuntimePrefill,
      subtitle: strings.buildRuntimePrefillSubtitle,
      icon: Icons.auto_awesome_rounded,
      child: Wrap(
        spacing: 10,
        runSpacing: 10,
        children: <Widget>[
          StatusPill(
            label: runtime.androidVersion.isEmpty
                ? strings.unknownValue
                : runtime.androidVersion,
            color: scheme.primary,
            icon: Icons.android_rounded,
          ),
          StatusPill(
            label: runtime.kernelVersion.isEmpty
                ? strings.unknownValue
                : runtime.kernelVersion,
            color: scheme.secondary,
            icon: Icons.memory_rounded,
          ),
          StatusPill(
            label: runtime.subLevel.isEmpty
                ? strings.unknownValue
                : runtime.subLevel,
            color: scheme.tertiary,
            icon: Icons.numbers_rounded,
          ),
          StatusPill(
            label: runtime.osPatchLevel.isEmpty
                ? strings.unknownValue
                : runtime.osPatchLevel,
            color: scheme.primaryContainer,
            icon: Icons.security_rounded,
          ),
          StatusPill(
            label: runtime.revision.isEmpty
                ? strings.unknownValue
                : runtime.revision,
            color: scheme.surfaceTint,
            icon: Icons.confirmation_number_rounded,
          ),
        ],
      ),
    );
  }
}

class _BannerCard extends StatelessWidget {
  const _BannerCard({
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

class _BoolChip extends StatelessWidget {
  const _BoolChip({
    required this.label,
    required this.value,
    required this.onChanged,
    this.enabled = true,
  });

  final String label;
  final bool value;
  final ValueChanged<bool> onChanged;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return FilterChip(
      label: Text(label),
      selected: value,
      onSelected: enabled ? onChanged : null,
      selectedColor: scheme.primaryContainer,
      checkmarkColor: scheme.primary,
      side: BorderSide(color: scheme.outlineVariant),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
    );
  }
}

class _TextRow extends StatelessWidget {
  const _TextRow({
    required this.width,
    required this.label,
    required this.value,
    required this.onChanged,
    this.obscure = false,
  });

  final double width;
  final String label;
  final String value;
  final ValueChanged<String> onChanged;
  final bool obscure;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: width,
      child: _SyncedTextFormField(
        fieldKey: ValueKey<String>('text-row-$label-$value'),
        value: value,
        obscureText: obscure,
        decoration: InputDecoration(labelText: label),
        onChanged: onChanged,
      ),
    );
  }
}

class _SyncedTextFormField extends StatefulWidget {
  const _SyncedTextFormField({
    required this.fieldKey,
    required this.value,
    required this.decoration,
    required this.onChanged,
    this.obscureText = false,
  });

  final Key fieldKey;
  final String value;
  final InputDecoration decoration;
  final ValueChanged<String> onChanged;
  final bool obscureText;

  @override
  State<_SyncedTextFormField> createState() => _SyncedTextFormFieldState();
}

class _SyncedTextFormFieldState extends State<_SyncedTextFormField> {
  late final TextEditingController _controller;
  late final FocusNode _focusNode;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.value);
    _focusNode = FocusNode();
  }

  @override
  void didUpdateWidget(covariant _SyncedTextFormField oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (_controller.text == widget.value) {
      return;
    }
    if (_focusNode.hasFocus) {
      return;
    }
    _controller.value = _controller.value.copyWith(
      text: widget.value,
      selection: TextSelection.collapsed(offset: widget.value.length),
      composing: TextRange.empty,
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      key: widget.fieldKey,
      controller: _controller,
      focusNode: _focusNode,
      obscureText: widget.obscureText,
      decoration: widget.decoration,
      onChanged: widget.onChanged,
    );
  }
}

List<_BuildQueueEntry> _buildQueueEntries(
  BuildPageState state,
  AppStrings strings, {
  Set<String>? taskKinds,
  bool includeTakeoverRuns = true,
}) {
  final localTasks = state.taskOrder
      .map((id) => state.taskById(id))
      .whereType<DesktopTaskSnapshot>()
      .where((task) => taskKinds == null || taskKinds.contains(task.kind))
      .toList(growable: false);
  final claimedRunIds = localTasks.expand(_taskAssociatedRunIds).toSet();

  final entries = <_BuildQueueEntry>[
    ...localTasks.map((task) => _buildQueueEntryFromTask(strings, task)),
  ];

  if (includeTakeoverRuns) {
    final takeoverRuns =
        state.runs
            .where(
              (run) =>
                  run.looksLikeKernelBuild &&
                  run.isRunning &&
                  !claimedRunIds.contains(run.id),
            )
            .toList(growable: true)
          ..sort((left, right) {
            final leftKey = left.updatedAt ?? left.createdAt ?? '';
            final rightKey = right.updatedAt ?? right.createdAt ?? '';
            return rightKey.compareTo(leftKey);
          });

    entries.addAll(
      takeoverRuns.map((run) => _buildQueueEntryFromRun(strings, run)),
    );
  }

  return entries;
}

_BuildQueueEntry _buildQueueEntryFromTask(
  AppStrings strings,
  DesktopTaskSnapshot task,
) {
  final localScopeSuffix = _taskSourceInstanceDisplay(task);
  final localHeadline = localScopeSuffix == null
      ? '${strings.buildLocalTaskScope} · ${strings.buildTaskLabel(task.kind)}'
      : '${strings.buildLocalTaskScope} · ${strings.buildTaskLabel(task.kind)} · $localScopeSuffix';
  return _BuildQueueEntry(
    id: task.id,
    headline: task.kind.startsWith('local.build.') ||
            task.kind.startsWith('local.backend.')
        ? localHeadline
        : '${_taskWorkflowLabel(strings, task)} · ${strings.buildTaskLabel(task.kind)}',
    currentStep: _taskCurrentStep(strings, task),
    preview: _taskTaskPreview(strings, task),
    state: task.state,
    task: task,
  );
}

_BuildQueueEntry _buildQueueEntryFromRun(
  AppStrings strings,
  BuildRunSummary run,
) {
  return _BuildQueueEntry(
    id: 'run:${run.id}',
    headline:
        '${_workflowLabelForRun(run)} · ${run.name.trim().isNotEmpty ? run.name.trim() : strings.buildWorkflowCenterTitle}',
    currentStep: strings.buildRunStatusLabel(run),
    preview: _runPreviewText(run),
    state: _runState(run),
    run: run,
  );
}

Set<int> _taskAssociatedRunIds(DesktopTaskSnapshot task) {
  final runIds = <int>{};
  final directRunId = task.result['runId'];
  if (directRunId is num) {
    runIds.add(directRunId.toInt());
  }
  final rawRunIds = task.result['runIds'];
  if (rawRunIds is List) {
    for (final value in rawRunIds) {
      if (value is num) {
        runIds.add(value.toInt());
      }
    }
  }
  final trackedRuns = task.result['trackedRuns'];
  if (trackedRuns is List) {
    for (final run in trackedRuns) {
      if (run is Map) {
        final id = run['id'];
        if (id is num) {
          runIds.add(id.toInt());
        }
      }
    }
  }
  return runIds;
}

String? _taskSourceInstanceDisplay(DesktopTaskSnapshot task) {
  final sourceInstance = task.result['sourceInstance'];
  if (sourceInstance is Map) {
    final displayName = sourceInstance['displayName'];
    if (displayName is String && displayName.trim().isNotEmpty) {
      return displayName.trim();
    }
    final androidVersion = sourceInstance['androidVersion'];
    final kernelVersion = sourceInstance['kernelVersion'];
    final branchMonth = sourceInstance['branchMonth'];
    if (androidVersion is String &&
        androidVersion.trim().isNotEmpty &&
        kernelVersion is String &&
        kernelVersion.trim().isNotEmpty &&
        branchMonth is String &&
        branchMonth.trim().isNotEmpty) {
      return '${androidVersion.trim()}/${kernelVersion.trim()}@${branchMonth.trim()}';
    }
  }

  final sourceInstanceId = task.result['sourceInstanceId'];
  if (sourceInstanceId is String && sourceInstanceId.trim().isNotEmpty) {
    final normalized = sourceInstanceId.trim();
    final atIndex = normalized.indexOf('@');
    if (atIndex > 0) {
      final kernelLine = normalized
          .substring(0, atIndex)
          .replaceFirst('-', '/');
      final branchMonth = normalized.substring(atIndex + 1);
      if (kernelLine.isNotEmpty && branchMonth.isNotEmpty) {
        return '$kernelLine@$branchMonth';
      }
    }
    return normalized;
  }

  final outputDisplay = task.output
      .map((line) => line.trim())
      .firstWhere(
        (line) => line.toLowerCase().startsWith('source instance: '),
        orElse: () => '',
      );
  if (outputDisplay.isNotEmpty) {
    return outputDisplay.substring('source instance: '.length).trim();
  }
  return null;
}

String _taskWorkflowLabel(AppStrings strings, DesktopTaskSnapshot task) {
  final runIds = _taskAssociatedRunIds(task);
  if (runIds.isEmpty) {
    return strings.buildTaskWorkflowPending;
  }
  final sortedIds = runIds.toList(growable: true)..sort();
  final first = sortedIds.first;
  if (sortedIds.length == 1) {
    return '#$first';
  }
  return '#$first +${sortedIds.length - 1}';
}

String _taskCurrentStep(AppStrings strings, DesktopTaskSnapshot task) {
  final message = task.message?.trim();
  if (message != null && message.isNotEmpty) {
    return strings.buildTaskMessageLabel(message);
  }
  final heading = task.output
      .map((line) => line.trim())
      .where((line) => line.startsWith('## '))
      .map((line) => line.substring(3).trim())
      .where((line) => line.isNotEmpty)
      .lastOrNull;
  return heading == null
      ? strings.buildTaskStateLabel(task.state)
      : strings.buildTaskMessageLabel(heading);
}

String _taskTaskPreview(AppStrings strings, DesktopTaskSnapshot task) {
  final lines = task.output
      .map((line) => line.trimRight())
      .where((line) => line.isNotEmpty && !line.trimLeft().startsWith('## '))
      .take(4)
      .toList(growable: false);
  if (lines.isNotEmpty) {
    return lines.join('\n');
  }
  final runIds = _taskAssociatedRunIds(task);
  if (runIds.isNotEmpty) {
    final sortedIds = runIds.toList(growable: true)..sort();
    return sortedIds.map((id) => '#$id').join(' · ');
  }
  return _taskPreviewText(task);
}

String _workflowLabelForRun(BuildRunSummary run) {
  if (run.runNumber > 0) {
    return '#${run.runNumber}';
  }
  return '#${run.id}';
}

String _runPreviewText(BuildRunSummary run) {
  final displayTitle = run.displayTitle.trim();
  if (displayTitle.isNotEmpty &&
      displayTitle.toLowerCase() != run.name.trim().toLowerCase()) {
    return displayTitle;
  }
  final branch = run.headBranch?.trim();
  if (branch != null && branch.isNotEmpty) {
    return branch;
  }
  return run.updatedAt ?? run.createdAt ?? '';
}

String _runState(BuildRunSummary run) {
  if (run.isSuccess) {
    return 'succeeded';
  }
  if (run.isFailure) {
    return 'failed';
  }
  if (run.isRunning) {
    return 'running';
  }
  return 'pending';
}

class _TaskTile extends StatelessWidget {
  const _TaskTile({
    required this.entry,
    required this.onPrimaryAction,
    this.onOpenDirectory,
  });

  final _BuildQueueEntry entry;
  final VoidCallback onPrimaryAction;
  final VoidCallback? onOpenDirectory;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final theme = Theme.of(context);
    final scheme = Theme.of(context).colorScheme;
    final color = _taskStateColor(scheme, entry.state);
    return Material(
      color: scheme.surfaceContainerHighest.withValues(alpha: 0.4),
      borderRadius: BorderRadius.circular(22),
      child: InkWell(
        borderRadius: BorderRadius.circular(22),
        onTap: onPrimaryAction,
        child: Container(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(22),
            border: Border.all(
              color: scheme.outlineVariant.withValues(alpha: 0.36),
            ),
          ),
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Row(
                children: <Widget>[
                  Expanded(
                    child: Text(
                      entry.headline,
                      style: theme.textTheme.titleMedium,
                    ),
                  ),
                  StatusPill(
                    label: strings.buildTaskStateLabel(entry.state),
                    color: color,
                    icon: _taskStateIcon(entry.state),
                  ),
                  const SizedBox(width: 8),
                  Icon(
                    Icons.chevron_right_rounded,
                    color: scheme.onSurfaceVariant,
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Wrap(
                spacing: 10,
                runSpacing: 10,
                children: <Widget>[
                  StatusPill(
                    label:
                        '${strings.buildTaskCurrentStep} · ${entry.currentStep}',
                    color: scheme.tertiary,
                    icon: Icons.route_rounded,
                  ),
                ],
              ),
              if (entry.preview.isNotEmpty) ...<Widget>[
                const SizedBox(height: 10),
                Text(
                  entry.preview,
                  maxLines: 4,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodySmall,
                ),
              ],
              const SizedBox(height: 12),
              Wrap(
                spacing: 10,
                runSpacing: 10,
                children: <Widget>[
                  FilledButton.tonalIcon(
                    onPressed: onPrimaryAction,
                    icon: Icon(
                      entry.task != null
                          ? Icons.article_outlined
                          : Icons.account_tree_rounded,
                    ),
                    label: Text(
                      entry.task != null
                          ? strings.buildTaskOpenLogs
                          : strings.buildTaskOpenWorkflow,
                    ),
                  ),
                  if (onOpenDirectory != null)
                    OutlinedButton(
                      onPressed: onOpenDirectory,
                      child: Text(strings.buildOpenDirectory),
                    ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

Future<void> _showTaskDetailsDialog(BuildContext context, String taskId) {
  return showDialog<void>(
    context: context,
    builder: (context) => _TaskDetailsDialog(taskId: taskId),
  );
}

class _TaskDetailsDialog extends ConsumerWidget {
  const _TaskDetailsDialog({required this.taskId});

  final String taskId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final strings = context.strings;
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final state = ref.watch(buildPageControllerProvider);
    final task = state.taskById(taskId);
    final previewTask = task;
    final consoleLines = previewTask == null
        ? const <String>[]
        : _taskConsoleLines(previewTask);
    final resultLines = previewTask == null
        ? const <String>[]
        : _taskResultLines(previewTask);
    final copyPayload = <String>[
      if (previewTask?.message != null) previewTask!.message!,
      if (consoleLines.isNotEmpty) ...<String>[
        '',
        strings.buildTaskConsoleTitle,
        ...consoleLines,
      ],
      if (resultLines.isNotEmpty) ...<String>[
        '',
        strings.buildTaskResultTitle,
        ...resultLines,
      ],
    ].join('\n');

    return Dialog(
      insetPadding: const EdgeInsets.all(28),
      child: SizedBox(
        width: 1120,
        height: 760,
        child: Padding(
          padding: const EdgeInsets.all(22),
          child: previewTask == null
              ? Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Row(
                      children: <Widget>[
                        Expanded(
                          child: Text(
                            strings.buildTaskDetailsTitle,
                            style: theme.textTheme.headlineSmall,
                          ),
                        ),
                        IconButton(
                          onPressed: () => Navigator.of(context).pop(),
                          icon: const Icon(Icons.close_rounded),
                        ),
                      ],
                    ),
                    const SizedBox(height: 18),
                    Text(strings.buildNoTasks),
                  ],
                )
              : Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Row(
                      children: <Widget>[
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: <Widget>[
                              Text(
                                strings.buildTaskDetailsTitle,
                                style: theme.textTheme.headlineSmall,
                              ),
                              const SizedBox(height: 6),
                              Text(
                                strings.buildTaskLabel(previewTask.kind),
                                style: theme.textTheme.bodyLarge?.copyWith(
                                  color: scheme.onSurfaceVariant,
                                ),
                              ),
                            ],
                          ),
                        ),
                        StatusPill(
                          label: strings.buildTaskStateLabel(previewTask.state),
                          color: _taskStateColor(scheme, previewTask.state),
                          icon: _taskStateIcon(previewTask.state),
                        ),
                        const SizedBox(width: 10),
                        if (copyPayload.trim().isNotEmpty)
                          FilledButton.tonalIcon(
                            onPressed: () => _copyToClipboard(
                              context,
                              copyPayload,
                              strings.buildTaskLogsCopied,
                            ),
                            icon: const Icon(Icons.content_copy_rounded),
                            label: Text(strings.buildTaskCopyLogs),
                          ),
                        const SizedBox(width: 10),
                        IconButton(
                          onPressed: () => Navigator.of(context).pop(),
                          icon: const Icon(Icons.close_rounded),
                        ),
                      ],
                    ),
                    const SizedBox(height: 18),
                    DecoratedBox(
                      decoration: BoxDecoration(
                        color: scheme.surfaceContainerHighest.withValues(
                          alpha: 0.22,
                        ),
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(
                          color: scheme.outlineVariant.withValues(alpha: 0.28),
                        ),
                      ),
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: <Widget>[
                            Text(
                              strings.buildTaskOverviewTitle,
                              style: theme.textTheme.titleMedium,
                            ),
                            const SizedBox(height: 10),
                            Wrap(
                              spacing: 10,
                              runSpacing: 10,
                              children: <Widget>[
                                StatusPill(
                                  label:
                                      '${strings.buildTaskIdentifier} · ${previewTask.id}',
                                  color: scheme.surfaceTint,
                                  icon: Icons.tag_rounded,
                                ),
                                if (previewTask.primaryDownloadPath != null)
                                  StatusPill(
                                    label: strings.buildOpenDirectory,
                                    color: scheme.secondary,
                                    icon: Icons.folder_open_rounded,
                                  ),
                              ],
                            ),
                            if (previewTask.message != null) ...<Widget>[
                              const SizedBox(height: 10),
                              Text(previewTask.message!),
                            ],
                            if (!previewTask.isTerminal) ...<Widget>[
                              const SizedBox(height: 10),
                              Text(
                                strings.buildTaskLiveHint,
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: scheme.onSurfaceVariant,
                                ),
                              ),
                            ],
                            if (previewTask.primaryDownloadPath !=
                                null) ...<Widget>[
                              const SizedBox(height: 12),
                              OutlinedButton.icon(
                                onPressed: () => _openDirectory(
                                  previewTask.primaryDownloadPath!,
                                ),
                                icon: const Icon(Icons.folder_open_rounded),
                                label: Text(strings.buildOpenDirectory),
                              ),
                            ],
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    Expanded(
                      child: LayoutBuilder(
                        builder: (context, constraints) {
                          final wide = constraints.maxWidth >= 960;
                          final consolePanel = _TaskLogPanel(
                            title: strings.buildTaskConsoleTitle,
                            lines: consoleLines,
                            emptyLabel: strings.buildTaskNoOutput,
                            language: MonacoCodeLanguage.plaintext,
                            followTail: true,
                            incrementalAppends: true,
                          );
                          final resultPanel = _TaskLogPanel(
                            title: strings.buildTaskResultTitle,
                            lines: resultLines,
                            emptyLabel: strings.buildTaskNoResult,
                            language: MonacoCodeLanguage.json,
                            followTail: false,
                            incrementalAppends: false,
                          );
                          if (!wide) {
                            return Column(
                              children: <Widget>[
                                Expanded(flex: 5, child: consolePanel),
                                const SizedBox(height: 12),
                                Expanded(flex: 3, child: resultPanel),
                              ],
                            );
                          }
                          return Row(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: <Widget>[
                              Expanded(flex: 7, child: consolePanel),
                              const SizedBox(width: 14),
                              Expanded(flex: 5, child: resultPanel),
                            ],
                          );
                        },
                      ),
                    ),
                  ],
                ),
        ),
      ),
    );
  }
}

class _TaskLogPanel extends StatelessWidget {
  const _TaskLogPanel({
    required this.title,
    required this.lines,
    required this.emptyLabel,
    required this.language,
    required this.followTail,
    required this.incrementalAppends,
  });

  final String title;
  final List<String> lines;
  final String emptyLabel;
  final MonacoCodeLanguage language;
  final bool followTail;
  final bool incrementalAppends;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    if (!Platform.isLinux) {
      return _buildLegacyPanel(context, theme);
    }
    return DecoratedBox(
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: theme.colorScheme.outlineVariant),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 8),
            child: Text(
              title,
              style: theme.textTheme.titleSmall?.copyWith(
                color: theme.colorScheme.onSurface,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          Divider(height: 1, color: theme.colorScheme.outlineVariant),
          Expanded(
            child: lines.isEmpty
                ? Padding(
                    padding: const EdgeInsets.all(16),
                    child: Text(
                      emptyLabel,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  )
                : MonacoCodeView(
                    content: lines.join('\n'),
                    language: language,
                    followTail: followTail,
                    incrementalAppends: incrementalAppends,
                    maxRetainedLines: 5000,
                    nativeInsets: const EdgeInsets.fromLTRB(1, 0, 1, 1),
                    fallbackPadding: const EdgeInsets.symmetric(vertical: 10),
                    fallbackStyle: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurface,
                      fontFamily: 'monospace',
                      height: 1.45,
                    ),
                    fallbackBuilder: (context, content) {
                      return _buildLegacyLogBody(context, content, theme);
                    },
                  ),
          ),
          if (lines.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
              child: Text(
                '${lines.length} line(s)',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildLegacyPanel(BuildContext context, ThemeData theme) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: const Color(0xFF0D1117),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: const Color(0xFF30363D)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 8),
            child: Text(
              title,
              style: theme.textTheme.titleSmall?.copyWith(
                color: Colors.white,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          const Divider(height: 1, color: Color(0xFF30363D)),
          Expanded(
            child: lines.isEmpty
                ? Padding(
                    padding: const EdgeInsets.all(16),
                    child: Text(
                      emptyLabel,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: Colors.white70,
                      ),
                    ),
                  )
                : _buildLegacyLogBody(context, lines.join('\n'), theme),
          ),
          if (lines.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
              child: Text(
                '${lines.length} line(s)',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: const Color(0xFF7D8590),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

Widget _buildLegacyLogBody(
  BuildContext context,
  String content,
  ThemeData theme,
) {
  final lines = content.split('\n');
  return Scrollbar(
    thumbVisibility: true,
    child: ListView.builder(
      padding: const EdgeInsets.symmetric(vertical: 10),
      itemCount: lines.length,
      itemBuilder: (context, index) {
        final line = lines[index];
        final isDivider = line.startsWith('## ');
        return Container(
          color: index.isEven
              ? const Color(0xFF0D1117)
              : const Color(0xFF111827),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              SizedBox(
                width: 52,
                child: Text(
                  '${index + 1}',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: const Color(0xFF7D8590),
                    fontFamily: 'monospace',
                  ),
                ),
              ),
              Expanded(
                child: SelectableText(
                  isDivider ? line.substring(3) : line,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: isDivider ? const Color(0xFF7EE787) : Colors.white,
                    fontFamily: 'monospace',
                    height: 1.45,
                  ),
                ),
              ),
            ],
          ),
        );
      },
    ),
  );
}

String _taskPreviewText(DesktopTaskSnapshot task) {
  final lines = _taskConsoleLines(task);
  if (lines.isNotEmpty) {
    return lines.take(5).join('\n');
  }
  return task.message?.trim().isNotEmpty == true
      ? task.message!.trim()
      : task.id;
}

List<String> _taskConsoleLines(DesktopTaskSnapshot task) {
  final lines = <String>[];
  if (task.message?.trim().isNotEmpty == true) {
    lines.add('## ${task.message!.trim()}');
  }
  for (final rawLine in task.output) {
    lines.addAll(_expandStructuredLogLine(rawLine));
  }
  return lines;
}

List<String> _taskResultLines(DesktopTaskSnapshot task) {
  if (task.result.isEmpty) {
    return const <String>[];
  }
  return _prettyJsonLines(task.result);
}

Color _taskStateColor(ColorScheme scheme, String state) {
  return switch (state) {
    'succeeded' => scheme.primary,
    'failed' => scheme.error,
    'running' => scheme.secondary,
    _ => scheme.onSurfaceVariant,
  };
}

IconData _taskStateIcon(String state) {
  return switch (state) {
    'succeeded' => Icons.check_circle_rounded,
    'failed' => Icons.error_rounded,
    'running' => Icons.sync_rounded,
    _ => Icons.schedule_rounded,
  };
}

List<String> _expandStructuredLogLine(String rawLine) {
  final line = rawLine.trimRight();
  if (line.isEmpty) {
    return const <String>[''];
  }
  final trimmed = line.trimLeft();
  if ((trimmed.startsWith('{') && trimmed.endsWith('}')) ||
      (trimmed.startsWith('[') && trimmed.endsWith(']'))) {
    try {
      return _prettyJsonLines(jsonDecode(trimmed));
    } catch (_) {
      return <String>[line];
    }
  }
  return <String>[line];
}

List<String> _prettyJsonLines(Object? value) {
  final encoder = const JsonEncoder.withIndent('  ');
  final text = encoder.convert(value);
  return text.split('\n');
}

Future<void> _openUrl(String url) async {
  await Process.start('xdg-open', <String>[url]);
}

Future<String?> _showLocalAuthorizationDialog(
  BuildContext context,
  LocalBuildBackendDescriptor backend,
) {
  final strings = context.strings;
  final controller = TextEditingController();
  final helper =
      backend.authorizationMessage ?? backend.detail ?? backend.label;
  return showDialog<String>(
    context: context,
    builder: (context) {
      return AlertDialog(
        title: Text(strings.buildLocalAuthorizationTitle),
        content: SizedBox(
          width: 420,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Text(strings.buildLocalAuthorizationSubtitle),
              const SizedBox(height: 12),
              Text(helper),
              const SizedBox(height: 16),
              TextField(
                controller: controller,
                obscureText: true,
                decoration: InputDecoration(
                  labelText: strings.buildLocalAuthorizationPasswordLabel,
                ),
                onSubmitted: (_) {
                  final password = controller.text.trim();
                  Navigator.of(context).pop(password.isEmpty ? null : password);
                },
              ),
            ],
          ),
        ),
        actions: <Widget>[
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: Text(strings.commonCancel),
          ),
          FilledButton(
            onPressed: () {
              final password = controller.text.trim();
              Navigator.of(context).pop(password.isEmpty ? null : password);
            },
            child: Text(strings.buildLocalAuthorizationAction),
          ),
        ],
      );
    },
  );
}

Future<void> _copyToClipboard(
  BuildContext context,
  String text,
  String successMessage,
) async {
  await Clipboard.setData(ClipboardData(text: text));
  if (!context.mounted) return;
  ScaffoldMessenger.of(
    context,
  ).showSnackBar(SnackBar(content: Text(successMessage)));
}

Future<void> _openDirectory(String path) async {
  final target = FileSystemEntity.isDirectorySync(path)
      ? path
      : File(path).parent.path;
  await Process.start('xdg-open', <String>[target]);
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

String _runtimePreviewLabel(RuntimeBuildSummary runtime) {
  final parts = <String>[
    runtime.androidVersion,
    runtime.kernelVersion,
    runtime.subLevel,
  ].where((value) => value.isNotEmpty).toList(growable: false);
  return parts.isEmpty ? 'runtime' : parts.join(' · ');
}

class _TaskWorkspaceStateFile {
  const _TaskWorkspaceStateFile({
    this.entryIds = const <String>[],
    this.activeEntryId,
    this.updatedAtMs = 0,
  });

  final List<String> entryIds;
  final String? activeEntryId;
  final int updatedAtMs;

  _TaskWorkspaceStateFile copyWith({
    List<String>? entryIds,
    String? activeEntryId,
    int? updatedAtMs,
  }) {
    return _TaskWorkspaceStateFile(
      entryIds: entryIds ?? this.entryIds,
      activeEntryId: activeEntryId,
      updatedAtMs: updatedAtMs ?? DateTime.now().millisecondsSinceEpoch,
    );
  }

  Map<String, dynamic> toJson() {
    return <String, dynamic>{
      'entryIds': entryIds,
      'activeEntryId': activeEntryId,
      'updatedAtMs': updatedAtMs == 0
          ? DateTime.now().millisecondsSinceEpoch
          : updatedAtMs,
    };
  }

  factory _TaskWorkspaceStateFile.fromJson(Map<String, dynamic> json) {
    final rawIds = json['entryIds'];
    return _TaskWorkspaceStateFile(
      entryIds: rawIds is List
          ? rawIds.whereType<String>().toList(growable: false)
          : const <String>[],
      activeEntryId: json['activeEntryId'] is String
          ? json['activeEntryId'] as String
          : null,
      updatedAtMs: json['updatedAtMs'] is int ? json['updatedAtMs'] as int : 0,
    );
  }
}

String _taskWorkspaceStateFilePath() {
  final baseDir =
      Platform.environment['XDG_RUNTIME_DIR']?.trim().isNotEmpty == true
      ? Platform.environment['XDG_RUNTIME_DIR']!.trim()
      : Directory.systemTemp.path;
  return '$baseDir/abk-task-workspace.json';
}

String _taskWorkspaceLockFilePath(String stateFilePath) =>
    '$stateFilePath.lock';

Future<_TaskWorkspaceStateFile?> _readTaskWorkspaceState(String path) async {
  final file = File(path);
  if (!await file.exists()) {
    return null;
  }
  final raw = await file.readAsString();
  if (raw.trim().isEmpty) {
    return const _TaskWorkspaceStateFile();
  }
  final decoded = jsonDecode(raw);
  if (decoded is Map<String, dynamic>) {
    return _TaskWorkspaceStateFile.fromJson(decoded);
  }
  if (decoded is Map) {
    return _TaskWorkspaceStateFile.fromJson(Map<String, dynamic>.from(decoded));
  }
  return const _TaskWorkspaceStateFile();
}

Future<void> _writeTaskWorkspaceState(
  String path,
  _TaskWorkspaceStateFile state,
) async {
  final file = File(path);
  await file.parent.create(recursive: true);
  await file.writeAsString(
    const JsonEncoder.withIndent('  ').convert(state.toJson()),
  );
}

Future<void> _writeTaskWorkspaceWindowLock(String stateFilePath) async {
  final file = File(_taskWorkspaceLockFilePath(stateFilePath));
  await file.parent.create(recursive: true);
  await file.writeAsString(
    jsonEncode(<String, dynamic>{
      'pid': pid,
      'heartbeatMs': DateTime.now().millisecondsSinceEpoch,
    }),
  );
}

Future<void> _releaseTaskWorkspaceWindowLock(String stateFilePath) async {
  final file = File(_taskWorkspaceLockFilePath(stateFilePath));
  if (await file.exists()) {
    await file.delete();
  }
}

Future<bool> _isTaskWorkspaceWindowAlive(String stateFilePath) async {
  final file = File(_taskWorkspaceLockFilePath(stateFilePath));
  if (!await file.exists()) {
    return false;
  }
  try {
    final decoded = jsonDecode(await file.readAsString());
    if (decoded is Map) {
      final heartbeatMs = decoded['heartbeatMs'];
      if (heartbeatMs is int) {
        final ageMs = DateTime.now().millisecondsSinceEpoch - heartbeatMs;
        if (ageMs <= 5000) {
          return true;
        }
      }
    }
  } catch (_) {}
  try {
    await file.delete();
  } catch (_) {}
  return false;
}

Future<void> _ensureTaskWorkspaceWindowRunning(
  String stateFilePath,
  String? sidecarBaseUrl,
) async {
  if (await _isTaskWorkspaceWindowAlive(stateFilePath)) {
    return;
  }
  final args = <String>[
    '--abk-task-window',
    '--abk-task-state-file',
    stateFilePath,
  ];
  final cleanBaseUrl = sidecarBaseUrl?.trim();
  if (cleanBaseUrl != null && cleanBaseUrl.isNotEmpty) {
    args
      ..add('--abk-base-url')
      ..add(cleanBaseUrl);
  }
  await Process.start(
    Platform.resolvedExecutable,
    args,
    mode: ProcessStartMode.detached,
    environment: Platform.environment,
  );
}

Future<_BuildQueueEntry?> _resolveTaskWorkspaceEntryFromApi(
  AbkSidecarApi api,
  AppStrings strings,
  String id,
) async {
  if (id.startsWith('run:')) {
    final runId = int.tryParse(id.substring('run:'.length));
    if (runId == null) {
      return null;
    }
    final result = await api.getBuildRun(runId);
    final run =
        result.run ??
        result.runs.where((candidate) => candidate.id == runId).firstOrNull;
    if (run == null) {
      return null;
    }
    return _buildQueueEntryFromRun(strings, run);
  }
  final task = await api.getTask(id);
  return _buildQueueEntryFromTask(strings, task);
}

extension<T> on Iterable<T> {
  T? get firstOrNull => isEmpty ? null : first;
  T? get lastOrNull => isEmpty ? null : last;
}
