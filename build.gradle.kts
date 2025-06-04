plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.4.5"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.vision"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("com.squareup.okhttp3:okhttp:4.9.3")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.0")

	implementation("org.bytedeco:javacv:1.5.9")
	implementation("org.bytedeco:javacv-platform:1.5.9")

	// For better memory management and faster processing
	implementation("org.bytedeco:ffmpeg:6.0-1.5.9")
	implementation("org.bytedeco:ffmpeg-platform:6.0-1.5.9")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
