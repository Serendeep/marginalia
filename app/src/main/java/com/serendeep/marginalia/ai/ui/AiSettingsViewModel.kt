package com.serendeep.marginalia.ai.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serendeep.marginalia.ai.AiConfig
import com.serendeep.marginalia.ai.AiException
import com.serendeep.marginalia.ai.AutoSorter
import com.serendeep.marginalia.ai.ModelCatalog
import com.serendeep.marginalia.ai.AiSettings
import com.serendeep.marginalia.ai.AiTask
import com.serendeep.marginalia.ai.ModelResolver
import com.serendeep.marginalia.ai.TaskModel
import com.serendeep.marginalia.ai.ChatGptAuth
import com.serendeep.marginalia.ai.ChatGptStatus
import com.serendeep.marginalia.ai.ChatModel
import com.serendeep.marginalia.ai.ProviderChoice
import com.serendeep.marginalia.update.RemoteConfigStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Immutable
sealed interface ModelsState {
    data object Idle : ModelsState
    data object Loading : ModelsState
    data class Loaded(val models: List<ChatModel>) : ModelsState
    data class Failed(val message: String) : ModelsState
}

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val auth: ChatGptAuth,
    private val settings: AiSettings,
    private val catalog: ModelCatalog,
    private val sorter: AutoSorter,
    private val resolver: ModelResolver,
    remote: RemoteConfigStore,
) : ViewModel() {

    /** False while the remote config switches AI off. */
    val aiAllowed: StateFlow<Boolean> = remote.config.map { it.aiAllowed }
        .stateIn(viewModelScope, SharingStarted.Eagerly, remote.config.value.aiAllowed)

    val config: StateFlow<AiConfig> = settings.config
    val status: StateFlow<ChatGptStatus> = auth.status
    val autoSort: StateFlow<Boolean> = sorter.enabled

    /** The model requests currently go to, shared by every surface that shows a picker. */
    val selected: StateFlow<String?> = combine(settings.config, auth.status) { c, s ->
        when (c.provider) {
            ProviderChoice.CHATGPT -> (s as? ChatGptStatus.Connected)?.model
            ProviderChoice.COMPATIBLE -> c.model.ifEmpty { null }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setAutoSort(on: Boolean) = sorter.setEnabled(on)

    val taskModels: StateFlow<Map<AiTask, TaskModel>> = settings.tasks

    fun setTaskModel(task: AiTask, value: TaskModel) = settings.setTask(task, value)

    /** The model [task] would use with no override, for the "Default" row of its picker. */
    fun defaultFor(task: AiTask, models: List<ChatModel>): String? =
        ModelResolver.resolve(task, TaskModel(), resolver.global(), models).model

    /** One-line state for the sidebar row. */
    val label: StateFlow<String> = combine(settings.config, auth.status) { c, s ->
        when (c.provider) {
            ProviderChoice.CHATGPT -> if (s is ChatGptStatus.Connected) "ChatGPT · Connected" else "Connect ChatGPT"
            ProviderChoice.COMPATIBLE -> if (c.baseUrl.isBlank()) "Set up AI" else "Custom endpoint"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Connect ChatGPT")

    private val _models = MutableStateFlow<ModelsState>(ModelsState.Idle)
    val models: StateFlow<ModelsState> = _models.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private var signIn: Job? = null

    fun setProvider(choice: ProviderChoice) {
        if (settings.config.value.provider == choice) return
        settings.update { it.copy(provider = choice) }
        _models.value = ModelsState.Idle
    }

    /** Starts the browser sign-in; [openUrl] receives the authorize URL once the loopback listener is up. */
    fun connect(openUrl: (String) -> Unit) {
        if (signIn?.isActive == true) return
        _notice.value = null
        signIn = viewModelScope.launch {
            val attempt = try {
                withContext(Dispatchers.IO) { auth.beginSignIn() }
            } catch (e: AiException) {
                _notice.value = e.error.message
                return@launch
            }
            val waiting = launch {
                delay(LISTENER_HEAD_START_MS)
                openUrl(attempt.url)
            }
            try {
                auth.completeSignIn(attempt)
            } finally {
                waiting.cancel()
            }
        }
    }

    fun cancelSignIn() {
        signIn?.cancel()
        signIn = null
    }

    fun disconnect() {
        cancelSignIn()
        auth.disconnect()
        _models.value = ModelsState.Idle
    }

    fun loadModels(refresh: Boolean = false) {
        if (_models.value is ModelsState.Loading) return
        _models.value = ModelsState.Loading
        viewModelScope.launch {
            _models.value = try {
                val list = catalog.models(refresh)
                if (list.isEmpty()) ModelsState.Failed("No models available") else ModelsState.Loaded(list)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                ModelsState.Failed(e.error.message)
            } catch (e: Exception) {
                ModelsState.Failed("Couldn't load models")
            }
            val loaded = _models.value as? ModelsState.Loaded ?: return@launch
            if (selectedSlug().isNullOrEmpty()) selectModel(loaded.models.first().slug)
        }
    }

    fun saveCustom(baseUrl: String, apiKey: String) {
        val url = baseUrl.trim()
        val key = apiKey.trim()
        val c = settings.config.value
        if (c.baseUrl != url || c.apiKey != key) settings.update { it.copy(baseUrl = url, apiKey = key) }
    }

    fun testCustom(baseUrl: String, apiKey: String) {
        saveCustom(baseUrl, apiKey)
        loadModels(refresh = true)
    }

    fun selectModel(slug: String) {
        if (settings.config.value.provider == ProviderChoice.CHATGPT) auth.selectModel(slug) else settings.update { it.copy(model = slug) }
    }

    fun selectedSlug(): String? = when (settings.config.value.provider) {
        ProviderChoice.CHATGPT -> (auth.status.value as? ChatGptStatus.Connected)?.model
        ProviderChoice.COMPATIBLE -> settings.config.value.model.ifEmpty { null }
    }

    private companion object {
        const val LISTENER_HEAD_START_MS = 150L
    }
}
