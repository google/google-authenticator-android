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

package com.google.android.apps.authenticator.util.permissions;

import android.app.Activity;
import android.content.Context;

/** Interface for requesting permissions, to abstract platform differences in permissions. */
public interface PermissionRequestor {

  /**
   * Determine whether you have been granted a particular permission.
   *
   * @return {@code PERMISSION_GRANTED} or {@code PERMISSION_DENIED}.
   */
  int checkSelfPermission(Context context, String permission);

  /**
   * @return whether you should show UI with rationale for requesting permission.
   */
  boolean shouldShowRequestPermissionRationale(Activity activity, String permission);

  /** Request permissions to be granted to this application. */
  void requestPermissions(Activity activity, String[] permissions, int requestCode);
}
