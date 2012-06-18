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

package com.google.android.apps.authenticator;

import java.io.IOException;

/**
 * A class for handling file system related methods, such as setting permissions.
 *
 * @author sarvar@google.com (Sarvar Patel)
 */
public class FileUtilities {

  /** Hidden constructor to prevent instantiation. */
  private FileUtilities() { }

  /**
   * Restricts the file permissions of the provided path so that only the owner (UID)
   * can access it.
   */
  public static void restrictAccessToOwnerOnly(String path) throws IOException {
    // IMPLEMENTATION NOTE: The code below simply invokes the hidden API
    // android.os.FileUtils.setPermissions(path, 0700, -1, -1) via Reflection.

    int errorCode;
    try {
      errorCode = (Integer) Class.forName("android.os.FileUtils")
          .getMethod("setPermissions", String.class, int.class, int.class, int.class)
          .invoke(null, path, 0700, -1, -1);
    } catch (Exception e) {
      // Can't chain exception because IOException doesn't have the right constructor on Froyo
      // and below
      throw new IOException("Failed to set permissions: " + e);
    }
    if (errorCode != 0) {
      throw new IOException("FileUtils.setPermissions failed with error code " + errorCode);
    }
  }
}
