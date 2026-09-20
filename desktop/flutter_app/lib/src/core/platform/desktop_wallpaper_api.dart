import 'package:flutter/services.dart';

abstract interface class DesktopWallpaperApi {
  Future<String?> getWallpaperPath();
}

class MethodChannelDesktopWallpaperApi implements DesktopWallpaperApi {
  static const MethodChannel _channel = MethodChannel(
    'com.abk.desktop/platform',
  );

  @override
  Future<String?> getWallpaperPath() async {
    try {
      final path = await _channel.invokeMethod<String>('getWallpaperPath');
      if (path == null || path.trim().isEmpty) {
        return null;
      }
      return path.trim();
    } on MissingPluginException {
      return null;
    } on PlatformException {
      return null;
    }
  }
}
