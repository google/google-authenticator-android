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

package com.google.android.apps.authenticator;

import com.google.android.apps.authenticator.Base32String.DecodingException;

import android.test.MoreAsserts;

import java.io.UnsupportedEncodingException;
import junit.framework.TestCase;

/**
 * Unit test for {@link Base32String}
 * @author sarvar@google.com (Sarvar Patel)
 */
public class Base32StringTest extends TestCase {

  // regression input and output values taken from RFC 4648
  // but stripped of the "=" padding from encoded output as required by the
  // implemented encoding in Base32String.java
  private static final byte[] INPUT1 = string2Bytes("foo");
  private static final byte[] INPUT2 = string2Bytes("foob");
  private static final byte[] INPUT3 = string2Bytes("fooba");
  private static final byte[] INPUT4 = string2Bytes("foobar");

  // RFC 4648 expected encodings for above inputs are:
  // "MZXW6===", "MZXW6YQ=", "MZXW6YTB", MZXW6YTBOI======".
  // Base32String encoding, however, drops the "=" padding.
  private static final String OUTPUT1 = "MZXW6";
  private static final String OUTPUT2 = "MZXW6YQ";
  private static final String OUTPUT3 = "MZXW6YTB";
  private static final String OUTPUT4 = "MZXW6YTBOI";


  private static byte[] string2Bytes(String s) {
    try {
      return s.getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("UTF-8 encoding is unsupported");
    }
  }

  public void testRegressionValuesFromRfc4648() throws DecodingException {
    // check encoding
    assertEquals(OUTPUT1, Base32String.encode(INPUT1));
    assertEquals(OUTPUT2, Base32String.encode(INPUT2));
    assertEquals(OUTPUT3, Base32String.encode(INPUT3));
    assertEquals(OUTPUT4, Base32String.encode(INPUT4));

    // check decoding
    MoreAsserts.assertEquals(INPUT1, Base32String.decode(OUTPUT1));
    MoreAsserts.assertEquals(INPUT2, Base32String.decode(OUTPUT2));
    MoreAsserts.assertEquals(INPUT3, Base32String.decode(OUTPUT3));
    MoreAsserts.assertEquals(INPUT4, Base32String.decode(OUTPUT4));
  }

  /**
   * Base32String implementation is not the same as that of RFC 4648, it drops
   * the last incomplete chunk and thus accepts encoded strings that should have
   * been rejected; also this results in multiple encoded strings being decoded
   * to the same byte array.
   * This test will catch any changes made regarding this behavior.
   */
  public void testAmbiguousDecoding() throws DecodingException {
    byte[] b16 = Base32String.decode("7777777777777777"); // 16 7s.
    byte[] b17 = Base32String.decode("77777777777777777"); // 17 7s.
    MoreAsserts.assertEquals(b16, b17);
  }

  // returns true if decoded, else false.
  private byte[] checkDecoding(String s) {
    try {
      return Base32String.decode(s);
    } catch (DecodingException e) {
      return null; // decoding failed.
    }
  }

  public void testSmallDecodingsAndFailures() {
    // decoded, but not enough to return any bytes.
    assertEquals(0, checkDecoding("A").length);
    assertEquals(0, checkDecoding("").length);
    assertEquals(0, checkDecoding(" ").length);

    // decoded successfully and returned 1 byte.
    assertEquals(1, checkDecoding("AA").length);
    assertEquals(1, checkDecoding("AAA").length);

    // decoded successfully and returned 2 bytes.
    assertEquals(2, checkDecoding("AAAA").length);

    // acceptable separators " " and "-" which should be ignored
    assertEquals(2, checkDecoding("AA-AA").length);
    assertEquals(2, checkDecoding("AA-AA").length);
    MoreAsserts.assertEquals(checkDecoding("AA-AA"), checkDecoding("AA AA"));
    MoreAsserts.assertEquals(checkDecoding("AAAA"), checkDecoding("AA AA"));

    // 1, 8, 9, 0 are not a valid character, decoding should fail
    assertNull(checkDecoding("11"));
    assertNull(checkDecoding("A1"));
    assertNull(checkDecoding("AAA8"));
    assertNull(checkDecoding("AAA9"));
    assertNull(checkDecoding("AAA0"));

    // non-alphanumerics (except =) are not valid characters and decoding should fail
    assertNull(checkDecoding("AAA,"));
    assertNull(checkDecoding("AAA;"));
    assertNull(checkDecoding("AAA."));
    assertNull(checkDecoding("AAA!"));

    // this just documents that a null string causes a nullpointerexception.
    try {
      checkDecoding(null);
      fail();
    } catch (NullPointerException e) {
      // expected.
    }
  }
}
