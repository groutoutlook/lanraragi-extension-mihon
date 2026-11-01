package eu.kanade.tachiyomi.extension.all.lanraragi

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal class CategorySelect(categories: List<Category>) : UriPartFilter(
    "Category",
    listOf("" to "").plus(
        categories.sortedWith(compareByDescending<Category> { it.pinned }.thenBy { it.name }).map {
            it.id to "${if (it.pinned == 1) "📌 " else ""}${it.name.orEmpty()}"
        },
    ).toTypedArray(),
)

internal class SortSelect : UriPartFilter("Sort order", arrayOf("asc" to "Ascending", "desc" to "Descending", "random" to "Random"))
internal class NewArchivesOnly : Filter.CheckBox("New Archives only", false)
internal class UntaggedArchivesOnly : Filter.CheckBox("Untagged Archives only", false)
internal class HideCompleted : Filter.CheckBox("Hide Completed", false)
internal class GroupByTanks : Filter.CheckBox("Group by Tankoubon", false)
internal class StartingPage : Filter.Text("Starting page", "")
internal class SortByNamespace : Filter.Text("Sort by (namespace)", "")

internal open class UriPartFilter(displayName: String, private val choices: Array<Pair<String, String>>) :
    Filter.Select<String>(displayName, choices.map { it.second }.toTypedArray()) {
    fun toUriPart(): String = choices[state].first
}

internal fun sourceFilters(categories: List<Category>): FilterList = FilterList(
    CategorySelect(categories),
    SortSelect(),
    NewArchivesOnly(),
    UntaggedArchivesOnly(),
    HideCompleted(),
    GroupByTanks(),
    StartingPage(),
    SortByNamespace(),
)

internal fun FilterList.hasArchiveFilters(): Boolean = any {
    when (it) {
        is CategorySelect -> it.state > 0
        is SortSelect -> it.toUriPart() == "random"
        is SortByNamespace -> it.state.isNotBlank()
        is Filter.CheckBox -> it.state
        else -> false
    }
}

internal fun FilterList.startingPage(): Int = firstInstanceOrNull<StartingPage>()?.state?.toIntOrNull()?.coerceAtLeast(1) ?: 1

internal fun FilterList.isRandom(): Boolean = firstInstanceOrNull<SortSelect>()?.toUriPart() == "random"

internal fun FilterList.archiveSearchUrl(baseUrl: String, query: String, start: Int): HttpUrl {
    val random = isRandom()
    val url = "$baseUrl/api/search${if (random) "/random" else ""}".toHttpUrl().newBuilder()
        .addQueryParameter("groupby_tanks", (firstInstanceOrNull<GroupByTanks>()?.state ?: false).toString())
    if (query.isNotBlank()) url.addQueryParameter("filter", query)
    forEach { filter ->
        when (filter) {
            is CategorySelect -> if (filter.state > 0) url.addQueryParameter("category", filter.toUriPart())
            is NewArchivesOnly -> if (filter.state) url.addQueryParameter("newonly", "true")
            is UntaggedArchivesOnly -> if (filter.state) url.addQueryParameter("untaggedonly", "true")
            is HideCompleted -> if (filter.state) url.addQueryParameter("hidecompleted", "true")
            is SortByNamespace -> if (!random && filter.state.isNotBlank()) url.addQueryParameter("sortby", filter.state.trim())
            else -> Unit
        }
    }
    if (random) {
        url.addQueryParameter("count", SERIES_PAGE_SIZE.toString())
    } else {
        url.addQueryParameter("order", firstInstanceOrNull<SortSelect>()?.toUriPart() ?: "asc")
        url.addQueryParameter("start", start.toString())
    }
    return url.build()
}

internal fun ArchiveSearchResult.hasNextPage(start: Int): Boolean = data.isNotEmpty() && start + data.size < (recordsFiltered ?: recordsTotal)
