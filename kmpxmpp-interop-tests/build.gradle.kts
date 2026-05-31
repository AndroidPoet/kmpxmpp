import io.github.androidpoet.xmpp.Configuration
import org.gradle.api.tasks.testing.Test

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

    sourceSets {
        commonMain.dependencies {
            api(project(":kmpxmpp-core"))
            api(project(":kmpxmpp-client"))
            api(project(":kmpxmpp-im"))
            api(project(":kmpxmpp-stream"))
            api(project(":kmpxmpp-transport"))
            api(project(":kmpxmpp-tcp"))
            api(project(":kmpxmpp-xep-0199-ping"))
            api(project(":kmpxmpp-xep-0184-receipts"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "io.github.androidpoet.kmpxmpp.modules.kmpxmpp_interop_tests"
    compileSdk = Configuration.COMPILE_SDK
    defaultConfig { minSdk = Configuration.MIN_SDK }
}

mavenPublishing {
    coordinates(Configuration.GROUP, "kmpxmpp-interop-tests", Configuration.VERSION)
}

val startProsodyE2e by tasks.registering(Exec::class) {
    group = "verification"
    description = "Starts Prosody Docker stack for KmpXMPP interop E2E tests."
    commandLine("bash", "${project.projectDir}/scripts/start-prosody-e2e.sh")
}

val stopProsodyE2e by tasks.registering(Exec::class) {
    group = "verification"
    description = "Stops Prosody Docker stack for KmpXMPP interop E2E tests."
    commandLine("bash", "${project.projectDir}/scripts/stop-prosody-e2e.sh")
}

tasks.register("jvmDockerE2e") {
    group = "verification"
    description = "Runs JVM interop E2E tests against Dockerized Prosody."
    dependsOn(startProsodyE2e)
    dependsOn("jvmTest")
    dependsOn(stopProsodyE2e)
}

tasks.named("jvmTest").configure {
    mustRunAfter(startProsodyE2e)
}

stopProsodyE2e.configure {
    mustRunAfter("jvmTest")
}

tasks.named<Test>("jvmTest").configure {
    environment("KMPXMPP_RUN_DOCKER_E2E", System.getenv("KMPXMPP_RUN_DOCKER_E2E") ?: "false")
}
