plugins {
    // JDK 21이 없으면 Gradle이 자동으로 내려받는다 (시스템 기본 Java는 건드리지 않음)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "gopssl-backend-spring"
