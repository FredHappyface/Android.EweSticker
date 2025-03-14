# Changelog

All major and minor version changes will be documented in this file. Details of
patch-level version changes can be found in [commit messages](../../commits/master).

<!--
## Next_Ver
-->

## 20250217

- Fix critical bug with the logger crashing the app

## 20250209

- Update dependency versions
- Add a shortcut to google keyboard (fixes #76)
- Add SVG image support
- Code quality improvements
- Use xLog (https://github.com/elvishew/xLog) to capture today's logs (to assist with debugging)
- Make PNG sticker fallback configurable and improve share sheet (fixes #80)
- Improve toast logging experience

## 20240825

- Add case insensitive sort
- Add app version in-app

## 20240322

- Add sticker search
- Add haptic feedback option
- Update translations from Weblate (thank you to the following!)
	- Indonesian
		- Reza Almanda <rezaalmanda27@gmail.com>
- Update screenshots and docs
- Update deps

## 20231008

- Update Fastlane Metadata
- Update to use API level 34
- Minor tweaks to readme
- Add translations from Weblate (thank you to the following!)
	- German
		- Ettore Atalan
	- French
		- J. Lavoie
- Add Android 13 Icon
- Code clean up and `ktlint` check and formatting
- Fix Bug: Keyboard has a transparent background
- Fix Bug where the keyboard forgets last 'Recent' last selected

## 20230828

- Update app description
- Add German (de) translations with help from google translate and friends. Contributions to enhance
  this are very welcome!

## 20230825

- Update launcher icon, version, screenshots

## 20230824

- Add Feature: switch between folders by swiping (closes issue #33)
- Add Progress bar for sticker import (closes issue #51)

## 20230823

- Update dependencies
- Performance improvements for sticker import (3-4x faster)
- Performance improvements for sticker keyboard (recyclerview)
- Add `example-files`
- Add restore prior keyboard on minimize (closes issue #49)
- Add send stickers with system sharesheets (closes issue #48)
- Add reload stickers from directory (closes issue #46)

## 20220311 - 2022/03/11

- New Feature: Improve error messages for the user per https://github.com/FredHappyface/Android.EweSticker/issues/39
- New Feature: Add [Help](/documentation/help) to provide guidance for error messages
- New Feature: Add Gradle tasks
	- ktlintCheck (`gradlew ktlintCheck`): run ktlint over the codebase
	- genDocs (`gradlew genDocs`): generate the api reference using dokka
- New Feature: Update navbar theme (dark/light rather than the app accent colour)
- Bugfix: Refactor in response to bug found when investigating https://github.com/FredHappyface/Android.EweSticker/issues/37
- Bugfix: Back button now enabled in fresh install per https://github.com/FredHappyface/Android.EweSticker/issues/38
- Update: dependencies
- Update: tutorial, and tutorial location to [Tutorials](/documentation/tutorials)

## 20220128 - 2022/01/28

- Highlight the selected tab. https://github.com/FredHappyface/Android.EweSticker/issues/29
- Add support for video formats https://github.com/FredHappyface/Android.EweSticker/issues/34
	- "video/3gpp", "video/mp4", "video/x-matroska", "video/webm"
- Reformat
- Update dependencies
- Limit sticker pack size to resolve `java.lang.OutOfMemoryError: at androidx.gridlayout.widget.GridLayout`

## 20220103 - 2022/01/03

- Add 'Enable Keyboard' section with 'Launch Settings' button. https://github.com/FredHappyface/Android.EweSticker/issues/31
- Show back button in navbar by default. https://github.com/FredHappyface/Android.EweSticker/issues/32

## 20211118 - 2021/11/18

- Attempt to resolve issue reported where the app crashed when importing stickers (suspected cause
  of a `java.lang.OutOfMemoryError`)
- Improve sticker layout https://github.com/FredHappyface/Android.EweSticker/issues/24
- Improve large sticker preview https://github.com/FredHappyface/Android.EweSticker/issues/25
- Recent tab can now be saved as last used https://github.com/FredHappyface/Android.EweSticker/issues/30

## 20211114 - 2021/11/14

- Reopen last used pack https://github.com/FredHappyface/Android.EweSticker/issues/14
- Variable number of columns in vertical scroll https://github.com/FredHappyface/Android.EweSticker/issues/16
- Nested directory structures now supported https://github.com/FredHappyface/Android.EweSticker/issues/17
	For example:
	```none
	/root
		/sticker-pack-name-1
							/sticker-1
							/sticker-2
		/sticker-pack-name-2
							/sticker-pack-name-3
												/sticker-1
												/sticker-2
							/sticker-1
							/sticker-2
		/sticker-1
		/sticker-2
	```
- Recent stickers are in the expected order https://github.com/FredHappyface/Android.EweSticker/issues/18
- Back button now switches to the previously used keyboard (api>=28) https://github.com/FredHappyface/Android.EweSticker/issues/23
- Localisations for Sticker Path: Not Set, Sticker Date: Never
- Code clean up + documentation improvements
- The new target SDK version is 31 (Android 12) - previously 30 (Android 11)
- The new minimum SDK version is 26 (Android 8 Oreo) - previously 28 (Android 9 Pie)

## 20211029 - 2021/10/29

- Add support for vertical scroll https://github.com/FredHappyface/Android.EweSticker/issues/8
- Long press on a sticker to show a preview https://github.com/FredHappyface/Android.EweSticker/issues/10
- Add Spanish translation https://github.com/FredHappyface/Android.EweSticker/pull/13
- Use coil https://coil-kt.github.io/coil/ for supported image types to improve performance
- Use JavaVersion.VERSION_11 in place of JavaVersion.VERSION_1_8
- Use GridLayout in place of linear views
- Refactor and code clean up (reduced ImageKeyboard.kt by about 17% sloc, reduced MainActivity.kt by about 18% sloc)
- Update UI (now more material you inspired)
- Update screenshots
- Update tutorial
- Update fonts (using fira sans ttf)

## 20211011 - 2021/10/11

- attempt to fix https://github.com/FredHappyface/Android.EweSticker/issues/7
	by using `layoutInflater.inflate(R.layout.image_container, imageContainer, false)`
	in place of `View.inflate(applicationContext, R.layout.image_container, null)`
- additional logging to predict `java.lang.IllegalStateException` (no prevention)
	in the hope that this provides additional context to make fixing easier if
	`imageContainer.addView(imageContainerLayout)` raises in the future
- Performance improvements to cache miss in `switchImageContainer`
	(call `createImageContainer(stickers)` once)
- update screenshots with new phone frame
- fix sticker shadows on light mode
- tidy up files committed to git

## 20210909 - 2021/09/09

- update ui
	- use MaterialCardView and Snack-bar in settings (MainActivity)
	- improvements to the keyboard layout
	- theme improvements
- general code improvements
- stability improvements

## 20210810 - 2021/08/10

- Code optimisations
	- Code clean-up
	- Removed APNG animation due to memory leak
	- Linting fixes
- Added caching functionality
	- to improve performance of fallback stickers
	- to enable addition of recent list
	- to improve switching packs performance
- Updated gradle and dependencies
- Add recent icon

## 20210723 - 2021/07/23

- Added link to online tutorial (on GitHub)
- Ignore the compat sticker when creating groups
- SVG back arrow
- Add headings to UI
- Removed unnecessary permissions

## 20210612 - 2021/06/12

- Changes from upstream:
	- Merged `ui-update`, `feature_distributed_apks`, `fallback`. Providing a fresher
		ui and the ability to send a fallback sticker for unsupported formats.
	- Converted to Kotlin
	- Provided fastlane metadata for fdroid
	- Provided tutorial (TUTORIAL.md)
	- Cleaned up assets and codebase
	- Changed package name to `com.fredhappyface.ewesticker`
	- Changed app icon

## app logo - 2021/06/11

- Change package name
- Create app logo
- Compile APKs

## first release - 2021/06/11

- Add to gh
- todo...
	- compile apks
	- create changelog
	- screenshots
	- app logo
	- stick on fdroid + google play
