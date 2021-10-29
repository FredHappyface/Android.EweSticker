plugins {
	id("com.android.application")
	id("kotlin-android")
}

android {
	compileSdk = 30
	buildToolsVersion = "30.0.3"

	defaultConfig {
		applicationId = "com.fredhappyface.ewesticker"
		minSdk = 28
		targetSdk = 30
		versionCode = 20211029
		versionName = "2021.10.29"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		setProperty("archivesBaseName", "$applicationId-$versionName")
	}

	buildTypes {
		getByName("debug") {
			versionNameSuffix = "-debug"
		}
		getByName("release") {
			versionNameSuffix ="-release"
			proguardFiles("proguard-android-optimize.txt", "proguard-rules.pro")
			isMinifyEnabled = false
		}
	}

	compileOptions {
		sourceCompatibility(JavaVersion.VERSION_11)
		targetCompatibility(JavaVersion.VERSION_11)
	}

	kotlinOptions {
		jvmTarget = "11"
	}
}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
	implementation("androidx.core:core-ktx:1.6.0")
	implementation("androidx.appcompat:appcompat:1.3.1")
	implementation("com.google.android.material:material:1.4.0")
	implementation("androidx.preference:preference-ktx:1.1.1")
	implementation("io.coil-kt:coil:1.4.0")
	implementation("io.coil-kt:coil-gif:1.4.0")
	implementation("androidx.gridlayout:gridlayout:1.0.0")
	testImplementation("junit:junit:4.13.2")
	testImplementation("androidx.test.ext:junit:1.1.3")
	testImplementation("androidx.test.espresso:espresso-core:3.4.0")
}
