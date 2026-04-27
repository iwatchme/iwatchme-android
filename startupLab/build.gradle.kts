plugins {
    id("jetpackstarter.android.library.compose")
}

android {
    namespace = "com.iwatchme.startuplab"
}

// ---------------------------------------------------------------------------
// Build-time code generator: produce heavy classes that simulate real SDK init
// ---------------------------------------------------------------------------
val generateWorkloadSimulator by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/source/workload")
    outputs.dir(outputDir)

    doLast {
        val baseDir = outputDir.get().asFile
            .resolve("com/iwatchme/startuplab/workload/generated")
        baseDir.mkdirs()

        val moduleCount = 1000
        val taskMapping = mapOf(
            "log_bootstrap" to (0 until 100),
            "crash_config" to (100 until 300),
            "cache_feed" to (300 until 500),
            "compose_seed" to (500 until 560),
            "strict_mode_audit" to (560 until 660),
            "analytics_warmup" to (660 until 800),
            "image_pipeline" to (800 until 900),
            "fresh_feed" to (900 until 970),
            "idle_preload" to (970 until 1000),
        )

        // Generate individual module classes
        for (i in 0 until moduleCount) {
            val className = "SdkModule_${"%04d".format(i)}"
            baseDir.resolve("$className.kt").writeText(
                buildString {
                    appendLine("package com.iwatchme.startuplab.workload.generated")
                    appendLine()
                    appendLine("import java.util.regex.Pattern")
                    appendLine("import java.util.TreeMap")
                    appendLine("import java.util.LinkedList")
                    appendLine("import java.security.MessageDigest")
                    appendLine()
                    appendLine("object $className {")
                    appendLine("    private val registry = HashMap<String, Any>(64)")
                    appendLine("    private val sorted = TreeMap<String, Long>()")
                    appendLine("    private val pattern = Pattern.compile(\"init_${i}_[a-z]+_(\\\\d+)\")")
                    appendLine("    private val pattern2 = Pattern.compile(\"(module_${i})_(\\\\w+)_(\\\\d+)_val\")")
                    appendLine()
                    appendLine("    fun init(): Int {")
                    appendLine("        val sb = StringBuilder(1024)")
                    appendLine("        val linked = LinkedList<String>()")
                    appendLine("        for (j in 0 until 80) {")
                    appendLine("            val key = \"module_${i}_item_\$j\"")
                    appendLine("            val value = key.hashCode().toLong() xor 0x${"%08X".format(i * 31 + 17)}L")
                    appendLine("            registry[key] = value")
                    appendLine("            sorted[key] = value")
                    appendLine("            sb.append(key).append('=').append(value).append(';')")
                    appendLine("            linked.addFirst(\"\$key:\$value\")")
                    appendLine("        }")
                    appendLine("        val matcher = pattern.matcher(sb.toString())")
                    appendLine("        var found = 0")
                    appendLine("        while (matcher.find()) found++")
                    appendLine("        val matcher2 = pattern2.matcher(sb.toString())")
                    appendLine("        while (matcher2.find()) found++")
                    appendLine("        val list = ArrayList<String>(registry.size)")
                    appendLine("        for ((k, v) in registry) {")
                    appendLine("            list.add(\"\$k=\$v\")")
                    appendLine("        }")
                    appendLine("        list.sort()")
                    appendLine("        val digest = MessageDigest.getInstance(\"SHA-256\")")
                    appendLine("        digest.update(sb.toString().toByteArray())")
                    appendLine("        val hash = digest.digest()")
                    appendLine("        return list.size + found + sb.length + hash.size + linked.size + sorted.size")
                    appendLine("    }")
                    appendLine("}")
                },
            )
        }

        // Generate the runner that maps task IDs to module groups
        baseDir.resolve("WorkloadRunner.kt").writeText(
            buildString {
                appendLine("package com.iwatchme.startuplab.workload.generated")
                appendLine()
                appendLine("/**")
                appendLine(" * Auto-generated workload runner.")
                appendLine(" * Each method simulates real CPU / class-loading work")
                appendLine(" * that benefits from AOT compilation via Baseline Profile.")
                appendLine(" */")
                appendLine("object WorkloadRunner {")
                appendLine()
                appendLine("    @Volatile")
                appendLine("    private var sink: Int = 0")
                appendLine()
                for ((taskId, range) in taskMapping) {
                    val methodName = taskId.replace(Regex("[^a-zA-Z0-9]"), "_")
                    appendLine("    fun run_$methodName() {")
                    appendLine("        var acc = 0")
                    for (i in range) {
                        appendLine("        acc += SdkModule_${"%04d".format(i)}.init()")
                    }
                    appendLine("        sink = acc")
                    appendLine("    }")
                    appendLine()
                }
                appendLine("    fun runForTask(taskId: String) {")
                appendLine("        when (taskId) {")
                for ((taskId, _) in taskMapping) {
                    val methodName = taskId.replace(Regex("[^a-zA-Z0-9]"), "_")
                    appendLine("            \"$taskId\" -> run_$methodName()")
                }
                appendLine("        }")
                appendLine("    }")
                appendLine("}")
            },
        )
    }
}

android.sourceSets["main"].java.srcDir(
    generateWorkloadSimulator.map { it.outputs.files.singleFile },
)

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(generateWorkloadSimulator)
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.startup)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material)
    implementation(libs.coroutines.android)

    implementation(project(":startupRuntime"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}
