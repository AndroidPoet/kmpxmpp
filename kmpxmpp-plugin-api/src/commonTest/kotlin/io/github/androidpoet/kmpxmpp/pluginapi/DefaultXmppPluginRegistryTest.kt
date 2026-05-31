package io.github.androidpoet.kmpxmpp.pluginapi

import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppPluginRegistryTest {
    private val context = XmppPluginContext(userJid = null, serverDomain = "example.com")

    @Test
    fun test_registry_applyOutbound_ordersPluginsByOrderThenKey() = runTest {
        val registry = DefaultXmppPluginRegistry()
        registry.register(EchoPlugin("b", order = 10, outboundSuffix = "|b"))
        registry.register(EchoPlugin("a", order = 10, outboundSuffix = "|a"))
        registry.register(EchoPlugin("c", order = 5, outboundSuffix = "|c"))

        val result = registry.applyOutbound(context, "start")
        val success = assertIs<XmppResult.Success<String>>(result)
        assertEquals("start|c|a|b", success.value)
    }

    @Test
    fun test_registry_runBeforeConnect_whenPluginFails_shortCircuitsPipeline() = runTest {
        val registry = DefaultXmppPluginRegistry()
        val calls = mutableListOf<String>()
        registry.register(
            object : XmppPlugin {
                override val key: String = "first"
                override suspend fun onBeforeConnect(context: XmppPluginContext): XmppResult<Unit> {
                    calls += key
                    return XmppResult.Failure(
                        xmppErrorInvalidState(
                            message = "boom",
                            stage = io.github.androidpoet.kmpxmpp.core.XmppErrorStage.Connect,
                        ),
                    )
                }
            },
        )
        registry.register(
            object : XmppPlugin {
                override val key: String = "second"
                override suspend fun onBeforeConnect(context: XmppPluginContext): XmppResult<Unit> {
                    calls += key
                    return XmppResult.Success(Unit)
                }
            },
        )

        val result = registry.runBeforeConnect(context)
        val failure = assertIs<XmppResult.Failure>(result)
        assertEquals("boom", failure.error.message)
        assertEquals(listOf("first"), calls)
    }

    @Test
    fun test_registry_applyInbound_whenPluginFails_returnsFailure() = runTest {
        val registry = DefaultXmppPluginRegistry()
        registry.register(EchoPlugin("mutate", inboundSuffix = "|ok"))
        registry.register(
            object : XmppPlugin {
                override val key: String = "fail"
                override suspend fun onInboundStanza(context: XmppPluginContext, stanzaXml: String): XmppResult<String> =
                    XmppResult.Failure(
                        xmppErrorInvalidState(
                            message = "blocked",
                            stage = io.github.androidpoet.kmpxmpp.core.XmppErrorStage.Messaging,
                        ),
                    )
            },
        )

        val result = registry.applyInbound(context, "x")
        val failure = assertIs<XmppResult.Failure>(result)
        assertEquals("blocked", failure.error.message)
    }

    @Test
    fun test_registry_unregister_whenExisting_removesPlugin() = runTest {
        val registry = DefaultXmppPluginRegistry()
        registry.register(EchoPlugin("one"))
        val removed = registry.unregister("one")
        val removedSuccess = assertIs<XmppResult.Success<Boolean>>(removed)
        assertTrue(removedSuccess.value)
        assertEquals(0, registry.list().size)
    }
}

private class EchoPlugin(
    override val key: String,
    override val order: Int = 0,
    private val outboundSuffix: String = "",
    private val inboundSuffix: String = "",
) : XmppPlugin {
    override suspend fun onOutboundStanza(context: XmppPluginContext, stanzaXml: String): XmppResult<String> =
        XmppResult.Success(stanzaXml + outboundSuffix)

    override suspend fun onInboundStanza(context: XmppPluginContext, stanzaXml: String): XmppResult<String> =
        XmppResult.Success(stanzaXml + inboundSuffix)
}
