package com.abk.kernel.viewmodel

import com.abk.kernel.data.model.WorkflowRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class BuildSummaryParserTest {

    @Test
    fun parsesTimestampedAnsiBuildSummaryFields() {
        val run = WorkflowRun(
            id = 42L,
            name = "构建内核",
            status = "completed",
            conclusion = "success",
            htmlUrl = "https://github.com/example/repo/actions/runs/42",
            createdAt = "2026-03-01T00:00:00Z",
            updatedAt = "2026-03-01T00:10:00Z",
            runNumber = 7,
            workflowId = 10L,
            headBranch = "main",
            displayTitle = "Android 14 build"
        )
        val logs = """
            2026-03-01T00:00:01Z ${'\u001B'}[32m内核构建配置摘要${'\u001B'}[0m
            2026-03-01T00:00:02Z Android 版本: android14
            2026-03-01T00:00:03Z 内核版本: 6.1
            子版本号：162
            补丁级别: 2026-03
            KSU 变体: NyaSU
            KSU 分支: Stable(标准)
            构建时间:
            ZRAM 增强: true
            ZRAM 完整算法: false
            ZRAM 额外算法: lz4,zstd
            BBG 补丁: false
            DDK LSM: true
            NTsync 补丁: true
            Networing 增强: true
            Re-Kernel: false
            虚拟化支持: 678
            自定义注入: 无
            Stock Config: 启用
        """.trimIndent()

        val summary = parseBuildParameterSummary(logs, 42L, run)

        assertNotNull(summary)
        summary!!
        assertEquals(42L, summary.runId)
        assertEquals(7, summary.runNumber)
        assertEquals("Android 14 build", summary.runTitle)
        assertEquals("android14", summary.androidVersion)
        assertEquals("6.1", summary.kernelVersion)
        assertEquals("162", summary.subLevel)
        assertEquals("2026-03", summary.osPatchLevel)
        assertEquals("NyaSU", summary.ksuVariant)
        assertEquals("Stable(标准)", summary.ksuBranch)
        assertEquals("无", summary.buildTime)
        assertEquals("true", summary.networkingEnabled)
        assertEquals("678", summary.virtualizationSupport)
        assertEquals("启用", summary.stockConfig)
    }

    @Test
    fun retiredSusfsAndKpmRowsAreIgnored() {
        val logs = """
            内核构建配置摘要
            Android 版本: android16
            KSU 变体: NyaSU
            SUSFS 状态: 启用
            KPM 功能: true
            Re-Kernel: false
        """.trimIndent()

        val summary = parseBuildParameterSummary(logs, 7L, null)

        assertNotNull(summary)
        summary!!
        assertEquals("android16", summary.androidVersion)
        assertEquals("NyaSU", summary.ksuVariant)
        assertEquals("false", summary.reKernelEnabled)
        // SUSFS / KPM 已下线：旧 run 里的这两行既不是已知字段，也不进额外行。
        assertEquals(true, summary.extraRows.isNullOrEmpty())
        assertNull(summary.extraRows?.get("SUSFS 状态"))
        assertNull(summary.extraRows?.get("KPM 功能"))
    }

    @Test
    fun returnsNullWhenNoSummaryFieldsExist() {
        assertNull(parseBuildParameterSummary("plain logs without summary", 1L, null))
    }
}
