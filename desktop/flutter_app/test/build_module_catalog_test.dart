import 'dart:async';

import 'package:abk_desktop/src/features/build/build_module_catalog.dart';
import 'package:http/http.dart' as http;
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('parseExternalModuleConf parses ABK module set metadata', () {
    final metadata = parseExternalModuleConf('''
ABK_MODULE_KIND=module_set
ABK_MODULE_SET_NAME="ABK Extras"
ABK_MODULE_SET_VERSION="1.2.3"
ABK_MODULE_SET_DESCRIPTION="A grouped module pack"
ABK_MODULE_SUPPORTED_STAGES=after_patch,before_build
ABK_MODULE_DEFAULT_STAGE=after_patch
ABK_MODULE_SET_ITEMS="
graphics|Graphics Pack|GPU tuning|https://github.com/acme/graphics|after_patch,before_build|after_patch|before_build|driver|1|0||
battery|Battery Pack|Battery tuning|https://github.com/acme/battery|after_patch|after_patch|after_patch|power|0|0||
"
''');

    expect(metadata.isModuleSet, isTrue);
    expect(metadata.name, 'ABK Extras');
    expect(metadata.version, '1.2.3');
    expect(metadata.children, hasLength(2));
    expect(metadata.children.first.id, 'graphics');
    expect(metadata.children.first.supportedStages, <String>[
      'after_patch',
      'before_build',
    ]);
    expect(metadata.children.first.recommendedStages, <String>['before_build']);
  });

  test('parseSelectedBuildModules keeps module-set workflow entries', () {
    final modules = parseSelectedBuildModules(
      'set:https://github.com/acme/abk-set#graphics;before_build|module:https://github.com/acme/plain-module;after_patch',
    );

    expect(modules, hasLength(2));
    expect(modules.first.isModuleSetChild, isTrue);
    expect(modules.first.groupRepoUrl, 'https://github.com/acme/abk-set');
    expect(modules.first.childId, 'graphics');
    expect(modules.first.stage, 'before_build');
    expect(modules.last.isModuleSetChild, isFalse);
    expect(modules.last.repoUrl, 'https://github.com/acme/plain-module');
  });

  test('selectedBuildModulesFromModuleSet serializes set workflow syntax', () {
    const child = BuildModuleSetChildMetadata(
      id: 'graphics',
      name: 'Graphics Pack',
      description: 'GPU tuning',
      repoUrl: 'https://github.com/acme/graphics',
      supportedStages: <String>['after_patch', 'before_build'],
      defaultStage: 'after_patch',
      recommendedStages: <String>['before_build'],
      groupRole: 'driver',
      controllable: true,
      hasWebUi: false,
      magiskModuleName: '',
      magiskModuleDownloadUrl: '',
    );
    const metadata = BuildExternalModuleMetadata(
      name: 'ABK Extras',
      version: '1.2.3',
      description: 'A grouped module pack',
      kind: 'module_set',
      moduleSetId: 'abk-extras',
      supportedStages: <String>['after_patch', 'before_build'],
      defaultStage: 'after_patch',
      recommendedStages: <String>['after_patch'],
      children: <BuildModuleSetChildMetadata>[child],
      magiskModuleName: '',
      magiskModuleDownloadUrl: '',
    );

    final modules = selectedBuildModulesFromModuleSet(
      groupRepoUrl: 'https://github.com/acme/abk-set',
      metadata: metadata,
      child: child,
      stages: const <String>['before_build'],
      fromCatalog: true,
    );

    expect(modules, hasLength(1));
    expect(
      modules.single.workflowEntry,
      'set:https://github.com/acme/abk-set#graphics;before_build',
    );
    expect(modules.single.groupName, 'ABK Extras');
    expect(modules.single.childName, 'Graphics Pack');
  });

  test(
    'fetchModuleMetadata times out instead of hanging indefinitely',
    () async {
      final client = BuildModuleCatalogClient(
        client: _NeverRespondingClient(),
        requestTimeout: const Duration(milliseconds: 10),
      );
      addTearDown(client.close);

      expect(
        () => client.fetchModuleMetadata('https://github.com/acme/abk-set'),
        throwsA(
          isA<FormatException>().having(
            (error) => error.message,
            'message',
            contains('timed out'),
          ),
        ),
      );
    },
  );
}

class _NeverRespondingClient extends http.BaseClient {
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    return Completer<http.StreamedResponse>().future;
  }
}
