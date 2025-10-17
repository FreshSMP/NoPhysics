plugins {
    java
}

repositories {
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        url = uri("https://maven.enginehub.org/repo/")
    }
    maven {
        name = "tcoded-releases"
        url = uri("https://repo.tcoded.com/releases")
    }
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.14")

    implementation("com.tcoded:FoliaLib:0.5.1")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
