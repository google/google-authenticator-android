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

package com.google.android.apps.authenticator.testability;

import com.google.android.apps.authenticator.AccountDb;
import com.google.android.apps.authenticator.OtpProvider;
import com.google.android.apps.authenticator.OtpSource;
import com.google.android.apps.authenticator.testability.accounts.AccountManager;
import com.google.android.apps.authenticator.testability.accounts.AndroidAccountManager;
import com.google.android.apps.authenticator.testability.app.AndroidNotificationManager;
import com.google.android.apps.authenticator.testability.app.NotificationManager;
import com.google.android.apps.authenticator.testability.content.pm.AndroidPackageManager;
import com.google.android.apps.authenticator.testability.content.pm.PackageManager;

import android.content.Context;
import android.test.RenamingDelegatingContext;

/**
 * Dependency injector that decouples the clients of various objects from their
 * creators/constructors and enables the injection of these objects for testing purposes.
 *
 * <p>The injector is singleton. It needs to be configured for production or test use using
 * {@link #configureForProductionIfNotConfigured(Context)} or
 * {@link #resetForIntegrationTesting(Context)}.
 * After that its clients can access the various objects such as {@link AccountDb} using the
 * respective getters (e.g., {@link #getAccountDb()}.
 *
 * <p>When testing, this class provides the means to inject different implementations of the
 * injectable objects (e.g., {@link #setAccountDb(AccountDb) setAccountDb}). To avoid inter-test
 * state-leakage, each test should invoke {@link #resetForIntegrationTesting(Context)}.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public final class DependencyInjector {

  private static Context sContext;

  private static AccountDb sAccountDb;
  private static OtpSource sOtpProvider;
  private static AccountManager sAccountManager;
  private static NotificationManager sNotificationManager;
  private static PackageManager sPackageManager;
  private static StartActivityListener sStartActivityListener;

  private enum Mode {
    PRODUCTION,
    INTEGRATION_TEST,
  }

  private static Mode sMode;

  private DependencyInjector() {}

  private static synchronized Context getContext() {
    if (sContext == null) {
      throw new IllegalStateException("Context not set");
    }
    return sContext;
  }

  /**
   * Sets the {@link AccountDb} instance returned by this injector. This will prevent the injector
   * from creating its own instance.
   */
  public static synchronized void setAccountDb(AccountDb accountDb) {
    if (sAccountDb != null) {
      sAccountDb.close();
    }
    sAccountDb = accountDb;
  }

  public static synchronized AccountDb getAccountDb() {
    if (sAccountDb == null) {
      sAccountDb = new AccountDb(getContext());
      if (sMode != Mode.PRODUCTION) {
        sAccountDb.deleteAllData();
      }
    }
    return sAccountDb;
  }

  /**
   * Sets the {@link OtpSource} instance returned by this injector. This will prevent the injector
   * from creating its own instance.
   */
  public static synchronized void setOtpProvider(OtpSource otpProvider) {
    sOtpProvider = otpProvider;
  }

  public static synchronized OtpSource getOtpProvider() {
    if (sOtpProvider == null) {
      sOtpProvider = new OtpProvider(getAccountDb());
    }
    return sOtpProvider;
  }

  /**
   * Sets the {@link AccountManager} instance returned by this injector. This will prevent the
   * injector from creating its own instance.
   */
  public static synchronized void setAccountManager(AccountManager accountManager) {
    sAccountManager = accountManager;
  }

  public static synchronized AccountManager getAccountManager() {
    if (sAccountManager == null) {
      sAccountManager =
          AndroidAccountManager.wrap(android.accounts.AccountManager.get(getContext()));
    }
    return sAccountManager;
  }

  /**
   * Sets the {@link NotificationManager} instance returned by this injector. This will prevent the
   * injector from creating its own instance.
   */
  public static synchronized void setNotificationManager(NotificationManager notificationManager) {
    sNotificationManager = notificationManager;
  }

  public static synchronized NotificationManager getNotificationManager() {
    if (sNotificationManager == null) {
      // Only obtain the actual NotificationManager in production mode.
      // In test mode simulate that there's no NotificationManager support on the device by
      // returning null.
      if (sMode == Mode.PRODUCTION) {
        sNotificationManager =
            AndroidNotificationManager.wrap(
                (android.app.NotificationManager) getContext().getSystemService(
                    Context.NOTIFICATION_SERVICE));
      }
    }
    return sNotificationManager;
  }

  /**
   * Sets the {@link PackageManager} instance returned by this injector. This will prevent the
   * injector from creating its own instance.
   */
  public static synchronized void setPackageManager(PackageManager packageManager) {
    sPackageManager = packageManager;
  }

  public static synchronized PackageManager getPackageManager() {
    if (sPackageManager == null) {
      sPackageManager = AndroidPackageManager.wrap(getContext().getPackageManager());
    }
    return sPackageManager;
  }

  /**
   * Sets the {@link StartActivityListener} instance returned by this injector.
   */
  public static synchronized void setStartActivityListener(StartActivityListener listener) {
    sStartActivityListener = listener;
  }

  public static synchronized StartActivityListener getStartActivityListener() {
    // Don't create an instance on demand -- the default behavior when the listener is null is to
    // proceed with launching activities.
    return sStartActivityListener;
  }

  /**
   * Clears any state and configures this injector for production use. Does nothing if the injector
   * is already configured.
   */
  public static synchronized void configureForProductionIfNotConfigured(Context context) {
    if (sMode != null) {
      return;
    }

    close();
    sMode = Mode.PRODUCTION;
    sContext = context;
  }

  /**
   * Clears any state and configures this injector to provide objects that are suitable for
   * integration testing.
   */
  // @VisibleForTesting
  public static synchronized void resetForIntegrationTesting(Context context) {
    if (context == null) {
      throw new NullPointerException("context == null");
    }

    close();

    sMode = Mode.INTEGRATION_TEST;
    RenamingDelegatingContext renamingContext = new RenamingDelegatingContext(context, "test_");
    renamingContext.makeExistingFilesAndDbsAccessible();
    sContext = renamingContext;
  }

  /**
   * Closes any resources and objects held by this injector. To use the injector again, invoke
   * {@link #resetForIntegrationTesting(Context)}.
   */
  public static synchronized void close() {
    if (sAccountDb != null) {
      sAccountDb.close();
    }

    sMode = null;
    sContext = null;
    sAccountDb = null;
    sOtpProvider = null;
    sAccountManager = null;
    sNotificationManager = null;
    sPackageManager = null;
    sStartActivityListener = null;
  }
}
