import io.github.androidpoet.xmpp.Configuration

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.vanniktech.publish)
}

kotlin {
    explicitApi()
    jvmToolchain(17)

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":kmpxmpp-core"))
            api(project(":kmpxmpp-transport"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    coordinates(Configuration.GROUP, "kmpxmpp-tcp", Configuration.VERSION)
}
