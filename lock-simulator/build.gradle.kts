plugins {
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
}

tasks.register<JavaExec>("runDemo") {
    group = "application"
    description = "运行锁状态机演示,把所有迁移事件实时打印到 stdout"
    mainClass.set("com.iwatchme.locksim.DemoKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardOutput = System.out
}
