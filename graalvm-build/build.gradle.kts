val ktor_version: String by project
val kotlin_version: String by project
val okio_version: String by project
val kotlin_logging_version: String by project
val taskGroupName = "cf-ddns"
val templeReleasePath = "release1"
val version_string: String by project

plugins {
    kotlin("jvm") version "1.9.23"
    id("io.ktor.plugin") version "3.0.0-beta-1"
    kotlin("plugin.serialization") version "1.9.23"
    application
    id("org.graalvm.buildtools.native") version "0.10.1"
}

group = "one.tain"
version = version_string

graalvmNative {
    binaries.all {
//        buildArgs.add("--gc=G1")
        buildArgs.add("-Ob")
        buildArgs.add("-march=compatibility")
    }
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

tasks.register<Copy>("nativeCompileAndCopy") {
    description = "native copile and copy"
    group = taskGroupName
    dependsOn(tasks.getByName("nativeCompile"))
    from("${buildDir}/native/nativeCompile/cf-ddns.exe")
    val path = "${buildDir}/../../build/${templeReleasePath}/"
    if (!File(path).exists()) {
        File(path).mkdirs()
    }
    into(path)
    val osName = System.getProperty("os.name").lowercase()

    rename(
        taskGroupName, "cf-ddns-graalvm-${
            if (osName.indexOf("windows") >= 0) {
                "windows"

            } else if (osName.indexOf("linux") >= 0) {
                "linux"
            } else {
                throw Exception("not support os")
            }
        }-x64-${version}"
    )
}

