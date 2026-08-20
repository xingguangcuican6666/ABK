import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';

import '../core/platform/desktop_code_view_api.dart';

enum MonacoCodeLanguage {
  plaintext('plaintext'),
  json('json');

  const MonacoCodeLanguage(this.wireValue);

  final String wireValue;
}

enum MonacoThemeMode {
  light('light'),
  dark('dark');

  const MonacoThemeMode(this.wireValue);

  final String wireValue;
}

@immutable
class MonacoThemePalette {
  const MonacoThemePalette({
    required this.background,
    required this.foreground,
    required this.lineNumber,
    required this.activeLineNumber,
    required this.gutterBackground,
    required this.widgetBackground,
    required this.selectionBackground,
    required this.inactiveSelectionBackground,
    required this.comment,
    required this.string,
    required this.number,
    required this.keyword,
  });

  factory MonacoThemePalette.fromTheme(ThemeData theme) {
    final scheme = theme.colorScheme;
    return MonacoThemePalette(
      background: _hexColor(scheme.surface),
      foreground: _hexColor(scheme.onSurface),
      lineNumber: _hexColor(scheme.onSurfaceVariant.withValues(alpha: 0.62)),
      activeLineNumber: _hexColor(scheme.onSurfaceVariant),
      gutterBackground: _hexColor(scheme.surface),
      widgetBackground: _hexColor(scheme.surfaceContainerHigh),
      selectionBackground: _hexColor(
        scheme.primaryContainer.withValues(alpha: 0.92),
      ),
      inactiveSelectionBackground: _hexColor(
        scheme.primaryContainer.withValues(alpha: 0.68),
      ),
      comment: _hexColor(scheme.onSurfaceVariant),
      string: _hexColor(scheme.tertiary),
      number: _hexColor(scheme.secondary),
      keyword: _hexColor(scheme.primary),
    );
  }

  final String background;
  final String foreground;
  final String lineNumber;
  final String activeLineNumber;
  final String gutterBackground;
  final String widgetBackground;
  final String selectionBackground;
  final String inactiveSelectionBackground;
  final String comment;
  final String string;
  final String number;
  final String keyword;

  Map<String, String> toJson() {
    return <String, String>{
      'background': background,
      'foreground': foreground,
      'lineNumber': lineNumber,
      'activeLineNumber': activeLineNumber,
      'gutterBackground': gutterBackground,
      'widgetBackground': widgetBackground,
      'selectionBackground': selectionBackground,
      'inactiveSelectionBackground': inactiveSelectionBackground,
      'comment': comment,
      'string': string,
      'number': number,
      'keyword': keyword,
    };
  }

  @override
  bool operator ==(Object other) {
    return other is MonacoThemePalette &&
        other.background == background &&
        other.foreground == foreground &&
        other.lineNumber == lineNumber &&
        other.activeLineNumber == activeLineNumber &&
        other.gutterBackground == gutterBackground &&
        other.widgetBackground == widgetBackground &&
        other.selectionBackground == selectionBackground &&
        other.inactiveSelectionBackground == inactiveSelectionBackground &&
        other.comment == comment &&
        other.string == string &&
        other.number == number &&
        other.keyword == keyword;
  }

  @override
  int get hashCode => Object.hash(
    background,
    foreground,
    lineNumber,
    activeLineNumber,
    gutterBackground,
    widgetBackground,
    selectionBackground,
    inactiveSelectionBackground,
    comment,
    string,
    number,
    keyword,
  );
}

@immutable
class MonacoDocumentState {
  const MonacoDocumentState({
    required this.content,
    required this.language,
    required this.theme,
    required this.themePalette,
    required this.followTail,
    required this.incrementalAppends,
    this.maxRetainedLines,
  });

  final String content;
  final MonacoCodeLanguage language;
  final MonacoThemeMode theme;
  final MonacoThemePalette themePalette;
  final bool followTail;
  final bool incrementalAppends;
  final int? maxRetainedLines;
}

@immutable
class MonacoHostMessage {
  const MonacoHostMessage(
    this.type, [
    this.payload = const <String, Object?>{},
  ]);

  final String type;
  final Map<String, Object?> payload;

  Map<String, Object?> toJson() {
    return <String, Object?>{'type': type, ...payload};
  }
}

