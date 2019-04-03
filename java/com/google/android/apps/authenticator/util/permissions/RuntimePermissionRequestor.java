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

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.v4.app.ActivityCompat;
import com.google.common.base.Preconditions;

/** Runtime permission requestor implementation for Android M and above. */
public class RuntimePermissionRequestor implements PermissionRequestor {

  @Override
  @TargetApi(23)
  public int checkSelfPermission(Context context, String permission) {
    Preconditions.checkArgument(VERSION.SDK_INT >= VERSION_CODES.M);
    return ActivityCompat.checkSelfPermission(context, permission);
  }

  @Override
  @TargetApi(23)
  public boolean shouldShowRequestPermissionRationale(Activity activity, String permission) {
    Preconditions.checkArgument(VERSION.SDK_INT >= VERSION_CODES.M);
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
  }

  @Override
  @TargetApi(23)
  public void requestPermissions(Activity activity, String[] permissions, int requestCode) {
    Preconditions.checkArgument(VERSION.SDK_INT >= VERSION_CODES.M);
    ActivityCompat.requestPermissions(activity, permissions, requestCode);
  }

}
