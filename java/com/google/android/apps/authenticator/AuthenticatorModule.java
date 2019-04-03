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

package com.google.android.apps.authenticator;

import android.content.Context;
import com.google.android.apps.authenticator.barcode.BarcodeCaptureActivity;
import com.google.android.apps.authenticator.barcode.BarcodeConditionChecker;
import com.google.android.apps.authenticator.common.AndroidDependenciesModule;
import com.google.android.apps.authenticator.common.ApplicationContext;
import com.google.android.apps.authenticator.crypto.CryptoModule;
import com.google.android.apps.authenticator.otp.OtpModule;
import com.google.android.apps.authenticator.timesync.TimeSyncModule;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

/**
 * Dagger injection module for Authenticator.
 */
@Module(
    includes = {
        AndroidDependenciesModule.class,
        CryptoModule.class,
        OtpModule.class,
        TimeSyncModule.class,
    },
    injects = {
        AuthenticatorActivity.class,
        BarcodeCaptureActivity.class
    }
)
public class AuthenticatorModule {
  private Context applicationContext;

  // No arg constructor is needed for this module to be included from other modules
  public AuthenticatorModule() {}

  public AuthenticatorModule(Context applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Provides @ApplicationContext
  public Context provideApplicationContext() {
    return applicationContext;
  }

  @Provides @Singleton
  public BarcodeConditionChecker provideBarcodeConditionChecker() {
    return new BarcodeConditionChecker();
  }
}
