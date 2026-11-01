import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Personal LANraragi"
    pkgName = "all.personallanraragi"
    versionCode = 10
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "Personal LANraragi"
        lang = "all"
        baseUrl {
            custom("http://127.0.0.1:3000")
        }
    }

    source {
        name = "Personal LANraragi NAS1"
        lang = "all"
        baseUrl {
            custom("http://127.0.0.1:3000")
        }
    }

    source {
        name = "Personal LANraragi NAS2"
        lang = "all"
        baseUrl {
            custom("http://127.0.0.1:3000")
        }
    }

    source {
        name = "Personal LANraragi Net1"
        lang = "all"
        baseUrl {
            custom("http://127.0.0.1:3000")
        }
    }

    source {
        name = "Personal LANraragi Net2"
        lang = "all"
        baseUrl {
            custom("http://127.0.0.1:3000")
        }
    }
}

android {
    sourceSets.named("test") {
        kotlin.directories.add("test")
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.stdlib)
    testImplementation(libs.kotlin.json)
    testImplementation(libs.okhttp.core)
    testImplementation(libs.tachiyomi.lib.v16)
}

// Unit tests use the source class already generated for the main variant.
tasks.matching { it.name == "kspDebugUnitTestKotlin" }.configureEach {
    enabled = false
}
