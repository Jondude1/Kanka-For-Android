package com.jondude1.kankaforandroid.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : Exception(message)

/** Kanka REST client. `url` is the site root of the self-hosted instance (the /api/1.0 suffix is added). */
class KankaApi(url: String, private val token: String) {
    private val base: String = normalize(url)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        fun normalize(raw: String): String {
            var u = raw.trim().trimEnd('/')
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
            if (!Regex("/api/\\d").containsMatchIn(u)) u += "/api/1.0"
            return u
        }
    }

    private suspend fun call(
        method: String,
        segments: List<String>,
        query: Map<String, String> = emptyMap(),
        body: JSONObject? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val ub = base.toHttpUrl().newBuilder()
        segments.forEach { ub.addPathSegment(it) }
        query.forEach { (k, v) -> ub.addQueryParameter(k, v) }
        val rb = body?.toString()?.toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(ub.build())
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .method(method, rb ?: if (method == "POST" || method == "PATCH" || method == "PUT") "".toRequestBody() else null)
            .build()
        repeat(3) { attempt ->
            http.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (res.code == 429 && attempt < 2) {
                    val wait = res.header("Retry-After")?.toLongOrNull() ?: (3L * (attempt + 1))
                    delay(wait.coerceIn(1, 30) * 1000)
                    return@repeat
                }
                if (!res.isSuccessful) {
                    val msg = runCatching { JSONObject(text).optString("message") }.getOrNull()?.takeIf { it.isNotBlank() }
                    throw ApiException(res.code, when (res.code) {
                        401 -> "Token rejected (401). Check your API token."
                        403 -> "Forbidden (403). Your Kanka user lacks permission."
                        404 -> "Not found (404)."
                        429 -> "Rate limited by Kanka (429). Try again in a minute."
                        else -> msg ?: "HTTP ${res.code}"
                    })
                }
                return@withContext if (text.isBlank()) JSONObject() else JSONObject(text)
            }
        }
        throw ApiException(429, "Rate limited by Kanka (429).")
    }

    suspend fun campaigns(): List<Campaign> =
        call("GET", listOf("campaigns")).getJSONArray("data").mapObjects {
            Campaign(it.getLong("id"), it.optString("name"), it.str("entry"))
        }

    private fun summary(o: JSONObject, kind: EntityKind?) = EntitySummary(
        id = o.getLong("id"),
        entityId = o.optLong("entity_id", 0),
        name = o.optString("name"),
        type = o.str("type"),
        isPrivate = o.optBoolean("is_private", false),
        kind = kind,
    )

    suspend fun list(cid: Long, kind: EntityKind, page: Int, name: String?): Page<EntitySummary> {
        val q = buildMap {
            put("page", page.toString())
            if (!name.isNullOrBlank()) put("name", name)
        }
        val r = call("GET", listOf("campaigns", "$cid", kind.endpoint), q)
        val meta = r.optJSONObject("meta")
        return Page(
            r.getJSONArray("data").mapObjects { summary(it, kind) },
            meta?.optInt("current_page", page) ?: page,
            meta?.optInt("last_page", page) ?: page,
        )
    }

    suspend fun search(cid: Long, term: String): List<EntitySummary> =
        call("GET", listOf("campaigns", "$cid", "search", term)).getJSONArray("data").mapObjects {
            summary(it, Kinds.bySingular(it.optString("type")))
        }

    /** Recently edited entities. Entries carry entity_type + child_id so we can open the typed record. */
    suspend fun recent(cid: Long): List<EntitySummary> =
        call("GET", listOf("campaigns", "$cid", "entities", "recent"), mapOf("amount" to "10"))
            .getJSONArray("data").mapObjects {
                val kind = Kinds.bySingular(it.optString("entity_type"))
                EntitySummary(
                    id = it.optLong("child_id", 0), entityId = it.getLong("id"), name = it.optString("name"),
                    type = it.str("type"), isPrivate = it.optBoolean("is_private", false), kind = kind,
                )
            }.filter { it.kind != null && it.id != 0L }

    suspend fun get(cid: Long, kind: EntityKind, id: Long): JSONObject =
        call("GET", listOf("campaigns", "$cid", kind.endpoint, "$id")).getJSONObject("data")

    /** Create (id == null) or partially update. Returns the saved record. */
    suspend fun save(cid: Long, kind: EntityKind, id: Long?, fields: JSONObject): JSONObject =
        if (id == null) call("POST", listOf("campaigns", "$cid", kind.endpoint), body = fields).getJSONObject("data")
        else call("PATCH", listOf("campaigns", "$cid", kind.endpoint, "$id"), body = fields).getJSONObject("data")

    suspend fun delete(cid: Long, kind: EntityKind, id: Long) {
        call("DELETE", listOf("campaigns", "$cid", kind.endpoint, "$id"))
    }

    private fun post(o: JSONObject) = Post(
        id = o.getLong("id"), name = o.optString("name"),
        entry = o.str("entry").orEmpty(),
        entryHtml = o.str("entry_parsed") ?: o.str("entry").orEmpty(),
        visibilityId = o.optInt("visibility_id", 1),
    )

    suspend fun posts(cid: Long, entityId: Long): List<Post> =
        call("GET", listOf("campaigns", "$cid", "entities", "$entityId", "posts")).getJSONArray("data").mapObjects(::post)

    suspend fun savePost(cid: Long, entityId: Long, postId: Long?, name: String, entry: String, visibilityId: Int): Post {
        val body = JSONObject().put("name", name).put("entry", entry).put("visibility_id", visibilityId).put("entity_id", entityId)
        val seg = listOf("campaigns", "$cid", "entities", "$entityId", "posts")
        return post(
            if (postId == null) call("POST", seg, body = body).getJSONObject("data")
            else call("PATCH", seg + "$postId", body = body).getJSONObject("data")
        )
    }

    suspend fun deletePost(cid: Long, entityId: Long, postId: Long) {
        call("DELETE", listOf("campaigns", "$cid", "entities", "$entityId", "posts", "$postId"))
    }
}
