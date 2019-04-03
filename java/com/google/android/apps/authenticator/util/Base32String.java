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

package com.google.android.apps.authenticator.util;

import com.google.common.collect.Maps;

import java.util.Locale;
import java.util.Map;

/**
 * Encodes arbitrary byte arrays as case-insensitive base-32 strings.
 *
 * <p> The implementation is slightly different than in RFC 4648. During encoding, padding is not
 * added, and during decoding the last incomplete chunk is not taken into account. The result is
 * that multiple strings decode to the same byte array, for example, string of sixteen 7s ("7...7")
 * and seventeen 7s both decode to the same byte array.
 *
 * <p>TODO: Revisit this encoding and whether this ambiguity needs fixing.
 */
public class Base32String {
  private static final String SEPARATOR = "-";
  private static final char[] DIGITS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
  private static final int MASK = DIGITS.length - 1;
  private static final int SHIFT = Integer.numberOfTrailingZeros(DIGITS.length);
  private static final Map<Character, Integer> CHAR_MAP =
      Maps.newHashMapWithExpectedSize(DIGITS.length);

  static {
    for (int i = 0; i < DIGITS.length; i++) {
      CHAR_MAP.put(DIGITS[i], i);
    }
  }

  public static byte[] decode(String encoded) throws DecodingException {
    // Remove whitespace and separators
    encoded = encoded.trim().replaceAll(SEPARATOR, "").replaceAll(" ", "");

    // Remove padding. Note: the padding is used as hint to determine how many
    // bits to decode from the last incomplete chunk (which is commented out
    // below, so this may have been wrong to start with).
    encoded = encoded.replaceFirst("[=]*$", "");

    // Canonicalize to all upper case
    encoded = encoded.toUpperCase(Locale.US);
    if (encoded.length() == 0) {
      return new byte[0];
    }
    int encodedLength = encoded.length();
    int outLength = encodedLength * SHIFT / 8;
    byte[] result = new byte[outLength];
    int buffer = 0;
    int next = 0;
    int bitsLeft = 0;
    for (char c : encoded.toCharArray()) {
      if (!CHAR_MAP.containsKey(c)) {
        throw new DecodingException("Illegal character: " + c);
      }
      buffer <<= SHIFT;
      buffer |= CHAR_MAP.get(c) & MASK;
      bitsLeft += SHIFT;
      if (bitsLeft >= 8) {
        result[next++] = (byte) (buffer >> (bitsLeft - 8));
        bitsLeft -= 8;
      }
    }
    // We'll ignore leftover bits for now.
    //
    // if (next != outLength || bitsLeft >= SHIFT) {
    //  throw new DecodingException("Bits left: " + bitsLeft);
    // }
    return result;
  }

  public static String encode(byte[] data) {
    int dataLength = data.length;
    if (dataLength == 0) {
      return "";
    }

    // SHIFT is the number of bits per output character, so the length of the
    // output is the length of the input multiplied by 8/SHIFT, rounded up.
    if (dataLength >= (1 << 28)) {
      // The computation below will fail, so don't do it.
      throw new IllegalArgumentException();
    }

    int outputLength = (dataLength * 8 + SHIFT - 1) / SHIFT;
    StringBuilder result = new StringBuilder(outputLength);

    int buffer = data[0];
    int next = 1;
    int bitsLeft = 8;
    while (bitsLeft > 0 || next < dataLength) {
      if (bitsLeft < SHIFT) {
        if (next < dataLength) {
          buffer <<= 8;
          buffer |= (data[next++] & 0xff);
          bitsLeft += 8;
        } else {
          int pad = SHIFT - bitsLeft;
          buffer <<= pad;
          bitsLeft += pad;
        }
      }
      int index = MASK & (buffer >> (bitsLeft - SHIFT));
      bitsLeft -= SHIFT;
      result.append(DIGITS[index]);
    }
    return result.toString();
  }

  /** Exception thrown when decoding fails */
  public static class DecodingException extends Exception {
    public DecodingException(String message) {
      super(message);
    }
  }
}
