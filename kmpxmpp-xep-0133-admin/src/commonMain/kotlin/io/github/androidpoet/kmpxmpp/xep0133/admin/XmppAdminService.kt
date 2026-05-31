package io.github.androidpoet.kmpxmpp.xep0133.admin

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val ADHOC_COMMANDS_NAMESPACE: String = "http://jabber.org/protocol/commands"
private const val ADMIN_NODE_REGISTERED_USERS: String = "http://jabber.org/protocol/admin#get-registered-users-num"

public data class RegisteredUsersCountResult(
    val count: Int,
)

public interface XmppAdminService {
    public suspend fun requestRegisteredUsersCount(adminService: Jid, requestId: String): XmppResult<Unit>

    public fun parseRegisteredUsersCountResult(xml: String): XmppResult<RegisteredUsersCountResult>

    public fun validateRegisteredUsersCountRequest(xml: String): XmppResult<String>

    public fun validateRegisteredUsersCountResponse(xml: String, requestId: String): XmppResult<Unit>
}

public class DefaultXmppAdminService(
    private val client: KmpXmppClient,
) : XmppAdminService {

    override suspend fun requestRegisteredUsersCount(adminService: Jid, requestId: String): XmppResult<Unit> {
        if (adminService.domain.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Admin service JID domain cannot be blank.", XmppErrorStage.Messaging, false))
        }
        if (requestId.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Request id cannot be blank.", XmppErrorStage.Messaging, true))
        }

        val stanza = "<iq type='set' to='${escapeXml(adminService.toString())}' id='${escapeXml(requestId)}'><command xmlns='$ADHOC_COMMANDS_NAMESPACE' node='$ADMIN_NODE_REGISTERED_USERS' action='execute'/></iq>"
        return client.sendStanza(stanza)
    }

    override fun parseRegisteredUsersCountResult(xml: String): XmppResult<RegisteredUsersCountResult> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Admin response XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        val iqTag = tags.firstOrNull { it.name == "iq" }
        if (!iqTag?.attributes?.get("type").equals("result", ignoreCase = true)) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Admin response must be IQ type='result'.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val value = XmppXmlMiniParser.textForTag(xml, "value")
            ?.trim()
            ?.toIntOrNull()
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Admin response missing numeric <value> for registered users count.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        return XmppResult.Success(RegisteredUsersCountResult(count = value))
    }

    override fun validateRegisteredUsersCountRequest(xml: String): XmppResult<String> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Admin request XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        val iqTag = tags.firstOrNull { it.name == "iq" }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Admin request must contain <iq/>.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        if (!iqTag.attributes["type"].equals("set", ignoreCase = true)) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Admin request IQ must be type='set'.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val requestId = iqTag.attributes["id"]
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Admin request IQ missing id attribute.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val commandTag = tags.firstOrNull { it.name == "command" }
        if (commandTag?.attributes?.get("xmlns") != ADHOC_COMMANDS_NAMESPACE) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Admin request missing ad-hoc commands namespace.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        if (commandTag?.attributes?.get("node") != ADMIN_NODE_REGISTERED_USERS) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Admin request missing registered users command node.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        return XmppResult.Success(requestId)
    }

    override fun validateRegisteredUsersCountResponse(xml: String, requestId: String): XmppResult<Unit> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Admin response XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val iqTag = XmppXmlMiniParser.parseStartTags(xml).firstOrNull { it.name == "iq" }
        if (!iqTag?.attributes?.get("type").equals("result", ignoreCase = true)) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Admin response must be IQ type='result'.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        if (iqTag?.attributes?.get("id") != requestId) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Admin response id does not match request id.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        return XmppResult.Success(Unit)
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
