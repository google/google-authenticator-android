/*
 * Copyright (C) 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.google.android.apps.authenticator.license;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.android.apps.authenticator2.R;

/**
 * Container class to store the name of a library and the filename of its associated license file.
 */
public final class License implements Comparable<License>, Parcelable {
  // Name of the third-party library.
  private final String libraryName;
  // Byte offset in the file to the start of the license text.
  private final long licenseOffset;
  // Byte length of the license text.
  private final int licenseLength;

  /**
   * Create an object representing a stored license. The text for all licenses is stored in a single
   * file, so the offset and length describe this license's position within the file.
   */
  static License create(String libraryName, long licenseOffset, int licenseLength) {
    return new License(libraryName, licenseOffset, licenseLength);
  }

  public static final Parcelable.Creator<License> CREATOR =
      new Parcelable.Creator<License>() {
        @Override
        public License createFromParcel(Parcel in) {
          return new License(in);
        }

        @Override
        public License[] newArray(int size) {
          return new License[size];
        }
      };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(libraryName);
    dest.writeLong(licenseOffset);
    dest.writeInt(licenseLength);
  }

  @Override
  public int compareTo(License o) {
    return libraryName.compareToIgnoreCase(o.getLibraryName());
  }

  @Override
  public String toString() {
    return getLibraryName();
  }

  private License(String libraryName, long licenseOffset, int licenseLength) {
    this.libraryName = libraryName;
    this.licenseOffset = licenseOffset;
    this.licenseLength = licenseLength;
  }

  private License(Parcel in) {
    libraryName = in.readString();
    licenseOffset = in.readLong();
    licenseLength = in.readInt();
  }

  String getLibraryName() {
    return libraryName;
  }

  long getLicenseOffset() {
    return licenseOffset;
  }

  int getLicenseLength() {
    return licenseLength;
  }
}
