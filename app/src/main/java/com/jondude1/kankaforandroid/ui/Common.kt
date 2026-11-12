package com.jondude1.kankaforandroid.ui

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.jondude1.kankaforandroid.data.EntitySummary
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Bar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        },
        actions = actions,
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MaterialTheme.colorScheme.error)
        if (onRetry != null) { Spacer(Modifier.height(12.dp)); Button(onClick = onRetry) { Text("Retry") } }
    }
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Runs [block] whenever [key] changes (or reload is called) and renders loading / error / content. */
@Composable
fun <T> Load(key: Any?, block: suspend () -> T, content: @Composable (T, () -> Unit) -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    var result by remember(key, tick) { mutableStateOf<Result<T>?>(null) }
    LaunchedEffect(key, tick) {
        result = try { Result.success(block()) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }
    val r = result
    when {
        r == null -> Loading()
        r.isFailure -> ErrorBox(r.exceptionOrNull()?.message ?: "Something went wrong", { tick++ })
        else -> content(r.getOrThrow()) { tick++ }
    }
}

@Composable
fun HtmlText(html: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val link = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { ctx -> TextView(ctx).apply { movementMethod = LinkMovementMethod.getInstance(); textSize = 16f } },
        update = {
            it.setTextColor(color); it.setLinkTextColor(link)
            it.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT).trimEnd()
        },
    )
}

@Composable
fun EntityRow(e: EntitySummary, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Text(e.kind?.glyph ?: "•", style = MaterialTheme.typography.titleLarge) },
        headlineContent = { Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val sub = listOfNotNull(e.kind?.singular?.replaceFirstChar { it.uppercase() }.takeIf { e.kind != null && e.type != e.kind?.singular }, e.type?.takeIf { e.kind?.singular != it }).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub)
        },
        trailingContent = { if (e.isPrivate) Icon(Icons.Filled.Lock, "Private", Modifier.size(18.dp)) },
    )
}

@Composable
fun UpdateBanner(vm: AppVm) {
    val info = vm.update ?: return
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Update available — v${info.versionName}", Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer)
                val p = vm.updateProgress
                if (p == null) {
                    TextButton(onClick = { vm.installUpdate() }) { Text("Update") }
                    TextButton(onClick = { vm.dismissUpdate() }) { Text("Later") }
                }
            }
            vm.updateProgress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
            vm.updateMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
    }
}
