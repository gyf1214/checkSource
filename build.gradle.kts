plugins {
    `java-gradle-plugin`
}

group = "org.shsts.checksource"
version = providers.gradleProperty("version").get()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

gradlePlugin {
    plugins {
        create("checkSource") {
            id = "org.shsts.checksource"
            implementationClass = "org.shsts.checksource.CheckSourcePlugin"
            displayName = "Check Source Plugin"
            description = "Checks Java source package boundaries from Gradle source sets."
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
