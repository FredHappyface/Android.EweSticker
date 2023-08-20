plugins {
	id("com.android.application")
	id("kotlin-android")
	id("org.jetbrains.dokka")
	id("org.jlleitschuh.gradle.ktlint")
}

tasks.dokkaGfm.configure {
	outputDirectory.set(buildDir.resolve("../../documentation/reference"))
	dokkaSourceSets {
		named("main") {
			skipDeprecated.set(true)
			skipEmptyPackages.set(true)
			sourceRoots.from(file("src/main/java"))
			suppressInheritedMembers.set(true)
			includeNonPublic.set(true)
		}
	}
}

tasks.register("genDocs") {
	val ref = buildDir.resolve("../../documentation/reference")
	delete(ref)
	dependsOn("dokkaGfm")
	doLast {
		copy {
			from("$ref/index.md")
			into(ref)
			rename { "README.md" }
		}
	}
}

android {
	compileSdk = 33
	buildToolsVersion = "34.0.0"

	defaultConfig {
		applicationId = "com.fredhappyface.ewesticker"
		minSdk = 26
		targetSdk = 33
		versionCode = 20230820
		versionName = "20230820"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		setProperty("archivesBaseName", "$applicationId-$versionName")
	}

	buildTypes {
		getByName("debug") { versionNameSuffix = "-debug" }
		getByName("release") {
			// versionNameSuffix = "-release"
			proguardFiles("proguard-android-optimize.txt", "proguard-rules.pro")
			isMinifyEnabled = false
		}
	}

	compileOptions {
		sourceCompatibility(JavaVersion.VERSION_17)
		targetCompatibility(JavaVersion.VERSION_17)
	}

	kotlinOptions { jvmTarget = "17" }
	namespace = "com.fredhappyface.ewesticker"
}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
	implementation("androidx.core:core-ktx:1.10.1")
	implementation("androidx.appcompat:appcompat:1.6.1")
	implementation("com.google.android.material:material:1.9.0")
	implementation("androidx.preference:preference-ktx:1.2.1")
	implementation("io.coil-kt:coil:2.4.0")
	implementation("io.coil-kt:coil-gif:2.4.0")
	implementation("io.coil-kt:coil-video:2.4.0")
	implementation("androidx.gridlayout:gridlayout:1.0.0")
	testImplementation("junit:junit:4.13.2")
	testImplementation("androidx.test.ext:junit:1.1.5")
	testImplementation("androidx.test.espresso:espresso-core:3.5.1")
	implementation("com.google.code.gson:gson:2.10.1")

}

ktlint {
	android.set(true)
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
