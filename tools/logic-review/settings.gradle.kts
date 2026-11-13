pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "TodaysTrackLogicReview"
include(":music-core")
project(":music-core").projectDir=file("../../music-core")
