package io.github.xingguangcuican6666.abk.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

@Composable
public fun AbkStat(
    label: String, value: String, modifier: Modifier = Modifier,
    delta: String? = null, deltaTone: AbkTone = AbkTone.Neutral,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AbkText(label, style = MaterialTheme.typography.bodyMedium, color = AbkTheme.colors.inkMuted)
        AbkText(value, style = MaterialTheme.typography.headlineLarge)
        if (delta != null) AbkText(delta, style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.status(deltaTone).color)
    }
}

@Composable
public fun AbkKeyValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        AbkText(label, Modifier.weight(1f), MaterialTheme.typography.bodyMedium, AbkTheme.colors.inkMuted)
        androidx.compose.material3.Text(value, Modifier.weight(1f), textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelMedium, color = AbkTheme.colors.ink)
    }
}

/** The host performs copying and owns the feedback state; the library needs no clipboard permission. */
@Composable
public fun AbkCodeBlock(
    code: String, modifier: Modifier = Modifier, title: String? = null,
    onCopy: (() -> Unit)? = null, copied: Boolean = false,
    copyLabel: String = "复制", copiedLabel: String = "已复制",
) {
    Surface(modifier, shape = AbkShapes.Medium, color = AbkTheme.colors.sunken, border = BorderStroke(1.dp, AbkTheme.colors.line)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null || onCopy != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AbkText(title ?: "", Modifier.weight(1f), MaterialTheme.typography.labelSmall, AbkTheme.colors.inkMuted)
                if (onCopy != null) AbkButton(onCopy, style = AbkButtonStyle.Ghost, size = AbkSize.Small) {
                    AbkText(if (copied) copiedLabel else copyLabel, style = MaterialTheme.typography.labelSmall)
                }
            }
            SelectionContainer {
                androidx.compose.material3.Text(code, Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    fontFamily = AbkTheme.monoFont, style = MaterialTheme.typography.bodyMedium, color = AbkTheme.colors.inkSoft)
            }
        }
    }
}

public data class AbkTableColumn(val label: String, val width: Dp = 140.dp, val numeric: Boolean = false)

/** Header stays outside the vertical body viewport; horizontal scrolling always moves both together. */
@Composable
public fun AbkTable(
    columns: List<AbkTableColumn>, rows: List<List<String>>, modifier: Modifier = Modifier,
    maxBodyHeight: Dp = 280.dp,
) {
    require(columns.isNotEmpty() && columns.all { it.width > 0.dp }) { "Table columns need positive widths" }
    require(rows.all { it.size == columns.size }) { "Each row must have one cell per column" }
    require(maxBodyHeight > 0.dp) { "Body viewport height must be positive" }
    val tableWidth = columns.fold(0.dp) { width, column -> width + column.width }
    Surface(modifier, shape = AbkShapes.Large, color = AbkTheme.colors.raised, border = BorderStroke(1.dp, AbkTheme.colors.line)) {
        Column(Modifier.horizontalScroll(rememberScrollState()).width(tableWidth)
            .semantics { collectionInfo = CollectionInfo(rows.size + 1, columns.size) }) {
            Row(Modifier.background(AbkTheme.colors.sunken)) {
                columns.forEachIndexed { index, column ->
                    TableCell(column.label, column, true, Modifier.semantics {
                        heading(); collectionItemInfo = CollectionItemInfo(0, 1, index, 1)
                    })
                }
            }
            AbkDivider()
            Column(Modifier.heightIn(max = maxBodyHeight).verticalScroll(rememberScrollState())) {
                rows.forEachIndexed { rowIndex, row ->
                    if (rowIndex > 0) AbkDivider()
                    Row {
                        row.forEachIndexed { columnIndex, cell ->
                            TableCell(cell, columns[columnIndex], false, Modifier.semantics {
                                collectionItemInfo = CollectionItemInfo(rowIndex + 1, 1, columnIndex, 1)
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(text: String, column: AbkTableColumn, header: Boolean, modifier: Modifier) {
    Box(modifier.width(column.width).defaultMinSize(minHeight = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = if (column.numeric) Alignment.CenterEnd else Alignment.CenterStart) {
        androidx.compose.material3.Text(text, color = if (header) AbkTheme.colors.inkMuted else AbkTheme.colors.ink,
            style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
            textAlign = if (column.numeric) TextAlign.End else TextAlign.Start)
    }
}
