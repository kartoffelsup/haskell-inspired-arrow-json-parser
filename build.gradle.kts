plugins {
    id("org.jetbrains.kotlin.jvm") version "1.4.10"
    `java-library`
}

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        url = uri("https://dl.bintray.com/arrow-kt/arrow-kt/")
        content {
            includeGroup("io.arrow-kt")
        }
    }
}

dependencies {
    val arrowVersion = "0.11.0"
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("io.arrow-kt:arrow-core:$arrowVersion")
    implementation("io.arrow-kt:arrow-syntax:$arrowVersion")
    implementation("io.arrow-kt:arrow-fx:$arrowVersion")

    testImplementation("io.arrow-kt:arrow-core-test:$arrowVersion")
}

tasks {
    withType<Test> {
        useJUnitPlatform()
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "11"
        }
    }
}
