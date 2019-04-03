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
import com.google.android.apps.authenticator.otp.AccountDb.OtpType;
import com.google.android.apps.authenticator.otp.PasscodeGenerator.Signer;
import com.google.android.apps.authenticator.util.Utilities;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Class containing implementation of HOTP/TOTP.
 *
 * <p>Generates OTP codes for one or more accounts.
 */
public class OtpProvider implements OtpSource {

  private static final int PIN_LENGTH = 6; // HOTP or TOTP
  private static final int REFLECTIVE_PIN_LENGTH = 9; // ROTP

  @Override
  public List<AccountIndex> enumerateAccounts() {
    return mAccountDb.getAccounts();
  }

  @Override
  public String getNextCode(AccountIndex account) throws OtpSourceException {
    return getCurrentCode(account, null);
  }

  // This variant is used when an additional challenge, such as URL or
  // transaction details, are included in the OTP request.
  // The additional string is appended to standard HOTP/TOTP state before
  // applying the MAC function.
  @Override
  public String respondToChallenge(AccountIndex account, String challenge)
      throws OtpSourceException {
    if (challenge == null) {
      return getCurrentCode(account, null);
    }
    try {
      byte[] challengeBytes = challenge.getBytes("UTF-8");
      return getCurrentCode(account, challengeBytes);
    } catch (UnsupportedEncodingException e) {
      return "";
    }
  }

  @Override
  public TotpCounter getTotpCounter() {
    return mTotpCounter;
  }

  @Override
  public TotpClock getTotpClock() {
    return mTotpClock;
  }

  private String getCurrentCode(AccountIndex account, byte[] challenge) throws OtpSourceException {
    // Account is required.
    if (account == null) {
      throw new OtpSourceException("No account");
    }

    OtpType type = mAccountDb.getType(account);
    String secret = getSecret(account);

    long otpState = 0;

    if (type == OtpType.TOTP) {
      // For time-based OTP, the state is derived from clock.
      otpState =
          mTotpCounter.getValueAtTime(Utilities.millisToSeconds(mTotpClock.nowMillis()));
    } else if (type == OtpType.HOTP){
      // For counter-based OTP, the state is obtained by incrementing stored counter.
      mAccountDb.incrementCounter(account);
      Integer counter = mAccountDb.getCounter(account);
      otpState = counter.longValue();
    }

    String result = computePin(secret, otpState, challenge);
    return result;
  }

  public OtpProvider(AccountDb accountDb, TotpClock totpClock) {
    this(DEFAULT_INTERVAL, accountDb, totpClock);
  }

  public OtpProvider(int interval, AccountDb accountDb, TotpClock totpClock) {
    mAccountDb = accountDb;
    mTotpCounter = new TotpCounter(interval);
    mTotpClock = totpClock;
  }

  /**
   * Computes the one-time PIN given the secret key.
   *
   * @param secret the secret key
   * @param otpState current token state (counter or time-interval)
   * @param challenge optional challenge bytes to include when computing passcode.
   * @return the PIN
   */
  private String computePin(String secret, long otpState, byte[] challenge)
      throws OtpSourceException {
    if (secret == null || secret.length() == 0) {
      throw new OtpSourceException("Null or empty secret");
    }

    try {
      Signer signer = AccountDb.getSigningOracle(secret);
      PasscodeGenerator pcg = new PasscodeGenerator(signer,
        (challenge == null) ? PIN_LENGTH : REFLECTIVE_PIN_LENGTH);

      return (challenge == null) ?
             pcg.generateResponseCode(otpState) :
             pcg.generateResponseCode(otpState, challenge);
    } catch (GeneralSecurityException e) {
      throw new OtpSourceException("Crypto failure", e);
    }
  }

  /**
   * Reads the secret key that was saved on the phone.
   * @param index {@link AccountIndex} identifying the user.
   * @return the secret key as base32 encoded string.
   */
  String getSecret(AccountIndex index) {
    return mAccountDb.getSecret(index);
  }

  /** Default passcode timeout period (in seconds) */
  public static final int DEFAULT_INTERVAL = 30;

  private final AccountDb mAccountDb;

  /** Counter for time-based OTPs (TOTP). */
  private final TotpCounter mTotpCounter;

  /** Clock input for time-based OTPs (TOTP). */
  private final TotpClock mTotpClock;
}
