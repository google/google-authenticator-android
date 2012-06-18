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

package com.google.android.apps.authenticator.testability.accounts;

import android.accounts.Account;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;

/**
 * Centralized registry of the user's online accounts. Offers a subset of the API of
 * {@link android.accounts.AccountManager}.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public interface AccountManager {

  /** @see android.accounts.AccountManager#KEY_AUTHTOKEN */
  String KEY_AUTHTOKEN = android.accounts.AccountManager.KEY_AUTHTOKEN;

  /** @see android.accounts.AccountManager#KEY_ACCOUNT_NAME */
  String KEY_ACCOUNT_NAME = android.accounts.AccountManager.KEY_ACCOUNT_NAME;

  /** @see android.accounts.AccountManager#KEY_INTENT */
  String KEY_INTENT = android.accounts.AccountManager.KEY_INTENT;

  /** @see android.accounts.AccountManager#getAccountsByType(String) */
  Account[] getAccountsByType(String type);

  /** @see android.accounts.AccountManager#getAuthToken(Account, String, Bundle, Activity, AccountManagerCallback, Handler) */
  AccountManagerFuture<Bundle> getAuthToken(
      Account account,
      String authTokenType,
      Bundle options,
      Activity activity,
      AccountManagerCallback<Bundle> callback,
      Handler handler);

  /** @see android.accounts.AccountManager#updateCredentials(Account, String, Bundle, Activity, AccountManagerCallback, Handler) */
  AccountManagerFuture<Bundle> updateCredentials(
      Account account,
      String authTokenType,
      Bundle options,
      Activity activity,
      AccountManagerCallback<Bundle> callback,
      Handler handler);

  /** @see android.accounts.AccountManager#invalidateAuthToken(String, String) */
  void invalidateAuthToken(String accountType, String authToken);
}
