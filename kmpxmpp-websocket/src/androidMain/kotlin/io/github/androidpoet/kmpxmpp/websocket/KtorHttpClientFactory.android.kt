package io.github.androidpoet.kmpxmpp.websocket

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets

internal actual fun createPlatformWebSocketHttpClient(readTimeoutMillis: Long): HttpClient =
    HttpClient(OkHttp) {
        install(WebSockets)
        install(HttpTimeout) {
            connectTimeoutMillis = readTimeoutMillis
            socketTimeoutMillis = readTimeoutMillis
            requestTimeoutMillis = readTimeoutMillis
        }
    }
