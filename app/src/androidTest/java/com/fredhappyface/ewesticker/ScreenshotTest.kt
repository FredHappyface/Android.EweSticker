package com.fredhappyface.ewesticker

import android.app.UiModeManager
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.takeScreenshot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.io.OutputStream
import java.util.Locale

/*
 * Illustrates usage of APIs to capture a bitmap from view and saving it to test storage.
 *
 * When this test is executed via gradle managed devices, the saved image files will be stored at
 * build/outputs/managed_device_android_test_additional_output/debugAndroidTest/managedDevice/nexusOneApi30/
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

	// a handy JUnit rule that stores the method name, so it can be used to generate unique
	// screenshot files per test method
	@get:Rule
	var nameRule: TestName = TestName()

	private val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
	private lateinit var activityScenario: ActivityScenario<MainActivity>

	@Before
	fun setUp() {
		// Launch the main activity of your app
		activityScenario = ActivityScenario.launch(MainActivity::class.java)
	}

	/**
	 * Captures and saves an image of the entire device screen to storage.
	 */
	@Test
	fun mainActivityLight() {
		val bitmap = takeScreenshot()
		saveBitmapToMediaStore(appContext, bitmap, nameRule.methodName)
		assert(true)
	}

	@Test
	fun mainActivityDark() {
		val uiModeManager = appContext.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
		uiModeManager.nightMode = UiModeManager.MODE_NIGHT_YES
		activityScenario.recreate()

		val bitmap = takeScreenshot()
		saveBitmapToMediaStore(appContext, bitmap, nameRule.methodName)

		uiModeManager.nightMode = UiModeManager.MODE_NIGHT_NO

		assert(true)
	}

	@Test
	fun mainActivityFr() {
		setLocale("fr")
		val bitmap = takeScreenshot()
		saveBitmapToMediaStore(appContext, bitmap, nameRule.methodName)
		assert(true)
	}

	private fun setLocale(languageCode: String) {
		val locale = Locale(languageCode)
		val config = Configuration()
		config.setLocale(locale)
		InstrumentationRegistry.getInstrumentation().targetContext.resources.updateConfiguration(
			config,
			null,
		)
	}

	private fun saveBitmapToMediaStore(context: Context, bitmap: Bitmap, fileName: String) {
		// Define the subdirectory where the image will be saved (e.g., "Screenshots")
		val subdirectory = "Screenshots/"

		// Prepare the content values for the MediaStore
		val values = ContentValues()
		values.put(MediaStore.Images.Media.DISPLAY_NAME, "screenshot_$fileName")
		values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
		values.put(
			MediaStore.MediaColumns.RELATIVE_PATH,
			"${Environment.DIRECTORY_DCIM}/$subdirectory",
		)
		values.put(MediaStore.Images.Media.IS_PENDING, 1) // Mark the image as pending

		// Insert the image into the MediaStore
		val uri =
			context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
		uri?.let {
			try {
				// Open an OutputStream to the newly created image
				val os: OutputStream? = context.contentResolver.openOutputStream(uri)

				// Compress and save the bitmap to the OutputStream
				bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os)
				os?.close()

				// Mark the image as non-pending
				values.clear()
				values.put(MediaStore.Images.Media.IS_PENDING, 0)
				context.contentResolver.update(uri, values, null, null)
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
	}
}
