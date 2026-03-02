plugins {
    `java-library`
}

dependencies {
    api("org.springframework.boot:spring-boot-starter:2.7.18")
    api("org.springframework.boot:spring-boot-autoconfigure:2.7.18")
    api("dev.openfeature:sdk:1.20.1")
    api("dev.openfeature.contrib.providers:flagd:0.11.19")
}
