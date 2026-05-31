package io.github.androidpoet.kmpxmpp.websocket

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets

internal actual fun createPlatformWebSocketHttpClient(readTimeoutMillis: Long): HttpClient =
    HttpClient(CIO) {
        install(WebSockets)
        install(HttpTimeout) {
            connectTimeoutMillis = readTimeoutMillis
            socketTimeoutMillis = readTimeoutMillis
            requestTimeoutMillis = readTimeoutMillis
        }
    }
