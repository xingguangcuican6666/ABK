import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:window_manager/window_manager.dart';

import 'src/app.dart';
import 'src/core/state/dashboard_controller.dart';

Future<void> main(List<String> args) async {
  WidgetsFlutterBinding.ensureInitialized();
  await windowManager.ensureInitialized();
  final launchMode = args.contains('--abk-task-window')
      ? AppLaunchMode.taskWindow
      : AppLaunchMode.main;
  final taskWorkspaceStateFilePath = _readArgValue(
    args,
    '--abk-task-state-file',
  );
  final sidecarBaseUrl = _readArgValue(args, '--abk-base-url');
  final options = launchMode == AppLaunchMode.taskWindow
      ? const WindowOptions(
          size: Size(1160, 760),
          center: true,
          backgroundColor: Colors.transparent,
          skipTaskbar: false,
          titleBarStyle: TitleBarStyle.hidden,
          windowButtonVisibility: false,
        )
      : const WindowOptions(
          size: Size(1600, 980),
          center: true,
          backgroundColor: Colors.transparent,
          skipTaskbar: false,
          titleBarStyle: TitleBarStyle.hidden,
          windowButtonVisibility: false,
        );
  windowManager.waitUntilReadyToShow(options, () async {
    await windowManager.show();
    await windowManager.focus();
  });
  runApp(
    ProviderScope(
      overrides: [
        sidecarBaseUrlOverrideProvider.overrideWithValue(sidecarBaseUrl),
      ],
      child: AbkDesktopApp(
        launchMode: launchMode,
        taskWorkspaceStateFilePath: taskWorkspaceStateFilePath,
      ),
    ),
  );
}

String? _readArgValue(List<String> args, String key) {
  final index = args.indexOf(key);
  if (index < 0 || index + 1 >= args.length) {
    return null;
  }
  final value = args[index + 1].trim();
  return value.isEmpty ? null : value;
}
