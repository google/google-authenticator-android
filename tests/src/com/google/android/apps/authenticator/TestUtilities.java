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

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator.testability.StartActivityListener;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.test.InstrumentationTestCase;
import android.test.TouchUtils;
import android.test.ViewAsserts;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Spinner;

import junit.framework.Assert;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

  public static final String APP_PACKAGE_NAME = "com.google.android.apps.authenticator2";

  /**
   * Timeout (milliseconds) when waiting for the results of a UI action performed by the code
   * under test.
   */
  public static final int UI_ACTION_EFFECT_TIMEOUT_MILLIS = 5000;

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

  /**
   * Sets the text of the provided {@link EditText} widget on the UI thread.
   *
   * @return the resulting text of the widget.
   */
  public static String setText(Instrumentation instr, final EditText editText, final String text) {
    instr.runOnMainSync(new Runnable() {
      @Override
      public void run() {
        editText.setText(text);
      }
    });
    instr.waitForIdleSync();
    return editText.getText().toString();
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
  public static void tapPreference(InstrumentationTestCase instrumentationTestCase,
      PreferenceActivity activity, Preference preference) {
    // IMPLEMENTATION NOTE: There's no obvious way to find out which View corresponds to the
    // preference because the Preference list in the adapter is flattened, whereas the View
    // hierarchy in the ListView is not.
    // Thus, we go for the Reflection-based invocation of Preference.performClick() which is as
    // close to the invocation stack of a normal tap as it gets.

    // Only perform the click if the preference is in the adapter to catch cases where the
    // preference is not part of the PreferenceActivity for some reason.
    ListView listView = activity.getListView();
    ListAdapter listAdapter = listView.getAdapter();
    for (int i = 0, len = listAdapter.getCount(); i < len; i++) {
      if (listAdapter.getItem(i) == preference) {
        invokePreferencePerformClickOnMainThread(
            instrumentationTestCase.getInstrumentation(),
            preference,
            activity.getPreferenceScreen());
        return;
      }
    }
    throw new IllegalArgumentException("Preference " + preference + " not in list");
  }

  private static void invokePreferencePerformClickOnMainThread(
      Instrumentation instrumentation,
      final Preference preference,
      final PreferenceScreen preferenceScreen) {
    if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
      invokePreferencePerformClick(preference, preferenceScreen);
    } else {
      FutureTask<Void> task = new FutureTask<Void>(new Runnable() {
        @Override
        public void run() {
          invokePreferencePerformClick(preference, preferenceScreen);
        }
      }, null);
      instrumentation.runOnMainSync(task);
      try {
        task.get();
      } catch (Exception e) {
        throw new RuntimeException("Failed to click on preference on main thread", e);
      }
    }
  }

  private static void invokePreferencePerformClick(
      Preference preference, PreferenceScreen preferenceScreen) {
    try {
      Method performClickMethod =
          Preference.class.getDeclaredMethod("performClick", PreferenceScreen.class);
      performClickMethod.setAccessible(true);
      performClickMethod.invoke(preference, preferenceScreen);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException("Preference.performClickMethod method not found", e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException("Preference.performClickMethod failed", e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Failed to access Preference.performClickMethod", e);
    }
  }

  /**
   * Waits until the window which contains the provided view has focus.
   */
  public static void waitForWindowFocus(View view)
      throws InterruptedException, TimeoutException {
    long deadline = SystemClock.uptimeMillis() + UI_ACTION_EFFECT_TIMEOUT_MILLIS;
    while (!view.hasWindowFocus()) {
      long millisTillDeadline = deadline - SystemClock.uptimeMillis();
      if (millisTillDeadline < 0) {
        throw new TimeoutException("Timed out while waiting for window focus");
      }
      Thread.sleep(50);
    }
  }

  /**
   * Waits until the {@link Activity} is finishing.
   */
  public static void waitForActivityFinishing(Activity activity)
      throws InterruptedException, TimeoutException {
    long deadline = SystemClock.uptimeMillis() + UI_ACTION_EFFECT_TIMEOUT_MILLIS;
    while (!activity.isFinishing()) {
      long millisTillDeadline = deadline - SystemClock.uptimeMillis();
      if (millisTillDeadline < 0) {
        throw new TimeoutException("Timed out while waiting for activity to start finishing");
      }
      Thread.sleep(50);
    }
  }

  /**
   * Invokes the {@link Activity}'s {@code onBackPressed()} on the UI thread and blocks (with
   * a timeout) the calling thread until the invocation completes. If the calling thread is the UI
   * thread, the {@code finish} is invoked directly and without a timeout.
   */
  public static void invokeActivityOnBackPressedOnUiThread(final Activity activity)
      throws InterruptedException, TimeoutException {
    FutureTask<Void> finishTask = new FutureTask<Void>(new Runnable() {
      @Override
      public void run() {
        activity.onBackPressed();
      }
    }, null);
    activity.runOnUiThread(finishTask);
    try {
      finishTask.get(UI_ACTION_EFFECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    } catch (ExecutionException e) {
      throw new RuntimeException("Activity.onBackPressed() failed", e);
    }
  }

  /**
   * Invokes the {@link Activity}'s {@code finish()} on the UI thread and blocks (with
   * a timeout) the calling thread until the invocation completes. If the calling thread is the UI
   * thread, the {@code finish} is invoked directly and without a timeout.
   */
  public static void invokeFinishActivityOnUiThread(final Activity activity)
      throws InterruptedException, TimeoutException {
    FutureTask<Void> finishTask = new FutureTask<Void>(new Runnable() {
      @Override
      public void run() {
        activity.finish();
      }
    }, null);
    activity.runOnUiThread(finishTask);
    try {
      finishTask.get(UI_ACTION_EFFECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    } catch (ExecutionException e) {
      throw new RuntimeException("Activity.finish() failed", e);
    }
  }

  private static boolean isViewAndAllItsParentsVisible(View view) {
    if (view.getVisibility() != View.VISIBLE) {
      return false;
    }
    ViewParent parent = view.getParent();
    if (!(parent instanceof View)) {
      // This View is the root of the View hierarche, and it's visible (checked above)
      return true;
    }
    // This View itself is actually visible only all of its parents are visible.
    return isViewAndAllItsParentsVisible((View) parent);
  }

  private static boolean isViewOrAnyParentVisibilityGone(View view) {
    if (view.getVisibility() == View.GONE) {
      return true;
    }
    ViewParent parent = view.getParent();
    if (!(parent instanceof View)) {
      // This View is the root of the View hierarchy, and its visibility is not GONE (checked above)
      return false;
    }
    // This View itself is actually visible only all of its parents are visible.
    return isViewOrAnyParentVisibilityGone((View) parent);
  }

  /**
   * Asserts that the provided {@link View} and all its parents are visible.
   */
  public static void assertViewAndAllItsParentsVisible(View view) {
    Assert.assertTrue(isViewAndAllItsParentsVisible(view));
  }

  /**
   * Asserts that the provided {@link View} and all its parents are visible.
   */
  public static void assertViewOrAnyParentVisibilityGone(View view) {
    Assert.assertTrue(isViewOrAnyParentVisibilityGone(view));
  }

  /**
   * Asserts that the provided {@link View} is on the screen and is visible (which means its parent
   * and the parent of its parent and so forth are visible too).
   */
  public static void assertViewVisibleOnScreen(View view) {
    ViewAsserts.assertOnScreen(view.getRootView(), view);
    assertViewAndAllItsParentsVisible(view);
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

  /**
   * Asserts that the provided {@link Activity} displayed a dialog with the provided ID at some
   * point in the past. Note that this does not necessarily mean that the dialog is still being
   * displayed.
   *
   * <p>
   * <b>Note:</b> this method resets the "was displayed" state of the dialog. This means that a
   * consecutive invocation of this method for the same dialog ID will fail unless the dialog
   * was displayed again prior to the invocation of this method.
   */
  public static void assertDialogWasDisplayed(Activity activity, int dialogId) {
    // IMPLEMENTATION NOTE: This code below relies on the fact that, if a dialog with the ID was
    // every displayed, then dismissDialog will succeed, whereas if the dialog with the ID has
    // never been shown, then dismissDialog throws an IllegalArgumentException.
    try {
      activity.dismissDialog(dialogId);
      // Reset the "was displayed" state
      activity.removeDialog(dialogId);
    } catch (IllegalArgumentException e) {
      Assert.fail("No dialog with ID " + dialogId + " was ever displayed");
    }
  }

  /**
   * Taps the positive button of a currently displayed dialog. This method assumes that a button
   * of the dialog is currently selected.
   *
   * @see #tapDialogNegativeButton(InstrumentationTestCase)
   */
  public static void tapDialogNegativeButton(InstrumentationTestCase testCase) {
    // The order of the buttons is reversed from ICS onwards
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      testCase.sendKeys("DPAD_RIGHT DPAD_CENTER");
    } else {
      testCase.sendKeys("DPAD_LEFT DPAD_CENTER");
    }
  }

  /**
   * Taps the negative button of a currently displayed dialog. This method assumes that a button
   * of the dialog is currently selected.
   *
   * @see #tapDialogNegativeButton(InstrumentationTestCase)
   */
  public static void tapDialogPositiveButton(InstrumentationTestCase testCase) {
    // The order of the buttons is reversed from ICS onwards
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      testCase.sendKeys("DPAD_LEFT DPAD_CENTER");
    } else {
      testCase.sendKeys("DPAD_RIGHT DPAD_CENTER");
    }
  }

  /**
   * Taps the negative button of a currently displayed 3 button dialog. This method assumes
   * that a button of the dialog is currently selected.
   */
  public static void tapNegativeButtonIn3ButtonDialog(InstrumentationTestCase testCase) {
    // The order of the buttons is reversed from ICS onwards
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      testCase.sendKeys("DPAD_RIGHT DPAD_RIGHT DPAD_CENTER");
    } else {
      testCase.sendKeys("DPAD_LEFT DPAD_LEFT DPAD_CENTER");
    }
  }

  /**
   * Taps the neutral button of a currently displayed 3 button dialog. This method assumes
   * that a button of the dialog is currently selected.
   */
  public static void tapNeutralButtonIn3ButtonDialog(InstrumentationTestCase testCase) {
    // The order of the buttons is reversed from ICS onwards
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      testCase.sendKeys("DPAD_RIGHT DPAD_CENTER");
    } else {
      testCase.sendKeys("DPAD_RIGHT DPAD_CENTER");
    }
  }

  /**
   * Taps the positive button of a currently displayed 3 button dialog. This method assumes
   * that a button of the dialog is currently selected.
   */
  public static void tapPositiveButtonIn3ButtonDialog(InstrumentationTestCase testCase) {
    // The order of the buttons is reversed from ICS onwards
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      testCase.sendKeys("DPAD_LEFT DPAD_LEFT DPAD_CENTER");
    } else {
      testCase.sendKeys("DPAD_RIGHT DPAD_RIGHT DPAD_CENTER");
    }
  }

  /**
   * Configures the {@link DependencyInjector} with a {@link StartActivityListener} that prevents
   * activity launches.
   */
  public static void withLaunchPreventingStartActivityListenerInDependencyResolver() {
    StartActivityListener mockListener = Mockito.mock(StartActivityListener.class);
    doReturn(true).when(mockListener).onStartActivityInvoked(
        Mockito.<Context>anyObject(), Mockito.<Intent>anyObject());
    DependencyInjector.setStartActivityListener(mockListener);
  }

  /**
   * Verifies (with a timeout of {@link #UI_ACTION_EFFECT_TIMEOUT_MILLIS}) that an activity launch
   * has been attempted and returns the {@link Intent} with which the attempt occurred.
   *
   * <p><b>NOTE: This method assumes that the {@link DependencyInjector} was configured
   * using {@link #withLaunchPreventingStartActivityListenerInDependencyResolver()}.</b>
   */
  public static Intent verifyWithTimeoutThatStartActivityAttemptedExactlyOnce() {
    StartActivityListener mockListener = DependencyInjector.getStartActivityListener();
    ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
    verify(mockListener, timeout(UI_ACTION_EFFECT_TIMEOUT_MILLIS))
        .onStartActivityInvoked(Mockito.<Context>anyObject(), intentCaptor.capture());
    return intentCaptor.getValue();
  }

  public static void assertLessThanOrEquals(long expected, long actual) {
    if (actual > expected) {
      Assert.fail(actual + " > " + expected);
    }
  }

  /*
   * Returns the x and y coordinates of center of view in pixels.
   */
  public static Point getCenterOfViewOnScreen(
      InstrumentationTestCase instr, View view) {
    int[] location = new int[2];
    view.getLocationOnScreen(location);
    int width = view.getWidth();
    int height = view.getHeight();
    final int center_x = location[0] + width / 2;
    final int center_y = location[1] + height / 2;
    return new Point(center_x, center_y);
  }

  /*
   * returns the pixel value at the right side end of the view.
   */
  public static int getRightXofViewOnScreen(View view) {
    int[] location = new int[2];
    view.getLocationOnScreen(location);
    int width = view.getWidth();
    return location[0] + width;
  }

  /*
   * returns the pixel value at the left side end of the view.
   */
  public static int getLeftXofViewOnScreen(View view) {
    int[] location = new int[2];
    view.getLocationOnScreen(location);
    return location[0];
  }


  /*
   * Drags from the center of the view to the toX value.
   * This methods exists in TouchUtil, however, it has a bug which causes it to work
   * while dragging on the left side, but not on the right side, hence, we
   * had to recreate it here.
   */
  public static int dragViewToX(InstrumentationTestCase test, View v, int gravity, int toX) {

    if (gravity != Gravity.CENTER) {
      throw new IllegalArgumentException("Can only handle Gravity.CENTER.");
    }
    Point point = getCenterOfViewOnScreen(test, v);

    final int fromX = point.x;
    final int fromY = point.y;

    int deltaX = Math.abs(fromX - toX);

    TouchUtils.drag(test, fromX, toX, fromY, fromY, deltaX);

    return deltaX;
  }

}
