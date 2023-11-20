import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.Executable
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

val ktor_version: String by project
val kotlin_version: String by project
val okio_version: String by project
val kotlin_logging_version: String by project
val taskGroupName = "cf-ddns"

plugins {
    kotlin("multiplatform") version "1.9.10"
    id("io.ktor.plugin") version "2.3.5"
    kotlin("plugin.serialization") version "1.9.0"
}

group = "one.tain"
version = "1.17-SNAPSHOT"

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
    linuxArm64 {
        config()
    }
    mingwX64 {
        config()
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
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
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
    group = taskGroupName
    dependsOn(tasks.getByName("clean"))
    dependsOn(tasks.getByName("jvmJar"))
    dependsOn(tasks.getByName("linuxArm64CopyAndCompile"))
    dependsOn(tasks.getByName("linuxX64CopyAndCompile"))
    dependsOn(tasks.getByName("mingwX64CopyAndCompile"))
}

tasks.register<Copy>("linuxArm64CopyAndCompile") {
    group = taskGroupName
    dependsOn(tasks.getByName("linuxArm64Binaries"))
    from("${buildDir}/bin/linuxArm64/releaseExecutable/")
    into("${buildDir}/release1/")
    rename(taskGroupName, "cf-ddns-linux-arm64-${version}")
}

tasks.register<Copy>("linuxX64CopyAndCompile") {
    group = taskGroupName
    dependsOn(tasks.getByName("linuxX64Binaries"))
    from("${buildDir}/bin/linuxX64/releaseExecutable/")
    into("${buildDir}/release1/")
    rename(taskGroupName, "cf-ddns-linux-x64-${version}")
}

tasks.register<Copy>("mingwX64CopyAndCompile") {
    group = taskGroupName
    dependsOn(tasks.getByName("mingwX64Binaries"))
    from("${buildDir}/bin/mingwX64/releaseExecutable/")
    into("${buildDir}/release1/")
    rename(taskGroupName, "cf-ddns-windows-x64-${version}")
}

tasks.register("github") {
    group = taskGroupName
    dependsOn(tasks.getByName("multPackage"))
    dependsOn(tasks.getByName("nativeDockerBuildx"))
    dependsOn(tasks.getByName("jvmDockerBuildx"))
}

tasks.register<Exec>("nativeDockerBuildx") {
    group = taskGroupName
    dependsOn(tasks.getByName("multPackage"))
    if(properties["release"]=="true"){
        dependsOn(tasks.getByName("dockerLogin"))
    }
    val arguments= listOfNotNull(
        "docker",
        "buildx",
        "build",
        "--platform",
        "linux/amd64",
        "-t",
        "selcarpa/cloudflare-ddns:$version",
        "--build-arg",
        "CF_DDNS_VERSION=$version",
        if(properties["release"]=="true"){
            "--push"
        }else{
            null
        },
        "-t",
        "selcarpa/cloudflare-ddns:latest",
        "."
    )
    commandLine(
        arguments
    )
}
tasks.register<Exec>("jvmDockerBuildx") {
    group = taskGroupName
    dependsOn(tasks.getByName("jvmJar"))
    if(properties["release"]=="true"){
        dependsOn(tasks.getByName("dockerLogin"))
    }
    val arguments= listOfNotNull(
        "docker",
        "buildx",
        "build",
        "--platform",
        "linux/amd64,linux/arm/v7,linux/arm64/v8,linux/ppc64le,linux/s390x,windows/amd64",
        "-t",
        "selcarpa/cloudflare-ddns-jvm:$version",
        "--build-arg",
        "CF_DDNS_VERSION=$version",
        if(properties["release"]=="true"){
            "--push"
        }else{
            null
        },
        "-t",
        "selcarpa/cloudflare-ddns-jvm:latest",
        "-f",
        "./Dockerfile-jvm",
        "."
    )
    commandLine(
        arguments
    )
}

tasks.register<Exec>("dockerLogin") {
    group = taskGroupName
    commandLine(
        "docker",
        "login",
        "-u",
        "${properties["dockerUserName"]}",
        "-p",
        "${properties["dockerPassword"]}"
    )
}
