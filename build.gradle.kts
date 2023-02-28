plugins {
    kotlin("jvm") version "1.8.0"
    application
    antlr
}



group = "ru.nsu.lisp2c"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    antlr("org.antlr:antlr4:4.11.1")
    implementation("org.antlr:antlr4-runtime:4.11.1")

}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

application {
    mainClass.set("MainKt")
}

tasks.compileKotlin{
    dependsOn += tasks.generateGrammarSource
}

tasks.generateGrammarSource {
    arguments = arguments + listOf("-visitor")
}

application {
    mainClass.set("ru.nsu.lisp2c.MainKt")
}