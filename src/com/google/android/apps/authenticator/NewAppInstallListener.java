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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

/**
 * {@link BroadcastReceiver} that launches the new Authenticator app as soon as it's installed or
 * updated. This ensures that the new Authenticator app attempts to import data from this app
 * immediately after having been installed, thus preempting users who then decide to immediately
 * uninstall this app.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class NewAppInstallListener extends BroadcastReceiver {

  private static final String NEW_APP_PACKAGE_NAME = "com.google.android.apps.authenticator2";
  private static final String NEW_APP_IMPORT_ACTIVITY_CLASS_NAME =
      "com.google.android.apps.authenticator.AuthenticatorActivity";

  private static final String LOG_TAG = NewAppInstallListener.class.getSimpleName();

  @Override
  public void onReceive(Context context, Intent intent) {
    String packageName = intent.getData().getSchemeSpecificPart();
    if (NEW_APP_PACKAGE_NAME.equals(packageName)) {
      try {
        context.startActivity(new Intent(Intent.ACTION_MAIN)
            .setComponent(
                new ComponentName(
                    NEW_APP_PACKAGE_NAME,
                    NEW_APP_IMPORT_ACTIVITY_CLASS_NAME))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      } catch (Exception e) {
        Log.w(LOG_TAG, "Failed to launch the new app", e);
      }
    }
  }
}
