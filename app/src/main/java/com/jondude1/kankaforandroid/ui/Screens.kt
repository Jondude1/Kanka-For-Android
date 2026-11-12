package com.jondude1.kankaforandroid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import com.jondude1.kankaforandroid.BuildConfig
import com.jondude1.kankaforandroid.data.EntityKind
import com.jondude1.kankaforandroid.data.EntitySummary
import com.jondude1.kankaforandroid.data.KankaApi
import com.jondude1.kankaforandroid.data.Kinds
import com.jondude1.kankaforandroid.data.Post
import com.jondude1.kankaforandroid.data.str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun Root(vm: AppVm) {
    Column(Modifier.fillMaxSize()) {
        UpdateBanner(vm)
        Box(Modifier.weight(1f)) {
            val s = vm.current
            key(vm.stack.size, s) {
                when (s) {
                    Screen.Login -> LoginScreen(vm)
                    Screen.Campaigns -> CampaignsScreen(vm)
                    Screen.Home -> HomeScreen(vm)
                    is Screen.KindList -> KindListScreen(vm, s.kind)
                    is Screen.Detail -> DetailScreen(vm, s.kind, s.id)
                    is Screen.Edit -> EditScreen(vm, s.kind, s.id)
                    Screen.Settings -> SettingsScreen(vm)
                }
            }
        }
    }
}

private fun open(vm: AppVm, e: EntitySummary) {
    val kind = e.kind ?: return
    if (e.id != 0L) vm.push(Screen.Detail(kind, e.id))
}

// ───────────────────────── Login ─────────────────────────

@Composable
fun LoginScreen(vm: AppVm) {
    var url by remember { mutableStateOf(vm.prefs.url) }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text("Kanka", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text("Connect to your self-hosted Kanka.", style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Server URL") },
            placeholder = { Text("https://kanka.example.com") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            token, { token = it }, Modifier.fillMaxWidth(), label = { Text("API token") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Text(
            "Create a token in Kanka under your profile → API → Create new token.",
            style = MaterialTheme.typography.bodySmall,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = !busy && url.isNotBlank() && token.isNotBlank(),
            onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        KankaApi(url, token.trim()).campaigns()
                        vm.login(url, token)
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "Could not connect."; busy = false }
                }
            },
        ) { Text(if (busy) "Connecting…" else "Connect") }
    }
}

// ───────────────────────── Campaigns ─────────────────────────

