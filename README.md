<div align="center">

# Swipey

### Swipe through your camera roll and decide what stays.

<br>

[![Download the APK](https://img.shields.io/badge/Download%20APK-v1.1%20%C2%B7%2026%20MB-2F6BFF?style=for-the-badge&logo=android&logoColor=white&labelColor=17191C)](https://github.com/GarikMartikyan/Swipey/releases/latest/download/Swipey.apk)

<br>

![Android 13+](https://img.shields.io/badge/Android-13%2B-17191C?style=flat-square&logo=android&logoColor=8B9097)
![No internet permission](https://img.shields.io/badge/no%20internet%20permission-3C845A?style=flat-square)
![No ads, no account](https://img.shields.io/badge/no%20ads%2C%20no%20account-17191C?style=flat-square)
![Kotlin](https://img.shields.io/badge/Kotlin-17191C?style=flat-square&logo=kotlin&logoColor=8B9097)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-17191C?style=flat-square&logo=jetpackcompose&logoColor=8B9097)

</div>

<br>

Your camera roll has four thousand photos in it and you are never going to sit down and
sort them. Nobody is. Gallery apps hand you a grid and expect you to make four thousand
decisions while looking at four thousand thumbnails at once.

Swipey deals them to you one at a time, full screen, like cards. **Left to bin, right to
keep.** One photograph, one decision, then the next one. You can do a hundred waiting for a
kettle to boil.

Nothing leaves your phone, and nothing is deleted behind your back.

<br>

## Install it

Swipey is not on the Play Store, so your phone will want to be told this is deliberate.
It takes about thirty seconds.

1. **[Download the APK](https://github.com/GarikMartikyan/Swipey/releases/latest/download/Swipey.apk)** — on the phone itself, not on a laptop.
2. Open it from your notifications, or from **Files → Downloads**.
3. Android will say it doesn't allow installing unknown apps from this source. Tap
   **Settings**, turn on **Allow from this source**, and press back.
4. Tap **Install**, then **Open**.

The first screen asks for your photos and videos. Choose **Allow all** rather than "Select
photos" — with only a handful shared, Swipey can't show you the bin, and so can't promise
that what you removed is recoverable. It will say so and send you to settings if you pick
the other one. That is the only thing it ever asks for; see
[what it asks for](#what-it-asks-for).

> **If the download finishes but no file appears**, look in your browser's Downloads list
> for a blocked entry — Chrome quietly holds back APKs it hasn't seen before, and there is
> a **Download anyway** on it. Failing that, `dist/Swipey.apk` in this repository is the
> same file, byte for byte.

> **Already have Swipey installed from a previous build?**
> If Android refuses the install, uninstall the old copy first. That does not touch your
> photos, but it does clear Swipey's record of what is in your bin — so empty or restore
> the bin before you do it.

<br>

## How it works

| | |
|---|---|
| **Swipe** | A photograph fills the screen. Flick it left to send it to the bin, right to keep it. The card behind is already there, so the next one never appears out of nothing. Change your mind: **Undo** takes back the last decision. |
| **Review** | Nothing has moved yet. When you're done swiping you get the full list of what you marked, with the total size, and you can untick anything you had second thoughts about. |
| **Confirm** | One tap moves the whole batch to your phone's trash. Android asks you to confirm — that dialog is the system's, not Swipey's, and it is the only thing that actually moves a file. |

Along the way there's a **shuffle**, four **sort orders** (newest, oldest, largest,
smallest), your **albums** listed with what each one is costing you in gigabytes, and a
**Recent** tile that puts you back on the card after the last one you judged.

Videos play in the deck — scrub them with your thumb, and hear them if you want to.

<br>

## Nothing goes missing

This is the part worth reading carefully, because it is the whole reason to trust an app
like this one.

- **A swipe deletes nothing.** It marks. Marks live in the session and nothing happens to
  the file until you review the batch and confirm it.
- **Confirming moves items to your phone's trash** — the real one, shared with Google
  Photos and Files. They are recoverable from there, and Swipey's own **Bin** screen tells
  you until when, per item.
- **Space is freed when the trash is emptied, not when you confirm.** Swipey will never
  tell you it saved you 4 GB. It didn't yet.
- **Restoring is one tap**, and Android asks you to confirm that too.
- **Deleting for good is a separate, deliberate act** in the Bin. It is never something a
  swipe can cause, and Android confirms it. Nothing undoes it afterwards.

<br>

## Make it yours

Four settings, behind the ☰ menu on the home screen. Each one says in plain words what it
is currently doing, so you can read the screen without opening anything.

| Setting | What it does |
|---|---|
| **Appearance** | Light or dark. Until you choose, Swipey follows whatever your phone is set to. The deck itself stays dark either way — a photograph judged against a white page is a different photograph. |
| **Swipe direction** | Which side bins. Left by default; flip it and the drag, the badge, the two buttons under the card and the first-run hints all swap together. |
| **Haptic feedback** | Whether the deck answers your thumb — a tick as a swipe passes the point of no return, a knock when it lands. You are looking at the photograph, not at the control, so that confirmation usually arrives through your hand. |
| **Video sound** | Whether clips start with their sound on. Muting one during a session still carries to the clips after it; every launch starts again from this setting. |

<br>

## What it asks for

The APK above declares exactly five permissions, and you can check that yourself with
`aapt2 dump badging Swipey.apk`:

| Permission | Why |
|---|---|
| `READ_MEDIA_IMAGES` · `READ_MEDIA_VIDEO` | To show you your photographs. Without these there is no app. |
| `READ_MEDIA_VISUAL_USER_SELECTED` | Declared because Android requires it alongside the two above. Swipey uses it to *detect* that you've shared only some photos, and then asks for all of them — with a partial share it cannot show you the bin, so it cannot promise that what you removed is recoverable. |
| `ACCESS_NETWORK_STATE` · `WAKE_LOCK` | Pulled in by the image and video libraries. Not used to do anything on your behalf. |

**There is no `INTERNET` permission.** Swipey cannot upload your photographs, phone home,
or show you an advert, because Android will not let it open a socket at all. There is no
account, no sign-in and no analytics.

Trashing and restoring go through Android's own `MediaStore` dialogs — which is why the
system asks you to confirm, and why the items land somewhere your other gallery apps can
see.

<br>

## Build it yourself

```bash
git clone https://github.com/GarikMartikyan/Swipey.git
cd Swipey
./gradlew installDebug     # onto a connected device
./gradlew test             # the unit suite
```

Android Studio, JDK 17, and an Android 13 (API 33) or newer device. There are no keys,
services or `local.properties` entries to fill in for a debug build.

`./gradlew assembleRelease` also works on a fresh clone, and produces an **unsigned** APK —
the release keystore is deliberately not in this repository. The signed build is published
as a [release](https://github.com/GarikMartikyan/Swipey/releases), and the same file is
committed at `dist/Swipey.apk` so it can be checked against the tree it was built from.

<br>

## Under the hood

Kotlin and Jetpack Compose, no Material components — the interface is built from a small
set of Swipey primitives over its own design tokens, which is what keeps the photograph the
brightest thing on the screen. `MediaStore` for the gallery, Room for the trash
bookkeeping, ExoPlayer for video, Coil for thumbnails.

The `domain/` package is pure Kotlin with no `android.*` imports at all — the swipe state
machine, the sort orders, the trash reconciliation and the resume logic are all plain
functions with plain tests, and a unit test fails the build if an Android import ever
appears there.

<br>

---

<div align="center">

**[⬇ Download Swipey v1.1](https://github.com/GarikMartikyan/Swipey/releases/latest/download/Swipey.apk)** · 26 MB · Android 13+

<sub>Built by <a href="https://github.com/GarikMartikyan">Garik Martikyan</a></sub>

</div>
