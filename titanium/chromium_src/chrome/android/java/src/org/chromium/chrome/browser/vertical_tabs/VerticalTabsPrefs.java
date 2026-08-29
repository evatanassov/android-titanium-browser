// Copyright 2026 The Titanium Authors
// Use of this source code is governed by a GPL-2.0-only style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vertical_tabs;

import android.content.SharedPreferences;

import org.chromium.base.ContextUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * Persisted state for the vertical tabs panel: sidebar position, width, collapsed (rail) state,
 * drawer visibility, selected model and per-group collapse state.
 */
public class VerticalTabsPrefs {
    private static final String KEY_POSITION_RIGHT = "vertical_tabs.position_right";
    private static final String KEY_WIDTH_DP = "vertical_tabs.width_dp";
    private static final String KEY_RAIL_COLLAPSED = "vertical_tabs.rail_collapsed";
    private static final String KEY_DRAWER_OPEN = "vertical_tabs.drawer_open";
    private static final String KEY_COLLAPSED_GROUPS = "vertical_tabs.collapsed_groups";

    public static final int DEFAULT_WIDTH_DP = 280;
    public static final int MIN_WIDTH_DP = 200;
    public static final int MAX_WIDTH_DP = 400;

    private final SharedPreferences mPreferences;

    public VerticalTabsPrefs() {
        mPreferences = ContextUtils.getAppSharedPreferences();
    }

    /** Whether the sidebar is docked on the right side of the window (default: left). */
    public boolean isPositionRight() {
        return mPreferences.getBoolean(KEY_POSITION_RIGHT, false);
    }

    public void setPositionRight(boolean right) {
        mPreferences.edit().putBoolean(KEY_POSITION_RIGHT, right).apply();
    }

    /** The docked sidebar width in dp. */
    public int getWidthDp() {
        return mPreferences.getInt(
                KEY_WIDTH_DP, DEFAULT_WIDTH_DP);
    }

    public void setWidthDp(int widthDp) {
        int clamped = Math.max(MIN_WIDTH_DP, Math.min(MAX_WIDTH_DP, widthDp));
        mPreferences.edit().putInt(KEY_WIDTH_DP, clamped).apply();
    }

    /** Whether the docked sidebar is collapsed to the icon rail. */
    public boolean isRailCollapsed() {
        return mPreferences.getBoolean(KEY_RAIL_COLLAPSED, false);
    }

    public void setRailCollapsed(boolean collapsed) {
        mPreferences.edit().putBoolean(KEY_RAIL_COLLAPSED, collapsed).apply();
    }

    /** Whether the drawer was left open in the previous session. */
    public boolean isDrawerOpen() {
        return mPreferences.getBoolean(KEY_DRAWER_OPEN, false);
    }

    public void setDrawerOpen(boolean open) {
        mPreferences.edit().putBoolean(KEY_DRAWER_OPEN, open).apply();
    }

    /** Whether the given tab group section is rendered collapsed. */
    public boolean isGroupCollapsed(String groupId) {
        return mPreferences.getStringSet(KEY_COLLAPSED_GROUPS, null) != null
                && mPreferences.getStringSet(KEY_COLLAPSED_GROUPS, null).contains(groupId);
    }

    public void setGroupCollapsed(String groupId, boolean collapsed) {
        Set<String> collapsedGroups =
                new HashSet<>(mPreferences.getStringSet(KEY_COLLAPSED_GROUPS, new HashSet<>()));
        if (collapsed) {
            collapsedGroups.add(groupId);
        } else {
            collapsedGroups.remove(groupId);
        }
        mPreferences.edit().putStringSet(KEY_COLLAPSED_GROUPS, collapsedGroups).apply();
    }
}
