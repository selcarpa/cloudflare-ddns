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
version = "1.0-SNAPSHOT"

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
    jvm {}
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
                implementation("org.slf4j:slf4j-simple:1.7.36")
                implementation("io.ktor:ktor-client-logging-jvm:2.3.3")
            }
        }

    }
}
