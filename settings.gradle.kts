pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Mirrors of the Google / Maven Central repositories, for networks where the
        // canonical hosts are unreachable. Declared after the canonical ones so CI uses
        // the official hosts and local builds still resolve.
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
    }
}

rootProject.name = "AniReku"
include(":app")
