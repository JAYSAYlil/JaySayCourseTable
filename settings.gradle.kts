pluginManagement {
    repositories {
        // 官方源优先；阿里云镜像仅作备用，避免国内网络失败时无法解析，
        // 同时保证 GitHub Actions 等海外环境不受镜像可用性影响。
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "JaySayCourseTable"
include(":app")
