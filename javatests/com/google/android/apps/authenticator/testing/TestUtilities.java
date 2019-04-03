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

package com.google.android.apps.authenticator.testing;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.pressBack;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.test.espresso.action.GeneralSwipeAction;
import androidx.test.espresso.action.Press;
import androidx.test.espresso.action.Swipe;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator.testability.StartActivityListener;
import com.google.android.apps.authenticator.testability.StartServiceListener;
import com.google.android.apps.authenticator.util.annotations.FixWhenMinSdkVersion;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import junit.framework.Assert;
import junit.framework.AssertionFailedError;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * A class that offers various utility methods for writing tests.
 */
// TODO: Move utilities for tests into the java/ tree.
public class TestUtilities {

  public static final String APP_PACKAGE_NAME = "com.google.android.apps.authenticator2";

  /**
   * Timeout (milliseconds) when waiting for the results of a UI action performed by the code under
   * test.
   */
  public static final int UI_ACTION_EFFECT_TIMEOUT_MILLIS = 5000;

  private TestUtilities() {}

  public static boolean clickView(Instrumentation instr, final View view) {
    boolean result =
        runOnMainSyncWithTimeout(
            new Callable<Boolean>() {
              @Override
              public Boolean call() {
                return view.performClick();
              }
            });
    // this shouldn't be needed but without it or sleep, there isn't time for view refresh, etc.
    instr.waitForIdleSync();
    return result;
  }

  public static boolean longClickView(Instrumentation instr, final View view) {
    boolean result =
        runOnMainSyncWithTimeout(
            new Callable<Boolean>() {
              @Override
              public Boolean call() {
                return view.performLongClick();
              }
            });
    instr.waitForIdleSync();
    return result;
  }

  /**
   * Performs a click/tap on a list item at the specified position.
   *
   * @return {@code true} if the click/tap was consumed, {@code false} otherwise.
   */
  public static boolean clickListViewItem(ListView listView, int position) {
    try {
      // Here we assume that accidental long-press can usually be undone by pressing back
      onView(is(listView.getChildAt(position))).perform(click(pressBack()));
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /** Sets the text of the provided {@link TextView} widget on the UI thread. */
  public static void setText(Instrumentation instr, final TextView view, final String text) {
    runOnMainSyncWithTimeout(
        new Callable<Void>() {
          @Override
          public Void call() {
            view.setText(text);
            return null;
          }
        });
    instr.waitForIdleSync();
    Assert.assertEquals(text, view.getText().toString());
  }

  /*
   * Sends a string to a EditText box.
   *
   * @return the resulting string read from the editText - this should equal text.
   */
  public static String enterText(
      Instrumentation instr, final EditText editText, final String text) {
    runOnMainSyncWithTimeout(
        new Callable<Void>() {
          @Override
          public Void call() {
            editText.requestFocus();
            return null;
          }
        });
    // TODO: Decide on using touch mode and how to do it consistently. e.g. the above could be
    // replaced by "TouchUtils.tapView(this, editText);"
    instr.sendStringSync(text);
    return editText.getText().toString();
  }

  /** Taps the specified preference displayed by the provided Activity. */
  @FixWhenMinSdkVersion(11)
  @SuppressWarnings("deprecation")
  public static void tapPreference(PreferenceActivity activity, Preference preference) {
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
        invokePreferencePerformClickOnMainThread(preference, activity.getPreferenceScreen());
        return;
      }
    }
    throw new IllegalArgumentException("Preference " + preference + " not in list");
  }

  private static void invokePreferencePerformClickOnMainThread(
      final Preference preference, final PreferenceScreen preferenceScreen) {
    runOnMainSyncWithTimeout(
        new Callable<Void>() {
          @Override
          public Void call() {
            invokePreferencePerformClick(preference, preferenceScreen);
            return null;
          }
        });
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

  /** Waits until the window which contains the provided view has focus. */
  public static void waitForWindowFocus(View view) throws InterruptedException, TimeoutException {
    long deadline = SystemClock.uptimeMillis() + UI_ACTION_EFFECT_TIMEOUT_MILLIS;
    while (!view.hasWindowFocus()) {
      long millisTillDeadline = deadline - SystemClock.uptimeMillis();
      if (millisTillDeadline < 0) {
        throw new TimeoutException("Timed out while waiting for window focus");
      }
      Thread.sleep(50);
    }
  }

  /** Waits until the {@link Activity} is finishing. */
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
   * Invokes the {@link Activity}'s {@code onBackPressed()} on the UI thread and blocks (with a
   * timeout) the calling thread until the invocation completes. If the calling thread is the UI
   * thread, the {@code finish} is invoked directly and without a timeout.
   */
  public static void invokeActivityOnBackPressedOnUiThread(final Activity activity) {
    runOnMainSyncWithTimeout(
        new Callable<Void>() {
          @Override
          public Void call() {
            activity.onBackPressed();
            return null;
          }
        });
  }

