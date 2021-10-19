# Changelog
All major and minor version changes will be documented in this file. Details of
patch-level version changes can be found in [commit messages](../../commits/master).

## 202110xx - 2021/10/xx

- Use glide for supported image types to improve performance
	- fallback to `ImageDecoder.decodeDrawable` for `image/webp` and `image/heif`
- Add support for vertical scroll https://github.com/FredHappyface/Android.EweSticker/issues/8
- Refactor and code clean up
- TODO update screenshots with new phone frame

## 20211011 - 2021/10/11

- Performance improvements to cache miss in `switchImageContainer`
  (call `createImageContainer(stickers)` once)
- update screenshots with new phone frame
- fix sticker shadows on light mode
- tidy up files committed to git
- attempt to fix https://github.com/FredHappyface/Android.EweSticker/issues/7
  by using `layoutInflater.inflate(R.layout.image_container, imageContainer, false)`
  in place of `View.inflate(applicationContext, R.layout.image_container, null)`
- additional logging to predict `java.lang.IllegalStateException` (no prevention)
  in the hope that this provides additional context to make fixing easier if
  `imageContainer.addView(imageContainerLayout)` raises in the future

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
