@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import io.github.androidpoet.xmpp.Configuration

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
}

kotlin {
    explicitApi()
    jvmToolchain(17)

    androidTarget { publishLibraryVariants("release") }
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()
    linuxX64()
    mingwX64()
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(project(":kmpxmpp-core"))
            api(project(":kmpxmpp-transport"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        macosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        linuxX64Main.dependencies {
            implementation(libs.ktor.client.curl)
        }
        mingwX64Main.dependencies {
            implementation(libs.ktor.client.winhttp)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "io.github.androidpoet.kmpxmpp.websocket"
    compileSdk = Configuration.COMPILE_SDK
    defaultConfig { minSdk = Configuration.MIN_SDK }
}

mavenPublishing {
    coordinates(Configuration.GROUP, "kmpxmpp-websocket", Configuration.VERSION)
}
