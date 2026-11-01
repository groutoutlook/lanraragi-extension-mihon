package eu.kanade.tachiyomi.extension.all.lanraragi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    @Test
    fun `browse exposes only series tags`() {
        val tags = listOf(
            DatabaseTag("Example Series", "series"),
            DatabaseTag("001", "chapter"),
            DatabaseTag("Name", "artist"),
            DatabaseTag("official"),
        )

        assertEquals(listOf("Example Series"), seriesCatalogPage(tags, 1, "", false).titles)
    }

    @Test
    fun `twenty series load at a time without overlapping pages`() {
        val tags = (1..45).map { DatabaseTag("Series ${it.toString().padStart(2, '0')}", "series") }
        val first = seriesCatalogPage(tags, 1, "", false)
        val second = seriesCatalogPage(tags, 2, "", false)
        val third = seriesCatalogPage(tags, 3, "", false)

        assertEquals(20, first.titles.size)
        assertEquals(20, second.titles.size)
        assertEquals(5, third.titles.size)
        assertTrue(first.hasNextPage)
        assertTrue(second.hasNextPage)
        assertFalse(third.hasNextPage)
        assertEquals(45, (first.titles + second.titles + third.titles).distinct().size)
    }

    @Test
    fun `an exact last page does not request another page`() {
        val tags = (1..40).map { DatabaseTag("Series $it", "series") }

        assertFalse(seriesCatalogPage(tags, 2, "", false).hasNextPage)
        assertTrue(seriesCatalogPage(tags, 3, "", false).titles.isEmpty())
    }

    @Test
    fun `series names are deduplicated and searched ignoring case`() {
        val tags = listOf(
            DatabaseTag(" Example Series ", "SERIES"),
            DatabaseTag("example series", "series"),
            DatabaseTag("Another Series", "series"),
            DatabaseTag(" ", "series"),
        )
        val page = seriesCatalogPage(tags, 1, " EXAMPLE ", false)

        assertEquals(listOf("Example Series"), page.titles)
        assertFalse(page.hasNextPage)
    }

    @Test
    fun `series sorting is stable in either direction`() {
        val tags = listOf(DatabaseTag("Zeta", "series"), DatabaseTag("alpha", "series"))

        assertEquals(listOf("alpha", "Zeta"), seriesCatalogPage(tags, 1, "", false).titles)
        assertEquals(listOf("Zeta", "alpha"), seriesCatalogPage(tags, 1, "", true).titles)
    }

    @Test
    fun `series filters quote punctuation and match the complete tag`() {
        assertEquals("\"series:Example, Series\"", seriesFilter("Example, Series"))
    }
}
