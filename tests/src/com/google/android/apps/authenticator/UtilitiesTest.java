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

import com.google.android.apps.authenticator.testability.accounts.AccountManager;

import android.accounts.Account;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.test.MoreAsserts;

import junit.framework.TestCase;

/**
 * Unit tests for {@link Utilities}.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class UtilitiesTest extends TestCase {

  public void testMillisToSeconds() {
    // Test rounding
    assertEquals(1234, Utilities.millisToSeconds(1234567));
    assertEquals(1234, Utilities.millisToSeconds(1234000));
    assertEquals(1234, Utilities.millisToSeconds(1234999));

    // Test that it works fine for longs
    assertEquals(12345678901L, Utilities.millisToSeconds(12345678901234L));
  }

  public void testSecondsToMillis() {
    assertEquals(1234000, Utilities.secondsToMillis(1234));

    // Test that it works fine for longs
    assertEquals(12345678901000L, Utilities.secondsToMillis(12345678901L));
  }

  public void testListGoogleAccounts_withNullAccountManager() {
    MoreAsserts.assertEquals(new Account[0], Utilities.listGoogleAccounts(null));
  }

  public void testListGoogleAccounts() {
    FakeAccountManager accountManager = new FakeAccountManager();
    accountManager.getAccountsByTypeResponse = new Account[3];

    Account[] actualAccounts = Utilities.listGoogleAccounts(accountManager);

    MoreAsserts.assertEquals(accountManager.getAccountsByTypeResponse, actualAccounts);
    assertEquals("com.google", accountManager.capturedGetAccountsByTypeType);
  }

  private static class FakeAccountManager implements AccountManager {

    private String capturedGetAccountsByTypeType;
    private Account[] getAccountsByTypeResponse;

    @Override
    public Account[] getAccountsByType(String type) {
      capturedGetAccountsByTypeType = type;
      return getAccountsByTypeResponse;
    }

    @Override
    public AccountManagerFuture<Bundle> getAuthToken(Account account, String authTokenType,
        Bundle options, Activity activity, AccountManagerCallback<Bundle> callback,
        Handler handler) {
      throw new RuntimeException("Should not have been invoked");
    }

    @Override
    public AccountManagerFuture<Bundle> updateCredentials(Account account, String authTokenType,
        Bundle options, Activity activity, AccountManagerCallback<Bundle> callback,
        Handler handler) {
      throw new RuntimeException("Should not have been invoked");
    }

    @Override
    public void invalidateAuthToken(String accountType, String authToken) {
      throw new RuntimeException("Should not have been invoked");
    }
  }
}
