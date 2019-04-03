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

import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;
import com.google.android.apps.authenticator2.R;
import java.util.List;

/** {@link AsyncTaskLoader} to load the list of licenses for the license menu activity. */
final class LicenseLoader extends AsyncTaskLoader<List<License>> {

  private List<License> licenses;

  LicenseLoader(Context context) {
    // This must only pass the application context to avoid leaking a pointer to the Activity.
    super(context.getApplicationContext());
  }

  @Override
  public List<License> loadInBackground() {
    return Licenses.getLicenses(getContext());
  }

  @Override
  public void deliverResult(List<License> licenses) {
    this.licenses = licenses;
    super.deliverResult(licenses);
  }

  @Override
  protected void onStartLoading() {
    if (licenses != null) {
      deliverResult(licenses);
    } else {
      forceLoad();
    }
  }

  @Override
  protected void onStopLoading() {
    cancelLoad();
  }
}
