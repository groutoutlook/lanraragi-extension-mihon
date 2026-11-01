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

internal fun seriesUrl(series: String): String = "/series?title=${Uri.encode(series)}"
