import 'dart:convert';
import 'dart:io';

import 'package:abk_desktop/src/widgets/monaco_code_view.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const platformChannel = MethodChannel('com.abk.desktop/platform');

  tearDown(() async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(platformChannel, null);
  });

  test('plans initialize and append Monaco messages', () {
    final initial = MonacoDocumentState(
      content: 'line 1',
      language: MonacoCodeLanguage.plaintext,
      theme: MonacoThemeMode.light,
      themePalette: const MonacoThemePalette(
        background: '#ffffff',
        foreground: '#111111',
        lineNumber: '#777777',
        activeLineNumber: '#333333',
        gutterBackground: '#ffffff',
        widgetBackground: '#f4f4f4',
        selectionBackground: '#ddeeff',
        inactiveSelectionBackground: '#ccddee',
        comment: '#666666',
        string: '#008877',
        number: '#aa6600',
        keyword: '#0044cc',
      ),
      followTail: true,
      incrementalAppends: true,
      maxRetainedLines: 5000,
    );
    final next = MonacoDocumentState(
      content: 'line 1\nline 2',
      language: MonacoCodeLanguage.plaintext,
      theme: MonacoThemeMode.light,
      themePalette: const MonacoThemePalette(
        background: '#ffffff',
        foreground: '#111111',
        lineNumber: '#777777',
        activeLineNumber: '#333333',
        gutterBackground: '#ffffff',
        widgetBackground: '#f4f4f4',
        selectionBackground: '#ddeeff',
        inactiveSelectionBackground: '#ccddee',
        comment: '#666666',
        string: '#008877',
        number: '#aa6600',
        keyword: '#0044cc',
      ),
      followTail: true,
      incrementalAppends: true,
      maxRetainedLines: 5000,
    );

    final initializeMessage = planMonacoHostMessages(
      previous: null,
      next: initial,
    ).single.toJson();
    expect(initializeMessage['type'], 'initialize');
    expect(initializeMessage['content'], 'line 1');
    expect(initializeMessage['language'], 'plaintext');
    expect(initializeMessage['theme'], 'light');
    expect(initializeMessage['followTail'], isTrue);
    expect(initializeMessage['maxRetainedLines'], 5000);
    expect(initializeMessage['themeData'], isA<Map<String, String>>());

    expect(
      planMonacoHostMessages(
        previous: initial,
        next: next,
      ).map((message) => message.toJson()).toList(growable: false),
      <Map<String, Object?>>[
        <String, Object?>{'type': 'appendContent', 'content': '\nline 2'},
      ],
    );
  });

  test('plans replace when content is rewritten', () {
    final previous = MonacoDocumentState(
      content: 'line 1\nline 2',
      language: MonacoCodeLanguage.plaintext,
      theme: MonacoThemeMode.light,
      themePalette: const MonacoThemePalette(
        background: '#ffffff',
        foreground: '#111111',
        lineNumber: '#777777',
        activeLineNumber: '#333333',
        gutterBackground: '#ffffff',
        widgetBackground: '#f4f4f4',
        selectionBackground: '#ddeeff',
        inactiveSelectionBackground: '#ccddee',
        comment: '#666666',
        string: '#008877',
        number: '#aa6600',
        keyword: '#0044cc',
      ),
      followTail: true,
      incrementalAppends: true,
      maxRetainedLines: 5000,
    );
    final next = MonacoDocumentState(
      content: 'line 1 rewritten\nline 2',
      language: MonacoCodeLanguage.json,
      theme: MonacoThemeMode.dark,
      themePalette: const MonacoThemePalette(
        background: '#101010',
        foreground: '#f5f5f5',
        lineNumber: '#777777',
        activeLineNumber: '#dddddd',
        gutterBackground: '#101010',
        widgetBackground: '#181818',
        selectionBackground: '#223366',
        inactiveSelectionBackground: '#1a2440',
        comment: '#888888',
        string: '#00aa99',
        number: '#ffbb33',
        keyword: '#88bbff',
      ),
      followTail: false,
      incrementalAppends: true,
      maxRetainedLines: 4000,
    );

    final messages = planMonacoHostMessages(
      previous: previous,
      next: next,
    ).map((message) => message.toJson()).toList(growable: false);
    expect(messages[0]['type'], 'setTheme');
    expect(messages[0]['theme'], 'dark');
    expect(messages[0]['themeData'], isA<Map<String, String>>());
    expect(messages[1], <String, Object?>{
      'type': 'setLanguage',
      'language': 'json',
    });
    expect(messages[2], <String, Object?>{
      'type': 'setFollowTail',
      'followTail': false,
    });
    expect(messages[3], <String, Object?>{
      'type': 'setRetentionLimit',
      'maxRetainedLines': 4000,
    });
    expect(messages[4], <String, Object?>{
      'type': 'replaceContent',
      'content': 'line 1 rewritten\nline 2',
    });
  });

  testWidgets('falls back to Flutter text when embedded view is unavailable', (
    tester,
  ) async {
    if (!Platform.isLinux) {
      return;
    }

    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(platformChannel, (call) async {
          if (call.method == 'createEmbeddedCodeView') {
            return false;
          }
          return null;
        });

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 420,
            height: 220,
            child: MonacoCodeView(
              content: 'hello\nworld',
              language: MonacoCodeLanguage.plaintext,
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('hello\nworld'), findsOneWidget);
  });

  testWidgets('sends initialize and append messages through the platform API', (
    tester,
  ) async {
    if (!Platform.isLinux) {
      return;
    }

    final calls = <MethodCall>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(platformChannel, (call) async {
          calls.add(call);
          if (call.method == 'createEmbeddedCodeView') {
            return true;
          }
          return true;
        });

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 420,
            height: 220,
            child: MonacoLogView(content: 'line 1'),
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.pump();

    final initialMessages = calls
        .where((call) => call.method == 'postEmbeddedCodeViewMessage')
        .map((call) {
          final arguments = call.arguments as Map<Object?, Object?>;
          return jsonDecode(arguments['message']! as String)
              as Map<String, dynamic>;
        })
        .toList(growable: false);
    final initializeMessage = initialMessages.singleWhere(
      (message) => message['type'] == 'initialize',
    );
    expect(initializeMessage['content'], 'line 1');
    expect(initializeMessage['language'], 'plaintext');
    expect(initializeMessage['theme'], 'light');
    expect(initializeMessage['followTail'], isTrue);
    expect(initializeMessage['maxRetainedLines'], 5000);
    expect(initializeMessage['themeData'], isA<Map<String, dynamic>>());

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 420,
            height: 220,
            child: MonacoLogView(content: 'line 1\nline 2'),
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.pump();

    final appendMessages = calls
        .where((call) => call.method == 'postEmbeddedCodeViewMessage')
        .map((call) {
          final arguments = call.arguments as Map<Object?, Object?>;
          return jsonDecode(arguments['message']! as String)
              as Map<String, dynamic>;
        })
        .where((message) => message['type'] == 'appendContent')
        .toList(growable: false);
    expect(appendMessages, isNotEmpty);
    expect(appendMessages.last['content'], '\nline 2');
  });
}
