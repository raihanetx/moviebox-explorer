package com.example.movieboxexplorer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Small persistent settings. The site rotates its mirror domains, so both the API
 * host and the mirror used for Origin/Referer are editable at runtime.
 */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("moviebox_explorer", Context.MODE_PRIVATE)

    /**
     * Bumped whenever the API host changes. The app recreates its repository — and
     * with it the session token — when this changes, so it must stay separate from
     * the content preferences below.
     */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val _ageGateAccepted = MutableStateFlow(prefs.getBoolean(KEY_AGE_GATE, false))
    val ageGateAcceptedFlow: StateFlow<Boolean> = _ageGateAccepted.asStateFlow()

    private val _showAdultContent = MutableStateFlow(prefs.getBoolean(KEY_ADULT, false))
    val adultContentFlow: StateFlow<Boolean> = _showAdultContent.asStateFlow()

    var apiBase: String
        get() = prefs.getString(KEY_API, MovieBoxApi.DEFAULT_API_BASE) ?: MovieBoxApi.DEFAULT_API_BASE
        set(value) {
            prefs.edit().putString(KEY_API, value.trim()).apply()
            bump()
        }

    var siteHost: String
        get() = prefs.getString(KEY_HOST, MovieBoxApi.DEFAULT_SITE_HOSTS.first())
            ?: MovieBoxApi.DEFAULT_SITE_HOSTS.first()
        set(value) {
            prefs.edit().putString(KEY_HOST, value.trim()).apply()
            bump()
        }

    /** Preferred host first, then the remaining known mirrors as fallbacks. */
    fun siteHosts(): List<String> =
        (listOf(siteHost) + MovieBoxApi.DEFAULT_SITE_HOSTS).distinct().filter { it.isNotBlank() }

    /**
     * Whether the user has acknowledged that the catalogue carries adult titles.
     *
     * The flag is only trustworthy on a title's *detail* record — the listing
     * endpoints always report `isPornographic = false`, so the gate cannot be applied
     * before a title has been opened.
     */
    var ageGateAccepted: Boolean
        get() = _ageGateAccepted.value
        set(value) {
            prefs.edit().putBoolean(KEY_AGE_GATE, value).apply()
            _ageGateAccepted.value = value
        }

    /**
     * Whether adult titles may be opened at all.
     *
     * Off by default: the catalogue surfaces adult titles inside ordinary rows and
     * search results, and the only reliable signal is the detail record.
     */
    var showAdultContent: Boolean
        get() = _showAdultContent.value
        set(value) {
            prefs.edit().putBoolean(KEY_ADULT, value).apply()
            _showAdultContent.value = value
        }

    fun reset() {
        prefs.edit().remove(KEY_API).remove(KEY_HOST).apply()
        bump()
    }

    private fun bump() {
        _revision.value = _revision.value + 1
    }

    private companion object {
        const val KEY_API = "api_base"
        const val KEY_HOST = "site_host"
        const val KEY_AGE_GATE = "age_gate_accepted"
        const val KEY_ADULT = "show_adult_content"
    }
}
