package eu.kanade.tachiyomi.ui.player.utils

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.jsonMime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.IOException

class JimakuApi(
    private val apiKey: String,
    private val cacheDir: File,
    private val client: OkHttpClient,
) {
    private val json: Json by injectLazy()

    private suspend fun <T> request(
        url: String,
        onSuccess: (okhttp3.Response) -> T,
        defaultValue: T,
    ): T {
        var rateLimited = false
        var lastError: IOException? = null
        repeat(3) { attempt ->
            try {
                val response = withContext(Dispatchers.IO) {
                    client.newCall(
                        GET(
                            url,
                            headers = Headers.Builder().add("Authorization", apiKey).build(),
                        ),
                    ).execute()
                }
                when (response.code) {
                    200 -> return onSuccess(response)
                    401 -> throw JimakuAuthException("Jimaku API key is invalid")
                    429 -> {
                        rateLimited = true
                        delay(2_000L)
                    }
                    else -> {
                        logcat(LogPriority.WARN) { "Jimaku returned HTTP ${response.code}" }
                        return defaultValue
                    }
                }
            } catch (e: JimakuAuthException) {
                throw e
            } catch (e: IOException) {
                lastError = e
                logcat(LogPriority.WARN, e) { "Jimaku request failed on attempt ${attempt + 1}" }
            }
        }
        if (rateLimited) throw JimakuRateLimitException("Jimaku rate limit reached")
        throw JimakuNetworkException("Jimaku network request failed", lastError)
    }

    suspend fun searchByAniListId(id: Long): List<JimakuEntry> =
        request(
            "https://jimaku.cc/api/entries/search?anilist_id=$id",
            { json.decodeFromString(it.body.string()) },
            emptyList(),
        )

    suspend fun searchByName(name: String): List<JimakuEntry> =
        request(
            "https://jimaku.cc/api/entries/search?query=${java.net.URLEncoder.encode(name, "UTF-8")}",
            { json.decodeFromString(it.body.string()) },
            emptyList(),
        )

    suspend fun getAniListIdFromMal(idMal: Long): Long = withContext(Dispatchers.IO) {
        val query = "query { Media(idMal:$idMal,type: ANIME) { id } }"
        val response = runCatching {
            client.newCall(
                POST(
                    "https://graphql.anilist.co",
                    body = buildJsonObject { put("query", query) }.toString().toRequestBody(jsonMime),
                ),
            ).execute()
        }.getOrNull() ?: return@withContext 0L
        runCatching {
            json.parseToJsonElement(response.body.string())
                .jsonObject["data"]?.jsonObject?.get("Media")?.jsonObject?.get("id")
                ?.jsonPrimitive?.longOrNull ?: 0L
        }.getOrDefault(0L)
    }

    suspend fun getSubtitleFiles(entryId: Long, episode: Int): List<JimakuFile> {
        return request(
            "https://jimaku.cc/api/entries/$entryId/files?episode=$episode",
            { response ->
                json.decodeFromString<List<JimakuFile>>(response.body.string())
                    .filter { isSupportedSubtitleFile(it.name) }
            },
            emptyList(),
        )
    }

    suspend fun downloadSubtitleToCache(file: JimakuFile): File? {
        return request(
            file.url,
            { response ->
                cacheDir.mkdirs()
                File(cacheDir, file.name.replace(Regex("[^A-Za-z0-9._-]"), "_")).apply {
                    writeBytes(response.body.bytes())
                }
            },
            null,
        )
    }
}

class JimakuAuthException(message: String) : Exception(message)
class JimakuRateLimitException(message: String) : Exception(message)
class JimakuNetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
data class JimakuEntry(
    val id: Long,
    val name: String,
    @SerialName("english_name") val englishName: String? = null,
    @SerialName("japanese_name") val japaneseName: String? = null,
    @SerialName("anilist_id") val anilistId: Int? = null,
)

@Serializable
data class JimakuFile(
    val url: String,
    val name: String,
    val size: Long = 0,
    @SerialName("last_modified") val lastModified: String? = null,
)