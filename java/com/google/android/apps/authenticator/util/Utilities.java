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

import android.content.Context;
import android.database.Cursor;
import android.text.Editable;
import android.text.Html;
import android.text.Html.TagHandler;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.BulletSpan;
import android.text.style.LeadingMarginSpan;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import java.io.UnsupportedEncodingException;
import org.xml.sax.XMLReader;

/** Assorted constants and utility methods. */
public class Utilities {

  public static final long SECOND_IN_MILLIS = 1000;
  public static final long MINUTE_IN_MILLIS = 60 * SECOND_IN_MILLIS;

  private Utilities() {}

  public static final long millisToSeconds(long timeMillis) {
    return timeMillis / 1000;
  }

  public static final long secondsToMillis(long timeSeconds) {
    return timeSeconds * 1000;
  }

  /**
   * Encodes the provided string as a byte array using the {@code US-ASCII} character encoding.
   */
  public static byte[] getAsciiBytes(String value) {
    try {
      return value.getBytes("US-ASCII");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("US-ASCII character encoding not supported", e);
    }
  }

  /**
   * Converts the provided HTML into a styled string. This method is similar to {@link
   * Html#fromHtml(String)}, but has the following advantages: <ul> <li>support for bullet
   * lists,</li> <li>trailing newlines are removed from the output.</li> </ul>
   */
  public static Spanned getStyledTextFromHtml(String html) {
    Spanned result = Html.fromHtml(html, null, new StyledTextTagHandler());
    result = removeTrailingNewlines(result);
    return result;
  }

  /**
   * {@link TagHandler} that adds support for bullet lists.
   */
  private static class StyledTextTagHandler implements TagHandler {

    @Override
    public void handleTag(boolean opening, String tag, Editable output, XMLReader xmlReader) {
      if ("ul".equalsIgnoreCase(tag)) {
        // Ensure there are at least two newlines both before and after the list.
        ensureAtLeastTwoTrailingNewlines(output);
      } else if ("li".equalsIgnoreCase(tag)) {
        appendNewlineIfNoTrailingNewline(output);
        int outputLength = output.length();
        if (opening) {
          // Attach a BulletSpan to the beginning of the list entry. The span will be removed
          // when processing the closing of this tag/entry.
          output.setSpan(new BulletSpan(), outputLength, outputLength, Spannable.SPAN_MARK_MARK);
        } else {
          // Attach a BulletSpan, spanning the whole list entry. This removes the span
          // attached to the start of this list entry when processing the opening of this tag/entry.
          // We also attach a LeadingMarginSpan to the same location to indent the list entries
          // and their bullets.
          BulletSpan[] bulletSpans = output.getSpans(0, outputLength, BulletSpan.class);
          if (bulletSpans.length > 0) {
            BulletSpan startMarkSpan = bulletSpans[bulletSpans.length - 1];
            int startIndex = output.getSpanStart(startMarkSpan);
            output.removeSpan(startMarkSpan);
            if (startIndex != outputLength) {
              output.setSpan(
                  new LeadingMarginSpan.Standard(10),
                  startIndex,
                  outputLength,
                  Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
              output.setSpan(
                  new BulletSpan(10),
                  startIndex,
                  outputLength,
                  Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
          }
        }
      }
    }

    /**
     * Appends a newline to the provided text if the text does not already end with a newline.
     */
    private static void appendNewlineIfNoTrailingNewline(Editable text) {
      int len = text.length();
      if (len == 0) {
        text.append('\n');
        return;
      }

      if (text.charAt(len - 1) != '\n') {
        text.append('\n');
      }
    }

    /**
     * Ensures that the text ends with two newline characters, appending newline characters only
     * when necessary.
     */
    private static void ensureAtLeastTwoTrailingNewlines(Editable text) {
      appendNewlineIfNoTrailingNewline(text);
      int len = text.length();
      if (len == 1) {
        // "text" is just a newline character (ensured by appendNewlineIfNoTrailingNewline above).
        text.append('\n');
        return;
      }

      // "text" now has at least two characters and ends with newline character
      if (text.charAt(len - 2) != '\n') {
        text.append('\n');
      }
    }
  }

  private static Spanned removeTrailingNewlines(Spanned text) {
    int trailingNewlineCharacterCount = 0;
    for (int i = text.length() - 1; i >= 0; i--) {
      char c = text.charAt(i);
      if ((c == '\n') || (c == '\r')) {
        trailingNewlineCharacterCount++;
      } else {
        break;
      }
    }
    if (trailingNewlineCharacterCount == 0) {
      return text;
    }

    return new SpannedString(
        text.subSequence(0, text.length() - trailingNewlineCharacterCount));
  }

  /**
   * This function insert a space in the middle of the string to divide a 6 digits pincode into two
   * 3-digits part which is used
   *
   * @param code The original pin code string
   * @return Return {@code null} when the input is {@code null}, return the original string if the
   *         code is not a 6-digits code, otherwise return the string with a space in the middle
   */

  public static String getStyledPincode(String code) {
    if (code == null) {
      return null;
    }

    // We need to check if the code is really a string with 6 digits
    if (code.length() != 6) {
      return code;
    }

    for (int i = 0; i < 6; ++i) {
      if (!('0' <= code.charAt(i) && code.charAt(i) <= '9')) {
        return code;
      }
    }

    return code.substring(0, 3) + " " + code.substring(3, 6);
  }

  /**
   * Get the combined text for issuer and account name, both of the texts appears in the same
   * textview with format: {@code [issuer] ([account name])}.
   *
   * <p>In case the issuer text is null or empty string, this function returns the account name
   * only.
   *
   * @param issuer the issuer text.
   * @param accountName the account name text.
   * @return the combined text as described above.
   */
  public static String getCombinedTextForIssuerAndAccountName(String issuer, String accountName) {
    if (!Strings.isNullOrEmpty(issuer)) {
      return issuer + " (" + accountName + ")";
    }
    return accountName;
  }
}
