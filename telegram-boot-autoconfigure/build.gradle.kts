plugins {
    `java-library`
}

dependencies {
    implementation(project(":telegram-boot-core"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation(kotlin("reflect"))

    kapt("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.micrometer.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
