import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:window_manager/window_manager.dart';

import 'core/localization/app_strings.dart';
import 'core/theme/app_theme.dart';
import 'core/theme/desktop_theme_provider.dart';
import 'features/build/build_page.dart';
import 'features/detect/detection_page.dart';
import 'features/device/device_page.dart';
import 'features/home/home_page.dart';
import 'features/settings/settings_page.dart';
import 'features/shell/app_shell.dart';

enum AppLaunchMode { main, taskWindow }

class AbkDesktopApp extends ConsumerWidget {
  const AbkDesktopApp({
    super.key,
    this.launchMode = AppLaunchMode.main,
    this.taskWorkspaceStateFilePath,
  });

  final AppLaunchMode launchMode;
  final String? taskWorkspaceStateFilePath;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final themeAsync = ref.watch(desktopThemeProvider);
    final theme =
        themeAsync.valueOrNull ??
        AppTheme.light(seedColor: AppTheme.fallbackSeedColor);

    if (launchMode == AppLaunchMode.taskWindow) {
      return MaterialApp(
        onGenerateTitle: (context) => AppStrings.of(context).appTitle,
        debugShowCheckedModeBanner: false,
        locale: const Locale('zh', 'CN'),
        supportedLocales: AppStrings.supportedLocales,
        localizationsDelegates: const [
          AppStrings.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        theme: theme,
        builder: (context, child) {
          return DesktopWindowFrame(child: child ?? const SizedBox.shrink());
        },
        home: TaskWorkspaceWindowPage(
          stateFilePath: taskWorkspaceStateFilePath ?? '',
        ),
      );
    }

    final router = GoRouter(
      initialLocation: '/home',
      routes: [
        ShellRoute(
          builder: (context, state, child) => AppShell(child: child),
          routes: [
            GoRoute(
              path: '/home',
              builder: (context, state) => const HomePage(),
            ),
            GoRoute(
              path: '/detect',
              builder: (context, state) => const DetectionPage(),
            ),
            GoRoute(
              path: '/build',
              builder: (context, state) => const BuildPage(),
            ),
            GoRoute(
              path: '/device',
              builder: (context, state) => const DevicePage(),
            ),
            GoRoute(
              path: '/device/kernel',
              builder: (context, state) => const KernelFeaturesPage(),
            ),
            GoRoute(
              path: '/device/susfs',
              builder: (context, state) => const SusfsPage(),
            ),
            GoRoute(
              path: '/settings',
              builder: (context, state) => const SettingsPage(),
            ),
          ],
        ),
      ],
    );

    return MaterialApp.router(
      onGenerateTitle: (context) => AppStrings.of(context).appTitle,
      debugShowCheckedModeBanner: false,
      locale: const Locale('zh', 'CN'),
      supportedLocales: AppStrings.supportedLocales,
      localizationsDelegates: const [
        AppStrings.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      theme: theme,
      builder: (context, child) {
        return DesktopWindowFrame(child: child ?? const SizedBox.shrink());
      },
      routerConfig: router,
    );
  }
}

class DesktopWindowFrame extends StatefulWidget {
  const DesktopWindowFrame({super.key, required this.child});

  final Widget child;

  @override
  State<DesktopWindowFrame> createState() => _DesktopWindowFrameState();
}

class _DesktopWindowFrameState extends State<DesktopWindowFrame>
    with WindowListener {
  bool _isFocused = true;
  bool _isMaximized = false;
  bool _isFullScreen = false;

  bool get _isDesktopWindow => Platform.isLinux || Platform.isWindows;

  double get _cornerRadius => (_isMaximized || _isFullScreen) ? 0 : 20;

  @override
  void initState() {
    super.initState();
    if (_isDesktopWindow) {
      windowManager.addListener(this);
      _syncWindowState();
    }
  }

  Future<void> _syncWindowState() async {
    try {
      final maximized = await windowManager.isMaximized();
      final fullscreen = await windowManager.isFullScreen();
      if (!mounted) return;
      setState(() {
        _isMaximized = maximized;
        _isFullScreen = fullscreen;
      });
    } catch (_) {}
  }

  @override
  void dispose() {
    if (_isDesktopWindow) {
      windowManager.removeListener(this);
    }
    super.dispose();
  }

  @override
  void onWindowFocus() {
    setState(() {
      _isFocused = true;
    });
  }

  @override
  void onWindowBlur() {
    setState(() {
      _isFocused = false;
    });
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

  @override
  void onWindowEnterFullScreen() {
    setState(() {
      _isFullScreen = true;
    });
  }

  @override
  void onWindowLeaveFullScreen() {
    setState(() {
      _isFullScreen = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    if (!_isDesktopWindow) {
      return widget.child;
    }

    final framedChild = DecoratedBox(
      decoration: BoxDecoration(
        color: Colors.transparent,
        border: Border.all(
          color: Theme.of(context).dividerColor,
          width: (_isMaximized || _isFullScreen) ? 0 : 1,
        ),
        borderRadius: BorderRadius.circular(_cornerRadius),
        boxShadow: <BoxShadow>[
          if (!_isMaximized && !_isFullScreen)
            BoxShadow(
              color: Colors.black.withValues(alpha: _isFocused ? 0.16 : 0.08),
              offset: Offset(0, _isFocused ? 8 : 4),
              blurRadius: _isFocused ? 24 : 14,
            ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(_cornerRadius),
        child: widget.child,
      ),
    );

    return DragToResizeArea(
      enableResizeEdges: (_isMaximized || _isFullScreen)
          ? const <ResizeEdge>[]
          : (Platform.isWindows
              ? const <ResizeEdge>[
                  ResizeEdge.topLeft,
                  ResizeEdge.top,
                  ResizeEdge.topRight,
                ]
              : null),
      child: framedChild,
    );
  }
}
