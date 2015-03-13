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

import java.util.Collection;

/**
 * Abstraction for collection of OTP tokens.
 *
 * @author cemp@google.com (Cem Paya)
 */
public interface OtpSource {

  /**
   * Enumerate list of accounts that this OTP token supports.
   *
   * @param result Collection to append usernames. This object is NOT cleared on
   *               entry; if there are existing items, they will not be removed.
   * @return Number of accounts added to the collection.
   */
  int enumerateAccounts(Collection<String> result);

  /**
   * Return the next OTP code for specified username.
   * Invoking this function may change internal state of the OTP generator,
   * for example advancing the counter.
   *
   * @param accountName Username, email address or other unique identifier for the account.
   * @return OTP as string code.
   */
  String getNextCode(String accountName) throws OtpSourceException;

  /**
   * Generate response to a given challenge based on next OTP code.
   * Subclasses are not required to implement this method.
   *
   * @param accountName Username, email address or other unique identifier for the account.
   * @param challenge Server specified challenge as UTF8 string.
   * @return Response to the challenge.
   * @throws UnsupportedOperationException if the token does not support
   *         challenge-response extension for this account.
   */
  String respondToChallenge(String accountName, String challenge) throws OtpSourceException;

  /**
   * Gets the counter for generating or verifying TOTP codes.
   */
  TotpCounter getTotpCounter();

  /**
   * Gets the clock for generating or verifying TOTP codes.
   */
  TotpClock getTotpClock();
}
