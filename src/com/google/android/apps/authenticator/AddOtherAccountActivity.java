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

import com.google.android.apps.authenticator.wizard.WizardPageActivity;
import com.google.android.apps.authenticator2.R;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import java.io.Serializable;

/**
 * The page of the "Add account" flow that offers the user to add an account.
 * The page offers the user to scan a barcode or manually enter the account details.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class AddOtherAccountActivity extends WizardPageActivity<Serializable> {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setPageContentView(R.layout.add_other_account);

    findViewById(R.id.scan_barcode).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        scanBarcode();
      }
    });
    findViewById(R.id.manually_add_account).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        manuallyEnterAccountDetails();
      }
    });

    mRightButton.setVisibility(View.INVISIBLE);
  }

  private void manuallyEnterAccountDetails() {
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setClass(this, EnterKeyActivity.class);
    startActivity(intent);
  }

  private void scanBarcode() {
    startActivity(AuthenticatorActivity.getLaunchIntentActionScanBarcode(this));
    finish();
  }
}
