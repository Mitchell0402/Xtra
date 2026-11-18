#!/usr/bin/env python3
"""Self-test for motion_guard.py: every rule gets one violating sample and must fail, and the allowed forms pass.

    python tools/ci/test_motion_guard.py
"""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import motion_guard as guard  # noqa: E402

UI = "app/src/main/java/com/github/andreyasadchy/xtra/ui"
CLEAN = {
    f"{UI}/main/Screen.kt": (
        "package x\n\n@Composable\nfun Home(visible: Boolean) {\n"
        "    val motion = Lagoon.motion\n"
        "    AnimatedVisibility(visible, enter = motion.expand(), exit = motion.collapse()) { Text(\"hi\") }\n"
        "    AnimatedContent(targetState = visible, transitionSpec = { motion.forward() }, label = \"x\") { Text(\"$it\") }\n"
        "    LaunchedEffect(visible) { progress.animateTo(1f, motion.playerSpring()) }\n"
        "    LaunchedEffect(visible) { progress.animateTo(targetValue = 1f, animationSpec = motion.layout()) }\n"
        "    Box(Modifier.animateContentSize(motion.move()))\n"
        "    // tween(250) in a comment is fine\n"
        "    val s = \"spring(\"\n"
        "}\n"
    ),
    f"{UI}/design/theme/LagoonMotion.kt": "fun <T> move() = tween<T>(300)\nval t = rememberInfiniteTransition(label = \"ok\")\n",
    "tools/ci/motion_allowlist.txt": "",
}


class MotionGuardTest(unittest.TestCase):

    def problems(self, changes):
        with tempfile.TemporaryDirectory() as root:
            files = dict(CLEAN)
            files.update(changes)
            for path, text in files.items():
                full = os.path.join(root, path)
                os.makedirs(os.path.dirname(full), exist_ok=True)
                with open(full, "w", encoding="utf-8") as f:
                    f.write(text)
            return guard.run(root)

    def assertFails(self, code, rule, path=f"{UI}/a/A.kt"):
        problems = self.problems({path: code})
        self.assertTrue(any(p.startswith(f"[{rule}]") for p in problems), f"expected a [{rule}] problem, got {problems}")

    def test_clean_tree_passes(self):
        self.assertEqual([], self.problems({}))

    def test_tween(self):
        self.assertFails("val a = fadeIn(tween(250))", "spec")

    def test_typed_spring(self):
        self.assertFails("val a = spring<Float>(dampingRatio = 0.5f)", "spec")

    def test_qualified_infinite(self):
        self.assertFails("val a = androidx.compose.animation.core.infiniteRepeatable(x)", "spec")

    def test_spec_in_design_components(self):
        self.assertFails("val a = keyframes { }", "spec", f"{UI}/design/components/B.kt")

    def test_animate_to_default(self):
        self.assertFails("suspend fun f() { dismiss.animateTo(0f) }", "animate-to")

    def test_animate_to_velocity_only(self):
        self.assertFails("suspend fun f() { a.animateTo(0f, initialVelocity = v) }", "animate-to")

    def test_visibility_without_specs(self):
        self.assertFails("@Composable fun f() { AnimatedVisibility(visible) { } }", "visibility")

    def test_visibility_enter_only(self):
        self.assertFails("@Composable fun f() { AnimatedVisibility(visible, enter = motion.expand()) { } }", "visibility")

    def test_content_without_spec(self):
        self.assertFails("@Composable fun f() { AnimatedContent(targetState = s, label = \"x\") { } }", "content")

    def test_animate_item_default(self):
        self.assertFails("@Composable fun f() { Box(Modifier.animateItem()) }", "item")

    def test_infinite_transition(self):
        self.assertFails("@Composable fun f() { val t = rememberInfiniteTransition(label = \"x\") }", "infinite",
                         f"{UI}/design/components/C.kt")

    def test_multiline_call(self):
        self.assertFails("@Composable fun f() {\n  AnimatedVisibility(\n    visible,\n    enter = fadeIn(),\n  ) { }\n}", "visibility")

    def test_allowlist_count_must_match(self):
        code = "val a = fadeIn(tween(250))\nval b = fadeOut(tween(250))\n"
        ok = self.problems({f"{UI}/a/A.kt": code, "tools/ci/motion_allowlist.txt": f"spec {UI}/a/A.kt 2  # reason\n"})
        self.assertEqual([], ok)
        more = self.problems({f"{UI}/a/A.kt": code + "val c = tween(1)\n",
                              "tools/ci/motion_allowlist.txt": f"spec {UI}/a/A.kt 2  # reason\n"})
        self.assertTrue(any(p.startswith("[spec]") for p in more), more)

    def test_stale_allowlist_entry(self):
        problems = self.problems({"tools/ci/motion_allowlist.txt": f"spec {UI}/a/Gone.kt 1  # reason\n"})
        self.assertTrue(any(p.startswith("[allowlist]") for p in problems), problems)


if __name__ == "__main__":
    unittest.main()
