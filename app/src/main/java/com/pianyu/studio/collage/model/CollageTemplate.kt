package com.pianyu.studio.collage.model

import kotlin.math.ceil
import kotlin.math.min

data class CollageCell(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

data class CollageTemplate(
    val id: String,
    val photoCount: Int,
    val title: String,
    val cells: List<CollageCell>,
)

data class CollageRatio(
    val id: String,
    val title: String,
    val width: Int,
    val height: Int,
) {
    val value: Float = width.toFloat() / height
}

val supportedCollageRatios = listOf(
    CollageRatio("ratio_3_4", "3:4", 3, 4),
    CollageRatio("ratio_1_1", "1:1", 1, 1),
    CollageRatio("ratio_9_16", "9:16", 9, 16),
    CollageRatio("ratio_2_3", "2:3", 2, 3),
    CollageRatio("ratio_1_2", "1:2", 1, 2),
    CollageRatio("ratio_4_3", "4:3", 4, 3),
    CollageRatio("ratio_3_2", "3:2", 3, 2),
    CollageRatio("ratio_16_9", "16:9", 16, 9),
)

fun templatesForPhotoCount(photoCount: Int): List<CollageTemplate> {
    if (photoCount !in 2..9) return emptyList()
    if (photoCount == 2) return twoPhotoTemplates()

    val candidates = buildList {
        addTemplate(photoCount, "grid_2", "均匀网格", grid(photoCount, 2))
        addTemplate(photoCount, "grid_3", "紧凑网格", grid(photoCount, 3))
        addTemplate(photoCount, "columns", "竖向分栏", columns(photoCount))
        addTemplate(photoCount, "rows", "横向分栏", rows(photoCount))

        val leftStack = featuredLeft(photoCount, secondaryColumns = 1)
        addTemplate(photoCount, "left_stack", "左侧突出", leftStack)
        addTemplate(photoCount, "right_stack", "右侧突出", leftStack.mirrorX())

        val topRow = featuredTop(photoCount, secondaryColumns = photoCount - 1)
        addTemplate(photoCount, "top_row", "顶部突出", topRow)
        addTemplate(photoCount, "bottom_row", "底部突出", topRow.mirrorY())

        val leftGrid = featuredLeft(photoCount, secondaryColumns = 2)
        addTemplate(photoCount, "left_grid", "左大右网格", leftGrid)
        addTemplate(photoCount, "right_grid", "右大左网格", leftGrid.mirrorX())

        val topGrid = featuredTop(photoCount, secondaryColumns = 2)
        addTemplate(photoCount, "top_grid", "上大下网格", topGrid)
        addTemplate(photoCount, "bottom_grid", "下大上网格", topGrid.mirrorY())

        val leftDense = featuredLeft(photoCount, secondaryColumns = 3, focus = 0.52f)
        addTemplate(photoCount, "left_dense", "左焦点拼接", leftDense)
        addTemplate(photoCount, "right_dense", "右焦点拼接", leftDense.mirrorX())

        val topDense = featuredTop(photoCount, secondaryColumns = 3, focus = 0.54f)
        addTemplate(photoCount, "top_dense", "上焦点拼接", topDense)
        addTemplate(photoCount, "bottom_dense", "下焦点拼接", topDense.mirrorY())
    }

    return candidates.distinctBy { template ->
        template.cells.joinToString("|") { cell ->
            "${cell.left},${cell.top},${cell.width},${cell.height}"
        }
    }.take(14)
}

private fun twoPhotoTemplates(): List<CollageTemplate> = listOf(
    CollageTemplate("two_equal_columns", 2, "左右均分", weightedColumns(0.5f)),
    CollageTemplate("two_equal_rows", 2, "上下一致", weightedRows(0.5f)),
    CollageTemplate("two_left_focus", 2, "左侧突出", weightedColumns(0.62f)),
    CollageTemplate("two_right_focus", 2, "右侧突出", weightedColumns(0.38f)),
    CollageTemplate("two_top_focus", 2, "顶部突出", weightedRows(0.62f)),
    CollageTemplate("two_bottom_focus", 2, "底部突出", weightedRows(0.38f)),
    CollageTemplate("two_left_panorama", 2, "左侧全景", weightedColumns(0.72f)),
    CollageTemplate("two_right_panorama", 2, "右侧全景", weightedColumns(0.28f)),
    CollageTemplate("two_top_panorama", 2, "顶部全景", weightedRows(0.72f)),
    CollageTemplate("two_bottom_panorama", 2, "底部全景", weightedRows(0.28f)),
)

private fun MutableList<CollageTemplate>.addTemplate(
    count: Int,
    id: String,
    title: String,
    cells: List<CollageCell>,
) {
    add(CollageTemplate("${count}_$id", count, title, cells))
}

private fun weightedColumns(split: Float) = listOf(
    CollageCell(0f, 0f, split, 1f),
    CollageCell(split, 0f, 1f - split, 1f),
)

private fun weightedRows(split: Float) = listOf(
    CollageCell(0f, 0f, 1f, split),
    CollageCell(0f, split, 1f, 1f - split),
)

private fun columns(count: Int): List<CollageCell> = List(count) { index ->
    val width = 1f / count
    CollageCell(index * width, 0f, width, 1f)
}

private fun rows(count: Int): List<CollageCell> = List(count) { index ->
    val height = 1f / count
    CollageCell(0f, index * height, 1f, height)
}

private fun grid(count: Int, columnCount: Int): List<CollageCell> {
    val rows = ceil(count / columnCount.toFloat()).toInt()
    val rowHeight = 1f / rows
    return buildList {
        repeat(rows) { row ->
            val rowStart = row * columnCount
            val itemsInRow = min(columnCount, count - rowStart)
            val cellWidth = 1f / itemsInRow
            repeat(itemsInRow) { column ->
                add(CollageCell(column * cellWidth, row * rowHeight, cellWidth, rowHeight))
            }
        }
    }
}

private fun featuredLeft(
    count: Int,
    secondaryColumns: Int,
    focus: Float = 0.58f,
): List<CollageCell> {
    val secondary = grid(count - 1, secondaryColumns).map { cell ->
        CollageCell(
            left = focus + cell.left * (1f - focus),
            top = cell.top,
            width = cell.width * (1f - focus),
            height = cell.height,
        )
    }
    return listOf(CollageCell(0f, 0f, focus, 1f)) + secondary
}

private fun featuredTop(
    count: Int,
    secondaryColumns: Int,
    focus: Float = 0.58f,
): List<CollageCell> {
    val secondary = grid(count - 1, secondaryColumns).map { cell ->
        CollageCell(
            left = cell.left,
            top = focus + cell.top * (1f - focus),
            width = cell.width,
            height = cell.height * (1f - focus),
        )
    }
    return listOf(CollageCell(0f, 0f, 1f, focus)) + secondary
}

private fun List<CollageCell>.mirrorX(): List<CollageCell> = map { cell ->
    cell.copy(left = (1f - cell.left - cell.width).coerceIn(0f, 1f))
}

private fun List<CollageCell>.mirrorY(): List<CollageCell> = map { cell ->
    cell.copy(top = (1f - cell.top - cell.height).coerceIn(0f, 1f))
}
