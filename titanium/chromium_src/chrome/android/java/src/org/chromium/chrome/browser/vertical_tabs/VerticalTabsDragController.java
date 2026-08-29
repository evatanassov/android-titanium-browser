// Copyright 2026 The Titanium Authors
// Use of this source code is governed by a GPL-2.0-only style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vertical_tabs;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/** Drag & drop (reorder, pin/unpin) and swipe-to-close for the vertical tabs list. */
class VerticalTabsDragController extends ItemTouchHelper.Callback {
    interface Delegate {
        void onDragMove(int fromPosition, int toPosition);

        void onSwipeToClose(int position);
    }

    private final Delegate mDelegate;

    VerticalTabsDragController(Delegate delegate) {
        mDelegate = delegate;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        // Long press opens the context menu; dragging is started from the row itself.
        return false;
    }

    @Override
    public int getMovementFlags(
            @NonNull RecyclerView recyclerView,
            @NonNull RecyclerView.ViewHolder viewHolder) {
        if (!VerticalTabsAdapter.isTabViewHolder(viewHolder)) {
            return makeMovementFlags(0, 0);
        }
        int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        int swipeFlags = ItemTouchHelper.START | ItemTouchHelper.END;
        return makeMovementFlags(dragFlags, swipeFlags);
    }

    @Override
    public boolean onMove(
            @NonNull RecyclerView recyclerView,
            @NonNull RecyclerView.ViewHolder viewHolder,
            @NonNull RecyclerView.ViewHolder target) {
        mDelegate.onDragMove(viewHolder.getBindingAdapterPosition(),
                target.getBindingAdapterPosition());
        return true;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        mDelegate.onSwipeToClose(viewHolder.getBindingAdapterPosition());
    }
}
