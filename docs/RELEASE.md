# Releasing Block (Zapstore and F-Droid)

Block is released in two places, and only these:

- **Zapstore**: the signed APK attached to a GitHub release, published to Nostr with the `zapstore` command-line tool.
- **F-Droid**: F-Droid builds the app itself from this repository's source at a release tag.

Both read the store listing from [`fastlane/metadata/android/`](../fastlane/metadata/android/), so the text and pictures live in one place.

## 1. The signing key (once)

Keep the keystore and its passwords safe and **never commit them**. `.gitignore` already excludes `*.jks`, `*.keystore` and `keystore.properties`.

```bash
keytool -genkeypair -v -keystore release.jks -alias blockface -keyalg RSA -keysize 4096 -validity 10000
```

The alias `blockface` dates from before the app was renamed Block. If you already made this keystore, keep it: an alias can't change.

Create `keystore.properties` in the project root:

```properties
storeFile=release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=blockface
keyPassword=YOUR_KEY_PASSWORD
```

**Back up the keystore in two places.** There is no store that can reset it. Every update must be signed with the same key, or Android refuses to install it over the old one, and people would have to uninstall (losing their settings) to update.

## 2. Version and changelog

In `app/build.gradle.kts`, set:
- `versionCode`: raise it by 1 for every release;
- `versionName`: for example `1.0.1`.

Write what changed, in at most 500 characters, to:
- `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`;
- `fastlane/metadata/android/th/changelogs/<versionCode>.txt` (Thai), if you can.

F-Droid shows these files next to the update.

## 3. Test the release build

R8 shrinking is on for release builds, so test the release APK itself, not only debug.

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew assembleRelease
```

Install `app/build/outputs/apk/release/app-release.apk` on a phone and check:

- [ ] **Bitcoin:**
  - [ ] Settings › Bitcoin and widgets and its Widgets page: your stack, currency, sources, which panels the widget shows.
  - [ ] Add each widget, resize it and tap it.
- [ ] **Log in** with each method: nsec, Amber, and a remote signer (bunker).
- [ ] **Timeline:** scrolls smoothly, photo albums, videos, a quoted note, Bitcoin transaction rows and their sheet.
- [ ] **Post, reply and quote:**
  - [ ] with an image;
- [ ] **Private messages:** send and receive.
- [ ] **Wallet tab:** connect an NWC wallet; balance and recent payments; Receive (invoice, QR code, copy, share); Send (paste and scan, the amount shows, confirm).
- [ ] **New post:** the floating + at the bottom right opens the composer.
- [ ] **Zaps:**
  - [ ] with an NWC wallet;
  - [ ] without one (an external wallet app opens).
- [ ] **Live:** a stream (side panel › Live), its chat and details sheet, the mini player and picture-in-picture.
- [ ] **Search, profile, bookmarks, lists.**
- [ ] **Themes:** Block, Ember, Gilded, Kyriaki. **Language:** Thai.
- [ ] **Airplane mode:** cached data shows, and the widgets keep their last values.
- [ ] **Phone notifications** for Nostr: the permission prompt appears when you turn them on, then the "instant?" question.
- [ ] **Instant notifications:** with the app closed and the screen off, a reply or DM from another account arrives within seconds; still after a restart of the phone; turning it off removes the "Instant notifications are on" notification.

## 4. Tag and make a GitHub release

F-Droid finds new versions by their tags, so always tag releases as `v<versionName>`.

```bash
git tag v1.0.1
```

```bash
git push origin v1.0.1
```

Build the signed APK at that tag and check its signature:

```bash
./gradlew assembleRelease
```

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

On GitHub, make a release from the tag:
- attach the APK, renamed `block-v1.0.1.apk`;
- paste the changelog.

## 5. Publish to Zapstore

Zapstore publishes apps as Nostr events signed by the developer. People see that the APK comes from your npub.

1. **Install the `zapstore` command-line tool**, following Zapstore's developer documentation.
2. **Add a `zapstore.yaml` at the project root** that points at the GitHub releases. Check the tool's current documentation for the exact fields. A starting point:

   ```yaml
   name: Block
   repository: https://github.com/phirapath21/Block
   license: MIT
   tags: [bitcoin, nostr, widgets]
   assets:
     - .*\.apk$
   ```

3. **Run the publish command** from the project root. It fetches the newest release APK, reads the listing, and signs the events.
   - Sign with your developer key through a remote signer (bunker) rather than pasting the nsec.
   - Use the same npub every time: it is how Zapstore users know updates are yours.

## 6. Publish to F-Droid

### First time

1. On GitLab, fork [fdroiddata](https://gitlab.com/fdroid/fdroiddata).
2. Add `metadata/io.github.phirapath21.btcwidget.yml`:

   ```yaml
   Categories:
     - Internet
     - Money
   License: MIT
   AuthorName: phirapath21
   SourceCode: https://github.com/phirapath21/Block
   IssueTracker: https://github.com/phirapath21/Block/issues

   AutoName: Block

   RepoType: git
   Repo: https://github.com/phirapath21/Block.git

   Builds:
     - versionName: 1.0.0
       versionCode: 1
       commit: v1.0.0
       subdir: app
       gradle:
         - yes
       ndk: 27.2.12479018

   AutoUpdateMode: Version
   UpdateCheckMode: Tags
   CurrentVersion: 1.0.0
   CurrentVersionCode: 1
   ```

3. Test the build with `fdroidserver`. Its Docker image is easiest:

   ```bash
   fdroid build -v -l io.github.phirapath21.btcwidget
   ```

4. Open a merge request to fdroiddata. Reviewers may ask for changes, and the first review can take a few weeks.

### Later updates

These are automatic. F-Droid checks the tags (`UpdateCheckMode: Tags`), builds the new `v…` tag, and publishes it, usually within a few days.

### Things F-Droid checks

- **Only free software:** Block uses no Google Play Services, Firebase or other closed libraries, and the QR scanner is CameraX + ZXing.
  - The build doesn't embed Google's encrypted dependency report (`dependenciesInfo` is off in `app/build.gradle.kts`).
- **Anti-features:** Block reads market data from services that aren't free software (Coinbase, Kraken, Bitkub, Yahoo Finance, CoinGecko, blockchain.info).
  - Reviewers will likely mark it **NonFreeNet**.
  - That's normal for apps like this, and it doesn't stop the listing.
- **Signing:** by default F-Droid signs its build with its own key. People then can't update between the F-Droid and Zapstore versions without reinstalling.
  - To let them, make the build **reproducible** and add `AllowedAPKSigningKeys` (your certificate's SHA-256) and `Binaries` (the GitHub release APK URL) to the metadata. F-Droid then ships your own signed APK once it rebuilds the same bytes.
  - This is optional, and can be added later.

## Notes

- **Application ID:** `io.github.phirapath21.btcwidget`. It is permanent once published.
- **SDK:** min SDK 24 (Android 7.0), target SDK 36.
- **The store listing** is in `fastlane/metadata/android/` in English and Thai:
  - title, short and full description;
  - icon, feature graphic and phone screenshots;
  - changelogs.
  - Add new screenshots to `images/phoneScreenshots/` (numbered `1.png`, `2.png`…).
