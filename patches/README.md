# Titanium patches

Git patches applied to the pristine Chromium tree (at Vanadium's pinned `VERSION`) with
`git am` in [`../build.sh`](../build.sh), **immediately after** the Vanadium patch series
(which is renamed `vanadium → titanium` first). Keep patches ordered by their numeric prefix.

| Patch | Purpose |
|---|---|
| `0001-Add-VerticalTabsAndroid-feature-flag.patch` | `VerticalTabsAndroid` base::Feature (default-enabled), Java constant, `kFeaturesExposedToJava` registration, permanent `flag-metadata.json` entry (chrome://flags overridable). |
| `0002-Add-vertical-tabs-strings.patch` | Strings for the vertical tabs sidebar in `android_chrome_strings.grd`. |
| `0003-Wire-vertical-tabs-into-ChromeTabbedActivity.patch` | Coordinator creation in `finishNativeInitialization`, keyboard shortcuts in `dispatchKeyEvent`, teardown in `onDestroyInternal`. |

New Java sources and resources for the sidebar are **not** carried here; they live in the repo
(`titanium/chromium_src/...`, `res/vertical_tabs/`) and are copied + registered into the tree by
[`../patch.sh`](../patch.sh) via the `chrome_java_ext_sources.gni` /
`chrome_app_java_resources_ext_sources.gni` extension mechanism (see
[`../docs/vertical-tabs-recon.md`](../docs/vertical-tabs-recon.md), item R1).

## Regenerating after a Chromium bump

The patches are generated against the **post-Vanadium** state of the target files (several
Vanadium patches touch the same files). To regenerate:

1. Fetch the pristine files at the new pinned tag, e.g.
   `https://chromium.googlesource.com/chromium/src/+/refs/tags/<VERSION>/<path>?format=TEXT`
   (base64-decode) for the five files listed in the generator below.
2. Run the generator, which re-applies the relevant Vanadium patches to a scratch repo and
   emits the three patches with correct context:

   ```shell
   python3 patches/gen_patches.py
   ```

3. Sanity-check that each patch still applies: `git apply --check patches/*.patch` inside a
   tree with the Vanadium series applied.
4. Re-verify the anchors listed in [`../docs/vertical-tabs-recon.md`](../docs/vertical-tabs-recon.md)
   (R1–R8) against the new version — APIs occasionally move (e.g. tab model classes moved to
   `chrome/browser/tabmodel/android/...`, group ids became `org.chromium.base.Token`).
