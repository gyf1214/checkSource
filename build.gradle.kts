plugins {
    `java-gradle-plugin`
    `maven-publish`
    checkstyle
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

publishing {
    repositories {
        val shstsMavenUrl = providers.gradleProperty("shstsMavenUrl")
        val shstsUser = providers.gradleProperty("shstsUser")
        if (shstsMavenUrl.isPresent || shstsUser.isPresent) {
            maven {
                name = "shsts"
                url = uri(shstsMavenUrl.orElse("https://www.shsts.org/m2").get())
                if (shstsUser.isPresent) {
                    credentials {
                        username = shstsUser.get()
                        password = providers.gradleProperty("shstsPassword").orElse("").get()
                    }
                }
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

checkstyle {
    toolVersion = "10.20.1"
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
