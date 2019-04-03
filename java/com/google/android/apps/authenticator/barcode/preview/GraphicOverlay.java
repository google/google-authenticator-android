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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import com.google.android.apps.authenticator.barcode.BarcodeCentralFilter;
import com.google.android.apps.authenticator2.R;

/**
 * A view group that contains the barcode scanner image at the center of the screen. This screen
 * will be overlayed on top of an associated preview (i.e., the camera preview).
 */
public class GraphicOverlay extends View {

  // Size of the instruction text, in sp.
  private static final int BARCODE_INSTRUCTION_TEXT_SIZE = 16;

  // Margin top for the instruction text below the square, in dp.
  private static final int BARCODE_INSTRUCTION_TEXT_MARGIN_TOP = 40;

  private boolean drawContent;
  private Bitmap scannerBitmap;

  public GraphicOverlay(Context context, AttributeSet attrs) {
    super(context, attrs);

    drawContent = false;
  }

  @Override
  @SuppressLint("DrawAllocation")
  protected void onDraw(Canvas canvas) {
    if (!drawContent) {
      return;
    }
    int width = canvas.getWidth();
    int height = canvas.getHeight();

    Rect frame = BarcodeCentralFilter.getSquareFrame(width, height);

    if (scannerBitmap == null) {
      scannerBitmap =
          BitmapFactory.decodeResource(
              getContext().getResources(), R.drawable.barcode_scanner_image);
    }

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(ContextCompat.getColor(getContext(), R.color.barcode_scanner_border_background));
    canvas.drawRect(0, 0, width, frame.top, paint);
    canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
    canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
    canvas.drawRect(0, frame.bottom + 1, width, height, paint);

    paint.setAlpha(255);
    Rect expandedFrame = BarcodeCentralFilter.getExtraSquareFrame(width, height);
    canvas.drawBitmap(scannerBitmap, null, expandedFrame, paint);

    // Draw text below the square with white color and 16sp size.
    Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    textPaint.setColor(Color.WHITE);
    textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    textPaint.setTextAlign(Align.CENTER);
    textPaint.setTextSize(TypedValue
        .applyDimension(TypedValue.COMPLEX_UNIT_SP, BARCODE_INSTRUCTION_TEXT_SIZE,
            getResources().getDisplayMetrics()));

    float marginTop = TypedValue
        .applyDimension(TypedValue.COMPLEX_UNIT_SP, BARCODE_INSTRUCTION_TEXT_MARGIN_TOP,
            getResources().getDisplayMetrics());
    canvas.drawText(getResources().getString(R.string.barcode_instruction_text),
        width / 2, frame.bottom + marginTop + (textPaint.ascent() + textPaint.descent()) / 2,
        textPaint);
  }

  public void showContent() {
    drawContent = true;
    invalidate();
  }

}
