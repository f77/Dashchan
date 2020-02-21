# Changelog
The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
### Changed
### Deprecated
### Removed
### Fixed
### Security


## [2.12.2] - 2020-02-21
### Added
- Added install instructions in English in the README.md.
### Changed
- Updated version history links in the CHANGELOG.md.

## [2.12.1] - 2019-12-13
### Added
- Added the preferences for the fast search in the "interface" section.

## [2.12.0] - 2019-12-11
### Added
- Added the beta version of "fast search" of the replies to the user posts.

## [2.11.1] - 2019-12-09
### Changed
- Reverted the dirty huck of WatcherService.

## [2.11.0] - 2019-12-08
### Added
- Added more faster Chrome Tabs instead of the WebView browser. (You still may disable Chrome Tabs in preferences).

## [2.10.18] - 2019-10-30
### Changed
- OLED theme has been fixed. The color of text is full white now.

## [2.10.17] - 2019-09-27
### Changed
- Migrated from GCC to Clang. NDK Revision r20 is used now.

## [2.10.16] - 2019-09-27
### Added
- Added the display of the total count of posts in the thread's summary.

## [2.10.15] - 2019-09-27
### Added
- Added the support of the automatically uploading to GitHub releases in the gradle script.

### Fixed
- Fixed YouTube videos' titles.

## [2.10.14] - 2019-09-25
### Changed
- The app has been rebuilt to the older targetSdkVersion 25. Maybe, you need to delete the previous version (targetSdkVersion 29) before install this update.

## [2.10.13] - 2019-09-25
### Changed
- Updated README.md.

## [2.10.12] - 2019-09-25
- Distribution system test 2.

## [2.10.11] - 2019-09-25
### Changed
- UPDATE_SOURCE_URI_STRING has been changed to f77's fork github page.

## [2.10.10] - 2019-09-25
### Changed
- Distribution system test 1.

## [2.10.9] - 2019-09-25
### Changed
- Research of fingerprints.

## [2.10.8] - 2019-09-25
### Fixed
- Fixed a bug that appeared after android api 28: an exception throws while creating a notification about successful file downloading.

## [2.10.7] - 2019-09-25
### Changed
- The android app's version is equal to the git version now.
- App's version code takes from git.
- Changed signingConfigs.

## [1.0.5] - 2019-09-24
### Fixed
- Fixed the git version.

## [1.0.4] - 2019-09-24
### Changed
- Updated the versions generation code.

## [1.0.3] - 2019-09-24
### Changed
- Updated README.md.

## [1.0.2] - 2019-09-24
### Added
- Added the possibility to change posts rating (tap to target post, select the menu option). The extension must implement the special method to this.
### Changed
- The app name has been changed to "Dashchan Continued".
- Preferences.DEFAULT_PARTIAL_THREAD_LOADING has been changed to "false" for compatibility to the new dvach extension.
- Preferences.DEFAULT_RECAPTCHA_JAVASCRIPT has been changed to "false" for compatibility to the new dvach extension.

## [1.0.1] - 2019-09-22
### Added
- Добавил поля для возможности настройки просмотра и изменения рейтинга поста в классе борды.
- Добавил показ кнопок изменения рейтинга поста, если в конфигурации борды доступно изменение рейтинга.

## [1.0.0] - 2019-09-22
### Added
- Added this changelog.
- Rebuilt to a new Android SDK (29).

[2.12.2]: https://github.com/f77/Dashchan/compare/2.12.1...2.12.2
[2.12.1]: https://github.com/f77/Dashchan/compare/2.12.0...2.12.1
[2.12.0]: https://github.com/f77/Dashchan/compare/2.11.1...2.12.0
[2.11.1]: https://github.com/f77/Dashchan/compare/2.11.0...2.11.1
[2.11.0]: https://github.com/f77/Dashchan/compare/2.10.18...2.11.0
[2.10.18]: https://github.com/f77/Dashchan/compare/2.10.17...2.10.18
[2.10.17]: https://github.com/f77/Dashchan/compare/2.10.16...2.10.17
[2.10.16]: https://github.com/f77/Dashchan/compare/2.10.15...2.10.16
[2.10.15]: https://github.com/f77/Dashchan/compare/2.10.14...2.10.15
[2.10.14]: https://github.com/f77/Dashchan/compare/2.10.13...2.10.14
[2.10.13]: https://github.com/f77/Dashchan/compare/2.10.12...2.10.13
[2.10.12]: https://github.com/f77/Dashchan/compare/2.10.11...2.10.12
[2.10.11]: https://github.com/f77/Dashchan/compare/2.10.10...2.10.11
[2.10.10]: https://github.com/f77/Dashchan/compare/2.10.9...2.10.10
[2.10.9]: https://github.com/f77/Dashchan/compare/2.10.8...2.10.9
[2.10.8]: https://github.com/f77/Dashchan/compare/2.10.7...2.10.8
[2.10.7]: https://github.com/f77/Dashchan/compare/1.0.5...2.10.7
[1.0.5]: https://github.com/f77/Dashchan/compare/1.0.4...1.0.5
[1.0.4]: https://github.com/f77/Dashchan/compare/1.0.3...1.0.4
[1.0.3]: https://github.com/f77/Dashchan/compare/1.0.2...1.0.3
[1.0.2]: https://github.com/f77/Dashchan/compare/1.0.1...1.0.2
[1.0.1]: https://github.com/f77/Dashchan/compare/1.0.0...1.0.1
[1.0.0]: https://github.com/f77/Dashchan/compare/e4ba0c2b2bb22e708992163212d335e498e110de...1.0.0
[Unreleased]: https://github.com/f77/Dashchan/compare/master...master
