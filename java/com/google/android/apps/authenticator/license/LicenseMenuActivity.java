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

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import com.google.android.apps.authenticator2.R;
import java.util.ArrayList;
import java.util.List;

/** An Activity listing third party libraries with notice licenses. */
public final class LicenseMenuActivity extends AppCompatActivity
    implements LoaderCallbacks<List<License>> {

  static final String ARGS_LICENSE = "license";

  private static final int LOADER_ID = 54321;

  private ArrayAdapter<License> listAdapter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.license_menu_activity);

    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    listAdapter = new ArrayAdapter<>(this, R.layout.license, R.id.license, new ArrayList<>());
    getSupportLoaderManager().initLoader(LOADER_ID, null, this);
    ListView listView = (ListView) findViewById(R.id.license_list);
    listView.setAdapter(listAdapter);
    listView.setOnItemClickListener(
        new OnItemClickListener() {
          @Override
          public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            License license = (License) parent.getItemAtPosition(position);
            Intent licenseIntent = new Intent(LicenseMenuActivity.this, LicenseActivity.class);
            licenseIntent.putExtra(ARGS_LICENSE, license);
            startActivity(licenseIntent);
          }
        });
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      // Go back one place in the history stack, if the app icon is clicked.
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    getSupportLoaderManager().destroyLoader(LOADER_ID);
  }

  @Override
  public Loader<List<License>> onCreateLoader(int id, Bundle args) {
    return new LicenseLoader(this);
  }

  @Override
  public void onLoadFinished(Loader<List<License>> loader, List<License> licenses) {
    listAdapter.clear();
    listAdapter.addAll(licenses);
    listAdapter.notifyDataSetChanged();
  }

  @Override
  public void onLoaderReset(Loader<List<License>> loader) {
    listAdapter.clear();
    listAdapter.notifyDataSetChanged();
  }
}
