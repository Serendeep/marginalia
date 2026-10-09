package com.serendeep.marginalia.ai

import javax.inject.Inject
import javax.inject.Singleton

/** Model lists per provider, fetched once per session so every picker opens instantly. */
@Singleton
class ModelCatalog @Inject constructor(
    private val router: dagger.Lazy<AiRouter>,
    private val settings: AiSettings,
) {
    private val cache = HashMap<String, List<ChatModel>>()

    private fun key(): String = settings.config.value.let { it.provider.name + it.baseUrl }

    /** Models already fetched for the active provider; empty until the first load. */
    fun cached(): List<ChatModel> = synchronized(cache) { cache[key()] }.orEmpty()

    suspend fun models(refresh: Boolean = false): List<ChatModel> {
        val key = key()
        if (!refresh) synchronized(cache) { cache[key] }?.let { return it }
        return router.get().active().models().also { if (it.isNotEmpty()) synchronized(cache) { cache[key] = it } }
    }
}
