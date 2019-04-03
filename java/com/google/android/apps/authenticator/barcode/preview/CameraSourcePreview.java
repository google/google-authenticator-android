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

package com.google.android.apps.authenticator.barcode.preview;

import android.Manifest.permission;
import android.content.Context;
import android.content.res.Configuration;
import android.support.annotation.RequiresPermission;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import com.google.android.apps.authenticator.barcode.BarcodeCentralFilter;
import com.google.android.gms.common.images.Size;
import com.google.android.gms.vision.CameraSource;
import java.io.IOException;

/**
 * A class that displays the camera preview.
 */
public class CameraSourcePreview extends ViewGroup {

  // The tag for log messages
  private static final String TAG = "CameraSourcePreview";

  private static final int DEFAULT_PREVIEW_WIDTH = 240;
  private static final int DEFAULT_PREVIEW_HEIGHT = 320;

  private final SurfaceView surfaceView;
  private boolean startRequested;
  private boolean surfaceAvailable;
  private CameraSource cameraSource;
  private GraphicOverlay graphicOverlay;

  public CameraSourcePreview(Context context, AttributeSet attrs) {
    super(context, attrs);
    startRequested = false;
    surfaceAvailable = false;

    surfaceView = new SurfaceView(context);
    surfaceView.getHolder().addCallback(new SurfaceCallback());
    addView(surfaceView);
  }

  /**
   * Starts the display of the camera preview. If the camera source passed to this method is null,
   * then the previous camera source is stopped and its resources get released.
   *
   * @param cameraSource the camera source from where the preview frames come from.
   * @param graphicOverlay CameraSourcePreview will set the preview info to the GraphicOverlay
   * instance when available.
   * @throws SecurityException if there is no permission to use the camera when this method was
   * called.
   */
  public void start(CameraSource cameraSource, GraphicOverlay graphicOverlay)
      throws SecurityException {
    this.graphicOverlay = graphicOverlay;
    if (cameraSource != null) {
      this.cameraSource = cameraSource;
      startRequested = true;
      startIfReady();
    } else {
      release();
    }
  }

  /**
   * Stops the camera preview.
   */
  public void stop() {
    if (cameraSource != null) {
      cameraSource.stop();
    }
  }

  /**
   * Stops the camera preview and releases the resources of the underlying camera source.
   */
  public void release() {
    if (cameraSource != null) {
      cameraSource.release();
      cameraSource = null;
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    // Each child needs to measure itself.
    for (int i = 0; i < getChildCount(); i++) {
      getChildAt(i).measure(widthMeasureSpec, heightMeasureSpec);
    }
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    int previewWidth = DEFAULT_PREVIEW_WIDTH;
    int previewHeight = DEFAULT_PREVIEW_HEIGHT;
    if (cameraSource != null) {
      Size size = cameraSource.getPreviewSize();
      if (size != null) {
        previewWidth = size.getWidth();
        previewHeight = size.getHeight();
      }
    }
    // Swap width and height sizes when in portrait, since it will be rotated 90 degrees.
    if (isPortraitMode()) {
      int tmp = previewWidth;
      previewWidth = previewHeight;
      previewHeight = tmp;
    }

    BarcodeCentralFilter.storeCameraSize(new Size(previewWidth, previewHeight));

    final int layoutWidth = right - left;
    final int layoutHeight = bottom - top;

    if (isPortraitMode()) {
      // Fit the width.
      int childHeight = (int) (((double) layoutWidth / (double) previewWidth) * previewHeight);
      int marginTop = (layoutHeight - childHeight) / 2;
      for (int i = 0; i < getChildCount(); i++) {
        getChildAt(i).layout(0, marginTop, layoutWidth, childHeight + marginTop);
      }
    } else {
      // Fit the height.
      int childWidth = (int) (((double) layoutHeight / (double) previewHeight) * previewWidth);
      int marginLeft = (layoutWidth - childWidth) / 2;
      for (int i = 0; i < getChildCount(); i++) {
        getChildAt(i).layout(marginLeft, 0, childWidth + marginLeft, layoutHeight);
      }
    }

    tryToStart();
  }

  /**
   * Check if we are is in the portrait mode or not
   *
   * @return true if the we are currently in portrait mode
   */
  private boolean isPortraitMode() {
    int orientation = getContext().getResources().getConfiguration().orientation;
    return orientation == Configuration.ORIENTATION_PORTRAIT;
  }

  private void tryToStart() {
    try {
      startIfReady();
    } catch (SecurityException e) {
      Log.e(TAG, "Do not have permission to start the camera", e);
    }
  }

  @RequiresPermission(permission.CAMERA)
  private void startIfReady() {
    if (startRequested && surfaceAvailable) {
      try {
        cameraSource.start(surfaceView.getHolder());
        requestLayout();
      } catch (IOException e) {
        Log.e(TAG, "Could not start camera source", e);
        release();
      } catch (SecurityException e) {
        Log.e(TAG, "Do not have permission to start the camera", e);
      } catch (RuntimeException e) {
        // This should happen only during tests
        Log.e(TAG, "Runtime Exception!", e);
      }
      graphicOverlay.showContent();
      startRequested = false;
    }
  }

  /**
   * Callback class for the Surface Holder
   */
  private class SurfaceCallback implements SurfaceHolder.Callback {

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
      surfaceAvailable = true;
      tryToStart();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
      surfaceAvailable = true;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
      surfaceAvailable = false;
    }
  }

}
