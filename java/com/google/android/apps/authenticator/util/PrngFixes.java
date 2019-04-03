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

import android.os.Build;
import android.os.Process;
import android.os.StrictMode;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.SecureRandomSpi;
import java.security.Security;
import javax.annotation.concurrent.GuardedBy;

/**
 * Fixes for the output of the default PRNG having low entropy.
 *
 * <p>The fixes need to be applied via {@link #apply()} before any use of Java
 * Cryptography Architecture primitives. A good place to invoke them is in the
 * application's {@code onCreate}.
 *
 * <p>Adapted from
 * http://android-developers.blogspot.com/2013/08/some-securerandom-thoughts.html
 */
public final class PrngFixes {
  private static final String TAG = "PrngFixes";

  /** Hidden constructor to prevent instantiation. */
  private PrngFixes() {}

  /**
   * Applies best effort toward all fixes.
   */
  public static void apply(){
    try {
      applyOpenSSLFix();
    } catch (SecurityException e) {
      Log.w(TAG, "Failed to apply the fix for OpenSSL PRNG having low entropy", e);
    }
    try {
      installLinuxPRNGSecureRandom();
    } catch (SecurityException e) {
      Log.w(TAG, "Failed to install a Linux PRNG-backed SecureRandom impl as default", e);
    }
  }

