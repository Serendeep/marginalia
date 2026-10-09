package com.serendeep.marginalia.ai

import javax.inject.Inject
import javax.inject.Singleton

/** Model lists per provider, fetched once per session so every picker opens instantly. */
@Singleton
class ModelCatalog @Inject constructor(
    private val router: AiRouter,
    private val settings: AiSettings,
) {
    private val cache = HashMap<String, List<ChatModel>>()

    suspend fun models(refresh: Boolean = false): List<ChatModel> {
        val config = settings.config.value
        val key = config.provider.name + config.baseUrl
        if (!refresh) synchronized(cache) { cache[key] }?.let { return it }
        return router.active().models().also { if (it.isNotEmpty()) synchronized(cache) { cache[key] = it } }
    }
}
