package eu.kanade.tachiyomi.extension.all.lanraragi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesTest {
    @Test
    fun `series and chapter tags take precedence over filenames`() {
        val archive = archive("Yogurt Lady (Official)_c057", "series:Yogurt Lady, chapter:056, official")

        assertEquals("Yogurt Lady", archive.seriesTitle())
        assertEquals(56F, archive.chapterNumber())
        assertEquals("Official", archive.chapterEdition())
    }

    @Test
    fun `tagged archives do not need chapter suffixes`() {
        val archive = archive("An unrelated filename", "series:I Can Buy Anything, chapter:074, uncensored")

        assertEquals("I Can Buy Anything", archive.seriesTitle())
        assertEquals(74F, archive.chapterNumber())
        assertEquals("Uncensored", archive.chapterEdition())
    }

    @Test
    fun `editions share a series without losing either archive`() {
        val archives = listOf(
            archive("Yogurt Lady (Official)_c057", "series:Yogurt Lady, chapter:057, official"),
            archive("Yogurt Lady_c057", "series:Yogurt Lady, chapter:057, uncensored"),
        )
        val grouped = archives.groupBy { it.seriesTitle() }

        assertEquals(setOf("Yogurt Lady"), grouped.keys)
        assertEquals(2, grouped.getValue("Yogurt Lady").size)
        assertEquals(listOf("Official", "Uncensored"), archives.map { it.chapterEdition() })
    }

    @Test
    fun `chapter tags sort numerically including decimals`() {
        val archives = listOf("010", "002.5", "002").map {
            archive("Filename", "series:Series, chapter:$it")
        }

        assertEquals(listOf(2F, 2.5F, 10F), archives.sortedBy { it.chapterNumber() }.map { it.chapterNumber() })
    }

    @Test
    fun `tag namespaces and edition labels ignore case and whitespace`() {
        val archive = archive("Filename", " Series : Yogurt Lady , CHAPTER : 057 , OFFICIAL ")

        assertEquals("Yogurt Lady", archive.seriesTitle())
        assertEquals(57F, archive.chapterNumber())
        assertEquals("Official", archive.chapterEdition())
    }

    @Test
    fun `missing tags retain filename grouping`() {
        listOf("Series_c001", "Series-c001.cbz", "Series ch. 1", "Series Chapter 1.pdf").forEach { title ->
            val archive = archive(title)

            assertEquals("Series", archive.seriesTitle())
            assertEquals(1F, archive.chapterNumber())
        }
    }

    @Test
    fun `missing chapter tag falls back to filename`() {
        val archive = archive("Yogurt Lady (Official)_c057", "series:Yogurt Lady, official")

        assertEquals("Yogurt Lady", archive.seriesTitle())
        assertEquals(57F, archive.chapterNumber())
    }

    @Test
    fun `invalid chapter tag falls back to filename`() {
        assertEquals(57F, archive("Series_c057", "chapter:special").chapterNumber())
    }

    @Test
    fun `series without a chapter number can still group`() {
        val archive = archive("Special", "series:Yogurt Lady")

        assertEquals("Yogurt Lady", archive.seriesTitle())
        assertNull(archive.chapterNumber())
    }

    @Test
    fun `standalone titles and bare numbers do not become series`() {
        listOf("Standalone", "Standalone 2026").forEach { title ->
            val archive = archive(title)

            assertNull(archive.seriesTitle())
            assertNull(archive.chapterNumber())
            assertNull(archive.chapterEdition())
        }
    }

    @Test
    fun `tankoubons keep their existing identity`() {
        val archive = archive("Collection_c001", "series:Yogurt Lady", "TANK_1234567890")

        assertNull(archive.seriesTitle())
    }

    @Test
    fun `series or group values are not edition labels`() {
        assertNull(archive("Filename", "series:Official, group:uncensored").chapterEdition())
    }

    private fun archive(title: String, tags: String? = null, id: String = "0".repeat(40)) = Archive(
        arcid = id,
        title = title,
        tags = tags,
        summary = null,
        isnew = false,
        pagecount = 20,
        toc = null,
    )
}
