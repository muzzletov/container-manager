plugins {
    application
    kotlin("jvm") version "2.1.10"
}

group = "de.muzzletov.pkg"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(23)
    }

    sourceCompatibility = JavaVersion.VERSION_23
    targetCompatibility = JavaVersion.VERSION_23
}

kotlin {
    jvmToolchain(23)
}

dependencies {
    implementation("org.json:json:20250107")
    compileOnly("org.apache.commons:commons-compress:1.27.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

application {
    this.mainClass.set("de.muzzletov.ContainerManager")
}

tasks.withType<Jar>() {
    exclude {
        it.path.startsWith("META-INF") &&
            (it.path.endsWith(".DSA", true) || it.path.endsWith(".RSA", true) || it.path.endsWith(".SF", true))
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "de.muzzletov.pkg.ContainerManager"
    }
    from(configurations.runtimeClasspath.get().map {if (it.isDirectory) it else zipTree(it)})
}
