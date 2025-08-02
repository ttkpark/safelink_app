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
        // JitPack 저장소 추가 (블루투스 라이브러리용)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "safelink_client"
include(":app")
 