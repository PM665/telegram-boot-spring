plugins {
    `java-library`
}

dependencies {
    implementation(project(":telegram-boot-core"))
    implementation(libs.telegrambots.meta)
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation(kotlin("reflect"))
    implementation("org.slf4j:slf4j-api")

    kapt("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(project(":telegram-boot-spring-boot-starter"))
    testImplementation(libs.telegrambots.client)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
