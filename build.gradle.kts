/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

group = "xyz.orbitary"
version = property("version") as String
description = "Perigee - Java Discord Rich Presence client"

plugins {
    alias(libs.plugins.bit.convention)
}

repositories {
    mavenCentral()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    testCompileOnly(libs.annotations)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.compilerArgs.addAll(listOf("-Xlint:all,-exports", "-parameters"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    enabled = false
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Perigee")
            description.set(project.description)
            url.set("https://github.com/Orbitary/Perigee")
            licenses {
                license {
                    name.set("GNU Lesser General Public License v3.0")
                    url.set("https://www.gnu.org/licenses/lgpl-3.0.txt")
                }
            }
            scm {
                url.set("https://github.com/Orbitary/Perigee")
                connection.set("scm:git:https://github.com/Orbitary/Perigee.git")
            }
        }
    }
    repositories {
        maven {
            url = uri("https://repo.bitsquidd.xyz/repository/bit/")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}
