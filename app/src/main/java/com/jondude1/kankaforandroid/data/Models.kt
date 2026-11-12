package com.jondude1.kankaforandroid.data

import org.json.JSONArray
import org.json.JSONObject

/** One Kanka entity type: API endpoint, search "type" code, and the extra text fields we expose in the editor. */
data class EntityKind(
    val endpoint: String,
    val singular: String,
    val label: String,
    val glyph: String,
    val extraFields: List<Pair<String, String>> = emptyList(), // json key to label
)

object Kinds {
    val all = listOf(
        EntityKind("characters", "character", "Characters", "👤", listOf("title" to "Title", "age" to "Age", "sex" to "Sex", "pronouns" to "Pronouns")),
        EntityKind("locations", "location", "Locations", "🗺️"),
        EntityKind("families", "family", "Families", "👪"),
        EntityKind("organisations", "organisation", "Organisations", "🏛️"),
        EntityKind("items", "item", "Items", "🗡️", listOf("price" to "Price", "size" to "Size")),
        EntityKind("notes", "note", "Notes", "📝"),
        EntityKind("events", "event", "Events", "📅", listOf("date" to "Date")),
        EntityKind("calendars", "calendar", "Calendars", "🗓️"),
        EntityKind("timelines", "timeline", "Timelines", "⏳"),
        EntityKind("creatures", "creature", "Creatures", "🐉"),
        EntityKind("races", "race", "Races", "🧝"),
        EntityKind("quests", "quest", "Quests", "🎯"),
        EntityKind("maps", "map", "Maps", "🧭"),
        EntityKind("journals", "journal", "Journals", "📖", listOf("date" to "Date")),
        EntityKind("abilities", "ability", "Abilities", "✨"),
        EntityKind("tags", "tag", "Tags", "🏷️", listOf("colour" to "Colour")),
    )
    fun byEndpoint(e: String) = all.first { it.endpoint == e }
    fun bySingular(s: String) = all.firstOrNull { it.singular == s }
}

data class Campaign(val id: Long, val name: String, val entry: String?)

data class EntitySummary(
    val id: Long,
    val entityId: Long,
    val name: String,
    val type: String?,   // subtype label on list endpoints, entity type code on search results
    val isPrivate: Boolean,
    val kind: EntityKind? = null,
)

data class Page<T>(val items: List<T>, val page: Int, val lastPage: Int)

data class Post(val id: Long, val name: String, val entry: String, val entryHtml: String, val visibilityId: Int)

fun JSONObject.str(key: String): String? =
    if (isNull(key) || !has(key)) null else opt(key)?.toString()?.takeIf { it != "null" }

inline fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> =
    (0 until length()).map { f(getJSONObject(it)) }
