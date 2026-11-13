plugins {
    kotlin("jvm") version "2.0.0"
    application
}
repositories { mavenCentral() }
kotlin { jvmToolchain(17) }
application { mainClass.set("dev.todaystrack.server.MainKt") }
dependencies {
    implementation(project(":music-core"))
    implementation("io.ktor:ktor-server-netty-jvm:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    testImplementation(kotlin("test-junit"))
}
// Exercise the actual Android-free vehicle coordinator against the real in-process service.
sourceSets.test { kotlin.srcDir("../vehicle/app/src/main/java/dev/todaystrack/vehicle/generation/v2") }
tasks.register<JavaExec>("musicReview") {
    group="verification"
    classpath=sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.todaystrack.server.music.MusicReviewKt")
    args(providers.gradleProperty("reviewInput").get(),providers.gradleProperty("reviewOutput").get())
}
