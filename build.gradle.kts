plugins {
    java
    application
    id("org.javamodularity.moduleplugin") version "1.8.15"
    id("org.openjfx.javafxplugin") version "0.0.13"
    id("org.beryx.jlink") version "2.25.0"
}

group = "fr.cactusStudio"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val junitVersion = "5.12.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

application {
    mainModule.set("fr.cactusstudio.bibliofx")
    // Use Launcher as entry to avoid JavaFX direct main constraints in some environments
    mainClass.set("fr.cactusstudio.bibliofx.Launcher")
}

javafx {
    version = "21.0.6"
    modules = listOf("javafx.controls", "javafx.fxml")
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:${junitVersion}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${junitVersion}")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

jlink {
    imageZip.set(layout.buildDirectory.file("/distributions/app-${javafx.platform.classifier}.zip"))
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
    launcher {
        name = "BiblioFX"
    }
    jpackage {
        // On Windows, this will create an .exe installer
        installerType = "exe"
        imageName = "BiblioFX"
        installerName = "BiblioFX-setup"
        vendor = "CactusStudio"
        appVersion = project.version.toString()
        // You can set icon if available in resources, e.g.: src/main/resources/icon.ico
        // icon = file("src/main/resources/icon.ico").absolutePath
        installerOptions = listOf(
            "--win-menu",
            "--win-shortcut"
        )
    }
}


// Convenience task to create the Windows installer quickly
tasks.register("makeInstaller") {
    group = "distribution"
    description = "Build the native installer using jpackage (Windows .exe)."
    dependsOn("jpackage")
}
