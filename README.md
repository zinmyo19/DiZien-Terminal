# DiZien Terminal

A rootless **Debian** terminal for Android. It runs a private Debian 13 ("trixie") system inside the app using
[PRoot](https://proot-me.github.io/), so you need **no root access and no other terminal app** (no Termux).
Dark "Amber Slate" theme, tabs, an extra-keys row, and `apt` that works.

> **Early review build (v0.1.3).** It works on the owner's arm64 phone (Debian install, `apt update`, `apt install nano`),
> but it has had little testing on other devices. Expect rough edges. Use at your own risk.

## Not affiliated with Debian

DiZien Terminal is an independent project. It is **not affiliated with, endorsed by or sponsored by Debian or
Software in the Public Interest, Inc. (SPI).** Debian is a registered trademark owned by Software in the Public
Interest, Inc. The app name and icon do not use the Debian name or the Debian swirl. The official Debian Open Use
Logo appears only on the app's About page, to show which operating system runs inside; it is
(c) 1999 SPI, Inc., licensed under LGPL-3.0-or-later or CC BY-SA 3.0 (see [`NOTICE-debian-logo.txt`](NOTICE-debian-logo.txt)).

## Install

1. Download `DiZienTerminal-0.1.3-review-arm64.apk` from the [Releases](../../releases) page and install it
   (allow "install unknown apps" for your browser/file manager when Android asks).
2. Open **DiZien Terminal**, tap **Install Debian**, and wait. It needs about 450 MB of free space and a
   connection; the download is about 30 MB.
3. Try `apt update && apt install nano`.

Requirements: Android 7.0+ (API 24) and a 64-bit ARM phone (**arm64-v8a only**). The Debian system is not
installed on other CPUs. The package ID is `app.slatehome.dizien.terminal`.

## Where the Debian system comes from (and how it is checked)

The Debian system is **not bundled** in the APK or in this repository. On first run the app downloads Debian's own
official root filesystem for Docker images, built by the Debian project's `debuerreotype` tooling:

- Source: <https://github.com/debuerreotype/docker-debian-artifacts>, pinned to one exact commit
  (`9c4818e3bfd35139d3798b0420e9c46836658b2e`, `trixie/slim/oci/blobs/rootfs.tar.gz`, 30,200,478 bytes), so the file can never
  silently change.
- Before anything is unpacked the app checks the file's **SHA-256**:
  `ddc0c1e36a6793411b6c8d48e1ddad2987405d377da92bcf532ed3181c2e4d62`. If it does not match, the install stops and
  nothing is unpacked.

You can verify the same file yourself with `sha256sum rootfs.tar.gz`. The pinned values live in
[`Rootfs.kt`](app/src/main/kotlin/app/slatehome/dizien/terminal/Rootfs.kt).

## Permissions

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Only to download the Debian image once, and for `apt` and other programs **you** run inside Debian. The app itself has no accounts, ads, analytics or tracking. |
| `FOREGROUND_SERVICE` | Keeps your terminal sessions alive (with a notification) when the app is in the background. |
| `MANAGE_EXTERNAL_STORAGE`, `READ/WRITE_EXTERNAL_STORAGE` | **Optional.** Only used if you turn on access to your phone's files as `/sdcard` (see below). Off by default. |

## Phone files: `debian-setup-storage`

By default Debian cannot see your phone's shared storage. Type `debian-setup-storage` inside the terminal (or turn on
*Settings > Phone storage in /sdcard*). It explains what it does, asks Android for permission (on Android 11+ it
opens the "All files access" screen), and prints OK or FAILED. After it succeeds, your files show up as `/sdcard`
(for example `/sdcard/Download`) in new tabs; if you ran it in an old tab, the app opens a fresh tab for you. You can switch
it off again in Settings at any time.

## Tool installer: `dizien-tools`

Type `dizien-tools` for a simple numbered menu that installs groups of programs with `apt` (always asks before installing,
shows apt's progress, and prints a clear OK or FAILED for each group):

| # | Group | Packages |
|---|---|---|
| 1 | Basics | git curl wget vim htop tree zip unzip |
| 2 | Python | python3 python3-pip python3-venv |
| 3 | Build tools | build-essential cmake |
| 4 | Node.js | nodejs npm |
| 5 | Network tools | iputils-ping bind9-dnsutils net-tools openssh-client |
| 6 | Security tools | nmap sqlmap hydra john wfuzz |
| 7 | Install a package by name | any Debian package |
| 8 | Update and upgrade Debian | apt-get update + upgrade |

Nothing is installed unless you choose it. The Security tools group only works rootless (for example `nmap -sT`);
anything needing root or Wi-Fi monitor mode does not work in this app. Use these tools only on systems you own or have
permission to test. The command is installed into `/usr/local/bin` every time the app starts.

## Works with DiZien Launcher

The DiZien Launcher Terminal shortcut opens this app automatically: it has an activity in the
category `app.slatehome.dizien.category.TERMINAL`. Nothing needs to be set up.

## Build

```
./gradlew assembleRelease     # JDK 17+, Android SDK 34
```

Create `local.properties` with `sdk.dir=/path/to/Android/Sdk`. If a `signing.properties` file (keys `storeFile`,
`storePassword`, `keyAlias`, `keyPassword`) exists the release is signed with it; otherwise you get an unsigned APK. Both files
are in `.gitignore` and **must never be committed**. The `terminal-emulator` / `terminal-view` libraries come from JitPack.
The release APK on GitHub was signed with a private review key that is not part of this repository.

`targetSdk` is deliberately 28: the Debian userland runs programs from the app's private storage, which Android blocks
for apps targeting 29 or newer (the same approach as Termux's sideloaded builds). Because of this the app is not suitable
for Google Play.

## Not included

- No root, no Magisk, no Termux. No NetHunter or Kali (a possible separate future project).
- No Debian system inside the repository or APK (downloaded and checked at first run).

## License

GPL-3.0-or-later for this app's own code (see [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE)). Bundled PRoot is GPL-2.0-or-later and
talloc is LGPL-3.0-or-later; their complete corresponding source and build recipe are in
[`corresponding-source/`](corresponding-source/) (details in
[`PROVENANCE.md`](app/src/main/assets/licenses/PROVENANCE.md)). Other third-party licences are listed in `NOTICE` and inside the app
(*Settings > About & licenses*).

Debian is a registered trademark of Software in the Public Interest, Inc. This project is not affiliated with Debian.
