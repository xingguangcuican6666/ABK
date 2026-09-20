import 'package:abk_desktop/src/core/models/build_models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('recognizes Chinese kernel workflow names as kernel builds', () {
    const run = BuildRunSummary(
      id: 215,
      name: 'Android 内核构建-自定义',
      displayTitle: 'Android 内核构建-自定义 #215',
      status: 'completed',
      conclusion: 'success',
      event: 'workflow_dispatch',
      headBranch: 'dev',
      htmlUrl: 'https://github.com/foo/bar/actions/runs/215',
      createdAt: '2026-07-15T00:54:00Z',
      updatedAt: '2026-07-15T01:19:49Z',
      runNumber: 215,
    );

    expect(run.looksLikeKernelBuild, isTrue);
  });

  test('still excludes ABK app workflows', () {
    const run = BuildRunSummary(
      id: 999,
      name: 'Build ABK App',
      displayTitle: 'Build ABK App',
      status: 'completed',
      conclusion: 'success',
      event: 'workflow_dispatch',
      headBranch: 'main',
      htmlUrl: 'https://github.com/foo/bar/actions/runs/999',
      createdAt: '2026-07-15T00:54:00Z',
      updatedAt: '2026-07-15T01:19:49Z',
      runNumber: 999,
    );

    expect(run.looksLikeKernelBuild, isFalse);
  });

  test('classifies workflow artifacts into Android-aligned categories', () {
    const kernel = BuildArtifactSummary(
      id: 1,
      name: 'ReSuKiSU_kernel-android14-6.1.zip',
      sizeBytes: 1,
      expired: false,
      archiveDownloadUrl: null,
    );
    const manager = BuildArtifactSummary(
      id: 2,
      name: 'ABK-manager-arm64.apk',
      sizeBytes: 1,
      expired: false,
      archiveDownloadUrl: null,
    );
    const module = BuildArtifactSummary(
      id: 3,
      name: 'susfs-module-1.0.zip',
      sizeBytes: 1,
      expired: false,
      archiveDownloadUrl: null,
    );

    expect(kernel.artifactType, BuildArtifactType.kernelPackage);
    expect(kernel.artifactCategory, BuildArtifactCategory.kernel);
    expect(manager.artifactType, BuildArtifactType.abkManager);
    expect(manager.artifactCategory, BuildArtifactCategory.manager);
    expect(module.artifactType, BuildArtifactType.susfsModule);
    expect(module.artifactCategory, BuildArtifactCategory.module);
  });
}
