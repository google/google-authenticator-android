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

package com.google.android.apps.authenticator.dataimport;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.android.apps.authenticator.AccountDb;
import com.google.android.apps.authenticator.AccountDb.OtpType;
import com.google.android.apps.authenticator.testability.DependencyInjector;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;

import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for {@link Importer}.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class ImporterTest extends AndroidTestCase {

  private Importer mImporter;
  private AccountDb mAccountDb;
  @Mock private SharedPreferences mMockPreferences;
  @Mock private SharedPreferences.Editor mMockPreferencesEditor;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    DependencyInjector.resetForIntegrationTesting(getContext());
    initMocks(this);
    doReturn(mMockPreferencesEditor).when(mMockPreferences).edit();
    doReturn(true).when(mMockPreferencesEditor).commit();
    mAccountDb = DependencyInjector.getAccountDb();
    mImporter = new Importer();
  }

  @Override
  protected void tearDown() throws Exception {
    DependencyInjector.close();

    super.tearDown();
  }

  public void testImport_withNullBundle() {
    try {
      mImporter.importFromBundle(null, mAccountDb, mMockPreferences);
      fail("NullPointerExcepton should have been thrown");
    } catch (NullPointerException e) {
      // Expected
    }
  }

  public void testImport_withNullAccountDb() {
    mImporter.importFromBundle(new Bundle(), null, mMockPreferences);
  }

  public void testImport_withNullPreferences() {
    mImporter.importFromBundle(new Bundle(), mAccountDb, null);
  }

  public void testImportAccountDb_withAccounts() {
    String account2 = "a@gmail.com";
    String account1 = "b@gmail.com";
    Bundle bundle = bundle(
        accountsBundle(
            accountBundle(account1, "ABCDEFGHI", "hotp", 12345),
            accountBundle(account2, "ABCDEF", "totp", null)),
        null);

    mImporter.importFromBundle(bundle, mAccountDb, null);

    assertAccountsInDbInOrder(account1, account2);
    assertAccountInDb(account1, "ABCDEFGHI", OtpType.HOTP, 12345);
    assertAccountInDb(account2, "ABCDEF", OtpType.TOTP, 0);
  }

  public void testImportAccountDbDoesNotOverwriteExistingAccounts() {
    String account2 = "a@gmail.com";
    String account1 = "b@gmail.com";
    mAccountDb.update(account1, "AAAAAAAA", account1, OtpType.TOTP, 13);
    Bundle bundle = bundle(
        accountsBundle(
            accountBundle(account1, "ABCDEFGHI", "hotp", 12345),
            accountBundle(account2, "ABCDEF", "totp", null)),
        null);

    mImporter.importFromBundle(bundle, mAccountDb, null);

    assertAccountsInDbInOrder(account1, account2);
    assertAccountInDb(account1, "AAAAAAAA", OtpType.TOTP, 13);
    assertAccountInDb(account2, "ABCDEF", OtpType.TOTP, 0);
  }

  public void testImportAccountDb_withAccountsWithMissingName() {
    // First account's name is missing
    String account2 = "a@gmail.com";
    Bundle bundle = bundle(
        accountsBundle(
            accountBundle(null, "ABCDEFGHI", "hotp", 12345),
            accountBundle(account2, "ABCDEF", "totp", null)),
        null);

    mImporter.importFromBundle(bundle, mAccountDb, null);

    assertAccountsInDbInOrder(account2);
    assertAccountInDb(account2, "ABCDEF", OtpType.TOTP, 0);
  }

  public void testImportAccountDb_withAccountsWithMissingSecret() {
    // First account's secret is missing
    String account2 = "a@gmail.com";
    Bundle bundle = bundle(
        accountsBundle(
            accountBundle("test", null, "hotp", 12345),
            accountBundle(account2, "ABCDEF", "totp", null)),
        null);

    mImporter.importFromBundle(bundle, mAccountDb, null);

    assertAccountsInDbInOrder(account2);
    assertAccountInDb(account2, "ABCDEF", OtpType.TOTP, 0);
  }

  public void testImportAccountDb_withAccountsWithMissingType() {
    // First account's type is missing
    String account2 = "a@gmail.com";
    Bundle bundle = bundle(
        accountsBundle(
            accountBundle("test", "ABCDEFGHI", null, 12345),
            accountBundle(account2, "ABCDEF", "totp", null)),
        null);

    mImporter.importFromBundle(bundle, mAccountDb, null);

    assertAccountsInDbInOrder(account2);
    assertAccountInDb(account2, "ABCDEF", OtpType.TOTP, 0);
  }

  public void testImportAccountDb_withAccountsWithMissingHotpCounter() {
    // First account's HOTP counter is missing
    String account2 = "a@gmail.com";
    Bundle bundle = bundle(
        accountsBundle(
            accountBundle("test", "ABCDEFGHI", "hotp", null),
            accountBundle(account2, "ABCDEF", "totp", null)),
        null);

    mImporter.importFromBundle(bundle, mAccountDb, null);

    assertAccountsInDbInOrder(account2);
    assertAccountInDb(account2, "ABCDEF", OtpType.TOTP, 0);
  }

  public void testImportAccountDb_withAccountsWithMissingTotpCounter() {
    // Second account's TOTP counter is missing, but it doesn't matter as it's not needed
    String account1 = "b@gmail.com";
    String account2 = "a@gmail.com";
    Bundle bundle = bundle(
        accountsBundle(
            accountBundle(account1, "ABCDEFGHI", "hotp", 12345),
            accountBundle(account2, "ABCDEF", "totp", null)),
        null);

    mImporter.importFromBundle(bundle, mAccountDb, null);

    assertAccountsInDbInOrder(account1, account2);
    assertAccountInDb(account1, "ABCDEFGHI", OtpType.HOTP, 12345);
    assertAccountInDb(account2, "ABCDEF", OtpType.TOTP, 0);
  }

  public void testImportAccountDb_withNoAccounts() {
    Bundle bundle = bundle(accountsBundle(), null);

    mImporter.importFromBundle(bundle, mAccountDb, null);

    assertAccountsInDbInOrder();
  }

  public void testGetDataExportPreferences_withPreferences() {
    Bundle prefBundle = new Bundle();
    prefBundle.putBoolean("bool", true);
    prefBundle.putInt("int", 9);
    prefBundle.putFloat("float", 3.14f);
    prefBundle.putString("string", "testing");
    prefBundle.putLong("long", 0x123456DEADBEEFL);
    prefBundle.putStringArray("stringarray", new String[] {"1", "2", "3"});
    Bundle bundle = new Bundle();
    bundle.putBundle("preferences", prefBundle);

    mImporter.importFromBundle(bundle, null, mMockPreferences);

    verify(mMockPreferencesEditor).putBoolean("bool", true);
    verify(mMockPreferencesEditor).putInt("int", 9);
    verify(mMockPreferencesEditor).putFloat("float", 3.14f);
    verify(mMockPreferencesEditor).putString("string", "testing");
    verify(mMockPreferencesEditor).putLong("long", 0x123456DEADBEEFL);
    verify(mMockPreferencesEditor).commit();
    verifyNoMoreInteractions(mMockPreferencesEditor);
  }

  public void testGetDataExportPreferences_withEmptyPreferences() {
    Bundle prefBundle = new Bundle();
    Bundle bundle = new Bundle();
    bundle.putBundle("preferences", prefBundle);

    mImporter.importFromBundle(bundle, null, mMockPreferences);

    verify(mMockPreferencesEditor).commit();
    verifyNoMoreInteractions(mMockPreferencesEditor);
  }

  public void testImportPreferences_withNullPreferences() {
    mImporter.importFromBundle(new Bundle(), null, mMockPreferences);

    verifyZeroInteractions(mMockPreferences);
  }

  private static Bundle accountBundle(
      String name, String encodedSecret, String type, Integer counter) {
    Bundle result = new Bundle();
    if (name != null) {
      result.putString(Importer.KEY_NAME, name);
    }
    if (encodedSecret != null) {
      result.putString(Importer.KEY_ENCODED_SECRET, encodedSecret);
    }
    if (type != null) {
      result.putString(Importer.KEY_TYPE, type);
    }
    if (counter != null) {
      result.putInt(Importer.KEY_COUNTER, counter);
    }
    return result;
  }

  private static Bundle accountsBundle(Bundle... accountBundles) {
    Bundle result = new Bundle();
    if (accountBundles != null) {
      for (int i = 0, len = accountBundles.length; i < len; i++) {
        result.putBundle(String.valueOf(i), accountBundles[i]);
      }
    }
    return result;
  }

  private static Bundle bundle(Bundle accountsBundle, Bundle preferencesBundle) {
    Bundle result = new Bundle();
    if (accountsBundle != null) {
      result.putBundle(Importer.KEY_ACCOUNTS, accountsBundle);
    }
    if (preferencesBundle != null) {
      result.putBundle(Importer.KEY_PREFERENCES, preferencesBundle);
    }
    return result;
  }

  private void assertAccountInDb(String name, String encodedSecret, OtpType type, int counter) {
    assertEquals(encodedSecret, mAccountDb.getSecret(name));
    assertEquals(type, mAccountDb.getType(name));
    assertEquals(new Integer(counter), mAccountDb.getCounter(name));
  }

  private void assertAccountsInDbInOrder(String... expectedNames) {
    List<String> actualNames = new ArrayList<String>();
    mAccountDb.getNames(actualNames);
    MoreAsserts.assertContentsInOrder(actualNames, (Object[]) expectedNames);
  }
}
