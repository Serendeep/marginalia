package com.serendeep.marginalia.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serendeep.marginalia.data.CourseEntity
import com.serendeep.marginalia.data.LectureEntity
import kotlinx.coroutines.delay

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onOpenLecture: (String) -> Unit,
) {
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var showNewCourse by remember { mutableStateOf(false) }

    // One screen-level launcher: a per-row launcher inside the lazy list would
    // unregister when its row is recycled while the system picker is open.
    var importTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val target = importTarget
        importTarget = null
        if (uri != null && target != null) viewModel.importPdf(target, uri)
    }
    val onImport: (String) -> Unit = {
        importTarget = it
        picker.launch(arrayOf("application/pdf"))
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Library", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = { showNewCourse = true }) { Text("New course") }
        }

        error?.let { message ->
            LaunchedEffect(message) {
                delay(4_000)
                viewModel.dismissError()
            }
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(courses, key = { it.id }) { course ->
                CourseCard(course, viewModel, onOpenLecture, onImport)
            }
        }
    }

    if (showNewCourse) {
        NamePromptDialog(
            title = "New course",
            onDismiss = { showNewCourse = false },
            onConfirm = {
                viewModel.createCourse(it)
                showNewCourse = false
            },
        )
    }
}

@Composable
private fun CourseCard(
    course: CourseEntity,
    viewModel: LibraryViewModel,
    onOpenLecture: (String) -> Unit,
    onImport: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    var showNewLecture by remember { mutableStateOf(false) }
    val lectures by viewModel.lecturesOf(course.id).collectAsStateWithLifecycle(initialValue = emptyList())

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(course.name, style = MaterialTheme.typography.titleMedium)
                Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleMedium)
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                lectures.forEach { lecture ->
                    LectureRow(lecture, viewModel, onOpenLecture, onImport)
                }
                TextButton(onClick = { showNewLecture = true }) { Text("New lecture") }
            }
        }
    }

    if (showNewLecture) {
        NamePromptDialog(
            title = "New lecture",
            onDismiss = { showNewLecture = false },
            onConfirm = {
                viewModel.createLecture(course.id, it)
                showNewLecture = false
            },
        )
    }
}

@Composable
private fun LectureRow(
    lecture: LectureEntity,
    viewModel: LibraryViewModel,
    onOpenLecture: (String) -> Unit,
    onImport: (String) -> Unit,
) {
    val documents by viewModel.documentsOf(lecture.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val latest = documents.filter { it.localPath.isNotEmpty() }.maxByOrNull { it.versionIndex }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenLecture(lecture.id) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(lecture.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                latest?.let { "${it.pageCount} pages · ${it.fileName}" } ?: "No PDF yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { onImport(lecture.id) }) {
            Text(if (latest == null) "Import PDF" else "Replace")
        }
    }
}

@Composable
private fun NamePromptDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
