// Copyright 2026 The Titanium Authors
// Use of this source code is governed by a GPL-2.0-only style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vertical_tabs;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.chromium.chrome.browser.tab.Tab;

import java.util.ArrayList;
import java.util.List;

/** RecyclerView adapter for the vertical tabs list. */
class VerticalTabsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    /** Callbacks implemented by the mediator. */
    interface Delegate {
        void onTabClicked(Tab tab);

        void onTabCloseClicked(Tab tab);

        void onTabLongClicked(Tab tab, View anchor);

        void onSectionHeaderClicked(String sectionId);
    }

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_TAB = 1;

    /** Returns whether the given holder renders a tab row (as opposed to a section header). */
    static boolean isTabViewHolder(RecyclerView.ViewHolder holder) {
        return holder instanceof TabViewHolder;
    }

    private Delegate mDelegate;
    private final VerticalTabsFaviconLoader mFaviconLoader;
    private List<VerticalTabsItem> mItems = new ArrayList<>();
    private int mActiveTabId = Tab.INVALID_TAB_ID;
    private boolean mRailMode;

    VerticalTabsAdapter(Delegate delegate, VerticalTabsFaviconLoader faviconLoader) {
        mDelegate = delegate;
        mFaviconLoader = faviconLoader;
        setHasStableIds(true);
    }

    void setDelegate(Delegate delegate) {
        mDelegate = delegate;
    }

    void setItems(List<VerticalTabsItem> newItems) {
        final List<VerticalTabsItem> oldItems = mItems;
        mItems = newItems;
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return mItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldItems
                                .get(oldItemPosition)
                                .key()
                                .equals(mItems.get(newItemPosition).key())
                        && oldItems.get(oldItemPosition).mType
                                == mItems.get(newItemPosition).mType;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                VerticalTabsItem oldItem = oldItems.get(oldItemPosition);
                VerticalTabsItem newItem = mItems.get(newItemPosition);
                if (oldItem.mType == VerticalTabsItem.Type.TAB
                        && newItem.mType == VerticalTabsItem.Type.TAB) {
                    boolean wasActive = oldItem.mTab.getId() == mActiveTabId;
                    boolean isActive = newItem.mTab.getId() == mActiveTabId;
                    return oldItem.contentHash() == newItem.contentHash() && wasActive == isActive;
                }
                return oldItem.contentHash() == newItem.contentHash();
            }
        }, true);
        result.dispatchUpdatesTo(this);
    }

    void setActiveTabId(int activeTabId) {
        if (mActiveTabId == activeTabId) return;
        mActiveTabId = activeTabId;
        notifyDataSetChanged();
    }

    void setRailMode(boolean railMode) {
        if (mRailMode == railMode) return;
        mRailMode = railMode;
        notifyDataSetChanged();
    }

    boolean isRailMode() {
        return mRailMode;
    }

    VerticalTabsItem getItemAt(int position) {
        return mItems.get(position);
    }

    int getItemCountForLookup() {
        return mItems.size();
    }

    /** Returns the list position of the row for the given tab, or -1. */
    int positionForTabId(int tabId) {
        for (int i = 0; i < mItems.size(); i++) {
            VerticalTabsItem item = mItems.get(i);
            if (item.mType == VerticalTabsItem.Type.TAB && item.mTab.getId() == tabId) return i;
        }
        return -1;
    }

    @Override
    public long getItemId(int position) {
        return mItems.get(position).key().hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        return mItems.get(position).mType == VerticalTabsItem.Type.SECTION_HEADER
                ? VIEW_TYPE_HEADER
                : VIEW_TYPE_TAB;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            return new HeaderViewHolder(
                    inflater.inflate(
                            R.layout.vertical_tabs_section_header, parent, false));
        }
        return new TabViewHolder(
                inflater.inflate(R.layout.vertical_tabs_tab_row, parent, false));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        VerticalTabsItem item = mItems.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(item);
        } else if (holder instanceof TabViewHolder) {
            ((TabViewHolder) holder).bind(item);
        }
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    private class HeaderViewHolder extends RecyclerView.ViewHolder {
        final View mHeader;
        final View mColorDot;
        final TextView mTitle;
        final ImageView mChevron;

        HeaderViewHolder(View itemView) {
            super(itemView);
            mHeader = itemView;
            mColorDot = itemView.findViewById(R.id.vertical_tabs_group_color_dot);
            mTitle = itemView.findViewById(R.id.vertical_tabs_section_title);
            mChevron = itemView.findViewById(R.id.vertical_tabs_section_chevron);
        }

        void bind(VerticalTabsItem item) {
            mTitle.setText(item.mTitle);
            if (item.mColor != 0) {
                mColorDot.setVisibility(View.VISIBLE);
                mColorDot.getBackground().mutate().setTint(item.mColor);
            } else {
                mColorDot.setVisibility(View.GONE);
            }
            mChevron.setRotation(item.mCollapsed ? 0f : 90f);
            mHeader.setOnClickListener(
                    v -> mDelegate.onSectionHeaderClicked(item.mSectionId));
        }
    }

    private class TabViewHolder extends RecyclerView.ViewHolder {
        final View mRow;
        final View mIndent;
        final ImageView mFavicon;
        final TextView mTitle;
        final View mClose;

        TabViewHolder(View itemView) {
            super(itemView);
            mRow = itemView;
            mIndent = itemView.findViewById(R.id.vertical_tabs_row_indent);
            mFavicon = itemView.findViewById(R.id.vertical_tabs_favicon);
            mTitle = itemView.findViewById(R.id.vertical_tabs_title);
            mClose = itemView.findViewById(R.id.vertical_tabs_close);
        }

        void bind(VerticalTabsItem item) {
            Tab tab = item.mTab;
            boolean active = tab.getId() == mActiveTabId;
            mRow.setActivated(active);

            ViewGroup.LayoutParams indentParams = mIndent.getLayoutParams();
            indentParams.width = item.mIsGroupChild && !mRailMode
                    ? mRow.getResources().getDimensionPixelSize(
                            R.dimen.vertical_tabs_row_indent_step)
                    : 0;
            mIndent.setLayoutParams(indentParams);

            if (mRailMode) {
                mTitle.setVisibility(View.GONE);
                mClose.setVisibility(View.GONE);
            } else {
                mTitle.setVisibility(View.VISIBLE);
                mClose.setVisibility(View.VISIBLE);
                mTitle.setText(tab.getTitle());
            }

            mFaviconLoader.load(tab, mFavicon);

            mRow.setOnClickListener(v -> mDelegate.onTabClicked(tab));
            mClose.setOnClickListener(v -> mDelegate.onTabCloseClicked(tab));
            mRow.setOnLongClickListener(
                    v -> {
                        mDelegate.onTabLongClicked(tab, mRow);
                        return true;
                    });
            mRow.setContentDescription(
                    mRow.getResources().getString(
                            R.string.vertical_tabs_tab_row_description, tab.getTitle()));
        }
    }
}
