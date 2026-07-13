plugins {
	java
	id("org.springframework.boot") version "3.5.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("checkstyle")
	id("jacoco")
	id("com.github.spotbugs") version "6.1.2"
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

dependencies {
	// Spring Boot
	implementation("org.springframework.boot:spring-boot-docker-compose")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")

	// Observability
	implementation("io.micrometer:micrometer-tracing-bridge-brave")
	implementation("net.logstash.logback:logstash-logback-encoder:8.0")

	// DB / Migration
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("com.h2database:h2")

	// UUID
	implementation("com.github.f4b6a3:uuid-creator:6.0.0")

	// PDF (JasperReports)
	implementation("net.sf.jasperreports:jasperreports:7.0.1")
	implementation("net.sf.jasperreports:jasperreports-pdf:7.0.1")
	implementation("net.sf.jasperreports:jasperreports-jdt:7.0.1")

	// API Documentation
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")

	// Lombok (Entity only — DTOs use record)
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	// Test
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("com.tngtech.archunit:archunit-junit5:1.4.0")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:postgresql")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// --- bootRun ---
// ヘッドレス環境（SageMaker 等）では /etc/fonts/fonts.conf が欠落しており、AWT の
// フォント初期化が「Fontconfig head is null」で失敗する。JasperReports の PDF 生成が
// この初期化を通るため、以下の 2 つを明示指定して回避する:
//   1. sun.awt.fontconfig    — 最小の fontconfig.properties を指定し head is null を回避
//   2. sun.java2d.fontpath   — 実体フォント（.ttf）のディレクトリを追加登録し
//                              「No physical fonts found」を回避（SageMaker では /opt/conda/fonts）
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	jvmArgs(
		"-Djava.awt.headless=true",
		"-Dsun.awt.fontconfig=${projectDir}/config/fonts/fontconfig.properties",
		"-Dsun.java2d.fontpath=append:/opt/conda/fonts",
	)
}

// --- Checkstyle ---
checkstyle {
	toolVersion = "10.21.4"
	configFile = file("config/checkstyle/checkstyle.xml")
}

// --- SpotBugs ---
spotbugs {
	toolVersion = "4.9.3"
	effort = com.github.spotbugs.snom.Effort.MAX
	reportLevel = com.github.spotbugs.snom.Confidence.MEDIUM
	excludeFilter = file("config/spotbugs/spotbugs-exclude.xml")
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask> {
	reports.create("html") { required = true }
	reports.create("xml") { required = false }
}

// --- Jacoco ---
tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required = true
		html.required = true
	}
}

tasks.jacocoTestCoverageVerification {
	violationRules {
		rule {
			limit {
				minimum = "0.80".toBigDecimal()
			}
		}
	}
}

// --- Test ---
tasks.withType<Test> {
	useJUnitPlatform()
	jvmArgs(
		"-Djava.awt.headless=true",
		"-Dsun.awt.fontconfig=${projectDir}/config/fonts/fontconfig.properties",
		"-Dsun.java2d.fontpath=append:/opt/conda/fonts",
	)
	finalizedBy(tasks.jacocoTestReport)
}
