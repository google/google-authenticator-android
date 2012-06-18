/*
 * Copyright 2011 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.authenticator;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.MenuItem;

/**
 * Interface for providing functionality not available in Market builds without having to modify
 * the codebase shared between the Market and non-Market builds.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public interface OptionalFeatures {

  /**
   * Invoked when the {@link AuthenticatorActivity} {@code onCreate} is almost done.
   */
  void onAuthenticatorActivityCreated(AuthenticatorActivity activity);

  /**
   * Gets the resource ID of the options menu to be used in the {@link AuthenticatorActivity}.
   *
   * @return resource ID or {@code 0} for the default (Market) menu.
   */
  int getAuthenticatorActivityOptionsMenuResourceId();

  /**
   * Invoked when an menu item (options or context menu) is selected in the
   * {@link AuthenticatorActivity}.
   *
   * @return {@code true} if the event has been consumed by these optional features, {@code false}
   *         otherwise.
   */
  boolean onAuthenticatorActivityMenuItemSelected(
      AuthenticatorActivity activity, int featureId, MenuItem item);

  /**
   * Invoked when a URI has been scanned.
   *
   * @return {@code true} if the URI has been consumed by these optional features, {@code false}
   *         otherwise.
   */
  boolean interpretScanResult(Context context, Uri scanResult);

  /**
   * Invoked when data have been successfully imported from the old Authenticator app.
   */
  void onDataImportedFromOldApp(Context context);

  /**
   * Gets the {@link SharedPreferences} into which to import preferences from the old Authenticator
   * app.
   *
   * @return preferences or {@code null} to skip the importing of preferences.
   */
  SharedPreferences getSharedPreferencesForDataImportFromOldApp(Context context);

  /**
   * Appends the Learn more link to data import dialog text.
   *
   * @return text with the link.
   */
  String appendDataImportLearnMoreLink(Context context, String text);

  /** Whether the UI for debugging/testing corp seed rotation is enabled. */
  boolean isCorpSeedDebugUiEnabled();
}
