package com.serendeep.marginalia.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.components.GlassTextButton
import com.serendeep.marginalia.ui.components.WebPopup
import com.serendeep.marginalia.ui.theme.BodyFamily
import com.serendeep.marginalia.ui.theme.DimInkDark
import com.serendeep.marginalia.ui.theme.MonoFamily
import com.serendeep.marginalia.ui.theme.Violet
import kotlinx.coroutines.delay

/** The one-line summary shown under the version in settings. */
fun statusLine(status: UpdateStatus, now: Long = System.currentTimeMillis()): String = when (status.phase) {
    UpdatePhase.CHECKING -> "Checking for updates…"
    UpdatePhase.DOWNLOADING -> (if (status.fullFallback) "Downloading full update " else "Downloading ") + "${status.progress}%"
    UpdatePhase.READY -> "Update ${status.info?.versionName.orEmpty()} ready"
    UpdatePhase.AVAILABLE -> "Update ${status.info?.versionName.orEmpty()} available"
    UpdatePhase.INSTALLING -> "Installing…"
    UpdatePhase.FAILED -> status.error ?: "Something went wrong"
    UpdatePhase.UP_TO_DATE ->
        if (status.checkedAt > 0) {
            "Up to date · checked " + DateUtils.getRelativeTimeSpanString(
                status.checkedAt,
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            )
        } else {
            "Not checked yet"
        }
}

private fun unknownSourcesIntent(context: Context) =
    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

/** The next step for the current state: install, fetch, or look again. */
@Composable
private fun rememberPrimaryAction(vm: UpdateViewModel): () -> Unit {
    val context = LocalContext.current
    return {
        when (vm.status.value.phase) {
            UpdatePhase.READY ->
                if (vm.canInstall()) vm.install() else context.startActivity(unknownSourcesIntent(context))
            UpdatePhase.AVAILABLE -> vm.download()
            UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING, UpdatePhase.INSTALLING -> Unit
            else -> vm.checkNow()
        }
    }
}

private fun primaryLabel(phase: UpdatePhase) = when (phase) {
    UpdatePhase.READY -> "Restart to update"
    UpdatePhase.AVAILABLE -> "Download"
    else -> "Check now"
}

/** Sidebar row: the ready update, or a quiet note on what changed in the version that was just installed. */
@Composable
fun UpdateSidebarRow(vm: UpdateViewModel = hiltViewModel()) {
    if (!vm.enabled) return
    val status by vm.status.collectAsStateWithLifecycle()
    val whatsNewUntil by vm.whatsNewUntil.collectAsStateWithLifecycle()
    var notes by remember { mutableStateOf<String?>(null) }
    val act = rememberPrimaryAction(vm)
    notes?.let { WebPopup(it) { notes = null } }
    if (status.phase == UpdatePhase.READY) {
        SidebarRow("Update ${status.info?.versionName.orEmpty()} ready", "Restart", act)
    } else if (whatsNewUntil > System.currentTimeMillis()) {
        SidebarRow("Updated to ${vm.versionName}", "What's new") {
            notes = vm.whatsNewUrl()
            vm.dismissWhatsNew()
        }
    }
}

@Composable
private fun SidebarRow(label: String, action: String, act: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(shape)
            .border(1.dp, Violet.copy(alpha = 0.5f), shape)
            .clickable(onClick = act)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Outlined.SystemUpdateAlt, null, tint = Violet, modifier = Modifier.size(16.dp))
        Text(
            label,
            fontFamily = BodyFamily,
            fontSize = 12.5.sp,
            color = Color(0xFFA0A0AB),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(action, fontFamily = MonoFamily, fontSize = 10.5.sp, letterSpacing = 0.4.sp, color = Violet)
    }
}

