plugins {
    id("java")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.apache.lucene:lucene-core:8.11.3")
    implementation("org.apache.lucene:lucene-analyzers-common:8.11.3")

    implementation("org.apache.lucene:lucene-queryparser:8.11.3")
    implementation("org.apache.lucene:lucene-suggest:8.11.3")
    implementation("com.google.code.gson:gson:2.10.1")
}

tasks.test {
    useJUnitPlatform()
}
