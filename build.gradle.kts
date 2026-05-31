plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.vanniktech.publish) apply false
}

val productionReleasePreflight = tasks.register<Exec>("productionReleasePreflight") {
    group = "verification"
    description = "Validates production release prerequisites and repository gates."
    commandLine("bash", "${rootProject.projectDir}/scripts/release-preflight.sh")
}

val productionDockerInterop = tasks.register("productionDockerInterop") {
    group = "verification"
    description = "Runs JVM interop E2E tests against Dockerized Prosody."
    dependsOn(":kmpxmpp-interop-tests:jvmDockerE2e")
}

val productionJvmVerify = tasks.register("productionJvmVerify") {
    group = "verification"
    description = "Runs JVM compile and test verification across modules."
}

val productionMobileCompileVerify = tasks.register("productionMobileCompileVerify") {
    group = "verification"
    description = "Runs Android/iOS compile verification across modules."
}

val productionDockerSample = tasks.register<Exec>("productionDockerSample") {
    group = "verification"
    description = "Runs WhatsApp-style JVM sample against Dockerized Prosody backend."
    commandLine("bash", "${rootProject.projectDir}/kmpxmpp-sample-whatsapp-jvm/scripts/run-whatsapp-docker-sample.sh")
    mustRunAfter(productionDockerInterop)
}

tasks.register("productionVerify") {
    group = "verification"
    description = "Runs production verification gates (tests, Docker E2E, preflight)."
    dependsOn(productionJvmVerify)
    dependsOn(productionMobileCompileVerify)
    dependsOn(productionDockerInterop)
    dependsOn(productionDockerSample)
    dependsOn(productionReleasePreflight)
}

productionDockerInterop.configure {
    mustRunAfter(productionJvmVerify)
}

productionReleasePreflight.configure {
    mustRunAfter(productionDockerSample)
}

gradle.projectsEvaluated {
    val compileTasks = subprojects
        .map { "${it.path}:compileKotlinJvm" }
        .filter { path -> rootProject.findProject(path.substringBeforeLast(":"))?.tasks?.findByName("compileKotlinJvm") != null }

    val jvmTestTasks = subprojects
        .map { "${it.path}:jvmTest" }
        .filter { path -> rootProject.findProject(path.substringBeforeLast(":"))?.tasks?.findByName("jvmTest") != null }

    productionJvmVerify.configure {
        dependsOn(compileTasks)
        dependsOn(jvmTestTasks)
    }

    val androidCompileTasks = subprojects
        .flatMap { project ->
            listOf(
                "${project.path}:compileDebugKotlinAndroid",
                "${project.path}:compileReleaseKotlinAndroid",
            )
        }
        .filter { path -> rootProject.findProject(path.substringBeforeLast(":"))?.tasks?.findByName(path.substringAfterLast(":")) != null }

    val iosCompileTasks = subprojects
        .flatMap { project ->
            listOf(
                "${project.path}:compileKotlinIosArm64",
                "${project.path}:compileKotlinIosSimulatorArm64",
            )
        }
        .filter { path -> rootProject.findProject(path.substringBeforeLast(":"))?.tasks?.findByName(path.substringAfterLast(":")) != null }

    productionMobileCompileVerify.configure {
        dependsOn(androidCompileTasks)
        dependsOn(iosCompileTasks)
    }
}
