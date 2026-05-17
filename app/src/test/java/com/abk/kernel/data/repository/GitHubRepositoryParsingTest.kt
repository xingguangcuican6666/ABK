package com.abk.kernel.data.repository

import com.abk.kernel.data.model.CustomExternalModuleStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
}
