Google Authenticator for Android (open source version)
======================================================
Copyright 2010 Google Inc.

https://github.com/google/google-authenticator-android

This project is an older fork of
[the one on the Play store](https://play.google.com/store/apps/details?id=com.google.android.apps.authenticator2).
It's an older version that doesn't get changes synced to it from the
[Play store version](https://play.google.com/store/apps/details?id=com.google.android.apps.authenticator2).

Description
-----------
The Google Authenticator project includes implementations of one-time passcode
generators for several mobile platforms, as well as a pluggable authentication
module (PAM). One-time passcodes are generated using open standards developed by
the [Initiative for Open Authentication (OATH)](http://www.openauthentication.org/)
(which is unrelated to [OAuth](http://oauth.net/)).

This project contains the Android app. All other apps, and the PAM module, are in
[a separate project](https://github.com/google/google-authenticator).

There is by design NO account backups in any of the apps.

This implementation supports the HMAC-Based One-time Password (HOTP) algorithm
specified in [RFC 4226](https://tools.ietf.org/html/rfc4226) and the Time-based
One-time Password (TOTP) algorithm specified in [RFC 6238](https://tools.ietf.org/html/rfc6238).

Further documentation is available in the [Wiki](https://github.com/google/google-authenticator/wiki).
