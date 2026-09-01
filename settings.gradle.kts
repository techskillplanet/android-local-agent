pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "android-local-agent"
include(":app")

// 与 monorepo 内 planet-components Android View 统一技术栈（Java + 传统 View）
include(":planet-components-android")
project(":planet-components-android").projectDir =
    file("../planet-components/android/library")
