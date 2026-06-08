package com.abk.kernel.data.repository

import com.abk.kernel.data.model.CustomExternalModuleStage
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
    fun parsesExternalModuleConfAndRejectsMissingName() {
        val metadata = repository.parseExternalModuleConf(
            """
                ABK_MODULE_NAME="Demo Module"
                ABK_MODULE_VERSION='1.2.3'
                ABK_MODULE_DESCRIPTION=Test module
                ABK_MODULE_SUPPORTED_STAGES=after-patch,before_build
                ABK_MODULE_DEFAULT_STAGE=before-build
                ABK_MODULE_RECOMMENDED_STAGES=before-build
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
