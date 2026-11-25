plugins {
    id("org.springframework.boot") version "4.1.0"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation(project(":telegram-boot-spring-boot-starter"))
    implementation(kotlin("reflect"))
    // Local Telegram emulator, enabled by the "emulator" profile; not packaged into the application jar.
    developmentOnly(project(":telegram-boot-emulator"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.bootRun {
    workingDir = projectDir
}
