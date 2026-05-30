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
include(":kmpxmpp-client")
include(":kmpxmpp-security")
include(":kmpxmpp-sasl")
include(":kmpxmpp-stream")
include(":kmpxmpp-transport")
include(":kmpxmpp-tcp")
include(":kmpxmpp-websocket")
