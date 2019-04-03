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

import static com.google.common.truth.Truth.assertThat;

import com.google.android.apps.authenticator.util.Base32String.DecodingException;
import java.io.UnsupportedEncodingException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link Base32String}. */
@RunWith(JUnit4.class)
public class Base32StringTest {

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

  @Test
  public void testRegressionValuesFromRfc4648() throws DecodingException {
    // check encoding
    assertThat(Base32String.encode(INPUT1)).isEqualTo(OUTPUT1);
    assertThat(Base32String.encode(INPUT2)).isEqualTo(OUTPUT2);
    assertThat(Base32String.encode(INPUT3)).isEqualTo(OUTPUT3);
    assertThat(Base32String.encode(INPUT4)).isEqualTo(OUTPUT4);

    // check decoding
    assertThat(Base32String.decode(OUTPUT1)).isEqualTo(INPUT1);
    assertThat(Base32String.decode(OUTPUT2)).isEqualTo(INPUT2);
    assertThat(Base32String.decode(OUTPUT3)).isEqualTo(INPUT3);
    assertThat(Base32String.decode(OUTPUT4)).isEqualTo(INPUT4);
  }

  /**
   * Base32String implementation is not the same as that of RFC 4648, it drops
   * the last incomplete chunk and thus accepts encoded strings that should have
   * been rejected; also this results in multiple encoded strings being decoded
   * to the same byte array.
   * This test will catch any changes made regarding this behavior.
   */
  @Test
  public void testAmbiguousDecoding() throws DecodingException {
    byte[] b16 = Base32String.decode("7777777777777777"); // 16 7s.
    byte[] b17 = Base32String.decode("77777777777777777"); // 17 7s.
    assertThat(b17).isEqualTo(b16);
  }

  @Test
  public void testSmallDecodingsAndFailures() {
    // decoded, but not enough to return any bytes.
    assertThat(checkDecoding("A")).hasLength(0);
    assertThat(checkDecoding("")).hasLength(0);
    assertThat(checkDecoding(" ")).hasLength(0);

    // decoded successfully and returned 1 byte.
    assertThat(checkDecoding("AA")).hasLength(1);
    assertThat(checkDecoding("AAA")).hasLength(1);

    // decoded successfully and returned 2 bytes.
    assertThat(checkDecoding("AAAA")).hasLength(2);

    // acceptable separators " " and "-" which should be ignored
    assertThat(checkDecoding("AA-AA")).hasLength(2);
    assertThat(checkDecoding("AA-AA")).hasLength(2);
    assertThat(checkDecoding("AA AA")).isEqualTo(checkDecoding("AA-AA"));
    assertThat(checkDecoding("AA AA")).isEqualTo(checkDecoding("AAAA"));

    // 1, 8, 9, 0 are not a valid character, decoding should fail
    assertThat(checkDecoding("11")).isNull();
    assertThat(checkDecoding("A1")).isNull();
    assertThat(checkDecoding("AAA8")).isNull();
    assertThat(checkDecoding("AAA9")).isNull();
    assertThat(checkDecoding("AAA0")).isNull();

    // non-alphanumerics (except =) are not valid characters and decoding should fail
    assertThat(checkDecoding("AAA,")).isNull();
    assertThat(checkDecoding("AAA;")).isNull();
    assertThat(checkDecoding("AAA.")).isNull();
    assertThat(checkDecoding("AAA!")).isNull();

    // this just documents that a null string causes a nullpointerexception.
    try {
      checkDecoding(null);
      Assert.fail();
    } catch (NullPointerException e) {
      // expected.
    }
  }

  private static byte[] string2Bytes(String s) {
    try {
      return s.getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("UTF-8 encoding is unsupported");
    }
  }

  private byte[] checkDecoding(String s) {
    try {
      return Base32String.decode(s);
    } catch (DecodingException e) {
      return null; // decoding failed.
    }
  }
}
