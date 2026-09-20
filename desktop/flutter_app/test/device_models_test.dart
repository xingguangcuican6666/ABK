import 'package:abk_desktop/src/core/models/device_models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('groups ABK runtime children with group metadata as module sets', () {
    final module = AbkRuntimeModule.fromJson(const <String, dynamic>{
      'id': 'abi-bridge',
      'name': 'ABK ABI Bridge',
      'type': 'builtin',
      'source': 'abk',
      'group_name': 'ABK Control Module',
      'group_id': 'abk-control',
      'group_repo_url':
          'https://github.com/xingguangcuican6666/ABK_control_module',
    });

    expect(module.isCustomModuleSetChild, isTrue);
    expect(module.isStandardRuntimeModule, isFalse);
    expect(module.isCustomModule, isFalse);
  });

  test('treats abk module_set entries as module sets and groups by repo', () {
    final module = AbkRuntimeModule.fromJson(const <String, dynamic>{
      'id': 'abi_bridge',
      'name': 'ABK ABI Bridge',
      'type': 'builtin',
      'source': 'abk',
      'entry_kind': 'module_set',
      'repo_url':
          'https://github.com/xingguangcuican6666/ABK_ABI_PATCH_SUITE.git',
    });

    expect(module.isCustomModuleSetChild, isTrue);
    expect(
      module.moduleGroupKey,
      'repo:https://github.com/xingguangcuican6666/abk_abi_patch_suite.git',
    );
    expect(module.moduleSetDisplayName, 'ABK_ABI_PATCH_SUITE');
  });

  test('treats ksud modules without group metadata as standard modules', () {
    final module = AbkRuntimeModule.fromJson(const <String, dynamic>{
      'id': 'zygisk-next',
      'name': 'Zygisk Next',
      'type': 'standard',
      'source': 'ksud',
    });

    expect(module.isStandardRuntimeModule, isTrue);
    expect(module.isCustomModuleSetChild, isFalse);
    expect(module.isCustomModule, isFalse);
  });

  test('does not treat non-abk sources with group metadata as module sets', () {
    final module = AbkRuntimeModule.fromJson(const <String, dynamic>{
      'id': 'zygisk-next',
      'name': 'Zygisk Next',
      'type': 'standard',
      'source': 'ksud',
      'group_name': 'Not an ABK set',
      'group_id': 'fake-group',
      'group_repo_url': 'https://example.com/not-abk',
    });

    expect(module.isStandardRuntimeModule, isTrue);
    expect(module.isCustomModuleSetChild, isFalse);
    expect(module.isCustomModule, isFalse);
  });

  test('treats abk custom single modules without group metadata as custom', () {
    final module = AbkRuntimeModule.fromJson(const <String, dynamic>{
      'id': 'abk-control',
      'name': 'ABK Control Module',
      'type': 'builtin',
      'source': 'abk',
    });

    expect(module.isCustomModule, isTrue);
    expect(module.isStandardRuntimeModule, isFalse);
    expect(module.isCustomModuleSetChild, isFalse);
  });
}
