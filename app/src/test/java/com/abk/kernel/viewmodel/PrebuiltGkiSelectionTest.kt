package com.abk.kernel.viewmodel

import com.abk.kernel.data.model.GitHubReleaseSummary
import com.abk.kernel.data.model.KernelBuildConfig
import com.abk.kernel.data.model.PrebuiltGkiAsset
import com.abk.kernel.data.model.ReleaseAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrebuiltGkiSelectionTest {

    @Test
    fun releaseAndAssetConversionUseStableFallbackIds() {
        val release = prebuiltGkiReleaseFromGitHub(
            GitHubReleaseSummary(
                id = 0L,
                tagName = "gki-android14-6.1.162",
                name = "",
                htmlUrl = "https://github.com/example/repo/releases/tag/gki",
                publishedAt = "2026-03-01T00:00:00Z",
                body = "Prebuilt boot.img"
            )
        )
        val assets = prebuiltGkiAssetsFromReleaseAssets(
            release,
            listOf(
                ReleaseAsset(
                    id = 0L,
                    name = "boot-android14-6.1.162.img",
                    size = 1234L,
                    browserDownloadUrl = "https://example.com/boot.img"
                )
            )
        )

        assertTrue(release.id > 0L)
        assertEquals(0L, release.apiId)
        assertEquals("gki-android14-6.1.162", release.name)
        assertEquals(1, assets.size)
        assertTrue(assets.first().id > 0L)
        assertEquals("gki-android14-6.1.162", assets.first().releaseTag)
        assertEquals(1234L, assets.first().sizeBytes)
    }

    @Test
    fun filtersPrebuiltReleaseAndAssetCandidates() {
        assertTrue(
            isPrebuiltGkiReleaseCandidate(
                GitHubReleaseSummary(
                    tagName = "gki-android14-boot",
                    name = "Prebuilt GKI",
                    htmlUrl = "",
                    body = "Contains boot.img"
                )
            )
        )
        assertFalse(
            isPrebuiltGkiReleaseCandidate(
                GitHubReleaseSummary(
                    tagName = "v1.1.0",
                    name = "ABK App",
                    htmlUrl = "",
                    body = "APK release"
                )
            )
        )
        assertTrue(isPrebuiltGkiCandidate(asset("boot-android14-6.1.162.img")))
        assertTrue(isPrebuiltGkiCandidate(asset("AnyKernel3-android14-6.1.162.zip")))
        assertFalse(isPrebuiltGkiCandidate(asset("KernelSU-Manager.apk")))
    }

    @Test
    fun comparatorPrefersRecommendedAndroidKernelPatchMatch() {
        val recommended = KernelBuildConfig(
            androidVersion = "android14",
            kernelVersion = "6.1",
            subLevel = "162",
            osPatchLevel = "2026-03"
        )
        val exact = asset(
            name = "boot-android14-6.1.162-2026-03.img",
            releaseTag = "gki-android14-6.1.162",
            releaseName = "Android 14 GKI",
            releaseBody = "2026-03"
        )
        val generic = asset(
            name = "boot-6.1.141.img",
            releaseTag = "gki-6.1.141",
            publishedAt = "2026-04-01T00:00:00Z"
        )

        val sorted = listOf(generic, exact).sortedWith(prebuiltGkiComparator(recommended))

        assertEquals(exact, sorted.first())
        assertTrue(prebuiltRecommendationScore(exact, recommended) > prebuiltRecommendationScore(generic, recommended))
    }

    private fun asset(
        name: String,
        releaseTag: String = "test",
        releaseName: String = "Test",
        releaseBody: String = "",
        publishedAt: String = "2026-03-01T00:00:00Z"
    ): PrebuiltGkiAsset = PrebuiltGkiAsset(
        id = name.hashCode().toLong().let { if (it < 0) -it else it },
        name = name,
        sizeBytes = 1L,
        browserDownloadUrl = "https://example.com/$name",
        releaseTag = releaseTag,
        releaseName = releaseName,
        releaseHtmlUrl = "https://example.com/releases/$releaseTag",
        publishedAt = publishedAt,
        releaseBody = releaseBody
    )
}
