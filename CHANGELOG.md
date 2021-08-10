# Changelog
All major and minor version changes will be documented in this file. Details of
patch-level version changes can be found in [commit messages](../../commits/master).


## NextVer - 2021/08/10
- replace old apng lib with 'com.linecorp:apng:1.11.0'

## 20210810 - 2021/08/10
- Code optimisations
  - Code clean-up
  - Removed APNG animation due to memory leak
  - Linting fixes
- Added caching functionality
  - to improve performance of fallback stickers
  - to enable addition of recent list
  - to improve switching packs performance
- Updated gradle and deps
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
