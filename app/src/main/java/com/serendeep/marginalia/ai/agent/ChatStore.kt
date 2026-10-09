package com.serendeep.marginalia.ai.agent

import com.serendeep.marginalia.data.ChatDao
import com.serendeep.marginalia.data.ChatEntity
import com.serendeep.marginalia.data.ChatTurnEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

const val LIBRARY_SCOPE = "library"

/** A saved chat; [scope] is [LIBRARY_SCOPE] or the id of the lecture it was held in. */
data class ChatInfo(val id: String, val title: String, val scope: String, val createdAt: Long, val updatedAt: Long)

class StoredTurn(val idx: Int, val json: String)

/** Where chats live between runs. */
interface ChatStore {
    suspend fun save(chat: ChatInfo, idx: Int, turnJson: String)
    suspend fun get(id: String): ChatInfo?
    suspend fun latest(scope: String): ChatInfo?
    suspend fun turns(chatId: String): List<StoredTurn>

    /** Newest first; every scope when [scope] is null. */
    fun observe(scope: String?): Flow<List<ChatInfo>>
    suspend fun rename(id: String, title: String)
    suspend fun delete(id: String)
}

class RoomChatStore @Inject constructor(private val dao: ChatDao) : ChatStore {
    override suspend fun save(chat: ChatInfo, idx: Int, turnJson: String) =
        dao.save(chat.toEntity(), ChatTurnEntity(chat.id, idx, turnJson))

    override suspend fun get(id: String) = dao.chat(id)?.toInfo()
    override suspend fun latest(scope: String) = dao.latest(scope)?.toInfo()
    override suspend fun turns(chatId: String) = dao.turns(chatId).map { StoredTurn(it.idx, it.json) }
    override fun observe(scope: String?): Flow<List<ChatInfo>> =
        (if (scope == null) dao.observeAll() else dao.observeScope(scope)).map { rows -> rows.map { it.toInfo() } }

    override suspend fun rename(id: String, title: String) = dao.rename(id, title)
    override suspend fun delete(id: String) = dao.delete(id)
}

private fun ChatEntity.toInfo() = ChatInfo(id, title, scope, createdAt, updatedAt)
private fun ChatInfo.toEntity() = ChatEntity(id, title, scope, createdAt, updatedAt)

/** First user message, whitespace collapsed and cut to [max] characters. */
fun chatTitle(firstMessage: String, max: Int = 60): String {
    val clean = firstMessage.trim().replace(Regex("\\s+"), " ")
    return if (clean.length <= max) clean else clean.take(max - 1).trimEnd() + "…"
}
