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
import android.content.Intent;

/**
 * Listener which is invoked when an attempt is made to launch an {@link android.app.Activity} via
 * {@link Context#startActivity(Intent)} or
 * {@link android.app.Activity#startActivityForResult(Intent, int)}.
 * The listener can decide whether to proceed with the launch.
 * 
 * @see StartServiceListener
 */
public interface StartActivityListener {
  
  /**
   * Invoked when a launch of an {@link android.app.Activity} is requested.
   * 
   * @return {@code true} to consume/ignore the request, {@code false} to proceed with the launching
   *         of the {@code Activity}.
   */
  boolean onStartActivityInvoked(Context sourceContext, Intent intent);
}
