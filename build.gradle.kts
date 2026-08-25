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
    implementation("org.fxmisc.richtext:richtextfx:0.11.7")
    implementation("io.github.mkpaz:atlantafx-base:2.1.0")
    implementation("org.kordamp.ikonli:ikonli-javafx:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-feather-pack:12.4.0")
    // not IDE modules — carried along so generated apps can use WebView/media
    implementation("org.openjfx:javafx-web:26.0.2:win")
    implementation("org.openjfx:javafx-media:26.0.2:win")
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

tasks.register<JavaExec>("smoke") {
    group = "verification"
    description = "Headless check that generated form code compiles"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "dev.dragifier.CodegenSmoke"
}

tasks.register<JavaExec>("packageSmoke") {
    group = "verification"
    description = "Headless check of the jpackage pipeline (pass -PpackageDest=<dir> to keep the output)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "dev.dragifier.PackageSmoke"
    if (project.hasProperty("packageDest")) {
        args(project.property("packageDest").toString())
    }
    if (project.hasProperty("packageWeb")) {
        args("--with-web")
    }
}

tasks.register<JavaExec>("aiSmoke") {
    group = "verification"
    description = "Headless check of the AI edit protocol (pass --args=--print-prompt to dump the system prompt)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "dev.dragifier.AiSmoke"
}
