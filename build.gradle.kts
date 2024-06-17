import org.jetbrains.kotlin.gradle.plugin.mpp.Executable
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

val ktor_version: String by project
val kotlin_version: String by project
val okio_version: String by project
val kotlin_logging_version: String by project
val tomlkt_version: String by project
val taskGroupName = "cf-ddns"
val templeReleasePath = "release1"
val version_string: String by project

plugins {
    kotlin("multiplatform") version "2.0.0"
    id("io.ktor.plugin") version "3.0.0-beta-1"
    kotlin("plugin.serialization") version "2.0.0"
}

group = "one.tain"
version = version_string

repositories {
    mavenCentral()
    google()
}

kotlin.targets.withType<KotlinNativeTarget> {
    binaries.all {
        freeCompilerArgs += "-Xdisable-phases=EscapeAnalysis"
    }
}

kotlin {
    applyDefaultHierarchyTemplate()
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
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-core:$ktor_version")
                implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0")
                implementation("io.ktor:ktor-client-logging:$ktor_version")
                implementation("com.squareup.okio:okio:$okio_version")
                implementation("net.peanuuutz.tomlkt:tomlkt:$tomlkt_version")
                implementation("io.github.oshai:kotlin-logging:$kotlin_logging_version")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
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

//        val linuxArm64Main by getting {
//            dependencies {
//                implementation("io.ktor:ktor-client-cio:$ktor_version")
//            }
//        }
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-cio:$ktor_version")
                implementation("ch.qos.logback:logback-classic:1.5.6")
            }
        }

    }
}

tasks.register("multPackage") {
    description = "Package all targets"
    group = taskGroupName
    dependsOn(tasks.getByName("jvmJar"))
//    dependsOn(tasks.getByName("linuxArm64CopyAndCompile"))
    dependsOn(tasks.getByName("linuxX64CopyAndCompile"))
    dependsOn(tasks.getByName("mingwX64CopyAndCompile"))
}

tasks.register<Copy>("linuxArm64CopyAndCompile") {
    description = "Copy and compile linuxArm64 binaries"
    group = taskGroupName
    dependsOn(tasks.getByName("linuxArm64Binaries"))
    from("${buildDir}/bin/linuxArm64/releaseExecutable/")
    into("${buildDir}/${templeReleasePath}/")
    rename(taskGroupName, "cf-ddns-linux-arm64-${version}")
}

tasks.register<Copy>("linuxX64CopyAndCompile") {
    description = "Copy and compile linuxX64 binaries"
    group = taskGroupName
    dependsOn(tasks.getByName("linuxX64Binaries"))
    from("${buildDir}/bin/linuxX64/releaseExecutable/")
    into("${buildDir}/${templeReleasePath}/")
    rename(taskGroupName, "cf-ddns-linux-x64-${version}")
}

tasks.register<Copy>("mingwX64CopyAndCompile") {
    description = "Copy and compile mingwX64 binaries"
    group = taskGroupName
    dependsOn(tasks.getByName("mingwX64Binaries"))
    from("${buildDir}/bin/mingwX64/releaseExecutable/")
    into("${buildDir}/${templeReleasePath}/")
    rename(taskGroupName, "cf-ddns-windows-x64-${version}")
}

tasks.register<Copy>("graalvmCopy") {
    description = "Copy graalvm native compile package"
    group = taskGroupName
    from("${buildDir}/../graalvm-build/build/native/nativeCompile/cf-ddns")
    into("${buildDir}/${templeReleasePath}/")
    rename(taskGroupName, "cf-ddns-graalvm-linux-x64-${version}")
}

tasks.register("github") {
    description = "Package all targets"
    group = taskGroupName
    dependsOn(tasks.getByName("multPackage"))
    dependsOn(tasks.getByName("nativeDockerBuildx"))
    dependsOn(tasks.getByName("jvmDockerBuildx"))
    dependsOn(tasks.getByName("graalvmCopy"))
}

tasks.register<Exec>("nativeDockerBuildx") {
    description = "Build and push native docker images"
    group = taskGroupName
    dependsOn(tasks.getByName("multPackage"))
    val arguments = listOfNotNull(
        "docker",
        "buildx",
        "build",
        "--platform",
        "linux/amd64",
        "-t",
        "selcarpa/cloudflare-ddns:$version",
        "-t",
        "selcarpa/cloudflare-ddns:snapshot",
        "--build-arg",
        "CF_DDNS_VERSION=$version",
        if (properties["release"] == "true") {
            "--push"
        } else {
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
    description = "Build and push jvm docker images"
    group = taskGroupName
    dependsOn(tasks.getByName("jvmJar"))
    val arguments = listOfNotNull(
        "docker",
        "buildx",
        "build",
        "--platform",
        "linux/amd64,linux/arm/v7,linux/arm64/v8,linux/ppc64le,linux/s390x,windows/amd64",
        "-t",
        "selcarpa/cloudflare-ddns-jvm:$version",
        "-t",
        "selcarpa/cloudflare-ddns-jvm:snapshot",
        "--build-arg",
        "CF_DDNS_VERSION=$version",
        if (properties["release"] == "true") {
            "--push"
        } else {
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

