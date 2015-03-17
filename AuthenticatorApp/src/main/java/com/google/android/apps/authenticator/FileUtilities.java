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

  /*
   * Uses reflection to call android.os.FileUtils.getFileStatus(path) which returns FileStatus.
   */
  public static StatStruct getStat(String path) throws IOException {
    boolean success;

    try {
      Object obj = Class.forName("android.os.FileUtils$FileStatus").newInstance();
      success = (Boolean) Class.forName("android.os.FileUtils")
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

  public static class StatStruct {
    public int dev;
    public int ino;
    public int mode;
    public int nlink;
    public int uid;
    public int gid;
    public int rdev;
    public long size;
    public int blksize;
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

}
