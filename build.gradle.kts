plugins {
    kotlin("js") version "1.4.32"
}

group = "me.cantanima"
version = "1.0-SNAPSHOT"

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test-js"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.5.0")
    implementation("io.ktor:ktor-client-core:1.6.0")
    implementation("io.ktor:ktor-client-js:1.6.0")
}

kotlin {
    js(LEGACY) {
        binaries.executable()
        browser {
            commonWebpackConfig {
                cssSupport.enabled = true
            }
            dceTask {
                keep("Groebner_Nim.get_game_context")
                keep("Groebner_Nim.level_zero_game")
                keep("Groebner_Nim.level_one_game")
                keep("Groebner_Nim.random_game")
                keep("Groebner_Nim.replay")
                keep("Groebner_Nim.restore_seed")
            }
        }
    }
}