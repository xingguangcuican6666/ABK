package com.abk.kernel.data.repository

import com.abk.kernel.data.model.CustomExternalModuleStage
import com.abk.kernel.data.model.ModuleCatalogItemKind
import com.abk.kernel.data.model.shouldOfferAppUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubRepositoryParsingTest {

    private val repository = GitHubRepository()

    @Test
    fun parsesGithubRepositoryUrlVariants() {
        assertEquals(
            GithubRepositoryParts("owner", "repo", "feature/test"),
            repository.parseGithubRepository("https://github.com/owner/repo/tree/feature/test")
        )
        assertEquals(
            GithubRepositoryParts("owner", "repo", null),
            repository.parseGithubRepository("git@github.com:owner/repo.git")
        )
        assertNull(repository.parseGithubRepository("https://gitlab.com/owner/repo"))
    }

    @Test
    fun buildsModuleCatalogAndModuleConfCandidates() {
        assertEquals(
            listOf(
                "https://raw.githubusercontent.com/owner/repo/main/abk-modules.json",
                "https://raw.githubusercontent.com/owner/repo/master/abk-modules.json"
            ),
            repository.moduleCatalogIndexCandidates("https://github.com/owner/repo")
        )
        assertEquals(
            listOf("https://raw.githubusercontent.com/owner/repo/dev/module.conf"),
            repository.externalModuleConfCandidates("https://github.com/owner/repo/tree/dev")
        )
        assertEquals(
            listOf("https://example.com/catalog.json"),
            repository.moduleCatalogIndexCandidates("https://example.com/catalog.json")
        )
    }

    @Test
    fun parsesAndSanitizesModuleCatalogDocument() {
        val document = """
            {
              "name": "Demo Catalog",
              "modules": [
                {
                  "name": "Demo Module",
                  "version": "1.0",
                  "description": "desc",
                  "repoUrl": " https://github.com/demo/module.git ",
                  "supportedStages": ["after-patch", "before_build"],
                  "defaultStage": "before-build",
                  "recommendedStage": "after_patch,before_build",
                  "author": "tester",
                  "homepage": "https://example.com"
                },
                { "name": "Missing Repo" },
                { "name": "Duplicate", "repoUrl": "https://github.com/demo/module.git" }
              ]
            }
        """.trimIndent()

        val parsed = repository.parseModuleCatalogDocument(document, "https://github.com/demo/catalog")

        assertEquals("Demo Catalog", parsed.name)
        assertEquals(1, parsed.modules.size)
        assertEquals(2, parsed.skippedCount)
        val module = parsed.modules.first()
        assertEquals("Demo Module", module.name)
        assertEquals("https://github.com/demo/module.git", module.repoUrl)
        assertEquals(
            listOf(CustomExternalModuleStage.AFTER_PATCH, CustomExternalModuleStage.BEFORE_BUILD),
            module.supportedStages
        )
        assertEquals(CustomExternalModuleStage.BEFORE_BUILD, module.defaultStage)
        assertEquals(
            listOf(CustomExternalModuleStage.AFTER_PATCH, CustomExternalModuleStage.BEFORE_BUILD),
            module.recommendedStages
        )
    }

    @Test
    fun parsesModuleSetCatalogAndModuleConfChildren() {
        val document = """
            {
              "name": "Demo Catalog",
              "modules": [
                {
                  "name": "Security Suite",
                  "kind": "module_set",
                  "moduleSetId": "security_suite",
                  "repoUrl": "https://github.com/demo/security-suite",
                  "supportedStages": ["after_patch", "before_build"],
                  "defaultStage": "after_patch",
                  "recommendedStages": ["after_patch"]
                }
              ]
            }
        """.trimIndent()

        val parsed = repository.parseModuleCatalogDocument(document, "https://github.com/demo/catalog")
        val module = parsed.modules.single()
        assertEquals(ModuleCatalogItemKind.MODULE_SET, module.kind)
        assertEquals("security_suite", module.moduleSetId)

        val metadata = repository.parseExternalModuleConf(
            """
                ABK_MODULE_KIND="module_set"
                ABK_MODULE_SET_ID="security_suite"
                ABK_MODULE_SET_NAME="Security Suite"
                ABK_MODULE_SET_VERSION="0.1.0"
                ABK_MODULE_SET_DESCRIPTION="Grouped security patches"
                ABK_MODULE_SUPPORTED_STAGES="after_patch,before_build"
                ABK_MODULE_DEFAULT_STAGE="after_patch"
                ABK_MODULE_RECOMMENDED_STAGES="after_patch"
                ABK_MODULE_SET_ITEMS='
                feat_guard|SELinux Guard|Harden checks|https://github.com/demo/security-suite|after_patch,before_build|after_patch|after_patch|feat|true|false|Play Integrity Fix|https://example.com/pif.zip
                fix_cleanup|Policy Cleanup|Remove redundant grants|https://github.com/demo/security-suite|before_build|before_build|before_build|fix|false|false||
                '
            """.trimIndent()
        )

        assertEquals(ModuleCatalogItemKind.MODULE_SET, metadata.kind)
        assertEquals("security_suite", metadata.moduleSetId)
        assertEquals(2, metadata.children.size)
        assertEquals("feat_guard", metadata.children.first().id)
        assertEquals(CustomExternalModuleStage.AFTER_PATCH, metadata.children.first().defaultStage)
        assertEquals("Play Integrity Fix", metadata.children.first().magiskModuleName)
        assertEquals("https://example.com/pif.zip", metadata.children.first().magiskModuleDownloadUrl)
    }

    @Test
    fun parsesExternalModuleConfAndRejectsMissingName() {
        val metadata = repository.parseExternalModuleConf(
            """
                ABK_MODULE_NAME="Demo Module"
                ABK_MODULE_VERSION='1.2.3'
                ABK_MODULE_DESCRIPTION=Test module
                ABK_MODULE_SUPPORTED_STAGES=after-patch,before_build
                ABK_MODULE_DEFAULT_STAGE=before-build
                ABK_MODULE_RECOMMENDED_STAGES=before-build
                ABK_MAGISK_MODULE_NAME="Play Integrity Fix"
                ABK_MAGISK_MODULE_DOWNLOAD_URL="https://example.com/pif.zip"
            """.trimIndent()
        )

        assertEquals("Demo Module", metadata.name)
        assertEquals("1.2.3", metadata.version)
        assertEquals("Test module", metadata.description)
        assertEquals(
            listOf(CustomExternalModuleStage.AFTER_PATCH, CustomExternalModuleStage.BEFORE_BUILD),
            metadata.supportedStages
        )
        assertEquals(CustomExternalModuleStage.BEFORE_BUILD, metadata.defaultStage)
        assertEquals(listOf(CustomExternalModuleStage.BEFORE_BUILD), metadata.recommendedStages)
        assertEquals("Play Integrity Fix", metadata.magiskModuleName)
        assertEquals("https://example.com/pif.zip", metadata.magiskModuleDownloadUrl)

        assertThrows(IllegalStateException::class.java) {
            repository.parseExternalModuleConf("ABK_MODULE_VERSION=1")
        }
    }

    @Test
    fun parsesAppUpdateMetadataAndKeepsStableUntouched() {
        val document = """
            {
              "stable": {
                "normal": {
                  "versionName": "1.2.0",
                  "versionCode": 10020,
                  "downloadUrl": "https://example.com/abk-stable.apk"
                }
              },
              "unstable": {
                "normal": {
                  "versionName": "1.2.1-dev",
                  "versionCode": 10020,
                  "downloadUrl": "https://nightly.link/example/normal.zip",
                  "buildTimestampEpochMillis": 1710000000000,
                  "sourceWorkflow": "Build ABK App",
                  "commitSha": "abc123",
                  "runId": 42
                },
                "dev": {
                  "versionName": "1.2.1-dev2",
                  "versionCode": 10021,
                  "downloadUrl": "https://nightly.link/example/dev.zip"
                }
              }
            }
        """.trimIndent()

        val parsed = repository.parseAppUpdateMetadata(document)

        assertEquals("1.2.0", parsed.stable.normal?.versionName)
        assertEquals("https://example.com/abk-stable.apk", parsed.stable.normal?.downloadUrl)
        assertEquals("1.2.1-dev", parsed.unstable.normal?.versionName)
        assertEquals(1710000000000, parsed.unstable.normal?.buildTimestampEpochMillis)
        assertEquals("Build ABK App", parsed.unstable.normal?.sourceWorkflow)
        assertEquals("1.2.1-dev2", parsed.unstable.dev?.versionName)
        assertNull(parsed.stable.dev)
    }

    @Test
    fun appUpdateComparisonUsesBuildTimestampForSameVersionCode() {
        assertTrue(
            shouldOfferAppUpdate(
                remote = com.abk.kernel.data.model.AppUpdateEntry(
                    versionName = "1.2.0",
                    versionCode = 10020,
                    buildTimestampEpochMillis = 2000L
                ),
                currentVersionCode = 10020,
                currentBuildTimestampEpochMillis = 1000L
            )
        )
        assertFalse(
            shouldOfferAppUpdate(
                remote = com.abk.kernel.data.model.AppUpdateEntry(
                    versionName = "1.2.0",
                    versionCode = 10020,
                    buildTimestampEpochMillis = 1000L
                ),
                currentVersionCode = 10020,
                currentBuildTimestampEpochMillis = 1000L
            )
        )
        assertTrue(
            shouldOfferAppUpdate(
                remote = com.abk.kernel.data.model.AppUpdateEntry(
                    versionName = "1.3.0",
                    versionCode = 10021,
                    buildTimestampEpochMillis = 500L
                ),
                currentVersionCode = 10020,
                currentBuildTimestampEpochMillis = 999999L
            )
        )
    }
}
