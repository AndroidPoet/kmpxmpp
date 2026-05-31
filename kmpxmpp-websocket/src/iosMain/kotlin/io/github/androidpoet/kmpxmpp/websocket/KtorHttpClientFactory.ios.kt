package io.github.androidpoet.kmpxmpp.websocket

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets

internal actual fun createPlatformWebSocketHttpClient(readTimeoutMillis: Long): HttpClient =
    HttpClient(Darwin) {
        install(WebSockets)
        install(HttpTimeout) {
            connectTimeoutMillis = readTimeoutMillis
            socketTimeoutMillis = readTimeoutMillis
            requestTimeoutMillis = readTimeoutMillis
        }
    }
