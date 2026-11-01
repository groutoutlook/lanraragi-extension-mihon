package eu.kanade.tachiyomi.extension.all.lanraragi

internal const val SERIES_PAGE_SIZE = 20

internal class SeriesCatalogPage(val titles: List<String>, val hasNextPage: Boolean)

internal fun seriesCatalogPage(tags: List<DatabaseTag>, page: Int, query: String, descending: Boolean): SeriesCatalogPage {
    val titles = tags.asSequence()
        .filter { it.namespace.equals("series", ignoreCase = true) }
        .map { it.text.trim() }
        .filter { it.isNotEmpty() && it.contains(query.trim(), ignoreCase = true) }
        .distinctBy { it.lowercase() }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
        .toList()
        .let { if (descending) it.asReversed() else it }
    val start = (page - 1) * SERIES_PAGE_SIZE
    return SeriesCatalogPage(titles.drop(start).take(SERIES_PAGE_SIZE), start + SERIES_PAGE_SIZE < titles.size)
}

internal fun seriesFilter(series: String): String = "\"series:$series\""
