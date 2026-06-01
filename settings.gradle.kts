pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kmpxmpp"

include(":kmpxmpp-core")
include(":kmpxmpp-xml")
include(":kmpxmpp-security")
include(":kmpxmpp-sasl")
include(":kmpxmpp-stream")
include(":kmpxmpp-transport")
include(":kmpxmpp-tcp")
include(":kmpxmpp-websocket")
include(":kmpxmpp-client")
include(":kmpxmpp-im")
include(":kmpxmpp-sm")
include(":kmpxmpp-bind")
include(":kmpxmpp-reconnect")
include(":kmpxmpp-xep-0184-receipts")
include(":kmpxmpp-xep-0280-carbons")
include(":kmpxmpp-xep-0333-chat-markers")
include(":kmpxmpp-xep-0085-chat-states")
include(":kmpxmpp-xep-0045-muc")
include(":kmpxmpp-xep-0249-direct-muc-invite")
include(":kmpxmpp-xep-0048-bookmarks")
include(":kmpxmpp-xep-0313-mam")
include(":kmpxmpp-xep-0030-disco")
include(":kmpxmpp-xep-0115-caps")
include(":kmpxmpp-xep-0059-rsm")
include(":kmpxmpp-xep-0297-forwarding")
include(":kmpxmpp-xep-0359-stanza-ids")
include(":kmpxmpp-xep-0363-http-upload")
include(":kmpxmpp-xep-0066-oob")
include(":kmpxmpp-xep-0234-jingle-file-transfer")
include(":kmpxmpp-xep-0264-thumbnails")
include(":kmpxmpp-xep-0357-push")
include(":kmpxmpp-xep-0352-csi")
include(":kmpxmpp-xep-0199-ping")
include(":kmpxmpp-xep-0319-idle")
include(":kmpxmpp-omemo-core")
include(":kmpxmpp-xep-0384-omemo")
include(":kmpxmpp-crypto-store")
include(":kmpxmpp-omemo-persistence-sqlite")
include(":kmpxmpp-xep-0133-admin")
include(":kmpxmpp-xep-0191-blocking")
include(":kmpxmpp-xep-0092-version")
include(":kmpxmpp-plugin-api")
include(":kmpxmpp-testkit")
include(":kmpxmpp-interop-tests")
include(":kmpxmpp-compliance")
include(":kmpxmpp-sample-whatsapp-jvm")
