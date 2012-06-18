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
 * {@link OptionalFeatures} implementation used in Market builds.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class MarketBuildOptionalFeatures implements OptionalFeatures {

  @Override
  public void onAuthenticatorActivityCreated(AuthenticatorActivity activity) {}

  @Override
  public int getAuthenticatorActivityOptionsMenuResourceId() {
    return 0;
  }

  @Override
  public boolean onAuthenticatorActivityMenuItemSelected(
      AuthenticatorActivity activity, int featureId, MenuItem item) {
    return false;
  }

  @Override
  public boolean interpretScanResult(Context context, Uri scanResult) {
    return false;
  }

  @Override
  public void onDataImportedFromOldApp(Context context) {}

  @Override
  public SharedPreferences getSharedPreferencesForDataImportFromOldApp(Context context) {
    return null;
  }

  @Override
  public String appendDataImportLearnMoreLink(Context context, String text) {
    return text;
  }

  @Override
  public boolean isCorpSeedDebugUiEnabled() {
    return false;
  }
}
