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

package com.google.android.apps.authenticator.otp;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.apps.authenticator.otp.AccountDb.AccountDbIdUpdateFailureException;
import com.google.android.apps.authenticator.otp.AccountDb.AccountIndex;
import com.google.android.apps.authenticator.otp.AccountDb.OtpType;
import com.google.android.apps.authenticator.otp.PasscodeGenerator.Signer;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator.util.Base32String;
import com.google.android.apps.authenticator.util.FileUtilities;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link AccountDb}.
 *
 * <p>TestCases belonging to the same test suite that are run simultaneously may interfere
 * with each other because AccountDb instances in this class point to the same underlying database.
 * For the time being this is not an issue since tests for android are run sequentially.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class AccountDbTest {
  /**
   * A simple data structure for holding test account parameters.
   */
  private static class TestAccount extends AccountIndex {
    public String name;
    public String secret;
    public OtpType type;
    public Integer counter;
    public Boolean isGoogleAccount;
    public String issuer;

    public TestAccount(
        String name,
        String secret,
        OtpType type,
        Integer counter,
        Boolean isGoogleAccount,
        String issuer) {
      super(name, issuer);
      this.name = name;
      this.secret = secret;
      this.type = type;
      this.counter = counter;
      this.isGoogleAccount = isGoogleAccount;
      this.issuer = issuer;
    }

    public TestAccount(TestAccount t) {
      this(t.name, t.secret, t.type, t.counter, t.isGoogleAccount, t.issuer);
    }
  }

  private static final String MESSAGE = "hello";
  private static final String SIGNATURE = "2GOH22N7HTHRAC3C4IY24TWH6FEFEOZ7";
  private static final String SECRET = "7777777777777777"; // 16 sevens
  private static final String SECRET2 = "2222222222222222"; // 16 twos
  private static final String SECRET3 = "AAAAAAAABBBBBBBB";
  private static final String ISSUER = "Microsoft";
  private static final String ISSUER2 = "Yahoo";
  private static final String GOOGLE_ISSUER = AccountDb.GOOGLE_ISSUER_NAME;

  /**
   * A list of {@link TestAccount}s that all have a {@code null} issuer.
   */
  private static final TestAccount[] TEST_ACCOUNTS_WITH_NULL_ISSUER = {
      new TestAccount("1@b.c", SECRET, OtpType.TOTP, null, true, null),
      new TestAccount("2@gmail.com", SECRET, OtpType.TOTP, null, null, null),
      new TestAccount("3@google.com", SECRET, OtpType.TOTP, null, null, null),
      new TestAccount("4", SECRET, OtpType.HOTP, 3, true, null),
      new TestAccount("5@yahoo.co.uk", SECRET, OtpType.TOTP, null, null, null),
      new TestAccount("gmail.com", SECRET, OtpType.TOTP, null, null, null),
      new TestAccount("Google Internal 2Factor", SECRET, OtpType.HOTP, null, null, null),
      new TestAccount("johndoe@gmail.com", SECRET, OtpType.TOTP, null, null, null),
      new TestAccount("amywinehouse@aol.com", SECRET2, OtpType.TOTP, null, null, null),
      new TestAccount("maryweiss@yahoo.com", SECRET, OtpType.HOTP, 0, null, null),
      new TestAccount("everycombo@gmail.com", SECRET, OtpType.TOTP, null, null, null),
      new TestAccount("Yahoo:everycombo@gmail.com", SECRET, OtpType.TOTP, null, null, null),
      new TestAccount("123combo@gmail.com", SECRET, OtpType.TOTP, null, null, null),
      new TestAccount("Foobar:baz@gmail.com", SECRET, OtpType.TOTP, null, null, null),
      new TestAccount(ISSUER + ":mismatched2@some.com", SECRET, OtpType.TOTP, null, null, null),
      new TestAccount(ISSUER + ":  whitespace@dup.com", SECRET, OtpType.TOTP, null, null, null),
      new TestAccount("hotp1@example.com", SECRET, OtpType.HOTP, 123, null, null),
      new TestAccount("hotp2@example.com", SECRET, OtpType.HOTP, 456, null, null),
      new TestAccount("hotp@gmail.com", SECRET, OtpType.HOTP, 789, true, null),
      new TestAccount("Google:user@gmail.com", SECRET, OtpType.TOTP, null, null, null),
      new TestAccount("Dropbox:user@gmail.com", SECRET2, OtpType.TOTP, null, null, null),
  };

  /**
   * A list of {@link TestAccount}s that all have issuers.
   */
  private static final TestAccount[] TEST_ACCOUNTS_WITH_ISSUER = {
      new TestAccount("5@yahoo.co.uk", SECRET2, OtpType.TOTP, null, null, ISSUER2),
      new TestAccount("amywinehouse@aol.com", SECRET, OtpType.TOTP, null, null, ISSUER),
      new TestAccount("everycombo@gmail.com", SECRET, OtpType.TOTP, null, null, GOOGLE_ISSUER),
      new TestAccount("Yahoo:everycombo@gmail.com", SECRET2, OtpType.TOTP, null, null, "Yahoo"),
      // Could add add the non-prefix version too, as below, but it would just overwrite
      // new TestAccount("everycombo@gmail.com", SECRET3, OtpType.TOTP, null, null, "Yahoo"),
      new TestAccount("123combo@gmail.com", SECRET, OtpType.TOTP, null, null, GOOGLE_ISSUER),
      new TestAccount("Google:123combo@gmail.com", SECRET, OtpType.TOTP, null, null, ISSUER),
      new TestAccount("Foobar:baz@gmail.com", SECRET2, OtpType.TOTP, null, null, "Foobar"),
      new TestAccount("only2issuers@samename.com", SECRET, OtpType.TOTP, null, null, ISSUER),
      new TestAccount("only2issuers@samename.com", SECRET2, OtpType.TOTP, null, null, ISSUER2),
      new TestAccount("only1issuer@unique.com", SECRET, OtpType.TOTP, null, null, ISSUER),
      new TestAccount(ISSUER + ":mismatched1@some.com", SECRET, OtpType.TOTP, null, null, ISSUER2),
      new TestAccount(ISSUER + ":mismatched2@some.com", SECRET, OtpType.TOTP, null, null, ISSUER2),
      new TestAccount(ISSUER + ":  whitespace@some.com", SECRET, OtpType.TOTP, null, null, ISSUER),
      new TestAccount(ISSUER + ":  whitespace@dup.com", SECRET, OtpType.TOTP, null, null, ISSUER),
      new TestAccount("hotp1@example.com", SECRET, OtpType.HOTP, 1, null, ISSUER),
      new TestAccount("hotp2@example.com", SECRET, OtpType.HOTP, 22, null, ISSUER),
      new TestAccount("hotp@gmail.com", SECRET, OtpType.HOTP, 111, null, GOOGLE_ISSUER),
      new TestAccount("hotp@google.com", SECRET, OtpType.HOTP, 2222, null, GOOGLE_ISSUER),
  };

  private static final TestAccount[] TEST_ACCOUNTS;
  static {
    int size = TEST_ACCOUNTS_WITH_ISSUER.length + TEST_ACCOUNTS_WITH_NULL_ISSUER.length;
    TEST_ACCOUNTS = new TestAccount[size];
    int i = 0;
    for (TestAccount t : TEST_ACCOUNTS_WITH_ISSUER) {
      TEST_ACCOUNTS[i++] = t;
    }
    for (TestAccount t : TEST_ACCOUNTS_WITH_NULL_ISSUER) {
      TEST_ACCOUNTS[i++] = t;
    }
  }

  private AccountDb accountDb;
  private boolean expectDbConsistent;

  @Before
  public void setUp() throws Exception {
    DependencyInjector.resetForIntegrationTesting(
        InstrumentationRegistry.getInstrumentation().getTargetContext());
    AccountDb.deleteDatabase(DependencyInjector.getContext());
    accountDb = new AccountDb(DependencyInjector.getContext());
    expectDbConsistent = true;
  }

  @After
  public void tearDown() throws Exception {
    assertThat(!expectDbConsistent || accountDb.isDbConsistent()).isTrue();
    DependencyInjector.close();
  }

  @Test
  public void testNoRecords() {
    assertThat(accountDb.getAccounts()).isEmpty();
    assertThat(accountDb.indexExists(index("johndoe@gmail.com", null))).isFalse();
    assertThat(accountDb.getSecret(index("johndoe@gmail.com", null))).isNull();
    assertThat(accountDb.findSimilarExistingIndex(index("johndoe@gmail.com", null))).isNull();
    assertThat(accountDb.indexExists(index("johndoe@gmail.com", GOOGLE_ISSUER))).isFalse();
    assertThat(accountDb.getSecret(index("johndoe@gmail.com", GOOGLE_ISSUER))).isNull();
    assertThat(accountDb.findSimilarExistingIndex(index("johndoe@gmail.com", GOOGLE_ISSUER))).isNull();
  }

  @Test
  public void testGetAccountsWithNullIssuer() {
    addSomeRecordsWithNullIssuer();
    assertThat(accountDb.getAccounts()).containsExactly( (Object[]) TEST_ACCOUNTS_WITH_NULL_ISSUER);

    // check indexExists()
    for (TestAccount t : TEST_ACCOUNTS_WITH_NULL_ISSUER) {
      assertThat(accountDb.indexExists(t)).isTrue();
    }
    // non-existent name.
    assertThat(accountDb.indexExists(index("marywinehouse@aol.com", null))).isFalse();
  }

  @Test
  public void testGetAccountsWithIssuer() {
    addSomeRecordsWithIssuer();
    assertThat(accountDb.getAccounts()).containsExactly((Object[]) TEST_ACCOUNTS_WITH_ISSUER);

    // check indexExists()
    for (TestAccount t : TEST_ACCOUNTS_WITH_ISSUER) {
      assertThat(accountDb.indexExists(t)).isTrue();
    }
    // non-existent name.
    assertThat(accountDb.indexExists(index("marywinehouse@aol.com", null))).isFalse();
  }

  @Test
  public void testGetAccounts() {
    addAllTestRecords();
    assertThat(accountDb.getAccounts()).hasSize(TEST_ACCOUNTS.length);

    // check indexExists()
    for (TestAccount t : TEST_ACCOUNTS) {
      assertThat(accountDb.indexExists(t)).isTrue();
    }
    // non-existent name.
    assertThat(accountDb.indexExists(index("marywinehouse@aol.com", null))).isFalse();
  }

  @Test
  public void testIssuerNotParsedFromName() {
    addSomeRecordsWithNullIssuer();
    // The account "Foobar:baz@gmail.com" (with null issuer parameter only) exists
    assertThat(accountDb.indexExists(index("Foobar:baz@gmail.com", null))).isTrue();
    // However, "baz@gmail.com" (with or without the issuer parameter) does not exist
    assertThat(accountDb.indexExists(index("baz@gmail.com", null))).isFalse();
    assertThat(accountDb.indexExists(index("baz@gmail.com", "Foobar"))).isFalse();
    // Also, we have not added the version of "Foobar:baz@gmail.com" with the issuer parameter set
    assertThat(accountDb.indexExists(index("Foobar:baz@gmail.com", "Foobar"))).isFalse();
  }

  @Test
  public void testFindSimilarExistingIndex() {
    addAllTestRecords(); // Just to provide some extra data
    String accountName = "bob@example.com";
    String issuer = "Some issuer";
    String prefixedName = issuer + ":" + accountName;
    String whitespaceName = issuer + ":   " + accountName + "   ";
    AccountIndex plainWithNoIssuer = new AccountIndex(accountName, null);
    AccountIndex prefixedWithNoIssuer = new AccountIndex(prefixedName, null);
    AccountIndex whitespaceWithNoIssuer = new AccountIndex(whitespaceName, null);
    AccountIndex plainWithIssuer = new AccountIndex(accountName, issuer);
    AccountIndex prefixedWithIssuer = new AccountIndex(prefixedName, issuer);
    AccountIndex whitespaceWithIssuer = new AccountIndex(whitespaceName, issuer);

    assertThat(accountDb.findSimilarExistingIndex(prefixedWithNoIssuer)).isNull();
    accountDb.add(prefixedName, "AAAAA", OtpType.TOTP, null, false, null);
    assertThat(accountDb.findSimilarExistingIndex(prefixedWithNoIssuer))
        .isEqualTo(prefixedWithNoIssuer);
    // Stripped names are never "similar" for null issuers
    assertThat(accountDb.findSimilarExistingIndex(plainWithNoIssuer)).isNull();
    assertThat(accountDb.findSimilarExistingIndex(prefixedWithIssuer)).isNull();

    assertThat(accountDb.findSimilarExistingIndex(whitespaceWithNoIssuer)).isNull();
    accountDb.add(whitespaceName, "BBBBB", OtpType.TOTP, null, false, null);
    assertThat(accountDb.findSimilarExistingIndex(whitespaceWithNoIssuer))
        .isEqualTo(whitespaceWithNoIssuer);

    assertThat(accountDb.findSimilarExistingIndex(plainWithIssuer)).isNull();
    accountDb.add(accountName, "CCCCC", OtpType.TOTP, null, false, issuer);
    assertThat(accountDb.findSimilarExistingIndex(plainWithIssuer)).isEqualTo(plainWithIssuer);
    // Now find it using similar but different indexes
    assertThat(accountDb.findSimilarExistingIndex(prefixedWithIssuer)).isEqualTo(plainWithIssuer);
    assertThat(accountDb.findSimilarExistingIndex(whitespaceWithIssuer)).isEqualTo(plainWithIssuer);
    // Should still not be similar to the case of no issuer
    assertThat(accountDb.findSimilarExistingIndex(plainWithNoIssuer)).isNull();

    accountDb.delete(plainWithIssuer);
    assertThat(accountDb.findSimilarExistingIndex(plainWithIssuer)).isNull();
    assertThat(accountDb.findSimilarExistingIndex(prefixedWithIssuer)).isNull();
    assertThat(accountDb.findSimilarExistingIndex(whitespaceWithIssuer)).isNull();
    accountDb.add(prefixedName, "DDDDD", OtpType.TOTP, null, false, issuer);
    assertThat(accountDb.findSimilarExistingIndex(prefixedWithIssuer))
        .isEqualTo(prefixedWithIssuer);
    // Now find it using similar but different indexes
    assertThat(accountDb.findSimilarExistingIndex(plainWithIssuer)).isEqualTo(prefixedWithIssuer);
    assertThat(accountDb.findSimilarExistingIndex(whitespaceWithIssuer))
        .isEqualTo(prefixedWithIssuer);

    accountDb.delete(prefixedWithIssuer);
    assertThat(accountDb.findSimilarExistingIndex(plainWithIssuer)).isNull();
    assertThat(accountDb.findSimilarExistingIndex(prefixedWithIssuer)).isNull();
    assertThat(accountDb.findSimilarExistingIndex(whitespaceWithIssuer)).isNull();
    accountDb.add(whitespaceName, "EEEEE", OtpType.TOTP, null, false, issuer);
    assertThat(accountDb.findSimilarExistingIndex(whitespaceWithIssuer))
        .isEqualTo(whitespaceWithIssuer);
    // Now find it using similar but different indexes
    assertThat(accountDb.findSimilarExistingIndex(plainWithIssuer)).isEqualTo(whitespaceWithIssuer);
    assertThat(accountDb.findSimilarExistingIndex(prefixedWithIssuer))
        .isEqualTo(whitespaceWithIssuer);
  }

  @Test
  public void testGetPrefixedNameFor() {
    String accountName = "accountname";
    String issuer = "Some issuer";
    String prefixedName = issuer + ":" + accountName;
    String whitespacePrefixedName = issuer + ":  " + accountName;
    assertThat(AccountDb.getPrefixedNameFor(accountName, issuer)).isEqualTo(prefixedName);
    // If the name is already prefixed, it should be returned unaltered
    assertThat(AccountDb.getPrefixedNameFor(prefixedName, issuer)).isEqualTo(prefixedName);
    assertThat(AccountDb.getPrefixedNameFor(whitespacePrefixedName, issuer))
        .isEqualTo(whitespacePrefixedName);

    for (AccountIndex index : TEST_ACCOUNTS_WITH_ISSUER) {
      assertThat(AccountDb.getPrefixedNameFor(index.getName(), index.getIssuer()))
          .startsWith(index.getIssuer() + ":");
    }
  }

  @Test
  public void testGetStrippedName() {
    String accountName = "accountname";
    String issuer = "Some issuer";
    String prefixedName = issuer + ":" + accountName;
    String whitespacePrefixedName = issuer + ":  " + accountName + "   ";
    AccountIndex withIssuer = index(accountName, issuer);
    AccountIndex withIssuerAndPrefix = index(prefixedName, issuer);
    AccountIndex withIssuerAndWhitespacePrefix = index(whitespacePrefixedName, issuer);

    assertThat(withIssuer.getStrippedName()).isEqualTo(accountName);
    assertThat(withIssuerAndPrefix.getStrippedName()).isEqualTo(accountName);
    assertThat(withIssuerAndWhitespacePrefix.getStrippedName()).isEqualTo(accountName);

    for (AccountIndex index : TEST_ACCOUNTS_WITH_ISSUER) {
      assertThat(index.getStrippedName().startsWith(index.getIssuer() + ":")).isFalse();
    }

    for (AccountIndex index : TEST_ACCOUNTS_WITH_NULL_ISSUER) {
      assertThat(index.getStrippedName()).isEqualTo(index.getName().trim());
    }
  }

  @Test
  public void testGetSecret() {
    addAllTestRecords();
    assertThat(accountDb.getSecret(index("johndoe@gmail.com", null))).isEqualTo(SECRET);
    assertThat(accountDb.getSecret(index("amywinehouse@aol.com", null))).isEqualTo(SECRET2);
    assertThat(accountDb.getSecret(index("everycombo@gmail.com", null))).isEqualTo(SECRET);
    assertThat(accountDb.getSecret(index("Yahoo:everycombo@gmail.com", null))).isEqualTo(SECRET);
    assertThat(accountDb.getSecret(index("everycombo@gmail.com", GOOGLE_ISSUER))).isEqualTo(SECRET);
    assertThat(accountDb.getSecret(index("Yahoo:everycombo@gmail.com", "Yahoo")))
        .isEqualTo(SECRET2);
    // Try a non-existent name
    assertThat(accountDb.getSecret(index("marywinehouse@aol.com", null))).isNull();
  }

  @Test
  public void testGetAndIncrementCounter() {
    addAllTestRecords();
    assertThat((int) accountDb.getCounter(index("maryweiss@yahoo.com", null))).isEqualTo(0);
    accountDb.incrementCounter(index("maryweiss@yahoo.com", null));
    assertThat((int) accountDb.getCounter(index("maryweiss@yahoo.com", null))).isEqualTo(1);
    // non-existent record
    assertThat(accountDb.getCounter(index("MARYwinehouse@yahoo.com", null))).isNull();
    // TOTP record
    assertThat((int) accountDb.getCounter(index("amywinehouse@aol.com", null))).isEqualTo(0);

    for (TestAccount t : TEST_ACCOUNTS_WITH_ISSUER) {
      if (t.type == OtpType.HOTP) {
        assertThat(accountDb.getCounter(t)).isEqualTo(t.counter);
      }
    }
  }

  @Test
  public void testGetType() {
    addAllTestRecords();
    assertThat(accountDb.getType(index("johndoe@gmail.com", null))).isEqualTo(OtpType.TOTP);
    assertThat(accountDb.getType(index("maryweiss@yahoo.com", null))).isEqualTo(OtpType.HOTP);
    assertThat(accountDb.getType(index("amywinehouse@aol.com", null))).isEqualTo(OtpType.TOTP);

    for (TestAccount t : TEST_ACCOUNTS) {
      assertThat(accountDb.getType(t)).isEqualTo(t.type);
    }
  }

  @Test
  public void testDelete() {
    addAllTestRecords();
    accountDb.delete(index("johndoe@gmail.com", null));
    assertThat(accountDb.getAccounts()).hasSize(TEST_ACCOUNTS.length - 1);
    assertThat(accountDb.indexExists(index("johndoe@gmail.com", null))).isFalse();
    // re-add johndoe.
    accountDb.add("johndoe@gmail.com", SECRET, OtpType.TOTP, null, null, null);
    assertThat(accountDb.indexExists(index("johndoe@gmail.com", null))).isTrue();
    assertThat(accountDb.getAccounts()).hasSize(TEST_ACCOUNTS.length);

    for (TestAccount t : TEST_ACCOUNTS) {
      accountDb.delete(t);
      assertThat(accountDb.getAccounts()).hasSize(TEST_ACCOUNTS.length - 1);
      addTestAccount(t); // Re-add the deleted account
      assertThat(accountDb.getAccounts()).hasSize(TEST_ACCOUNTS.length);
    }
  }

  @Test
  public void testAddWithNullIssuer() {
    String accountName = "johndoe@dasher.com";
    // Add the account as a "Google Account" and verify the result
    accountDb.add(accountName, SECRET, OtpType.HOTP, 3, true, null);
    assertThat(accountDb.getAccounts()).containsExactly(index(accountName, null));
    validateAccountRecord(index(accountName, null), SECRET, OtpType.HOTP, 3, true);

    // Add with the same name again, which should create a new account with the
    // suffix "(1)"
    accountDb.add(accountName, SECRET2, OtpType.TOTP, 7, null, null);
    String accountName2 = accountName + "(1)";
    assertThat(accountDb.getAccounts())
        .containsExactly(index(accountName, null), index(accountName2, null));
    validateAccountRecord(index(accountName2, null), SECRET2, OtpType.TOTP, 7, false);
    // Check that the first account was unaffected
    validateAccountRecord(index(accountName, null), SECRET, OtpType.HOTP, 3, true);

    // And again, this time creating an account with suffix "(2)"
    // Use an empty string for issuer, which should be treated equivalently to a null
    accountDb.add(accountName, SECRET3, OtpType.TOTP, 1, false, "");
    String accountName3 = accountName + "(2)";
    assertThat(accountDb.getAccounts())
        .containsExactly(index(accountName, null), index(accountName2, null) , index(accountName3, null));
    validateAccountRecord(index(accountName3, null), SECRET3, OtpType.TOTP, 1, false);
    // Check that the other two accounts are unaffected
    validateAccountRecord(index(accountName2, null), SECRET2, OtpType.TOTP, 7, false);
    validateAccountRecord(index(accountName, null), SECRET, OtpType.HOTP, 3, true);

    // Test max duplicate account name limit
    String anotherAccountName = "janedoe@example.com";
    for (int i = 0; i < AccountDb.MAX_DUPLICATE_NAMES; i++) {
      accountDb.add(anotherAccountName, SECRET + i, OtpType.TOTP, null, false, null);
      String expectedAccountName = anotherAccountName + "(" + i + ")";
      if (i == 0) {
        expectedAccountName = anotherAccountName;
      }
      validateAccountRecord(index(expectedAccountName, null), SECRET + i, OtpType.TOTP, 0, false);
    }
    // Now add one more than the limit
    try {
      accountDb.add(anotherAccountName, SECRET, OtpType.TOTP, null, false, null);
      fail();
    } catch (AccountDb.AccountDbDuplicateLimitException expected) {
      // pass
    }
  }

  @Test
  public void testAddWithGoogleCorpAccount() {
    AccountIndex index = index(AccountDb.GOOGLE_CORP_ACCOUNT_NAME, null);
    // No existing account yet
    assertThat(accountDb.addWillOverwriteExistingSeedFor(index)).isFalse();

    addSomeRecordsWithNullIssuer(); // Adds a Google Internal 2Factor account
    assertThat(accountDb.addWillOverwriteExistingSeedFor(index)).isTrue();
    validateAccountRecord(index, SECRET, OtpType.HOTP, 0, true);

    // Try re-adding the account, which should overwrite despite the null issuer
    accountDb.add(AccountDb.GOOGLE_CORP_ACCOUNT_NAME, SECRET2, OtpType.HOTP, 0, null, null);
    validateAccountRecord(index, SECRET2, OtpType.HOTP, 0, true);

    assertThat(accountDb.getAccounts()).hasSize(TEST_ACCOUNTS_WITH_NULL_ISSUER.length);
  }

  @Test
  public void testAddWithIssuer() {
    String accountName = "johndoe@dasher.com";
    // Add the account as a "Google Account" and verify the result
    accountDb.add(accountName, SECRET, OtpType.TOTP, null, null, GOOGLE_ISSUER);
    assertThat(accountDb.getAccounts()).containsExactly(index(accountName, GOOGLE_ISSUER));
    validateAccountRecord(index(accountName, GOOGLE_ISSUER), SECRET, OtpType.TOTP, 0, true);

    // Re-add the same account, overwriting the current values
    accountDb.add(accountName, SECRET2, OtpType.HOTP, 4, null, GOOGLE_ISSUER);
    assertThat(accountDb.getAccounts()).containsExactly(index(accountName, GOOGLE_ISSUER));
    validateAccountRecord(index(accountName, GOOGLE_ISSUER), SECRET2, OtpType.HOTP, 4, true);

    // Add an account with the same name but a different issuer, which should not conflict
    accountDb.add(accountName, SECRET3, OtpType.TOTP, 8, null, ISSUER2);
    assertThat(accountDb.getAccounts())
        .containsExactly(index(accountName, GOOGLE_ISSUER), index(accountName, ISSUER2));
    validateAccountRecord(index(accountName, ISSUER2), SECRET3, OtpType.TOTP, 8, false);
    // Check that the GOOGLE_ISSUER account was unaffected
    validateAccountRecord(index(accountName, GOOGLE_ISSUER), SECRET2, OtpType.HOTP, 4, true);
  }

  @Test
  public void testAddAndOverwrite() {
    addAllTestRecords();
    int expectedNumAccounts = TEST_ACCOUNTS.length;
    for (TestAccount t : TEST_ACCOUNTS) {
      assertThat(accountDb.getAccounts()).hasSize(expectedNumAccounts);
      // Create a new secret each iteration
      String newSecret = "ZZZZZZZZZZZZZ"
          + ('A' + (char) (expectedNumAccounts % 26))
          + ('A' + (char) ((expectedNumAccounts / 26) % 26));

      // Try overwriting with an account that has the stripped name
      AccountIndex newIndex = index(t.getStrippedName(), t.getIssuer());
      if (!accountDb.addWillOverwriteExistingSeedFor(newIndex)) {
        expectedNumAccounts++;
      }
      accountDb.add(newIndex.getName(), newSecret, t.type, t.counter, false, newIndex.getIssuer());
      assertThat(accountDb.getAccounts()).hasSize(expectedNumAccounts);

      // Try overwriting with an account that has the full name
      newIndex = index(t.toString(), t.getIssuer());
      if (!accountDb.addWillOverwriteExistingSeedFor(newIndex)) {
        expectedNumAccounts++;
      }
      accountDb.add(newIndex.getName(), newSecret, t.type, t.counter, false, newIndex.getIssuer());
      assertThat(accountDb.getAccounts()).hasSize(expectedNumAccounts);
    }

    // Manual test
    AccountIndex first = index("LostPass:  whitespace@some.com", "LostPass");
    AccountIndex second = index("whitespace@some.com", "LostPass");
    AccountIndex third = index("LostPass:whitespace@some.com", "LostPass");

    assertThat(accountDb.addWillOverwriteExistingSeedFor(first)).isFalse();
    expectedNumAccounts++;
    accountDb.add(first.getName(), "AAAAAAAAAAAA", OtpType.TOTP, null, null, first.getIssuer());
    validateAccountRecord(first, "AAAAAAAAAAAA", OtpType.TOTP, 0, false);
    assertThat(accountDb.getAccounts()).hasSize(expectedNumAccounts);

    assertThat(accountDb.addWillOverwriteExistingSeedFor(second)).isTrue();
    assertThat(accountDb.addWillOverwriteExistingSeedFor(third)).isTrue();
    accountDb.add(second.getName(), "BBBBBBBBBBBB", OtpType.TOTP, null, null, second.getIssuer());
    validateAccountRecord(second, "BBBBBBBBBBBB", OtpType.TOTP, 0, false);
    accountDb.add(third.getName(), "CCCCCCCCCCCC", OtpType.TOTP, null, null, third.getIssuer());
    validateAccountRecord(third, "CCCCCCCCCCCC", OtpType.TOTP, 0, false);
    assertThat(accountDb.getAccounts()).hasSize(expectedNumAccounts);
  }

  @Test
  public void testSwapId() throws AccountDbIdUpdateFailureException {
    addAllTestRecords();
    int firstIdBeforeUpdate = accountDb.getId(TEST_ACCOUNTS[0]);
    int secondIdBeforeUpdate = accountDb.getId(TEST_ACCOUNTS[1]);

    accountDb.swapId(TEST_ACCOUNTS[0], TEST_ACCOUNTS[1]);

    assertThat(accountDb.getId(TEST_ACCOUNTS[1])).isEqualTo(firstIdBeforeUpdate);
    assertThat(accountDb.getId(TEST_ACCOUNTS[0])).isEqualTo(secondIdBeforeUpdate);
  }

  /**
   * Note: caller should carefully determine the expected value of {@code isGoogleAccount}, which
   * is affected by multiple factors.
   *
   * @param isGoogleAccount is a {@code boolean}, not a {@link Boolean}
   */
  private void validateAccountRecord(
      AccountIndex index, String secret, OtpType type, Integer counter, boolean isGoogleAccount) {
    Integer expectedCounter = 0; // a null argument means counter = 0
    if (counter != null) {
      expectedCounter = counter;
    }
    assertThat(accountDb.indexExists(index)).isTrue();
    assertThat(accountDb.getSecret(index)).isEqualTo(secret);
    assertThat(accountDb.getType(index)).isEqualTo(type);
    assertThat(accountDb.getCounter(index)).isEqualTo(expectedCounter);
    assertThat(accountDb.isGoogleAccount(index)).isEqualTo(isGoogleAccount);
  }

  @Test
  public void testAddWithLegacyAccountsFromOlderDatabase() {
    assertThat(AccountDb.deleteDatabase(DependencyInjector.getContext())).isTrue();
    SQLiteDatabase database = createOlderDatabaseWithRecords();
    database.close();
    database = null;

    accountDb = new AccountDb(DependencyInjector.getContext());

    // Lookup an account and validate it
    TestAccount orig = findTestAccountByName(TEST_ACCOUNTS_WITH_NULL_ISSUER, "johndoe@gmail.com");
    validateAccountRecord(orig, orig.secret, orig.type, orig.counter, true);
    // Old records have no OriginalName set
    assertThat(accountDb.getOriginalName(orig)).isNull();

    // Add a duplicate account with no issuer, to see that it is renamed
    TestAccount dup = new TestAccount(orig);
    dup.secret = SECRET2;
    addTestAccount(dup);
    String dupName = dup.name + "(1)"; // Expected renaming
    // Due to the renaming, we no longer assume this is a Google account based on the name alone
    validateAccountRecord(index(dupName, null), dup.secret, dup.type, dup.counter, false);
    // Check that the original was unaffected
    validateAccountRecord(orig, orig.secret, orig.type, orig.counter, true);

    // These accounts are assumed to be a Google account iff they are from an old database
    assertThat(accountDb.isGoogleAccount(index("2@gmail.com", null))).isTrue();
    assertThat(accountDb.isGoogleAccount(index("3@google.com", null))).isTrue();
  }

  @Test
  public void testUpdateWithNullIssuer() {
    doTestUpdate(null);
  }

  @Test
  public void testUpdateWithGoogleIssuer() {
    doTestUpdate(GOOGLE_ISSUER);
  }

  @Test
  public void testUpdateWithNonGoogleIssuer() {
    doTestUpdate(ISSUER);
  }

  public void doTestUpdate(String issuer) {
    String accountName = "johndoe@dasher.com";
    AccountIndex index = index(accountName, issuer);
    boolean expectGoogleAccount = (issuer == null) || issuer.equals(GOOGLE_ISSUER);
    // Add the account as a "Google Account", but that flag is only respected for null issuer
    accountDb.add(accountName, SECRET, OtpType.HOTP, 3, true, issuer);
    assertThat(accountDb.getAccounts()).containsExactly(index).inOrder();
    validateAccountRecord(index, SECRET, OtpType.HOTP, 3, expectGoogleAccount);

    // Now update the account overwriting the existing entry, except for the
    // "is Google Account" flag which should be preserved.
    accountDb.update(index, SECRET2, OtpType.TOTP, 7, null);
    assertThat(accountDb.getAccounts()).containsExactly(index).inOrder();
    validateAccountRecord(index, SECRET2, OtpType.TOTP, 7, expectGoogleAccount);

    // Update the account again, overwriting the existing entry, including the
    // "is Google Account" flag this time.
    expectGoogleAccount = (issuer != null) && issuer.equals(GOOGLE_ISSUER);
    accountDb.update(index, SECRET3, OtpType.TOTP, 1, false);
    assertThat(accountDb.getAccounts()).containsExactly(index).inOrder();
    validateAccountRecord(index, SECRET3, OtpType.TOTP, 1, expectGoogleAccount);
  }

  @Test
  public void testRenameWithNullIssuer() {
    doTestRename(null);
  }

  @Test
  public void testRenameWithGoogleIssuer() {
    doTestRename(GOOGLE_ISSUER);
  }

  @Test
  public void testRenameWithNonGoogleIssuer() {
    doTestRename(ISSUER);
  }

  public void doTestRename(String issuer) {
    String accountName = "johndoe@gmail.com";
    AccountIndex index = index(accountName, issuer);
    String newAccountName = "johndoe@yahoo.com";
    AccountIndex newIndex = index(newAccountName, issuer);

    boolean expectGoogleAccount = (issuer == null) || issuer.equals(GOOGLE_ISSUER);
    accountDb.add(accountName, SECRET, OtpType.TOTP, 5, true, issuer);
    assertThat(accountDb.getAccounts()).containsExactly(index).inOrder();
    validateAccountRecord(index, SECRET, OtpType.TOTP, 5, expectGoogleAccount);

    // Rename -- check that everything (except the name) is preserved
    assertThat(accountDb.rename(index, newAccountName)).isTrue();
    assertThat(accountDb.getAccounts()).containsExactly(newIndex).inOrder();
    // The "Google Account" flag should be preserved since it was explicitly set in the add() above
    validateAccountRecord(newIndex, SECRET, OtpType.TOTP, 5, expectGoogleAccount);

    // Rename back to the original
    assertThat(accountDb.rename(newIndex, accountName)).isTrue();
    assertThat(accountDb.getAccounts()).containsExactly(index).inOrder();
    validateAccountRecord(index, SECRET, OtpType.TOTP, 5, expectGoogleAccount);
  }

  @Test
  public void testRenameToSameNameWithNullIssuer() {
    doTestRenameToSameName(null);
  }

  @Test
  public void testRenameToSameNameWithGoogleIssuer() {
    doTestRenameToSameName(GOOGLE_ISSUER);
  }

  @Test
  public void testRenameToSameNameWithNonGoogleIssuer() {
    doTestRenameToSameName(ISSUER);
  }

  public void doTestRenameToSameName(String issuer) {
    String accountName = "johndoe@gmail.com";
    AccountIndex index = index(accountName, issuer);
    boolean expectGoogleAccount = (issuer == null) || issuer.equals(GOOGLE_ISSUER);
    accountDb.add(accountName, SECRET, OtpType.TOTP, 5, true, issuer);
    assertThat(accountDb.getAccounts()).containsExactly(index).inOrder();
    validateAccountRecord(index, SECRET, OtpType.TOTP, 5, expectGoogleAccount);

    assertThat(accountDb.rename(index, accountName)).isTrue();
    assertThat(accountDb.getAccounts()).containsExactly(index).inOrder();
    validateAccountRecord(index, SECRET, OtpType.TOTP, 5, expectGoogleAccount);
  }

  @Test
  public void testRenameToExistingNameFailsWithNullIssuer() {
   doTestRenameToExistingNameFails(null);
  }

  @Test
  public void testRenameToExistingNameFailsWithGoogleIssuer() {
    doTestRenameToExistingNameFails(GOOGLE_ISSUER);
  }

  @Test
  public void testRenameToExistingNameFailsWithNonGoogleIssuer() {
    doTestRenameToExistingNameFails(ISSUER);
  }

  public void doTestRenameToExistingNameFails(String issuer) {
    String accountName1 = "johndoe@gmail.com";
    AccountIndex index1 = index(accountName1, issuer);
    String accountName2 = "another@dasher.com";
    AccountIndex index2 = index(accountName2, issuer);

    accountDb.add(accountName1, SECRET, OtpType.TOTP, 5, true, issuer);
    accountDb.add(accountName2, SECRET, OtpType.HOTP, 3, false, issuer);

    // Verify that the rename attempt fails and does not change the database
    assertThat(accountDb.rename(index1, accountName2)).isFalse();
    assertThat(accountDb.getAccounts()).containsExactly(index1, index2);
    boolean expectGoogleAccount1 = (issuer == null) || issuer.equals(GOOGLE_ISSUER);
    boolean expectGoogleAccount2 = (issuer != null) && issuer.equals(GOOGLE_ISSUER);
    validateAccountRecord(index1, SECRET, OtpType.TOTP, 5, expectGoogleAccount1);
    validateAccountRecord(index2, SECRET, OtpType.HOTP, 3, expectGoogleAccount2);
  }

  @Test
  public void testRenameGoogleCorpAccountNotSupported() {
    String accountName = AccountDb.GOOGLE_CORP_ACCOUNT_NAME;
    accountDb.add(accountName, SECRET, OtpType.HOTP, 1, true, null);
    try {
      accountDb.rename(index(accountName, null), "newName");
      fail();
    } catch (UnsupportedOperationException expected) {}
  }

  @Test
  public void testIsGoogleAcountWithIssuer() {
    addSomeRecordsWithIssuer();
    for (TestAccount t : TEST_ACCOUNTS_WITH_ISSUER) {
      assertThat(accountDb.isGoogleAccount(t)).isEqualTo(GOOGLE_ISSUER.equals(t.issuer));
    }
  }

  @Test
  public void testIsGoogleAccountWithNullIssuer() {
    addSomeRecordsWithNullIssuer();
    assertThat(accountDb.isGoogleAccount(index("1@b.c", null))).isTrue();
    // Note: New accounts with @gmail or @google addresses are no longer assumed to be Google
    // accounts unless they also have the Google issuer
    assertThat(accountDb.isGoogleAccount(index("2@gmail.com", null))).isFalse();
    assertThat(accountDb.isGoogleAccount(index("3@google.com", null))).isFalse();
    assertThat(accountDb.isGoogleAccount(index("4", null))).isTrue();
    assertThat(accountDb.isGoogleAccount(index("5@yahoo.co.uk", null))).isFalse();
    assertThat(accountDb.isGoogleAccount(index("gmail.com", null))).isFalse();
    assertThat(accountDb.isGoogleAccount(index("Google Internal 2Factor", null))).isTrue();
    assertThat(accountDb.isGoogleAccount(index("non-existent account", null))).isFalse();
  }

  @Test
  public void testFindMatchingGoogleAccountWithoutPrefix() {
    doTestFindMatchingGoogleAccount(false);
  }

  @Test
  public void testFindMatchingGoogleAccountWithPrefix() {
    doTestFindMatchingGoogleAccount(true);
  }

  public void doTestFindMatchingGoogleAccount(boolean usePrefix) {
    addAllTestRecords();
    String prefix = "";
    if (usePrefix) {
      prefix = "Google:";
    }
    String gmailUserAccountName = "a.new.user@gmail.com";
    String dasherUserAccountName = "carol@new-dasher.com";

    // Test with a non-matching account
    assertThat(accountDb.findMatchingGoogleAccount("no.such.accountname@gmail.com")).isNull();

    // Test a corp account name
    assertThat(accountDb.findMatchingGoogleAccount("sergey@google.com").getName())
        .isEqualTo(AccountDb.GOOGLE_CORP_ACCOUNT_NAME);

    // Try with a new style account for a gmail user
    TestAccount gmailUserWithIssuer = new TestAccount(
        prefix + gmailUserAccountName, SECRET, OtpType.TOTP, null, null, GOOGLE_ISSUER);
    assertThat(findMatchingGoogleWorksFor(gmailUserWithIssuer)).isTrue();
    assertThat(findMatchingGoogleAccountWorksAfterRenamingFor(gmailUserWithIssuer)).isTrue();

    // Try with a new style account for a dasher user
    TestAccount dasherUserWithIssuer = new TestAccount(
        prefix + dasherUserAccountName, SECRET, OtpType.TOTP, null, null, GOOGLE_ISSUER);
    assertThat(findMatchingGoogleWorksFor(dasherUserWithIssuer)).isTrue();
    assertThat(findMatchingGoogleAccountWorksAfterRenamingFor(dasherUserWithIssuer)).isTrue();

    // Try with an old style account for a gmail user
    TestAccount gmailUserWithNullIssuer = new TestAccount(
        prefix + gmailUserAccountName, SECRET, OtpType.TOTP, null, null, null);
    assertThat(findMatchingGoogleWorksFor(gmailUserWithNullIssuer)).isTrue();
    assertThat(findMatchingGoogleAccountWorksAfterRenamingFor(gmailUserWithNullIssuer)).isFalse();

    // Try with an old style account for a dasher user
    TestAccount dasherUserWithNullIssuer = new TestAccount(
        prefix + dasherUserAccountName, SECRET, OtpType.TOTP, null, null, null);
    assertThat(findMatchingGoogleWorksFor(dasherUserWithNullIssuer)).isTrue();
    assertThat(findMatchingGoogleAccountWorksAfterRenamingFor(dasherUserWithNullIssuer)).isFalse();
  }

  private boolean findMatchingGoogleAccountWorksAfterRenamingFor(TestAccount t) {
    addTestAccount(t);
    AccountIndex index = index("CustomName", t.getIssuer());
    accountDb.rename(t, index.getName());
    try {
      return index.equals(accountDb.findMatchingGoogleAccount(t.getName()));
    } finally {
      accountDb.delete(index);
    }
  }

  private boolean findMatchingGoogleWorksFor(TestAccount t) {
    addTestAccount(t);
    try {
      return t.equals(accountDb.findMatchingGoogleAccount(t.getName()));
    } finally {
      accountDb.delete(t);
    }
  }

  @Test
  public void testRenamePreservesSourceFlagWithNullIssuer() {
    // The output of isGoogleAccount depends on the "source flag" and the name of the account.

    // Source flag explicitly set to "Google Account" -- isGoogleAccount should not depend on the
    // name
    accountDb.add("a@b.c", SECRET, OtpType.TOTP, null, true, null);
    assertThat(accountDb.isGoogleAccount(index("a@b.c", null))).isTrue();
    // Source flag not set explicitly -- for new accounts with null issuer, this implies they are
    // not Google accounts.
    accountDb.add("test@gmail.com", SECRET, OtpType.TOTP, null, null, null);
    assertThat(accountDb.isGoogleAccount(index("test@gmail.com", null))).isFalse();

    accountDb.rename(index("a@b.c", null), "b@a.c");
    accountDb.rename(index("test@gmail.com", null), "test@yahoo.com");
    assertThat(accountDb.isGoogleAccount(index("b@a.c", null))).isTrue();
    assertThat(accountDb.isGoogleAccount(index("test@yahoo.com", null))).isFalse();
  }

  @Test
  public void testConstruct_whenDatabaseLacksNewerColumns() {
    expectDbConsistent = false;
    assertThat(AccountDb.deleteDatabase(DependencyInjector.getContext())).isTrue();

    SQLiteDatabase database =
        DependencyInjector.getContext().openOrCreateDatabase(
            FileUtilities.DATABASES_PATH, Context.MODE_PRIVATE, null);
    database.execSQL("CREATE TABLE " + AccountDb.TABLE_NAME + " (first INTEGER)");
    assertThat(AccountDb.listTableColumnNamesLowerCase(database, AccountDb.TABLE_NAME))
        .containsExactly("first");
    database.close();
    database = null;

    accountDb = new AccountDb(DependencyInjector.getContext());
    assertThat(AccountDb.listTableColumnNamesLowerCase(accountDb.mDatabase, AccountDb.TABLE_NAME))
        .containsExactly(
            "first",
            AccountDb.PROVIDER_COLUMN,
            AccountDb.ISSUER_COLUMN,
            AccountDb.ORIGINAL_NAME_COLUMN);
  }

  @Test
  public void testConstruct_whenDatabaseIsOlder() {
    assertThat(AccountDb.deleteDatabase(DependencyInjector.getContext())).isTrue();
    SQLiteDatabase database = createOlderDatabaseWithRecords();

    String[] olderColumnNames = {
        AccountDb.ID_COLUMN, AccountDb.NAME_COLUMN, AccountDb.SECRET_COLUMN,
        AccountDb.COUNTER_COLUMN, AccountDb.TYPE_COLUMN, AccountDb.PROVIDER_COLUMN
        };

    String[] allColumnNames = {
        AccountDb.ID_COLUMN, AccountDb.NAME_COLUMN, AccountDb.SECRET_COLUMN,
        AccountDb.COUNTER_COLUMN, AccountDb.TYPE_COLUMN, AccountDb.PROVIDER_COLUMN,
        AccountDb.ISSUER_COLUMN, AccountDb.ORIGINAL_NAME_COLUMN
        };

    assertThat(AccountDb.listTableColumnNamesLowerCase(database, AccountDb.TABLE_NAME))
        .containsExactly((Object[]) olderColumnNames);
    database.close();
    database = null;

   accountDb = new AccountDb(DependencyInjector.getContext());
   assertThat(AccountDb.listTableColumnNamesLowerCase(accountDb.mDatabase, AccountDb.TABLE_NAME))
       .containsExactly((Object[]) allColumnNames);
   assertThat(accountDb.getAccounts())
       .containsExactly((Object[]) autoUpgraded(TEST_ACCOUNTS_WITH_NULL_ISSUER))
       .inOrder();
  }

  private AccountIndex[] autoUpgraded(AccountIndex[] oldEntries) {
    AccountIndex[] newEntries = new AccountIndex[oldEntries.length];
    for (int i = 0; i < oldEntries.length; i++) {
      newEntries[i] = oldEntries[i];
      if (oldEntries[i].getName().startsWith("Google:")) {
        newEntries[i] = index(oldEntries[i].getName(), "Google");
      }
      if (oldEntries[i].getName().startsWith("Dropbox:")) {
        newEntries[i] = index(oldEntries[i].getName(), "Dropbox");
      }
    }
    return newEntries;
  }

  @Test
  public void testSigningOracle() throws Exception {
    Signer signer = AccountDb.getSigningOracle(SECRET);
    assertThat(Base32String.encode(signer.sign(MESSAGE.getBytes()))).isEqualTo(SIGNATURE);
  }

  private SQLiteDatabase createOlderDatabaseWithRecords() {
    SQLiteDatabase database = DependencyInjector.getContext().openOrCreateDatabase(
        FileUtilities.DATABASES_PATH, Context.MODE_PRIVATE, null);

    database.execSQL(String.format(
        "CREATE TABLE IF NOT EXISTS %s" +
        " (%s INTEGER PRIMARY KEY, %s TEXT NOT NULL, %s TEXT NOT NULL, " +
        " %s INTEGER DEFAULT %s, %s INTEGER, %s INTEGER DEFAULT %s)",
        AccountDb.TABLE_NAME, AccountDb.ID_COLUMN, AccountDb.NAME_COLUMN, AccountDb.SECRET_COLUMN,
        AccountDb.COUNTER_COLUMN, AccountDb.DEFAULT_HOTP_COUNTER,
        AccountDb.TYPE_COLUMN,
        AccountDb.PROVIDER_COLUMN, AccountDb.PROVIDER_UNKNOWN));

    for (TestAccount t : TEST_ACCOUNTS_WITH_NULL_ISSUER) {
      addToOlderDatabase(database, t.name, t.secret, t.type, t.counter, t.isGoogleAccount);
    }
    return database;
  }

  private void addToOlderDatabase(
      SQLiteDatabase database,
      String name,
      String secret,
      OtpType type,
      Integer counter,
      Boolean googleAccount) {
    ContentValues values = new ContentValues();
    values.put(AccountDb.SECRET_COLUMN, secret);
    values.put(AccountDb.TYPE_COLUMN, type.ordinal());
    values.put(AccountDb.COUNTER_COLUMN, counter);
    if (googleAccount != null) {
      values.put(
          AccountDb.PROVIDER_COLUMN,
          (googleAccount.booleanValue()) ? AccountDb.PROVIDER_GOOGLE : AccountDb.PROVIDER_UNKNOWN);
    }
    values.put(AccountDb.NAME_COLUMN, name);
    database.insert(AccountDb.TABLE_NAME, null, values);
  }

  private void addSomeRecordsWithNullIssuer() {
    for (TestAccount t : TEST_ACCOUNTS_WITH_NULL_ISSUER) {
      addTestAccount(t);
    }
  }

  private void addSomeRecordsWithIssuer() {
    for (TestAccount t : TEST_ACCOUNTS_WITH_ISSUER) {
      addTestAccount(t);
    }
  }

  private void addAllTestRecords() {
    addSomeRecordsWithIssuer();
    addSomeRecordsWithNullIssuer();
  }

  private void addTestAccount(TestAccount t) {
    accountDb.add(t.name, t.secret, t.type, t.counter, t.isGoogleAccount, t.issuer);
  }

  private TestAccount findTestAccountByName(TestAccount[] testAccounts, String name) {
    for (TestAccount t : testAccounts) {
      if (t.name.equalsIgnoreCase(name)) {
        return t;
      }
    }
    fail("Couldn't find a test account for name: " + name);
    return null;
  }

  private static AccountIndex index(String name, String issuer) {
    return new AccountIndex(name, issuer);
  }
}