  /**
   * Invokes the {@link Activity}'s {@code finish()} on the UI thread and blocks (with a timeout)
   * the calling thread until the invocation completes. If the calling thread is the UI thread, the
   * {@code finish} is invoked directly and without a timeout.
   */
  public static void invokeFinishActivityOnUiThread(final Activity activity) {
    runOnMainSyncWithTimeout(
        new Callable<Void>() {
          @Override
          public Void call() {
            activity.finish();
            return null;
          }
        });
  }

  /**
   * Invokes the {@link Activity}'s {@code onActivityResult} on the UI thread and blocks (with a
   * timeout) the calling thread until the invocation completes. If the calling thread is the UI
   * thread, the {@code finish} is invoked directly and without a timeout.
   */
  public static void invokeOnActivityResultOnUiThread(
      final Activity activity, final int requestCode, final int resultCode, final Intent intent) {
    runOnMainSyncWithTimeout(
        new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            // The method has to be invoked via Reflection because it's protected rather than
            // public.
            Method method =
                Activity.class.getDeclaredMethod(
                    "onActivityResult", int.class, int.class, Intent.class);
            method.setAccessible(true);
            method.invoke(activity, requestCode, resultCode, intent);
            return null;
          }
        });
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

  /** Asserts that the provided {@link View} and all its parents are visible. */
  public static void assertViewAndAllItsParentsVisible(View view) {
    Assert.assertTrue(isViewAndAllItsParentsVisible(view));
  }

  /** Asserts that the provided {@link View} and all its parents are visible. */
  public static void assertViewOrAnyParentVisibilityGone(View view) {
    Assert.assertTrue(isViewOrAnyParentVisibilityGone(view));
  }

  /**
   * Asserts that the provided {@link View} is on the screen and is visible (which means its parent
   * and the parent of its parent and so forth are visible too).
   */
  public static void assertViewVisibleOnScreen(View view) {
    onView(equalTo(view)).check(matches(isDisplayed()));
    assertViewAndAllItsParentsVisible(view);
  }

  /**
   * Opens the options menu of the provided {@link Activity} and invokes the menu item with the
   * provided ID.
   *
   * <p>Note: This method cannot be invoked on the main thread.
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
   * <p>Note: This method cannot be invoked on the main thread.
   */
  public static void openContextMenuAndInvokeItem(
      Instrumentation instrumentation, final Activity activity, final View view, final int itemId) {
    // IMPLEMENTATION NOTE: Instrumentation.invokeContextMenuAction would've been much simpler, but
    // it requires the View to be focused which is hard to achieve in touch mode.
    runOnMainSyncWithTimeout(
        new Callable<Void>() {
          @Override
          public Void call() {
            // Use performLongClick instead of showContextMenu to exercise more of the code path
            // that
            // is invoked when the user normally opens a context menu.
            if (!view.performLongClick()) {
              throw new RuntimeException("Failed to perform long click");
            }
            if (!activity.getWindow().performContextMenuIdentifierAction(itemId, 0)) {
              throw new RuntimeException("Failed perform to context menu action");
            }
            return null;
          }
        });
    instrumentation.waitForIdleSync();
  }

  /**
   * Asserts that the provided {@link Activity} displayed a dialog with the provided ID at some
   * point in the past. Note that this does not necessarily mean that the dialog is still being
   * displayed.
   *
   * <p><b>Note:</b> this method resets the "was displayed" state of the dialog. This means that a
   * consecutive invocation of this method for the same dialog ID will fail unless the dialog was
   * displayed again prior to the invocation of this method.
   */
  @SuppressWarnings("deprecation") // TODO: fix by using a fragment instead
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

  /** Asserts that the provided {@link Activity} has not displayed a dialog with the provided ID. */
  @SuppressWarnings("deprecation") // TODO: fix by using a fragment instead
  public static void assertDialogWasNotDisplayed(Activity activity, int dialogId) {
    // IMPLEMENTATION NOTE: This code below relies on the fact that, if a dialog with the ID was
    // every displayed, then dismissDialog will succeed, whereas if the dialog with the ID has
    // never been shown, then dismissDialog throws an IllegalArgumentException.
    try {
      activity.dismissDialog(dialogId);
      Assert.fail("Dialog with ID " + dialogId + " was displayed");
    } catch (IllegalArgumentException expected) {
      // Expected
    }
  }

  /**
   * Taps the negative button of a currently displayed dialog. This method assumes that a button of
   * the dialog is currently selected.
   *
   * @see #tapDialogPositiveButton(Instrumentation)
   */
  public static void tapDialogNegativeButton(Instrumentation instrumentation) {
    selectDialogButton(instrumentation);

    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);
    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
  }

  /**
   * Taps the positive button of a currently displayed dialog. This method assumes that a button of
   * the dialog is currently selected.
   *
   * @see #tapDialogNegativeButton(Instrumentation)
   */
  @FixWhenMinSdkVersion(14)
  public static void tapDialogPositiveButton(Instrumentation instrumentation) {
    selectDialogButton(instrumentation);

    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
  }

  /**
   * Selects a button at the bottom of a dialog. This assumes that a dialog is currently displayed
   * in the foreground.
   */
  private static void selectDialogButton(Instrumentation instrumentation) {
    // If the dialog contains too much text it will scroll and the buttons at the bottom will only
    // get selected once it scrolls to the very bottom.
    // So far, 6 x DPAD_DOWN seems to do the trick for our app...
    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
  }

  /**
   * Taps the negative button of a currently displayed 3 button dialog. This method assumes that a
   * button of the dialog is currently selected.
   */
  public static void tapNegativeButtonIn3ButtonDialog(Instrumentation instrumentation) {
    selectDialogButton(instrumentation);

    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);
    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);
    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
  }

  /**
   * Taps the neutral button of a currently displayed 3 button dialog. This method assumes that a
   * button of the dialog is currently selected.
   */
  public static void tapNeutralButtonIn3ButtonDialog(Instrumentation instrumentation) {
    selectDialogButton(instrumentation);

    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
  }

  /**
   * Taps the positive button of a currently displayed 3 button dialog. This method assumes that a
   * button of the dialog is currently selected.
   */
  public static void tapPositiveButtonIn3ButtonDialog(Instrumentation instrumentation) {
    selectDialogButton(instrumentation);

    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
  }

  /**
   * Configures the {@link DependencyInjector} with a {@link StartActivityListener} that prevents
   * activity launches.
   */
  public static void withLaunchPreventingStartActivityListenerInDependencyResolver() {
    StartActivityListener mockListener = Mockito.mock(StartActivityListener.class);
    doReturn(true)
        .when(mockListener)
        .onStartActivityInvoked(Mockito.anyObject(), Mockito.anyObject());
    DependencyInjector.setStartActivityListener(mockListener);
  }

  /**
   * Configures the {@link DependencyInjector} with a {@link StartServiceListener} that prevents
   * service launches.
   */
  public static void withLaunchPreventingStartServiceListenerInDependencyResolver() {
    StartServiceListener mockListener = Mockito.mock(StartServiceListener.class);
    doReturn(true)
        .when(mockListener)
        .onStartServiceInvoked(Mockito.anyObject(), Mockito.anyObject());
    DependencyInjector.setStartServiceListener(mockListener);
  }

  /**
   * Verifies (with a timeout of {@link #UI_ACTION_EFFECT_TIMEOUT_MILLIS}) that an activity launch
   * has been attempted and returns the {@link Intent} with which the attempt occurred.
   *
   * <p><b>NOTE: This method assumes that the {@link DependencyInjector} was configured using {@link
   * #withLaunchPreventingStartActivityListenerInDependencyResolver()}.</b>
   */
  public static Intent verifyWithTimeoutThatStartActivityAttemptedExactlyOnce() {
    StartActivityListener mockListener = DependencyInjector.getStartActivityListener();
    ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
    verify(mockListener, timeout(UI_ACTION_EFFECT_TIMEOUT_MILLIS))
        .onStartActivityInvoked(Mockito.anyObject(), intentCaptor.capture());
    return intentCaptor.getValue();
  }

  public static void assertLessThanOrEquals(long expected, long actual) {
    if (actual > expected) {
      Assert.fail(actual + " > " + expected);
    }
  }

  public static void assertMoreThanOrEquals(long expected, long actual) {
    if (actual < expected) {
      Assert.fail(actual + " < " + expected);
    }
  }

  /*
   * Returns the x and y coordinates of center of view in pixels.
   */
  public static Point getCenterOfViewOnScreen(View view) {
    int[] location = new int[2];
    view.getLocationOnScreen(location);
    int centerX = location[0] + view.getWidth() / 2;
    int centerY = location[1] + view.getHeight() / 2;
    return new Point(centerX, centerY);
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
  public static int dragViewToX(View v, int gravity, int toX) {

    if (gravity != Gravity.CENTER) {
      throw new IllegalArgumentException("Can only handle Gravity.CENTER.");
    }
    Point point = getCenterOfViewOnScreen(v);

    final int fromX = point.x;
    final int fromY = point.y;

    int deltaX = Math.abs(fromX - toX);

    onView(equalTo(v))
        .perform(
            new GeneralSwipeAction(
                Swipe.SLOW,
                (view) -> {
                  return new float[] {fromX, fromY};
                },
                (view) -> {
                  return new float[] {toX, fromY};
                },
                Press.FINGER));

    return deltaX;
  }

  /**
   * Finds an {@link Intent} whose component name points to the specified class.
   *
   * @return first matching {@code Intent} or {@code null} if no match found.
   */
  public static Intent findIntentByComponentClass(Collection<Intent> intents, Class<?> cls) {
    for (Intent intent : intents) {
      ComponentName componentName = intent.getComponent();
      if ((componentName != null) && (cls.getName().equals(componentName.getClassName()))) {
        return intent;
      }
    }
    return null;
  }

  private static Object jsonValueToJavaValue(Object value) throws JSONException {
    if ((value == null) || (value == JSONObject.NULL)) {
      return null;
    } else if (value instanceof JSONObject) {
      return jsonObjectToMap((JSONObject) value);
    } else if (value instanceof JSONArray) {
      return jsonArrayToList((JSONArray) value);
    } else {
      return value;
    }
  }

  private static Map<String, Object> jsonObjectToMap(JSONObject jsonObject) throws JSONException {
    Map<String, Object> result = Maps.newHashMap();
    JSONArray names = jsonObject.names();
    for (int i = 0, len = ((names != null) ? names.length() : 0); i < len; i++) {
      String name = names.getString(i);
      Object value = jsonObject.get(name);
      result.put(name, jsonValueToJavaValue(value));
    }
    return result;
  }

  private static List<Object> jsonArrayToList(JSONArray jsonArray) throws JSONException {
    List<Object> result = Lists.newArrayList();
    for (int i = 0, len = (jsonArray != null) ? jsonArray.length() : 0; i < len; i++) {
      result.add(jsonValueToJavaValue(jsonArray.get(i)));
    }
    return result;
  }

  /** Gets the result code that the provided {@link Activity} is currently set to return. */
  public static int getActivityResultCode(Activity activity) {
    // We need to use Reflection because the code is stored in the resultCode field with package
    // private visibility.
    try {
      Field field = Activity.class.getDeclaredField("mResultCode");
      field.setAccessible(true);
      // The field is guarded (for concurrency) by the Activity instance.
      synchronized (activity) {
        return field.getInt(activity);
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to get Acitivity.mResultCode for " + activity, e);
    }
  }

  /** Gets the result data that the provided {@link Activity} is currently set to return. */
  public static Intent getActivityResultData(Activity activity) {
    // We need to use Reflection because the data is stored in the resultData field with package
    // private visibility.
    try {
      Field field = Activity.class.getDeclaredField("mResultData");
      field.setAccessible(true);
      // The field is guarded (for concurrency) by the Activity instance.
      synchronized (activity) {
        return (Intent) field.get(activity);
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to get Acitivity.mResultData for " + activity, e);
    }
  }

  /** Asserts that the actual value is in the expected range (inclusive). */
  public static void assertInRangeInclusive(
      long actual, long expectedMinValue, long expectedMaxValue) {
    if ((actual < expectedMinValue) || (actual > expectedMaxValue)) {
      Assert.fail(actual + " not in [" + expectedMinValue + ", " + expectedMaxValue + "]");
    }
  }

  /**
   * Invokes the provided {@link Callable} on the main thread and blocks until the operation
   * completes or times out. If this method is invoked on the main thread, the {@code Callable} is
   * invoked immediately and no timeout is enforced.
   *
   * <p>Exceptions thrown by the {@code Callable} are rethrown by this method. Checked exceptions
   * are rethrown as unchecked exceptions.
   *
   * @return result returned by the {@code Callable}.
   */
  public static <V> V runOnMainSyncWithTimeout(Callable<V> callable) {
    try {
      return runOnMainSyncWithTimeoutAndWithCheckedExceptionsExpected(callable);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Unexpected checked exception", e);
    }
  }

  /**
   * Invokes the provided {@link Callable} on the main thread and blocks until the operation
   * completes or times out. If this method is invoked on the main thread, the {@code Callable} is
   * invoked immediately and no timeout is enforced.
   *
   * <p>Exceptions thrown by the {@code Callable} are rethrown by this method.
   *
   * @return result returned by the {@code Callable}.
   */
  private static <V> V runOnMainSyncWithTimeoutAndWithCheckedExceptionsExpected(
      Callable<V> callable) throws Exception {
    Looper mainLooper = Looper.getMainLooper();
    if (mainLooper.getThread() == Thread.currentThread()) {
      // This method is being invoked on the main thread -- invoke the Callable inline to avoid
      // a deadlock.
      return callable.call();
    } else {
      FutureTask<V> task = new FutureTask<V>(callable);
      new Handler(mainLooper).post(task);
      try {
        return task.get(UI_ACTION_EFFECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
      } catch (ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof Exception) {
          throw (Exception) cause;
        } else {
          throw new RuntimeException("Execution on main thread failed", e);
        }
      }
    }
  }

  /**
   * Fails the test if any {@link Intent}s are still floating around after any validated ones have
   * been consumed.
   */
  public static boolean isStrayIntentRemaining() {
    try {
      intended(anyIntent());
      return true;
    } catch (AssertionFailedError expected) {
      return false;
    }
  }

  /** Creates a fake PendingIntent that can be used as a test value */
  public static PendingIntent createFakePendingIntent() {
    try {
      Constructor<PendingIntent> constructor =
          PendingIntent.class.getDeclaredConstructor(IBinder.class);
      constructor.setAccessible(true);
      return constructor.newInstance(mock(IBinder.class));
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (InstantiationException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Intent that can be compared to another Intent (base Intent doesn't override equals method)
   *
   * <p>Reason: (new Intent("MY_ACTION)).equals(new Intent("MY_ACTION")) returns false. This is
   * really annoying for unit tests for testing expecting method calls.
   *
   * <p>A ComparableIntent can be used instead of an Intent when calling:
   * Mockito.verify(myMock).myFunction(new ComparableIntent("MY_ACTION")). This avoids the
   * shenanigan to use an ArgumentCaptor.
   *
   * <p>Warning: this breaks the usual contract of equals that A.equals(B) means B.equals(A), so
   * this should be used just for testing.
   */
  public static class ComparableIntent extends Intent {

    public ComparableIntent() {
      super();
    }

    public ComparableIntent(String action) {
      super(action);
    }

    public ComparableIntent(Context packageContext, Class<?> cls) {
      super(packageContext, cls);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(filterHashCode(), getFlags(), getExtras());
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof Intent) {
        return filterEquals((Intent) o) && compareFlags((Intent) o) && compareExtras((Intent) o);
      }
      return false;
    }

    private boolean compareFlags(Intent other) {
      return other != null && this.getFlags() == other.getFlags();
    }

    private boolean compareExtras(Intent other) {
      if (other == null) {
        return false;
      }

      Bundle myExtras = this.getExtras();
      Bundle theirExtras = other.getExtras();

      if (myExtras == null && theirExtras == null) {
        return true;
      }

      if (myExtras.size() != theirExtras.size()) {
        return false;
      }

      for (String key : myExtras.keySet()) {
        if (!theirExtras.containsKey(key) || !myExtras.get(key).equals(theirExtras.get(key))) {
          return false;
        }
      }

      return true;
    }
  }
}
