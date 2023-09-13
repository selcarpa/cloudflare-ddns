import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.gradle.*

val ktor_version: String by project
val kotlin_version: String by project
val okio_version: String by project

plugins {
    kotlin("multiplatform") version "1.9.10"
    id("io.ktor.plugin") version "2.3.3"
    kotlin("plugin.serialization") version "1.9.0"
}

group = "one.tain"
version = "1.3-SNAPSHOT"

repositories {
    mavenCentral()
    google()
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    targetHierarchy.default()
    fun KotlinNativeTarget.config(custom: Executable.() -> Unit = {}) {
        binaries {
            executable {
                entryPoint = "main"
                custom()
            }
        }
    }

    linuxX64 {
        config()
    }
//    linuxArm64 {
//        config()
//    }
    jvm {
        withJava()
        @kotlin.Suppress("UNUSED_VARIABLE")
        val jvmJar by tasks.getting(org.gradle.jvm.tasks.Jar::class) {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            doFirst {
                manifest {
                    attributes["Main-Class"] = "MainKt"
                }
                from(configurations.getByName("runtimeClasspath").map { if (it.isDirectory) it else zipTree(it) })
            }
        }
    }
    @kotlin.Suppress("UNUSED_VARIABLE")
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-core:$ktor_version")
                implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("com.squareup.okio:okio:$okio_version")
                implementation("net.mamoe.yamlkt:yamlkt:0.13.0")
                implementation("net.peanuuutz:tomlkt:0.2.0")
            }
        }

        val linuxX64Main by getting {
            dependencies {
                implementation("io.github.oshai:kotlin-logging-linuxx64:5.1.0")
                implementation("io.ktor:ktor-client-curl:$ktor_version")
                implementation("io.ktor:ktor-client-logging:$ktor_version")
            }
        }

//        val linuxArm64Main by getting {
//            dependencies {
//                implementation("io.github.oshai:kotlin-logging-linuxarm64:5.1.0")
//                implementation("io.ktor:ktor-client-curl:$ktor_version")
//                implementation("io.ktor:ktor-client-logging:$ktor_version")
//            }
//        }
        val jvmMain by getting{
            dependencies{
                implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")
                implementation("io.ktor:ktor-client-cio:$ktor_version")
                implementation("ch.qos.logback:logback-classic:1.4.7")
                implementation("io.ktor:ktor-client-logging-jvm:2.3.3")
            }
        }

    }
}
