#!/usr/bin/env python3
"""Generates the Titanium vertical-tabs git patches from pristine Chromium files.

Run after a Chromium version bump:
1. Fetch the pristine files at the new pinned tag into $RECON_DIR (default /tmp/recon2),
   base64-decoding googlesource `?format=TEXT` responses. The file list is in FILES below.
2. Ensure the vanadium submodule is checked out (git submodule update --init).
3. python3 gen_patches.py

The generator re-applies the (renamed) Vanadium patches that touch the target files, so the
emitted patches are based on the post-Vanadium tree exactly like the real build.
"""

import os
import subprocess
import sys

RECON = os.environ.get("RECON_DIR", "/tmp/recon2")
VERSION = os.environ.get("CHROMIUM_VERSION", "152.0.7977.64")
OUT = os.path.dirname(os.path.abspath(__file__))
WORK = "/tmp/patchgen"
VANADIUM_DIR = os.path.join(os.path.dirname(OUT), "vanadium", "patches")

FILES = {
    "chrome/browser/flags/android/java/src/org/chromium/chrome/browser/flags/ChromeFeatureList.java":
        f"{RECON}/ChromeFeatureList.java",
    "chrome/browser/flags/android/chrome_feature_list.cc":
        f"{RECON}/chrome_feature_list.cc",
    "chrome/browser/flag-metadata.json":
        f"{RECON}/flag-metadata.json",
    "chrome/browser/ui/android/strings/android_chrome_strings.grd":
        f"{RECON}/android_chrome_strings.grd",
    "chrome/android/java/src/org/chromium/chrome/browser/ChromeTabbedActivity.java":
        f"{RECON}/ChromeTabbedActivity.java",
}

# Vanadium patches that touch the files above (applied in order, filtered to the target
# files). Update this list when rebasing to a new Chromium version.
VANADIUM_PATCHES = [
    "0001-Vanadium-string-rebranding-at-chrome-layer",
    "0027-downstream-string-additions-for-chrome-specific-brow",
    "0129-remove-Google-prefix-from-storage-settings-label",
    "0149-Disable-default-browser-promotion-features-by-defaul",
    "0152-Toggle-for-closing-tabs-on-exit",
    "0153-Toggle-for-opening-external-links-in-incognito-tabs",
    "0216-Revert-Fixit-Update-Password-Manager-to-Google-Passw",
    "0253-Apply-toggle-for-opening-links-in-incognito-for-expl",
    "0255-always-enable-autofill-screens-regardless-of-autofil",
    "0272-Restore-chrome-browser-password_entry_edit-for-local",
    "0273-Restore-local-password-manager-UI",
    "0276-import-and-export-bookmarks-feature",
]

INCLUDES = [
    "--include=chrome/browser/flags/android/java/src/org/chromium/chrome/browser/flags/ChromeFeatureList.java",
    "--include=chrome/browser/flags/android/chrome_feature_list.cc",
    "--include=chrome/browser/flag-metadata.json",
    "--include=chrome/browser/ui/android/strings/android_chrome_strings.grd",
    "--include=chrome/android/java/src/org/chromium/chrome/browser/ChromeTabbedActivity.java",
]


def run(cmd, **kw):
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True, **kw)
    if result.returncode != 0:
        print(result.stdout)
        print(result.stderr)
        sys.exit(f"command failed: {cmd}")
    return result.stdout


def replace(content, old, new):
    if old not in content:
        sys.exit(f"ANCHOR NOT FOUND:\n{old[:200]}")
    return content.replace(old, new, 1)


def setup():
    run(f"rm -rf {WORK}")
    os.makedirs(WORK)
    run("git init -q", cwd=WORK)
    run("git config user.email t@t && git config user.name t", cwd=WORK)
    for rel, src in FILES.items():
        dst = os.path.join(WORK, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        run(f"cp '{src}' '{dst}'")
    run("git add -A && git commit -qm base", cwd=WORK)

    for name in VANADIUM_PATCHES:
        src = os.path.join(VANADIUM_DIR, name + ".patch")
        renamed = os.path.join(WORK, "vp_" + name + ".patch")
        with open(src, encoding="utf-8") as f:
            content = f.read()
        content = content.replace("VANADIUM", "TITANIUM")
        content = content.replace("Vanadium", "Titanium")
        content = content.replace("vanadium", "titanium")
        with open(renamed, "w", encoding="utf-8") as f:
            f.write(content)
        cmd = "git apply --whitespace=nowarn " + " ".join(INCLUDES) + f" '{renamed}'"
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True, cwd=WORK)
        if result.returncode != 0:
            print(f"note: {name} did not apply (ok if no hunks for target files)")
    run("git add -A && git commit -qm vanadium-base", cwd=WORK)


def read(rel):
    with open(os.path.join(WORK, rel), encoding="utf-8") as f:
        return f.read()


