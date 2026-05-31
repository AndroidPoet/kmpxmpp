plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":kmpxmpp-core"))
    implementation(project(":kmpxmpp-client"))
    implementation(project(":kmpxmpp-im"))
    implementation(project(":kmpxmpp-stream"))
    implementation(project(":kmpxmpp-tcp"))
    implementation(project(":kmpxmpp-xep-0085-chat-states"))
    implementation(project(":kmpxmpp-xep-0184-receipts"))
    implementation(project(":kmpxmpp-xep-0333-chat-markers"))
    implementation(project(":kmpxmpp-xep-0249-direct-muc-invite"))
    implementation(project(":kmpxmpp-xep-0363-http-upload"))
    implementation(project(":kmpxmpp-xep-0066-oob"))
    implementation(project(":kmpxmpp-xep-0045-muc"))
    implementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass.set("io.github.androidpoet.kmpxmpp.sample.whatsapp.MainKt")
}
