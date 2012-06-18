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
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;

/**
 * {@PackageManager} implementation that delegates all invocations to the respective methods
 * of {@link android.content.pm.PackageManager}.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class AndroidPackageManager implements PackageManager {

  private final android.content.pm.PackageManager mDelegate;

  private AndroidPackageManager(android.content.pm.PackageManager delegate) {
    mDelegate = delegate;
  }

  public static AndroidPackageManager wrap(android.content.pm.PackageManager delegate) {
    return (delegate != null) ? new AndroidPackageManager(delegate) : null;
  }

  @Override
  public String[] getPackagesForUid(int uid) {
    return mDelegate.getPackagesForUid(uid);
  }

  @Override
  public PackageInfo getPackageInfo(String packageName, int flags) throws NameNotFoundException {
    return mDelegate.getPackageInfo(packageName, flags);
  }

  @Override
  public ResolveInfo resolveActivity(Intent intent, int flags) {
    return mDelegate.resolveActivity(intent, flags);
  }
}
