package eu.kanade.tachiyomi.extension.all.lanraragi

import eu.kanade.tachiyomi.source.model.FilterList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltersTest {
    @Test
    fun `filter panel includes every advanced control before categories load`() {
        assertEquals(
            listOf(
                "Category", "Sort order", "New Archives only", "Untagged Archives only",
                "Hide Completed", "Group by Tankoubon", "Starting page", "Sort by (namespace)",
            ),
            sourceFilters(emptyList()).map { it.name },
        )
        assertFalse(sourceFilters(emptyList()).hasArchiveFilters())
    }

    @Test
    fun `categories use their server IDs after sorting pinned entries first`() {
        val category = CategorySelect(
            listOf(Category("ordinary-id", "Alpha", 0), Category("pinned-id", "Zeta", 1)),
        ).apply { state = 1 }
        val filters = FilterList(category)

        assertTrue(filters.hasArchiveFilters())
        assertEquals("pinned-id", filters.archiveSearchUrl(SERVER, "", 0).queryParameter("category"))
    }

    @Test
    fun `search preserves LANraragi tag and exclusion syntax`() {
        val query = "\"artist:Example Name\",official,-uncensored"
        val url = FilterList().archiveSearchUrl(SERVER, query, 0)

        assertEquals(query, url.queryParameter("filter"))
        assertEquals("/library/api/search", url.encodedPath)
        assertEquals("false", url.queryParameter("groupby_tanks"))
    }

    @Test
    fun `each checkbox activates and reaches the archive search`() {
        val cases = listOf(
            NewArchivesOnly().apply { state = true } to "newonly",
            UntaggedArchivesOnly().apply { state = true } to "untaggedonly",
            HideCompleted().apply { state = true } to "hidecompleted",
            GroupByTanks().apply { state = true } to "groupby_tanks",
        )
        cases.forEach { (filter, parameter) ->
            val filters = FilterList(filter)
            assertTrue(parameter, filters.hasArchiveFilters())
            assertEquals("true", filters.archiveSearchUrl(SERVER, "", 0).queryParameter(parameter))
        }
    }

    @Test
    fun `unchecked archive filters do not constrain requests`() {
        val url = sourceFilters(emptyList()).archiveSearchUrl(SERVER, "", 0)

        assertNull(url.queryParameter("newonly"))
        assertNull(url.queryParameter("untaggedonly"))
        assertNull(url.queryParameter("hidecompleted"))
        assertNull(url.queryParameter("category"))
        assertNull(url.queryParameter("sortby"))
    }

    @Test
    fun `namespace order and raw archive offset are passed to the server`() {
        val filters = FilterList(
            SortByNamespace().apply { state = " date_added " },
            SortSelect().apply { state = 1 },
        )
        val url = filters.archiveSearchUrl(SERVER, "", 40)

        assertTrue(filters.hasArchiveFilters())
        assertEquals("date_added", url.queryParameter("sortby"))
        assertEquals("desc", url.queryParameter("order"))
        assertEquals("40", url.queryParameter("start"))
    }

    @Test
    fun `alphabetical ordering keeps the fast series index`() {
        assertFalse(FilterList(SortSelect()).hasArchiveFilters())
        assertFalse(FilterList(SortSelect().apply { state = 1 }).hasArchiveFilters())
    }

    @Test
    fun `random search is bounded and keeps category and checkbox filters`() {
        val filters = FilterList(
            SortSelect().apply { state = 2 },
            NewArchivesOnly().apply { state = true },
            SortByNamespace().apply { state = "date_added" },
        )
        val url = filters.archiveSearchUrl(SERVER, "official", 40)

        assertTrue(filters.hasArchiveFilters())
        assertTrue(filters.isRandom())
        assertEquals("/library/api/search/random", url.encodedPath)
        assertEquals("20", url.queryParameter("count"))
        assertEquals("official", url.queryParameter("filter"))
        assertEquals("true", url.queryParameter("newonly"))
        assertNull(url.queryParameter("sortby"))
        assertNull(url.queryParameter("order"))
        assertNull(url.queryParameter("start"))
    }

    @Test
    fun `starting page defaults to one and supports skipping pages`() {
        assertEquals(1, FilterList().startingPage())
        assertEquals(3, FilterList(StartingPage().apply { state = "3" }).startingPage())
        assertEquals(1, FilterList(StartingPage().apply { state = "0" }).startingPage())
        assertEquals(1, FilterList(StartingPage().apply { state = "-1" }).startingPage())
        assertEquals(1, FilterList(StartingPage().apply { state = "invalid" }).startingPage())
    }

    @Test
    fun `pagination uses raw matching archives rather than deduplicated series or library totals`() {
        val archives = (1..2).map {
            Archive(it.toString(), "Chapter $it", "series:Example Series", null, false, 1, null)
        }
        val result = ArchiveSearchResult(archives, 6, 200)

        assertTrue(result.hasNextPage(0))
        assertTrue(result.hasNextPage(2))
        assertFalse(result.hasNextPage(4))
        assertFalse(ArchiveSearchResult(emptyList(), 6, 200).hasNextPage(0))
        assertFalse(ArchiveSearchResult(archives, null, 2).hasNextPage(0))
    }

    companion object {
        private const val SERVER = "https://example.org/library"
    }
}