/** Body of the Updates section in settings; applies its toggles immediately. */
@Composable
fun UpdatesSectionContent(vm: UpdateViewModel = hiltViewModel()) {
    val status by vm.status.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var canInstall by remember { mutableStateOf(vm.canInstall()) }
    var notes by remember { mutableStateOf<String?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canInstall = vm.canInstall() }
    val act = rememberPrimaryAction(vm)
    val context = LocalContext.current
    val busy = status.phase in setOf(UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING, UpdatePhase.INSTALLING)

    notes?.let { WebPopup(it) { notes = null } }
    val channel by vm.channel.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Row(
        Modifier.clickable {
            clipboard.setText(AnnotatedString(vm.versionName))
            copied = true
        },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(installedLine(vm.versionName, vm.installedChannel), style = MaterialTheme.typography.bodyLarge)
        if (copied) Text("Copied", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(statusLine(status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (status.phase == UpdatePhase.AVAILABLE || status.phase == UpdatePhase.DOWNLOADING) {
        status.info?.downloadSize(vm.installedVersionCode, usePatch = !status.fullFallback)?.takeIf { it > 0 }?.let { bytes ->
            Text(
                Formatter.formatShortFileSize(context, bytes) + " update",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (status.phase == UpdatePhase.READY || status.phase == UpdatePhase.AVAILABLE) {
            GlassButton(primaryLabel(status.phase), onClick = act)
        }
        GlassTextButton("Check now", onClick = vm::checkNow, enabled = !busy)
        status.info?.notesUrl?.let { url -> GlassTextButton("What's new", onClick = { notes = url }) }
    }
    if (!canInstall) {
        Text(
            "Android needs your permission before Marginalia can install its own updates.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GlassTextButton("Allow installing updates", onClick = { context.startActivity(unknownSourcesIntent(context)) })
    }
    if (Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true) || Build.MANUFACTURER.equals("HONOR", ignoreCase = true)) {
        Text(
            "This tablet asks for one tap on INSTALL to confirm each update.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text("Channel", style = MaterialTheme.typography.bodyLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        UpdateChannel.entries.forEach { option ->
            FilterChip(
                selected = option == channel,
                onClick = { vm.setChannel(option) },
                label = { Text(option.label, maxLines = 1) },
            )
        }
    }
    Text(
        "Nightly gets every change as it lands. It's tested, but rougher.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    switchBackNote(vm.installedChannel, channel)?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ToggleRow("Check automatically", settings.checkAutomatically) { v -> vm.setSettings { it.copy(checkAutomatically = v) } }
    ToggleRow("Wi-Fi only", settings.wifiOnly) { v -> vm.setSettings { it.copy(wifiOnly = v) } }
    ToggleRow("Download automatically", settings.downloadAutomatically) { v -> vm.setSettings { it.copy(downloadAutomatically = v) } }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Banners at the top of Today: a pinned one for versions that must be replaced, and a dismissable note. */
@Composable
fun UpdateBanners(modifier: Modifier = Modifier, vm: UpdateViewModel = hiltViewModel()) {
    if (!vm.enabled) return
    val status by vm.status.collectAsStateWithLifecycle()
    val remote by vm.remote.collectAsStateWithLifecycle()
    val dismissed by vm.dismissedMessage.collectAsStateWithLifecycle()
    val act = rememberPrimaryAction(vm)
    var link by remember { mutableStateOf<String?>(null) }
    link?.let { WebPopup(it) { link = null } }
    val must = remote.mustUpdate(vm.installedVersionCode)
    val note = remote.message?.takeIf { it.text != dismissed }
    if (!must && note == null) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (must) {
            val busy = status.phase in setOf(UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING, UpdatePhase.INSTALLING)
            Banner {
                Text("This version has a known problem — update now", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                if (busy) {
                    Text(statusLine(status), style = MaterialTheme.typography.bodySmall, color = DimInkDark)
                } else {
                    GlassButton(primaryLabel(status.phase), onClick = act)
                }
            }
        }
        note?.let { message ->
            Banner {
                Text(message.text, modifier = Modifier.weight(1f))
                message.url?.let { url -> GlassTextButton("Learn more", onClick = { link = url }) }
                IconButton(onClick = { vm.dismissMessage(message.text) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Banner(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, Violet.copy(alpha = 0.5f), shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        content = content,
    )
}
