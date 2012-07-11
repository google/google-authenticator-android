/*
 * Copyright 2012 Google Inc. All Rights Reserved.
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

import android.os.Build;
import android.os.Bundle;
import android.test.InstrumentationTestRunner;

import org.mockito.Mockito;

/**
 * {@link InstrumentationTestRunner} that makes it possible to use Mockito on Eclair
 * without changing any other code. The runner by the framework is created before any tests are run
 * and thus has the opportunity to fix Mockito on Eclair.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class MockitoWorkaroundForEclairInstrumentationTestRunner
    extends InstrumentationTestRunner {

  @Override
  public void onCreate(Bundle arguments) {
    // This is a workaround for Eclair for http://code.google.com/p/mockito/issues/detail?id=354.
    // Mockito loads the Android-specific MockMaker (provided by DexMaker) using the current
    // thread's context ClassLoader. On Eclair this ClassLoader is set to the system ClassLoader
    // which doesn't know anything about this app (which includes DexMaker). The workaround is to
    // use the app's ClassLoader.

    // TODO(klyubin): Remove this workaround (and most likely this whole class) once Eclair is no
    // longer supported.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
      // Make Mockito look up a MockMaker using the app's ClassLoader, by asking Mockito to create
      // a mock.
      ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(Mockito.class.getClassLoader());
      Mockito.mock(Runnable.class);
      Thread.currentThread().setContextClassLoader(originalContextClassLoader);
    }

    super.onCreate(arguments);
  }
}
