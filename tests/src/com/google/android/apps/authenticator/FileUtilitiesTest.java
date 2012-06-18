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

import android.test.AndroidTestCase;

import java.io.File;
import java.io.IOException;

/**
 * Unit tests for {@link Utilities}.
 *
 * @author sarvar@google.com (Sarvar Patel)
 */
public class FileUtilitiesTest extends AndroidTestCase {

  public void testRestrictAccess_withActualDirectory() throws Exception {
    File dir = createTempDirInCacheDir();
    try {
      String path = dir.getPath();
      setFilePermissions(path, 0755);

      FileUtilities.restrictAccessToOwnerOnly(path);
      assertEquals(0700, getFilePermissions(path) & 0777);
    } finally {
      dir.delete();
    }
  }

  public void testRestrictAccess_withActualNonExistentDirectory()
      throws Exception {
    File dir = createTempDirInCacheDir();
    assertTrue(dir.delete());

    try {
      FileUtilities.restrictAccessToOwnerOnly(dir.getPath());
      fail();
    } catch (IOException expected) {}
  }

  public void testGetStat_withActualDirectory() throws Exception {
    File dir = createTempDirInCacheDir();
    try {
      String path = dir.getPath();
      setFilePermissions(path, 0755);
      FileUtilities.StatStruct s1 = FileUtilities.getStat(path);
      assertEquals(0755, s1.mode & 0777);
      long ctime1 = s1.ctime;
      assertTrue(s1.toString().contains(Long.toString(ctime1)));

      setFilePermissions(path, 0700);
      FileUtilities.StatStruct s2 = FileUtilities.getStat(path);
      assertEquals(0700, s2.mode & 0777);
      long ctime2 = s2.ctime;
      assertTrue(ctime2 >= ctime1);
    } finally {
      dir.delete();
    }
  }

  public void testGetStat_withActualNonExistentDirectory() throws Exception {
    File dir = createTempDirInCacheDir();
    assertTrue(dir.delete());
    try {
      FileUtilities.getStat(dir.getPath());
      fail();
    } catch (IOException expected) {}
  }

  private static void setFilePermissions(String path, int mode) throws Exception {
    // IMPLEMENTATION NOTE: The code below simply invokes
    // android.os.FileUtils.setPermissions(path, mode, -1, -1) via Reflection.

    int errorCode = (Integer) Class.forName("android.os.FileUtils")
        .getMethod("setPermissions", String.class, int.class, int.class, int.class)
        .invoke(null, path, mode, -1, -1);
    assertEquals(0, errorCode);
    assertEquals(mode, getFilePermissions(path) & 0777);
  }

  private static int getFilePermissions(String path)
      throws Exception {
    // IMPLEMENTATION NOTE: The code below simply invokes
    // android.os.FileUtils.getPermissions(path, int[]) via Reflection.

    int[] modeUidAndGid = new int[3];
    int errorCode = (Integer) Class.forName("android.os.FileUtils")
        .getMethod("getPermissions", String.class, int[].class)
        .invoke(null, path, modeUidAndGid);
    assertEquals(0, errorCode);
    return modeUidAndGid[0];
  }

  private File createTempDirInCacheDir() throws IOException {
    // IMPLEMENTATION NOTE: There's no API to create temp dir on one go. Thus, we create
    // a temp file, delete it, and recreate it as a directory.
    File file = File.createTempFile(getClass().getSimpleName(), "", getContext().getCacheDir());
    assertTrue(file.delete());
    assertTrue(file.mkdir());
    return file;
  }
}
