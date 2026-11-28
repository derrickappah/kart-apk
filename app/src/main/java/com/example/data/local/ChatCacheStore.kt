package com.example.data.local

import com.example.domain.model.EnrichedConversation
import com.example.domain.model.MessageRow
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

/**
 * Lightweight persistent JSON disk cache for chat conversations and message threads.
 * Lets the inbox and threads render cached messages immediately on open while
 * fetching only newer messages from Supabase.
 */
class ChatCacheStore(private val cacheDir: File) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val conversationsAdapter: JsonAdapter<List<EnrichedConversation>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, EnrichedConversation::class.java)
    )

    private val messagesAdapter: JsonAdapter<List<MessageRow>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, MessageRow::class.java)
    )

    fun loadConversations(userId: String): List<EnrichedConversation> {
        if (userId.isBlank()) return emptyList()
        val file = File(cacheDir, "conversations_${safeId(userId)}.json")
        if (!file.exists()) return emptyList()
        return runCatching {
            conversationsAdapter.fromJson(file.readText()).orEmpty()
        }.getOrDefault(emptyList())
    }

    fun saveConversations(userId: String, conversations: List<EnrichedConversation>) {
        if (userId.isBlank()) return
        val capped = conversations.take(MAX_CACHED_CONVERSATIONS)
        val file = File(cacheDir, "conversations_${safeId(userId)}.json")
        writeAtomically(file, conversationsAdapter.toJson(capped))
    }

    fun loadMessages(userId: String, conversationId: String): List<MessageRow> {
        if (userId.isBlank() || conversationId.isBlank()) return emptyList()
        val file = File(cacheDir, "messages_${safeId(userId)}_${safeId(conversationId)}.json")
        if (!file.exists()) return emptyList()
        return runCatching {
            messagesAdapter.fromJson(file.readText()).orEmpty()
        }.getOrDefault(emptyList())
    }

    fun saveMessages(userId: String, conversationId: String, messages: List<MessageRow>) {
        if (userId.isBlank() || conversationId.isBlank()) return
        val persisted = messages
            .filter { it.id?.startsWith("local_") != true }
            .takeLast(MAX_CACHED_MESSAGES)
        val file = File(cacheDir, "messages_${safeId(userId)}_${safeId(conversationId)}.json")
        writeAtomically(file, messagesAdapter.toJson(persisted))
    }

    fun clearAll() {
        runCatching {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun writeAtomically(target: File, json: String) {
        runCatching {
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val tmp = File(cacheDir, "${target.name}.tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(target)) {
                target.writeText(json)
                tmp.delete()
            }
        }
    }

    private fun safeId(raw: String): String = raw.replace(Regex("[^a-zA-Z0-9_-]"), "_")

    companion object {
        private const val MAX_CACHED_CONVERSATIONS = 100
        private const val MAX_CACHED_MESSAGES = 300
    }
}