def write(rel, content):
    with open(os.path.join(WORK, rel), "w", encoding="utf-8") as f:
        f.write(content)


def emit(name, subject, body):
    diff = run("git diff", cwd=WORK)
    run("git checkout -q -- .", cwd=WORK)
    header = (
        f"From 0000000000000000000000000000000000000000 Mon Sep 17 00:00:00 2001\n"
        f"From: Titanium Browser <titanium-browser@users.noreply.github.com>\n"
        f"Date: Sat, 29 Aug 2026 00:00:00 +0000\n"
        f"Subject: [PATCH] {subject}\n\n"
        f"{body}\n---\n"
    )
    with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
        f.write(header + diff)
    print(f"wrote {name} ({len(diff.splitlines())} diff lines)")


def patch_flag():
    rel = "chrome/browser/flags/android/java/src/org/chromium/chrome/browser/flags/ChromeFeatureList.java"
    write(rel, replace(read(rel),
        '    public static final String VERIFY_STARTUP_SIGNIN_STATE = "VerifyStartupSigninState";\n',
        '    public static final String VERIFY_STARTUP_SIGNIN_STATE = "VerifyStartupSigninState";\n'
        '    public static final String VERTICAL_TABS_ANDROID = "VerticalTabsAndroid";\n'))

    rel = "chrome/browser/flags/android/chrome_feature_list.cc"
    c = read(rel)
    c = replace(c,
        "BASE_FEATURE(kVerifyStartupSigninState, base::FEATURE_ENABLED_BY_DEFAULT);\n",
        "BASE_FEATURE(kVerifyStartupSigninState, base::FEATURE_ENABLED_BY_DEFAULT);\n"
        "BASE_FEATURE(kVerticalTabsAndroid, base::FEATURE_ENABLED_BY_DEFAULT);\n")
    c = replace(c, "    &kVerifyStartupSigninState,\n",
                "    &kVerifyStartupSigninState,\n    &kVerticalTabsAndroid,\n")
    write(rel, c)

    rel = "chrome/browser/flag-metadata.json"
    c = read(rel).rstrip("\n")
    if not c.endswith("  }\n]"):
        sys.exit("flag-metadata.json tail anchor not found")
    entry = (
        ',\n'
        '  {\n'
        '    // Titanium: vertical tabs sidebar (Zen/Arc-style); never expires.\n'
        '    "name": "VerticalTabsAndroid",\n'
        '    "owners": [\n'
        '      "titanium-browser@users.noreply.github.com"\n'
        '    ],\n'
        '    "expiry_milestone": -1\n'
        '  }\n'
        ']'
    )
    write(rel, c[:-1] + entry + "\n")

    emit("0001-Add-VerticalTabsAndroid-feature-flag.patch",
         "Add VerticalTabsAndroid feature flag",
         "Adds the VerticalTabsAndroid base::Feature (enabled by default), exposes it to\n"
         "Java via ChromeFeatureList and kFeaturesExposedToJava, and registers permanent\n"
         "flag metadata so it can be toggled from chrome://flags.")


def patch_strings():
    rel = "chrome/browser/ui/android/strings/android_chrome_strings.grd"
    anchor = (
        '      <message name="IDS_MENU_NEW_TAB" desc="Menu item for opening a new tab. '
        '[CHAR_LIMIT=27]">\n'
        '        New tab\n'
        '      </message>\n'
    )
    messages = anchor + '''      <!-- Titanium: vertical tabs sidebar -->
      <message name="IDS_VERTICAL_TABS_TOGGLE_SIDEBAR" desc="Content description and tooltip for the button that shows or hides the vertical tabs sidebar. [CHAR_LIMIT=30]">
        Toggle vertical tabs sidebar
      </message>
      <message name="IDS_VERTICAL_TABS_NEW_TAB" desc="Content description for the new tab button in the vertical tabs sidebar. [CHAR_LIMIT=30]">
        New tab
      </message>
      <message name="IDS_VERTICAL_TABS_SEARCH_TABS" desc="Placeholder text for the tab search box in the vertical tabs sidebar. [CHAR_LIMIT=30]">
        Search tabs
      </message>
      <message name="IDS_VERTICAL_TABS_SHOW_INCOGNITO" desc="Content description for the button that switches the vertical tabs sidebar between regular and Incognito tabs. [CHAR_LIMIT=30]">
        Show Incognito tabs
      </message>
      <message name="IDS_VERTICAL_TABS_SECTION_PINNED" desc="Title of the pinned tabs section in the vertical tabs sidebar. [CHAR_LIMIT=30]">
        Pinned
      </message>
      <message name="IDS_VERTICAL_TABS_SECTION_TABS" desc="Title of the plain tabs section in the vertical tabs sidebar. [CHAR_LIMIT=30]">
        Tabs
      </message>
      <message name="IDS_VERTICAL_TABS_SECTION_GROUP" desc="Title of a tab group section in the vertical tabs sidebar when the group has no name. [CHAR_LIMIT=30]">
        Tab group
      </message>
      <message name="IDS_VERTICAL_TABS_TAB_ROW_DESCRIPTION" desc="Content description for a tab row in the vertical tabs sidebar. [CHAR_LIMIT=none]">
        Tab: <ph name="TAB_TITLE">%1$s<ex>Example domain</ex></ph>
      </message>
      <message name="IDS_VERTICAL_TABS_CONTEXT_PIN" desc="Context menu item to pin a tab in the vertical tabs sidebar. [CHAR_LIMIT=30]">
        Pin tab
      </message>
      <message name="IDS_VERTICAL_TABS_CONTEXT_UNPIN" desc="Context menu item to unpin a tab in the vertical tabs sidebar. [CHAR_LIMIT=30]">
        Unpin tab
      </message>
      <message name="IDS_VERTICAL_TABS_CONTEXT_DUPLICATE" desc="Context menu item to duplicate a tab in the vertical tabs sidebar. [CHAR_LIMIT=30]">
        Duplicate tab
      </message>
      <message name="IDS_VERTICAL_TABS_CONTEXT_CLOSE_OTHERS" desc="Context menu item to close all tabs except the selected one in the vertical tabs sidebar. [CHAR_LIMIT=30]">
        Close other tabs
      </message>
      <message name="IDS_VERTICAL_TABS_CONTEXT_CLOSE" desc="Context menu item to close a tab in the vertical tabs sidebar. [CHAR_LIMIT=30]">
        Close tab
      </message>
'''
    write(rel, replace(read(rel), anchor, messages))
    emit("0002-Add-vertical-tabs-strings.patch",
         "Add vertical tabs strings",
         "Adds the strings used by the vertical tabs sidebar to\n"
         "android_chrome_strings.grd.")


