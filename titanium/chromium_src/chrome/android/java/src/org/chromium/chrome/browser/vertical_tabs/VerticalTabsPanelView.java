// Copyright 2026 The Titanium Authors
// Use of this source code is governed by a GPL-2.0-only style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vertical_tabs;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.chromium.build.annotations.Nullable;

/**
 * The vertical tabs panel. Hosts the search box, the new-tab / incognito buttons and the tab list
 * RecyclerView, and supports three visual states:
 *
 * <ul>
 *   <li>{@link #MODE_DOCKED}: persistent sidebar docked at the window edge (DeX / large windows).
 *   <li>{@link #MODE_DRAWER}: slide-over drawer with a scrim (phones).
 *   <li>{@link #MODE_RAIL}: collapsed 48dp icon strip (docked, collapsed state).
 * </ul>
 */
public class VerticalTabsPanelView extends FrameLayout {
    public static final int MODE_DRAWER = 0;
    public static final int MODE_DOCKED = 1;
    public static final int MODE_RAIL = 2;

    private RecyclerView mRecyclerView;
    private EditText mSearchView;
    private ImageButton mNewTabButton;
    private ImageButton mIncognitoToggleButton;
    private View mResizeHandle;
    private View mContent;

    private int mMode = MODE_DRAWER;
    private boolean mPositionRight;

    public VerticalTabsPanelView(Context context) {
        this(context, null);
    }

    public VerticalTabsPanelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.vertical_tabs_panel, this, true);
        mContent = findViewById(R.id.vertical_tabs_content);
        mRecyclerView = findViewById(R.id.vertical_tabs_recycler_view);
        mSearchView = findViewById(R.id.vertical_tabs_search);
        mNewTabButton = findViewById(R.id.vertical_tabs_new_tab);
        mIncognitoToggleButton = findViewById(R.id.vertical_tabs_incognito_toggle);
        mResizeHandle = findViewById(R.id.vertical_tabs_resize_handle);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        mRecyclerView.setAdapter(null);
    }

    RecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    EditText getSearchView() {
        return mSearchView;
    }

    ImageButton getNewTabButton() {
        return mNewTabButton;
    }

    ImageButton getIncognitoToggleButton() {
        return mIncognitoToggleButton;
    }

    View getResizeHandle() {
        return mResizeHandle;
    }

    int getMode() {
        return mMode;
    }

    void setAdapter(RecyclerView.Adapter<?> adapter) {
        mRecyclerView.setAdapter(adapter);
    }

    void setPositionRight(boolean positionRight) {
        mPositionRight = positionRight;
        updateLayoutDirection();
    }

    boolean isPositionRight() {
        return mPositionRight;
    }

    void scrollToTab(int position) {
        if (position < 0) return;
        LinearLayoutManager layout = (LinearLayoutManager) mRecyclerView.getLayoutManager();
        if (layout == null) return;
        int first = layout.findFirstCompletelyVisibleItemPosition();
        int last = layout.findLastCompletelyVisibleItemPosition();
        if (position < first || position > last) {
            mRecyclerView.smoothScrollToPosition(position);
        }
    }

    /** Applies the visual state for the given mode. Size is managed by the coordinator. */
    void setMode(int mode) {
        mMode = mode;
        updateLayoutDirection();
        boolean rail = mode == MODE_RAIL;
        mContent.findViewById(R.id.vertical_tabs_header).setVisibility(
                rail ? GONE : VISIBLE);
        mResizeHandle.setVisibility(mode == MODE_DOCKED ? VISIBLE : GONE);
        setElevation(getResources().getDimensionPixelSize(
                mode == MODE_DRAWER
                        ? R.dimen.vertical_tabs_drawer_elevation
                        : R.dimen.vertical_tabs_docked_elevation));
        // In rail mode the rows collapse to favicon-only strips.
        if (getAdapter() instanceof VerticalTabsAdapter) {
            ((VerticalTabsAdapter) getAdapter()).setRailMode(rail);
        }
    }

    /** Shows the drawer with animation. */
    void animateDrawerOpen() {
        animate().translationX(0f).setDuration(200L).start();
    }

    /** Hides the drawer with animation (slides it off the window edge). */
    void animateDrawerClose() {
        float offset = mPositionRight ? getWidth() : -getWidth();
        animate().translationX(offset).setDuration(200L).start();
    }

    /** Instantly positions the drawer without animation (e.g. on mode changes). */
    void setDrawerOpenState(boolean open) {
        clearAnimation();
        animate().cancel();
        setTranslationX(open ? 0f : (mPositionRight ? getWidth() : -getWidth()));
    }

    private void updateLayoutDirection() {
        int gravity = mPositionRight ? RIGHT : LEFT;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
        if (params != null) {
            params.gravity = gravity;
            setLayoutParams(params);
        }
        mResizeHandle.getLayoutParams().width =
                getResources().getDimensionPixelSize(R.dimen.vertical_tabs_resize_handle_width);
        // The resize handle sits on the inner edge of the docked sidebar.
        ((FrameLayout.LayoutParams) mResizeHandle.getLayoutParams()).gravity =
                mPositionRight ? LEFT : RIGHT;
    }
}
