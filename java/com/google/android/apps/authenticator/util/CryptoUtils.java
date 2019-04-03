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

import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

/** Utilities for cryptographic operations. */
public class CryptoUtils {
  
  public static final String DIGEST_SHA_512 = "SHA-512";
  public static final String DIGEST_SHA_256 = "SHA-256";
  public static final String HMAC_SHA_256 = "HmacSHA256";
  
  /** Hidden constructor to prevent instantiation. */
  private CryptoUtils() {}
  
  public static byte[] digest(String algorithm, byte[] data) {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance(algorithm);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException("Unsupported digest algorithm: " + algorithm, e);
    }
    return digest.digest(data);
  }
  
  public static byte[] generateMac(String algorithm, SecretKey key, byte[] data) {
    Mac mac;
    try {
      mac = Mac.getInstance(algorithm);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException("Unsupported MAC algorithm: " + algorithm, e);
    }
    try {
      mac.init(key);
    } catch (InvalidKeyException e) {
      throw new IllegalArgumentException("Invalid MAC key", e);
    }
    return mac.doFinal(data);
  }
  
  public static boolean verifyMac(String algorithm, SecretKey key, byte[] data, byte[] mac) {
    return constantTimeArrayEquals(generateMac(algorithm, key, data), mac);
  }
  
  /**
   * Returns {@code true} if the two arrays are equal to one another.
   * When the two arrays differ in length, trivially returns {@code false}.
   * When the two arrays are equal in length, does a constant-time comparison
   * of the two, i.e. does not abort the comparison when the first differing
   * element is found.
   * 
   * <p>NOTE: This is a copy of {@code java/com/google/math/crypto/ConstantTime#arrayEquals}.
   *
   * @param a An array to compare
   * @param b Another array to compare
   * @return {@code true} if these arrays are both null or if they have equal
   *         length and equal bytes in all elements
   */
  public static boolean constantTimeArrayEquals(byte[] a, byte[] b) {
    if (a == null || b == null) {
      return (a == b);
    }
    if (a.length != b.length) {
      return false;
    }
    byte result = 0;
    for (int i = 0; i < b.length; i++) {
      result = (byte) (result | a[i] ^ b[i]);
    }
    return (result == 0);
  }
}
