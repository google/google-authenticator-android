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

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import android.test.MoreAsserts;

import junit.framework.TestCase;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicHeader;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Arrays;

/**
 * Unit tests for {@link NetworkTimeProvider}.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class NetworkTimeProviderTest extends TestCase {

  @Mock private HttpClient mMockHttpClient;
  private NetworkTimeProvider mProvider;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    mProvider = new NetworkTimeProvider(mMockHttpClient);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testRequest() throws Exception {
    withHttpRequestThrowing(new IOException("arbitrary"));
    try {
      mProvider.getNetworkTime();
    } catch (Exception expected) {}

    ArgumentCaptor<HttpUriRequest> requestCaptor = ArgumentCaptor.forClass(HttpUriRequest.class);
    verify(mMockHttpClient).execute(requestCaptor.capture());

    HttpUriRequest request = requestCaptor.getValue();
    assertEquals("HEAD", request.getMethod());
    assertEquals("https://www.google.com", request.getURI().toString());
    MoreAsserts.assertEmpty(Arrays.asList(request.getAllHeaders()));
  }

  public void testResponseWithValidDate() throws Exception {
    withHttpRequestReturningDate("Tue, 05 Jun 2012 22:54:01 GMT");
    assertEquals(1338936841000L, mProvider.getNetworkTime());
  }

  public void testResponseWithMissingDate() throws Exception {
    withHttpRequestReturningDate(null);
    try {
      mProvider.getNetworkTime();
      fail();
    } catch (IOException expected) {}
  }

  public void testResponseWithMalformedDate() throws Exception {
    withHttpRequestReturningDate("Tue, 05 Jun 2012 22:54:01 Unknown");
    try {
      mProvider.getNetworkTime();
      fail();
    } catch (IOException expected) {}
  }

  public void testRequestThrowsExceptions() throws Exception {
    withHttpRequestThrowing(new IOException(""));
    try {
      mProvider.getNetworkTime();
      fail();
    } catch (IOException expected) {}

    withHttpRequestThrowing(new ClientProtocolException());
    try {
      mProvider.getNetworkTime();
      fail();
    } catch (IOException expected) {}
  }

  private void withHttpRequestThrowing(Exception exception) throws IOException {
    doThrow(exception).when(mMockHttpClient).execute(Mockito.<HttpUriRequest>anyObject());
  }

  private void withHttpRequestReturningDate(String dateHeaderValue) throws IOException {
    HttpResponse mockResponse = mock(HttpResponse.class);
    if (dateHeaderValue != null) {
      doReturn(new BasicHeader("Date", dateHeaderValue)).when(mockResponse).getLastHeader("Date");
    }

    doReturn(mockResponse).when(mMockHttpClient).execute(Mockito.<HttpUriRequest>anyObject());
  }
}
