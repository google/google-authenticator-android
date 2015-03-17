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

package com.google.android.apps.authenticator.dataimport;

import com.google.android.apps.authenticator.dataexport.IExportServiceV2;
import com.google.android.apps.authenticator.testability.DependencyInjector;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

/**
 * @author klyubin@google.com (Alex Klyubin)
 */
public class ExportServiceBasedImportController implements ImportController {

  private static final String OLD_APP_PACKAGE_NAME = "com.google.android.apps.authenticator";
  private static final String OLD_APP_EXPORT_SERVICE_CLASS_NAME =
      OLD_APP_PACKAGE_NAME + ".dataexport.ExportServiceV2";

  private static final String LOG_TAG = "ImportController";

  public ExportServiceBasedImportController() {}

  @Override
  public void start(Context context, Listener listener) {
    int oldAppVersionCode = getOldAppVersionCode();
    if (oldAppVersionCode == -1) {
      Log.d(LOG_TAG, "Skipping importing because the old app is not installed");
      notifyListenerFinished(listener);
      return;
    }

    Intent intent = new Intent();
    intent.setComponent(new ComponentName(OLD_APP_PACKAGE_NAME, OLD_APP_EXPORT_SERVICE_CLASS_NAME));
    ServiceConnection serviceConnection = new ExportServiceConnection(context, listener);
    boolean bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    if (!bound) {
      Log.i(LOG_TAG, "Not importing because the old app is too old (" + oldAppVersionCode
          + ") and can't export");
      context.unbindService(serviceConnection);
      notifyListenerFinished(listener);
      return;
    }

    // The flow continues in ExportServiceConnection.onServiceConnected which is invoked
    // later on by the OS once the connection with the ExportService has been established.
  }

  private class ExportServiceConnection implements ServiceConnection {
    private final Context mContext;
    private final Listener mListener;

    private ExportServiceConnection(Context context, Listener listener) {
      mContext = context;
      mListener = listener;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      try {
        IExportServiceV2 exportService;
        try {
          exportService = IExportServiceV2.Stub.asInterface(service);
        } catch (SecurityException e) {
          Log.w(LOG_TAG, "Failed to obtain export interface: " + e);
          return;
        }

        Bundle importedData;
        try {
          importedData = exportService.getData();
        } catch (SecurityException e) {
          Log.w(LOG_TAG, "Failed to obtain data: " + e);
          return;
        } catch (RemoteException e) {
          Log.w(LOG_TAG, "Failed to obtain data: " + e);
          return;
        }

        if (importedData != null) {
          new Importer().importFromBundle(
              importedData,
              DependencyInjector.getAccountDb(),
              DependencyInjector.getOptionalFeatures()
                  .getSharedPreferencesForDataImportFromOldApp(mContext));
          Log.i(LOG_TAG, "Successfully imported data from the old app");
          notifyListenerDataImported(mListener);
          try {
            exportService.onImportSucceeded();
          } catch (SecurityException e) {
            Log.w(LOG_TAG, "Failed to notify old app that import succeeded: " + e);
            return;
          } catch (RemoteException e) {
            Log.w(LOG_TAG, "Failed to notify old app that import succeeded: " + e);
            return;
          }
          notifyListenerUninstallOldAppSuggested(mListener);
        } else {
          Log.w(LOG_TAG, "Old app returned null data");
        }
      } finally {
        // The try-catch below is to avoid crashing when the unbind operation fails. Occasionally
        // the operation throws an IllegalArgumentException because the connection has been closed
        // by the OS/framework.
        try {
          mContext.unbindService(this);
        } catch (Exception e) {
          Log.w(LOG_TAG, "Failed to unbind service", e);
        } finally {
          notifyListenerFinished(mListener);
        }
      }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {}
  }

  /**
   * Gets the version code of the old app.
   *
   * @return version code or {@code -1} if the old app is not installed.
   */
  private static int getOldAppVersionCode() {
    try {
      PackageInfo oldAppPackageInfo = DependencyInjector.getPackageManager().getPackageInfo(
          OLD_APP_PACKAGE_NAME, 0);
      return oldAppPackageInfo.versionCode;
    } catch (PackageManager.NameNotFoundException e) {
      return -1;
    }
  }

  private static void notifyListenerDataImported(Listener listener) {
    if (listener != null) {
      listener.onDataImported();
    }
  }

  private static void notifyListenerUninstallOldAppSuggested(Listener listener) {
    if (listener != null) {
      listener.onOldAppUninstallSuggested(
          new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + OLD_APP_PACKAGE_NAME)));
    }
  }

  private static void notifyListenerFinished(Listener listener) {
    if (listener != null) {
      listener.onFinished();
    }
  }
}
