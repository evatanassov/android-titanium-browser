# Vertical Tabs — Recon Notes (Chromium 152.0.7977.64)

Findings for [`plans/vertical-tabs-zen-arc-plan.md`](../plans/vertical-tabs-zen-arc-plan.md) Phase 0
(recon checklist R1–R8), verified against the pristine Chromium tree at Vanadium's pinned version
`152.0.7977.64` and the Vanadium patch series in [`vanadium/patches/`](../vanadium/patches).

## R1 — How `titanium/chromium_src` Java files are registered in `chrome_java`

Vanadium patch `0035-Extend-chrome-android-java-targets-sources-and-deps.patch` makes
`chrome/android/BUILD.gn` extend the `chrome_java` target:

```gn
# From java_sources.gni.
sources = chrome_java_sources

# Extension of sources, deps, and srcjar_deps for chrome_java target
import("//titanium/chromium_src/chrome/android/chrome_java_ext_sources.gni")
sources += chrome_java_ext_sources
```

(after the `vanadium → titanium` rename done by [`build.sh`](../build.sh)).

`chrome_java_ext_sources.gni` exposes two lists:

- `chrome_java_ext_full_path_sources` — GN absolute paths (`//titanium/...`).
- `chrome_java_ext_rel_path_sources` — paths **relative to `titanium/chromium_src/chrome/android/`**
  (`rebase_path()` inside the .gni resolves them against the .gni file's own directory). Example
  from Vanadium patch 0140: `"java/src/org/chromium/chrome/browser/privacy/settings/PrivacySettingsExt.java"`.

**Decision:** new Java files live in this repo at
`titanium/chromium_src/chrome/android/java/src/org/chromium/chrome/browser/vertical_tabs/*.java`
(mirroring the in-tree location), are copied into the tree by [`patch.sh`](../patch.sh), and are
registered by appending entries to `chrome_java_ext_rel_path_sources` via `sed`.

The same patch also provides `chrome_app_java_resources_ext_sources.gni` (imported by
`android_resources("chrome_app_java_resources")`) with `chrome_app_java_resources_ext_rel_path_sources`.
New resources (layouts/drawables/dimens) are copied to
`titanium/chromium_src/chrome/android/java/res_vertical_tabs/` and registered the same way.
This avoids patching `chrome/android/BUILD.gn` at all.

## R2 — Root layout hierarchy of `ChromeTabbedActivity`

`chrome/android/java/res_app/layout/main.xml` is a `<merge>` inflated into the activity content
(`android.R.id.content`, a `FrameLayout`). Its main child is
`org.chromium.components.browser_ui.widget.CoordinatorLayoutForPointer` with
`android:id="@+id/coordinator"`, which hosts (among many stubs/overlays):

- `<include layout="@layout/compositor_view_holder"/>` (web content surface),
- the top toolbar (`control_container`),
- bottom bar / bottom app bar stubs, tab switcher stub, message container, sheet container.

**Decision:** the `VerticalTabsPanelView` is added programmatically to `android.R.id.content` as a
sibling of `@id/coordinator` (no layout file surgery). In DOCKED mode the sidebar docks by setting
`MarginLayoutParams.marginStart/marginEnd` on `@id/coordinator`, which shifts the toolbar, web
content and bottom bar together — the Zen/Arc look — and the compositor resizes naturally
(same mechanism as free-form window resizing).

## R3 — Per-window size signal (ToolbarTablet vs ToolbarPhone)

`ToolbarManager` (M152) uses `DeviceFormFactor.isNonMultiDisplayContextOnTablet(mActivity)`
(`org.chromium.ui.base.DeviceFormFactor`) to pick the tablet toolbar. The tab switcher/Hub uses
pane-based UI (`LayoutType.HUB`).

**Decision:** mode selection (DOCKED vs DRAWER) is primarily width-based
(`contentRoot.getWidth() / density >= 600dp`), re-evaluated on every layout change — this is
per-window and resize-aware (DeX drag-resize, foldables). `DeviceFormFactor` is not required.

## R4 — Insetting web content

No `CompositorViewHolder` changes are needed with the margin approach from R2: shrinking the
coordinator resizes the compositor surface exactly like a window resize; touch routing and window
insets follow the view bounds. Fallback (overlay-docked sidebar without content inset) is a
one-line change (skip the margin update).

## R5 — `TabModelObserver` event surface (M152)

`chrome/browser/tabmodel/android/java/src/org/chromium/chrome/browser/tabmodel/TabModelObserver.java`:

- `didAddTab(Tab tab, @TabLaunchType int type, @TabCreationState int creationState, boolean markedForSelection)`
- `tabRemoved(Tab tab)`, `didMoveTab(Tab tab, int newIndex, int curIndex)`
- `didSelectTab(Tab tab, @TabSelectionType int type, int lastId)`
- `tabClosureUndone(Tab tab)`, `tabClosureCommitted(Tab tab)`, `restoreCompleted()`
- Group events: `onTabGroupCreated(Token)`, `onTabGroupRemoving(Token)`, `onTabGroupMoved(Token, int)`,
  `onTabGroupVisualsChanged(Token)` (`org.chromium.base.Token` — group ids are `Token`s, not strings)
- Pin events: `willChangePinState(Tab)`, `didChangePinState(Tab)`

Register via `TabModelSelector.addObserverToAllModels(TabModelObserver)` /
`removeObserverFromAllModels(...)` — covers regular + incognito models.

Other confirmed M152 APIs:

- `Tab`: `getId()`, `getTitle()`, `getUrl(): GURL`, `isIncognito()`, `getTabGroupId(): @Nullable Token`,
  `getIsPinned()/setIsPinned(boolean)` — **native pinned tabs exist on Android in M152**
  (`TabModel.pinTab(int tabId, boolean showUngroupDialog, @Nullable TabModelActionListener)`,
  `TabModel.unpinTab(int tabId)`, `getPinnedTabsCount()`, `findFirstNonPinnedTabIndex()`).
- `TabModel extends TabList`: `getCount()`, `getTabAt(int)`, `indexOf(Tab)`, `index()`,
  `isIncognito()`, `setIndex(int, @TabSelectionType int)`, `moveTab(int id, int newIndex)`,
  `getTabById(int)`.
- `TabModelSelector`: `getModel(boolean)`, `getCurrentModel()`, `getCurrentTab()`,
  `openNewTab(LoadUrlParams, @TabLaunchType int, @Nullable Tab, boolean)`,
  `tryCloseTab(TabClosureParams, boolean allowDialog)` with
  `TabClosureParams.closeTab(tab).build()` / `closeTabs(List<Tab>)`.
- Selecting a tab (as used by M152 code):
  `tabModel.setIndex(tabModel.indexOf(tab), TabSelectionType.FROM_USER)`.
- Opening a tab from Chrome UI: `TabLaunchType.FROM_CHROME_UI`.

## R6 — Grid tab switcher (Hub) show/hide signals

M152 replaced the classic tab-switcher layout with the pane-based Hub. `LayoutManager`
(`chrome/browser/ui/android/layouts/.../LayoutManager.java`, interface extending
`LayoutStateProvider`) offers `addObserver(LayoutStateObserver)` with
`onStartedShowing(@LayoutType int)` / `onStartedHiding(@LayoutType int)` and
`isLayoutVisible(@LayoutType int)`. The overview/tab-switcher layout is `LayoutType.HUB`.

**Decision:** the coordinator observes `LayoutType.HUB` via `ChromeActivity.getLayoutManager()`
and hides the sidebar while the Hub is visible.

## R7 — Keyboard shortcut dispatch path

`ChromeTabbedActivity.dispatchKeyEvent(KeyEvent)` first delegates to
`KeyboardShortcuts.dispatchKeyEvent(...)`, then to the extensions toolbar coordinator, then falls
back to `super`. **Decision:** insert the vertical-tabs shortcut handling after the extensions
block (Ctrl+Shift+B toggle, Ctrl+Shift+]/[ next/previous tab).

## R8 — Favicon resolver + theme utilities

`org.chromium.chrome.browser.tab_ui.TabListFaviconProvider` (used by the grid tab switcher):

- `TabListFaviconProvider(Context, @TabListMode int, int faviconCornerRadiusDimen,
  @Nullable TabWebContentsFaviconDelegate)` — any dimen resource works for the corner radius;
  `TabListMode.VERTICAL` exists.
- `initWithNative(Profile)` (e.g. `Profile.getLastUsedRegularProfile()`), `destroy()`.
- `getFaviconDrawableForTabAsync(TabFaviconMetadata(tab, url, isIncognito, isInTabGroup),
  Callback<Drawable>)` — handles NTP/internal pages, group proxy favicons, fallback globe.

## Back press

`BackPressManager` (`chrome/browser/back_press/android/...`) — `addHandler(BackPressHandler, @Type int)`
/ `removeHandler(BackPressHandler)`. `BackPressHandler`
(`org.chromium.components.browser_ui.widget.gesture.BackPressHandler`) requires
`getHandleBackPressChangedSupplier(): MonotonicObservableSupplier<Boolean>` and
`handleBackPress(): @BackPressResult int`. Pattern used by M152 handlers:
`ObservableSuppliers.createNonNull(false)` (`SettableNonNullObservableSupplier`).
The sidebar registers with `Type.NATIVE_PAGE` (low priority: other overlays close first).

## Flag plumbing (verified formats)

- `ChromeFeatureList.java`: constants live in a `keep-sorted` block;
  `public static final String VERTICAL_TABS_ANDROID = "VerticalTabsAndroid";` sorts between
  `VERIFY_STARTUP_SIGNIN_STATE` and `VIRTUAL_KEYBOARD_TRANSIENT_INNER_HEIGHT_FIX`.
- `chrome/browser/flags/android/chrome_feature_list.cc`: add
  `BASE_FEATURE(kVerticalTabsAndroid, base::FEATURE_ENABLED_BY_DEFAULT);` (sorted block) and
  `&kVerticalTabsAndroid,` to `kFeaturesExposedToJava` (sorted array) — this is what makes the
  feature settable from Java and visible in `chrome://flags`.
- `chrome/browser/flag-metadata.json`: append an entry with `"expiry_milestone": -1`.
- Default-enabled natively, so no `chrome_browser_field_trials.cc` override is required.

## Toolbar entry point

`toolbar_phone.xml` / `toolbar_tablet.xml` both contain a `toolbar_buttons` LinearLayout with a
`ToggleTabStackButton`. A `ViewStub` (`vertical_tabs_toggle_stub`, layout
`@layout/vertical_tabs_toolbar_toggle`) is injected before the `ToggleTabStackButton` via `sed`
(same proven pattern as the extensions toolbar stub), inflated by the coordinator at runtime.

## Delivery summary

| Artifact | Mechanism |
|---|---|
| Java sources (`vertical_tabs` package) | repo `titanium/chromium_src/...` → copied by `patch.sh`; registered in `chrome_java_ext_sources.gni` |
| Resources (`res_vertical_tabs`) | repo `res/vertical_tabs/` → copied by `patch.sh`; registered in `chrome_app_java_resources_ext_sources.gni` |
| Toolbar toggle stub | `sed` into `toolbar_phone.xml` + `toolbar_tablet.xml` (patch.sh) |
| Feature flag, strings, `ChromeTabbedActivity` wiring | git patches in [`patches/`](../patches), applied by `build.sh` after the Vanadium series |
