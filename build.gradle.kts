plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "dev.dragifier"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.14.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

javafx {
    version = "26.0.2"
    modules = listOf("javafx.controls", "javafx.graphics")
}

application {
    mainClass = "dev.dragifier.DragifierApp"
    applicationDefaultJvmArgs = listOf("--enable-native-access=javafx.graphics")
}
