package com.jondude1.kankaforandroid.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jondude1.kankaforandroid.data.EntityKind
import com.jondude1.kankaforandroid.data.KankaApi
import com.jondude1.kankaforandroid.data.Prefs
import com.jondude1.kankaforandroid.update.UpdateInfo
import com.jondude1.kankaforandroid.update.Updater
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Login : Screen
    data object Campaigns : Screen
    data object Home : Screen
    data class KindList(val kind: EntityKind) : Screen
    data class Detail(val kind: EntityKind, val id: Long) : Screen
    data class Edit(val kind: EntityKind, val id: Long?) : Screen
    data object Settings : Screen
}

class AppVm(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    var api: KankaApi? = null
        private set
    val stack = mutableStateListOf<Screen>()

    var campaignId by mutableStateOf(prefs.campaignId)
        private set
    var campaignName by mutableStateOf(prefs.campaignName)
        private set

    // Self-update state
    var update by mutableStateOf<UpdateInfo?>(null)
    var updateProgress by mutableStateOf<Float?>(null)
    var updateMessage by mutableStateOf<String?>(null)
    var checking by mutableStateOf(false)

    init {
        if (prefs.loggedIn) {
            api = KankaApi(prefs.url, prefs.token)
            stack.add(Screen.Campaigns)
            if (campaignId != 0L) stack.add(Screen.Home)
        } else stack.add(Screen.Login)
    }

    val current get() = stack.last()
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    /** Replace the top screen (e.g. Edit -> Detail after saving). */
    fun replaceTop(s: Screen) { stack[stack.lastIndex] = s }
    fun popTo(s: Screen) { while (stack.size > 1 && stack.last() != s) stack.removeAt(stack.lastIndex) }

    fun login(url: String, token: String) {
        prefs.url = url.trim(); prefs.token = token.trim()
        api = KankaApi(prefs.url, prefs.token)
        stack.clear(); stack.add(Screen.Campaigns)
    }

    fun logout() {
        prefs.logout(); api = null; campaignId = 0; campaignName = ""
        stack.clear(); stack.add(Screen.Login)
    }

    fun openCampaign(id: Long, name: String) {
        prefs.campaignId = id; prefs.campaignName = name
        campaignId = id; campaignName = name
        popTo(Screen.Campaigns); push(Screen.Home)
    }

    // ---- self-update ----
    fun checkForUpdate(force: Boolean) {
        val now = System.currentTimeMillis()
        if (checking || (!force && now - prefs.lastUpdateCheck < 30 * 60 * 1000L)) return
        checking = true
        viewModelScope.launch {
            try {
                prefs.lastUpdateCheck = now
                val info = Updater.check()
                update = info
                updateMessage = if (force) (if (info == null) "You're on the latest version." else "Version ${info.versionName} is available.") else null
            } catch (e: Exception) {
                if (force) updateMessage = e.message ?: "Update check failed."
            } finally { checking = false }
        }
    }

    fun installUpdate() {
        val info = update ?: return
        if (updateProgress != null) return
        updateProgress = 0f
        viewModelScope.launch {
            try {
                val apk = Updater.download(getApplication(), info) { updateProgress = it }
                updateProgress = null
                if (!Updater.install(getApplication(), apk)) {
                    updateMessage = "Allow installs from this app, then tap Update again."
                }
            } catch (e: Exception) {
                updateProgress = null
                updateMessage = e.message ?: "Download failed."
            }
        }
    }

    fun dismissUpdate() { update = null }
}
