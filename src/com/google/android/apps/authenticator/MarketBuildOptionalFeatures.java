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

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

/**
 * {@link OptionalFeatures} implementation used in Market builds.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class MarketBuildOptionalFeatures implements OptionalFeatures {

  @Override
  public void onAuthenticatorActivityCreated(AuthenticatorActivity activity) {}

  @Override
  public void onAuthenticatorActivityAccountSaved(Context context, String account) {}

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
  public OtpSource createOtpSource(AccountDb accountDb, TotpClock totpClock) {
    return new OtpProvider(accountDb, totpClock);
  }

  @Override
  public void onAuthenticatorActivityGetNextOtpFailed(
      AuthenticatorActivity activity, String accountName, OtpSourceException exception) {
    throw new RuntimeException("Failed to generate OTP for account", exception);
  }

  @Override
  public Dialog onAuthenticatorActivityCreateDialog(AuthenticatorActivity activity, int id) {
    return null;
  }

  @Override
  public void onAuthenticatorActivityAddAccount(AuthenticatorActivity activity) {
    activity.startActivity(new Intent(activity, AddOtherAccountActivity.class));
  }
}
