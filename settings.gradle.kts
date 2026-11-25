pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        kotlin("kapt") version "2.4.10"
    }
}

rootProject.name = "telegram-boot"
include(
    "telegram-boot-core",
    "telegram-boot-telegram",
    "telegram-boot-autoconfigure",
    "telegram-boot-spring-boot-starter",
    "telegram-boot-emulator",
    "telegram-boot-e2e-tests",
    "examples:telegram-boot-sample-app",
)
