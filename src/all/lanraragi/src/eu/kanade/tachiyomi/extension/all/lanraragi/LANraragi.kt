package eu.kanade.tachiyomi.extension.all.lanraragi

import android.content.SharedPreferences
import android.net.Uri
import android.text.InputType
import android.util.Base64
import android.widget.Toast
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.DEFAULT_CACHE_CONTROL
import keiyoushi.network.Fetch
import keiyoushi.network.call
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.Dns
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URL

@Source
abstract class LANraragi :
    KeiSource(),
    ConfigurableSource,
    UnmeteredSource {
    private val apiKey by lazy { getPrefAPIKey() }

    private val artistNamespaces by lazy {
        buildNamespaceCandidates(ARTIST_NAMESPACE, getPrefArtistNamespaces())
    }

    private val authorNamespaces by lazy {
        buildNamespaceCandidates(AUTHOR_NAMESPACE, getPrefAuthorNamespaces())
    }

    private var randomArchiveID: String = ""

    override fun OkHttpClient.Builder.configureClient() = dns(Dns.SYSTEM)
        .addInterceptor { chain ->
            var request = chain.request()
            val serverPath = baseUrl.toHttpUrl().encodedPath.trimEnd('/')
            if (request.url.encodedPath == "$serverPath$SERIES_COVER_PATH") {
                val series = request.url.queryParameter("title")!!
                val searchUrl = "$baseUrl/api/search/random".toHttpUrl().newBuilder()
                    .addQueryParameter("filter", seriesFilter(series))
                    .addQueryParameter("groupby_tanks", "false")
                    .addQueryParameter("count", "1")
                    .build()
                val search = chain.proceed(request.newBuilder().url(searchUrl).fetch(Fetch.XHR).build())
                if (!search.isSuccessful) {
                    val code = search.code
                    search.close()
                    throw IOException("Could not load series cover: HTTP $code")
                }
                val archive = search.parseAs<ArchiveSearchResult>().data.firstOrNull()
                    ?: throw IOException("No cover available for this series.")
                request = request.newBuilder().url(getThumbnailUri(archive.arcid)).build()
            }
            val response = chain.proceed(request)
            if (response.code == 401) {
                response.close()
                throw IOException("If the server is in No-Fun Mode make sure the extension's API Key is correct.")
            }
            response
        }
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val statsPath = "${baseUrl.toHttpUrl().encodedPath.trimEnd('/')}/api/database/stats"
            if (response.isSuccessful && chain.request().url.encodedPath == statsPath) {
                response.newBuilder().header("Cache-Control", "private, max-age=300").build()
            } else {
                response
            }
        }

    override fun Headers.Builder.configureHeaders() = apply {
        if (apiKey.isNotEmpty()) {
            val apiKey64 = Base64.encodeToString(apiKey.toByteArray(), Base64.NO_WRAP)
            add("Authorization", "Bearer $apiKey64")
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    private var latestPageSize = 0

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val start = if (page == 1) 0 else (page - 1) * latestPageSize
        val url = "$baseUrl/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("filter", "series:")
            .addQueryParameter("groupby_tanks", "false")
            .addQueryParameter("sortby", "date_added")
            .addQueryParameter("order", "desc")
            .addQueryParameter("start", start.toString())
            .build()
        val result = client.get(url, Fetch.XHR, CacheControl.FORCE_NETWORK).parseAs<ArchiveSearchResult>()
        if (page == 1) latestPageSize = result.data.size
        val series = result.data.map { archiveToSManga(it) }.distinctBy { it.url }
        return MangasPage(series, result.data.isNotEmpty() && start + result.data.size < (result.recordsFiltered ?: result.recordsTotal))
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val cacheControl = if (page == 1 && query.isBlank()) CacheControl.FORCE_NETWORK else DEFAULT_CACHE_CONTROL
        val tags = client.get("$baseUrl/api/database/stats", Fetch.XHR, cacheControl).parseAs<List<DatabaseTag>>()
        val catalog = seriesCatalogPage(tags, page, query, filters.firstInstanceOrNull<SeriesSort>()?.state == 1)
        val series = catalog.titles.map { name ->
            SManga.create().apply {
                url = seriesUrl(name)
                title = name
                thumbnail_url = "$baseUrl$SERIES_COVER_PATH".toHttpUrl().newBuilder()
                    .addQueryParameter("title", name)
                    .build()
                    .toString()
                status = SManga.UNKNOWN
            }
        }
        return MangasPage(series, catalog.hasNextPage)
    }

    private fun archiveToSManga(archive: Archive) = SManga.create().apply {
        val series = archive.seriesTitle()
        val (artistName, authorName) = parseMangaCredits(
            archive.tags,
            artistNamespaces,
            authorNamespaces,
        )

        url = series?.let { seriesUrl(it) } ?: "/reader?id=${archive.arcid}"
        title = series ?: archive.title
        description = if (archive.summary.isNullOrBlank()) archive.title else archive.summary
        thumbnail_url = getThumbnailUri(archive.arcid)
        genre = archive.tags?.replace(",", ", ")
        artist = artistName
        author = authorName
        status = if (series == null) SManga.COMPLETED else SManga.UNKNOWN
    }

    private suspend fun fetchSeries(series: String): List<Archive> {
        val url = "$baseUrl/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("filter", seriesFilter(series))
            .addQueryParameter("groupby_tanks", "false")
            .addQueryParameter("start", "-1")
            .build()
        return client.get(url, Fetch.XHR, CacheControl.FORCE_NETWORK).parseAs<ArchiveSearchResult>().data
            .filter { it.seriesTitle()?.equals(series, ignoreCase = true) == true }
            .sortedBy { it.chapterNumber() ?: Float.MAX_VALUE }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val server = baseUrl.toHttpUrl()
        if (url.host != server.host || url.port != server.port) return null
        val id = url.queryParameter("id") ?: return null
        if (!REGEX_ID_FROM_URL.matches(id)) return null
        val manga = SManga.create().apply {
            this.url = "/reader?id=$id"
            title = id
        }
        return fetchMangaUpdate(manga, emptyList(), true, true).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (manga.url.startsWith("/series?")) {
            val series = Uri.parse(manga.url).getQueryParameter("title")!!
            val archives = fetchSeries(series)
            val updatedManga = archives.firstOrNull()?.let { archiveToSManga(it) } ?: manga
            updatedManga.url = manga.url
            return SMangaUpdate(updatedManga, parseChapters(archives))
        }

        val id = if (manga.url.startsWith("/api/search/random")) randomArchiveID else getIDFromURL(manga.url)

        val response = client.get(apiTypeByID(id).toString(), Fetch.XHR)

        val (archive, archives) = if (!id.startsWith("TANK_")) {
            val archive = response.parseAs<Archive>()
            val series = archive.seriesTitle()
            archive to if (series != null) fetchSeries(series) else listOf(archive)
        } else {
            val tank = response.parseAs<Tankoubon>()

            // The separators are not the default ", " to merge properly when combining across multiple archives: ",tag:x" vs ", tag:x"
            val tags = tank.result?.full_data?.mapNotNull { it.tags }?.joinToString(",")?.split(",")?.distinct()?.sorted()?.joinToString(",")

            Archive(
                arcid = tank.result!!.id,
                isnew = false,
                tags = tags,
                summary = tank.result.summary,
                title = tank.result.name!!,
                toc = emptyList(),
                pagecount = 0,
            ) to tank.result.full_data
        }

        val updatedManga = archiveToSManga(archive).apply { url = manga.url }

        if (manga.url.startsWith("/api/search/random")) {
            randomArchiveID = getRandomID(Uri.parse(manga.url).encodedQuery.toString())
            updatedManga.description = "Refresh for a new random entry.\n\n${updatedManga.description}"
        }

        return SMangaUpdate(updatedManga, parseChapters(archives))
    }

    override fun getMangaUrl(manga: SManga): String {
        val id = getIDFromURL(manga.thumbnail_url ?: manga.url)
        val defaultUrl = if (id.isEmpty()) baseUrl else "$baseUrl/reader?id=$id"
        val namespace = preferences.getString(URL_TAG_PREFIX_KEY, URL_TAG_PREFIX_DEFAULT)

        if (namespace.isNullOrEmpty()) {
            return defaultUrl
        }

        val tag = manga.genre?.split(", ")?.find { it.startsWith(namespace) }
        return tag?.substringAfter(namespace) ?: defaultUrl
    }

    private suspend fun parseChapters(archives: List<Archive>?): List<SChapter> {
        val chapters = mutableListOf<SChapter>()

        // Legacy extension-exclusive behavior to remove isnew on single archives when viewing
        val prefClearNew = preferences.getBoolean(CLEAR_NEW_KEY, CLEAR_NEW_DEFAULT)
        if (prefClearNew && archives?.size == 1 && archives[0].isnew) {
            val clearNew = Request.Builder()
                .url("$baseUrl/api/archives/${archives[0].arcid}/isnew")
                .headers(headers)
                .delete()
                .build()

            client.call(clearNew.newBuilder().fetch(Fetch.XHR).build(), ensureSuccess = false).close()
        }

        var baseChapter = 0F

        // Supports single, single+ToC, tank, tank+ToC...
        archives?.forEach { arc ->
            baseChapter = arc.chapterNumber() ?: (baseChapter + 1F)

            val baseFiles = getApiUriBuilder("/api/archives/${arc.arcid}/files").build().toString()
            val date = 1000 * (getNSTag(arc.tags, "date_added")?.first()?.toLong() ?: 0)

            if (arc.toc?.isEmpty() == false) {
                var lastStart = 0

                val toc = buildList {
                    if (arc.toc.first().page > 1) add(ArchiveTOCEntry(arc.title, 1)) // Starting gap filler
                    addAll(arc.toc)
                }

                toc.forEachIndexed { i, entry ->
                    val nextPage = if (i + 1 < toc.size) toc[i + 1].page - 1 else arc.pagecount

                    chapters.add(
                        SChapter.create().apply {
                            url = "$baseFiles#$lastStart-$nextPage"
                            chapter_number = baseChapter + "0.${i + 1}".toFloat()
                            name = "$chapter_number - ${entry.name}"
                            scanlator = arc.chapterEdition()
                            date_upload = date
                        },
                    )

                    lastStart = nextPage
                }
            } else {
                chapters.add(
                    SChapter.create().apply {
                        url = baseFiles
                        chapter_number = baseChapter
                        name = if (arc.chapterNumber() != null) {
                            "Chapter ${chapter_number.toString().removeSuffix(".0")}"
                        } else if (archives.size == 1) {
                            "Chapter"
                        } else {
                            "${chapter_number.toInt()} - ${arc.title}"
                        }
                        scanlator = arc.chapterEdition()
                        date_upload = date
                    },
                )
            }
        }
        chapters.reverse() // For tanks orders them in "latest first"

        return chapters
    }

    override fun getChapterUrl(chapter: SChapter): String = chapter.url

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = chapter.url.toHttpUrl()
        val archivePage = client.get(url, Fetch.XHR).parseAs<ArchivePage>()
        val range = url.fragment?.split("-")?.map { it.toInt() } ?: listOf(0, archivePage.pages.size)

        return archivePage.pages.subList(range.first(), range.last()).mapIndexed { index, url ->
            var newUrl = url
            val subPath = URL(baseUrl).path
            if (!subPath.isNullOrEmpty()) {
                newUrl = newUrl.replaceFirst(subPath, "")
            }
            val uri = Uri.parse("${baseUrl}${newUrl.trimStart('.')}")
            Page(index, uri.toString(), uri.toString(), uri)
        }
    }

    private class SeriesSort : Filter.Select<String>("Series order", arrayOf("A–Z", "Z–A"))

    override fun getFilterList(data: JsonElement?) = FilterList(SeriesSort())

    // Preferences
    internal val preferences: SharedPreferences by getPreferencesLazy()

    private fun getPrefAPIKey(): String = preferences.getString(APIKEY_KEY, "")!!
    private fun getPrefArtistNamespaces(): String = preferences.getString(ARTIST_NAMESPACES_KEY, ADDITIONAL_NAMESPACES_DEFAULT)!!
    private fun getPrefAuthorNamespaces(): String = preferences.getString(AUTHOR_NAMESPACES_KEY, ADDITIONAL_NAMESPACES_DEFAULT)!!

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        screen.addPreference(screen.editTextPreference(APIKEY_KEY, "API Key", "", "Required if No-Fun Mode is enabled.", true))
        screen.addPreference(screen.checkBoxPreference(CLEAR_NEW_KEY, "Clear New status", CLEAR_NEW_DEFAULT, "Clear an entry's New status when its details are viewed."))
        screen.addPreference(
            screen.editTextPreference(
                ARTIST_NAMESPACES_KEY,
                "Artist namespaces",
                ADDITIONAL_NAMESPACES_DEFAULT,
                "The built-in \"artist\" namespace is always used. Add other namespace names separated by commas, e.g. 艺术家, 画家.",
                inputType = InputType.TYPE_CLASS_TEXT,
            ).apply {
                dialogMessage = "Enter additional namespace names without the trailing colon, separated by commas."
            },
        )
        screen.addPreference(
            screen.editTextPreference(
                AUTHOR_NAMESPACES_KEY,
                "Author namespaces (group)",
                ADDITIONAL_NAMESPACES_DEFAULT,
                "The built-in \"group\" namespace is always used. Add other namespace names separated by commas, e.g. 团队, 社团.",
                inputType = InputType.TYPE_CLASS_TEXT,
            ).apply {
                dialogMessage = "Enter additional namespace names without the trailing colon, separated by commas."
            },
        )
        screen.addPreference(screen.editTextPreference(URL_TAG_PREFIX_KEY, "Set tag prefix to get WebView URL", URL_TAG_PREFIX_DEFAULT, "Example: 'source:' will try to get the URL from the first tag starting with 'source:' and it will open it in the WebView. Leave empty for the default behavior."))
    }

    private fun androidx.preference.PreferenceScreen.checkBoxPreference(key: String, title: String, default: Boolean, summary: String = ""): androidx.preference.CheckBoxPreference = androidx.preference.CheckBoxPreference(context).apply {
        this.key = key
        this.title = title
        this.summary = summary
        setDefaultValue(default)

        setOnPreferenceChangeListener { _, newValue ->
            preferences.edit().putBoolean(this.key, newValue as Boolean).commit()
        }
    }

    private fun androidx.preference.PreferenceScreen.editTextPreference(
        key: String,
        title: String,
        default: String,
        summary: String,
        isPassword: Boolean = false,
        refreshSummary: Boolean = false,
        inputType: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
    ): androidx.preference.EditTextPreference = androidx.preference.EditTextPreference(context).apply {
        this.key = key
        this.title = title
        this.summary = summary
        this.setDefaultValue(default)
        setOnBindEditTextListener { it.inputType = inputType }

        if (isPassword) {
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }

        setOnPreferenceChangeListener { _, newValue ->
            try {
                val newString = newValue.toString()
                val res = preferences.edit().putString(this.key, newString).commit()

                if (refreshSummary) {
                    this.apply {
                        this.summary = newValue as String
                    }
                }

                Toast.makeText(context, "Restart app to apply new setting.", Toast.LENGTH_LONG).show()
                res
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    // Helper
    private fun apiTypeByID(id: String): Uri = getApiUriBuilder(
        if (id.startsWith("TANK_")) {
            "/api/tankoubons/$id/full"
        } else {
            "/api/archives/$id/metadata"
        },
    ).build()

    private suspend fun getRandomID(query: String): String {
        val searchRandom = client.get("$baseUrl/api/search/random?count=1&$query", headers, Fetch.XHR, CacheControl.FORCE_NETWORK)
        val result = searchRandom.parseAs<ArchiveSearchResult>() // Intermittent empty data[] on parse, but not from manual API testing
        return result.data.firstOrNull()?.arcid ?: randomArchiveID
    }

    private fun getApiUriBuilder(path: String): Uri.Builder = Uri.parse("$baseUrl$path").buildUpon()

    private fun getThumbnailUri(id: String): String {
        val type = if (id.startsWith("TANK_")) "tankoubons" else "archives"
        val uri = getApiUriBuilder("/api/$type/$id/thumbnail")

        return uri.toString()
    }

    private fun getIDFromURL(url: String): String = REGEX_ID_FROM_URL.find(url)?.groupValues?.get(1) ?: ""

    private fun getNSTag(tags: String?, tag: String): List<String>? = namespaceTagValues(tags, listOf(tag))

    companion object {

        private val REGEX_ID_FROM_URL = Regex("""(?:/reader\?id=)?(TANK_[0-9]{10}|\w{40})(?:/thumbnail)?""")

        private const val APIKEY_KEY = "apiKey"
        private const val ARTIST_NAMESPACE = "artist"
        private const val AUTHOR_NAMESPACE = "group"
        private const val ARTIST_NAMESPACES_KEY = "artistNamespacePref"
        private const val AUTHOR_NAMESPACES_KEY = "authorNamespacePref"
        private const val ADDITIONAL_NAMESPACES_DEFAULT = ""

        private const val SERIES_COVER_PATH = "/api/series-cover"

        private const val CLEAR_NEW_KEY = "clearNew"
        private const val CLEAR_NEW_DEFAULT = true
        private const val URL_TAG_PREFIX_KEY = "urlTagPrefix"
        private const val URL_TAG_PREFIX_DEFAULT = ""
    }
}

private fun buildNamespaceCandidates(
    builtInNamespace: String,
    configuredNamespaces: String?,
): List<String> = buildList {
    add(builtInNamespace)

    configuredNamespaces
        ?.split(',', '，')
        ?.forEach { value ->
            val namespace = value.trim().trimEnd(':', '：').trim()
            if (namespace.isNotEmpty()) add(namespace)
        }
}.distinctBy { it.lowercase() }

internal fun namespaceTagValues(
    tags: String?,
    namespaces: Collection<String>,
): List<String>? {
    if (tags.isNullOrBlank() || namespaces.isEmpty()) return null

    val normalizedNamespaces = namespaces.map { it.lowercase() }.toSet()

    return tags.split(',')
        .mapNotNull { rawTag ->
            val tag = rawTag.trim()
            val separator = tag.indexOf(':')
            if (separator <= 0) return@mapNotNull null

            val namespace = tag.substring(0, separator).trim()
            if (namespace.lowercase() !in normalizedNamespaces) return@mapNotNull null

            tag.substring(separator + 1).trim().takeIf { it.isNotEmpty() }
        }
        .distinctBy { it.lowercase() }
        .takeIf { it.isNotEmpty() }
}

private fun parseMangaCredits(
    tags: String?,
    artistNamespaces: Collection<String>,
    authorNamespaces: Collection<String>,
): Pair<String?, String?> {
    val artist = namespaceTagValues(tags, artistNamespaces)?.joinToString()
    val author = namespaceTagValues(tags, authorNamespaces)?.joinToString() ?: artist
    return artist to author
}
