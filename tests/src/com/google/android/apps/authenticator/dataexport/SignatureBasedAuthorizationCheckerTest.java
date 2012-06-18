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

import static com.google.testing.littlemock.LittleMock.doReturn;
import static com.google.testing.littlemock.LittleMock.doThrow;
import static com.google.testing.littlemock.LittleMock.initMocks;

import com.google.android.apps.authenticator.testability.content.pm.PackageManager;
import com.google.testing.littlemock.Mock;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;

import junit.framework.TestCase;

/**
 * Unit tests for {@link SignatureBasedAuthorizationChecker}.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class SignatureBasedAuthorizationCheckerTest extends TestCase {
  private static final int UID = 12345;
  private static final String NOT_AUTHORIZED_PACKAGE = "pkg1";
  private static final String AUTHORIZED_PACKAGE = "pkg2";
  private static final Signature AUTHORIZED_SIGNATURE = new Signature("abcdef");
  private static final Signature NOT_AUTHORIZED_SIGNATURE1 = new Signature("11223344");
  private static final Signature NOT_AUTHORIZED_SIGNATURE2 = new Signature("1122334455");

  private SignatureBasedAuthorizationChecker mChecker;

  @Mock private PackageManager mMockPackageManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    initMocks(this);
    mChecker = new SignatureBasedAuthorizationChecker(
        mMockPackageManager, AUTHORIZED_PACKAGE, AUTHORIZED_SIGNATURE);
  }

  public void testConstructor_withWithNullPackageManager() {
    try {
      new SignatureBasedAuthorizationChecker(null, AUTHORIZED_PACKAGE, AUTHORIZED_SIGNATURE);
      fail("NullPointerExcepton should have been thrown");
    } catch (NullPointerException e) {
      // Expected
    }
  }

  public void testConstructor_withWithNullPackage() {
    try {
      new SignatureBasedAuthorizationChecker(mMockPackageManager, null, AUTHORIZED_SIGNATURE);
      fail("NullPointerExcepton should have been thrown");
    } catch (NullPointerException e) {
      // Expected
    }
  }

  public void testConstructor_withWithNullSignature() {
    try {
      new SignatureBasedAuthorizationChecker(mMockPackageManager, AUTHORIZED_PACKAGE, null);
      fail("NullPointerExcepton should have been thrown");
    } catch (NullPointerException e) {
      // Expected
    }
  }

  public void testGetData_withMatchingSignature() throws Exception {
    withCheckPassingForUid(UID);

    mChecker.checkAuthorization(UID);
  }

  public void testGetData_withNullPackages() throws Exception {
    withCheckPassingForUid(UID);
    withPackagesForUid(UID, null);

    assertCheckAuthorizationThrowsSecurityException(UID);
  }

  public void testGetData_withNoPackages() throws Exception {
    withCheckPassingForUid(UID);
    withPackagesForUid(UID, new String[0]);

    assertCheckAuthorizationThrowsSecurityException(UID);
  }

  public void testGetData_withPackageNotFound() throws Exception {
    withCheckPassingForUid(UID);
    doThrow(new NameNotFoundException()).when(mMockPackageManager)
        .getPackageInfo(AUTHORIZED_PACKAGE, PackageManager.GET_SIGNATURES);

    assertCheckAuthorizationThrowsSecurityException(UID);
  }

  public void testGetData_withNullPackageInfo() throws Exception {
    withCheckPassingForUid(UID);
    withPackageInfo(AUTHORIZED_PACKAGE, null);

    assertCheckAuthorizationThrowsSecurityException(UID);
  }

  public void testGetData_withNoSignatures() throws Exception {
    withCheckPassingForUid(UID);
    withPackageSignatures(AUTHORIZED_PACKAGE, new Signature[0]);

    assertCheckAuthorizationThrowsSecurityException(UID);
  }

  public void testGetData_withNonMatchingSignature() throws Exception {
    withCheckPassingForUid(UID);
    withPackageSignatures(AUTHORIZED_PACKAGE, new Signature[] {NOT_AUTHORIZED_SIGNATURE1});

    assertCheckAuthorizationThrowsSecurityException(UID);
  }

  private void assertCheckAuthorizationThrowsSecurityException(int uid) {
    try {
      mChecker.checkAuthorization(uid);
      fail("SecurityException should have been thrown");
    } catch (SecurityException e) {
      // Expected
    }
  }

  private void withPackagesForUid(int uid, String[] packageNames) {
    doReturn(packageNames).when(mMockPackageManager).getPackagesForUid(uid);
  }

  private void withPackageInfo(String packageName, PackageInfo packageInfo)
      throws NameNotFoundException {
    doReturn(packageInfo).when(mMockPackageManager)
        .getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
  }

  private void withPackageSignatures(String packageName, Signature[] signatures)
      throws NameNotFoundException {
    PackageInfo packageInfo = new PackageInfo();
    packageInfo.packageName = packageName;
    packageInfo.signatures = signatures;
    withPackageInfo(packageName, packageInfo);
  }

  private void withCheckPassingForUid(int uid) throws NameNotFoundException {
    // Add a couple of NOT_AUTHORIZED package to the UID to check that the SUT doesn't pick the
    // first one
    withPackagesForUid(
        uid,
        new String[] {NOT_AUTHORIZED_PACKAGE, AUTHORIZED_PACKAGE, NOT_AUTHORIZED_PACKAGE});

    // Add a couple of NOT_AUTHORIZED signatures to the package to check that the SUT doesn't pick
    // the first one.
    withPackageSignatures(
        AUTHORIZED_PACKAGE,
        new Signature[] {
            NOT_AUTHORIZED_SIGNATURE1, AUTHORIZED_SIGNATURE, NOT_AUTHORIZED_SIGNATURE2});
  }
}
