# Personal LANraragi

A series-first LANraragi extension for Mihon.

## Install

1. Add the [repository index](../../releases/latest/download/index.pb) to Mihon's extension repositories.
2. Install **Personal LANraragi**, then set your server URL and API key in its source settings.

The APK is also available in the [latest release](../../releases/latest).

## Library

Browse series using LANraragi's `series:` tags. Open a series to load its chapters, ordered by `chapter:` tags. Chapter editions remain available within the same series.

## Development

See [CONTRIBUTING.md](CONTRIBUTING.md) and the [publishing workflow](../../actions/workflows/build_push.yml).

Increment `versionCode` in `src/all/lanraragi/build.gradle.kts` for each user-facing change. The extension version is `libVersion.versionCode` (for example, `1.6.9`). Publishing uses that same version for the release tag (`v1.6.9`) and title (`Personal LANraragi 1.6.9`); each version is published once.

Derived from [Keiyoushi extensions-source](https://github.com/keiyoushi/extensions-source). [Apache 2.0](LICENSE).
