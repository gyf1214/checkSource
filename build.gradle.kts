plugins {
    `java-gradle-plugin`
}

group = "dev.checksource"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

gradlePlugin {
    plugins {
        create("checkSource") {
            id = "dev.checksource.plugin"
            implementationClass = "dev.checksource.CheckSourcePlugin"
            displayName = "Check Source Plugin"
            description = "Gradle plugin scaffold for checkSource."
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
