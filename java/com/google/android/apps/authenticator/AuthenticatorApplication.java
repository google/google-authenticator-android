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

package com.google.android.apps.authenticator;

import android.net.http.HttpResponseCache;
import android.support.multidex.MultiDexApplication;
import android.util.Log;
import com.google.android.apps.authenticator.testability.DaggerInjector;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator.util.FileUtilities;
import com.google.android.apps.authenticator.util.PrngFixes;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Authenticator application which is one of the first things instantiated when our process starts.
 *
 * <p>At the moment the only reason for the existence of this class is to initialize
 * {@link DependencyInjector} with the application context so that the class can (later) instantiate
 * the various objects it owns.
 *
 * <p>Also restricts UNIX file permissions on application's persistent data directory to owner
 * (this app's UID) only.
 */
public class AuthenticatorApplication extends MultiDexApplication {

  private static final String TAG = AuthenticatorApplication.class.getSimpleName();
  private static final long CACHE_SIZE = 1 * 1024 * 1024; // 1 MiB;
  private static final int GET_BARCODE_DETECTOR_TIMEOUT_MS = 20000; // 20,000 milliseconds

  @Override
  public void onCreate() {
    super.onCreate();

    PrngFixes.apply();
    // Try to restrict data dir file permissions to owner (this app's UID) only. This mitigates the
    // security vulnerability where SQLite database transaction journals are world-readable.
    // See CVE-2011-3901 advisory for more information.
    // NOTE: This also prevents all files in the data dir from being world-accessible, which is fine
    // because this application does not need world-accessible files.
    try {
      FileUtilities.restrictAccessToOwnerOnly(
          getApplicationContext().getApplicationInfo().dataDir);
    } catch (Throwable e) {
      // Ignore this exception and don't log anything to avoid attracting attention to this fix
    }

    installCache();

    // During test runs the injector may have been configured already. Thus we take care to avoid
    // overwriting any existing configuration here.
    DependencyInjector.configureForProductionIfNotConfigured(getApplicationContext());

    initDagger();
    initBarcodeDetector();
  }

  @Override
  public void onTerminate() {
    DependencyInjector.close();

    super.onTerminate();
  }

  protected void initDagger() {
    DaggerInjector.init(new AuthenticatorModule(this));
  }

  /**
   * Asynchronously create the barcode detector. Warms it up so first use is faster.
   */
  private void initBarcodeDetector() {
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    executorService.execute(barcodeDetectorFutureTask);
  }

  /**
   * Get the cached barcode detector. If the barcode detector is not available yet, we build and
   * catch it, the building takes about ~1000ms.
   */
  public BarcodeDetector getBarcodeDetector() {
    try {
      return barcodeDetectorFutureTask.get(GET_BARCODE_DETECTOR_TIMEOUT_MS,
          TimeUnit.MILLISECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Building the barcode detector is an expensive operation (~1000ms), so we are building it in
   * another thread and cached it. This barcode detector only detects QR code format.
   */
  private final FutureTask<BarcodeDetector> barcodeDetectorFutureTask =
      new FutureTask<>(
          new Callable<BarcodeDetector>() {
            @Override
            public BarcodeDetector call() {
              return new BarcodeDetector.Builder(getApplicationContext())
                  .setBarcodeFormats(Barcode.QR_CODE)
                  .build();
            }
          });

  /**
   * Installs a cache for fetched URLs
   */
  private void installCache() {
    HttpResponseCache cache = HttpResponseCache.getInstalled();
    if (cache == null) {
      try {
        File httpCacheDir = new File(getApplicationContext().getCacheDir(), "http");
        HttpResponseCache.install(httpCacheDir, CACHE_SIZE);
      } catch (IOException e) {
        Log.w(TAG, "HTTP response cache installation failed:", e);
      }
    }
  }
}
