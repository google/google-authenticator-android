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

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import com.google.android.apps.authenticator.util.annotations.FixWhenMinSdkVersion;
import java.io.IOException;

/** A class for handling file system related methods, such as setting permissions. */
public class FileUtilities {

  /** Path to databases */
  public static final String DATABASES_PATH = "databases";

  /** Hidden constructor to prevent instantiation. */
  private FileUtilities() { }

  /**
   * Restricts the file permissions of the provided path so that only the owner (UID)
   * can access it.
   */
  @FixWhenMinSdkVersion(9) // IOException(String, Throwable) will be available
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

  /**
   * Gets filesystem information about the provided path.
   */
  @FixWhenMinSdkVersion(9) // IOException(String, Throwable) will be available
  public static StatStruct getStat(String path) throws IOException {
    // IMPLEMENTATION NOTE: This method uses Reflection to invoke
    // android.os.FileUtils.getFileStatus(path) which returns FileStatus.
    // In JellyBean MR1 FileUtils.getFileStatus was removed. Thus, we fall back to
    // libcore.io.Libcore.os.stat(path).
    // Since Libcore is not available until ICS, we have to keep using FileUtils and falling back
    // to Libcore.

    // Try android.os.FileUtils.getFileStatus(path)
    try {
      @FixWhenMinSdkVersion(14) // Use Libcore unconditionally
      Object obj = Class.forName("android.os.FileUtils$FileStatus").newInstance();
      boolean success = (Boolean) Class.forName("android.os.FileUtils")
          .getMethod("getFileStatus", String.class,
              Class.forName("android.os.FileUtils$FileStatus"))
          .invoke(null, path, obj);
      if (success) {
        StatStruct stat = new StatStruct();
        stat.dev = getFileStatusInt(obj, "dev");
        stat.ino = getFileStatusInt(obj, "ino");
        stat.mode = getFileStatusInt(obj, "mode");
        stat.nlink = getFileStatusInt(obj, "nlink");
        stat.uid = getFileStatusInt(obj, "uid");
        stat.gid = getFileStatusInt(obj, "gid");
        stat.rdev = getFileStatusInt(obj, "rdev");
        stat.size = getFileStatusLong(obj, "size");
        stat.blksize = getFileStatusInt(obj, "blksize");
        stat.blocks = getFileStatusLong(obj, "blocks");
        stat.atime = getFileStatusLong(obj, "atime");
        stat.mtime = getFileStatusLong(obj, "mtime");
        stat.ctime = getFileStatusLong(obj, "ctime");
        return stat;
      } else {
        throw new IOException("FileUtils.getFileStatus returned with failure.");
      }
    } catch (ClassNotFoundException ignored) {
      // FileUtils.FileStatus not found. Fall back to Libcore.os.stat() and the resulting
      // libcore.io.StructStat
    } catch (Exception e) {
      // Can't chain exception because IOException doesn't have the right constructor on Froyo
      // and below
      throw new IOException("Failed to get FileStatus: " + e);
    }

    // Try libcore.io.Libcore.os.stat(path)
    try {
      // Get the Libcore.os static field
      Object os = Class.forName("libcore.io.Libcore").getField("os").get(null);
      // Invoke Libcore.os.stat(String)
      Object structStat = os.getClass().getMethod("stat", String.class).invoke(os, path);
      if (structStat != null) {
        StatStruct stat = new StatStruct();
        stat.dev = getLibcoreFileStatusLong(structStat, "st_dev");
        stat.ino = getLibcoreFileStatusLong(structStat, "st_ino");
        stat.mode = getLibcoreFileStatusInt(structStat, "st_mode");
        stat.nlink = getLibcoreFileStatusLong(structStat, "st_nlink");
        stat.uid = getLibcoreFileStatusInt(structStat, "st_uid");
        stat.gid = getLibcoreFileStatusInt(structStat, "st_gid");
        stat.rdev = getLibcoreFileStatusLong(structStat, "st_rdev");
        stat.size = getLibcoreFileStatusLong(structStat, "st_size");
        stat.blksize = getLibcoreFileStatusLong(structStat, "st_blksize");
        stat.blocks = getLibcoreFileStatusLong(structStat, "st_blocks");
        stat.atime = getLibcoreFileStatusLong(structStat, "st_atime");
        stat.mtime = getLibcoreFileStatusLong(structStat, "st_mtime");
        stat.ctime = getLibcoreFileStatusLong(structStat, "st_ctime");
        return stat;
      } else {
        throw new IOException("Libcore.os.stat returned null");
      }
    } catch (Exception e) {
      // Can't chain exception because IOException doesn't have the right constructor on Froyo
      // and below
      throw new IOException("Failed to get FileStatus: " + e);
    }
  }

