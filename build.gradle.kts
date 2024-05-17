val ktor_version: String by project
val kotlin_version: String by project
val okio_version: String by project
val kotlin_logging_version: String by project
val taskGroupName = "cf-ddns"
val templeReleasePath = "release1"

plugins {
    kotlin("multiplatform") version "1.9.24"
    id("io.ktor.plugin") version "3.0.0-beta-1"
    kotlin("plugin.serialization") version "1.9.23"
    application
    id("org.graalvm.buildtools.native") version "0.10.1"
}

group = "one.tain"
version = "1.38-SNAPSHOT"

graalvmNative{

}

repositories {
    mavenCentral()
    google()
}
application {
    mainClass.set("MainKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("io.ktor:ktor-client-logging:$ktor_version")
    implementation("com.squareup.okio:okio:$okio_version")
    implementation("net.peanuuutz.tomlkt:tomlkt:0.3.7")
    implementation("io.github.oshai:kotlin-logging:$kotlin_logging_version")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
    implementation("ch.qos.logback:logback-classic:1.4.1")
    implementation("io.ktor:ktor-client-okhttp-jvm:$ktor_version")
}


