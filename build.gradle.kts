plugins {
    val kotlinVersion = "1.9.24"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion

    id("net.mamoe.mirai-console") version "2.16.0"
}

group = "com.bcz"
version = "1.4.7"

repositories {
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

tasks.register<JavaExec>("previewAchievementImage") {
    group = "verification"
    description = "Render a local achievement notification PNG for visual debugging."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.bcz.ImageRenderPreviewKt")
}

mirai {
    jvmTarget = JavaVersion.VERSION_1_8
}
