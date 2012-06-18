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

package com.google.android.apps.authenticator.dataexport;

import com.google.android.apps.authenticator.Preconditions;
import com.google.android.apps.authenticator.testability.content.pm.PackageManager;

import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.pm.PackageManager.NameNotFoundException;

import java.util.Arrays;

/**
 * {@link AuthorizationChecker} that verifies that the caller's UID contains a particular package
 * with signed with a particular key.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
class SignatureBasedAuthorizationChecker implements AuthorizationChecker {

  private final PackageManager mPackageManager;
  private final String mAuthorizedPackage;
  private final Signature mAuthorizedSignature;

  /**
   * Constructs a new {@code SignatureBasedAuthorizationChecker}.
   *
   * @param authorizedPackage name of the authorized package (e.g., {@code com.google.market}).
   * @param authorizedSignature signature that the authorized package must contain in order to
   *        pass the check..
   */
  public SignatureBasedAuthorizationChecker(
      PackageManager packageManager, String authorizedPackage, Signature authorizedSignature) {
    mPackageManager = Preconditions.checkNotNull(packageManager);
    mAuthorizedPackage = Preconditions.checkNotNull(authorizedPackage);
    mAuthorizedSignature = Preconditions.checkNotNull(authorizedSignature);
  }

  @Override
  public void checkAuthorization(int callerUid) {
    String[] callerPackages = mPackageManager.getPackagesForUid(callerUid);
    if ((callerPackages == null) || (!Arrays.asList(callerPackages).contains(mAuthorizedPackage))) {
      throw new SecurityException("No authorized packages for caller's UID " + callerUid);
    }

    PackageInfo callerPackageInfo;
    try {
      callerPackageInfo =
          mPackageManager.getPackageInfo(mAuthorizedPackage, PackageManager.GET_SIGNATURES);
    } catch (NameNotFoundException e) {
      throw new SecurityException("Failed to obtain caller's package info", e);
    }
    if (callerPackageInfo == null) {
      throw new SecurityException("Failed to obtain caller's package info");
    }
    Signature[] callerPackageSignatures = callerPackageInfo.signatures;
    if ((callerPackageSignatures == null) || (callerPackageSignatures.length == 0)) {
      throw new SecurityException("Failed to obtain caller's package signature");
    }
    for (Signature signature : callerPackageSignatures) {
      if (mAuthorizedSignature.equals(signature)) {
        return;
      }
    }
    throw new SecurityException("Caller's package does not have the correct signature");
  }
}
