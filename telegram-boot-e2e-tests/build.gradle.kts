dependencies {
    testImplementation(project(":examples:telegram-boot-sample-app"))
    testImplementation(project(":telegram-boot-spring-boot-starter"))
    testImplementation(project(":telegram-boot-emulator"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
