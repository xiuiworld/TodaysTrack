pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "TodaysTrackServer"
include(":music-core")
project(":music-core").projectDir = file("../music-core")
