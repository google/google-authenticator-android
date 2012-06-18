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
 * {@link AccountManager} implementation that delegates all invocations to the respective methods
 * of {@link android.accounts.AccountManager}.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class AndroidAccountManager implements AccountManager {

  private final android.accounts.AccountManager mDelegate;

  private AndroidAccountManager(android.accounts.AccountManager delegate) {
    mDelegate = delegate;
  }

  public static AndroidAccountManager wrap(android.accounts.AccountManager delegate) {
    return (delegate != null) ? new AndroidAccountManager(delegate) : null;
  }

  @Override
  public Account[] getAccountsByType(String type) {
    return mDelegate.getAccountsByType(type);
  }

  @Override
  public AccountManagerFuture<Bundle> getAuthToken(
      Account account,
      String authTokenType,
      Bundle options, Activity activity,
      AccountManagerCallback<Bundle> callback,
      Handler handler) {
    return mDelegate.getAuthToken(account, authTokenType, options, activity, callback, handler);
  }

  @Override
  public AccountManagerFuture<Bundle> updateCredentials(
      Account account,
      String authTokenType,
      Bundle options,
      Activity activity,
      AccountManagerCallback<Bundle> callback,
      Handler handler) {
    return mDelegate.updateCredentials(
        account, authTokenType, options, activity, callback, handler);
  }

  @Override
  public void invalidateAuthToken(String accountType, String authToken) {
    mDelegate.invalidateAuthToken(accountType, authToken);
  }
}
