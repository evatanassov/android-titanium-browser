// Copyright 2026 The Titanium Authors
// Use of this source code is governed by a GPL-2.0-only style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vertical_tabs;

import android.app.Activity;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.ItemTouchHelper;

import org.chromium.base.supplier.ObservableSuppliers;
import org.chromium.base.supplier.SettableNonNullObservableSupplier;
import org.chromium.chrome.browser.back_press.BackPressManager;
import org.chromium.chrome.browser.layouts.LayoutStateProvider;
import org.chromium.chrome.browser.layouts.LayoutType;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.components.browser_ui.widget.gesture.BackPressHandler;

/**
 * Public entry point for the vertical tabs feature. Owns the panel view, wires the mediator and
 * adapter, and manages the presentation mode:
 *
 * <ul>
 *   <li>DOCKED: persistent sidebar on DeX / large windows (content inset via coordinator margins).
 *   <li>RAIL: collapsed 48dp icon strip.
 *   <li>DRAWER: slide-over drawer with scrim and edge swipe on phones.
 * </ul>
 *
 * Modes are re-evaluated on every layout change of the content root (DeX window resizing,
 * foldables, rotation).
 */
public class VerticalTabsCoordinator implements BackPressHandler {
    private static final int LARGE_WINDOW_DP = 600;

    private final Activity mActivity;
    private final TabModelSelector mTabModelSelector;
    private final BackPressManager mBackPressManager;
    private final LayoutStateProvider mLayoutProvider;
    private final ViewGroup mContentRoot;
    private final View mCoordinatorView;
    private final VerticalTabsPrefs mPrefs;
    private final VerticalTabsFaviconLoader mFaviconLoader;
    private final VerticalTabsAdapter mAdapter;
    private final VerticalTabsMediator mMediator;
    private final VerticalTabsPanelView mPanel;
    private final View mScrim;
    private final View mEdgeStrip;
    private final SettableNonNullObservableSupplier<Boolean> mShouldInterceptBack =
            ObservableSuppliers.createNonNull(false);

    private boolean mDrawerOpen;
    private boolean mHubVisible;

