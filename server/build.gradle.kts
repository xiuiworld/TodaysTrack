plugins {
    kotlin("jvm") version "2.0.0"
    application
}
repositories { mavenCentral() }
kotlin { jvmToolchain(17) }
application { mainClass.set("dev.todaystrack.server.MainKt") }
dependencies {
    implementation("io.ktor:ktor-server-netty-jvm:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    testImplementation(kotlin("test-junit"))
}
