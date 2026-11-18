#!/usr/bin/env python3
"""Self-test for legacy_ui_guard.py: every rule gets one violating sample and must fail (plan gate 1, last item).

    python tools/ci/test_legacy_ui_guard.py
"""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import legacy_ui_guard as guard  # noqa: E402

PKG = "app/src/main/java/com/github/andreyasadchy/xtra"
CLEAN = {
    "app/src/main/AndroidManifest.xml": '<manifest><application><activity android:name=".ui.main.MainActivity"/></application></manifest>',
    f"{PKG}/ui/main/Screen.kt": "package x\n\n@Composable\nfun Home() {\n    LagoonBottomSheet(onDismiss = {}) { Text(\"hi\") }\n}\n",
    "app/src/main/res/layout/activity_main.xml": "<FrameLayout/>",
    "app/src/main/res/values/styles.xml": '<resources><style name="DarkTheme" parent="Theme.AppCompat.NoActionBar"/></resources>',
    "docs/ui-overhaul/u9/surfaces.md": "| `MainActivity` | main |\n| `Screen.kt#Home` | sheet |\n",
    "tools/ci/legacy_ui_allowlist.txt": "layout app/src/main/res/layout/activity_main.xml  # main window\n",
}


class GuardTest(unittest.TestCase):

    def problems(self, changes):
        with tempfile.TemporaryDirectory() as root:
            files = dict(CLEAN)
            files.update(changes)
            for path, text in files.items():
                if text is None:
                    continue
                full = os.path.join(root, path)
                os.makedirs(os.path.dirname(full), exist_ok=True)
                with open(full, "w", encoding="utf-8") as f:
                    f.write(text)
            return guard.run(root)

    def assertFails(self, changes, rule):
        problems = self.problems(changes)
        self.assertTrue(any(p.startswith(f"[{rule}]") for p in problems), f"expected a [{rule}] problem, got {problems}")

    def test_clean_tree_passes(self):
        self.assertEqual([], self.problems({}))

    def test_legacy_class(self):
        self.assertFails({f"{PKG}/ui/A.kt": "fun a() { MaterialAlertDialogBuilder(ctx).show() }"}, "legacy")

    def test_legacy_string_literal(self):
        self.assertFails({f"{PKG}/ui/A.kt": 'val c = Class.forName("com.google.android.material.chip.Chip")'}, "legacy")

    def test_legacy_in_resources(self):
        self.assertFails({"app/src/main/res/values/styles.xml": '<resources><style name="X" parent="Theme.Material3.Dark"/></resources>'}, "legacy")

    def test_device_default_theme(self):
        self.assertFails({f"{PKG}/ui/A.kt": "val t = android.R.style.Theme_DeviceDefault_NoActionBar"}, "legacy")

    def test_toast(self):
        self.assertFails({f"{PKG}/ui/A.kt": "fun a() { Toast.makeText(ctx, \"x\", 0).show() }"}, "legacy")

    def test_proguard_and_baseline_profile(self):
        self.assertFails({"app/proguard-rules.pro": "-keep class com.google.android.material.** { *; }"}, "legacy")
        self.assertFails({"app/src/main/baseline-prof.txt": "Lcom/google/android/material/theme/MaterialComponentsViewInflater;"}, "legacy")

    def test_comments_do_not_count(self):
        self.assertEqual([], self.problems({f"{PKG}/ui/main/Screen.kt": CLEAN[f"{PKG}/ui/main/Screen.kt"] + "// MaterialAlertDialogBuilder was here\n/* PopupMenu */\n"}))

    def test_compose_dialog(self):
        self.assertFails({f"{PKG}/ui/A.kt": "@Composable fun a() { AlertDialog(onDismissRequest = {}) {} }"}, "compose")

    def test_m3_import_outside_design(self):
        self.assertFails({f"{PKG}/ui/A.kt": "import androidx.compose.material3.Slider\n"}, "m3-import")
        self.assertEqual([], self.problems({f"{PKG}/ui/design/components/X.kt": "import androidx.compose.material3.Slider\n",
                                            f"{PKG}/ui/B.kt": "import androidx.compose.material3.Text\n"}))

    def test_screen_height(self):
        self.assertFails({f"{PKG}/ui/A.kt": "val h = LocalConfiguration.current.screenHeightDp * 0.5f"}, "screen-height")

    def test_hard_coded_colour(self):
        self.assertFails({f"{PKG}/ui/A.kt": "val c = Color(0xFF5B4BB7)"}, "color")
        self.assertEqual([], self.problems({f"{PKG}/ui/design/theme/C.kt": "val c = Color(0xFF5B4BB7)"}))

    def test_unregistered_layout(self):
        self.assertFails({"app/src/main/res/layout/dialog_x.xml": "<LinearLayout/>"}, "layout")

    def test_unregistered_sheet(self):
        self.assertFails({f"{PKG}/ui/B.kt": "@Composable\nfun Other() {\n    ActionSheet(title = \"x\") {}\n}\n"}, "surfaces")

    def test_stale_surface_row(self):
        self.assertFails({"docs/ui-overhaul/u9/surfaces.md": CLEAN["docs/ui-overhaul/u9/surfaces.md"] + "| `Gone.kt#Old` | x |\n"}, "surfaces")

    def test_unused_allowlist_entry(self):
        self.assertFails({"tools/ci/legacy_ui_allowlist.txt": CLEAN["tools/ci/legacy_ui_allowlist.txt"] + f"color {PKG}/ui/Nope.kt  # gone\n"}, "allowlist")

    def test_allowlist_entry_allows(self):
        self.assertEqual([], self.problems({
            f"{PKG}/ui/A.kt": "val c = Color(0xFF5B4BB7)",
            "tools/ci/legacy_ui_allowlist.txt": CLEAN["tools/ci/legacy_ui_allowlist.txt"] + f"color {PKG}/ui/A.kt  # sample\n",
        }))


if __name__ == "__main__":
    unittest.main()
