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

package com.google.android.apps.authenticator.time;

/**
 * Standard "wall" clock expressing milliseconds since the UNIX epoch.
 *
 * <p>This is equivalent to {@link System#currentTimeMillis()}.
 */
public class SystemWallClock implements Clock {
  @Override
  public long nowMillis() {
    return System.currentTimeMillis();
  }
}
