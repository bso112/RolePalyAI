package com.bso112.data.remote.datasource

import com.bso112.data.BuildConfig
import com.bso112.data.remote.KtorClient
import com.bso112.data.remote.Message
import com.bso112.data.remote.Role
import com.bso112.data.remote.model.ChatApiModel
import com.bso112.data.remote.request.ChatRequest
import com.bso112.data.remote.request.Model
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType


class ChatRemoteDataSource(
    private val client: HttpClient = KtorClient.httpClient
) {

    suspend fun sendChat(message: String): ChatApiModel {
        val url = "https://api.openai.com/v1/chat/completions"
        val apiKey = BuildConfig.CHAT_GPT_API_KEY
        val body = ChatRequest(
            model = Model.GPT_3_5.alias,
            messages = listOf(
                Message(
                    role = Role.User.alias,
                    content = message
                )
            ),
            temperature = 0.8f
        )

        return client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(body)
            headers {
                append(HttpHeaders.Authorization, "Bearer $apiKey")
            }
        }.body()
    }
}