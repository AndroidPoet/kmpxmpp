package io.github.androidpoet.kmpxmpp.stream

public enum class XmppStreamState {
    Disconnected,
    Connected,
    StreamOpened,
    FeaturesReceived,
    TlsUpgraded,
    Authenticated,
    Bound,
    Ready,
}
