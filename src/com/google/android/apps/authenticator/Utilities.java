/*
 * Copyright 2010 Google Inc. All Rights Reserved.
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
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.View;

/**
 * A class for handling a variety of utility things.  This was mostly made
 * because I needed to centralize dialog related constants. I foresee this class
 * being used for other code sharing across Activities in the future, however.
 *
 * @author alexei@czeskis.com (Alexei Czeskis)
 *
 */
public class Utilities {
  // Links
  public static final String ZXING_MARKET =
    "market://search?q=pname:com.google.zxing.client.android";
  public static final String ZXING_DIRECT =
    "https://zxing.googlecode.com/files/BarcodeScanner3.1.apk";

  // Dialog IDs
  public static final int DOWNLOAD_DIALOG = 0;
  public static final int MULTIPLE_ACCOUNTS_DIALOG = 1;
  static final int INVALID_QR_CODE = 3;
  static final int INVALID_SECRET_IN_QR_CODE = 7;

  /** Google account type used by {@link AccountManager}. */
  public static final String GOOGLE_ACCOUNT_TYPE = "com.google";


  // Constructor -- Does nothing yet
  private Utilities() { }

  public static final long millisToSeconds(long timeMillis) {
    return timeMillis / 1000;
  }

  public static final long secondsToMillis(long timeSeconds) {
    return timeSeconds * 1000;
  }

  /**
   * Lists all Google accounts registered with the provided {@link AccountManager}.
   *
   * @param accountManager account manager or {@code null} for none in which case {@code 0} accounts
   *        will be returned.
   */
  public static Account[] listGoogleAccounts(AccountManager accountManager) {
    return (accountManager != null)
        ? accountManager.getAccountsByType(GOOGLE_ACCOUNT_TYPE)
        : new Account[0];
  }

  /**
   * Finds a Google account having the provided name.
   *
   * @return account or {@code null} if no such account is registered with the
   *         {@code accountManager}.
   */
  public static Account findGoogleAccountByName(AccountManager accountManager, String name) {
    if (name == null) {
      return null;
    }

    for (Account account : listGoogleAccounts(accountManager)) {
      if (name.equalsIgnoreCase(account.name)) {
        return account;
      }
    }
    return null;
  }

  /**
   * Replaces all spans of type {@link URLSpan} in the provided source with clickable spans that
   * invoked the provided listener when clicked.
   */
  public static Spanned replaceEmptyUrlSpansWithClickableSpans(
      Spanned source, final View.OnClickListener onClickListener) {
    URLSpan[] sourceSpans = source.getSpans(0, source.length(), URLSpan.class);
    if ((sourceSpans == null) || (sourceSpans.length == 0)) {
      // Nothing to replace
      return source;
    }
    if (!(source instanceof Spannable)) {
      throw new RuntimeException("Source not spannable: " + source);
    }
    Spannable spannableSource = (Spannable) source;
    for (URLSpan sourceSpan : sourceSpans) {
      if (!TextUtils.isEmpty(sourceSpan.getURL())) {
        continue;
      }
      int spanStart = source.getSpanStart(sourceSpan);
      int spanEnd = source.getSpanEnd(sourceSpan);
      int spanFlags = source.getSpanFlags(sourceSpan);
      spannableSource.removeSpan(sourceSpan);
      spannableSource.setSpan(
          new ClickableSpan() {
            @Override
            public void onClick(View widget) {
              onClickListener.onClick(widget);
            }
          },
          spanStart,
          spanEnd,
          spanFlags);
    }
    return source;
  }
}
