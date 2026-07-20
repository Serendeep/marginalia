package com.serendeep.marginalia.notebook

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.serendeep.marginalia.pdf.PdfDocumentSource

/** Temporary host for the PDF pane until the two-pane notebook lands. */
@Composable
fun NotebookScreen() {
    val context = LocalContext.current
    var source by remember { mutableStateOf<PdfDocumentSource?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        source?.close()
        source = context.contentResolver.openFileDescriptor(uri, "r")?.let {
            PdfDocumentSource.open(context, it)
        }
    }

    DisposableEffect(Unit) {
        onDispose { source?.close() }
    }

    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        Button(
            onClick = { picker.launch(arrayOf("application/pdf")) },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text("Open PDF")
        }

        val current = source
        if (current == null) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Pick a lecture PDF", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            com.serendeep.marginalia.pdf.PdfPane(
                source = current,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}
