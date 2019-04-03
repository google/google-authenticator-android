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

import android.util.Log;

import dagger.ObjectGraph;

import javax.annotation.Nullable;

/** Dependency injector using dagger. */
public class DaggerInjector {

  private static final String TAG = DaggerInjector.class.getSimpleName();

  @Nullable
  private static volatile ObjectGraph mObjectGraph;

  /**
   * Initializes dagger injector with the application module.
   *
   * <p>If module is {@code null} this disables Dagger injection (for unit tests).
   */
  public static void init(Object module) {
    mObjectGraph = module != null ? ObjectGraph.create(module) : null;
  }

  /** Injects this instance */
  public static void inject(Object instance) {
    if (mObjectGraph == null) {
      // This should happen only during unit tests
      Log.w(TAG, "Dagger injection has not been initialized!");
      return;
    }

    mObjectGraph.inject(instance);
  }
}
