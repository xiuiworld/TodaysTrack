pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral(); maven("https://nexus-playground.pleos.ai/repository/maven-releases/") }
}
rootProject.name = "TodaysTrack"
include(":app")
include(":music-core")
project(":music-core").projectDir=file("../music-core")
