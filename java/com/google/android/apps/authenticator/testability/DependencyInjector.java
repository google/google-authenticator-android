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

import android.content.Context;
import com.google.android.apps.authenticator.otp.AccountDb;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

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
 * state leakage, each test should invoke {@link #resetForIntegrationTesting(Context)}.
 *
 * <p>The injector is configured with a "module" which is responsible for creating, storing, and
 * providing the various injected objects. Normally, there is no need to deal with modules directly
 * as {@code configureForProductionIfNotConfigured} and {@code resetForIntegrationTesting} will
 * deal with that. However, it's possible to provide the injector with custom modules via
 * {@link #configure(DependencyInjectorModule)} and
 * {@link #configureIfNotConfigured(DependencyInjectorModule)}.
 */
public final class DependencyInjector {

  private static DependencyInjectorModule sModule;

  private DependencyInjector() {}

  /**
   * Gets the {@link Context} passed the instances created by this injector.
   */
  public static Context getContext() {
    return getModule().getContext();
  }

  public static AccountDb getAccountDb() {
    return getModule().getAccountDb();
  }

  /**
   * Sets the {@link StartActivityListener} instance returned by this injector.
   */
  public static void setStartActivityListener(StartActivityListener listener) {
    getModule().setStartActivityListener(listener);
  }

  public static StartActivityListener getStartActivityListener() {
    return getModule().getStartActivityListener();
  }

  /**
   * Sets the {@link StartServiceListener} instance returned by this injector.
   */
  public static void setStartServiceListener(StartServiceListener listener) {
    getModule().setStartServiceListener(listener);
  }

  public static StartServiceListener getStartServiceListener() {
    return getModule().getStartServiceListener();
  }

  /**
   * Clears any state and configures this injector for production use. Does nothing if the injector
   * is already configured.
   */
  public static void configureForProductionIfNotConfigured(Context context) {
    Preconditions.checkNotNull(context);
    DependencyInjectorModule module = createProductionModule();
    module.initialize(context);
    configureIfNotConfigured(module);
  }

  /**
   * Clears any state and configures this injector to provide objects that are suitable for
   * integration testing.
   */
  @VisibleForTesting
  public static void resetForIntegrationTesting(Context context) {
    Preconditions.checkNotNull(context);
    DependencyInjectorModule module = createModuleForIntegrationTesting();
    module.initialize(context);
    configure(module);
  }

  /**
   * Closes any resources and objects held by this injector. To use the injector again, invoke
   * {@link #resetForIntegrationTesting(Context)}.
   */
  public static synchronized void close() {
    if (sModule != null) {
      sModule.close();
      sModule = null;
    }
  }

  public static synchronized DependencyInjectorModule getModule() {
    Preconditions.checkState(sModule != null, "Not initialized");
    return sModule;
  }

  /**
   * Configures this injector with the provided module if the injector is not yet configured.
   */
  public static synchronized boolean configureIfNotConfigured(DependencyInjectorModule module) {
    if (sModule != null) {
      return false;
    }
    configure(module);
    return true;
  }

  /**
   * Resets the injector and configures it with the provided module.
   */
  public static synchronized void configure(DependencyInjectorModule module) {
    Preconditions.checkNotNull(module);
    close();
    sModule = module;
  }

  /**
   * Creates the module to be used for the normal operation of the app, where no tests are running.
   */
  private static DependencyInjectorModule createProductionModule() {
    return new DependencyInjectorModule();
  }

  /**
   * Creates the module to be used when running automated tests.
   */
  private static DependencyInjectorModule createModuleForIntegrationTesting() {
    // We have to load these modules using Reflection since they are not present in the app package.
    // The class is present in the test package.
    String packageName = DependencyInjectorModule.class.getPackage().getName();
    String className = packageName + ".DependencyInjectorModuleForIntegrationTesting";
    try {
      return (DependencyInjectorModule) Class.forName(className).newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
