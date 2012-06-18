/*
 * Copyright 2012 Google Inc. All Rights Reserved.
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

import android.test.MoreAsserts;

import junit.framework.TestCase;

/**
 * Unit tests for {@link HexEncoding}.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class HexEncodingTest extends TestCase {

  public void testEncodeNull() {
    try {
      HexEncoding.encode(null);
      fail();
    } catch (NullPointerException expected) {}
  }

  public void testEncodeEmpty() {
    assertEquals("", HexEncoding.encode(new byte[0]));
  }

  public void testEncodeAllDigits() {
    assertEquals("0123456789abcdef", HexEncoding.encode(
        new byte[] {0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xab, (byte) 0xcd, (byte) 0xef}));
  }

  public void testDecodeNull() {
    try {
      HexEncoding.decode(null);
      fail();
    } catch (NullPointerException expected) {}
  }

  public void testDecodeEmpty() {
    MoreAsserts.assertEquals(new byte[0], HexEncoding.decode(""));
  }

  public void testDecodeAllDigits() {
    MoreAsserts.assertEquals(
        new byte[] {0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xab, (byte) 0xcd, (byte) 0xef},
        HexEncoding.decode("0123456789abcdef"));
  }

  public void testDecodeOddNumberOfDigits() {
    MoreAsserts.assertEquals(
        new byte[] {0x0f, 0x23, 0x45},
        HexEncoding.decode("f2345"));
  }

  public void testDecodeOneDigit() {
    MoreAsserts.assertEquals(
        new byte[] {0x03},
        HexEncoding.decode("3"));
  }

  public void testDecode_withSpaces() {
    try {
      HexEncoding.decode("01 23");
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  public void testDecode_withUpperCaseDigits() {
    MoreAsserts.assertEquals(
        new byte[] {(byte) 0xab, (byte) 0xcd, (byte) 0xef},
        HexEncoding.decode("ABCDEF"));
  }
}
