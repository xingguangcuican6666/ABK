import 'dart:convert';

import 'package:flutter/services.dart';

abstract interface class DesktopCodeViewApi {
  Future<bool> createView({required String viewId});

  Future<void> updateViewFrame({
    required String viewId,
    required double left,
    required double top,
    required double width,
    required double height,
    required bool visible,
  });

  Future<void> postMessage({
    required String viewId,
    required Map<String, Object?> message,
  });

  Future<void> disposeView({required String viewId});
}

class MethodChannelDesktopCodeViewApi implements DesktopCodeViewApi {
  static const MethodChannel _channel = MethodChannel(
    'com.abk.desktop/platform',
  );

  @override
  Future<bool> createView({required String viewId}) async {
    try {
      final created = await _channel.invokeMethod<bool>(
        'createEmbeddedCodeView',
        <String, Object?>{'viewId': viewId},
      );
      return created == true;
    } on MissingPluginException {
      return false;
    } on PlatformException {
      return false;
    }
  }

  @override
  Future<void> updateViewFrame({
    required String viewId,
    required double left,
    required double top,
    required double width,
    required double height,
    required bool visible,
  }) async {
    try {
      await _channel
          .invokeMethod<void>('updateEmbeddedCodeViewFrame', <String, Object?>{
            'viewId': viewId,
            'left': left,
            'top': top,
            'width': width,
            'height': height,
            'visible': visible,
          });
    } on MissingPluginException {
      return;
    } on PlatformException {
      return;
    }
  }

  @override
  Future<void> postMessage({
    required String viewId,
    required Map<String, Object?> message,
  }) async {
    try {
      await _channel.invokeMethod<void>(
        'postEmbeddedCodeViewMessage',
        <String, Object?>{'viewId': viewId, 'message': jsonEncode(message)},
      );
    } on MissingPluginException {
      return;
    } on PlatformException {
      return;
    }
  }

  @override
  Future<void> disposeView({required String viewId}) async {
    try {
      await _channel.invokeMethod<void>(
        'disposeEmbeddedCodeView',
        <String, Object?>{'viewId': viewId},
      );
    } on MissingPluginException {
      return;
    } on PlatformException {
      return;
    }
  }
}
