// Copyright 2026 The Titanium Authors
// Use of this source code is governed by a GPL-2.0-only style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vertical_tabs;

import org.chromium.chrome.browser.flags.ChromeFeatureList;

/** Accessor for the {@code VerticalTabsAndroid} feature state. */
public final class VerticalTabsFeature {
    private static Boolean mEnabledForTesting;

    private VerticalTabsFeature() {}

    /** Returns whether the vertical tabs sidebar feature is enabled. */
    public static boolean isEnabled() {
        if (mEnabledForTesting != null) return mEnabledForTesting;
        return ChromeFeatureList.isEnabled(ChromeFeatureList.VERTICAL_TABS_ANDROID);
    }

    /** Overrides the feature state for testing. */
    public static void setEnabledForTesting(Boolean enabled) {
        mEnabledForTesting = enabled;
    }
}