@visibleForTesting
List<MonacoHostMessage> planMonacoHostMessages({
  required MonacoDocumentState? previous,
  required MonacoDocumentState next,
}) {
  if (previous == null) {
    return <MonacoHostMessage>[
      MonacoHostMessage('initialize', <String, Object?>{
        'content': next.content,
        'language': next.language.wireValue,
        'theme': next.theme.wireValue,
        'themeData': next.themePalette.toJson(),
        'followTail': next.followTail,
        'maxRetainedLines': next.maxRetainedLines,
      }),
    ];
  }

  final messages = <MonacoHostMessage>[];
  if (previous.theme != next.theme ||
      previous.themePalette != next.themePalette) {
    messages.add(
      MonacoHostMessage('setTheme', <String, Object?>{
        'theme': next.theme.wireValue,
        'themeData': next.themePalette.toJson(),
      }),
    );
  }
  if (previous.language != next.language) {
    messages.add(
      MonacoHostMessage('setLanguage', <String, Object?>{
        'language': next.language.wireValue,
      }),
    );
  }
  if (previous.followTail != next.followTail) {
    messages.add(
      MonacoHostMessage('setFollowTail', <String, Object?>{
        'followTail': next.followTail,
      }),
    );
  }
  if (previous.maxRetainedLines != next.maxRetainedLines) {
    messages.add(
      MonacoHostMessage('setRetentionLimit', <String, Object?>{
        'maxRetainedLines': next.maxRetainedLines,
      }),
    );
  }
  if (previous.content == next.content) {
    return messages;
  }

  final appendedContent = _computeAppendedContent(
    previous.content,
    next.content,
  );
  if (previous.incrementalAppends &&
      next.incrementalAppends &&
      appendedContent != null &&
      appendedContent.isNotEmpty) {
    messages.add(
      MonacoHostMessage('appendContent', <String, Object?>{
        'content': appendedContent,
      }),
    );
    return messages;
  }

  messages.add(
    MonacoHostMessage('replaceContent', <String, Object?>{
      'content': next.content,
    }),
  );
  return messages;
}

String? _computeAppendedContent(String previous, String next) {
  if (previous.isEmpty) {
    return next;
  }
  if (!next.startsWith(previous)) {
    return null;
  }
  return next.substring(previous.length);
}

class MonacoCodeView extends StatefulWidget {
  const MonacoCodeView({
    super.key,
    required this.content,
    required this.language,
    this.followTail = false,
    this.incrementalAppends = false,
    this.maxRetainedLines,
    this.api,
    this.fallbackPadding = const EdgeInsets.all(12),
    this.fallbackStyle,
    this.fallbackBuilder,
    this.nativeInsets = EdgeInsets.zero,
  });

  final String content;
  final MonacoCodeLanguage language;
  final bool followTail;
  final bool incrementalAppends;
  final int? maxRetainedLines;
  final DesktopCodeViewApi? api;
  final EdgeInsetsGeometry fallbackPadding;
  final TextStyle? fallbackStyle;
  final Widget Function(BuildContext context, String content)? fallbackBuilder;
  final EdgeInsets nativeInsets;

  @override
  State<MonacoCodeView> createState() => _MonacoCodeViewState();
}

class MonacoLogView extends StatelessWidget {
  const MonacoLogView({
    super.key,
    required this.content,
    this.language = MonacoCodeLanguage.plaintext,
    this.maxRetainedLines = 5000,
    this.api,
    this.fallbackPadding = const EdgeInsets.all(12),
    this.fallbackStyle,
    this.fallbackBuilder,
    this.nativeInsets = EdgeInsets.zero,
  });

  final String content;
  final MonacoCodeLanguage language;
  final int maxRetainedLines;
  final DesktopCodeViewApi? api;
  final EdgeInsetsGeometry fallbackPadding;
  final TextStyle? fallbackStyle;
  final Widget Function(BuildContext context, String content)? fallbackBuilder;
  final EdgeInsets nativeInsets;

  @override
  Widget build(BuildContext context) {
    return MonacoCodeView(
      content: content,
      language: language,
      followTail: true,
      incrementalAppends: true,
      maxRetainedLines: maxRetainedLines,
      api: api,
      fallbackPadding: fallbackPadding,
      fallbackStyle: fallbackStyle,
      fallbackBuilder: fallbackBuilder,
      nativeInsets: nativeInsets,
    );
  }
}

class _MonacoCodeViewState extends State<MonacoCodeView> {
  static int _nextViewCounter = 0;

  late final DesktopCodeViewApi _api;
  late final String _viewId;
  Rect? _lastRect;
  MonacoDocumentState? _lastSentState;
  bool _embeddedViewActive = false;
  bool _syncScheduled = false;

  bool get _shouldUseEmbeddedView => Platform.isLinux;

  @override
  void initState() {
    super.initState();
    _api = widget.api ?? MethodChannelDesktopCodeViewApi();
    _viewId =
        'monaco-${DateTime.now().microsecondsSinceEpoch}-${_nextViewCounter++}';
    if (_shouldUseEmbeddedView) {
      unawaited(_initializeEmbeddedView());
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_embeddedViewActive) {
      _scheduleSync();
    }
  }

  @override
  void didUpdateWidget(covariant MonacoCodeView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (_embeddedViewActive) {
      _scheduleSync();
    }
  }

  Future<void> _initializeEmbeddedView() async {
    final created = await _api.createView(viewId: _viewId);
    if (!mounted) {
      if (created) {
        await _api.disposeView(viewId: _viewId);
      }
      return;
    }
    if (!created) {
      return;
    }
    setState(() {
      _embeddedViewActive = true;
    });
    _scheduleSync();
  }

