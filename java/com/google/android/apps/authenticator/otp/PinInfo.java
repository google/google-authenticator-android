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

import android.os.Parcel;
import android.os.Parcelable;
import com.google.android.apps.authenticator.otp.AccountDb.AccountIndex;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import javax.annotation.Nullable;

/**
 * A tuple of user, OTP value, and type, that represents a particular user.
 *
 * <p>It Implements {@link Parcelable} so that this class can be conveyed through {@link
 * android.os.Bundle}.
 */
public class PinInfo implements Parcelable {

  /** Calculated OTP, or a placeholder if not calculated */
  @Nullable
  private String mPin = null;

  /** {@link AccountIndex} that owns the pin */
  private final AccountIndex mIndex;

  /**
   * Used to represent whether the OTP type is TOTP or HOTP.
   *
   * <ul>
   *   <li>true: HOTP (counter based)
   *   <li>false: TOTP (time based)
   * </ul>
   **/
  private final boolean mIsHotp;

  /** HOTP only: Whether code generation is allowed for this account. */
  private boolean mHotpCodeGenerationAllowed = false;

  /**
   * Constructor of {@link PinInfo}. The default value for mIsHotp is false.
   *
   * @param index the {@link AccountIndex} that owns the pin
   */
  public PinInfo(AccountIndex index) {
    this(index, false);
  }

  /**
   * Constructor of {@link PinInfo}.
   *
   * @param index {@link AccountIndex} that owns the pin
   * @param isHotp represents whether the OTP type is TOTP or HOTP
   */
  public PinInfo(AccountIndex index, boolean isHotp) {
    mIndex = Preconditions.checkNotNull(index);
    mIsHotp = isHotp;
  }

  public PinInfo(Parcel pc) {
    // Using readValue instead of readString since mPin can be null.
    mPin = (String) pc.readValue(PinInfo.class.getClassLoader());
    mIndex = (AccountIndex) pc.readSerializable();
    boolean[] booleanArray = new boolean[2];
    pc.readBooleanArray(booleanArray);
    mIsHotp = booleanArray[0];
    mHotpCodeGenerationAllowed = booleanArray[1];
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel pc, int flags) {
    // Using writeValue instead of writeString since mPin can be null.
    pc.writeValue(mPin);
    pc.writeSerializable(mIndex);
    pc.writeBooleanArray(new boolean[] { mIsHotp, mHotpCodeGenerationAllowed });
  }

  public static final Parcelable.Creator<PinInfo> CREATOR = new Parcelable.Creator<PinInfo>() {
      @Override
    public PinInfo createFromParcel(Parcel in) {
      return new PinInfo(in);
    }

      @Override
    public PinInfo[] newArray(int size) {
      return new PinInfo[size];
    }
  };

  @Nullable
  public String getPin() {
    return mPin;
  }

  public PinInfo setPin(String pin) {
    mPin = pin;
    return this;
  }

  public AccountIndex getIndex() {
    return mIndex;
  }

  public boolean isHotp() {
    return mIsHotp;
  }

  public boolean isHotpCodeGenerationAllowed() {
    return mHotpCodeGenerationAllowed;
  }

  public PinInfo setIsHotpCodeGenerationAllowed(boolean hotpCodeGenerationAllowed) {
    mHotpCodeGenerationAllowed = hotpCodeGenerationAllowed;
    return this;
  }

  public static void swapIndex(PinInfo[] pinInfoArray, int i, int j) {
    PinInfo pinInfo = pinInfoArray[i];
    pinInfoArray[i] = pinInfoArray[j];
    pinInfoArray[j] = pinInfo;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(mPin, mIndex, mIsHotp, mHotpCodeGenerationAllowed);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof PinInfo)) {
      return false;
    }
    PinInfo other = (PinInfo) obj;
    return Objects.equal(other.mIndex, mIndex) && Objects.equal(other.mIsHotp, mIsHotp)
        && Objects.equal(other.mPin, mPin)
        && Objects.equal(other.mHotpCodeGenerationAllowed, mHotpCodeGenerationAllowed);
  }

  @Override
  public String toString() {
    return String.format("PinInfo {mPin=%s, mIndex=%s, mIsHotp=%s, mHotpCodeGenerationAllowed=%s}",
        mPin, mIndex, mIsHotp, mHotpCodeGenerationAllowed);
  }
}

