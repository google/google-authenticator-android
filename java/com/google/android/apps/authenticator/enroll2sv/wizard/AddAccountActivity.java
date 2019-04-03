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

package com.google.android.apps.authenticator.enroll2sv.wizard;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import com.google.android.apps.authenticator.AuthenticatorActivity;
import com.google.android.apps.authenticator.howitworks.HowItWorksActivity;
import com.google.android.apps.authenticator.otp.EnterKeyActivity;
import com.google.android.apps.authenticator.settings.SettingsActivity;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator.testability.TestableActivity;
import com.google.android.apps.authenticator2.R;

/**
 * 2-step verification enrollment wizard page for adding an account scanning a QR code or manually
 * entering its details (name and secret key).
 */
public class AddAccountActivity extends TestableActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.enroll2sv_add_account);

    setSupportActionBar((Toolbar) findViewById(R.id.add_account_toolbar));
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setDisplayShowHomeEnabled(true);
    getSupportActionBar().setDisplayShowTitleEnabled(false);

    findViewById(R.id.enroll2sv_choose_account_page_scan_barcode_layout).setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View view) {
            startActivity(AuthenticatorActivity
                .getLaunchIntentActionScanBarcode(AddAccountActivity.this, true));
          }
        });

    findViewById(R.id.enroll2sv_choose_account_page_enter_key_layout).setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View view) {
            startActivity(new Intent(AddAccountActivity.this, EnterKeyActivity.class));
          }
        }
    );
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.add_account, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.how_it_works) {
      startActivity(new Intent(this, HowItWorksActivity.class));
      return true;
    } else if (item.getItemId() == R.id.settings) {
      startActivity(new Intent(this, SettingsActivity.class));
      return true;
    }
    return super.onOptionsItemSelected(item);
  }
}

