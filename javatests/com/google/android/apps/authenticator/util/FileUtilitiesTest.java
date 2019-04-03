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

package com.google.android.apps.authenticator.util;

import static android.os.Build.VERSION_CODES.KITKAT;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link FileUtilities}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class FileUtilitiesTest {
  
  @Test
  public void testRestrictAccess_withActualDirectory() throws Exception {
    File dir = createTempDirInCacheDir();
    try {
      String path = dir.getPath();
      setFilePermissions(path, 0755);
      
      FileUtilities.restrictAccessToOwnerOnly(path);
      assertThat(getFilePermissions(path) & 0777).isEqualTo(0700);
    } finally {
      dir.delete();
    }
  }
  
  @Test
  public void testRestrictAccess_withActualNonExistentDirectory()
      throws Exception {
    File dir = createTempDirInCacheDir();
    assertThat(dir.delete()).isTrue();
    
    try {
      FileUtilities.restrictAccessToOwnerOnly(dir.getPath());
      Assert.fail();
    } catch (IOException expected) {}
  }
  
  // TODO: Remove SDK suppression once getStat implementation no longer uses reflection
  // and deprecated APIs.
  @Test
  @SdkSuppress(maxSdkVersion = KITKAT)
  public void testGetStat_withActualDirectory() throws Exception {
    File dir = createTempDirInCacheDir();
    try {
      String path = dir.getPath();
      setFilePermissions(path, 0755);
      FileUtilities.StatStruct s1 = FileUtilities.getStat(path);
      assertThat(s1.mode & 0777).isEqualTo(0755);
      long ctime1 = s1.ctime;
      assertThat(s1.toString()).contains(Long.toString(ctime1));
      
      setFilePermissions(path, 0700);
      FileUtilities.StatStruct s2 = FileUtilities.getStat(path);
      assertThat(s2.mode & 0777).isEqualTo(0700);
      long ctime2 = s2.ctime;
      assertThat(ctime2 >= ctime1).isTrue();
    } finally {
      dir.delete();
    }
  }
  
  @Test
  public void testGetStat_withActualNonExistentDirectory() throws Exception {
    File dir = createTempDirInCacheDir();
    assertThat(dir.delete()).isTrue();
    try {
      FileUtilities.getStat(dir.getPath());
      Assert.fail();
    } catch (IOException expected) {}
  }      
  
  private static void setFilePermissions(String path, int mode) throws Exception {
    // IMPLEMENTATION NOTE: The code below simply invokes
    // android.os.FileUtils.setPermissions(path, mode, -1, -1) via Reflection.
    
    int errorCode = (Integer) Class.forName("android.os.FileUtils")
        .getMethod("setPermissions", String.class, int.class, int.class, int.class)
        .invoke(null, path, mode, -1, -1);
    assertThat(errorCode).isEqualTo(0);
    assertThat(getFilePermissions(path) & 0777).isEqualTo(mode);
  }
  
  private static int getFilePermissions(String path)
      throws Exception {
    // IMPLEMENTATION NOTE: The code below simply invokes
    // android.os.FileUtils.getPermissions(path, int[]) via Reflection.
    // However, getPermissions has been removed in JB MR1. As a result, we fall back to
    // libcore.io.StructStat libcore.io.Libcore.os.stat(path), from which we return st_mode.
    // Since Libcore is not available until ICS, we have to keep using FileUtils and falling back
    // to Libcore.
    
    try {
      int[] modeUidAndGid = new int[3];
      int errorCode = (Integer) Class.forName("android.os.FileUtils")
          .getMethod("getPermissions", String.class, int[].class)
          .invoke(null, path, modeUidAndGid);
      assertThat(errorCode).isEqualTo(0);
      return modeUidAndGid[0];
    } catch (NoSuchMethodException ignored) {
      // Fall back to Libcore.os.stat(path).st_mode
    }
    
    // Get the Libcore.os static field
    Object os = Class.forName("libcore.io.Libcore").getField("os").get(null);
    // Invoke Libcore.os.stat(String)
    Object structStat = os.getClass().getMethod("stat", String.class).invoke(os, path);
    // Get the value of st_mode field
    return structStat.getClass().getField("st_mode").getInt(structStat);
  }
  
  private File createTempDirInCacheDir() throws IOException {
    // IMPLEMENTATION NOTE: There's no API to create temp dir on one go. Thus, we create
    // a temp file, delete it, and recreate it as a directory.
    File file =
        File.createTempFile(
            getClass().getSimpleName(),
            "",
            InstrumentationRegistry.getInstrumentation().getContext().getCacheDir());
    assertThat(file.delete()).isTrue();
    assertThat(file.mkdir()).isTrue();
    return file;
  }
}
