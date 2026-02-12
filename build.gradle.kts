plugins {
    java
    `java-library`
    `maven-publish`
}

group = "org.openjobspec"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    // Jackson (optional, for enhanced JSON serialization)
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.17.0")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name = "OJS Java SDK"
                description = "Official Open Job Spec SDK for Java"
                url = "https://github.com/openjobspec/ojs-java-sdk"
                licenses {
                    license {
                        name = "Apache License 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                    }
                }
            }
        }
    }
}
