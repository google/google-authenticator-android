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

package com.google.android.apps.authenticator;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewDebug.ExportedProperty;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.apps.authenticator.util.annotations.FixWhenMinSdkVersion;
import com.google.android.apps.authenticator2.R;

/**
 * Custom view that shows the user, pin, and "Get New Code" button (if enabled).
 * The layout for this is under res/layout/user_row.xml
 * For better accessibility, we have created this custom class to generate
 * accessibility events that are better suited for this widget.
 */
public class UserRowView extends LinearLayout {

  public UserRowView(Context context) {
    super(context);
  }
  
  public UserRowView(Context context, AttributeSet attrset) {
    super(context, attrset);
  }

  @Override
  @FixWhenMinSdkVersion(14)
  public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent accessEvent) {
    // IMPLEMENTATION NOTE: On pre-ICS platforms, content description of this View is ignored by
    // TalkBack. We thus explicitly set the text to be spoken for this AccessibilityEvent.
    accessEvent.setClassName(getClass().getName());
    accessEvent.setPackageName(getContext().getPackageName());
    accessEvent.getText().add(getTalkBackText());
    return true;
  }

  @Override
  @ExportedProperty(category = "accessibility")
  public CharSequence getContentDescription() {
    // IMPLEMENTATION NOTE: On ICS and above, when content description of this View is null,
    // TalkBack traverses all children of this View to compose the text to speak. We want to provide
    // custom text to be spoken when this View is selected by the user. Thus, we return the custom
    // text here instead of returning null (which is the default behavior).
    return getTalkBackText();
  }
  
  /**
   * Gets the text to be read back by an on-screen reader (e.g., TalkBack) for this row.
   */
  private String getTalkBackText() {
    // Format: OTP/verification code (digit-by-digit) followed by account name.
    // If there's no OTP, then it should say "get code" followed by account name.
    Context ctx = getContext();
    StringBuilder message = new StringBuilder();
    CharSequence pinText = ((TextView) findViewById(R.id.pin_value)).getText();
    if (ctx.getString(R.string.empty_pin).equals(pinText.toString())){
      message = message.append(ctx.getString(R.string.counter_pin));
    } else {
      for (int i = 0; i < pinText.length(); i++) {
        if (message.length() > 0) {
          message.append(' ');
        }
        message.append(pinText.charAt(i));
      }
    }
    
    CharSequence userText = ((TextView) findViewById(R.id.current_user)).getText();
    if (message.length() > 0) {
      message.append(' ');
    }
    message.append(userText);
    return message.toString();
  }
}
