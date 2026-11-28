@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral {
            mavenContent {
                releasesOnly()
            }
        }
        exclusiveContent {
            forRepository {
                maven {
                    name = "JitPack"
                    setUrl("https://jitpack.io")
                }
            }
            filter {
                includeGroup("com.github.therealbush")
                includeGroup("com.github.TeamNewPipe")
            }
        }
    }
}

// F-Droid doesn't support foojay-resolver plugin
// plugins {
//     id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
// }

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "YumaPlayer"

val hasAndroidSdk =
    !System.getenv("ANDROID_HOME").isNullOrBlank() ||
        !System.getenv("ANDROID_SDK_ROOT").isNullOrBlank() ||
        file("local.properties").takeIf { it.isFile }?.readText()?.contains("sdk.dir=") == true
val isGitHubActions = System.getenv("GITHUB_ACTIONS") == "true"
val skipApp = providers.gradleProperty("skipApp").orNull?.toBoolean() == true
val forceIncludeApp = providers.gradleProperty("forceIncludeApp").orNull?.toBoolean() == true

if (!skipApp && (hasAndroidSdk || isGitHubActions || forceIncludeApp)) {
    include(":app")
} else if (!skipApp) {
    logger.lifecycle(
        "Android SDK not detected (ANDROID_HOME/ANDROID_SDK_ROOT/local.properties missing); " +
            "skipping :app. Pass -PforceIncludeApp=true to include it.",
    )
}
include(":designsystem")
include(":database")
include(":core")
include(":core:innertube")
include(":core:audio-dsp")
include(":lyrics:kugou")
include(":lyrics:lrclib")
include(":lyrics:simpmusic")
include(":lyrics:betterlyrics")
include(":lyrics:unison")
include(":lyrics:youlyplus")
include(":lastfm")
include(":canvas")
include(":shazamkit")
include(":spotifycore")
include(":flaccore")
include(":addons")
include(":moriextractor")
include(":morideobfuscator")
include(":morideobfuscator-quickjs")
include(":cipher-api")

// Use a local copy of NewPipe Extractor by uncommenting the lines below.
// We assume, that ArchiveTune and NewPipe Extractor have the same parent directory.
// If this is not the case, please change the path in includeBuild().
//
// For this to work you also need to change the implementation in core/build.gradle.kts
// to one which does not specify a version.
// From:
//      implementation(libs.newpipe.extractor)
// To:
//      implementation("com.github.TeamNewPipe:NewPipeExtractor")
// includeBuild("../NewPipeExtractor") {
//    dependencySubstitution {
//        substitute(module("com.github.TeamNewPipe:NewPipeExtractor")).using(project(":extractor"))
//    }
// }
