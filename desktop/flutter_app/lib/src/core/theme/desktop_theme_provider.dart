import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:palette_generator/palette_generator.dart';

import '../platform/desktop_wallpaper_api.dart';
import 'app_theme.dart';

final desktopWallpaperApiProvider = Provider<DesktopWallpaperApi>((ref) {
  return MethodChannelDesktopWallpaperApi();
});

/// Light + dark Material 3 Expressive themes derived from a single seed color.
class DesktopThemeSet {
  const DesktopThemeSet({required this.light, required this.dark});

  final ThemeData light;
  final ThemeData dark;

  factory DesktopThemeSet.fromSeed(Color seed) => DesktopThemeSet(
    light: AppTheme.light(seedColor: seed),
    dark: AppTheme.dark(seedColor: seed),
  );
}

/// User-selected theme mode. Defaults to following the OS setting.
final themeModeProvider = StateProvider<ThemeMode>((ref) => ThemeMode.system);

/// Builds both theme variants from the current desktop wallpaper's dominant
/// color so the app picks up the system accent.
final desktopThemeProvider = FutureProvider<DesktopThemeSet>((ref) async {
  final wallpaperApi = ref.read(desktopWallpaperApiProvider);
  final wallpaperPath = await wallpaperApi.getWallpaperPath();
  final wallpaperSeed = await _resolveWallpaperSeed(wallpaperPath);
  return DesktopThemeSet.fromSeed(wallpaperSeed ?? AppTheme.fallbackSeedColor);
});

Future<Color?> _resolveWallpaperSeed(String? wallpaperPath) async {
  if (wallpaperPath == null || wallpaperPath.isEmpty) {
    return null;
  }

  final wallpaper = File(wallpaperPath);
  if (!await wallpaper.exists()) {
    return null;
  }

  try {
    final imageProvider = ResizeImage(
      FileImage(wallpaper),
      width: 192,
      height: 192,
    );
    final palette = await PaletteGenerator.fromImageProvider(
      imageProvider,
      size: const Size(192, 192),
      maximumColorCount: 18,
    );

    return palette.vibrantColor?.color ??
        palette.dominantColor?.color ??
        palette.lightVibrantColor?.color ??
        palette.darkVibrantColor?.color ??
        palette.mutedColor?.color;
  } catch (_) {
    return null;
  }
}
