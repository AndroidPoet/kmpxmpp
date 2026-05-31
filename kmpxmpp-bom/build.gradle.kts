import io.github.androidpoet.xmpp.Configuration

plugins {
    `java-platform`
    `maven-publish`
}

group = Configuration.GROUP
version = Configuration.VERSION

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api(project(":kmpxmpp-core"))
        api(project(":kmpxmpp-client"))
        api(project(":kmpxmpp-stream"))
        api(project(":kmpxmpp-transport"))
        api(project(":kmpxmpp-tcp"))
        api(project(":kmpxmpp-websocket"))
        api(project(":kmpxmpp-security"))
        api(project(":kmpxmpp-sasl"))
        api(project(":kmpxmpp-sm"))
        api(project(":kmpxmpp-im"))
        api(project(":kmpxmpp-xml"))
        api(project(":kmpxmpp-bind"))
        api(project(":kmpxmpp-plugin-api"))
        api(project(":kmpxmpp-crypto-store"))
        api(project(":kmpxmpp-compliance"))
        api(project(":kmpxmpp-omemo-core"))
        api(project(":kmpxmpp-omemo-persistence-sqlite"))
        api(project(":kmpxmpp-testkit"))
        api(project(":kmpxmpp-reconnect"))
        api(project(":kmpxmpp-xep-0030-disco"))
        api(project(":kmpxmpp-xep-0045-muc"))
        api(project(":kmpxmpp-xep-0048-bookmarks"))
        api(project(":kmpxmpp-xep-0059-rsm"))
        api(project(":kmpxmpp-xep-0066-oob"))
        api(project(":kmpxmpp-xep-0085-chat-states"))
        api(project(":kmpxmpp-xep-0092-version"))
        api(project(":kmpxmpp-xep-0115-caps"))
        api(project(":kmpxmpp-xep-0133-admin"))
        api(project(":kmpxmpp-xep-0184-receipts"))
        api(project(":kmpxmpp-xep-0191-blocking"))
        api(project(":kmpxmpp-xep-0199-ping"))
        api(project(":kmpxmpp-xep-0234-jingle-file-transfer"))
        api(project(":kmpxmpp-xep-0249-direct-muc-invite"))
        api(project(":kmpxmpp-xep-0264-thumbnails"))
        api(project(":kmpxmpp-xep-0280-carbons"))
        api(project(":kmpxmpp-xep-0297-forwarding"))
        api(project(":kmpxmpp-xep-0313-mam"))
        api(project(":kmpxmpp-xep-0319-idle"))
        api(project(":kmpxmpp-xep-0333-chat-markers"))
        api(project(":kmpxmpp-xep-0352-csi"))
        api(project(":kmpxmpp-xep-0357-push"))
        api(project(":kmpxmpp-xep-0359-stanza-ids"))
        api(project(":kmpxmpp-xep-0363-http-upload"))
        api(project(":kmpxmpp-xep-0384-omemo"))
    }
}

publishing {
    publications {
        create<MavenPublication>("bom") {
            from(components["javaPlatform"])
            artifactId = "kmpxmpp-bom"
        }
    }
}
