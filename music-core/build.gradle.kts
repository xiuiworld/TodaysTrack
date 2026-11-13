plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    testImplementation(kotlin("test-junit"))
}

// Reuse the real, Android-free legacy algorithms without creating duplicate Android classes.
val legacy = tasks.register("prepareLegacy", Copy::class) {
    from("../vehicle/app/src/main/java/dev/todaystrack/vehicle/journey") {
        include("JourneySummaryBuilder.kt", "SourceCoverage.kt")
        filter { line: String -> line.replace("package dev.todaystrack.vehicle.journey", "package dev.todaystrack.music.legacy") }
    }
    into(layout.buildDirectory.dir("generated/legacy"))
}
sourceSets.main { kotlin.srcDir(layout.buildDirectory.dir("generated/legacy")) }
tasks.named("compileKotlin") { dependsOn(legacy) }
