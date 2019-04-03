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

import android.content.Intent;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import com.google.android.apps.authenticator.otp.AccountDb.AccountIndex;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator.util.Base32String;
import com.google.android.apps.authenticator.util.Base32String.DecodingException;
import com.google.android.apps.authenticator.util.Utilities;
import com.google.android.apps.authenticator2.R;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The activity that displays the integrity check value for a key.
 *
 * <p>The user is passed in via the extra bundle in "user".
 */
public class CheckCodeActivity extends AppCompatActivity {
  private TextView mCheckCodeTextView;
  private TextView mCodeTextView;
  private TextView mCounterValue;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.check_code);

    setSupportActionBar((Toolbar) findViewById(R.id.enter_key_toolbar));
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setDisplayShowHomeEnabled(true);

    mCodeTextView = (TextView) findViewById(R.id.code_value);
    mCheckCodeTextView = (TextView) findViewById(R.id.check_code);
    mCounterValue = (TextView) findViewById(R.id.counter_value);

    Intent intent = getIntent();
    Bundle extras = intent.getExtras();
    AccountIndex index = (AccountIndex) extras.getSerializable("index");

    AccountDb accountDb = DependencyInjector.getAccountDb();
    AccountDb.OtpType type = accountDb.getType(index);
    if (type == AccountDb.OtpType.HOTP) {
      mCounterValue.setText(accountDb.getCounter(index).toString());
      findViewById(R.id.counter_area).setVisibility(View.VISIBLE);
    } else {
      findViewById(R.id.counter_area).setVisibility(View.GONE);
    }

    String secret = accountDb.getSecret(index);
    String checkCode = null;
    String errorMessage = null;
    try {
      checkCode = getCheckCode(secret);
    } catch (GeneralSecurityException e) {
      errorMessage = getString(R.string.general_security_exception);
    } catch (DecodingException e) {
      errorMessage = getString(R.string.decoding_exception);
    }
    if (errorMessage != null) {
      mCheckCodeTextView.setText(errorMessage);
      return;
    }
    mCodeTextView.setText(Utilities.getStyledPincode(checkCode));
    String checkCodeMessage = String.format(getString(R.string.check_code),
        TextUtils.htmlEncode(index.getStrippedName()));
    CharSequence styledCheckCode = Utilities.getStyledTextFromHtml(checkCodeMessage);
    mCheckCodeTextView.setText(styledCheckCode);
    mCheckCodeTextView.setVisibility(View.VISIBLE);
    findViewById(R.id.code_area).setVisibility(View.VISIBLE);

    // Show fake shadow on pre-Lollipop devices.
    if (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) {
      findViewById(R.id.toolbar_shadow).setVisibility(View.VISIBLE);
    } else {
      findViewById(R.id.toolbar_shadow).setVisibility(View.GONE);
    }
  }

  static String getCheckCode(String secret) throws GeneralSecurityException,
      DecodingException {
    final byte[] keyBytes = Base32String.decode(secret);
    Mac mac = Mac.getInstance("HMACSHA1");
    mac.init(new SecretKeySpec(keyBytes, ""));
    PasscodeGenerator pcg = new PasscodeGenerator(mac);
    return pcg.generateResponseCode(0L);
  }

}
