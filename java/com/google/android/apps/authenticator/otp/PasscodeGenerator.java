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

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;

/**
 * An implementation of the HOTP generator specified by RFC 4226.
 *
 * <p>Generates short passcodes that may be used in challenge-response protocols or as timeout
 * passcodes that are only valid for a short period.
 *
 * <p>The default passcode is a 6-digit decimal code. The maximum passcode length is 9 digits.
 */
public class PasscodeGenerator {
  /**
   * Maximum passcode length, in digits. Must be kept in sync with
   * {@link #DIGITS_POWER}.
   */
  private static final int MAX_PASSCODE_LENGTH = 9;

  /** Default decimal passcode length */
  private static final int PASS_CODE_LENGTH = 6;

  /** The number of previous and future intervals to check */
  private static final int ADJACENT_INTERVALS = 1;

  /**
   * Powers of 10 to shorten the pin to the desired number of digits. This
   * prevents invalid OTP generation when Math.pow() is implemented incorrectly
   * (e.g. when 10^6 != 1000000), and matches the reference implementation in
   * RFC 6238. Must be kept in sync with {@link #MAX_PASSCODE_LENGTH}.
   */
  private static final int[] DIGITS_POWER =
      {1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000, 1000000000};

  private final Signer signer;
  private final int codeLength;

  /**
   * Using an interface to allow us to inject different signature
   * implementations.
   */
  interface Signer {
    /**
     * @param data Preimage to sign, represented as sequence of arbitrary bytes
     * @return Signature as sequence of bytes.
     * @throws GeneralSecurityException
     */
    byte[] sign(byte[] data) throws GeneralSecurityException;
  }

  /**
   * @param mac A {@link Mac} used to generate passcodes
   */
  public PasscodeGenerator(Mac mac) {
    this(mac, PASS_CODE_LENGTH);
  }

  public PasscodeGenerator(Signer signer) {
    this(signer, PASS_CODE_LENGTH);
  }

  /**
   * @param mac A {@link Mac} used to generate passcodes
   * @param passCodeLength The length of the decimal passcode
   */
  public PasscodeGenerator(final Mac mac, int passCodeLength) {
    this(new Signer() {
      @Override
      public byte[] sign(byte[] data){
        return mac.doFinal(data);
      }
    }, passCodeLength);
  }

  public PasscodeGenerator(Signer signer, int passCodeLength) {
    if ((passCodeLength < 0) || (passCodeLength > MAX_PASSCODE_LENGTH)) {
      throw new IllegalArgumentException(
        "PassCodeLength must be between 1 and " + MAX_PASSCODE_LENGTH + " digits.");
    }
    this.signer = signer;
    this.codeLength = passCodeLength;
  }

  private String padOutput(int value) {
    String result = Integer.toString(value);
    for (int i = result.length(); i < codeLength; i++) {
      result = "0" + result;
    }
    return result;
  }

  /**
   * @param state 8-byte integer value representing internal OTP state.
   * @return A decimal response code
   * @throws GeneralSecurityException If a JCE exception occur
   */
  public String generateResponseCode(long state)
      throws GeneralSecurityException {
    byte[] value = ByteBuffer.allocate(8).putLong(state).array();
    return generateResponseCode(value);
  }


  /**
   * @param state 8-byte integer value representing internal OTP state.
   * @param challenge Optional challenge as array of bytes.
   * @return A decimal response code
   * @throws GeneralSecurityException If a JCE exception occur
   */
  public String generateResponseCode(long state, byte[] challenge)
      throws GeneralSecurityException {
    if (challenge == null) {
      return generateResponseCode(state);
    } else {
      // Allocate space for combination and store.
      byte[] value =
          ByteBuffer.allocate(8 + challenge.length)
              .putLong(state) // Write out OTP state
              .put(challenge, 0, challenge.length) // Concatenate with challenge.
              .array();
      return generateResponseCode(value);
    }
  }

  /**
   * @param challenge An arbitrary byte array used as a challenge
   * @return A decimal response code
   * @throws GeneralSecurityException If a JCE exception occur
   */
  public String generateResponseCode(byte[] challenge)
      throws GeneralSecurityException {
    byte[] hash = signer.sign(challenge);

    // Dynamically truncate the hash
    // OffsetBits are the low order bits of the last byte of the hash
    int offset = hash[hash.length - 1] & 0xF;
    // Grab a positive integer value starting at the given offset.
    int truncatedHash = hashToInt(hash, offset) & 0x7FFFFFFF;
    int pinValue = truncatedHash % DIGITS_POWER[codeLength];
    return padOutput(pinValue);
  }

  /**
   * Grabs a positive integer value from the input array starting at
   * the given offset.
   * @param bytes the array of bytes
   * @param start the index into the array to start grabbing bytes
   * @return the integer constructed from the four bytes in the array
   */
  private int hashToInt(byte[] bytes, int start) {
    DataInput input = new DataInputStream(
        new ByteArrayInputStream(bytes, start, bytes.length - start));
    int val;
    try {
      val = input.readInt();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return val;
  }

  /**
   * @param challenge A challenge to check a response against
   * @param response A response to verify
   * @return True if the response is valid
   */
  public boolean verifyResponseCode(long challenge, String response)
      throws GeneralSecurityException {
    String expectedResponse = generateResponseCode(challenge, null);
    return expectedResponse.equals(response);
  }

  /**
   * Verify a timeout code. The timeout code will be valid for a time
   * determined by the interval period and the number of adjacent intervals
   * checked.
   *
   * @param timeoutCode The timeout code
   * @return True if the timeout code is valid
   */
  public boolean verifyTimeoutCode(long currentInterval, String timeoutCode)
      throws GeneralSecurityException {
    return verifyTimeoutCode(timeoutCode, currentInterval,
                             ADJACENT_INTERVALS, ADJACENT_INTERVALS);
  }

  /**
   * Verify a timeout code. The timeout code will be valid for a time
   * determined by the interval period and the number of adjacent intervals
   * checked.
   *
   * @param timeoutCode The timeout code
   * @param pastIntervals The number of past intervals to check
   * @param futureIntervals The number of future intervals to check
   * @return True if the timeout code is valid
   */
  public boolean verifyTimeoutCode(String timeoutCode,
                                   long currentInterval,
                                   int pastIntervals,
                                   int futureIntervals) throws GeneralSecurityException {
    // Ensure that look-ahead and look-back counts are not negative.
    pastIntervals = Math.max(pastIntervals, 0);
    futureIntervals = Math.max(futureIntervals, 0);

    // Try upto "pastIntervals" before current time, and upto "futureIntervals" after.
    for (int i = -pastIntervals; i <= futureIntervals; ++i) {
      String candidate = generateResponseCode(currentInterval - i, null);
      if (candidate.equals(timeoutCode)) {
        return true;
      }
    }

    return false;
  }
}
