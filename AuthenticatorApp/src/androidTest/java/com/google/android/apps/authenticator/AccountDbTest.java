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

package com.google.android.apps.authenticator;

import com.google.android.apps.authenticator.AccountDb.OtpType;
import com.google.android.apps.authenticator.PasscodeGenerator.Signer;
import com.google.android.apps.authenticator.testability.DependencyInjector;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Unit tests for {@link AccountDb}.
 * @author sarvar@google.com (Sarvar Patel)
 *
 * TestCases belonging to the same test suite that are run simultaneously may interfere
 * with each other because AccountDb instances in this class point to the same underlying database.
 * For the time being this is not an issue since tests for android are run sequentially.
 */
public class AccountDbTest extends  AndroidTestCase {
  private static final String MESSAGE = "hello";
  private static final String SIGNATURE = "2GOH22N7HTHRAC3C4IY24TWH6FEFEOZ7";
  private static final String SECRET = "7777777777777777"; // 16 sevens
  private static final String SECRET2 = "2222222222222222"; // 16 twos

  private Collection<String> result = new ArrayList<String>();
  private AccountDb accountDb;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    DependencyInjector.resetForIntegrationTesting(getContext());
    accountDb = DependencyInjector.getAccountDb();
  }

  @Override
  protected void tearDown() throws Exception {
    DependencyInjector.close();

    super.tearDown();
  }

  private void addSomeRecords() {
    accountDb.update("johndoe@gmail.com", SECRET, "johndoe@gmail.com", OtpType.TOTP, null);
    accountDb.update("amywinehouse@aol.com", SECRET2, "amywinehouse@aol.com", OtpType.TOTP, null);
    accountDb.update("maryweiss@yahoo.com", SECRET, "maryweiss@yahoo.com", OtpType.HOTP, 0);
  }

  public void testNoRecords() throws Exception {
    assertEquals(0, accountDb.getNames(result));
    assertEquals(0, result.size());
    assertEquals(false, accountDb.nameExists("johndoe@gmail.com"));
    assertEquals(null, accountDb.getSecret("johndoe@gmail.com"));
  }

  public void testGetNames() throws Exception {
    addSomeRecords();
    accountDb.getNames(result);
    MoreAsserts.assertContentsInAnyOrder(result,
        "johndoe@gmail.com", "amywinehouse@aol.com", "maryweiss@yahoo.com");

    // check nameExists()
    assertTrue(accountDb.nameExists("johndoe@gmail.com"));
    assertTrue(accountDb.nameExists("amywinehouse@aol.com"));
    assertTrue(accountDb.nameExists("maryweiss@yahoo.com"));
    assertFalse(accountDb.nameExists("marywinehouse@aol.com")); // non-existent email.
  }

  public void testGetSecret() throws Exception {
    addSomeRecords();
    assertEquals(SECRET, accountDb.getSecret("johndoe@gmail.com"));
    assertEquals(SECRET2, accountDb.getSecret("amywinehouse@aol.com"));
    assertEquals(null, accountDb.getSecret("marywinehouse@aol.com")); // non-existent email.
  }

  public void testGetAndIncrementCounter() throws Exception {
    addSomeRecords();
    assertEquals(0, (int) accountDb.getCounter("maryweiss@yahoo.com"));
    accountDb.incrementCounter("maryweiss@yahoo.com");
    assertEquals(1, (int) accountDb.getCounter("maryweiss@yahoo.com"));
    assertEquals(null, accountDb.getCounter("marywinehouse@yahoo.com")); // non-existent record.
    assertEquals(0, (int) accountDb.getCounter("amywinehouse@aol.com"));  // TOTP record
  }

  public void testGetAndSetType() throws Exception {
    addSomeRecords();
    assertTrue(accountDb.getType("johndoe@gmail.com").equals(OtpType.TOTP));
    assertTrue(accountDb.getType("maryweiss@yahoo.com").equals(OtpType.HOTP));
    assertFalse(accountDb.getType("amywinehouse@aol.com").equals(OtpType.HOTP));
    accountDb.setType("johndoe@gmail.com", OtpType.HOTP);
    assertTrue(accountDb.getType("johndoe@gmail.com").equals(OtpType.HOTP));
    // check that the counter retains its values.
    assertEquals(0, (int) accountDb.getCounter("johndoe@gmail.com"));
    // check that it can be reset to original value
    accountDb.setType("johndoe@gmail.com", OtpType.TOTP);
    assertTrue(accountDb.getType("johndoe@gmail.com").equals(OtpType.TOTP));
  }

  public void testGetAndSetAccountType() throws Exception {
    addSomeRecords();
    assertTrue(accountDb.getType("johndoe@gmail.com").equals(OtpType.TOTP));
    assertTrue(accountDb.getType("maryweiss@yahoo.com").equals(OtpType.HOTP));
    assertFalse(accountDb.getType("amywinehouse@aol.com").equals(OtpType.HOTP));
    accountDb.setType("johndoe@gmail.com", OtpType.HOTP);
    assertTrue(accountDb.getType("johndoe@gmail.com").equals(OtpType.HOTP));
    // check that the counter retains its values.
    assertEquals(0, (int) accountDb.getCounter("johndoe@gmail.com"));
    // check that it can be reset to original value
    accountDb.setType("johndoe@gmail.com", OtpType.TOTP);
    assertTrue(accountDb.getType("johndoe@gmail.com").equals(OtpType.TOTP));
  }

  public void testDelete() throws Exception {
    addSomeRecords();
    accountDb.delete("johndoe@gmail.com");
    assertEquals(2, accountDb.getNames(result));
    assertFalse(accountDb.nameExists("johndoe@gmail.com"));
    // re-add johndoe.
    accountDb.update("johndoe@gmail.com", SECRET, "johndoe@gmail.com", OtpType.TOTP, null);
    assertTrue(accountDb.nameExists("johndoe@gmail.com"));
  }

  public void testUpdate() throws Exception {
    addSomeRecords();
    // check updates with existing records - that it doesn't increase the records.
    accountDb.update("johndoe@gmail.com", SECRET, "johndoe@gmail.com", OtpType.TOTP, null);
    accountDb.getNames(result);
    MoreAsserts.assertContentsInAnyOrder(result,
        "johndoe@gmail.com", "amywinehouse@aol.com", "maryweiss@yahoo.com");
    // add new record.
    accountDb.update("johndoenew@gmail.com", SECRET, "johndoe@gmail.com", OtpType.TOTP, null);
    result.clear();
    accountDb.getNames(result);
    MoreAsserts.assertContentsInAnyOrder(result,
        "johndoenew@gmail.com", "amywinehouse@aol.com", "maryweiss@yahoo.com");
    // re-update with the original name
    accountDb.update("johndoe@gmail.com", SECRET, "johndoenew@gmail.com", OtpType.TOTP, null);
    result.clear();
    accountDb.getNames(result);
    MoreAsserts.assertContentsInAnyOrder(result,
        "johndoe@gmail.com", "amywinehouse@aol.com", "maryweiss@yahoo.com");
  }

  public void testIsGoogleAccount() {
    accountDb.update("1@b.c", SECRET, "1@b.c", OtpType.TOTP, null, true);
    accountDb.update("2@gmail.com", SECRET, "2@gmail.com", OtpType.TOTP, null);
    accountDb.update("3@google.com", SECRET, "3@google.com", OtpType.TOTP, null);
    accountDb.update("4", SECRET, "4", OtpType.HOTP, 3, true);
    accountDb.update("5@yahoo.co.uk", SECRET, "5@yahoo.co.uk", OtpType.TOTP, null);
    accountDb.update("gmail.com", SECRET, "gmail.com", OtpType.TOTP, null);
    accountDb.update(
        "Google Internal 2Factor", SECRET, "Google Internal 2Factor", OtpType.HOTP, null);
    assertTrue(accountDb.isGoogleAccount("1@b.c"));
    assertTrue(accountDb.isGoogleAccount("2@gmail.com"));
    assertTrue(accountDb.isGoogleAccount("3@google.com"));
    assertTrue(accountDb.isGoogleAccount("4"));
    assertFalse(accountDb.isGoogleAccount("5@yahoo.co.uk"));
    assertFalse(accountDb.isGoogleAccount("gmail.com"));
    assertTrue(accountDb.isGoogleAccount("Google Internal 2Factor"));
    assertFalse(accountDb.isGoogleAccount("non-existent account"));
  }

  public void testUpdateWithoutSourceValuePreservesSourceValue() {
    accountDb.update("a@b.c", SECRET, "a@b.c", OtpType.TOTP, null, true);
    accountDb.update("test@gmail.com", SECRET, "test@gmail.com", OtpType.TOTP, null);
    assertTrue(accountDb.isGoogleAccount("a@b.c"));
    assertTrue(accountDb.isGoogleAccount("test@gmail.com"));
    accountDb.update("b@a.c", SECRET, "a@b.c", OtpType.TOTP, null);
    accountDb.update("test@yahoo.com", SECRET, "test@gmail.com", OtpType.TOTP, null);
    assertTrue(accountDb.isGoogleAccount("b@a.c"));
    assertFalse(accountDb.isGoogleAccount("test@yahoo.com"));
  }

  public void testConstruct_whenNoDatabase() {
    deleteAccountDb();
    accountDb = DependencyInjector.getAccountDb();
  }

  public void testConstruct_whenDatabaseWithoutProviderColumn() {
    deleteAccountDb();
    SQLiteDatabase database =
        DependencyInjector.getContext().openOrCreateDatabase(
            AccountDb.PATH, Context.MODE_PRIVATE, null);
    database.execSQL("CREATE TABLE " + AccountDb.TABLE_NAME + " (first INTEGER)");
    MoreAsserts.assertContentsInAnyOrder(
        AccountDb.listTableColumnNamesLowerCase(database, AccountDb.TABLE_NAME),
        "first");
    database.close();
    database = null;

    accountDb = DependencyInjector.getAccountDb();
    MoreAsserts.assertContentsInAnyOrder(
        AccountDb.listTableColumnNamesLowerCase(accountDb.mDatabase, AccountDb.TABLE_NAME),
        "first", AccountDb.PROVIDER_COLUMN);
  }

  private void deleteAccountDb() {
    if (accountDb != null) {
      accountDb.close();
      accountDb = null;
    }
    DependencyInjector.setAccountDb(null);

    assertTrue(DependencyInjector.getContext().deleteDatabase(AccountDb.PATH));
  }

  public void testSigningOracle() throws Exception {
    Signer signer = AccountDb.getSigningOracle(SECRET);
    assertEquals(SIGNATURE, Base32String.encode(signer.sign(MESSAGE.getBytes())));
  }
}