  /**
   * Applies the fix for OpenSSL PRNG having low entropy. Does nothing if the
   * fix is not needed.
   *
   * @throws SecurityException if the fix is needed but could not be applied.
   */
  private static void applyOpenSSLFix() throws SecurityException {
    if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN)
        || (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2)) {
      // No need to apply the fix
      return;
    }

    try {
      // Mix in the device- and invocation-specific seed.
      Class.forName("org.apache.harmony.xnet.provider.jsse.NativeCrypto")
          .getMethod("RAND_seed", byte[].class)
          .invoke(null, generateSeed());

      // Mix output of Linux PRNG into OpenSSL's PRNG
      int bytesRead =
          (Integer)
              Class.forName("org.apache.harmony.xnet.provider.jsse.NativeCrypto")
                  .getMethod("RAND_load_file", String.class, long.class)
                  .invoke(null, "/dev/urandom", 1024);
      if (bytesRead != 1024) {
        throw new IOException("Unexpected number of bytes read from Linux PRNG: " + bytesRead);
      }
    } catch (Exception e) {
      throw new SecurityException("Failed to seed OpenSSL PRNG", e);
    }
  }

  /**
   * Installs a Linux PRNG-backed {@code SecureRandom} implementation as the
   * default. Does nothing if the implementation is already the default or if
   * there is not need to install the implementation.
   *
   * @throws SecurityException if the fix is needed but could not be applied.
   */
  private static void installLinuxPRNGSecureRandom() throws SecurityException {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
      // No need to apply the fix
      return;
    }

    // Install a Linux PRNG-based SecureRandom implementation as the
    // default, if not yet installed.
    Provider[] secureRandomProviders = Security.getProviders("SecureRandom.SHA1PRNG");
    if ((secureRandomProviders == null)
        || (secureRandomProviders.length < 1)
        || (!LinuxPRNGSecureRandomProvider.class.equals(secureRandomProviders[0].getClass()))) {
      Security.insertProviderAt(new LinuxPRNGSecureRandomProvider(), 1);
    }

    // Assert that new SecureRandom() and
    // SecureRandom.getInstance("SHA1PRNG") return a SecureRandom backed
    // by the Linux PRNG-based SecureRandom implementation.
    SecureRandom rng1 = new SecureRandom();
    if (!LinuxPRNGSecureRandomProvider.class.equals(rng1.getProvider().getClass())) {
      throw new SecurityException(
          "new SecureRandom() backed by wrong Provider: " + rng1.getProvider().getClass());
    }

    SecureRandom rng2;
    try {
      rng2 = SecureRandom.getInstance("SHA1PRNG");
    } catch (NoSuchAlgorithmException e) {
      throw new SecurityException("SHA1PRNG not available", e);
    }
    if (!LinuxPRNGSecureRandomProvider.class.equals(rng2.getProvider().getClass())) {
      throw new SecurityException(
          "SecureRandom.getInstance(\"SHA1PRNG\") backed by wrong"
              + " Provider: "
              + rng2.getProvider().getClass());
    }
  }

  /**
   * {@code Provider} of {@code SecureRandom} engines which pass through
   * all requests to the Linux PRNG.
   */
  private static class LinuxPRNGSecureRandomProvider extends Provider {

    public LinuxPRNGSecureRandomProvider() {
      super(
          "LinuxPRNG", 1.0, "A Linux-specific random number provider that uses" + " /dev/urandom");
      // Although /dev/urandom is not a SHA-1 PRNG, some apps
      // explicitly request a SHA1PRNG SecureRandom and we thus need to
      // prevent them from getting the default implementation whose output
      // may have low entropy.
      put("SecureRandom.SHA1PRNG", LinuxPRNGSecureRandom.class.getName());
      put("SecureRandom.SHA1PRNG ImplementedIn", "Software");
    }
  }

  /**
   * {@link SecureRandomSpi} which passes all requests to the Linux PRNG
   * ({@code /dev/urandom}). You should not use this class directly - instead
   * call {@link #apply}.
   */
  public static final class LinuxPRNGSecureRandom extends SecureRandomSpi {

    /*
     * IMPLEMENTATION NOTE: Requests to generate bytes and to mix in a seed
     * are passed through to the Linux PRNG (/dev/urandom). Instances of
     * this class seed themselves by mixing in the current time, PID, UID,
     * build fingerprint, and hardware serial number (where available) into
     * Linux PRNG.
     *
     * Concurrency: Read requests to the underlying Linux PRNG are
     * serialized (on sLock) to ensure that multiple threads do not get
     * duplicated PRNG output.
     */

    private static final File URANDOM_FILE = new File("/dev/urandom");

    private static final Object sLock = new Object();

    /**
     * Input stream for reading from Linux PRNG or {@code null} if not yet
     * opened.
     */
    @GuardedBy("sLock")
    private static DataInputStream sUrandomIn;

    /**
     * Output stream for writing to Linux PRNG or {@code null} if not yet
     * opened.
     */
    @GuardedBy("sLock")
    private static OutputStream sUrandomOut;

    /**
     * Whether this engine instance has been seeded. This is needed because
     * each instance needs to seed itself if the client does not explicitly
     * seed it.
     */
    private boolean mSeeded;

    @Override
    protected void engineSetSeed(byte[] bytes) {
      try {
        OutputStream out;
        synchronized (sLock) {
          out = getUrandomOutputStream();
        }
        out.write(bytes);
        out.flush();
      } catch (IOException e) {
        // On a small fraction of devices /dev/urandom is not writable, primarily the Galaxy S4
        // running 4.2.2. Log and ignore.
        Log.w(TAG, "Failed to mix seed into " + URANDOM_FILE);
      } finally {
        mSeeded = true;
      }
    }

    @Override
    protected void engineNextBytes(byte[] bytes) {
      // b/29254355 - urandom isn't a real file, it never blocks
      StrictMode.ThreadPolicy old = StrictMode.allowThreadDiskWrites();
      try {
        if (!mSeeded) {
          // Mix in the device- and invocation-specific seed.
          engineSetSeed(generateSeed());
        }
        DataInputStream in;
        synchronized (sLock) {
          in = getUrandomInputStream();
        }
        synchronized (in) {
          in.readFully(bytes);
        }
      } catch (IOException e) {
        throw new SecurityException("Failed to read from " + URANDOM_FILE, e);
      } finally {
        StrictMode.setThreadPolicy(old);
      }
    }

    @Override
    protected byte[] engineGenerateSeed(int size) {
      byte[] seed = new byte[size];
      engineNextBytes(seed);
      return seed;
    }

    private DataInputStream getUrandomInputStream() {
      synchronized (sLock) {
        if (sUrandomIn == null) {
          // NOTE: Consider inserting a BufferedInputStream between
          // DataInputStream and FileInputStream if you need higher
          // PRNG output performance and can live with future PRNG
          // output being pulled into this process prematurely.
          try {
            sUrandomIn = new DataInputStream(new FileInputStream(URANDOM_FILE));
          } catch (IOException e) {
            throw new SecurityException("Failed to open " + URANDOM_FILE + " for reading", e);
          }
        }
        return sUrandomIn;
      }
    }

    private OutputStream getUrandomOutputStream() throws IOException {
      synchronized (sLock) {
        if (sUrandomOut == null) {
          sUrandomOut = new FileOutputStream(URANDOM_FILE);
        }
        return sUrandomOut;
      }
    }
  }

  /**
   * Generates a device- and invocation-specific seed to be mixed into the
   * Linux PRNG.
   */
  private static byte[] generateSeed() {
    try {
      ByteArrayOutputStream seedBuffer = new ByteArrayOutputStream();
      DataOutputStream seedBufferOut = new DataOutputStream(seedBuffer);
      seedBufferOut.writeLong(java.lang.System.currentTimeMillis());
      seedBufferOut.writeLong(java.lang.System.nanoTime());
      seedBufferOut.writeInt(Process.myPid());
      seedBufferOut.writeInt(Process.myUid());
      seedBufferOut.write(getBuildFingerprintAndDeviceSerial());
      seedBufferOut.close();
      return seedBuffer.toByteArray();
    } catch (IOException e) {
      throw new SecurityException("Failed to generate seed", e);
    }
  }

  private static byte[] getBuildFingerprintAndDeviceSerial() {
    StringBuilder result = new StringBuilder();
    String fingerprint = Build.FINGERPRINT;
    if (fingerprint != null) {
      result.append(fingerprint);
    }
    String serial = Build.SERIAL;
    if (serial != null) {
      result.append(serial);
    }
    try {
      return result.toString().getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("UTF-8 encoding not supported");
    }
  }
}
