// Copyright 2026 The Titanium Authors
// Use of this source code is governed by a GPL-2.0-only style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vertical_tabs;

import androidx.annotation.IntDef;

import org.chromium.build.annotations.Nullable;
import org.chromium.chrome.browser.tab.Tab;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A single row in the vertical tabs list: either a section header (Pinned / tab group / Tabs) or a
 * tab row.
 */
class VerticalTabsItem {
    @IntDef({Type.SECTION_HEADER, Type.TAB})
    @Retention(RetentionPolicy.SOURCE)
    @interface Type {
        int SECTION_HEADER = 0;
        int TAB = 1;
    }

    /** Section id for the pinned tabs section. */
    static final String SECTION_PINNED = "pinned";
    /** Section id for the plain (ungrouped, unpinned) tabs section. */
    static final String SECTION_TABS = "tabs";

    final @Type int mType;
    /** For headers: section id ("pinned", "tabs" or the group token string). */
    final String mSectionId;
    /** For headers: display title. */
    final String mTitle;
    /** For headers: color dot argb, or 0 when no dot should be shown. */
    final int mColor;
    /** For headers: whether the section is rendered collapsed. */
    final boolean mCollapsed;
    /** For tab rows: the tab. */
    final @Nullable Tab mTab;
    /** For tab rows: whether the row is indented as a child of a group section. */
    final boolean mIsGroupChild;

    private VerticalTabsItem(@Type int type, String sectionId, String title, int color,
            boolean collapsed, @Nullable Tab tab, boolean isGroupChild) {
        mType = type;
        mSectionId = sectionId;
        mTitle = title;
        mColor = color;
        mCollapsed = collapsed;
        mTab = tab;
        mIsGroupChild = isGroupChild;
    }

    static VerticalTabsItem createSectionHeader(
            String sectionId, String title, int color, boolean collapsed) {
        return new VerticalTabsItem(
                Type.SECTION_HEADER, sectionId, title, color, collapsed, null, false);
    }

    static VerticalTabsItem createTab(Tab tab, boolean isGroupChild) {
        return new VerticalTabsItem(
                Type.TAB, "", "", 0, false, tab, isGroupChild);
    }

    /** Stable identity used for diffing. */
    String key() {
        return mType == Type.SECTION_HEADER ? "h:" + mSectionId : "t:" + mTab.getId();
    }

    /** Content hash used for diffing (whether the row needs re-binding). */
    long contentHash() {
        if (mType == Type.SECTION_HEADER) {
            return (mTitle != null ? mTitle.hashCode() : 0) * 31 + mColor * 7 + (mCollapsed ? 1 : 0);
        }
        return mTab.getId() * 31
                + (mTab.getTitle() != null ? mTab.getTitle().hashCode() : 0)
                + (mIsGroupChild ? 5 : 0);
    }
}
