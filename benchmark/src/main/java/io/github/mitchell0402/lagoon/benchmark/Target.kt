package io.github.mitchell0402.lagoon.benchmark

import androidx.test.platform.app.InstrumentationRegistry

/** Package under test: the benchmark build of this checkout unless the runner argument names another one. */
val TARGET_PACKAGE: String
    get() = InstrumentationRegistry.getArguments().getString("targetPackage") ?: "io.github.mitchell0402.lagoon.bench"
