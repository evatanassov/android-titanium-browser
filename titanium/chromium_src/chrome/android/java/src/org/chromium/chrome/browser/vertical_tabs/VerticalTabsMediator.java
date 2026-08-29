// Copyright 2026 The Titanium Authors
// Use of this source code is governed by a GPL-2.0-only style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vertical_tabs;

import android.content.Context;
import android.view.View;
import android.widget.PopupMenu;

import org.chromium.base.Token;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabCreationState;
import org.chromium.chrome.browser.tab.TabLaunchType;
import org.chromium.chrome.browser.tab.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.TabClosureParams;
import org.chromium.chrome.browser.tabmodel.TabList;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.content_public.browser.LoadUrlParams;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Business logic for the vertical tabs panel: observes the tab models, maintains the item list
 * (sections for pinned tabs and tab groups), and implements the tab management actions.
 */
class VerticalTabsMediator implements VerticalTabsAdapter.Delegate {
    // Palette mirroring Chromium's tab group colors; used for group section dots.
    private static final int[] GROUP_COLOR_PALETTE = {
        0xFF1A73E8, // blue
        0xFF12B5CB, // cyan
        0xFF188038, // green
        0xFFE37400, // orange
        0xFFD01818, // red
        0xFF9334E6, // purple
        0xFFC5221F, // red orange
        0xFFE8710A, // yellow
        0xFF007B83, // teal
        0xFFC5221F, // dark red
        0xFF476B1E, // olive
    };

    private static final String NTP_URL = "chrome://newtab/";

    private final Context mContext;
    private final TabModelSelector mTabModelSelector;
    private final VerticalTabsPrefs mPrefs;
    private final VerticalTabsAdapter mAdapter;
    private final Runnable mOnListChanged;
    private final TabModelObserver mObserver;

    private String mSearchQuery = "";
    private Runnable mOnTabSelectedCallback;

    VerticalTabsMediator(Context context, TabModelSelector tabModelSelector,
            VerticalTabsPrefs prefs, VerticalTabsAdapter adapter, Runnable onListChanged) {
        mContext = context;
        mTabModelSelector = tabModelSelector;
        mPrefs = prefs;
        mAdapter = adapter;
        mOnListChanged = onListChanged;

        mObserver = new TabModelObserver() {
            @Override
            public void didAddTab(
                    Tab tab, @TabLaunchType int type, @TabCreationState int creationState,
                    boolean markedForSelection) {
                rebuild();
            }

            @Override
            public void tabRemoved(Tab tab) {
                rebuild();
            }

            @Override
            public void didMoveTab(Tab tab, int newIndex, int curIndex) {
                rebuild();
            }

            @Override
            public void didSelectTab(Tab tab, @TabSelectionType int type, int lastId) {
                rebuild();
            }

            @Override
            public void tabClosureUndone(Tab tab) {
                rebuild();
            }

            @Override
            public void tabClosureCommitted(Tab tab) {
                rebuild();
            }

            @Override
            public void restoreCompleted() {
                rebuild();
            }

            @Override
            public void didChangePinState(Tab tab) {
                rebuild();
            }

            @Override
            public void onTabGroupCreated(Token groupId) {
                rebuild();
            }

            @Override
            public void onTabGroupRemoving(Token groupId) {
                rebuild();
            }

            @Override
            public void onTabGroupMoved(Token groupId, int oldIndex) {
                rebuild();
            }

            @Override
            public void onTabGroupVisualsChanged(Token groupId) {
                rebuild();
            }

            @Override
            public void onDestroy() {
                // Model destruction; a full rebuild happens on the next event.
            }
        };
        mTabModelSelector.addObserverToAllModels(mObserver);
    }

    /** The model currently rendered by the panel. */
    TabModel getCurrentModel() {
        return mTabModelSelector.getCurrentModel();
    }

    /** Switches the rendered (and browser-active) model between regular and incognito. */
    void toggleIncognitoModel() {
        boolean toIncognito = !mTabModelSelector.isIncognitoSelected();
        mTabModelSelector.selectModel(toIncognito);
        rebuild();
    }

    void setSearchQuery(String query) {
        mSearchQuery = query == null ? "" : query;
        rebuild();
    }

    /** Sets a callback invoked after the user selects a tab in the panel. */
    void setOnTabSelectedCallback(Runnable callback) {
        mOnTabSelectedCallback = callback;
    }

