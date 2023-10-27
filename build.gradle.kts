import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.Executable
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

val ktor_version: String by project
val kotlin_version: String by project
val okio_version: String by project
val kotlin_logging_version: String by project

plugins {
    kotlin("multiplatform") version "1.9.10"
    id("io.ktor.plugin") version "2.3.5"
    kotlin("plugin.serialization") version "1.9.0"
}

group = "one.tain"
version = "1.11-SNAPSHOT"

repositories {
    mavenCentral()
    google()
}

kotlin.targets.withType<KotlinNativeTarget> {
    binaries.all {
        freeCompilerArgs += "-Xdisable-phases=EscapeAnalysis"
    }
}

@OptIn(ExperimentalKotlinGradlePluginApi::class) kotlin {
    targetHierarchy.default()
    fun KotlinNativeTarget.config(custom: Executable.() -> Unit = {}, targetName: String) {
        binaries {
            executable {
                entryPoint = "main"
                custom()
                baseName = "${rootProject.name}-${targetName}-${version}"

            }
        }
    }

    linuxX64 {
        config(targetName = "linux-x64")
    }
    linuxArm64 {
        config(targetName = "linux-arm64")
    }
    mingwX64 {
        config(targetName = "windows-x64")
    }
//    macosArm64 {
//        config(targetName = "macos-arm64")
//    }
//    macosX64 {
//        config(targetName = "macos-x64")
//    }
    jvm {
        withJava()
        @Suppress("UNUSED_VARIABLE") val jvmJar by tasks.getting(org.gradle.jvm.tasks.Jar::class) {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            doFirst {
                manifest {
                    attributes["Main-Class"] = "MainKt"
                }
                from(configurations.getByName("runtimeClasspath").map { if (it.isDirectory) it else zipTree(it) })
            }
        }
    }
    @Suppress("UNUSED_VARIABLE") sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-core:$ktor_version")
                implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("com.squareup.okio:okio:$okio_version")
                implementation("net.mamoe.yamlkt:yamlkt:0.13.0")
                implementation("net.peanuuutz.tomlkt:tomlkt:0.3.7")
                implementation("io.github.oshai:kotlin-logging:$kotlin_logging_version")
                implementation("io.ktor:ktor-client-logging:$ktor_version")
            }
        }

        val nativeMain by getting {
            dependencies {}
        }

        val linuxX64Main by getting {
            dependencies {
                implementation("io.ktor:ktor-client-curl:$ktor_version")
            }
        }

        val mingwX64Main by getting {
            dependencies {
                implementation("io.ktor:ktor-client-winhttp:$ktor_version")
            }
        }

        val linuxArm64Main by getting {
            dependencies {
                implementation("io.ktor:ktor-client-cio:$ktor_version")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-cio:$ktor_version")
                implementation("ch.qos.logback:logback-classic:1.4.7")
            }
        }

    }
}

tasks.register("multPackage") {
    dependsOn(tasks.getByName("clean"))
    dependsOn(tasks.getByName("jvmJar"))
    dependsOn(tasks.getByName("linuxArm64Binaries"))
    dependsOn(tasks.getByName("linuxX64Binaries"))
    dependsOn(tasks.getByName("mingwX64Binaries"))
}

tasks.register("publish-github") {
    dependsOn(tasks.getByName("multPackage"))
    dependsOn(tasks.getByName("dockerBuildx"))
    dependsOn(tasks.getByName("dockerPush"))
}

task("dockerBuildx", Exec::class) {
    dependsOn(tasks.getByName("multPackage"))
    commandLine(
        //command line args should be an array of strings
        //ref: https://stackoverflow.com/a/51564974
        "docker buildx build --platform linux/amd64 -t selcarpa/cloudflare-ddns:$version --build-arg CF_DDNS_VERSION=$version -t selcarpa/cloudflare-ddns:latest .".split(
            " "
        )
    )
}
task("dockerLogin", Exec::class) {
    commandLine(
        "docker",
        "login",
        "-u",
        "${properties["dockerUserName"]}",
        "-p",
        "${properties["dockerPassword"]}"
    )
}

task("dockerPush", Exec::class) {
    dependsOn(tasks.getByName("dockerLogin"))
    commandLine("docker push selcarpa/cloudflare-ddns --all-tags".split(" "))
}
