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

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import com.google.android.apps.authenticator.AuthenticatorActivity;
import com.google.android.apps.authenticator.otp.AccountDb.AccountIndex;
import com.google.android.apps.authenticator.otp.AccountDb.OtpType;
import com.google.android.apps.authenticator.testability.TestableActivity;
import com.google.android.apps.authenticator.util.Base32String;
import com.google.android.apps.authenticator.util.Base32String.DecodingException;
import com.google.android.apps.authenticator2.R;
import com.google.common.annotations.VisibleForTesting;

/**
 * The activity that lets the user manually add an account by entering its name, key, and type
 * (TOTP/HOTP).
 */
public class EnterKeyActivity extends TestableActivity implements TextWatcher {

  @VisibleForTesting static final int DIALOG_ID_INVALID_DEVICE = 1;

  private static final int MIN_KEY_BYTES = 10;

  private EditText keyEntryField;
  private EditText accountName;
  private TextInputLayout keyEntryFieldInputLayout;
  private RadioButton typeTotp;
  private RadioButton typeHotp;

  /** Called when the activity is first created */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
    if (preferences.getBoolean(AuthenticatorActivity.KEY_DARK_MODE_ENABLED, false)) {
      setTheme(R.style.AuthenticatorTheme_NoActionBar_Dark);
    } else {
      setTheme(R.style.AuthenticatorTheme_NoActionBar);
    }

    super.onCreate(savedInstanceState);
    setContentView(R.layout.enter_key);

    setSupportActionBar((Toolbar) findViewById(R.id.enter_key_toolbar));
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setDisplayShowHomeEnabled(true);

    // Find all the views on the page
    keyEntryField = (EditText) findViewById(R.id.key_value);
    accountName = (EditText) findViewById(R.id.account_name);
    keyEntryFieldInputLayout = (TextInputLayout) findViewById(R.id.key_value_input_layout);

    typeTotp = (RadioButton) findViewById(R.id.type_choice_totp);
    typeHotp = (RadioButton) findViewById(R.id.type_choice_hotp);

    typeTotp.setText(getResources().getStringArray(R.array.type)[OtpType.TOTP.value]);
    typeHotp.setText(getResources().getStringArray(R.array.type)[OtpType.HOTP.value]);

    keyEntryFieldInputLayout.setErrorEnabled(true);

    // Set listeners
    keyEntryField.addTextChangedListener(this);

    findViewById(R.id.add_account_button_enter_key)
        .setOnClickListener(addButtonEnterKeyOnClickListener);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        this.onBackPressed();
        break;
    }
    return true;
  }

  /*
   * Return key entered by user, replacing visually similar characters 1 and 0.
   */
  private String getEnteredKey() {
    String enteredKey = keyEntryField.getText().toString();
    return enteredKey.replace('1', 'I').replace('0', 'O');
  }

  /*
   * Verify that the input field contains a valid base32 string,
   * and meets minimum key requirements.
   */
  private boolean validateKeyAndUpdateStatus(boolean submitting) {
    String userEnteredKey = getEnteredKey();
    try {
      byte[] decoded = Base32String.decode(userEnteredKey);
      if (decoded.length < MIN_KEY_BYTES) {
        // If the user is trying to submit a key that's too short, then
        // display a message saying it's too short.
        keyEntryFieldInputLayout.setError(
            submitting ? getString(R.string.enter_key_too_short) : null);
        return false;
      } else {
        keyEntryFieldInputLayout.setError(null);
        return true;
      }
    } catch (DecodingException e) {
      keyEntryFieldInputLayout.setError(getString(R.string.enter_key_illegal_char));
      return false;
    }
  }

  @SuppressWarnings("deprecation") // TODO: refactor to use DialogFrament
  private final View.OnClickListener addButtonEnterKeyOnClickListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          // TODO: This depends on the OtpType enumeration to correspond to array indices for the
          // dropdown with different OTP modes.
          OtpType mode = typeTotp.isChecked() ? OtpType.TOTP : OtpType.HOTP;

          if (validateKeyAndUpdateStatus(true)) {
            String accountName = EnterKeyActivity.this.accountName.getText().toString();

            // Note that this never overwrites an existing account, and instead a counter will be
            // appended to the account name if there is a collision.
            AuthenticatorActivity.saveSecret(
                EnterKeyActivity.this,
                new AccountIndex(accountName, null), // Manually entered keys have no issuer
                getEnteredKey(),
                mode,
                AccountDb.DEFAULT_HOTP_COUNTER);

            Intent intent = new Intent(EnterKeyActivity.this, AuthenticatorActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);

            finish();
          }
        }
      };

  @SuppressWarnings("deprecation") // TODO: refactor to use DialogFrament
  @Override
  protected Dialog onCreateDialog(int id, Bundle args) {
    switch (id) {
      case DIALOG_ID_INVALID_DEVICE:
        return new AlertDialog.Builder(this)
            .setIcon(R.drawable.quantum_ic_report_problem_grey600_24)
            .setTitle(R.string.error_title)
            .setMessage(R.string.error_invalid_device)
            .setPositiveButton(R.string.ok, null)
            .create();
      default:
        return super.onCreateDialog(id, args);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void afterTextChanged(Editable userEnteredValue) {
    validateKeyAndUpdateStatus(false);
  }

  /** {@inheritDoc} */
  @Override
  public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
    // Do nothing
  }

  /** {@inheritDoc} */
  @Override
  public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
    // Do nothing
  }
}
