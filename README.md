# Portix

<img src="branding/portix-512.png" width="128" align="right" alt="Portix icon">

An Android app for social networks without their apps: one tab per account (Instagram, TikTok,
Facebook…, signed in or not), links received in WhatsApp or anywhere else open in the right account,
and everything else in a temporary browser that forgets everything. It runs on Firefox's engine
(GeckoView) so that real browser extensions block ads, trackers, cookie banners and the "open the
app" nags. The interface is in French.

Formerly Vigie (package `app.vigie`): Portix is a new package, `app.portix`, installed next to it. At its first
start, an empty `Documents/Portix/` gets a copy of `Documents/Vigie/accounts.json` (the old file stays).

## What it does

- **No account by default.** You add each one yourself (address, optional tab name): Instagram,
  TikTok, any network, or any site. Each account is a tab at the top; long-press a tab and drag it to
  reorder, or use the arrows in Réglages. An account can be signed in or not.
- **Links open in the right place.** A link to a network you have an account for opens in that
  account's tab; a link to anything else opens in the **temporary browser**. With two accounts on the
  same network, the one marked « Ouvrir les liens ici » gets the links.
- **The temporary browser** is a private session: no account, nothing written to disk, everything
  forgotten when you close it (its « Temporaire » tab, or the banner's « Fermer »). It has the same
  blockers. Its menu offers « Ouvrir dans le navigateur du téléphone » and « Ajouter ce site comme
  compte ».
- **Each network only sees its own pages.** A link tapped inside a tab that leads to another network
  goes to that network's account (or the temporary browser); a link to an article goes to the
  temporary browser. Sign-in pages a site sends you to (Google, Apple, Facebook login) and redirects
  stay in the tab, so "sign in with…" keeps working.
- **Links are cleaned**: link shims (`l.instagram.com`, `l.facebook.com`, Google's `/url`) are
  unwrapped to their real target, and sharing trackers are dropped (`igsh`, TikTok's `_t`/`_r`, X's
  `s`/`t`, YouTube's `si`, `utm_*`, `fbclid`…). Instagram reels and posts get `l=1`, which skips
  Instagram's « open the app / see on the web » page (what its own « Voir sur le web » adds).
- **Logins stay**, **tabs stay loaded**, **each tab reopens on its last page** (pages of that account
  only), back walks the tab's history then returns to the app you came from.
- **Per-account options** (tab menu → Modifier, or Réglages): desktop version, « Ouvrir les liens
  ici », « Effacer cookies et données » (logs that account out only), delete.

## Start-up

A link opening Portix loads at once: that start skips the update checks and syncs 4 seconds later,
once the page is on its way. Until that sync the account list is read-only, so a change made on this
phone cannot overwrite one made on another phone in the meantime (Synchrotron keeps the newer file).
If the sync removes the account whose tab is on screen, its page carries on in the temporary browser
instead of vanishing; a link still waiting for a removed account is routed again. Once the extensions were installed, tabs no longer wait for them:
Gecko starts them with the engine, and uBlock Origin holds the network until its lists are loaded.
Réglages → Dernier démarrage shows the last cold start step by step (engine, extensions, page
requested, page shown), to tell a slow engine from a slow site.

## Opening the networks' links with Portix

Android only gives a web link to an app the site itself vouches for (an `assetlinks.json` file on
the domain), which only the networks' own apps have. Any other app must be allowed by the user, once,
per address, and only for addresses no installed app has verified: an app that did (preinstalled Meta
apps on Samsung, even disabled) keeps them, and Android unticks them for Portix. Réglages → Liens
shows how many addresses are ticked (no banner elsewhere: leaving some unticked is a choice) and lists the apps that keep some; turning off their « Ouverture
liens pris en charge » (or uninstalling them) frees the addresses.

1. Réglages → Liens → « Ouvrir le réglage Android » (or Android's Settings → Apps → Portix →
   Définition par défaut).
2. Turn on « Ouverture liens pris en charge », then in « Adr. Web prises en charge » tick everything.

From then on a link tapped in WhatsApp, SMS, mail… opens in Portix. The networks' apps must not be
installed (they would claim the links themselves). For a link outside the list: Partager → Portix.

The networks Portix knows, and so the addresses it can be given, are fixed in the app
(`links/Networks.kt`, mirrored in the manifest; a test keeps them in step): Instagram, TikTok,
Facebook, X, YouTube, Reddit, LinkedIn, Pinterest, Threads, Bluesky, Snapchat, Twitch, Tumblr. Each
with its short-link and mobile hosts (`vm.tiktok.com`, `youtu.be`, `redd.it`, `fb.watch`, `t.co`,
`m.`…), 89 hosts in all. Adding a network needs a new build. An account on a site outside this list
works as a tab, and gets links to its own site from the temporary browser, but Android cannot hand it
links from other apps.

## Privacy

Same engine and protections as Échoppe:

| Measure | How |
|---|---|
| Extensions | [uBlock Origin](https://github.com/gorhill/uBlock) (GPL-3.0) and [Consent-O-Matic](https://github.com/cavi-au/Consent-O-Matic) (MIT), installed from addons.mozilla.org at the first start (no page loads before), allowed in the private temporary browser too, updated every 4 days. Their popups open from the tab menu (per-site switch, element picker). |
| uBlock, once per phone | Réglages, with a reminder banner until done: subscribe to Fanboy's Mobile Notifications (the "open in the app" banners, in no list of uBlock's catalogue), one tap on uBlock's own subscribe page; and tick the « Nuisances » (Annoyances) lists (cookie notices, chat widgets, newsletter pop-ups). |
| One isolated context per account | `account_<id>`: separate cookies, storage, cache. The temporary browser is a private session. |
| Firefox's protections | Enhanced Tracking Protection strict, Total Cookie Protection, query stripping, Global Privacy Control. |
| No exit to other apps | Only `http(s)` pages load; `intent://`, `market://`, custom schemes and store links are refused (an `intent://` web fallback opens in the tab); `window.open()` and `target=_blank` stay in the app. |
| No permissions | Location, camera, microphone, notifications: always refused. |
| No telemetry | No analytics, login saving off, app backup off. |

Limits:

- Instagram without an account shows a sign-in wall after a few videos; signed in, it works.
- TikTok's website pushes the app and limits some videos.
- If WhatsApp opens a link in its own in-app browser instead of handing it to Android, Portix never
  sees it.
- APK about 90 MB (the Firefox engine, arm64 only); GeckoView is pinned in `app/build.gradle.kts`
  and must be bumped with each Firefox release for its security fixes.

## Data

The account list is `Documents/Portix/accounts.json`:

```json
{
  "format_version": 1,
  "accounts": [
    { "id": "k3v9x0a1qz", "name": "Instagram", "url": "https://www.instagram.com/", "desktop": false, "link_default": false }
  ]
}
```

In tab order; unknown fields are ignored. `id` is random and never changes: it names the account's
private context, so renaming keeps its login. Written atomically (`.<name>.synctmp-<uuid>`, flushed,
renamed); an unreadable file is reported and never overwritten; a newer `format_version` is refused.
Cookies, logins, history, each tab's last page, the selected tab and the extensions' settings are
device-only. The folder needs "all files access", asked with a banner; until then the list is empty
and read-only.

## Proton Drive sync (Synchrotron)

Add `Documents/Portix` in [Synchrotron](https://github.com/KrustyLeBot/Synchrotron) (e.g. with
`Apps/Portix` on Proton Drive). Each time Portix opens it asks for a sync; the account list is read-only
while it runs (browsing goes on) and is re-read afterwards. An account added on one phone appears on
the others, signed out.

## Get the APK

Every push to `main` runs the unit tests, builds the APK signed with the release key and publishes it
as the single `latest` release (`Portix.apk`, titled "Portix build N"; N is the version code). Signing
key, secrets and CI: `docs/RELEASE.md`. The app offers each new build with a banner (Réglages → Mise
à jour checks by hand), checks its size and SHA-256 and opens Android's installer.

## Build locally

JDK 17 and the Android SDK platform 37.2 (GeckoView is built against it: AGP 9.5, Kotlin 2.4).
Without the release key a local build is signed with the SDK's debug key:

```
./gradlew testDebugUnitTest assembleRelease
```

## Project layout

```
app/src/main/java/app/portix/
  PortixApp.kt, MainActivity.kt (incoming links), PortixViewModel.kt (accounts, routing, sync)
  links/      Networks (the catalogue), Links (cleaning, routing, in-tab rules), pure Kotlin
  data/       Account + WebAddress, AccountsFile (JSON format), AccountStore, AtomicFiles
  web/        Browser (engine and protections), Extensions, Tabs (one context per account, the
              private temporary browser, in-tab routing), Navigation (no app links), Prompts,
              Downloads, PhoneBrowser (hand-off to the phone's browser, link settings)
  ui/         MainScreen, SettingsScreen, AccountDialog, theme
  suite/      suite kit copied from Synchrotron
  sync/       SynchrotronClient, copy of Synchrotron's client library
app/src/test/   unit tests (routing, cleaning, catalogue vs manifest, file format, addresses)
branding/   icon: portix.svg, adaptive-icon layers, render-icons.js, monochrome.py
```
