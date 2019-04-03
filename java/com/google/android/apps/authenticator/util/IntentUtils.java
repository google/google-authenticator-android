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

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

/** Utilities to handle intents. */
public class IntentUtils {

  /**
   * Gets a mandatory extra value from an Intent.
   *
   * @throws IntentParsingException if key is not found.
   */
  public static String getMandatoryExtraString(Intent intent, String key)
      throws IntentParsingException {
    if (!intent.hasExtra(key)) {
      throw new IntentParsingException(
          String.format("Intent does not contain %s extra parameter", key));
    }
    return intent.getStringExtra(key);
  }

  /**
   * Exception thrown when an Intent doesn't contain a mandatory extra value.
   */
  public static class IntentParsingException extends Exception {
    public IntentParsingException(String message) {
      super(message);
    }

    public IntentParsingException(Throwable cause) {
      super(cause);
    }
  }

  /**
   * Marshalls a list of {@link Parcelable} into parcel bytes for adding into an {@link Intent}
   * extras. Workaround for bug: https://code.google.com/p/android/issues/detail?id=6822
   */
  public static byte[] marshallParcelableList(ArrayList<? extends Parcelable> parcelables) {
    Parcel parcel = Parcel.obtain();
    parcel.writeList(parcelables);
    parcel.setDataPosition(0);
    return parcel.marshall();
  }

  /**
   * Unmarshalls a list of {@link Parcelable}.
   * Workaround for bug: https://code.google.com/p/android/issues/detail?id=6822
   */
  @SuppressWarnings("unchecked")
  public static <T extends Parcelable> ArrayList<T> unmarshallParcelableList(byte[] bytes,
      ClassLoader classLoader) {
    Parcel parcel = Parcel.obtain();
    parcel.unmarshall(bytes, 0, bytes.length);
    parcel.setDataPosition(0);
    return parcel.readArrayList(classLoader);
  }
}
