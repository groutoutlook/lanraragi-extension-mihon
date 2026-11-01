import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Personal LANraragi"
    pkgName = "all.personallanraragi"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "Personal LANraragi"
        lang = "all"
        baseUrl {
            custom("http://127.0.0.1:3000")
        }
    }
}
