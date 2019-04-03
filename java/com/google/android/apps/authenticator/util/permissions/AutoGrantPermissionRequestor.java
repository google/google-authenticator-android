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
import android.content.pm.PackageManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import com.google.common.base.Preconditions;

/**
 * Permission requestor implementation that automatically grants requests for any permissions.
 *
 * <p>This should only be used when API level is below 23.
 */
public class AutoGrantPermissionRequestor implements PermissionRequestor {
  @Override
  public int checkSelfPermission(Context context, String permission) {
    Preconditions.checkArgument(VERSION.SDK_INT < VERSION_CODES.M);
    return PackageManager.PERMISSION_GRANTED;
  }

  @Override
  public boolean shouldShowRequestPermissionRationale(Activity activity, String permission) {
    Preconditions.checkArgument(VERSION.SDK_INT < VERSION_CODES.M);
    return false;
  }

  @Override
  public void requestPermissions(Activity activity, String[] permissions, int requestCode) {
    Preconditions.checkArgument(VERSION.SDK_INT < VERSION_CODES.M);
    throw new UnsupportedOperationException("All permissions are already granted");
  }
}
