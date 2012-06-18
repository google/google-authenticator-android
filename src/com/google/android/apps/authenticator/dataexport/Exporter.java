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

import com.google.android.apps.authenticator.AccountDb;
import com.google.android.apps.authenticator.Preconditions;

import android.content.SharedPreferences;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Exports the contents of {@link AccountDb}, and the key material and settings into a
 * {@link Bundle}.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class Exporter {

  private final AccountDb mAccountDb;
  private final SharedPreferences mPreferences;

  /**
   * Constructs a new {@code Exporter}.
   *
   * @param accountDb {@link AccountDb} whose data is to be exported.
   * @param preferences preferences to be exported or {@code null} for none.
   */
  public Exporter(
      AccountDb accountDb,
      SharedPreferences preferences) {
    mAccountDb = Preconditions.checkNotNull(accountDb);
    mPreferences = preferences;
  }

  public Bundle getData() {
    Bundle result = new Bundle();
    result.putBundle("accountDb", getAccountDbBundle(mAccountDb));
    if (mPreferences != null) {
      result.putBundle("preferences", getPreferencesBundle(mPreferences));
    }
    return result;
  }

  private static Bundle getAccountDbBundle(AccountDb accountDb) {
    List<String> accountNames = new ArrayList<String>();
    accountDb.getNames(accountNames);

    Bundle result = new Bundle();
    int accountPosition = 0;
    for (String accountName : accountNames) {
      accountPosition++;
      Bundle account = new Bundle();
      account.putString("name", accountName);
      account.putString("encodedSecret", accountDb.getSecret(accountName));
      account.putInt("counter", accountDb.getCounter(accountName));
      AccountDb.OtpType accountType = accountDb.getType(accountName);
      String serializedAccountType;
      switch (accountType) {
        case HOTP:
          serializedAccountType = "hotp";
          break;
        case TOTP:
          serializedAccountType = "totp";
          break;
        default:
          throw new RuntimeException("Unsupported account type: " + accountType);
      }
      account.putString("type", serializedAccountType);
      result.putBundle(String.valueOf(accountPosition), account);
    }
    return result;
  }

  private Bundle getPreferencesBundle(SharedPreferences preferences) {
    Map<String, ?> preferencesMap = preferences.getAll();
    if (preferencesMap == null) {
      preferencesMap = Collections.emptyMap();
    }
    Bundle result = new Bundle();
    for (String key : preferencesMap.keySet()) {
      Object value = preferencesMap.get(key);
      if (value instanceof Boolean) {
        result.putBoolean(key, (Boolean) value);
      } else if (value instanceof Float) {
        result.putFloat(key, (Float) value);
      } else if (value instanceof Integer) {
        result.putInt(key, (Integer) value);
      } else if (value instanceof Long) {
        result.putLong(key, (Long) value);
      } else if (value instanceof String) {
        result.putString(key, (String) value);
      } else {
        // Can only be Set<String> at the moment (API Level 11+), which we don't use anyway.
        // Ignore this type of preference, since losing preferences on export is not lethal
      }
    }
    return result;
  }
}
