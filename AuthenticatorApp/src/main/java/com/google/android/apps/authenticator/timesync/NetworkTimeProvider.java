/*
 * Copyright 2012 Google Inc. All Rights Reserved.
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

package com.google.android.apps.authenticator.timesync;

import android.util.Log;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;

import java.io.IOException;
import java.util.Date;

/**
 * Provider of network time that obtains the time by making a network request to Google.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class NetworkTimeProvider {

  private static final String LOG_TAG = NetworkTimeProvider.class.getSimpleName();
  private static final String URL = "https://www.google.com";

  private final HttpClient mHttpClient;

  public NetworkTimeProvider(HttpClient httpClient) {
    mHttpClient = httpClient;
  }

  /**
   * Gets the system time by issuing a request over the network.
   *
   * @return time (milliseconds since epoch).
   *
   * @throws IOException if an I/O error occurs.
   */
  public long getNetworkTime() throws IOException {
    HttpHead request = new HttpHead(URL);
    Log.i(LOG_TAG, "Sending request to " + request.getURI());
    HttpResponse httpResponse;
    try {
      httpResponse = mHttpClient.execute(request);
    } catch (ClientProtocolException e) {
      throw new IOException(String.valueOf(e));
    } catch (IOException e) {
      throw new IOException("Failed due to connectivity issues: " + e);
    }

    try {
      Header dateHeader = httpResponse.getLastHeader("Date");
      Log.i(LOG_TAG, "Received response with Date header: " + dateHeader);
      if (dateHeader == null) {
        throw new IOException("No Date header in response");
      }
      String dateHeaderValue = dateHeader.getValue();
      try {
        Date networkDate = DateUtils.parseDate(dateHeaderValue);
        return networkDate.getTime();
      } catch (DateParseException e) {
        throw new IOException(
            "Invalid Date header format in response: \"" + dateHeaderValue + "\"");
      }
    } finally {
      // Consume all of the content of the response to facilitate HTTP 1.1 persistent connection
      // reuse and to avoid running out of connections when this methods is scheduled on different
      // threads.
      try {
        HttpEntity responseEntity = httpResponse.getEntity();
        if (responseEntity != null) {
          responseEntity.consumeContent();
        }
      } catch (IOException e) {
        // Ignored because this is not an error that is relevant to clients of this transport.
      }
    }
  }
}
