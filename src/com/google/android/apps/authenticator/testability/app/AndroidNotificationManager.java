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

package com.google.android.apps.authenticator.testability.app;

import android.app.Notification;

/**
 * {@link NotificationManager} implementation that delegates all invocations to the respective
 * methods of {@link android.app.NotificationManager}.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class AndroidNotificationManager implements NotificationManager {

  private final android.app.NotificationManager mDelegate;

  private AndroidNotificationManager(android.app.NotificationManager delegate) {
    mDelegate = delegate;
  }

  public static AndroidNotificationManager wrap(android.app.NotificationManager delegate) {
    return (delegate != null) ? new AndroidNotificationManager(delegate) : null;
  }

  @Override
  public void cancel(int id) {
    mDelegate.cancel(id);
  }

  @Override
  public void notify(int id, Notification notification) {
    mDelegate.notify(id, notification);
  }
}
