package com.serendeep.marginalia.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.serendeep.marginalia.ui.components.GlassTextButton
import com.serendeep.marginalia.ui.components.PanelSection
import com.serendeep.marginalia.ui.components.SidePanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Panel listing what changed since the last version the user saw. */
@Composable
fun WhatsNewPanel(onDismiss: () -> Unit, vm: UpdateViewModel) {
    val versions by produceState(emptyList<VersionNotes>()) { value = withContext(Dispatchers.IO) { vm.whatsNew() } }
    NotesPanel("What's new in ${vm.versionName}", versions, vm, onDismiss)
}

/** Panel with the full bundled release history. */
@Composable
fun ChangelogPanel(onDismiss: () -> Unit, vm: UpdateViewModel) {
    val versions by produceState(emptyList<VersionNotes>()) { value = withContext(Dispatchers.IO) { vm.changelog() } }
    NotesPanel("Changelog", versions, vm, onDismiss)
}

@Composable
private fun NotesPanel(title: String, versions: List<VersionNotes>, vm: UpdateViewModel, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    SidePanel(
        onDismiss = onDismiss,
        title = title,
        eyebrow = "Updates",
        footer = { GlassTextButton("View on GitHub", onClick = { uri.openUri(vm.releasesUrl) }) },
    ) {
        if (versions.isEmpty()) {
            Text("Nothing to show yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        versions.forEach { ReleaseNotesBody(it, header = versions.size > 1 || title == "Changelog") }
    }
}

/** One release's notes in New, Fixed and Improved groups, with a version header when [header] is set. */
@Composable
fun ReleaseNotesBody(notes: VersionNotes, header: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (header && notes.version.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(notes.version, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                notes.date?.let { Text(localDate(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp)) }
            }
        }
        group("New", notes.new)
        group("Fixed", notes.fixed)
        group("Improved", notes.improved)
    }
}

@Composable
private fun group(label: String, items: List<String>) {
    if (items.isEmpty()) return
    PanelSection(label) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

private fun localDate(iso: String): String = try {
    LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
} catch (_: Exception) {
    iso
}
