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

import com.google.android.apps.authenticator.otp.AccountDb.AccountIndex;

import java.util.List;

/** Abstraction for collection of OTP tokens. */
public interface OtpSource {

  /**
   * Enumerate list of accounts that this OTP token supports.
   */
  List<AccountIndex> enumerateAccounts();

  /**
   * Return the next OTP code for specified account.
   * Invoking this function may change internal state of the OTP generator,
   * for example advancing the counter.
   *
   * @return OTP as string code.
   */
  String getNextCode(AccountIndex account) throws OtpSourceException;

  /**
   * Generate response to a given challenge based on next OTP code.
   * Subclasses are not required to implement this method.
   *
   * @param account the unique index for the account.
   * @param challenge Server specified challenge as UTF8 string.
   * @return Response to the challenge.
   * @throws UnsupportedOperationException if the token does not support
   *         challenge-response extension for this account.
   */
  String respondToChallenge(AccountIndex account, String challenge) throws OtpSourceException;

  /**
   * Gets the counter for generating or verifying TOTP codes.
   */
  TotpCounter getTotpCounter();

  /**
   * Gets the clock for generating or verifying TOTP codes.
   */
  TotpClock getTotpClock();
}
