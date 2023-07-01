package com.bso112.roleplayai.android.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bso112.data.local.AppPreference
import com.bso112.domain.Chat
import com.bso112.domain.ChatRepository
import com.bso112.domain.Profile
import com.bso112.domain.ProfileRepository
import com.bso112.domain.Role
import com.bso112.domain.createChat
import com.bso112.domain.toChatLog
import com.bso112.domain.toSystemChat
import com.bso112.roleplayai.android.util.DispatcherProvider
import com.bso112.roleplayai.android.util.Empty
import com.bso112.roleplayai.android.util.randomID
import com.bso112.roleplayai.android.util.stateIn
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val profileRepository: ProfileRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val appPreference: AppPreference,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val argument = ChatScreenArg(savedStateHandle)
    val user: StateFlow<Profile> = stateIn(profileRepository.getUser(), Profile.Empty)
    val opponent: StateFlow<Profile> =
        stateIn(profileRepository.getProfile(argument.profileId), Profile.Empty)

    private val logId: String = (argument.chatLogId ?: randomID)

    val chatList: StateFlow<List<Chat>> = stateIn(chatRepository.getAllChat(logId))

    val userInput = MutableStateFlow("")

    private val coroutineContext =
        dispatcherProvider.io + CoroutineExceptionHandler { _, throwable -> throwable.printStackTrace() }

    fun sendChat(message: String) {
        viewModelScope.launch(coroutineContext) {
            val userChat = checkNotNull(user.value).createChat(message, logId, Role.User)
            chatRepository.saveChat(userChat)
            chatRepository.saveChatLog(userChat.toChatLog(opponentId = opponent.value.id))

            val requestChatList: List<Chat> = buildList {
                appPreference.mainPrompt.getValue().toSystemChat(
                    userName = user.value.name,
                    charName = opponent.value.name
                ).also(::add)

                appPreference.characterPrompt.getValue().plus(opponent.value.description)
                    .toSystemChat(
                        userName = user.value.name,
                        charName = opponent.value.name
                    ).also(::add)

                addAll(chatList.value)
            }

            val chat = chatRepository.sendChat(
                speaker = checkNotNull(opponent.value),
                messages = requestChatList,
                logId = logId
            ).first()

            chatRepository.saveChat(chat = chat)
            chatRepository.saveChatLog(chat.toChatLog())
        }
        userInput.update { String.Empty }
    }
}
