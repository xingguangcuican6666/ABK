import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:palette_generator/palette_generator.dart';

import '../platform/desktop_wallpaper_api.dart';
import 'app_theme.dart';

final desktopWallpaperApiProvider = Provider<DesktopWallpaperApi>((ref) {
  return MethodChannelDesktopWallpaperApi();
});

final desktopThemeProvider = FutureProvider<ThemeData>((ref) async {
  final wallpaperApi = ref.read(desktopWallpaperApiProvider);
  final wallpaperPath = await wallpaperApi.getWallpaperPath();
  final wallpaperSeed = await _resolveWallpaperSeed(wallpaperPath);
  return AppTheme.light(seedColor: wallpaperSeed ?? AppTheme.fallbackSeedColor);
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
