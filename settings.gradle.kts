pluginManagement {
    includeBuild("build-logic")
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "Mikomi"

include(":app")
include(":benchmark")

include(":core:model")
include(":core:common")
include(":core:designsystem")
include(":core:ui")
include(":core:navigation")
include(":core:database")
include(":core:datastore")
include(":core:network")
include(":core:media")
include(":core:analytics")
include(":core:testing")

include(":data:anime")
include(":data:video")
include(":data:user")

include(":feature:home")
include(":feature:animap")
include(":feature:profile")
include(":feature:search")
include(":feature:detail")
include(":feature:player")
include(":feature:library")
include(":feature:history")
include(":feature:settings")
