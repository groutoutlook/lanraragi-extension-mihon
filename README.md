# Personal LANraragi

A personal Mihon extension that groups chapter archives into series.

![Personal LANraragi icon](src/all/lanraragi/res/mipmap-xhdpi/ic_launcher.png)

# Setup

After the first successful [GitHub Actions release](https://github.com/groutoutlook/lanraragi-extension-mihon/actions/workflows/build_push.yml), copy this r [... 0 more matches]

```text
https://github.com/groutoutlook/lanraragi-extension-mihon/releases/latest/download/index.pb
```

[Select/open the repository index](https://github.com/groutoutlook/lanraragi-extension-mihon/releases/latest/download/index.pb) · [Latest release and APK](h [... 1 more match]

1. In Mihon, open **More → Settings → Browse → Extension repos → Add**, then paste the URL above.
2. Open **Browse → Extensions**, refresh, and install **Personal LANraragi**. Trust the extension when prompted.
3. In its source settings, set **Custom base URL** to your LANraragi address, for example `http://192.168.1.20:3000`. Enter its **API Key** if No-Fun Mode is enabled, then restart Mihon. `127.0.0.1` refers to the Android device, so use your server's LAN address.
4. Open **Personal LANraragi** and add the grouped series to your library. Standard LANraragi remains a separate installation.

This link becomes available only after publishing; editing files locally does not update the installed extension. Refresh the Extensions screen after future releases. Increase `versionCode` in the extension's Gradle file for each update.

## Grouping rules

- Archives with the same `series:` tag are grouped under that series name. The `chapter:` tag supplies the chapter number, including decimals. These tags take precedence over archive titles, so `Yogurt Lady (Official)_c057` with `series:Yogurt Lady, chapter:057` appears under **Yogurt Lady** as **Chapter 57**.
- `official` and `uncensored` tags label chapter editions in Mihon's scanlator field. Editions stay in the same series, and archives with the same chapter number remain separate readable chapters.
- When tags are missing, archive titles ending in `_c001`, `-c002`, ` ch. 3`, or ` Chapter 4.5` supply the series name and chapter number. An optional `.zip`, `.cbz`, `.rar`, `.cbr`, `.7z`, or `.pdf` suffix is accepted. Chapters are sorted numerically; standalone archives and unmarked titles ending only in a number remain separate entries.
- Grouping uses metadata reported by LANraragi and does not change server metadata or files. Loose image folders must first be imported in a format LANraragi supports.
- Browsing collects all matching server pages before grouping to avoid splitting a series across pages. Large libraries may take longer to load. Opening a series searches for its matching archives again, so newly imported chapters appear on refresh.
- This personal package has a different name, icon, package ID, and source ID from the standard extension. Existing library entries are not automatically migrated; use Mihon's migration feature or add the grouped entry yourself.

## Development

Only `src/all/lanraragi` is loaded. `core`, `compiler`, `common`, and `gradle` remain because the extension depends on that build infrastructure. Repository utilities are PowerShell scripts; local builds are opt-in. See [AGENTS.md](AGENTS.md) and [CONTRIBUTING.md](CONTRIBUTING.md) for source conventions.

Derived from [Keiyoushi extensions-source](https://github.com/keiyoushi/extensions-source). Licensed under [Apache 2.0](LICENSE).
