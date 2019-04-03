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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import java.net.HttpURLConnection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/** Unit tests for {@link NetworkTimeProvider}. */
@RunWith(JUnit4.class)
public class NetworkTimeProviderTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Mock private HttpURLConnection mMockHttpURLConnection;
  @Mock private HttpURLConnectionFactory mMockHttpURLConnectionFactory;
  @InjectMocks NetworkTimeProvider mProvider;

  @Before
  public void setUp() {
    initMocks(this);
  }

  @Test
  public void testResponseWithValidDate() throws Exception {
    withHttpRequestReturningDate(1338936841000L);
    assertThat(mProvider.getNetworkTime()).isEqualTo(1338936841000L);
  }

  @Test
  public void testResponseWithMissingDate() throws Exception {
    withHttpRequestReturningDate(null);
    thrown.expect(IOException.class);
    mProvider.getNetworkTime();
  }

  @Test
  public void testRequestThrowsExceptions() throws Exception {
    withHttpRequestThrowing(new IOException(""));
    thrown.expect(IOException.class);
    mProvider.getNetworkTime();
  }

  @Test
  public void testRequestReturnsErrorStatus() throws Exception {
    withHttpRequestReturningStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR);
    thrown.expect(IOException.class);
    mProvider.getNetworkTime();
  }

  private void withHttpRequestThrowing(Exception exception) throws IOException {
    when(mMockHttpURLConnectionFactory.openHttpUrl(NetworkTimeProvider.TIME_SOURCE_URL))
        .thenThrow(exception);
  }

  private void withHttpRequestReturningStatusCode(int statusCode) throws IOException {
    when(mMockHttpURLConnection.getResponseCode()).thenReturn(statusCode);
    when(mMockHttpURLConnectionFactory.openHttpUrl(NetworkTimeProvider.TIME_SOURCE_URL))
        .thenReturn(mMockHttpURLConnection);
  }

  private void withHttpRequestReturningDate(Long dateHeaderValue) throws IOException {
    when(mMockHttpURLConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
    if (dateHeaderValue != null) {
      when(mMockHttpURLConnection.getHeaderFieldDate("Date", 0)).thenReturn(dateHeaderValue);
    }
    when(mMockHttpURLConnectionFactory.openHttpUrl(NetworkTimeProvider.TIME_SOURCE_URL))
        .thenReturn(mMockHttpURLConnection);
  }
}
