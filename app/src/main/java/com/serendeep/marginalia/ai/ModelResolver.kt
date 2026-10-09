package com.serendeep.marginalia.ai

import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedModel(val model: String?, val effort: Effort?)

/** Picks the model and reasoning effort for an action: explicit override, then the action's tier default, then the global model. */
@Singleton
class ModelResolver @Inject constructor(
    private val settings: AiSettings,
    private val auth: ChatGptAuth,
    private val catalog: ModelCatalog,
) {
    fun global(): String? = when (settings.config.value.provider) {
        ProviderChoice.CHATGPT -> auth.selectedModel()
        ProviderChoice.COMPATIBLE -> settings.config.value.model.ifBlank { null }
    }

    /** Loads the model list first when the action's tier default needs it and it isn't cached yet. */
    suspend fun resolve(task: AiTask): ResolvedModel {
        val override = settings.tasks.value[task] ?: TaskModel()
        if (override.model == null && task.fast && catalog.cached().isEmpty()) {
            try { catalog.models() } catch (_: Exception) { }
        }
        return resolve(task, override, global(), catalog.cached())
    }

    companion object {
        private val FAST_MARKERS = listOf("mini", "nano", "flash")

        private val AiTask.fast: Boolean get() = this == AiTask.AUTO_SORT

        fun resolve(task: AiTask, override: TaskModel, global: String?, models: List<ChatModel>): ResolvedModel {
            val model = override.model
                ?: if (task.fast) models.firstOrNull { m -> FAST_MARKERS.any { m.slug.contains(it, ignoreCase = true) } }?.slug else null
            val chosen = model ?: global
            // Non-reasoning models reject the parameter, so the default only applies where LOW is known to work.
            val supportsLow = models.firstOrNull { it.slug == chosen }?.efforts?.contains(Effort.LOW) == true
            return ResolvedModel(chosen, override.effort ?: if (task.fast && supportsLow) Effort.LOW else null)
        }
    }
}
