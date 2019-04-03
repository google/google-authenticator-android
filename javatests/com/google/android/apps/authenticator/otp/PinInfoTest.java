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

import android.os.Parcel;
import com.google.android.apps.authenticator.otp.AccountDb.AccountIndex;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link PinInfo}. */
@RunWith(JUnit4.class)
public class PinInfoTest {

  @Test
  public void testWriteToParcel_verifyRestoredInstanceEqualsOriginal() {
    PinInfo pinInfo = new PinInfo(new AccountIndex("name1", "issuer1"), true);
    pinInfo.setPin("000111");
    pinInfo.setIsHotpCodeGenerationAllowed(false);

    Parcel parcel = Parcel.obtain();
    pinInfo.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);
    PinInfo restoredFromParcel = PinInfo.CREATOR.createFromParcel(parcel);
    assertThat(restoredFromParcel).isEqualTo(pinInfo);
  }

  @Test
  public void testWriteToParcel_verifyRestoredInstanceEqualsOriginalIncludingNullFields() {
    PinInfo pinInfo = new PinInfo(new AccountIndex("name1", null));
    pinInfo.setPin(null); // Default values should be null, but explicitly set
                          // null here for testing.
    pinInfo.setIsHotpCodeGenerationAllowed(false);

    Parcel parcel = Parcel.obtain();
    pinInfo.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);
    PinInfo restoredFromParcel = PinInfo.CREATOR.createFromParcel(parcel);
    assertThat(restoredFromParcel).isEqualTo(pinInfo);
  }

  @Test
  public void testSwapIndex() {
    PinInfo pinInfo1 = new PinInfo(new AccountIndex("name1", "issuer1"));
    pinInfo1.setPin("000111");
    PinInfo pinInfo2 = new PinInfo(new AccountIndex("name2", "issuer2"), false);
    PinInfo[] pinInfoArray = new PinInfo[] { pinInfo1, pinInfo2 };

    PinInfo.swapIndex(pinInfoArray, 0, 1);

    assertThat(pinInfoArray[1]).isEqualTo(pinInfo1);
    assertThat(pinInfoArray[0]).isEqualTo(pinInfo2);
  }
}

