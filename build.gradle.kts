import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

// Spring Boot BOM이 관리하지 않는 라이브러리는 버전을 직접 지정한다.
// mybatis 4.1.x = Spring Boot 4.1 대응 (3.0.x는 Boot 3.2~3.5 전용)
// springdoc 3.x = Spring Boot 4 대응 (2.x는 Boot 3 전용)
val mybatisVersion = "4.1.0"
val springdocVersion = "3.1.1"
val jjwtVersion = "0.13.0"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-mysql")
	implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:$mybatisVersion")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

	// JWT — api 는 컴파일 시점, impl/jackson 은 런타임에만 필요하다.
	implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	runtimeOnly("com.mysql:mysql-connector-j")

	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.mybatis.spring.boot:mybatis-spring-boot-starter-test:$mybatisVersion")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-mysql")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
	useJUnitPlatform()

	testLogging {
		events("passed", "skipped", "failed")
		exceptionFormat = TestExceptionFormat.FULL   // 단축된 스택트레이스 대신 전체 출력
		showExceptions = true
		showCauses = true
		showStackTraces = true
		// 평소에는 조용히, 필요할 때만 SQL 로그와 MockMvc 요청/응답 덤프까지 본다.
		//   ./gradlew test -Pverbose
		showStandardStreams = project.hasProperty("verbose")
	}
}
