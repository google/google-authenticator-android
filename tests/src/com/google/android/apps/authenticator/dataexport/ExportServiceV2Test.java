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

import static com.google.testing.littlemock.LittleMock.doNothing;
import static com.google.testing.littlemock.LittleMock.doThrow;
import static com.google.testing.littlemock.LittleMock.initMocks;
import static com.google.testing.littlemock.LittleMock.verify;

import com.google.android.apps.authenticator.AccountDb;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.testing.littlemock.Mock;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Bundle;
import android.test.MoreAsserts;
import android.test.ServiceTestCase;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Unit tests for {@link ExportServiceV2}.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class ExportServiceV2Test extends ServiceTestCase<ExportServiceV2> {

  @Mock private AuthorizationChecker mMockAuthorizationChecker;
  private FakeExporter mFakeExporter;
  private AccountDb mAccountDb;

  public ExportServiceV2Test() {
    super(ExportServiceV2.class);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    DependencyInjector.resetForIntegrationTesting(getContext());

    initMocks(this);
    mFakeExporter = new FakeExporter(
        DependencyInjector.getAccountDb(),
        null);

    mAccountDb = DependencyInjector.getAccountDb();
  }

  @Override
  protected void setupService() {
    super.setupService();

    getService().mAuthorizationChecker = mMockAuthorizationChecker;
    getService().mExporter = mFakeExporter;
  }

  @Override
  protected void tearDown() throws Exception {
    DependencyInjector.close();

    super.tearDown();
  }

  public void testServiceExported() throws Exception {
    Intent intent = new Intent(getSystemContext(), ExportServiceV2.class);
    ResolveInfo resolveInfo = getSystemContext().getPackageManager().resolveService(intent, 0);
    ServiceInfo serviceInfo = resolveInfo.serviceInfo;
    assertTrue(serviceInfo.exported);
  }

  public void testGetData_whenAuthorized() throws Exception {
    Bundle expectedExportedData = new Bundle();
    withExportedData(expectedExportedData);
    withPassingAuthorizationCheck(Binder.getCallingUid());

    Bundle data = bind().getData();
    verifyAuthorizationCheckInvoked(Binder.getCallingUid());
    verifyExporterGetDataInvoked();
    assertSame(expectedExportedData, data);
  }

  public void testGetData_whenNotAuthorized() throws Exception {
    withFailingAuthorizationCheck(Binder.getCallingUid());

    IExportServiceV2 exporter = bind();
    try {
      exporter.getData();
      fail("SecurityException should have been thrown");
    } catch (SecurityException e) {
      // Expected
    }
    verifyAuthorizationCheckInvoked(Binder.getCallingUid());
    verifyExporterGetDataNotInvoked();
  }

  public void testOnImportSucceeded_whenAuthorized() throws Exception {
    withPassingAuthorizationCheck(Binder.getCallingUid());
    withAccountInAccountDb("account1");

    bind().onImportSucceeded();
    verifyAuthorizationCheckInvoked(Binder.getCallingUid());
    assertAccountDbEmpty();
  }

  public void testOnImportSucceeded_whenNotAuthorized() throws Exception {
    withFailingAuthorizationCheck(Binder.getCallingUid());
    withAccountInAccountDb("account1");

    IExportServiceV2 exporter = bind();
    try {
      exporter.onImportSucceeded();
      fail("SecurityException should have been thrown");
    } catch (SecurityException e) {
      // Expected
    }
    verifyAuthorizationCheckInvoked(Binder.getCallingUid());
    assertAccountDbAccountsInAnyOrder("account1");
  }

  public void testOnCreate_withoutMocksUsesCorrectImplementations() throws Exception {
    mFakeExporter = null;
    mMockAuthorizationChecker = null;

    bind();

    assertEquals(
        SignatureBasedAuthorizationChecker.class, getService().mAuthorizationChecker.getClass());
    assertEquals(
        Exporter.class, getService().mExporter.getClass());
  }

  private IExportServiceV2 bind() {
    return
        IExportServiceV2.Stub.asInterface(
            bindService(new Intent(getContext(), ExportServiceV2.class)));
  }

  private static class FakeExporter extends Exporter {
    private boolean getDataInvoked;
    private Bundle getDataResponse;

    private FakeExporter(AccountDb accountDb, SharedPreferences preferences) {
      super(accountDb, preferences);
    }

    @Override
    public Bundle getData() {
      getDataInvoked = true;
      return getDataResponse;
    }
  }

  private void withExportedData(Bundle data) {
    mFakeExporter.getDataResponse = data;
  }

  private void verifyExporterGetDataInvoked() {
    assertTrue(mFakeExporter.getDataInvoked);
  }

  private void verifyExporterGetDataNotInvoked() {
    assertFalse(mFakeExporter.getDataInvoked);
  }

  private void withPassingAuthorizationCheck(int uid) {
    doNothing().when(mMockAuthorizationChecker).checkAuthorization(uid);
  }


  private void withFailingAuthorizationCheck(int uid) {
    doThrow(new SecurityException()).when(mMockAuthorizationChecker).checkAuthorization(uid);
  }

  private void withAccountInAccountDb(String accountName) {
    mAccountDb.update(accountName, "AAAA", accountName, AccountDb.OtpType.TOTP, null);
  }

  private void verifyAuthorizationCheckInvoked(int uid) {
    verify(mMockAuthorizationChecker).checkAuthorization(uid);
  }

  private void assertAccountDbEmpty() {
    Collection<String> accountNames = new ArrayList<String>();
    mAccountDb.getNames(accountNames);
    MoreAsserts.assertEmpty(accountNames);
  }

  private void assertAccountDbAccountsInAnyOrder(Object... expectedAccountNames) {
    Collection<String> actualAccountNames = new ArrayList<String>();
    mAccountDb.getNames(actualAccountNames);
    MoreAsserts.assertContentsInAnyOrder(actualAccountNames, expectedAccountNames);
  }
}
