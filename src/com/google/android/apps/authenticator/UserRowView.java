/*
 * Copyright 2010 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.authenticator;

import com.google.android.apps.authenticator2.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Custom view that shows the user, pin, and "Get New Code" button (if enabled).
 * The layout for this is under res/layout/user_row.xml
 * For better accessibility, we have created this custom class to generate
 * accessibility events that are better suited for this widget.
 *
 * @author clchen@google.com (Charles L. Chen)
 */
public class UserRowView extends LinearLayout {

  public UserRowView(Context context) {
    super(context);
  }

  public UserRowView(Context context, AttributeSet attrset) {
    super(context, attrset);
  }

  @Override
  public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent accessEvent) {
    Context ctx = this.getContext();

    String message = "";
    CharSequence pinText = ((TextView) findViewById(R.id.pin_value)).getText();
    if (ctx.getString(R.string.empty_pin).equals(pinText)){
        message = ctx.getString(R.string.counter_pin);
    } else {
        for (int i = 0; i < pinText.length(); i++) {
            message = message + pinText.charAt(i) + " ";
        }
    }
    CharSequence userText = ((TextView) findViewById(R.id.current_user)).getText();
    message = message + " " + userText;
    accessEvent.setClassName(getClass().getName());
    accessEvent.setPackageName(ctx.getPackageName());
    accessEvent.getText().add(message);

    return true;
  }
}
