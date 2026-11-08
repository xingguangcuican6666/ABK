import 'package:abk_desktop/src/features/build/kernel_support.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  // An LTS build is addressed as sub_level=X + os_patch_level=lts. `subLevelOptions()`
  // offers 'X', so `patchLevelOptions()` must resolve it to 'lts'; otherwise the
  // fallback in BuildFormState.normalized() selects a numeric month and the
  // dispatched combination is rejected by build.yml and the CLI.
  test('sub level X resolves to the lts patch level only', () {
    expect(
      DesktopKernelSupport.patchLevelOptions('android15', '6.6', 'X'),
      equals(<String>['lts']),
    );
  });

  test('sub level X resolves to lts on every kernel line', () {
    for (final line in DesktopKernelSupport.lines) {
      expect(
        DesktopKernelSupport.patchLevelOptions(
          line.androidVersion,
          line.kernelVersion,
          'X',
        ),
        equals(<String>['lts']),
        reason: '${line.androidVersion}/${line.kernelVersion}',
      );
    }
  });

  test('numeric sub levels keep their month patch levels', () {
    expect(
      DesktopKernelSupport.patchLevelOptions('android15', '6.6', '127'),
      equals(<String>['2026-04']),
    );
  });
}
