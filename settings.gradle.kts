/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

pluginManagement {
    repositories {
        mavenLocal()

        maven { url = uri("https://repo.bitsquidd.xyz/repository/bit/") }

        gradlePluginPortal()
    }
}

rootProject.name = "perigee"
