pluginManagement {
    repositories {
        maven { url = uri("https://maven.fabricmc.net/") }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Loom adds local remapped repositories at the project level.
    // Preferring settings repositories can prevent those from being used.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://libraries.minecraft.net/") }
        maven { url = uri("https://maven.impactdev.net/repository/maven-public/") }
        maven { url = uri("https://maven.impactdev.net/repository/development/") }
        maven { url = uri("https://api.modrinth.com/maven") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "cobble_arena"