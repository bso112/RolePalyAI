package com.bso112.data.repository

import com.bso112.data.alsoSuspend
import com.bso112.data.local.datasource.ChatLocalDataSource
import com.bso112.data.local.entity.ChatEntity
import com.bso112.data.local.entity.ChatLogEntity
import com.bso112.data.local.entity.toDomain
import com.bso112.data.local.entity.toEntity
import com.bso112.data.remote.datasource.ChatRemoteDataSource
import com.bso112.data.remote.response.toDomain
import com.bso112.data.remote.toApiModel
import com.bso112.domain.Chat
import com.bso112.domain.ChatLog
import com.bso112.domain.ChatRepository
import com.bso112.domain.DataChangedEvent
import com.bso112.domain.LanguageCode
import com.bso112.domain.Profile
import com.bso112.domain.autoRefreshFlow
import com.bso112.domain.toChatLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow

class ChatRepositoryImpl(
    private val chatRemoteDataSource: ChatRemoteDataSource,
    private val chatLocalDataSource: ChatLocalDataSource,
) : ChatRepository {
    private val _dataChangedEvent = MutableSharedFlow<DataChangedEvent<*>>()
    override val dataChangedEvent: SharedFlow<DataChangedEvent<*>> =
        _dataChangedEvent.asSharedFlow()

    object ChatListChanged : DataChangedEvent<Chat>
    object ChatLogChanged : DataChangedEvent<ChatLog>

    override suspend fun saveChat(
        chat: Chat
    ) {
        chatLocalDataSource.saveChatList(listOf(chat.toEntity()))
        _dataChangedEvent.emit(ChatListChanged)
    }

    override fun translateWithGoogle(
        message: String,
        sourceLanguageCode: LanguageCode,
        targetLanguageCode: LanguageCode
    ): Flow<String> = flow {
        chatRemoteDataSource.translateWithGoogle(
            listOf(message),
            sourceLanguageCode.value,
            targetLanguageCode.value
        ).alsoSuspend {
            if (it.translatedText.isEmpty()) return@alsoSuspend
            emit(it.translatedText.first())
        }
    }

    override fun translateWithGPT(message: String): Flow<String> = flow {
        chatRemoteDataSource.translateWithGPT(message).alsoSuspend {
            if (it.isNullOrEmpty()) return@alsoSuspend
            emit(it)
        }
    }

    override suspend fun saveChatLog(chatLog: ChatLog) {
        chatLocalDataSource.saveChatLog(chatLog.toEntity())
        _dataChangedEvent.emit(ChatLogChanged)
    }

    override suspend fun saveChatList(chatList: List<Chat>, opponentId : String) {
        if (chatList.isEmpty()) return
        chatLocalDataSource.saveChatList(chatList.map(Chat::toEntity))
        chatLocalDataSource.saveChatLog(chatList.last().toChatLog(opponentId).toEntity())
        _dataChangedEvent.emit(ChatListChanged)
        _dataChangedEvent.emit(ChatLogChanged)
    }

    override fun sendChat(
        speaker: Profile,
        messages: List<Chat>,
        logId: String
    ): Flow<Chat> = flow {
        chatRemoteDataSource.sendChat(messages.map(Chat::toApiModel))
            .toDomain(speaker, logId).alsoSuspend(::emit)
    }

    override fun getAllChat(
        logId: String
    ): Flow<List<Chat>> = autoRefreshFlow {
        chatLocalDataSource.getAllChat(logId).map(ChatEntity::toDomain)
    }

    override fun getChatLog(): Flow<List<ChatLog>> = autoRefreshFlow {
        chatLocalDataSource.getChatLog().map(ChatLogEntity::toDomain)
    }

    override suspend fun deleteChatLog(chatLog: ChatLog) {
        chatLocalDataSource.deleteChatLog(chatLog.toEntity())
        _dataChangedEvent.emit(ChatLogChanged)
    }

    override suspend fun deleteChatLogByProfileId(profileId: String) {
        chatLocalDataSource.deleteByProfileId(profileId)
        _dataChangedEvent.emit(ChatLogChanged)
    }
}

