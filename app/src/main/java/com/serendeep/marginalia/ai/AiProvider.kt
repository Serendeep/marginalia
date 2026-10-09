package com.serendeep.marginalia.ai

import android.content.Context
import com.serendeep.marginalia.shell.PREFS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface AiProvider {
    val name: String
    fun stream(request: AiRequest): Flow<AiEvent>
    suspend fun models(): List<ChatModel>
}

enum class ProviderChoice { CHATGPT, COMPATIBLE }

data class AiConfig(
    val provider: ProviderChoice = ProviderChoice.CHATGPT,
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
)

class AiSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(load())
    val config: StateFlow<AiConfig> = _config.asStateFlow()

    private val _tasks = MutableStateFlow(loadTasks())
    val tasks: StateFlow<Map<AiTask, TaskModel>> = _tasks.asStateFlow()

    @Synchronized
    fun setTask(task: AiTask, value: TaskModel) {
        prefs.edit().apply {
            if (value.model != null) putString(taskKey(task, "model"), value.model) else remove(taskKey(task, "model"))
            if (value.effort != null) putString(taskKey(task, "effort"), value.effort.name) else remove(taskKey(task, "effort"))
        }.apply()
        _tasks.value = _tasks.value + (task to value)
    }

    private fun loadTasks() = AiTask.entries.associateWith {
        TaskModel(
            model = prefs.getString(taskKey(it, "model"), null)?.takeIf(String::isNotEmpty),
            effort = Effort.parse(prefs.getString(taskKey(it, "effort"), null)),
        )
    }

    private fun taskKey(task: AiTask, field: String) = "ai_task_${task.name}_$field"

    @Synchronized
    fun update(transform: (AiConfig) -> AiConfig) {
        val next = transform(_config.value)
        prefs.edit()
            .putString(PROVIDER, next.provider.name)
            .putString(BASE_URL, next.baseUrl)
            .putString(MODEL, next.model)
            .putString(API_KEY, if (next.apiKey.isEmpty()) "" else SecretBox.encrypt(next.apiKey))
            .apply()
        _config.value = next
    }

    private fun load() = AiConfig(
        provider = prefs.getString(PROVIDER, null)?.let { runCatching { ProviderChoice.valueOf(it) }.getOrNull() }
            ?: ProviderChoice.CHATGPT,
        baseUrl = prefs.getString(BASE_URL, "").orEmpty(),
        model = prefs.getString(MODEL, "").orEmpty(),
        apiKey = prefs.getString(API_KEY, "")?.takeIf { it.isNotEmpty() }?.let(SecretBox::decrypt).orEmpty(),
    )

    private companion object {
        const val PROVIDER = "ai_provider"
        const val BASE_URL = "ai_base_url"
        const val MODEL = "ai_model"
        const val API_KEY = "ai_api_key"
    }
}

class ChatGptProvider(private val client: ResponsesClient) : AiProvider {
    override val name = "ChatGPT"
    override fun stream(request: AiRequest) = client.stream(request)
    override suspend fun models() = client.listModels()
}

class CompatibleProvider(private val client: OpenAiCompatibleClient) : AiProvider {
    override val name = "OpenAI-compatible"
    override fun stream(request: AiRequest) = client.stream(request)
    override suspend fun models() = client.listModels()
}

@Singleton
class AiRouter @Inject constructor(
    private val settings: AiSettings,
    responses: ResponsesClient,
    compatible: OpenAiCompatibleClient,
) {
    private val chatGpt = ChatGptProvider(responses)
    private val custom = CompatibleProvider(compatible)

    fun active(): AiProvider = when (settings.config.value.provider) {
        ProviderChoice.CHATGPT -> chatGpt
        ProviderChoice.COMPATIBLE -> custom
    }
}
