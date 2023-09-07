// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
	id("com.android.application") version "8.1.1" apply false
	id("org.jetbrains.kotlin.android") version "1.9.0" apply false
	id("org.jetbrains.dokka") version "1.8.20"
	id("org.jlleitschuh.gradle.ktlint") version "10.2.1"
}

tasks.register("clean") { delete(layout.buildDirectory) }

ktlint {
	coloredOutput.set(false)
	enableExperimentalRules.set(true)
	disabledRules.set(
		setOf(
			"indent",
			"parameter-list-wrapping",
			"experimental:argument-list-wrapping"
		)
	)
	reporters {
		reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
	}
}
