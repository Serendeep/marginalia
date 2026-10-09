package com.serendeep.marginalia.ai.agent

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.TreeMap

class FakeChatStore : ChatStore {
    private val chats = MutableStateFlow<Map<String, ChatInfo>>(emptyMap())
    private val rows = mutableMapOf<String, TreeMap<Int, String>>()

    override suspend fun save(chat: ChatInfo, idx: Int, turnJson: String) {
        chats.value = chats.value + (chat.id to chat)
        rows.getOrPut(chat.id) { TreeMap() }[idx] = turnJson
    }

    override suspend fun get(id: String) = chats.value[id]
    override suspend fun latest(scope: String) = chats.value.values.filter { it.scope == scope }.maxByOrNull { it.updatedAt }
    override suspend fun turns(chatId: String) = rows[chatId].orEmpty().map { StoredTurn(it.key, it.value) }
    override fun observe(scope: String?): Flow<List<ChatInfo>> =
        chats.map { all -> all.values.filter { scope == null || it.scope == scope }.sortedByDescending { it.updatedAt } }

    override suspend fun rename(id: String, title: String) {
        chats.value[id]?.let { chats.value = chats.value + (id to it.copy(title = title)) }
    }

    override suspend fun delete(id: String) {
        chats.value = chats.value - id
        rows.remove(id)
    }
}
