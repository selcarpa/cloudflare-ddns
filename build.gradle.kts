import org.jetbrains.kotlin.gradle.plugin.mpp.*

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


kotlin {

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


    sourceSets {
        linuxX64 {
            dependencies {
                implementation("io.github.oshai:kotlin-logging-linuxx64:5.1.0")
            }
        }
//        linuxArm64 {
//            dependencies {
//                implementation("io.github.oshai:kotlin-logging-linuxarm64:5.1.0")
//            }
//        }

        commonMain {
            dependencies {
                implementation("io.ktor:ktor-client-core:$ktor_version")
                implementation("io.ktor:ktor-client-curl:$ktor_version")
                implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("com.squareup.okio:okio:$okio_version")
                implementation("net.mamoe.yamlkt:yamlkt:0.13.0")
                implementation("net.peanuuutz:tomlkt:0.2.0")
                implementation("io.ktor:ktor-client-logging:$ktor_version")
            }
        }

    }
}
