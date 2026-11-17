package io.github.mitchell0402.lagoon.benchmark

import androidx.test.platform.app.InstrumentationRegistry

/** Package under test: the benchmark build of this checkout unless the runner argument names another one. */
val TARGET_PACKAGE: String
    get() = InstrumentationRegistry.getArguments().getString("targetPackage") ?: "io.github.mitchell0402.lagoon.bench"

/**
 * Starts the app's main screen. Benchmark builds carry a second launcher activity (the lab's component showcase),
 * and the default launch intent picked that one: the baseline profiles recorded before U8 covered the showcase,
 * not MainActivity.
 */
fun androidx.benchmark.macro.MacrobenchmarkScope.startMainActivity() {
    startActivityAndWait(
        android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            .setClassName(packageName, "com.github.andreyasadchy.xtra.ui.main.MainActivity")
    )
}
