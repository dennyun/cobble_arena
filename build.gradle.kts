plugins {
    java
    kotlin("jvm") version "2.1.0"
    // Loom 1.15+ supports Kotlin 2.1+ metadata remapping (fixes "Requested to write version 2.2.0")
    id("fabric-loom") version "1.15.5"
}

group = "com.cobblemon.blacklist"
version = "1.0.0"

base {
    archivesName.set("cobble_arena")
}

sourceSets {
    main {
        // Keep root as source root (for `package cobblemon.arena...`) but
        // compile only actual Java/Kotlin sources under `cobblemon/**`.
        // This avoids Gradle/Loom treating `build/**` outputs as inputs.
        java.srcDir(".")
        java.include("cobblemon/**")
        resources.srcDirs("assets", "data", "META-INF")
        // Include Fabric metadata files that are currently at project root.
        // Without these, Fabric does not detect/load this mod in dev runs.
        resources.srcDir(".")
        resources.include(
            "fabric.mod.json",
            "cobblemon_arena.mixins.json",
            "icon.png",
            "assets/**",
            "data/**",
            "META-INF/**"
        )
    }
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.architectury.dev/")
    maven("https://maven.impactdev.net/repository/maven-public/")
    maven("https://maven.impactdev.net/repository/development/")
    maven("https://api.modrinth.com/maven")
    maven("https://jitpack.io")
}

dependencies {
    // Minecraft
    minecraft("com.mojang:minecraft:1.21.1")

    // Mappings
    mappings("net.fabricmc:yarn:1.21.1+build.1:v2")

    // Fabric
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.116.6+1.21.1")
    modImplementation("net.fabricmc:fabric-language-kotlin:1.13.0+kotlin.2.1.0")
    modImplementation("dev.architectury:architectury-fabric:13.0.8")

    // Cobblemon — version controlled via gradle.properties :: cobblemon_version
    modImplementation("com.cobblemon:fabric:${property("cobblemon_version")}")

    // ❌ NÃO adicione mixinextras aqui (quebra o remap)
}

loom {
    runs {
        named("client") {
            property("fabric.development", "true")
        }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
