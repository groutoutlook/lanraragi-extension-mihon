package eu.kanade.tachiyomi.extension.all.lanraragi

import android.net.Uri

internal class ChapterTitle(val series: String, val number: Float)

private val chapterSuffix = Regex(
    """^(.+?)[\s_-]+(?:c|ch(?:apter)?\.?)[\s_-]*(\d+(?:\.\d+)?)(?:\.(?:zip|cbz|rar|cbr|7z|pdf))?$""",
    RegexOption.IGNORE_CASE,
)

internal fun parseChapterTitle(title: String): ChapterTitle? {
    val match = chapterSuffix.matchEntire(title.trim()) ?: return null
    return ChapterTitle(match.groupValues[1], match.groupValues[2].toFloat())
}

internal fun Archive.seriesTitle(): String? {
    if (arcid.startsWith("TANK_")) return null
    return namespaceTagValues(tags, listOf("series"))?.firstOrNull() ?: parseChapterTitle(title)?.series
}

internal fun Archive.chapterNumber(): Float? = namespaceTagValues(tags, listOf("chapter"))?.firstOrNull()?.toFloatOrNull()
    ?: parseChapterTitle(title)?.number

internal fun Archive.chapterEdition(): String? = tags?.split(',')
    ?.mapNotNull {
        when (it.trim().lowercase()) {
            "official" -> "Official"
            "uncensored" -> "Uncensored"
            else -> null
        }
    }
    ?.distinct()
    ?.takeIf { it.isNotEmpty() }
    ?.joinToString()

internal fun seriesUrl(series: String): String = "/series?title=${Uri.encode(series.lowercase())}"
