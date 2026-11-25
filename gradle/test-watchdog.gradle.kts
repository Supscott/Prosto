// Per-test watchdog for the unit tests (PRO-164). JUnit4 has no global test timeout, and a Timeout rule
// moves the test off Robolectric's main thread, so the limit is enforced from the Gradle side: when one
// test runs longer than testTimeoutSeconds (default 240; `-PtestTimeoutSeconds=N` or the environment
// variable ORG_GRADLE_PROJECT_testTimeoutSeconds=N changes it), the
// watchdog records the test in build/test-timeout.txt (tools/test_summary.py prints it as a FAIL line)
// and kills the test worker, so the run ends with a failure within seconds instead of at the 15-minute
// task timeout. Bisect a hang with `tools/preview.sh Class.method`.

import java.util.concurrent.ConcurrentHashMap

// No top-level vals or funs: Android Lint's Kotlin-script analysis crashes on script properties
// ("findFirCompiledSymbol only works on compiled declarations"), so everything lives in this block.
tasks.withType<Test>().configureEach {
    val perTestSeconds = (project.findProperty("testTimeoutSeconds") as String?)?.toLongOrNull() ?: 240L
    val timeoutReport = project.layout.buildDirectory.file("test-timeout.txt").get().asFile
    val killTestWorkers = {
        ProcessHandle.current().descendants().forEach { process ->
            val text = (listOfNotNull(process.info().command().orElse(null)) + process.info().arguments().orElse(emptyArray()))
                .joinToString(" ")
            if (text.contains("GradleWorkerMain") || text.contains("Gradle Test Executor")) process.destroyForcibly()
        }
    }
    val running = ConcurrentHashMap<String, Long>()
    var watchdog: Thread? = null

    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {
            if (suite.parent != null) return
            timeoutReport.delete()
            watchdog = Thread {
                try {
                    while (true) {
                        Thread.sleep(2_000)
                        val now = System.nanoTime()
                        val hung = running.entries.firstOrNull { (now - it.value) / 1_000_000_000 >= perTestSeconds }
                        if (hung != null) {
                            val message = "${hung.key}\t$perTestSeconds"
                            timeoutReport.parentFile.mkdirs()
                            timeoutReport.writeText(message + "\n")
                            System.err.println("==> TEST TIMEOUT: ${hung.key} ran longer than ${perTestSeconds}s; killing the test worker")
                            killTestWorkers()
                            return@Thread
                        }
                    }
                } catch (_: InterruptedException) {
                }
            }.apply {
                isDaemon = true
                name = "test-watchdog"
                start()
            }
        }

        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (suite.parent == null) watchdog?.interrupt()
        }

        override fun beforeTest(testDescriptor: TestDescriptor) {
            running["${testDescriptor.className}.${testDescriptor.name}"] = System.nanoTime()
        }

        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
            running.remove("${testDescriptor.className}.${testDescriptor.name}")
        }
    })
}
