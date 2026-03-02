plugins {
    id("org.springframework.boot") version "2.7.18" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
}

val validateFlags by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates flag file schema, key consistency, and Helm drift."
    commandLine("python3", "${rootDir}/scripts/validate_flags.py")
}

allprojects {
    group = "com.example"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(11))
            }
        }

        tasks.withType<Test> {
            useJUnitPlatform()
        }

        tasks.matching { it.name == "check" }.configureEach {
            dependsOn(rootProject.tasks.named("validateFlags"))
        }
    }
}
