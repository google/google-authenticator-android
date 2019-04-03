# Google Authenticator for Android ChangeLog


## v5.00

* Support security key flow calling via Google Play Services FIDO U2F API.
* Support backup and restore for app preferences. (This will NOT preserve any
  OTP keys.)
* Support long-press copy OTP codes into clipboard.


## v4.74

* New Material design UX (including new welcome screens).
* Built-in barcode scanner.
* Dark mode theme.
* Ability to reorder accounts.


## v4.60

* Support for Android M permission model.
* Minor UI improvements.
* Bug fixes and performance improvements.


## v4.44

* UI improvements: new theme and icon.
* Added upport for Android Wear devices
* Bug fixes and performance improvements
* Developer preview: support for NFC Security Key (FIDO Universal 2nd Factor
  protocol)


## v2.49

* New feature: Account branding in the UI.
* Added new dialog for the "Already Enrolled" error in 30-Second Flow
  enrollment.


## v2.47

* Prevented accidental overwriting of accounts when secrets are issued by
  different providers.
* Added Greenland and New Caledonia to 30-second flow locales.
* Updated country name "Palestinian Territories" -> Palestine.


## v2.44

* Closed the CAB when new accounts are added. 8362472
* Disabled the renaming of Google Internal 2Factor accounts.
* Fixed a bug where no account name is displayed in the CAB after an orientation
  change.
* Added Kazakhstan to country dropdown in the 2SV 30-sec flow.
* Dropped support for Eclair (Android 2.1, API Level 7), making Froyo (Android
  2.2, API Level 8) the minimum supported platform.
* Replaced the context menu in the account/code list with the Contextual Action
  Bar (CAB) on Honeycomb (Android 3.0) and higher.
* Enabled the users to send feedback to Google via the "Send feedback" menu item
  in the main screen of the app.
* Added new Terms of Service URL.
* Fixed how TalkBack reads out account information on ICS and above -- codes are
  now read out digit by digit.
* Fixed the description of the "get next code" button as spoken by TalkBack.
* Displayed a "toast" after a verification code has been copied to clipboard. 
* Removed the link to the open-source project from Settings -> About.


## v2.35

* After the first account is added, made the main page display a notice about
  how to obtain/use the verification codes. 
* Added assorted UI tweaks to the 30-sec enrollment flow into 2-step
  verification.
* De-duped phone numbers in the Review Settings page.
* Fixed the bug where deleting a pre-filled backup number and pressing Skip
  resulted in a non-empty number on the next page.
* Fixed a crash in the Review Settings page on low screen density (~120dpi)
  devices.
* Shortened the title of the Rename Account dialog.
* Switched to the new URL for Terms of Service.


## v2.33

* Added reporting of 30-sec flow enrollment analytics to the server.
* Fixed the bug where a backup number is displayed in the Review Settings page
  even though it was deleted in the preceding step.
* Fixed an rare crash in the 30-sec flow ("Unable to add window").
* Fixed a rare crash which when dismissing the "Beginning setup" dialog.


## v2.32

* Added 30-sec enrollment flow into 2-step verification.
* "Time Sync -> About this feature" now opens a Help Center article in the web
  browser instead of displaying a built-in page.
* Fixed a bug where the background around the Time Sync dialog was not dimmed.


## v2.24

* Fixed a bug where the Holo theme for Time Sync dialogs was not used.


## v2.21

* Enabled the app to generate correct time-based (TOTP) verification codes
  even on devices where the system time is wrong. The user can synchronize
  the app's internal time with Google servers using Settings ->
  Time correction for codes -> Sync now.
  NOTE: This feature adds requirement for the Full Internet Access
  permission.
* Made the countdown indicator and the Get Next Code button on Gingerbread
  and below green instead of gray.
* Fixed a bug where parsing a HOTP account provisioning URL with an invalid
  counter crashed the app.


## v2.15

* Changed package name from com.google.android.apps.authenticator to
  com.google.android.apps.authenticator2 and changed the signing key.
  This is a new app as far as the Android OS and Google Play are concerned.
  The app securely imports data from the "old" app (v0.91).
* Made the app's UI look more native. The blue on black theme has been
  replaced with the system default (dark) theme.
* Grouped Scan Barcode and Manually Enter Account functionality into a
  separate Add Account screen.
* Switched the Privacy Policy URL to http://www.google.com/policies/privacy/.
* Renamed account deletion to account removal and added more details to the
  removal prompt. The prompt is slightly different for Google accounts.