def patch_activity():
    rel = "chrome/android/java/src/org/chromium/chrome/browser/ChromeTabbedActivity.java"
    c = read(rel)
    c = replace(c,
        "import org.chromium.chrome.browser.toolbar.ToolbarManager;\n",
        "import org.chromium.chrome.browser.toolbar.ToolbarManager;\n"
        "import org.chromium.chrome.browser.vertical_tabs.VerticalTabsCoordinator;\n"
        "import org.chromium.chrome.browser.vertical_tabs.VerticalTabsFeature;\n")
    c = replace(c,
        "    private final TabSwitcherBackPressHandlerManager mDragHandlerManager =\n"
        "            new TabSwitcherBackPressHandlerManager();\n",
        "    private final TabSwitcherBackPressHandlerManager mDragHandlerManager =\n"
        "            new TabSwitcherBackPressHandlerManager();\n\n"
        "    // Titanium: vertical tabs sidebar (Zen/Arc-style).\n"
        "    private VerticalTabsCoordinator mVerticalTabsCoordinator;\n")
    c = replace(c,
        "            super.finishNativeInitialization();\n",
        "            super.finishNativeInitialization();\n\n"
        "            // Titanium: wire the vertical tabs sidebar.\n"
        "            if (VerticalTabsFeature.isEnabled()) {\n"
        "                mVerticalTabsCoordinator =\n"
        "                        new VerticalTabsCoordinator(\n"
        "                                this,\n"
        "                                getTabModelSelector(),\n"
        "                                mBackPressManager,\n"
        "                                getLayoutManager(),\n"
        "                                (ViewGroup) findViewById(android.R.id.content));\n"
        "            }\n")
    c = replace(c,
        "        return result != null ? result : super.dispatchKeyEvent(event);\n",
        "        // Titanium: vertical tabs keyboard shortcuts.\n"
        "        if (mVerticalTabsCoordinator != null\n"
        "                && mVerticalTabsCoordinator.handleKeyEvent(event)) {\n"
        "            return true;\n"
        "        }\n\n"
        "        return result != null ? result : super.dispatchKeyEvent(event);\n")
    c = replace(c,
        "        super.onDestroyInternal();\n",
        "        if (mVerticalTabsCoordinator != null) {\n"
        "            mVerticalTabsCoordinator.destroy();\n"
        "            mVerticalTabsCoordinator = null;\n"
        "        }\n\n"
        "        super.onDestroyInternal();\n")
    write(rel, c)
    emit("0003-Wire-vertical-tabs-into-ChromeTabbedActivity.patch",
         "Wire vertical tabs into ChromeTabbedActivity",
         "Creates the VerticalTabsCoordinator during finishNativeInitialization when the\n"
         "VerticalTabsAndroid feature is enabled, adds keyboard shortcuts (Ctrl+Shift+B\n"
         "toggle, Ctrl+Shift+]/[ next/previous tab) and tears the coordinator down on\n"
         "destroy.")


def main():
    setup()
    patch_flag()
    patch_strings()
    patch_activity()
    print("done")


if __name__ == "__main__":
    main()
