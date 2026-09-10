plugins { java }

group = "id.cadera"
version = "1.7.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")

    // Database drivers are bundled so SQLite/MySQL remain optional but require
    // no server-side driver installation or runtime network download.
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("com.mysql:mysql-connector-j:26.7.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test { useJUnitPlatform() }

// Self-contained production JAR. Paper/PAPI stay compileOnly; JDBC runtime
// dependencies are unpacked into the plugin JAR.
tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get().map { dependency ->
            if (dependency.isDirectory) dependency else zipTree(dependency)
        }
    })
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
}
