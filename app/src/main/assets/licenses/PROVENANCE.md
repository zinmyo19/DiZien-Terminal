# Bundled programs: where they come from

DiZien Terminal bundles three small native programs in the APK (folder lib/arm64-v8a, installed as libproot.so, libproot-loader.so and libproot-loader32.so):

- PRoot (GPL-2.0-or-later) - lets the app run a Debian system as a normal Android app, without root.
- PRoot's two tiny "loader" programs (same licence as PRoot).
- talloc (LGPL-3.0-or-later) - memory library statically linked into PRoot.

They are the same binaries that the author already uses in the separate app DZ Terminal (Alpine edition), built with Android NDK r27c for API 24 from unmodified upstream PRoot sources plus one include line (see below). They were not changed for DiZien Terminal.

The complete corresponding source code and build recipe for these programs is stored in the source repository of this app, in the folder corresponding-source/:

- alpine-proot-corresponding-source.tgz - PRoot source, generated build trees, BUILD-REPRODUCE.sh and notes
- talloc-2.4.4-source.tgz, talloc-Alpine-APKBUILD.txt, talloc-always-libs.patch - talloc source and patch

Source change to upstream PRoot: a leading `#include <string.h>` in extension/ashmem_memfd/ashmem_memfd.c.

The Debian system itself is NOT bundled. It is downloaded on first run from Debian's own image-build repository (github.com/debuerreotype/docker-debian-artifacts), pinned to one exact commit and checked against a SHA-256 value before it is unpacked. Debian packages carry their own licences under /usr/share/doc/<package>/copyright inside the system.
