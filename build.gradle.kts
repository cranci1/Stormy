plugins {
    java
    id("com.github.weave-mc.weave-gradle") version "fac948db7f"
    id("org.openjfx.javafxplugin") version "0.0.8"
}

group = "me.tryfle"
version = "1.1"

minecraft.version("1.8.9")

repositories {
    maven("https://jitpack.io")
    maven("https://repo.spongepowered.org/maven/")
    mavenCentral()
}

dependencies {
    implementation("org.projectlombok:lombok:1.18.28")
    implementation("org.json:json:20231013")
    annotationProcessor("org.projectlombok:lombok:1.18.28")
    compileOnly("com.github.weave-mc:weave-loader:v0.2.4")
    compileOnly("org.spongepowered:mixin:0.8.5")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.compileJava {
    options.release.set(17)
}

tasks.javadoc {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).charSet = "UTF-8"
}