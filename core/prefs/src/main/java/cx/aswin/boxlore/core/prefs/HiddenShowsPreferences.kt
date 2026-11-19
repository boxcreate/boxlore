package cx.aswin.boxlore.core.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import cx.aswin.boxlore.core.model.HiddenShow
import cx.aswin.boxlore.core.model.HiddenShows
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The shows the listener hid everywhere ([HiddenShow]), newest first. A device preference: it is not
 * cleared on an account switch, and it travels in file and Android backups.
 *
 * Stored as two DataStore strings: `hidden_podcast_ids`, the ordered show IDs ([PreferenceIdList]),
 * which decide what is hidden, and `hidden_podcast_details`, each show's title, artwork and alias
 * IDs ([HiddenShowsCodec]). A show without details is still hidden under its own ID.
 */
class HiddenShowsPreferences internal constructor(private val dataStore: DataStore<Preferences>,) {
    private val preferences: Flow<Preferences> =
        dataStore.data.catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }

    /** The hidden shows, newest first. */
    val showsStream: Flow<List<HiddenShow>> = preferences.map(::read).distinctUntilChanged()

    /** Every ID that hides a show ([HiddenShows.matchIds]). */
    val matchIdsStream: Flow<Set<String>> = showsStream.map(HiddenShows::matchIds).distinctUntilChanged()

    suspend fun matchIds(): Set<String> = matchIdsStream.first()

    /** Hides [show], or refreshes its title, artwork and aliases when it is already hidden; it moves to the front. */
    suspend fun hide(show: HiddenShow) {
        val id = show.id.trim()
        if (id.isEmpty()) return
        dataStore.edit { prefs ->
            val current = read(prefs).filterNot { it.id == id }
            write(prefs, listOf(show.copy(id = id)) + current)
        }
    }

    /** Unhides every hidden show named by any of [showIds] (its own ID or an alias); returns the shows unhidden. */
    suspend fun unhide(showIds: Set<String>): List<HiddenShow> {
        if (showIds.isEmpty()) return emptyList()
        var removed = emptyList<HiddenShow>()
        dataStore.edit { prefs ->
            val (gone, kept) = read(prefs).partition { show -> show.matchIds.any { it in showIds } }
            removed = gone
            if (gone.isNotEmpty()) write(prefs, kept)
        }
        return removed
    }

    /** Replaces the hidden shows (a restore); an empty list clears them. */
    suspend fun setAll(shows: List<HiddenShow>) {
        dataStore.edit { prefs -> write(prefs, shows) }
    }

    private fun read(prefs: Preferences): List<HiddenShow> = HiddenShowsCodec.decode(prefs[IDS_KEY], prefs[DETAILS_KEY])

    private fun write(prefs: androidx.datastore.preferences.core.MutablePreferences, shows: List<HiddenShow>) {
        val (ids, details) = HiddenShowsCodec.encode(shows)
        if (ids.isEmpty()) {
            prefs.remove(IDS_KEY)
            prefs.remove(DETAILS_KEY)
        } else {
            prefs[IDS_KEY] = ids
            prefs[DETAILS_KEY] = details
        }
    }

    private companion object {
        /** Stable DataStore names; never renamed. */
        val IDS_KEY = stringPreferencesKey("hidden_podcast_ids")
        val DETAILS_KEY = stringPreferencesKey("hidden_podcast_details")
    }
}

/** Encodes [HiddenShow]s for [HiddenShowsPreferences]: the ID list and one details record per show. */
internal object HiddenShowsCodec {
    /** The most shows kept hidden; the oldest go first past it. */
    const val MAX = 500

    private const val FIELD = '\u001e'
    private const val ALIAS = '\u001d'

    fun encode(shows: List<HiddenShow>): Pair<String, String> {
        val clean = sanitize(shows)
        val ids = PreferenceIdList.encode(clean.map { it.id })
        val details =
            PreferenceIdList.encode(
                clean.map { show ->
                    listOf(show.id, show.title, show.imageUrl, show.aliasIds.joinToString(ALIAS.toString())).joinToString(FIELD.toString())
                },
            )
        return ids to details
    }

    fun decode(rawIds: String?, rawDetails: String?): List<HiddenShow> {
        val details =
            PreferenceIdList.decode(rawDetails).mapNotNull(::decodeRecord).associateBy(HiddenShow::id)
        return sanitize(PreferenceIdList.decode(rawIds).map { id -> details[id] ?: HiddenShow(id) })
    }

    private fun decodeRecord(record: String): HiddenShow? {
        val fields = record.split(FIELD)
        val id = fields.getOrNull(0)?.trim().orEmpty()
        if (id.isEmpty()) return null
        return HiddenShow(
            id = id,
            title = fields.getOrNull(1).orEmpty(),
            imageUrl = fields.getOrNull(2).orEmpty(),
            aliasIds = fields.getOrNull(3).orEmpty().split(ALIAS).map(String::trim).filter(String::isNotEmpty).toSet(),
        )
    }

    /** Distinct non-blank IDs, at most [MAX], with the separators removed from every field. */
    private fun sanitize(shows: List<HiddenShow>): List<HiddenShow> = shows
        .map { show ->
            HiddenShow(
                id = clean(show.id).trim(),
                title = clean(show.title).trim(),
                imageUrl = clean(show.imageUrl).trim(),
                aliasIds = show.aliasIds.map { clean(it).trim() }.filter { it.isNotEmpty() && it != show.id.trim() }.toSet(),
            )
        }.filter { it.id.isNotEmpty() }
        .distinctBy(HiddenShow::id)
        .take(MAX)

    private fun clean(value: String): String = value.filterNot { it == FIELD || it == ALIAS || it == '\u001f' }
}
