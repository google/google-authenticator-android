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
import static com.google.testing.littlemock.LittleMock.initMocks;

import com.google.android.apps.authenticator.AccountDb;
import com.google.android.apps.authenticator.AccountDb.OtpType;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.testing.littlemock.Mock;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Unit tests for {@link Exporter}.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class ExporterTest extends AndroidTestCase {

  private Exporter mExporter;
  private AccountDb mAccountDb;
  @Mock private SharedPreferences mMockPreferences;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    DependencyInjector.resetForIntegrationTesting(getContext());
    initMocks(this);
    mAccountDb = DependencyInjector.getAccountDb();
    mExporter = new Exporter(mAccountDb, mMockPreferences);
  }

  @Override
  protected void tearDown() throws Exception {
    DependencyInjector.close();

    super.tearDown();
  }

  public void testConstruct_withNullAccountDb() {
    try {
      new Exporter(null, mMockPreferences);
      fail("NullPointerExcepton should have been thrown");
    } catch (NullPointerException e) {
      // Expected
    }
  }

  public void testGetDataExportAccountDb_withAccounts() {
    mAccountDb.update("test@gmail.com", "ABCDEF", "test@gmail.com", OtpType.TOTP, 0);
    mAccountDb.update("test2@gmail.com", "ABCDEFGHI", "test2@gmail.com", OtpType.HOTP, 12345);

    Bundle data = mExporter.getData();
    Bundle accounts = data.getBundle("accountDb");
    MoreAsserts.assertContentsInAnyOrder(accounts.keySet(), "1", "2");

    Bundle totpAccount = accounts.getBundle("1");
    assertEquals("test@gmail.com", totpAccount.getString("name"));
    assertEquals("ABCDEF", totpAccount.getString("encodedSecret"));
    assertEquals("totp", totpAccount.getString("type"));
    assertEquals(0, totpAccount.getInt("counter"));

    Bundle hotpAccount = accounts.getBundle("2");
    assertEquals("test2@gmail.com", hotpAccount.getString("name"));
    assertEquals("ABCDEFGHI", hotpAccount.getString("encodedSecret"));
    assertEquals("hotp", hotpAccount.getString("type"));
    assertEquals(12345, hotpAccount.getInt("counter"));
  }

  public void testGetDataExportAccountDb_withNoAccounts() {
    Bundle data = mExporter.getData();
    Bundle accounts = data.getBundle("accountDb");
    MoreAsserts.assertEmpty(accounts.keySet());
  }

  public void testGetDataExportPreferences_withNullPreferences() {
    mExporter = new Exporter(mAccountDb, null);
    Bundle data = mExporter.getData();
    assertNull(data.getBundle("preferences"));
  }

  public void testGetDataExportPreferences_withPreferences() {
    Map<String, Object> preferencesMap = new HashMap<String, Object>();
    preferencesMap.put("bool", Boolean.TRUE);
    preferencesMap.put("int", new Integer(9));
    preferencesMap.put("float", new Float(3.14));
    preferencesMap.put("string", "testing");
    preferencesMap.put("long", new Long(0x123456DEADBEEFL));
    preferencesMap.put("weird", new HashSet<String>());
    doReturn(preferencesMap).when(mMockPreferences).getAll();

    Bundle data = mExporter.getData();
    Bundle preferencesBundle = data.getBundle("preferences");
    Set<String> expectedKeys = new HashSet<String>(preferencesMap.keySet());
    expectedKeys.remove("weird"); // dropped during export because its type is unsupported
    assertEquals(expectedKeys, preferencesBundle.keySet());
    assertEquals(true, preferencesBundle.getBoolean("bool"));
    assertEquals(9, preferencesBundle.getInt("int"));
    assertEquals(3.14f, preferencesBundle.getFloat("float"));
    assertEquals("testing", preferencesBundle.getString("string"));
    assertEquals(0x123456DEADBEEFL, preferencesBundle.getLong("long"));
  }

  public void testGetDataExportPreferences_withEmptyPreferences() {
    doReturn(new HashMap<String, Object>()).when(mMockPreferences).getAll();

    Bundle data = mExporter.getData();
    Bundle preferencesBundle = data.getBundle("preferences");
    MoreAsserts.assertEmpty(preferencesBundle.keySet());
  }
}
