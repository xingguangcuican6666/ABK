package io.github.xingguangcuican6666.abk.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.xingguangcuican6666.abk.ui.theme.AbkColors
import io.github.xingguangcuican6666.abk.ui.theme.abkSeedColors
import org.junit.Assert.assertEquals
import org.junit.Test

public class AbkPaletteTest {
    private data class Vector(val seed: Int, val dark: Boolean, val role: String, val argb: Int)

    private fun vectors(): List<Vector> {
        val stream = checkNotNull(javaClass.getResourceAsStream("/abk/seed-palettes.tsv"))
        val lines = stream.bufferedReader(Charsets.UTF_8).use { it.readLines() }
        check(lines.first() == "seed\tdark\trole\targb")
        return lines.drop(1).map { line ->
            val fields = line.split('\t')
            check(fields.size == 4)
            Vector(
                ("ff" + fields[0].removePrefix("#")).toLong(16).toInt(),
                fields[1].toBooleanStrict(), fields[2], fields[3].toLong(16).toInt(),
            )
        }
    }

    @Test public fun fixturesCoverEightSeedsTwoThemesAndEveryRoleExactlyOnce() {
        val vectors = vectors()
        assertEquals(512, vectors.size)
        assertEquals(512, vectors.map { Triple(it.seed, it.dark, it.role) }.toSet().size)
        val groups = vectors.groupBy { it.seed to it.dark }
        assertEquals(8, groups.keys.map { it.first }.toSet().size)
        assertEquals(16, groups.size)
        groups.forEach { (key, values) ->
            assertEquals(setOf(false, true), groups.keys.filter { it.first == key.first }.map { it.second }.toSet())
            assertEquals(webRoles(abkSeedColors(Color(key.first), key.second)).keys, values.map { it.role }.toSet())
        }
    }

    @Test public fun allSeedPaletteRolesMatchTheWebGoldenVectors() {
        val palettes = mutableMapOf<Pair<Int, Boolean>, Map<String, Color>>()
        vectors().forEach { vector ->
            val roles = palettes.getOrPut(vector.seed to vector.dark) {
                webRoles(abkSeedColors(Color(vector.seed), vector.dark))
            }
            val context = "seed=%08X dark=%s role=%s".format(vector.seed, vector.dark, vector.role)
            assertEquals(context, vector.argb, roles.getValue(vector.role).toArgb())
        }
    }

    private fun webRoles(c: AbkColors): Map<String, Color> = linkedMapOf(
        "bg" to c.background,
        "bg-raised" to c.raised,
        "bg-sunken" to c.sunken,
        "bg-input" to c.input,
        "ink" to c.ink,
        "ink-soft" to c.inkSoft,
        "ink-muted" to c.inkMuted,
        "ink-faint" to c.inkFaint,
        "line" to c.line,
        "line-strong" to c.lineStrong,
        "accent" to c.accent.color,
        "accent-ink" to c.accent.onColor,
        "accent-soft" to c.accent.container,
        "accent-soft-ink" to c.accent.onContainer,
        "accent-line" to c.line,
        "danger" to c.danger.color,
        "danger-ink" to c.danger.onColor,
        "danger-soft" to c.danger.container,
        "danger-soft-ink" to c.danger.onContainer,
        "info" to c.info.color,
        "info-ink" to c.info.onColor,
        "info-soft" to c.info.container,
        "info-soft-ink" to c.info.onContainer,
        "success" to c.success.color,
        "success-ink" to c.success.onColor,
        "success-soft" to c.success.container,
        "success-soft-ink" to c.success.onContainer,
        "warning" to c.warning.color,
        "warning-ink" to c.warning.onColor,
        "warning-soft" to c.warning.container,
        "warning-soft-ink" to c.warning.onContainer,
        "scrim" to c.scrim,
    )
}
