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
        api(project(":kmpxmpp-omemo-core"))
        api(project(":kmpxmpp-omemo-persistence-sqlite"))
        api(project(":kmpxmpp-xep-0048-bookmarks"))
        api(project(":kmpxmpp-xep-0133-admin"))
        api(project(":kmpxmpp-xep-0234-jingle-file-transfer"))
        api(project(":kmpxmpp-xep-0264-thumbnails"))
        api(project(":kmpxmpp-xep-0357-push"))
        api(project(":kmpxmpp-xep-0359-stanza-ids"))
        api(project(":kmpxmpp-xep-0384-omemo"))
    }
}

publishing {
    publications {
        create<MavenPublication>("bomNonBaseline") {
            from(components["javaPlatform"])
            artifactId = "kmpxmpp-bom-nonbaseline"
        }
    }
}
