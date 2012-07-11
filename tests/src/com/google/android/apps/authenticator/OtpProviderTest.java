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

import static org.mockito.Mockito.doReturn;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.android.apps.authenticator.AccountDb.OtpType;
import com.google.android.apps.authenticator.testability.DependencyInjector;

import android.test.AndroidTestCase;
import android.test.MoreAsserts;

import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Unit tests for {@link OtpProvider}.
 * @author sarvar@google.com (Sarvar Patel)
 *
 */
public class OtpProviderTest extends  AndroidTestCase {
  private static final String SECRET = "7777777777777777"; // 16 sevens
  private static final String SECRET2 = "2222222222222222"; // 16 twos

  private Collection<String> result = new ArrayList<String>();
  private OtpProvider otpProvider;
  private AccountDb accountDb;
  @Mock private TotpClock mockTotpClock;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    DependencyInjector.resetForIntegrationTesting(getContext());
    initMocks(this);
    DependencyInjector.setTotpClock(mockTotpClock);

    accountDb = DependencyInjector.getAccountDb();
    otpProvider = new OtpProvider(accountDb, mockTotpClock);
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

  public void testEnumerateAccountsNoRecords() throws Exception {
    assertEquals(0, otpProvider.enumerateAccounts(result));
    MoreAsserts.assertEmpty(result);
  }

  public void testEnumerateAccounts() throws Exception {
    addSomeRecords();
    otpProvider.enumerateAccounts(result);
    MoreAsserts.assertContentsInAnyOrder(result,
        "johndoe@gmail.com", "amywinehouse@aol.com", "maryweiss@yahoo.com");
  }

  public void testGetNextCode() throws Exception {
    addSomeRecords();
    // HOTP, counter at 0, check getNextcode response.
    assertEquals("683298", otpProvider.getNextCode("maryweiss@yahoo.com"));
    // counter updated to 1, check response has changed.
    assertEquals("891123", otpProvider.getNextCode("maryweiss@yahoo.com"));

    // TOTP: HOTP with current time (seconds / 30) as the counter
    withTotpClockCurrentTimeSeconds(OtpProvider.DEFAULT_INTERVAL * 1);
    assertEquals("683298", otpProvider.getNextCode("johndoe@gmail.com"));
    withTotpClockCurrentTimeSeconds(OtpProvider.DEFAULT_INTERVAL * 2);
    assertEquals("891123", otpProvider.getNextCode("johndoe@gmail.com"));

    // Different TOTP account/secret
    withTotpClockCurrentTimeSeconds(OtpProvider.DEFAULT_INTERVAL * 1234567890L);
    assertEquals("817746", otpProvider.getNextCode("amywinehouse@aol.com"));
  }

  public void testGetNextCodeWithEmptyAccountName() throws Exception {
    accountDb.update("", SECRET, "", OtpType.HOTP, null);
    // HOTP, counter at 0, check getNextcode response.
    assertEquals("683298", otpProvider.getNextCode(""));
  }

  public void testRespondToChallengeWithNullChallenge() throws Exception {
    addSomeRecords();
    assertEquals("683298", otpProvider.respondToChallenge("maryweiss@yahoo.com", null));
  }

  public void testRespondToChallenge() throws Exception {
    addSomeRecords();
    assertEquals("308683298", otpProvider.respondToChallenge("maryweiss@yahoo.com", ""));
    assertEquals("561472261",
        otpProvider.respondToChallenge("maryweiss@yahoo.com", "this is my challenge"));

    // TOTP: HOTP with current time (seconds / 30) as the counter
    withTotpClockCurrentTimeSeconds(OtpProvider.DEFAULT_INTERVAL * 1);
    assertEquals("308683298", otpProvider.respondToChallenge("johndoe@gmail.com", ""));
    withTotpClockCurrentTimeSeconds(OtpProvider.DEFAULT_INTERVAL * 2);
    assertEquals("561472261",
        otpProvider.respondToChallenge("johndoe@gmail.com", "this is my challenge"));

    // Different TOTP account/secret
    withTotpClockCurrentTimeSeconds(OtpProvider.DEFAULT_INTERVAL * 9876543210L);
    assertEquals("834647199",
        otpProvider.respondToChallenge("amywinehouse@aol.com", "this is my challenge"));
  }

  private void withTotpClockCurrentTimeSeconds(long timeSeconds) {
    doReturn(Utilities.secondsToMillis(timeSeconds)).when(mockTotpClock).currentTimeMillis();
  }
}
