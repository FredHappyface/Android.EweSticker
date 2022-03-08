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
		}
	}
}

tasks.register("genDocs") {
	val ref = buildDir.resolve("../../documentation/reference")
	dependsOn("dokkaGfm")
	copy {
		from("$ref/index.md")
		into(ref)
		rename { "README.md" }
	}
}

android {
	compileSdk = 31
	buildToolsVersion = "30.0.3"

	defaultConfig {
		applicationId = "com.fredhappyface.ewesticker"
		minSdk = 26
		targetSdk = 31
		versionCode = 20220128
		versionName = "2022.01.28"
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
		sourceCompatibility(JavaVersion.VERSION_11)
		targetCompatibility(JavaVersion.VERSION_11)
	}

	kotlinOptions { jvmTarget = "11" }
}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.10")
	implementation("androidx.core:core-ktx:1.7.0")
	implementation("androidx.appcompat:appcompat:1.4.1")
	implementation("com.google.android.material:material:1.5.0")
	implementation("androidx.preference:preference-ktx:1.2.0")
	implementation("io.coil-kt:coil:1.4.0")
	implementation("io.coil-kt:coil-gif:1.4.0")
	implementation("io.coil-kt:coil-video:1.4.0")
	implementation("androidx.gridlayout:gridlayout:1.0.0")
	testImplementation("junit:junit:4.13.2")
	testImplementation("androidx.test.ext:junit:1.1.3")
	testImplementation("androidx.test.espresso:espresso-core:3.4.0")
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
