package com.abk.kernel.utils

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RangeDownloadTest {
    @Test
    fun plansContiguousRanges() {
        assertEquals(
            listOf(
                DownloadUtils.ByteRange(0, 2),
                DownloadUtils.ByteRange(3, 5),
                DownloadUtils.ByteRange(6, 9),
            ),
            DownloadUtils.planByteRanges(10, 3)
        )
    }

    @Test
    fun parsesAndRejectsContentRanges() {
        assertEquals(DownloadUtils.RangeInfo(2, 4, 10), DownloadUtils.parseContentRange("bytes 2-4/10"))
        assertNull(DownloadUtils.parseContentRange("bytes 4-2/10"))
        assertNull(DownloadUtils.parseContentRange("bytes 0-10/10"))
        assertNull(DownloadUtils.parseContentRange("invalid"))
    }

    @Test
    fun downloadsAndMergesRanges() = runBlocking {
        val content = ByteArray(1024) { (it % 251).toByte() }
        withServer(rangeDispatcher(content)) { server, destination ->
            DownloadUtils.downloadUrlToFile(server.url("/file").toString(), emptyMap(), destination, content.size.toLong(), 8) {}
            assertArrayEquals(content, destination.readBytes())
        }
    }

    @Test
    fun fallsBackWhenServerIgnoresRange() = runBlocking {
        val content = "single response".toByteArray()
        withServer(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody(Buffer().write(content))
        }) { server, destination ->
            DownloadUtils.downloadUrlToFile(server.url("/file").toString(), emptyMap(), destination, content.size.toLong(), 8) {}
            assertArrayEquals(content, destination.readBytes())
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun retriesFailedRange() = runBlocking {
        val content = ByteArray(256) { it.toByte() }
        var failed = false
        val dispatcher = rangeDispatcher(content) { request ->
            if (!failed && request.getHeader("Range") != "bytes=0-0") {
                failed = true
                MockResponse().setResponseCode(500)
            } else null
        }
        withServer(dispatcher) { server, destination ->
            DownloadUtils.downloadUrlToFile(server.url("/file").toString(), emptyMap(), destination, content.size.toLong(), 4) {}
            assertArrayEquals(content, destination.readBytes())
        }
    }

    private fun rangeDispatcher(
        content: ByteArray,
        override: (RecordedRequest) -> MockResponse? = { null }
    ) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            override(request)?.let { return it }
            val header = request.getHeader("Range") ?: return MockResponse().setBody(Buffer().write(content))
            val match = Regex("bytes=(\\d+)-(\\d+)").matchEntire(header) ?: return MockResponse().setResponseCode(416)
            val start = match.groupValues[1].toInt()
            val end = match.groupValues[2].toInt()
            return MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes $start-$end/${content.size}")
                .setBody(Buffer().write(content, start, end - start + 1))
        }
    }

    private suspend fun withServer(
        dispatcher: Dispatcher,
        block: suspend (MockWebServer, File) -> Unit
    ) {
        val server = MockWebServer().apply { this.dispatcher = dispatcher }
        val dir = Files.createTempDirectory("range-download-").toFile()
        try {
            server.start()
            block(server, File(dir, "download.bin"))
        } finally {
            server.shutdown()
            dir.deleteRecursively()
        }
    }
}
