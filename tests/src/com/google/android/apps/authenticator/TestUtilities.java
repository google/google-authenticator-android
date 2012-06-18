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

import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.test.InstrumentationTestCase;
import android.test.TouchUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Spinner;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/**
 * A class that offers various utility methods for writing tests.
 *
 * @author sarvar@google.com (Sarvar Patel)
 */
public class TestUtilities {

  private TestUtilities() { }

  public static void clickView(Instrumentation instr, final View view) {
    instr.runOnMainSync(new Runnable() {
      @Override
      public void run() {
        view.performClick();
      }
    });
    // this shouldn't be needed but without it or sleep, there isn't time for view refresh, etc.
    instr.waitForIdleSync();
  }

  public static void longClickView(Instrumentation instr, final View view) {
    instr.runOnMainSync(new Runnable() {
      @Override
      public void run() {
        view.performLongClick();
      }
    });
    instr.waitForIdleSync();
  }

  /**
   * Selects the item at the requested position in the Spinner.
   *
   * @return the selected item as string.
   */
  public static String selectSpinnerItem(
      Instrumentation instr, final Spinner spinner, final int position) {
    instr.runOnMainSync(new Runnable() {
      @Override
      public void run() {
        spinner.setSelection(position);
      }
    });
    instr.waitForIdleSync();
    return spinner.getSelectedItem().toString();
  }

  /*
   * Sends a string to a EditText box.
   *
   * @return the resulting string read from the editText - this should equal text.
   */
  public static String enterText(
      Instrumentation instr, final EditText editText, final String text) {
    instr.runOnMainSync(new Runnable() {
      @Override
      public void run() {
        editText.requestFocus();
      }
    });
    // TODO(sarvar): decide on using touch mode and how to do it consistently. e.g.,
    //               the above could be replaced by "TouchUtils.tapView(this, editText);"
    instr.sendStringSync(text);
    return editText.getText().toString();
  }

  /**
   * Taps the specified preference displayed by the provided Activity.
   */
  public static void tapPreference(
      InstrumentationTestCase instrumentationTestCase,
      PreferenceActivity activity,
      Preference preference) {
    ListView listView = activity.getListView();
    ListAdapter listAdapter = listView.getAdapter();
    for (int i = 0, len = listAdapter.getCount(); i < len; i++) {
      if (listAdapter.getItem(i) == preference) {
        View itemView = listView.getChildAt(listView.getHeaderViewsCount() + i);
        TouchUtils.tapView(instrumentationTestCase, itemView);
        return;
      }
    }
    throw new IllegalArgumentException("Preference " + preference + " not in list");
  }

  /*
   * Sends a space separated key sequence to a EditText box using
   * {@link InstrumentationTestCase#sendKeys()}.
   *
   * @param instrumentationTestCase the test case instance to use. We need this, unlike the other
   *        methods which use instrumentation, because sending a space separated keys sequence
   *        is not supported by any methods in Instrumentation.
   * @param editText EditText where the space separated keys sequence should be entered.
   * @param keysSequence string of space separated keys sequence to enter in the editText.
   * @return the resulting string read from the editText - this should equal keysSequence.
   */
  public static String enterKeysSequence(InstrumentationTestCase instrumentationTestCase,
      final EditText editText, final String keysSequence) {
    instrumentationTestCase.getInstrumentation().runOnMainSync(new Runnable() {
      @Override
      public void run() {
        editText.requestFocus();
      }
    });
    instrumentationTestCase.sendKeys(keysSequence);
    instrumentationTestCase.getInstrumentation().waitForIdleSync();
    return editText.getText().toString();
  }

  /**
   * Waits until the window which contains the provided view has focus.
   */
  public static void waitForWindowFocus(View view, long timeoutMillis)
      throws InterruptedException, TimeoutException {
    long deadline = SystemClock.uptimeMillis() + timeoutMillis;
    while (!view.hasWindowFocus()) {
      long millisTillDeadline = deadline - SystemClock.uptimeMillis();
      if (millisTillDeadline < 0) {
        throw new TimeoutException("Timed out while waiting for window focus");
      }
      Thread.sleep(50);
    }
  }

  /**
   * Invokes the {@link Activity}'s {@code finish()} on the UI thread and blocks (with
   * a timeout) the calling thread until the invocation completes. If the calling thread is the UI
   * thread, the {@code finish} is invoked directly and without a timeout.
   */
  public static void invokeFinishActivityOnUiThread(final Activity activity, long timeoutMillis)
      throws InterruptedException, TimeoutException {
    FutureTask<Void> finishTask = new FutureTask<Void>(new Runnable() {
      @Override
      public void run() {
        activity.finish();
      }
    }, null);
    activity.runOnUiThread(finishTask);
    try {
      finishTask.get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (ExecutionException e) {
      throw new RuntimeException("Activity.finish() failed", e);
    }
  }

  /**
   * Opens the options menu of the provided {@link Activity} and invokes the menu item with the
   * provided ID.
   *
   * Note: This method cannot be invoked on the main thread.
   */
  public static void openOptionsMenuAndInvokeItem(
      Instrumentation instrumentation, final Activity activity, final int itemId) {
    if (!instrumentation.invokeMenuActionSync(activity, itemId, 0)) {
      throw new RuntimeException("Failed to invoke options menu item ID " + itemId);
    }
    instrumentation.waitForIdleSync();
  }

  /**
   * Opens the context menu for the provided {@link View} and invokes the menu item with the
   * provided ID.
   *
   * Note: This method cannot be invoked on the main thread.
   */
  public static void openContextMenuAndInvokeItem(
      Instrumentation instrumentation, final Activity activity, final View view, final int itemId) {
    // IMPLEMENTATION NOTE: Instrumentation.invokeContextMenuAction would've been much simpler, but
    // it doesn't work on ICS because its KEY_UP event times out.
    FutureTask<Boolean> task = new FutureTask<Boolean>(new Callable<Boolean>() {
      @Override
      public Boolean call() {
        // Use performLongClick instead of showContextMenu to exercise more of the code path that
        // is invoked when the user normally opens a context menu.
        view.performLongClick();
        return activity.getWindow().performContextMenuIdentifierAction(itemId, 0);
      }
    });
    instrumentation.runOnMainSync(task);
    try {
      if (!task.get()) {
        throw new RuntimeException("Failed to invoke context menu item with ID " + itemId);
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to open context menu and select a menu item", e);
    }
    instrumentation.waitForIdleSync();
  }
}

