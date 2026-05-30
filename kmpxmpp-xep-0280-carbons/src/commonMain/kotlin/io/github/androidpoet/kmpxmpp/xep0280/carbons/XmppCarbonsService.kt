package io.github.androidpoet.kmpxmpp.xep0280.carbons

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.XmppResult

private const val CARBONS_NAMESPACE: String = "urn:xmpp:carbons:2"

public interface XmppCarbonsService {
    public suspend fun enableCarbons(): XmppResult<Unit>

    public suspend fun disableCarbons(): XmppResult<Unit>
}

public class DefaultXmppCarbonsService(
    private val client: KmpXmppClient,
) : XmppCarbonsService {

    override suspend fun enableCarbons(): XmppResult<Unit> =
        client.sendStanza("<iq type='set' id='carbons-enable'><enable xmlns='$CARBONS_NAMESPACE'/></iq>")

    override suspend fun disableCarbons(): XmppResult<Unit> =
        client.sendStanza("<iq type='set' id='carbons-disable'><disable xmlns='$CARBONS_NAMESPACE'/></iq>")
}
