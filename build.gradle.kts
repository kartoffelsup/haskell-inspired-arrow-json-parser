plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.61"
    `java-library`
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    val arrowVersion = "0.10.3"
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("io.arrow-kt:arrow-core:$arrowVersion")
    implementation("io.arrow-kt:arrow-syntax:$arrowVersion")
    implementation("io.arrow-kt:arrow-fx:$arrowVersion")

    testImplementation("io.arrow-kt:arrow-test:$arrowVersion")
}

tasks {
    withType<Test> {
        useJUnitPlatform()
    }
}