    void destroy() {
        mTabModelSelector.removeObserverFromAllModels(mObserver);
    }

    /** Rebuilds the item list and pushes it to the adapter. */
    void rebuild() {
        mAdapter.setItems(buildItems());
        Tab currentTab = mTabModelSelector.getCurrentTab();
        mAdapter.setActiveTabId(currentTab != null ? currentTab.getId() : Tab.INVALID_TAB_ID);
        mOnListChanged.run();
    }

    private List<VerticalTabsItem> buildItems() {
        List<VerticalTabsItem> items = new ArrayList<>();
        TabModel model = getCurrentModel();
        if (model == null) return items;

        List<Tab> allTabs = new ArrayList<>();
        for (int i = 0; i < model.getCount(); i++) {
            Tab tab = model.getTabAt(i);
            if (tab != null) allTabs.add(tab);
        }

        // Search mode: flat list of matching tabs.
        if (!mSearchQuery.isEmpty()) {
            String query = mSearchQuery.toLowerCase(Locale.getDefault());
            for (Tab tab : allTabs) {
                if (matches(tab, query)) items.add(VerticalTabsItem.createTab(tab, false));
            }
            return items;
        }

        List<Tab> pinned = new ArrayList<>();
        List<Tab> ungrouped = new ArrayList<>();
        Map<String, List<Tab>> groups = new LinkedHashMap<>();
        for (Tab tab : allTabs) {
            if (tab.getIsPinned()) {
                pinned.add(tab);
            } else if (tab.getTabGroupId() != null) {
                groups.computeIfAbsent(tab.getTabGroupId().toString(), k -> new ArrayList<>())
                        .add(tab);
            } else {
                ungrouped.add(tab);
            }
        }

        boolean hasSections = !pinned.isEmpty() || !groups.isEmpty();
        if (!pinned.isEmpty()) {
            items.add(VerticalTabsItem.createSectionHeader(VerticalTabsItem.SECTION_PINNED,
                    mContext.getString(R.string.vertical_tabs_section_pinned), 0, false));
            for (Tab tab : pinned) items.add(VerticalTabsItem.createTab(tab, false));
        }
        for (Map.Entry<String, List<Tab>> entry : groups.entrySet()) {
            boolean collapsed = mPrefs.isGroupCollapsed(entry.getKey());
            items.add(VerticalTabsItem.createSectionHeader(entry.getKey(),
                    mContext.getString(R.string.vertical_tabs_section_group),
                    colorForGroup(entry.getKey()), collapsed));
            if (!collapsed) {
                for (Tab tab : entry.getValue()) {
                    items.add(VerticalTabsItem.createTab(tab, true));
                }
            }
        }
        if (!ungrouped.isEmpty()) {
            if (hasSections) {
                items.add(VerticalTabsItem.createSectionHeader(VerticalTabsItem.SECTION_TABS,
                        mContext.getString(R.string.vertical_tabs_section_tabs), 0, false));
            }
            for (Tab tab : ungrouped) items.add(VerticalTabsItem.createTab(tab, false));
        }
        return items;
    }

    private static boolean matches(Tab tab, String query) {
        String title = tab.getTitle();
        if (title != null && title.toLowerCase(Locale.getDefault()).contains(query)) return true;
        String url = tab.getUrl() != null ? tab.getUrl().getSpec() : null;
        return url != null && url.toLowerCase(Locale.getDefault()).contains(query);
    }

    private static int colorForGroup(String groupId) {
        int index = Math.abs(groupId.hashCode()) % GROUP_COLOR_PALETTE.length;
        return GROUP_COLOR_PALETTE[index];
    }

    // VerticalTabsAdapter.Delegate implementations.

    @Override
    public void onTabClicked(Tab tab) {
        TabModel model = mTabModelSelector.getModel(tab.isIncognito());
        if (model == null) return;
        int index = model.indexOf(tab);
        if (index == TabList.INVALID_TAB_INDEX) return;
        model.setIndex(index, TabSelectionType.FROM_USER);
        if (mOnTabSelectedCallback != null) mOnTabSelectedCallback.run();
    }

    @Override
    public void onTabCloseClicked(Tab tab) {
        mTabModelSelector.tryCloseTab(TabClosureParams.closeTab(tab).build(), true);
    }