@Composable
fun CampaignsScreen(vm: AppVm) {
    val api = vm.api ?: return
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Bar("Campaigns", null) {
                IconButton({ vm.push(Screen.Settings) }) { Icon(Icons.Filled.Settings, "Settings") }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            Load(Unit, { api.campaigns() }) { list, _ ->
                if (list.isEmpty()) Text("No campaigns found for this token.", Modifier.padding(24.dp))
                LazyColumn {
                    items(list) { c ->
                        ListItem(
                            modifier = Modifier.clickable { vm.openCampaign(c.id, c.name) },
                            headlineContent = { Text(c.name) },
                            supportingContent = { c.entry?.let { Text(android.text.Html.fromHtml(it, 0).toString().take(120)) } },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// ───────────────────────── Home (types, search, recent) ─────────────────────────

@Composable
fun HomeScreen(vm: AppVm) {
    val api = vm.api ?: return
    val cid = vm.campaignId
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<EntitySummary>?>(null) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun runSearch() {
        if (query.isBlank()) { results = null; return }
        searching = true; searchError = null
        scope.launch {
            try { results = api.search(cid, query.trim()) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { searchError = e.message }
            finally { searching = false }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Bar(vm.campaignName.ifBlank { "Campaign" }, { vm.pop() }) {
                IconButton({ vm.push(Screen.Settings) }) { Icon(Icons.Filled.Settings, "Settings") }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            item {
                OutlinedTextField(
                    query, { query = it; if (it.isBlank()) results = null },
                    Modifier.fillMaxWidth().padding(16.dp, 8.dp), singleLine = true,
                    placeholder = { Text("Search the world…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                )
            }
            val res = results
            if (searching) item { Text("Searching…", Modifier.padding(16.dp)) }
            searchError?.let { err -> item { Text(err, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) } }
            if (res != null) {
                if (res.isEmpty()) item { Text("No matches.", Modifier.padding(16.dp)) }
                items(res) { EntityRow(it) { open(vm, it) } }
            } else {
                items(Kinds.all.chunked(2)) { row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { k ->
                            Card(Modifier.weight(1f).clickable { vm.push(Screen.KindList(k)) }) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(k.glyph, style = MaterialTheme.typography.titleLarge)
                                    Spacer(Modifier.padding(6.dp))
                                    Text(k.label)
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item { RecentSection(vm, cid) }
            }
        }
    }
}

@Composable
private fun RecentSection(vm: AppVm, cid: Long) {
    val api = vm.api ?: return
    var recent by remember { mutableStateOf<List<EntitySummary>>(emptyList()) }
    LaunchedEffect(cid) {
        recent = try { api.recent(cid) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
    }
    if (recent.isNotEmpty()) {
        Column {
            Text("Recently edited", Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            recent.forEach { EntityRow(it) { open(vm, it) } }
        }
    }
}

// ───────────────────────── Entity list ─────────────────────────

@Composable
fun KindListScreen(vm: AppVm, kind: EntityKind) {
    val api = vm.api ?: return
    val cid = vm.campaignId
    var items by remember { mutableStateOf(listOf<EntitySummary>()) }
    var page by remember { mutableStateOf(1) }
    var last by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }
    var applied by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun fetch(p: Int) {
        loading = true; error = null
        try {
            val r = api.list(cid, kind, p, applied.ifBlank { null })
            items = if (p == 1) r.items else items + r.items
            page = r.page; last = r.lastPage
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Failed to load" }
        finally { loading = false }
    }
    LaunchedEffect(applied) { fetch(1) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Bar(kind.label, { vm.pop() }) {
                IconButton({ scope.launch { fetch(1) } }) { Icon(Icons.Filled.Refresh, "Refresh") }
            }
        },
        floatingActionButton = {
            FloatingActionButton({ vm.push(Screen.Edit(kind, null)) }) { Icon(Icons.Filled.Add, "New ${kind.singular}") }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)) {
            item {
                OutlinedTextField(
                    filter, { filter = it }, Modifier.fillMaxWidth().padding(16.dp, 8.dp), singleLine = true,
                    placeholder = { Text("Filter by name…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { applied = filter.trim() }),
                )
            }
            items(items, key = { it.id }) { e -> EntityRow(e) { vm.push(Screen.Detail(kind, e.id)) } }
            error?.let { msg -> item { ErrorBox(msg) { scope.launch { fetch(page) } } } }
            if (loading) item { Text("Loading…", Modifier.padding(16.dp)) }
            else if (items.isEmpty() && error == null) item { Text("Nothing here yet. Tap + to create one.", Modifier.padding(16.dp)) }
            if (!loading && page < last) item {
                TextButton({ scope.launch { fetch(page + 1) } }, Modifier.fillMaxWidth()) { Text("Load more") }
            }
        }
    }
}

// ───────────────────────── Detail ─────────────────────────

private class DetailData(val rec: JSONObject, val posts: List<Post>)

@Composable
fun DetailScreen(vm: AppVm, kind: EntityKind, id: Long) {
    val api = vm.api ?: return
    val cid = vm.campaignId
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var editingPost by remember { mutableStateOf<Post?>(null) }
    var newPost by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    Load(id, {
        val rec = api.get(cid, kind, id)
        val posts = try { api.posts(cid, rec.getLong("entity_id")) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
        DetailData(rec, posts)
    }) { d, reload ->
        val rec = d.rec
        val entityId = rec.optLong("entity_id", 0)
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Bar(rec.optString("name"), { vm.pop() }) {
                    IconButton(reload) { Icon(Icons.Filled.Refresh, "Refresh") }
                    IconButton({ vm.push(Screen.Edit(kind, id)) }) { Icon(Icons.Filled.Edit, "Edit") }
                    IconButton({ confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                }
            },
        ) { pad ->
            Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${kind.glyph} ${kind.singular.replaceFirstChar { it.uppercase() }}" + (rec.str("type")?.let { " · $it" } ?: "") + if (rec.optBoolean("is_private")) " · 🔒 private" else "",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                kind.extraFields.forEach { (k, label) ->
                    rec.str(k)?.takeIf { it.isNotBlank() }?.let { Text("$label: $it") }
                }
                val html = rec.str("entry_parsed") ?: rec.str("entry")
                if (!html.isNullOrBlank()) HtmlText(html, Modifier.fillMaxWidth())
                else Text("No description yet.", style = MaterialTheme.typography.bodyMedium)

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Posts", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (entityId != 0L) TextButton({ newPost = true }) { Text("Add post") }
                }
                d.posts.forEach { p ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.name + if (p.visibilityId != 1) " 🔒" else "", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                IconButton({ editingPost = p }) { Icon(Icons.Filled.Edit, "Edit post") }
                                IconButton({
                                    scope.launch {
                                        try { api.deletePost(cid, entityId, p.id); reload() }
                                        catch (e: CancellationException) { throw e }
                                        catch (e: Exception) { actionError = e.message }
                                    }
                                }) { Icon(Icons.Filled.Delete, "Delete post") }
                            }
                            if (p.entryHtml.isNotBlank()) HtmlText(p.entryHtml, Modifier.fillMaxWidth())
                        }
                    }
                }
                actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        if (confirmDelete) AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${rec.optString("name")}?") },
            text = { Text("This removes it from the campaign. Kanka keeps deleted entities in its recovery list.") },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    scope.launch {
                        try { api.delete(cid, kind, id); vm.pop() }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { actionError = e.message }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
        )

        if (newPost || editingPost != null) {
            PostDialog(
                existing = editingPost,
                onDismiss = { newPost = false; editingPost = null },
                onSave = { name, entry, vis ->
                    val pid = editingPost?.id
                    newPost = false; editingPost = null
                    scope.launch {
                        try { api.savePost(cid, entityId, pid, name, entry, vis); reload() }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { actionError = e.message }
                    }
                },
            )
        }
    }
}

@Composable
private fun PostDialog(existing: Post?, onDismiss: () -> Unit, onSave: (String, String, Int) -> Unit) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var entry by remember { mutableStateOf(existing?.entry.orEmpty()) }
    var vis by remember { mutableStateOf(existing?.visibilityId ?: 1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New post" else "Edit post") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
                OutlinedTextField(entry, { entry = it }, Modifier.fillMaxWidth().height(200.dp), label = { Text("Text (HTML)") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(vis == 1, { vis = 1 }, label = { Text("Everyone") })
                    FilterChip(vis == 3, { vis = 3 }, label = { Text("Admins only") })
                }
            }
        },
        confirmButton = { TextButton({ onSave(name.trim(), entry, vis) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

// ───────────────────────── Edit / create ─────────────────────────

@Composable
fun EditScreen(vm: AppVm, kind: EntityKind, id: Long?) {
    val api = vm.api ?: return
    val cid = vm.campaignId
    if (id == null) EditForm(vm, kind, null)
    else Load(id, { api.get(cid, kind, id) }) { rec, _ -> EditForm(vm, kind, rec) }
}

@Composable
private fun EditForm(vm: AppVm, kind: EntityKind, rec: JSONObject?) {
    val api = vm.api ?: return
    val cid = vm.campaignId
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(rec?.optString("name").orEmpty()) }
    var subtype by remember { mutableStateOf(rec?.str("type").orEmpty()) }
    var isPrivate by remember { mutableStateOf(rec?.optBoolean("is_private") ?: false) }
    val extras = remember { kind.extraFields.associate { (k, _) -> k to mutableStateOf(rec?.str(k).orEmpty()) } }
    var entry by remember { mutableStateOf(TextFieldValue(rec?.str("entry").orEmpty())) }
    var preview by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun wrap(open: String, close: String) {
        val s = entry.text; val sel = entry.selection
        val chosen = s.substring(sel.min, sel.max)
        val text = s.replaceRange(sel.min, sel.max, open + chosen + close)
        entry = TextFieldValue(text, TextRange(sel.min + open.length, sel.min + open.length + chosen.length))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Bar(if (rec == null) "New ${kind.singular}" else "Edit ${kind.singular}", { vm.pop() }) {
                TextButton(
                    enabled = !saving && name.isNotBlank(),
                    onClick = {
                        saving = true; error = null
                        val body = JSONObject().put("name", name.trim()).put("type", subtype.trim())
                            .put("is_private", isPrivate).put("entry", entry.text)
                        extras.forEach { (k, v) -> body.put(k, v.value.trim()) }
                        scope.launch {
                            try {
                                val saved = api.save(cid, kind, rec?.getLong("id"), body)
                                if (rec == null) vm.replaceTop(Screen.Detail(kind, saved.getLong("id"))) else vm.pop()
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = e.message ?: "Save failed"; saving = false }
                        }
                    },
                ) { Text(if (saving) "Saving…" else "Save") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
            OutlinedTextField(subtype, { subtype = it }, Modifier.fillMaxWidth(), label = { Text("Type (e.g. NPC, City, Deity)") }, singleLine = true)
            kind.extraFields.forEach { (k, label) ->
                val st = extras.getValue(k)
                OutlinedTextField(st.value, { st.value = it }, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Private (hidden from non-admins)", Modifier.weight(1f))
                Switch(isPrivate, { isPrivate = it })
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Entry", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (!preview) {
                    OutlinedButton({ wrap("<b>", "</b>") }) { Text("B") }
                    OutlinedButton({ wrap("<i>", "</i>") }) { Text("I") }
                    OutlinedButton({ wrap("<h2>", "</h2>") }) { Text("H") }
                    OutlinedButton({ wrap("<ul><li>", "</li></ul>") }) { Text("•") }
                }
                FilterChip(preview, { preview = !preview }, label = { Text("Preview") })
            }
            if (preview) HtmlText(entry.text, Modifier.fillMaxWidth())
            else OutlinedTextField(
                entry, { entry = it }, Modifier.fillMaxWidth().height(280.dp),
                placeholder = { Text("<p>HTML supported. Mention other entities with [character:123].</p>") },
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

// ───────────────────────── Settings ─────────────────────────

@Composable
fun SettingsScreen(vm: AppVm) {
    val clipboard = LocalClipboardManager.current
    val server = vm.prefs.url
    val config = """
{
  "mcpServers": {
    "kanka": {
      "command": "node",
      "args": ["/path/to/kanka-for-android/mcp-server/dist/index.js"],
      "env": {
        "KANKA_URL": "$server",
        "KANKA_TOKEN": "YOUR_KANKA_TOKEN",
        "KANKA_CAMPAIGN_ID": "${vm.campaignId}"
      }
    }
  }
}""".trim()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { Bar("Settings", { vm.pop() }) },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("App version", style = MaterialTheme.typography.titleSmall)
                    Text("v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
                    Button({ vm.checkForUpdate(force = true) }, enabled = !vm.checking) { Text(if (vm.checking) "Checking…" else "Check for updates") }
                    vm.updateMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Connected server", style = MaterialTheme.typography.titleSmall)
                    Text(server)
                    if (vm.campaignName.isNotBlank()) Text("Campaign: ${vm.campaignName} (#${vm.campaignId})")
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("AI agent access (MCP)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "The repo includes an MCP server that lets Claude and other agents search, read and edit this same Kanka world. " +
                            "Run it on your server or PC (see mcp-server/README.md), then paste this config into your agent.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton({ clipboard.setText(AnnotatedString(config)) }) { Text("Copy MCP config") }
                }
            }
            OutlinedButton({ vm.logout() }) { Text("Disconnect / log out") }
        }
    }
}
