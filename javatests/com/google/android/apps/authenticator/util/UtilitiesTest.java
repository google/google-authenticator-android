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
import static org.junit.Assert.fail;
import static org.mockito.MockitoAnnotations.initMocks;

import android.text.Html;
import android.text.Spanned;
import android.text.style.BulletSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.StyleSpan;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link Utilities}. */
@RunWith(JUnit4.class)
public class UtilitiesTest {

  @Before
  public void setUp() throws Exception {
    initMocks(this);
  }

  @Test
  public void testMillisToSeconds() {
    // Test rounding
    assertThat(Utilities.millisToSeconds(1234567)).isEqualTo(1234);
    assertThat(Utilities.millisToSeconds(1234000)).isEqualTo(1234);
    assertThat(Utilities.millisToSeconds(1234999)).isEqualTo(1234);

    // Test that it works fine for longs
    assertThat(Utilities.millisToSeconds(12345678901234L)).isEqualTo(12345678901L);
  }

  @Test
  public void testSecondsToMillis() {
    assertThat(Utilities.secondsToMillis(1234)).isEqualTo(1234000);

    // Test that it works fine for longs
    assertThat(Utilities.secondsToMillis(12345678901L)).isEqualTo(12345678901000L);
  }

  @Test
  public void testGetStyledTextFromHtmlWithNullInput() {
    try {
      Utilities.getStyledTextFromHtml(null);
      fail();
    } catch (NullPointerException expected) {}
  }

  @Test
  public void testGetStyledTextFromHtmlWithEmptyInput() {
    assertThat(Utilities.getStyledTextFromHtml("").toString()).isEqualTo("");
  }

  @Test
  public void testGetStyledTextFromHtmlWithInputWithoutParagraphs() {
    Spanned result = Utilities.getStyledTextFromHtml("123<b>first</b>, <i>second</i>");
    assertThat(result.toString()).isEqualTo("123first, second");

    StyleSpan[] spans = result.getSpans(0, result.length(), StyleSpan.class);
    assertThat(spans).hasLength(2);
    assertSpanLocation(spans[0], result, 3, 8);
    assertSpanLocation(spans[1], result, 10, 16);
  }

  @Test
  public void testGetStyledTextFromHtmlWithInputWithParagraph() {
    Spanned result = Utilities.getStyledTextFromHtml("123<b>first</b><p><i>second</i>");
    assertThat(result.toString()).isEqualTo("123first\n\nsecond");

    StyleSpan[] spans = result.getSpans(0, result.length(), StyleSpan.class);
    assertThat(spans).hasLength(2);
    assertSpanLocation(spans[0], result, 3, 8);
    assertSpanLocation(spans[1], result, 10, 16);
  }

  @Test
  public void testGetStyledTextFromHtmlWithInputWithTrailingParagraph() {
    assertThat(Utilities.getStyledTextFromHtml("First<p>Second<p>").toString())
        .isEqualTo("First\n\nSecond");
  }

  @Test
  public void testGetStyledTextFromHtmlWithInputWithBulletList() {
    assertThat(
            Utilities.getStyledTextFromHtml(
                "<ul><li>First<li><li>Second</li><li>Third<li></ul>End").toString())
        .isEqualTo("\n\nFirst\nSecond\nThird\n\nEnd");

    Spanned result = Utilities.getStyledTextFromHtml(
        "Some items:<ul>\n<li>First<li><li>Second</li>\n<li>Third<li>\n\n</ul>End");
    assertThat(result.toString()).isEqualTo("Some items:\n\nFirst\nSecond\nThird\n\nEnd");

    BulletSpan[] bulletSpans = result.getSpans(0, result.length(), BulletSpan.class);
    assertThat(bulletSpans).hasLength(3);
    assertSpanLocation(bulletSpans[0], result, 13, 19);
    assertSpanLocation(bulletSpans[1], result, 19, 26);
    assertSpanLocation(bulletSpans[2], result, 26, 32);

    LeadingMarginSpan.Standard[] leadingMarginSpans =
        result.getSpans(0, result.length(), LeadingMarginSpan.Standard.class);
    assertThat(bulletSpans).hasLength(3);
    assertSpanLocation(leadingMarginSpans[0], result, 13, 19);
    assertSpanLocation(leadingMarginSpans[1], result, 19, 26);
    assertSpanLocation(leadingMarginSpans[2], result, 26, 32);
  }

  @Test
  public void testGetStyledPincode() {
    assertThat(Utilities.getStyledPincode("")).isEqualTo("");
    assertThat(Utilities.getStyledPincode(null)).isNull();
    assertThat(Utilities.getStyledPincode("123456")).isEqualTo("123 456");
    assertThat(Utilities.getStyledPincode("______")).isEqualTo("______");
    assertThat(Utilities.getStyledPincode("000000")).isEqualTo("000 000");
    assertThat(Utilities.getStyledPincode("12345")).isEqualTo("12345");
    assertThat(Utilities.getStyledPincode("1234567")).isEqualTo("1234567");
  }

  @Test
  public void testGetCombinedTextForIssuerAndAccountName(){
    assertThat(Utilities.getCombinedTextForIssuerAndAccountName("Google", "abc.xyz@gmail.com"))
        .isEqualTo("Google (abc.xyz@gmail.com)");
    assertThat(
            Utilities.getCombinedTextForIssuerAndAccountName(
                "Yahoo Messenger", "abc.xyz@gmail.com"))
        .isEqualTo("Yahoo Messenger (abc.xyz@gmail.com)");
    assertThat(Utilities.getCombinedTextForIssuerAndAccountName("Facebook", "someone.xyz"))
        .isEqualTo("Facebook (someone.xyz)");
    assertThat(Utilities.getCombinedTextForIssuerAndAccountName("", "someone.xyz"))
        .isEqualTo("someone.xyz");
    assertThat(Utilities.getCombinedTextForIssuerAndAccountName(null, "abc.xyz@gmail.com"))
        .isEqualTo("abc.xyz@gmail.com");
  }

  private static void assertSpanLocation(
      Object span, Spanned text, int expectedStartIndex, int expectedEndIndex) {
    assertThat(text.getSpanStart(span)).isEqualTo(expectedStartIndex);
    assertThat(text.getSpanEnd(span)).isEqualTo(expectedEndIndex);
  }
}