* Made the "Manual account entry" screen scrollable.
* Made the "Integrity check code" screen support the landscape orientation.
* Enabled account provisioning via a click on a hyperlink in the device's web
  browser (otpauth://...).


## v0.91

* Starting from March 29 2012 7:00 GMT, display a deprecation notice in the
  main screen of the app. The notice urges users to switch to the new
  Authenticator app (com.google.android.apps.authenticator2).


## v0.87

* Protect against the SQLite journal vulnerability (CVE-2011-3901) by changing
  permissions on the app's data directory using android.os.FileUtils.
* Enable the export of key material to facilitate the migration to the "new"
  Authenticator (v2.x).
* Clear counter-based OTPs around two minutes after they have been generated.
* Added support for the landscape orientation in the Manual Account Entry
  screen.


## v0.86

* Roll back v0.85 functionality making the app effectively equivalent to
  v0.73.


## v0.85

* Protect against the SQLite journal vulnerability (CVE-2011-3901) by changing
  permissions on the app's data directory using chmod.
* Enable the export of key material to facilitate the migration to the "new"
  Authenticator (v2.x).
* Clear counter-based OTPs around two minutes after they have been generated.
* Added support for the landscape orientation in the Manual Account Entry
  screen.

## v0.73

* Raised the minimum supported platform from Android 2.0.1 (API Level 6) to
  Android 2.1 (API Level 7).
* Added support for landscape orientation of the account list screen.
* Moved the Version dialog and the menu items Open-Source Project, Privacy
  Policy, and Terms of Service under the About menu item.
* Removed support for old account setup URI formats: totp://... and
  https://www.google.com/accounts/KeyProv
* Report an error if the account setup URI is neither TOTP nor HOTP.
* Fixed the bug where verification codes were not generated for the account
  with an empty name.
* Fixed a bug where disabled HOTP "Get code" buttons are reenabled prematurely
  when TOTP codes refresh.
* Changed the Korean translation of the "Manually add account" menu item.


## v0.65

* Added support for Cupcake and Donut (Android 1.5 and 1.6 respectively).
* Dropped "Google" from the application title visible in the launcher and
  application list.
* Made the refresh time of TOTP codes independent of application start time.
* Replaced the textual "Get code" button for HOTP accounts with a clickable
  refresh icon.
* Fixed a rare crash caused by the invoked QR-code reader returning no Intent.
* Fixed a rare crash (introduced in v0.64) which occurred when the system time
  jumped backwards when the application was visible.


## v0.64

* Removed the Re-Enter Password feature and the associated GET_ACCOUNTS and
  MANAGE_ACCOUNTS permissions that were introduced in v0.62 of the
  application.
* Added a countdown indicator for TOTP accounts on the main screen.
* Removed the lock icon that appeared next to each account on the main screen.
* Fixed the bug where the "Illegal character" error message in the Manual
  Account Entry screen was not translated.
* Fixed the bug where secret keys for newly added accounts were not
  sanity-checked leading to crashes at startup (NullPointerException in
  PasscodeGenerator.generateResponseCode()).
* Fixed an obscure crash when the barcode scanner returned OK but without a
  scan result.


## v0.62

* Add translations for 38 languages.
* Add the "Re-enter password" menu item for manually providing the device's
  Account Manager with an ASP. NOTE: This feature makes the application
  require Account Management permissions.
* Remove support for SDK version 5 (Android 2.0). The minimum supported
  version is 6 (Android 2.0.1).
* Fix crashes when QR-code scanning does not produce a valid "otpauth://..."
  URL -- display the "Cannot interpret QR code" error message instead.
* Improve the launcher icon to follow the Android Icon Design Guidelines.
* Add a new icon for the "Scan a barcode" menu item.  The new icon looks like
  a QR code, to better suggest the action it represents.
* Rename the menu item "Scan account barcode" to "Scan a barcode".
* Remove the "Refresh" menu item for refreshing TOTP verification codes.
* Disable suggestions and upper-casing in the "Enter key" field in the "Manual
  account entry" screen.
* Add user confirmation to QR-code scanning of OTP secrets in order to prevent
  a malicious apps from updating OTP secrets in the phone.
* When a secret is saved present a toast with a "secret saved" message instead
  of updating the status message on the bottom of the screen.
* Fix the bug where the account list was scrolled to the top every time TOTPs
  were updated (every 30 seconds).
* Switch "Terms of Service" and "Privacy Policy" links to those that show the
  resulting pages in the language currently selected on the phone.
* Remove the "Press Menu for more options" status text from the bottom of the
  main screen.
* Don't log usernames of accounts configured in the app to the Android system
  log.
* Fix the bug with lower-case "i" in the secret key crashing the app when
  language is set to Turkish.
* Fix a very rare crash due to a NullPointerException in EnterKeyActivity's
  AccountDb.getAccount().


## v0.54
