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
import static org.mockito.Mockito.doReturn;
import static org.mockito.MockitoAnnotations.initMocks;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.apps.authenticator.otp.AccountDb.AccountIndex;
import com.google.android.apps.authenticator.otp.AccountDb.OtpType;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator.util.Utilities;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

/** Unit tests for {@link OtpProvider}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class OtpProviderTest {

  private static final AccountIndex ACCOUNT1 = new AccountIndex("johndoe@gmail.com", null);
  private static final AccountIndex ACCOUNT2 =
      new AccountIndex("amywinehouse@aol.com", AccountDb.GOOGLE_ISSUER_NAME);
  private static final AccountIndex ACCOUNT3 = new AccountIndex("maryweiss@yahoo.com", "Yahoo");
  private static final AccountIndex ACCOUNT4 = new AccountIndex("maryweiss@yahoo.com", null);
  private static final AccountIndex GOOGLE_CORP_INDEX =
      new AccountIndex(AccountDb.GOOGLE_CORP_ACCOUNT_NAME, null);

  private static final AccountIndex[] TEST_ACCOUNTS = {
    ACCOUNT1,
    ACCOUNT2,
    ACCOUNT3,
    ACCOUNT4,
  };

  private static final String SECRET = "7777777777777777"; // 16 sevens
  private static final String SECRET2 = "2222222222222222"; // 16 twos

  private OtpProvider otpProvider;
  private AccountDb accountDb;
  @Mock private TotpClock mockTotpClock;

  @Before
  public void setUp() throws Exception {
    DependencyInjector.resetForIntegrationTesting(
        InstrumentationRegistry.getInstrumentation().getTargetContext());
    initMocks(this);

    accountDb = DependencyInjector.getAccountDb();
    otpProvider = new OtpProvider(accountDb, mockTotpClock);
  }

  @After
  public void tearDown() throws Exception {
    DependencyInjector.close();
  }

  private void addSomeRecords() {
    accountDb.add("johndoe@gmail.com", SECRET, OtpType.TOTP, null, null, null);
    accountDb.add("amywinehouse@aol.com", SECRET2, OtpType.TOTP, null, null,
        AccountDb.GOOGLE_ISSUER_NAME);
    accountDb.add("maryweiss@yahoo.com", SECRET, OtpType.HOTP, 0, null, "Yahoo");
    accountDb.add("maryweiss@yahoo.com", SECRET, OtpType.HOTP, 0, null, null);
  }

  @Test
  public void testEnumerateAccountsNoRecords() {
    assertThat(otpProvider.enumerateAccounts()).isEmpty();
  }

  @Test
  public void testEnumerateAccounts() {
    addSomeRecords();
    assertThat(otpProvider.enumerateAccounts()).containsExactly((Object[]) TEST_ACCOUNTS);
  }

  @Test
  public void testGetNextCode() throws Exception {
    addSomeRecords();
    // HOTP, counter at 0, check getNextcode response.
    assertThat(otpProvider.getNextCode(ACCOUNT3)).isEqualTo("683298");
    // counter updated to 1, check response has changed.
    assertThat(otpProvider.getNextCode(ACCOUNT3)).isEqualTo("891123");
    // Uses the same seed as the previous account, but counter should not have been advanced
    assertThat(otpProvider.getNextCode(ACCOUNT4)).isEqualTo("683298");

    // TOTP: HOTP with current time (seconds / 30) as the counter
    withTotpClockCurrentTimeSeconds(OtpProvider.DEFAULT_INTERVAL * 1);
    assertThat(otpProvider.getNextCode(ACCOUNT1)).isEqualTo("683298");
    withTotpClockCurrentTimeSeconds(OtpProvider.DEFAULT_INTERVAL * 2);
    assertThat(otpProvider.getNextCode(ACCOUNT1)).isEqualTo("891123");

    // Different TOTP account/secret
    withTotpClockCurrentTimeSeconds(OtpProvider.DEFAULT_INTERVAL * 1234567890L);
    assertThat(otpProvider.getNextCode(ACCOUNT2)).isEqualTo("817746");
  }

  @Test
  public void testGetNextCodeWithEmptyAccountName() throws Exception {
    accountDb.add("", SECRET, OtpType.HOTP, null, null, null);
    // HOTP, counter at 0, check getNextcode response.
    assertThat(otpProvider.getNextCode(new AccountIndex("", null))).isEqualTo("683298");
  }

  @Test
  public void testRespondToChallengeWithNullChallenge() throws Exception {
    addSomeRecords();
    assertThat(otpProvider.respondToChallenge(ACCOUNT3, null)).isEqualTo("683298");
  }

  @Test
  public void testRespondToChallenge() throws Exception {
    addSomeRecords();
    assertThat(otpProvider.respondToChallenge(ACCOUNT3, "")).isEqualTo("308683298");
    assertThat(otpProvider.respondToChallenge(ACCOUNT3, "this is my challenge"))
        .isEqualTo("561472261");

    // TOTP: HOTP with current time (seconds / 30) as the counter
    withTotpClockCurrentTimeSeconds(OtpProvider.DEFAULT_INTERVAL * 1);
    assertThat(otpProvider.respondToChallenge(ACCOUNT1, "")).isEqualTo("308683298");
    withTotpClockCurrentTimeSeconds(OtpProvider.DEFAULT_INTERVAL * 2);
    assertThat(otpProvider.respondToChallenge(ACCOUNT1, "this is my challenge"))
        .isEqualTo("561472261");

    // Different TOTP account/secret
    withTotpClockCurrentTimeSeconds(OtpProvider.DEFAULT_INTERVAL * 9876543210L);
    assertThat(otpProvider.respondToChallenge(ACCOUNT2, "this is my challenge"))
        .isEqualTo("834647199");
  }

  @Test
  public void testGetNextCodeCorpAccount() throws Exception {
    String username = AccountDb.GOOGLE_CORP_ACCOUNT_NAME;
    accountDb.add(username, SECRET, OtpType.HOTP, null, null, null);
    // HOTP, counter at 0, check getNextcode response.
    assertThat(otpProvider.getNextCode(GOOGLE_CORP_INDEX)).isEqualTo("683298");
  }

  @Test
  public void testGetNextCodeFromNonCorpAccount() throws Exception {
    String username = "test@gmail.com";
    AccountIndex index = new AccountIndex(username, null);
    accountDb.add(username, SECRET, OtpType.HOTP, null, null, null);
    // HOTP, counter at 0, check getNextcode response.
    assertThat(otpProvider.getNextCode(index)).isEqualTo("683298");

    // HOTP, counter at 1, check getNextcode response.
    assertThat(otpProvider.getNextCode(index)).isEqualTo("891123");
  }


  private void withTotpClockCurrentTimeSeconds(long timeSeconds) {
    doReturn(Utilities.secondsToMillis(timeSeconds)).when(mockTotpClock).nowMillis();
  }
}
