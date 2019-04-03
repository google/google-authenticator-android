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

import static com.google.common.truth.Truth.assertThat;

import com.google.android.apps.authenticator.otp.PasscodeGenerator.Signer;
import com.google.android.apps.authenticator.util.Base32String;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link PasscodeGenerator}. */
@RunWith(JUnit4.class)
public class PasscodeGeneratorTest {

  private byte[] keyBytes1;
  private byte[] keyBytes2;
  private Mac mac1;
  private Mac mac2;
  private PasscodeGenerator passcodeGenerator1;
  private PasscodeGenerator passcodeGenerator2;
  private Signer signer;

  @Before
  public void setUp() throws Exception {
    keyBytes1 = Base32String.decode("7777777777777777");
    keyBytes2 = Base32String.decode("22222222222222222");
    mac1 = Mac.getInstance("HMACSHA1");
    mac1.init(new SecretKeySpec(keyBytes1, ""));
    mac2 = Mac.getInstance("HMACSHA1");
    mac2.init(new SecretKeySpec(keyBytes2, ""));
    passcodeGenerator1 = new PasscodeGenerator(mac1);
    passcodeGenerator2 = new PasscodeGenerator(mac2);
    signer = AccountDb.getSigningOracle("7777777777777777");
  }

  @Test
  public void testGenerateResponseCodeLong() throws Exception {
    // test with long
    String response1Long = passcodeGenerator1.generateResponseCode(123456789123456789L);
    assertThat(passcodeGenerator1.verifyResponseCode(123456789123456789L, response1Long)).isTrue();
    assertThat(passcodeGenerator1.verifyResponseCode(123456789123456789L, "boguscode")).isFalse();
    // test with (long, null), response code should be same as with just long
    String response1LongNull = passcodeGenerator1.generateResponseCode(123456789123456789L, null);
    assertThat(response1LongNull).isEqualTo(response1Long);
    // test with byte[] using base32 encoded representation of byte array created from 0L
    String response1ByteArray =
        passcodeGenerator1.generateResponseCode(Base32String.decode("AG3JWS5M2BPRK"));
    assertThat(response1ByteArray).isEqualTo(response1Long);

    // test Long with another key bytes.
    String response2Long = passcodeGenerator2.generateResponseCode(123456789123456789L);
    assertThat(passcodeGenerator2.verifyResponseCode(123456789123456789L, response2Long)).isTrue();
  }

  @Test
  public void testRegressionGenerateResponseCode() throws Exception {
    // test with long
    assertThat(passcodeGenerator1.generateResponseCode(0L)).isEqualTo("724477");
    assertThat(passcodeGenerator1.generateResponseCode(123456789123456789L)).isEqualTo("815107");
    // test with byte[] for 0L and then for 123456789123456789L
    assertThat(passcodeGenerator1.generateResponseCode(Base32String.decode("AAAAAAAAAAAAA")))
        .isEqualTo("724477");
    assertThat(passcodeGenerator1.generateResponseCode(Base32String.decode("AG3JWS5M2BPRK")))
        .isEqualTo("815107");
    // test with long and byte[]

    assertThat(
            passcodeGenerator1.generateResponseCode(
                123456789123456789L, "challenge".getBytes("UTF-8")))
        .isEqualTo("498157");
  }

  @Test
  public void testVerifyTimeoutCode() throws Exception {
    /*      currentInterval is 1234 in this test.
     *      timeInterval, timeoutCode values around 1234.
     *              1231, 422609
     *              1232, 628381
     *              1233, 083501
     *              1234, 607007
     *              1235, 972746
     *              1236, 706552
     *              1237, 342936
     */
    // verify code and plus one interval and minus one interval timeout codes.
    assertThat(passcodeGenerator1.verifyTimeoutCode(1234, "607007")).isTrue();
    assertThat(passcodeGenerator1.verifyTimeoutCode(1234, "972746")).isTrue(); // plus one
    assertThat(passcodeGenerator1.verifyTimeoutCode(1234, "083501")).isTrue(); // minus one
    // fail for minus two
    assertThat(passcodeGenerator1.verifyTimeoutCode(1234, "628381")).isFalse();

    // verify timeout with custom window of +/- 2 intervals
    assertThat(passcodeGenerator1.verifyTimeoutCode("607007", 1234, 2, 2)).isTrue();
    assertThat(passcodeGenerator1.verifyTimeoutCode("972746", 1234, 2, 2)).isTrue(); // plus one
    assertThat(passcodeGenerator1.verifyTimeoutCode("706552", 1234, 2, 2)).isTrue(); // plus two
    assertThat(passcodeGenerator1.verifyTimeoutCode("083501", 1234, 2, 2)).isTrue(); // minus one
    assertThat(passcodeGenerator1.verifyTimeoutCode("628381", 1234, 2, 2)).isTrue(); // minus two
    // fail for wrong code
    assertThat(passcodeGenerator1.verifyTimeoutCode("000000", 1234, 2, 2)).isFalse();
    // fail for plus three
    assertThat(passcodeGenerator1.verifyTimeoutCode("342936", 1234, 2, 2)).isFalse();
    // verify timeout with custom window of +1 and -2 intervals
    assertThat(passcodeGenerator1.verifyTimeoutCode("607007", 1234, 1, 2)).isTrue();
    assertThat(passcodeGenerator1.verifyTimeoutCode("972746", 1234, 1, 2)).isTrue(); // plus one
    assertThat(passcodeGenerator1.verifyTimeoutCode("083501", 1234, 1, 2)).isTrue(); // minus one
    assertThat(passcodeGenerator1.verifyTimeoutCode("628381", 1234, 1, 2)).isTrue(); // minus two
    // fail for plus two
    assertThat(passcodeGenerator1.verifyTimeoutCode("706552", 1234, 1, 2)).isFalse();
    // fail for plus three
    assertThat(passcodeGenerator1.verifyTimeoutCode("342936", 1234, 1, 2)).isFalse();
    // fail for wrong code
    assertThat(passcodeGenerator1.verifyTimeoutCode("000000", 1234, 1, 2)).isFalse();
    // verify timeout with custom window of 0 and -0 intervals
    // pass for current
    assertThat(passcodeGenerator1.verifyTimeoutCode("607007", 1234, 0, 0)).isTrue();
    // fail for plus one
    assertThat(passcodeGenerator1.verifyTimeoutCode("972746", 1234, 0, 0)).isFalse();
    // fail for minus one
    assertThat(passcodeGenerator1.verifyTimeoutCode("083501", 1234, 0, 0)).isFalse();
  }

  @Test
  public void testMacAndSignEquivalence() throws Exception {
    String codeFromMac = passcodeGenerator1.generateResponseCode(0L);
    String codeFromSigning = new PasscodeGenerator(signer, 6).generateResponseCode(0L);
    assertThat(codeFromSigning).isEqualTo(codeFromMac);

    String codeFromSigning2 = new PasscodeGenerator(signer, 6).generateResponseCode(1L);
    assertThat(codeFromSigning.equals(codeFromSigning2)).isFalse();
  }
}
