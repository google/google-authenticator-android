/*
 * Copyright 2011 Google Inc. All Rights Reserved.
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

import com.google.android.apps.authenticator.testability.DependencyInjector;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

/**
 * Activity that tells the user that this application is deprecated and urges the user to install
 * the new application.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class DeprecationInfoActivity extends Activity {

  static final String NEW_APP_PACKAGE_NAME = "com.google.android.apps.authenticator2";
  private static final String NEW_APP_INSTALL_VIA_MARKET_URL =
      "market://details?id=" + NEW_APP_PACKAGE_NAME;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Use a different (longer) title from the one that's declared in the manifest (and the one that
    // the Android launcher displays).
    setTitle(R.string.app_name);

    setContentView(R.layout.deprecation_info_page);

    final Intent installNewAppIntent;
    int deprecationNoticeTextResId;
    if (isMarketInstalled()) {
      installNewAppIntent =
          new Intent(Intent.ACTION_VIEW, Uri.parse(NEW_APP_INSTALL_VIA_MARKET_URL));
      deprecationNoticeTextResId = R.string.deprecation_notice_page_text_install_via_market;
    } else {
      installNewAppIntent = null;
      deprecationNoticeTextResId = R.string.deprecation_notice_page_text_install_path_unknown;
    }

    TextView textView = (TextView) findViewById(R.id.text);
    if (installNewAppIntent != null) {
      // We have a link to install the new app
      final Intent onClickIntent = installNewAppIntent;
      // Change the behavior of HTML links (if any) to start the onClickIntent
      Spanned deprecationNotice =
          Utilities.replaceEmptyUrlSpansWithClickableSpans(
              Html.fromHtml(getString(deprecationNoticeTextResId)),
              new OnClickListener() {
                @Override
                public void onClick(View v) {
                  startActivity(onClickIntent);
                }
              });
      textView.setText(deprecationNotice);
      // Enable the link(s) in this text to be selected when focus is moved
      textView.setMovementMethod(LinkMovementMethod.getInstance());
    } else {
      // We don't have a link to install the new app
      textView.setText(Html.fromHtml(getString(deprecationNoticeTextResId)));
    }
  }

  private static boolean isMarketInstalled() {
    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(NEW_APP_INSTALL_VIA_MARKET_URL));
    return DependencyInjector.getPackageManager().resolveActivity(intent, 0) != null;
  }
}
