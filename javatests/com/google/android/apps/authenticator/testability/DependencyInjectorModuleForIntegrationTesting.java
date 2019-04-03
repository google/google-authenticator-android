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

package com.google.android.apps.authenticator.testability;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.preference.PreferenceManager;
import com.google.android.apps.authenticator.AuthenticatorApplication;
import com.google.android.apps.authenticator.otp.AccountDb;
import org.mockito.Mockito;

/**
 * Dependency injector module used during automated testing. The module aims to leave as much
 * functionality in the app as possible, while also stubbing/resetting certain aspects to avoid
 * inter-test state leakage, spurious actions in response to events outside of the app, and
 * dependency on the configuration of the test device.
 */
public class DependencyInjectorModuleForIntegrationTesting extends DependencyInjectorModule {

  @Override
  public synchronized void initialize(Context context) {
    // Delete the default (renamed) shared preferences to avoid passing state between tests
    PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();

    super.initialize(context);

    // Ignore requests to launch services from TestableActivity instances during tests.
    // This does not break the good-enough behavior needed in most tests and at the same time
    // does not introduce spurious actions due to services starting during tests.
    StartServiceListener startServiceListener = mock(StartServiceListener.class);
    doReturn(true)
        .when(startServiceListener)
        .onStartServiceInvoked(Mockito.anyObject(), Mockito.anyObject());
    setStartServiceListener(startServiceListener);
  }

  @Override
  protected AccountDb createAccountDb() {
    // Delete the database to prevent leaking state between tests
    AccountDb.deleteDatabase(getContext());
    return super.createAccountDb();
  }
}
