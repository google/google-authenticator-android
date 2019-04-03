/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.authenticator.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.preference.PreferenceManager;
import com.google.android.apps.authenticator.time.Clock;
import com.google.android.apps.authenticator.time.SystemWallClock;
import com.google.android.apps.authenticator.util.permissions.AutoGrantPermissionRequestor;
import com.google.android.apps.authenticator.util.permissions.PermissionRequestor;
import com.google.android.apps.authenticator.util.permissions.RuntimePermissionRequestor;
import dagger.Module;
import dagger.Provides;
import java.net.Authenticator;
import javax.inject.Singleton;

/**
 * Android framework dependencies module.
 */
@Module(
    library = true,
    complete = false
)
public class AndroidDependenciesModule {

  @Provides @Singleton
  public SharedPreferences provideSharedPreferences(
      @ApplicationContext Context applicationContext) {
    return PreferenceManager.getDefaultSharedPreferences(applicationContext);
  }

  @Provides @Singleton
  public Clock provideClock() {
    return new SystemWallClock();
  }

  @Provides @Singleton
  public PermissionRequestor providesPermissionRequestor() {
    return VERSION.SDK_INT < VERSION_CODES.N
        ? new AutoGrantPermissionRequestor()
        : new RuntimePermissionRequestor();
  }
}
