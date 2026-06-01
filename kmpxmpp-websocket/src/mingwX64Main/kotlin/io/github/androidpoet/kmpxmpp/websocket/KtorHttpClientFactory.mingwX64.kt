package io.github.androidpoet.kmpxmpp.websocket

import io.ktor.client.HttpClient
import io.ktor.client.engine.winhttp.WinHttp

internal actual fun createPlatformWebSocketHttpClient(readTimeoutMillis: Long): HttpClient =
    HttpClient(WinHttp)
