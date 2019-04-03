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

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;
import com.google.android.apps.authenticator.util.IntentUtils.IntentParsingException;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link IntentUtils}. */
@RunWith(JUnit4.class)
public class IntentUtilsTest {
  private static final String KEY = "key";
  private static final String VALUE = "value";

  @Test
  public void testGetMandatoryExtraStringSuccess() throws IntentParsingException {
    Intent intent = new Intent().putExtra(KEY, VALUE);

    String result = IntentUtils.getMandatoryExtraString(intent, KEY);

    assertThat(result).isEqualTo(VALUE);
  }

  @Test
  public void testGetMandatoryExtraStringFailure() {
    Intent intent = new Intent();

    try {
      IntentUtils.getMandatoryExtraString(intent, KEY);
      fail("Should have thrown IntentParsingException");
    } catch (IntentParsingException e) {
      // Expected
    }
  }

  @Test
  public void testMarshallUnmarshallParcelableList() {
    ArrayList<ParcelableTest> parcelables = new ArrayList<IntentUtilsTest.ParcelableTest>();
    parcelables.add(new ParcelableTest("1234"));
    parcelables.add(new ParcelableTest("test"));

    ArrayList<ParcelableTest> result = IntentUtils.unmarshallParcelableList(
        IntentUtils.marshallParcelableList(parcelables),
        ParcelableTest.class.getClassLoader());

    assertThat(result).isEqualTo(parcelables);
  }

  /**
   * Test {@link Parcelable} for tests.
   */
  public static class ParcelableTest implements Parcelable {

    private final String value;

    public ParcelableTest(String value) {
      this.value = value;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((value == null) ? 0 : value.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof ParcelableTest)) {
        return false;
      }
      ParcelableTest other = (ParcelableTest) obj;
      if (value == null) {
        return other.value == null;
      } else {
        return value.equals(other.value);
      }
    }

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeString(value);
    }

    public static final Parcelable.Creator<ParcelableTest> CREATOR =
        new Parcelable.Creator<IntentUtilsTest.ParcelableTest>() {
      @Override
      public ParcelableTest createFromParcel(Parcel source) {
        return new ParcelableTest(source.readString());
      }

      @Override
      public ParcelableTest[] newArray(int size) {
        return new ParcelableTest[size];
      }
    };
  }
}
