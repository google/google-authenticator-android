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

package com.google.android.apps.authenticator.timesync;

import android.util.Log;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import javax.inject.Inject;

/** Provider of network time that obtains the time by making a network request to Google. */
public class NetworkTimeProvider {

  @VisibleForTesting static final URL TIME_SOURCE_URL;
  static {
    try {
      // URL is unrelated to time sync, but likely to remain live for a long time and does not use
      // caching.
      TIME_SOURCE_URL = new URL("https://www.google.com");
    } catch (MalformedURLException e) {
      // Shouldn't happen in practice, propagate the impossible as a RuntimeException just in case.
      throw new RuntimeException(e);
    }
  }

  private static final String LOG_TAG = NetworkTimeProvider.class.getSimpleName();
  private static final String DATE_HEADER = "Date";

  private final HttpURLConnectionFactory mUrlConnectionFactory;

  @Inject
  public NetworkTimeProvider(HttpURLConnectionFactory urlConnectionFactory) {
    mUrlConnectionFactory = urlConnectionFactory;
  }

  /**
   * Gets the system time by issuing a request over the network.
   *
   * @return time (milliseconds since epoch).
   *
   * @throws IOException if an I/O error occurs.
   */
  public long getNetworkTime() throws IOException {
    HttpURLConnection urlConnection = mUrlConnectionFactory.openHttpUrl(TIME_SOURCE_URL);
    urlConnection.setRequestMethod("HEAD");
    int responseCode = urlConnection.getResponseCode();
    if (responseCode != HttpURLConnection.HTTP_OK) {
      Log.d(LOG_TAG, String.format("URL %s returned %d", TIME_SOURCE_URL, responseCode));
      throw new IOException(String.format("HTTP status code %d", responseCode));
    }
    long date = urlConnection.getHeaderFieldDate(DATE_HEADER, 0);
    if (date == 0) {
      String dateHeader = urlConnection.getHeaderField(DATE_HEADER);
      throw new IOException("Got missing or invalid date from header value " + dateHeader);
    }
    Log.d(LOG_TAG, String.format("Got date value %d", date));
    return date;
  }
}
