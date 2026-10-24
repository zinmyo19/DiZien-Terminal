pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Termux's Apache-2.0 terminal-emulator / terminal-view libraries are published here.
        maven("https://jitpack.io") { content { includeGroup("com.github.termux.termux-app") } }
    }
}
rootProject.name = "DiZienTerminal"
include(":app")
