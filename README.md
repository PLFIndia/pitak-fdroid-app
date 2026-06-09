# Pitak (F-Droid edition)

पिटक — a single-user, local-first Android app for managing a personal book
library: owned books, a wishlist, and an encrypted, biometric-gated record of
who has borrowed what. Fully offline; no developer backend, no telemetry, no
account. Owned books and wishlist are stored unencrypted (intended to be
shareable/publishable); borrower and loan data is encrypted at rest.

This is the **F-Droid variant**: it contains no proprietary Google libraries.
Barcode (ISBN) and QR scanning use ZXing; cover capture uses the system camera
plus an open-source image cropper. There is no ML Kit and no Google Play
Services dependency. (The Google Play edition lives in a separate repository.)

## License

Copyright (C) 2026 Parallel Line Foundation

This program is free software: you can redistribute it and/or modify it under
the terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with
this program. If not, see <https://www.gnu.org/licenses/>.

The full license text is in [LICENSE](LICENSE).

### Third-party components

Pitak bundles open-source libraries under GPL-compatible licenses, including:

- AndroidX / Jetpack, Hilt, Coroutines, Coil, Retrofit, OkHttp, Moshi,
  Accompanist, ZXing, and the vanniktech/canhub image cropper — Apache-2.0
- Argon2kt — MIT
- SQLCipher (net.zetetic) — BSD-3-Clause

All are compatible with GPL-3.0-or-later.
