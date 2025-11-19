plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.hasirciogluhq"
version = "1.0.0"
description = "Easy MC Admin - Universal Minecraft Server Management Plugin"

java {
    // Bytecode level = 17 (Minecraft compatibility)
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    
    // Use Java 17 toolchain to avoid compatibility issues with Java 25
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()

    // Bukkit / Spigot API
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")

    // Additional Spigot dependencies
    maven("https://oss.sonatype.org/content/groups/public/")

    // PaperMC APIs
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Bukkit/Spigot API
    compileOnly("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")

    // WebSocket Client (fat-jar included)
    implementation("org.java-websocket:Java-WebSocket:1.5.4")

    // JSON Serializer
    implementation("com.google.code.gson:gson:2.10.1")
}

tasks {

    processResources {
        filteringCharset = "UTF-8"
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        
        // Replace placeholders in plugin.yml
        expand(
            "name" to project.name,
            "version" to project.version.toString(),
            "description" to project.description.toString()
        )
    }

    shadowJar {
        archiveBaseName.set("EasyMcAdmin")
        archiveClassifier.set("")   // No "-all"
        archiveVersion.set("")      // No version number in file name

        // Ensures all lib META-INF files merge correctly
        mergeServiceFiles()
    }

    build {
        dependsOn(shadowJar)
    }

    compileJava {
        options.encoding = "UTF-8"
    }
}

tasks.register("deploy") {
    dependsOn("build")

    doLast {
        val jarFile = layout.buildDirectory.file("libs/EasyMcAdmin.jar").get().asFile
        val targetDir = file("/Users/hasircioglu/mc-server-1/servers/test/plugins")

        if (!jarFile.exists()) {
            throw GradleException("JAR file not found: ${jarFile.absolutePath}")
        }

        if (!targetDir.exists()) {
            targetDir.mkdirs()
            println("Created target directory: ${targetDir.absolutePath}")
        }

        copy {
            from(jarFile)
            into(targetDir)
        }

        println("🔥 Deployed ${jarFile.name} → ${targetDir.absolutePath}")
    }
}
