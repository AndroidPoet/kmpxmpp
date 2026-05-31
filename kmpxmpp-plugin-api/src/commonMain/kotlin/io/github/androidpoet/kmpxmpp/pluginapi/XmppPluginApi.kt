package io.github.androidpoet.kmpxmpp.pluginapi

import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppResultOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public data class XmppPluginContext(
    val userJid: Jid?,
    val serverDomain: String,
    val metadata: Map<String, String> = emptyMap(),
)

public interface XmppPlugin {
    public val key: String
    public val order: Int get() = 0

    public suspend fun onBeforeConnect(context: XmppPluginContext): XmppResult<Unit> = XmppResult.Success(Unit)

    public suspend fun onAfterAuthenticate(context: XmppPluginContext): XmppResult<Unit> = XmppResult.Success(Unit)

    public suspend fun onBeforeDisconnect(context: XmppPluginContext): XmppResult<Unit> = XmppResult.Success(Unit)

    public suspend fun onOutboundStanza(context: XmppPluginContext, stanzaXml: String): XmppResult<String> =
        XmppResult.Success(stanzaXml)

    public suspend fun onInboundStanza(context: XmppPluginContext, stanzaXml: String): XmppResult<String> =
        XmppResult.Success(stanzaXml)
}

public interface XmppPluginRegistry {
    public suspend fun register(plugin: XmppPlugin): XmppResult<Unit>
    public suspend fun unregister(key: String): XmppResult<Boolean>
    public suspend fun list(): List<XmppPlugin>

    public suspend fun runBeforeConnect(context: XmppPluginContext): XmppResult<Unit>
    public suspend fun runAfterAuthenticate(context: XmppPluginContext): XmppResult<Unit>
    public suspend fun runBeforeDisconnect(context: XmppPluginContext): XmppResult<Unit>

    public suspend fun applyOutbound(context: XmppPluginContext, stanzaXml: String): XmppResult<String>
    public suspend fun applyInbound(context: XmppPluginContext, stanzaXml: String): XmppResult<String>
}

public class DefaultXmppPluginRegistry : XmppPluginRegistry {
    private val mutex = Mutex()
    private val plugins = linkedMapOf<String, XmppPlugin>()

    override suspend fun register(plugin: XmppPlugin): XmppResult<Unit> = xmppResultOf {
        require(plugin.key.isNotBlank()) { "Plugin key cannot be blank." }
        mutex.withLock {
            require(!plugins.containsKey(plugin.key)) { "Plugin already registered: ${plugin.key}" }
            plugins[plugin.key] = plugin
        }
    }

    override suspend fun unregister(key: String): XmppResult<Boolean> = xmppResultOf {
        require(key.isNotBlank()) { "Plugin key cannot be blank." }
        mutex.withLock { plugins.remove(key) != null }
    }

    override suspend fun list(): List<XmppPlugin> = mutex.withLock {
        orderedLocked()
    }

    override suspend fun runBeforeConnect(context: XmppPluginContext): XmppResult<Unit> =
        runLifecycle(context) { plugin -> plugin.onBeforeConnect(context) }

    override suspend fun runAfterAuthenticate(context: XmppPluginContext): XmppResult<Unit> =
        runLifecycle(context) { plugin -> plugin.onAfterAuthenticate(context) }

    override suspend fun runBeforeDisconnect(context: XmppPluginContext): XmppResult<Unit> =
        runLifecycle(context) { plugin -> plugin.onBeforeDisconnect(context) }

    override suspend fun applyOutbound(context: XmppPluginContext, stanzaXml: String): XmppResult<String> =
        runStanzaPipeline(context, stanzaXml) { plugin, current -> plugin.onOutboundStanza(context, current) }

    override suspend fun applyInbound(context: XmppPluginContext, stanzaXml: String): XmppResult<String> =
        runStanzaPipeline(context, stanzaXml) { plugin, current -> plugin.onInboundStanza(context, current) }

    private suspend fun runLifecycle(
        context: XmppPluginContext,
        block: suspend (XmppPlugin) -> XmppResult<Unit>,
    ): XmppResult<Unit> {
        val snapshot = list()
        for (plugin in snapshot) {
            when (val result = block(plugin)) {
                is XmppResult.Success -> Unit
                is XmppResult.Failure -> return result
            }
        }
        return XmppResult.Success(Unit)
    }

    private suspend fun runStanzaPipeline(
        context: XmppPluginContext,
        stanzaXml: String,
        block: suspend (XmppPlugin, String) -> XmppResult<String>,
    ): XmppResult<String> {
        val snapshot = list()
        var current = stanzaXml
        for (plugin in snapshot) {
            when (val result = block(plugin, current)) {
                is XmppResult.Success -> current = result.value
                is XmppResult.Failure -> return result
            }
        }
        return XmppResult.Success(current)
    }

    private fun orderedLocked(): List<XmppPlugin> = plugins.values.sortedWith(
        compareBy<XmppPlugin> { it.order }.thenBy { it.key },
    )
}
