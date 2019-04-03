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
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.AdapterView;
import com.mobeta.android.dslv.DragSortListView;

/**
 * A special {@link DragSortListView} that knows how to handle the click event
 * on empty space of the list.
 */
public class EmptySpaceClickableDragSortListView extends DragSortListView {

  /**
   * Handle click event on the empty space of the list.
   */
  public interface OnEmptySpaceClickListener {
    void onEmptySpaceClick();
  }

  private OnEmptySpaceClickListener mOnEmptySpaceClickListener;

  public EmptySpaceClickableDragSortListView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public void setOnEmptySpaceClickListener(OnEmptySpaceClickListener onEmptySpaceClickListener) {
    mOnEmptySpaceClickListener = onEmptySpaceClickListener;
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent ev) {
    int x = (int) ev.getX();
    int y = (int) ev.getY();
    if (pointToPosition(x, y) == AdapterView.INVALID_POSITION
        && ev.getAction() == MotionEvent.ACTION_DOWN
        && mOnEmptySpaceClickListener != null) {
      mOnEmptySpaceClickListener.onEmptySpaceClick();
    }
    return super.dispatchTouchEvent(ev);
  }
}