  void _scheduleSync() {
    if (_syncScheduled) {
      return;
    }
    _syncScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _syncScheduled = false;
      if (!mounted || !_embeddedViewActive) {
        return;
      }
      unawaited(_syncToNative());
    });
  }

  MonacoDocumentState _currentDocumentState(Brightness brightness) {
    final theme = Theme.of(context);
    return MonacoDocumentState(
      content: widget.content,
      language: widget.language,
      theme: brightness == Brightness.dark
          ? MonacoThemeMode.dark
          : MonacoThemeMode.light,
      themePalette: MonacoThemePalette.fromTheme(theme),
      followTail: widget.followTail,
      incrementalAppends: widget.incrementalAppends,
      maxRetainedLines: widget.maxRetainedLines,
    );
  }

  Future<void> _syncToNative() async {
    final rect = _lastRect;
    if (rect == null) {
      return;
    }
    final viewportSize = MediaQuery.maybeOf(context)?.size ?? Size.zero;
    final brightness = Theme.of(context).brightness;
    final nextState = _currentDocumentState(brightness);
    final adjustedRect = Rect.fromLTWH(
      rect.left + widget.nativeInsets.left,
      rect.top + widget.nativeInsets.top,
      (rect.width - widget.nativeInsets.horizontal).clamp(1.0, rect.width),
      (rect.height - widget.nativeInsets.vertical).clamp(1.0, rect.height),
    );
    final visible =
        adjustedRect.width > 1 &&
        adjustedRect.height > 1 &&
        adjustedRect.left >= 0 &&
        adjustedRect.top >= 0 &&
        adjustedRect.right <= viewportSize.width &&
        adjustedRect.bottom <= viewportSize.height;
    await _api.updateViewFrame(
      viewId: _viewId,
      left: adjustedRect.left,
      top: adjustedRect.top,
      width: adjustedRect.width,
      height: adjustedRect.height,
      visible: visible,
    );

    final messages = planMonacoHostMessages(
      previous: _lastSentState,
      next: nextState,
    );
    for (final message in messages) {
      await _api.postMessage(viewId: _viewId, message: message.toJson());
    }
    _lastSentState = nextState;
  }

  void _handleGeometryChanged(Rect rect) {
    if (_lastRect == rect) {
      return;
    }
    _lastRect = rect;
    if (_embeddedViewActive) {
      _scheduleSync();
    }
  }

  Widget _buildFallback(BuildContext context) {
    final builder = widget.fallbackBuilder;
    if (builder != null) {
      return builder(context, widget.content);
    }
    return Scrollbar(
      thumbVisibility: true,
      child: SingleChildScrollView(
        padding: widget.fallbackPadding,
        child: SelectableText(
          widget.content,
          style:
              widget.fallbackStyle ??
              Theme.of(context).textTheme.bodySmall?.copyWith(
                fontFamily: 'monospace',
                height: 1.45,
              ),
        ),
      ),
    );
  }

  @override
  void dispose() {
    if (_embeddedViewActive) {
      unawaited(_api.disposeView(viewId: _viewId));
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!_shouldUseEmbeddedView || !_embeddedViewActive) {
      return _buildFallback(context);
    }
    return _GeometryObserver(
      onGeometryChanged: _handleGeometryChanged,
      child: const SizedBox.expand(),
    );
  }
}

class _GeometryObserver extends SingleChildRenderObjectWidget {
  const _GeometryObserver({
    required this.onGeometryChanged,
    required super.child,
  });

  final ValueChanged<Rect> onGeometryChanged;

  @override
  RenderObject createRenderObject(BuildContext context) {
    return _RenderGeometryObserver(onGeometryChanged);
  }

  @override
  void updateRenderObject(
    BuildContext context,
    covariant _RenderGeometryObserver renderObject,
  ) {
    renderObject.onGeometryChanged = onGeometryChanged;
  }
}

class _RenderGeometryObserver extends RenderProxyBox {
  _RenderGeometryObserver(this.onGeometryChanged);

  ValueChanged<Rect> onGeometryChanged;
  Rect? _lastReportedRect;
  bool _reportScheduled = false;

  @override
  void paint(PaintingContext context, Offset offset) {
    super.paint(context, offset);
    _scheduleReport();
  }

  void _scheduleReport() {
    if (_reportScheduled) {
      return;
    }
    _reportScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _reportScheduled = false;
      if (!attached || !hasSize) {
        return;
      }
      final transform = getTransformTo(null);
      final rect = MatrixUtils.transformRect(transform, Offset.zero & size);
      if (_lastReportedRect == rect) {
        return;
      }
      _lastReportedRect = rect;
      onGeometryChanged(rect);
    });
  }
}

String _hexColor(Color color) {
  final red = (color.r * 255).round().clamp(0, 255);
  final green = (color.g * 255).round().clamp(0, 255);
  final blue = (color.b * 255).round().clamp(0, 255);
  return '#'
      '${red.toRadixString(16).padLeft(2, '0')}'
      '${green.toRadixString(16).padLeft(2, '0')}'
      '${blue.toRadixString(16).padLeft(2, '0')}';
}
