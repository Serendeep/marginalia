@file:Suppress("RestrictedApi")

package com.serendeep.marginalia.handwriting

import android.util.Log

import android.graphics.RectF
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import com.serendeep.marginalia.data.InkStroke
import com.serendeep.marginalia.ink.toPoints
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface ModelState {
    data object NotDownloaded : ModelState
    data object Downloading : ModelState
    data object Ready : ModelState
    data class Unavailable(val reason: String) : ModelState
}

private const val LANGUAGE_TAG = "en-US"
private const val MAX_CANDIDATES = 3

/**
 * On-device handwriting recognition. Every failure, including Play services being absent,
 * ends as [ModelState.Unavailable] or an empty result; nothing here throws to callers.
 */
@Singleton
open class InkRecognizer @Inject constructor() {

    private val _state = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    open val state: StateFlow<ModelState> get() = _state.asStateFlow()

    private val download = Mutex()
    private var client: DigitalInkRecognizer? = null

    /** Set when the latest recognition threw, so callers can tell "nothing written" from "failed". */
    @Volatile
    var lastFailed = false
        private set

    private val model: DigitalInkRecognitionModel by lazy {
        val id = DigitalInkRecognitionModelIdentifier.fromLanguageTag(LANGUAGE_TAG)
            ?: error("No handwriting model for $LANGUAGE_TAG")
        DigitalInkRecognitionModel.builder(id).build()
    }

    /** Re-reads whether the model is on the device, without downloading. */
    open suspend fun refresh(): ModelState {
        if (_state.value == ModelState.Downloading) return ModelState.Downloading
        val next = guarded(ModelState.Unavailable("Handwriting recognition is not available on this device")) {
            if (RemoteModelManager.getInstance().isModelDownloaded(model).await()) ModelState.Ready else ModelState.NotDownloaded
        }
        _state.value = next
        return next
    }

    /** Downloads the model if needed; true once it is ready. */
    open suspend fun ensureModel(): Boolean = download.withLock {
        if (refresh() == ModelState.Ready) return@withLock true
        if (_state.value is ModelState.Unavailable) return@withLock false
        _state.value = ModelState.Downloading
        // Closing the sheet must not strand the state at Downloading.
        withContext(NonCancellable) {
            _state.value = guarded(ModelState.Unavailable("Could not download the handwriting model. Check the connection and try again")) {
                RemoteModelManager.getInstance().download(model, DownloadConditions.Builder().build()).await()
                ModelState.Ready
            }
        }
        _state.value == ModelState.Ready
    }

    /** Candidate transcriptions of one line, best first; empty when recognition is unavailable. */
    open suspend fun recognize(strokes: List<InkStroke>, writingArea: RectF?): List<String> {
        if (strokes.isEmpty() || !ensureModel()) return emptyList()
        lastFailed = false
        return guarded(emptyList(), onError = { lastFailed = true }) {
            val ink = withContext(Dispatchers.Default) { buildInk(strokes) }
            // ML Kit rejects a context without pre-context, even when nothing was written before.
            val context = RecognitionContext.builder().setPreContext("").apply {
                if (writingArea != null) setWritingArea(WritingArea(writingArea.width(), writingArea.height()))
            }.build()
            val recogniser = client ?: DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
                .also { client = it }
            recogniser.recognize(ink, context).await().candidates.take(MAX_CANDIDATES).map { it.text }
        }
    }

    private fun buildInk(strokes: List<InkStroke>): Ink {
        val timed = timeline(strokes.map { TimedStroke(it.startedAt, it.batch.toPoints()) })
        val ink = Ink.builder()
        for (points in timed) {
            val stroke = Ink.Stroke.builder()
            points.forEach { stroke.addPoint(Ink.Point.create(it.x, it.y, it.t)) }
            ink.addStroke(stroke.build())
        }
        return ink.build()
    }

    private suspend fun <T> guarded(fallback: T, onError: () -> Unit = {}, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Log.w(TAG, "Handwriting recognition failed: ${e.javaClass.simpleName}: ${e.message}")
        onError()
        fallback
    }
}

/** One recognised string per line of [strokes], blank lines dropped. */
suspend fun InkRecognizer.recognizeLines(strokes: List<InkStroke>): List<String> =
    groupLines(strokes) { it.bounds }.mapNotNull { recognizeLine(it) }

/** Best transcription of strokes already known to form one line; null when nothing was read. */
suspend fun InkRecognizer.recognizeLine(line: List<InkStroke>): String? {
    val area = RectF(
        line.minOf { it.bounds.left }, line.minOf { it.bounds.top },
        line.maxOf { it.bounds.right }, line.maxOf { it.bounds.bottom },
    )
    return recognize(line, area).firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

private const val TAG = "InkRecognizer"