  private static int getFileStatusInt(Object obj, String field) throws IllegalArgumentException,
      IllegalAccessException, NoSuchFieldException, ClassNotFoundException {
    return Class.forName("android.os.FileUtils$FileStatus").getField(field).getInt(obj);
  }

  private static long getFileStatusLong(Object obj, String field) throws IllegalArgumentException,
      IllegalAccessException, NoSuchFieldException, ClassNotFoundException {
    return Class.forName("android.os.FileUtils$FileStatus").getField(field).getLong(obj);
  }

  private static int getLibcoreFileStatusInt(Object obj, String field)
      throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException,
      ClassNotFoundException {
    return Class.forName("libcore.io.StructStat").getField(field).getInt(obj);
  }

  private static long getLibcoreFileStatusLong(Object obj, String field)
      throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException,
      ClassNotFoundException {
    return Class.forName("libcore.io.StructStat").getField(field).getLong(obj);
  }

  /** Holds file extended information  */
  public static class StatStruct {
    public long dev;
    public long ino;
    public int mode;
    public long nlink;
    public int uid;
    public int gid;
    public long rdev;
    public long size;
    public long blksize;
    public long blocks;
    public long atime;
    public long mtime;
    public long ctime;

    @Override
    public String toString() {
      return new String(String.format("StatStruct{ dev: %d, ino: %d, mode: %o (octal), nlink: %d, "
          + "uid: %d, gid: %d, rdev: %d, size: %d, blksize: %d, blocks: %d, atime: %d, mtime: %d, "
          + "ctime: %d }\n",
          dev, ino, mode, nlink, uid, gid, rdev, size, blksize, blocks, atime, mtime, ctime));
    }
  }

  public static String getFilesystemInfoForErrorString(Context context) {
    String dataPackageDir = context.getApplicationInfo().dataDir;
    String databaseDirPathname = context.getDatabasePath(DATABASES_PATH).getParent();
    String databasePathname = context.getDatabasePath(DATABASES_PATH).getAbsolutePath();
    String[] dirsToStat = new String[] {dataPackageDir, databaseDirPathname, databasePathname};
    StringBuilder error = new StringBuilder();
    int myUid = Process.myUid();
    for (String directory : dirsToStat) {
      try {
        StatStruct stat = getStat(directory);
        String ownerUidName = null;
        try {
          if (stat.uid == 0) {
            ownerUidName = "root";
          } else {
            PackageManager packageManager = context.getPackageManager();
            ownerUidName = (packageManager != null) ? packageManager.getNameForUid(stat.uid) : null;
          }
        } catch (Exception e) {
          ownerUidName = e.toString();
        }
        error.append(directory + " directory stat (my UID: " + myUid);
        if (ownerUidName == null) {
          error.append("): ");
        } else {
          error.append(", dir owner UID name: " + ownerUidName + "): ");
        }
        error.append(stat.toString() + "\n");
      } catch (IOException e) {
        error.append(directory + " directory stat threw an exception: " + e + "\n");
      }
    }
    return error.toString();
  }

}
