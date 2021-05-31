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
            }
        }
    }
}