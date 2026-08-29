// Copyright 2026 The Titanium Authors
// Use of this source code is governed by a GPL-2.0-only style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vertical_tabs;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab_ui.TabListFaviconProvider;
import org.chromium.chrome.browser.tab_ui.TabListMode;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads and caches favicons for the vertical tabs list, backed by the same favicon resolver used
 * by the grid tab switcher ({@link TabListFaviconProvider}).
 */
class VerticalTabsFaviconLoader {
    private static final int CACHE_LIMIT = 512;

    private final TabListFaviconProvider mProvider;
    private final Map<String, Drawable> mCache = new HashMap<>();

    VerticalTabsFaviconLoader(Context context) {
        mProvider = new TabListFaviconProvider(
                context, TabListMode.VERTICAL, R.dimen.vertical_tabs_favicon_corner_radius, null);
        mProvider.initWithNative(Profile.getLastUsedRegularProfile());
    }

    /**
     * Asynchronously loads the favicon for {@code tab} into {@code view}. The view is tagged with
     * the tab id so stale callbacks (recycled rows) are dropped.
     */
    void load(Tab tab, ImageView view) {
        String cacheKey = cacheKey(tab);
        Drawable cached = mCache.get(cacheKey);
        if (cached != null) {
            view.setImageDrawable(cached);
            return;
        }

        view.setTag(R.id.vertical_tabs_favicon, tab.getId());
        mProvider.getFaviconDrawableForTabAsync(
                new TabListFaviconProvider.TabFaviconMetadata(
                        /* tab= */ tab,
                        /* url= */ tab.getUrl(),
                        /* isIncognito= */ tab.isIncognito(),
                        /* isInTabGroup= */ tab.getTabGroupId() != null),
                (Callback<Drawable>) (drawable) -> {
                    if (drawable == null) return;
                    if (mCache.size() >= CACHE_LIMIT) mCache.clear();
                    mCache.put(cacheKey, drawable);
                    Object tag = view.getTag(R.id.vertical_tabs_favicon);
                    if (tag instanceof Integer && (Integer) tag == tab.getId()) {
                        view.setImageDrawable(drawable);
                    }
                });
    }

    /** Drops all cached favicons (e.g. on theme change). */
    void clearCache() {
        mCache.clear();
    }

    void destroy() {
        mCache.clear();
        mProvider.destroy();
    }

    private static String cacheKey(Tab tab) {
        return tab.getId() + ":" + (tab.getUrl() != null ? tab.getUrl().getSpec() : "");
    }
}
