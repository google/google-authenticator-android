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

package com.google.android.apps.authenticator.testability;

import android.os.Build;
import android.test.AndroidTestCase;

import org.apache.http.client.HttpClient;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

/**
 * Unit tests for {@link HttpClientFactory}.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class HttpClientFactoryTest extends AndroidTestCase {

  private HttpClient mClient;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mClient = HttpClientFactory.createHttpClient(getContext());
  }

  @Override
  protected void tearDown() throws Exception {
    if (mClient != null) {
      ClientConnectionManager connectionManager = mClient.getConnectionManager();
      if (connectionManager != null) {
        connectionManager.shutdown();
      }
    }
    super.tearDown();
  }

  public void testClientClass() throws Exception {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.ECLAIR_MR1) {
      assertEquals(
          getContext().getClassLoader().loadClass(DefaultHttpClient.class.getName()),
          mClient.getClass());
    } else {
      assertEquals(
          getContext().getClassLoader().loadClass("android.net.http.AndroidHttpClient"),
          mClient.getClass());
    }
  }

  public void testClientConfiguration() throws Exception {
    HttpParams params = mClient.getParams();
    assertFalse(HttpClientParams.isRedirecting(params));
    assertFalse(HttpClientParams.isAuthenticating(params));
    assertEquals(
        HttpClientFactory.DEFAULT_CONNECT_TIMEOUT_MILLIS,
        HttpConnectionParams.getConnectionTimeout(params));
    assertEquals(
        HttpClientFactory.DEFAULT_READ_TIMEOUT_MILLIS,
        HttpConnectionParams.getSoTimeout(params));
    assertEquals(
        HttpClientFactory.DEFAULT_GET_CONNECTION_FROM_POOL_TIMEOUT_MILLIS,
        ConnManagerParams.getTimeout(params));
  }
}
