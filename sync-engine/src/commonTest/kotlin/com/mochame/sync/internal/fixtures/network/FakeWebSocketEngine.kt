package com.mochame.sync.internal.fixtures.network

import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.websocket.WebSocketCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext


class FakeWebSocketEngine(
    private val onConnect: (HttpRequestData) -> FakeWebSocketSession,
    parentContext: CoroutineContext,
    override val config: HttpClientEngineConfig = HttpClientEngineConfig()
) : HttpClientEngineBase("FakeWebSocketEngine") {

    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = setOf(WebSocketCapability)

    override val dispatcher: CoroutineDispatcher =
        parentContext[ContinuationInterceptor] as CoroutineDispatcher

    val handshakeRequests = Channel<HttpRequestData>(Channel.UNLIMITED)
    val sessionChannel = Channel<FakeWebSocketSession>(Channel.UNLIMITED)
    var failureOnConnect: Throwable? = null

    @InternalAPI
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        failureOnConnect?.let { throw it }
        handshakeRequests.send(data)
        val session = onConnect(data)
        sessionChannel.send(session)

        return HttpResponseData(
            statusCode = HttpStatusCode.SwitchingProtocols,
            requestTime = GMTDate(),
            headers = headersOf(
                HttpHeaders.Upgrade to listOf("websocket"),
                HttpHeaders.Connection to listOf("Upgrade")
            ),
            version = HttpProtocolVersion.HTTP_1_1,
            body = session,
            callContext = session.coroutineContext
        )
    }
}