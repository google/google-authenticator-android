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

package com.google.android.apps.authenticator.testability.content.pm;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;

/**
 * Abstraction for retrieving various kinds of information related to the application packages that
 * are currently installed on the device. Offers a subset of the API of
 * {@link android.content.pm.PackageManager}.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public interface PackageManager {

  /** @see android.content.pm.PackageManager#GET_SIGNATURES */
  int GET_SIGNATURES = android.content.pm.PackageManager.GET_SIGNATURES;

  /** @see android.content.pm.PackageManager#getPackagesForUid(int) */
  String[] getPackagesForUid(int uid);

  /** @see android.content.pm.PackageManager#getPermissionInfo(String, int) */
  PackageInfo getPackageInfo(String packageName, int flags)
      throws android.content.pm.PackageManager.NameNotFoundException;

  /** @see android.content.pm.PackageManager#resolveActivity(Intent, int) */
  ResolveInfo resolveActivity(Intent intent, int flags);
}
