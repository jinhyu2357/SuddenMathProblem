# SuddenMathProblem

이 문서는 현재 프로젝트에서 사용할 수 있는 주요 명령어와, 파일에서 변경 가능한 설정값을 빠르게 파악할 수 있도록 정리한 가이드입니다.

## 1) 프로젝트 개요
- 플랫폼: Paper(Minecraft) 플러그인
- 언어: Kotlin(JVM)
- 빌드 도구: Gradle Wrapper

---

## 2) 자주 쓰는 명령어
모든 명령어는 프로젝트 루트(`/workspace/SuddenMathProblem`)에서 실행합니다.

### 빌드/정리
- `./gradlew build`
  - 플러그인을 빌드합니다.
  - 현재 설정상 `build`는 `shadowJar`에 의존하므로, shaded JAR 생성까지 함께 수행됩니다.
- `./gradlew shadowJar`
  - 의존성을 포함한 JAR(Shadow JAR)를 생성합니다.
- `./gradlew clean`
  - `build/` 산출물을 정리합니다.

### 개발 서버 실행
- `./gradlew runServer`
  - 로컬 Paper 테스트 서버를 실행합니다.
  - `build.gradle.kts`의 `runServer { minecraftVersion("1.21") }` 설정을 사용합니다.

### 정보 확인
- `./gradlew tasks`
  - 현재 프로젝트에서 사용 가능한 Gradle 작업 목록을 확인합니다.
- `./gradlew help`
  - Gradle 기본 도움말을 확인합니다.

> Windows에서는 `./gradlew` 대신 `gradlew.bat`를 사용하면 됩니다.

---

## 3) 설정 가능한 파일/항목 정리

## 3-1) `build.gradle.kts`
빌드와 실행 동작의 핵심 설정 파일입니다.

### 플러그인 버전
- `kotlin("jvm") version "2.3.20-RC"`
  - Kotlin Gradle 플러그인 버전
- `id("com.gradleup.shadow") version "8.3.0"`
  - Shadow JAR 플러그인 버전
- `id("xyz.jpenilla.run-paper") version "2.3.1"`
  - Paper 테스트 서버 실행 플러그인 버전

### 프로젝트 메타 정보
- `group = "org.example.jinhhyu"`
  - 아티팩트 그룹 ID
- `version = "1.0"`
  - 프로젝트/플러그인 버전(리소스 치환에도 사용)

### 저장소(Repositories)
- `mavenCentral()`
- `maven("https://repo.papermc.io/repository/maven-public/")`
  - 필요 시 사내 저장소/미러 저장소 추가 가능

### 의존성(Dependencies)
- `compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")`
  - Paper API 대상 버전
- `implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")`
  - Kotlin 표준 라이브러리

### 실행/빌드 동작
- `runServer { minecraftVersion("1.21") }`
  - 로컬 테스트 서버 Minecraft 버전
- `val targetJavaVersion = 21`
  - Kotlin JVM Toolchain 타깃 Java 버전
- `tasks.build { dependsOn("shadowJar") }`
  - `build` 시 `shadowJar` 자동 실행 여부
- `tasks.processResources { ... filesMatching("paper-plugin.yml") { expand(props) } }`
  - `paper-plugin.yml` 내 `${version}` 같은 템플릿 변수 치환 동작

---

## 3-2) `src/main/resources/paper-plugin.yml`
Paper 플러그인 메타데이터 파일입니다.

- `name`: 플러그인 이름
- `version`: 플러그인 버전
  - 현재 `'1.0'`으로 고정되어 있으며, 필요 시 `${version}` 형태로 바꾸면 Gradle 프로젝트 버전과 연동 가능
- `main`: 플러그인 메인 클래스 경로
- `api-version`: 대상 Paper API 메이저 버전

필요에 따라 다음 항목도 추가 가능합니다.
- `authors`, `description`, `website`
- `commands`, `permissions`
- `depend`, `softdepend`, `loadbefore`

---

## 3-3) `settings.gradle.kts`
멀티 모듈/프로젝트 식별 관련 설정입니다.

- `rootProject.name = "SuddenMathProblem"`
  - 루트 프로젝트 이름

---

## 3-4) `gradle/wrapper/gradle-wrapper.properties`
Gradle Wrapper 동작을 제어합니다.

- `distributionUrl`
  - 사용할 Gradle 배포판 버전 (현재 `gradle-8.8-bin.zip`)
- `networkTimeout`
  - 배포판 다운로드 시 네트워크 타임아웃(ms)
- `validateDistributionUrl`
  - distribution URL 검증 여부
- `distributionBase`, `distributionPath`, `zipStoreBase`, `zipStorePath`
  - Wrapper 캐시 저장 위치 관련 설정

---

## 3-5) `gradle.properties`
현재 비어 있으며, 필요한 빌드 전역 속성을 추가할 수 있습니다.

예시로 자주 쓰는 항목:
- `org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8`
- `org.gradle.parallel=true`
- `kotlin.code.style=official`

---

## 4) 출력물 위치
- 일반 빌드 산출물: `build/`
- JAR 파일: 보통 `build/libs/`

---

## 5) 빠른 시작(예시)
1. `./gradlew clean build`
2. `./gradlew runServer`
3. 서버에 접속해 플러그인 로딩 여부 확인

