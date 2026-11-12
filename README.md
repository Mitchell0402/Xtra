# Lagoon

<img src="app/src/main/ic_launcher-web.png" align="left" width="100"/>

Lagoon is an ad-free Twitch player and browser for Android, a fork of [Xtra](https://github.com/crackededed/Xtra).

</br>
</br>

## What is different from Xtra

- Detects Twitch mid-roll ads and keeps playing without them: it races several clean stream sources at startup,
  switches to a clean source at the ad boundary, and hides or skips ad segments as a fallback.
- Settings > Ad blocking with presets, statistics and an ad log.
- Recordings skip ad segments.
- Own app id (`io.github.mitchell0402.lagoon`), so it installs next to Xtra instead of replacing it.

## Download

Signed APKs are on the [releases page](https://github.com/Mitchell0402/Xtra/releases).

## Building a release

Release builds are signed with the key named in `keystore.properties` in the project root (git-ignored):

```
storeFile=/path/to/lagoon-release.jks
storePassword=...
keyAlias=lagoon
keyPassword=...
```

The same values can come from the `LAGOON_KEYSTORE_FILE`, `LAGOON_KEYSTORE_PASSWORD`, `LAGOON_KEY_ALIAS` and
`LAGOON_KEY_PASSWORD` environment variables. Without them, `assembleRelease` falls back to the debug key.

## License
Lagoon, like Xtra, is licensed under the [GNU Affero General Public License v3.0](LICENSE).