    @Override
    public void onTabLongClicked(Tab tab, View anchor) {
        PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);
        popup.getMenu()
                .add(tab.getIsPinned()
                                ? R.string.vertical_tabs_context_unpin
                                : R.string.vertical_tabs_context_pin)
                .setOnMenuItemClickListener(
                        item -> {
                            setPinned(tab, !tab.getIsPinned());
                            return true;
                        });
        popup.getMenu().add(R.string.vertical_tabs_context_duplicate)
                .setOnMenuItemClickListener(
                        item -> {
                            duplicateTab(tab);
                            return true;
                        });
        popup.getMenu().add(R.string.vertical_tabs_context_close_others)
                .setOnMenuItemClickListener(
                        item -> {
                            closeOtherTabs(tab);
                            return true;
                        });
        popup.getMenu().add(R.string.vertical_tabs_context_close).setOnMenuItemClickListener(
                item -> {
                    onTabCloseClicked(tab);
                    return true;
                });
        popup.show();
    }

    @Override
    public void onSectionHeaderClicked(String sectionId) {
        if (VerticalTabsItem.SECTION_PINNED.equals(sectionId)
                || VerticalTabsItem.SECTION_TABS.equals(sectionId)) {
            return;
        }
        mPrefs.setGroupCollapsed(sectionId, !mPrefs.isGroupCollapsed(sectionId));
        rebuild();
    }

    // Tab management actions.

    void setPinned(Tab tab, boolean pinned) {
        TabModel model = mTabModelSelector.getModel(tab.isIncognito());
        if (model == null) return;
        if (pinned) {
            model.pinTab(tab.getId(), /* showUngroupDialog= */ false, /* listener= */ null);
        } else {
            model.unpinTab(tab.getId());
        }
    }

    void duplicateTab(Tab tab) {
        if (tab.getUrl() == null) return;
        mTabModelSelector.openNewTab(
                new LoadUrlParams(tab.getUrl().getSpec()), TabLaunchType.FROM_CHROME_UI, tab,
                tab.isIncognito());
    }

    void closeOtherTabs(Tab keepTab) {
        TabModel model = mTabModelSelector.getModel(keepTab.isIncognito());
        if (model == null) return;
        List<Tab> toClose = new ArrayList<>();
        for (int i = 0; i < model.getCount(); i++) {
            Tab tab = model.getTabAt(i);
            if (tab != null && tab.getId() != keepTab.getId()) toClose.add(tab);
        }
        if (toClose.isEmpty()) return;
        mTabModelSelector.tryCloseTab(TabClosureParams.closeTabs(toClose).build(), true);
    }

    void newTab() {
        boolean incognito = mTabModelSelector.isIncognitoSelected();
        mTabModelSelector.openNewTab(
                new LoadUrlParams(NTP_URL), TabLaunchType.FROM_CHROME_UI, null, incognito);
    }

    /**
     * Moves a tab (drag & drop). Dragging into the pinned section pins the tab; dragging out of it
     * unpins; otherwise the tab is reordered within the model.
     */
    void moveItem(int fromPosition, int toPosition) {
        VerticalTabsItem dragged = mAdapter.getItemAt(fromPosition);
        if (dragged.mType != VerticalTabsItem.Type.TAB || dragged.mTab == null) return;
        Tab tab = dragged.mTab;
        TabModel model = mTabModelSelector.getModel(tab.isIncognito());
        if (model == null) return;

        if (toPosition < 0 || toPosition >= mAdapter.getItemCountForLookup()) return;
        VerticalTabsItem target = mAdapter.getItemAt(toPosition);

        boolean targetInPinned = isWithinPinnedSection(toPosition);
        if (targetInPinned && !tab.getIsPinned()) {
            setPinned(tab, true);
            return;
        }
        if (!targetInPinned && tab.getIsPinned()) {
            setPinned(tab, false);
            return;
        }
        if (target.mType == VerticalTabsItem.Type.TAB && target.mTab != null) {
            int targetIndex = model.indexOf(target.mTab);
            if (targetIndex != TabList.INVALID_TAB_INDEX) {
                model.moveTab(tab.getId(), targetIndex);
            }
        }
    }

    private boolean isWithinPinnedSection(int position) {
        boolean inPinned = false;
        for (int i = 0; i <= position && i < mAdapter.getItemCountForLookup(); i++) {
            VerticalTabsItem item = mAdapter.getItemAt(i);
            if (item.mType == VerticalTabsItem.Type.SECTION_HEADER) {
                inPinned = VerticalTabsItem.SECTION_PINNED.equals(item.mSectionId);
            }
        }
        return inPinned;
    }
}
