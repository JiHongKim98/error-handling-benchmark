plugins {
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
    id("me.champeau.jmh") version "0.7.3"
    kotlin("jvm") version "1.9.23"
}

group = "benchmark"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.23")
    jmhImplementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.23")

    implementation(platform("io.arrow-kt:arrow-stack:1.2.4"))
    implementation("io.arrow-kt:arrow-core")
    implementation("io.arrow-kt:arrow-fx-coroutines")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

sourceSets {
    named("main") {
        java.srcDir("src/main/kotlin")
    }
    named("jmh") {
        java.srcDir("src/jmh/kotlin")
    }
}

jmh {
    fork = 3
    warmupForks = 1

    iterations = 5
    warmupIterations = 2

    failOnError.set(true)
    duplicateClassesStrategy = DuplicatesStrategy.WARN

    resultFormat = "TEXT"
    resultsFile = file("results/results.txt")
}