    /**
     * @param activity The ChromeTabbedActivity.
     * @param tabModelSelector Supplies the tab models.
     * @param backPressManager The activity back press manager.
     * @param layoutProvider Supplies layout visibility (Hub/tab switcher gating); may be null.
     * @param contentRoot The activity content root (android.R.id.content).
     */
    public VerticalTabsCoordinator(Activity activity, TabModelSelector tabModelSelector,
            BackPressManager backPressManager, LayoutStateProvider layoutProvider,
            ViewGroup contentRoot) {
        mActivity = activity;
        mTabModelSelector = tabModelSelector;
        mBackPressManager = backPressManager;
        mLayoutProvider = layoutProvider;
        mContentRoot = contentRoot;
        mCoordinatorView = activity.findViewById(org.chromium.chrome.R.id.coordinator);
        mPrefs = new VerticalTabsPrefs();
        mFaviconLoader = new VerticalTabsFaviconLoader(activity);

        mAdapter = new VerticalTabsAdapter(
                /* delegate= */ null, mFaviconLoader);
        mMediator = new VerticalTabsMediator(activity, tabModelSelector, mPrefs, mAdapter,
                this::onListChanged);
        // The mediator implements the adapter delegate; set it after construction.
        mAdapter.setDelegate(mMediator);

        mPanel = new VerticalTabsPanelView(activity);
        mPanel.setAdapter(mAdapter);
        mPanel.setPositionRight(mPrefs.isPositionRight());
        mPanel.getNewTabButton().setOnClickListener(v -> mMediator.newTab());
        mPanel.getIncognitoToggleButton().setVisibility(View.VISIBLE);
        mPanel.getIncognitoToggleButton().setOnClickListener(v -> mMediator.toggleIncognitoModel());
        mPanel.getSearchView().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                mMediator.setSearchQuery(s.toString());
            }
        });
        mPanel.getResizeHandle().setOnTouchListener(this::onResizeTouch);
        // On phones, selecting a tab closes the drawer (plan default).
        mMediator.setOnTabSelectedCallback(() -> {
            if (isDrawerMode() && mDrawerOpen) hide();
        });

        mScrim = new View(activity);
        mScrim.setBackgroundColor(
                activity.getResources().getColor(R.color.vertical_tabs_scrim, null));
        mScrim.setOnClickListener(v -> hide());

        mEdgeStrip = new View(activity);
        mEdgeStrip.setBackgroundColor(Color.TRANSPARENT);
        mEdgeStrip.setOnTouchListener(this::onEdgeTouch);

        mContentRoot.addView(mScrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        mContentRoot.addView(mPanel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams edgeParams = new FrameLayout.LayoutParams(
                (int) (12 * activity.getResources().getDisplayMetrics().density),
                FrameLayout.LayoutParams.MATCH_PARENT);
        edgeParams.gravity = mPrefs.isPositionRight() ? Gravity.END : Gravity.START;
        mContentRoot.addView(mEdgeStrip, edgeParams);

        mContentRoot.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (l != ol || r != or || t != ot || b != ob) reevaluateMode();
        });

        ItemTouchHelper itemTouchHelper =
                new ItemTouchHelper(new VerticalTabsDragController(new DragDelegate()));
        itemTouchHelper.attachToRecyclerView(mPanel.getRecyclerView());

        inflateToolbarToggle();

        mBackPressManager.addHandler(this, BackPressHandler.Type.NATIVE_PAGE);
        if (mLayoutProvider != null) {
            mLayoutProvider.addObserver(new LayoutStateProvider.LayoutStateObserver() {
                @Override
                public void onStartedShowing(@LayoutType int layoutType) {
                    if (layoutType == LayoutType.HUB) {
                        mHubVisible = true;
                        applyVisibility();
                    }
                }

                @Override
                public void onStartedHiding(@LayoutType int layoutType) {
                    if (layoutType == LayoutType.HUB) {
                        mHubVisible = false;
                        applyVisibility();
                    }
                }
            });
        }

        mDrawerOpen = mPrefs.isDrawerOpen();
        reevaluateMode();
    }

    private void inflateToolbarToggle() {
        ViewStub stub =
                mActivity.findViewById(org.chromium.chrome.R.id.vertical_tabs_toggle_stub);
        if (stub == null) return;
        View toggle = stub.inflate();
        toggle.setOnClickListener(v -> toggle());
    }

    // Public API.

    public boolean isOpen() {
        if (isDrawerMode()) return mDrawerOpen;
        return !mHubVisible;
    }

    public void toggle() {
        if (isDrawerMode()) {
            if (mDrawerOpen) {
                hide();
            } else {
                show();
            }
        } else {
            mPrefs.setRailCollapsed(!mPrefs.isRailCollapsed());
            reevaluateMode();
        }
    }

    public void show() {
        if (isDrawerMode()) {
            mDrawerOpen = true;
            mPrefs.setDrawerOpen(true);
            mScrim.setVisibility(View.VISIBLE);
            mEdgeStrip.setVisibility(View.GONE);
            mPanel.setVisibility(View.VISIBLE);
            mPanel.animateDrawerOpen();
        }
        applyVisibility();
    }

    public void hide() {
        if (isDrawerMode()) {
            mDrawerOpen = false;
            mPrefs.setDrawerOpen(false);
            mScrim.setVisibility(View.GONE);
            mEdgeStrip.setVisibility(View.VISIBLE);
            mPanel.animateDrawerClose();
        }
        applyVisibility();
    }

    /** Handles vertical-tabs keyboard shortcuts; returns true if the event was consumed. */
    public boolean handleKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (!event.hasModifiers(KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON)) return false;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_B:
                toggle();
                return true;
            case KeyEvent.KEYCODE_RIGHT_BRACKET:
                selectNextTab(/* forward= */ true);
                return true;
            case KeyEvent.KEYCODE_LEFT_BRACKET:
                selectNextTab(/* forward= */ false);
                return true;
            default:
                return false;
        }
    }

    // BackPressHandler implementation (drawer mode only): back closes the open drawer.

    @Override
    public org.chromium.base.supplier.MonotonicObservableSupplier<Boolean>
    getHandleBackPressChangedSupplier() {
        return mShouldInterceptBack;
    }

    @Override
    public @BackPressResult int handleBackPress() {
        if (!isDrawerMode() || !mDrawerOpen) {
            return BackPressHandler.BackPressResult.FAILURE;
        }
        hide();
        return BackPressHandler.BackPressResult.SUCCESS;
    }

    public void destroy() {
        mBackPressManager.removeHandler(this);
        mMediator.destroy();
        mFaviconLoader.destroy();
        if (mPanel.getParent() instanceof ViewGroup) {
            ((ViewGroup) mPanel.getParent()).removeView(mPanel);
        }
        if (mScrim.getParent() instanceof ViewGroup) {
            ((ViewGroup) mScrim.getParent()).removeView(mScrim);
        }
        if (mEdgeStrip.getParent() instanceof ViewGroup) {
            ((ViewGroup) mEdgeStrip.getParent()).removeView(mEdgeStrip);
        }
    }

    // Internals.

    private boolean isDrawerMode() {
        return mPanel.getMode() == VerticalTabsPanelView.MODE_DRAWER;
    }

    private void onListChanged() {
        Tab currentTab = mTabModelSelector.getCurrentTab();
        if (currentTab != null) {
            mPanel.scrollToTab(mAdapter.positionForTabId(currentTab.getId()));
        }
        applyVisibility();
    }

    private void applyVisibility() {
        boolean visible = !mHubVisible;
        mPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        mScrim.setVisibility(visible && isDrawerMode() && mDrawerOpen
                        ? View.VISIBLE
                        : View.GONE);
        mEdgeStrip.setVisibility(
                visible && isDrawerMode() && !mDrawerOpen ? View.VISIBLE : View.GONE);
        mShouldInterceptBack.set(visible && isDrawerMode() && mDrawerOpen);
    }

    /** Re-evaluates DOCKED / RAIL / DRAWER based on the current window size. */
    private void reevaluateMode() {
        int rootWidth = mContentRoot.getWidth();
        if (rootWidth == 0) return;
        float density = mActivity.getResources().getDisplayMetrics().density;
        boolean largeWindow = rootWidth / density >= LARGE_WINDOW_DP;

        int mode;
        if (largeWindow) {
            mode = mPrefs.isRailCollapsed() ? VerticalTabsPanelView.MODE_RAIL
                                            : VerticalTabsPanelView.MODE_DOCKED;
        } else {
            mode = VerticalTabsPanelView.MODE_DRAWER;
        }
        mPanel.getLayoutParams().width = getPanelWidthPx(mode, rootWidth, density);
        mPanel.setMode(mode);
        mPanel.setLayoutParams(mPanel.getLayoutParams());

        applyContentInset(mode);
        if (mode == VerticalTabsPanelView.MODE_DRAWER) {
            mPanel.setDrawerOpenState(mDrawerOpen);
        } else {
            mScrim.setVisibility(View.GONE);
            mEdgeStrip.setVisibility(View.GONE);
            mPanel.setTranslationX(0f);
        }
        applyVisibility();
    }

    private int getPanelWidthPx(int mode, int rootWidth, float density) {
        if (mode == VerticalTabsPanelView.MODE_RAIL) {
            return mActivity.getResources().getDimensionPixelSize(
                    R.dimen.vertical_tabs_rail_width);
        }
        if (mode == VerticalTabsPanelView.MODE_DOCKED) {
            return (int) (mPrefs.getWidthDp() * density);
        }
        int drawerWidth = (int) (density
                * mActivity.getResources().getDimensionPixelSize(
                        R.dimen.vertical_tabs_drawer_width));
        int maxWidth = rootWidth
                - (int) (density
                        * mActivity.getResources().getDimensionPixelSize(
                                R.dimen.vertical_tabs_drawer_edge_margin));
        return Math.min(drawerWidth, maxWidth);
    }

    private void applyContentInset(int mode) {
        if (mCoordinatorView == null) return;
        ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) mCoordinatorView.getLayoutParams();
        int inset = mode == VerticalTabsPanelView.MODE_DRAWER
                ? 0
                : mPanel.getLayoutParams().width;
        if (mPrefs.isPositionRight()) {
            params.rightMargin = inset;
            params.leftMargin = 0;
        } else {
            params.leftMargin = inset;
            params.rightMargin = 0;
        }
        mCoordinatorView.setLayoutParams(params);
    }

    private boolean onResizeTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return true;
            case MotionEvent.ACTION_MOVE:
                {
                    float density =
                            mActivity.getResources().getDisplayMetrics().density;
                    int delta = mPrefs.isPositionRight()
                            ? (int) (view.getRootView().getWidth() - event.getRawX())
                            : (int) event.getRawX();
                    int widthDp = (int) (delta / density);
                    mPrefs.setWidthDp(widthDp);
                    mPanel.getLayoutParams().width =
                            (int) (mPrefs.getWidthDp() * density);
                    mPanel.setLayoutParams(mPanel.getLayoutParams());
                    applyContentInset(VerticalTabsPanelView.MODE_DOCKED);
                    return true;
                }
            default:
                return false;
        }
    }

    private boolean onEdgeTouch(View view, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            show();
            return true;
        }
        return false;
    }

    private void selectNextTab(boolean forward) {
        TabModel model = mTabModelSelector.getCurrentModel();
        if (model == null || model.getCount() < 2) return;
        int index = model.index();
        index = forward ? (index + 1) % model.getCount()
                        : (index - 1 + model.getCount()) % model.getCount();
        model.setIndex(index, TabSelectionType.FROM_USER);
    }

    private class DragDelegate implements VerticalTabsDragController.Delegate {
        @Override
        public void onDragMove(int fromPosition, int toPosition) {
            mMediator.moveItem(fromPosition, toPosition);
        }

        @Override
        public void onSwipeToClose(int position) {
            if (position < 0 || position >= mAdapter.getItemCountForLookup()) return;
            VerticalTabsItem item = mAdapter.getItemAt(position);
            if (item.mType == VerticalTabsItem.Type.TAB && item.mTab != null) {
                mMediator.onTabCloseClicked(item.mTab);
            }
        }
    }
}
