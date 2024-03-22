// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
	id("com.android.application") version "8.3.0" apply false
	id("org.jetbrains.kotlin.android") version "1.9.0" apply false
	id("org.jetbrains.dokka") version "1.9.20"
	id("org.jlleitschuh.gradle.ktlint") version "11.6.0"
}

tasks.register("clean") { delete(layout.buildDirectory) }

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
	version.set("0.50.0")
	coloredOutput.set(false)
}
