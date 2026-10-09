import 'package:flutter/services.dart';

abstract interface class DesktopWebUiApi {
  Future<bool> openWebUiWindow({required String url, required String title});
}

class MethodChannelDesktopWebUiApi implements DesktopWebUiApi {
  static const MethodChannel _channel = MethodChannel(
    'com.abk.desktop/platform',
  );

  @override
  Future<bool> openWebUiWindow({
    required String url,
    required String title,
  }) async {
    try {
      final opened = await _channel.invokeMethod<bool>('openWebUiWindow', {
        'url': url,
        'title': title,
      });
      return opened == true;
    } on MissingPluginException {
      return false;
    } on PlatformException {
      return false;
    }
  }
}
