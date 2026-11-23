"""Adversarial tests for the independent appearance-only source audit."""
import copy
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "scripts/audit_visual_only.py"
spec = importlib.util.spec_from_file_location("audit_visual_only", SCRIPT)
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)
A = 'xmlns:android="http://schemas.android.com/apk/res/android"'
APP = "CalorieWidget/app/"
RES = APP + "src/main/res/"
LAYOUT = RES + "layout/activity_input.xml"
GRADLE = APP + "build.gradle.kts"
CODE = APP + "src/main/java/com/example/caloriewidget/data/CalorieRepository.kt"
COLORS = RES + "values/colors.xml"
THEMES = RES + "values/themes.xml"


class VisualOnlyAuditTest(unittest.TestCase):
    def setUp(self):
        self.original = {
            "README.md": b"Existing README content\n",
            "CalorieWidget/README.md": b"Existing project instructions\n",
            GRADLE: b'versionCode = 2\nversionName = "1.1"\ndependencies { implementation("existing:1") }\n',
            CODE: b"fun balance(goal: Long, intake: Long) = goal - intake\n",
            RES + "values/strings.xml": b'<resources><string name="save">Save</string></resources>',
            RES + "drawable/ic_launcher.xml": b'<vector/>',
            APP + "src/test/java/com/example/caloriewidget/RepositoryTest.kt": b"existing test\n",
            COLORS: b'<resources><color name="surface">#FFFFFF</color></resources>',
            THEMES: b'<resources><style name="Theme.App" parent="Theme.Material3.DayNight.NoActionBar"><item name="colorPrimary">@color/surface</item></style></resources>',
            LAYOUT: ('<ScrollView ' + A + ' android:layout_width="match_parent" android:layout_height="match_parent">'
                     '<LinearLayout android:orientation="vertical" android:layout_width="match_parent" android:layout_height="wrap_content">'
                     '<TextView android:id="@+id/title" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Existing title"/>'
                     '<com.google.android.material.textfield.TextInputLayout android:hint="Amount">'
                     '<com.google.android.material.textfield.TextInputEditText android:id="@+id/amount_input" android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="numberDecimal" android:maxLength="14"/>'
                     '</com.google.android.material.textfield.TextInputLayout>'
                     '<RadioGroup android:id="@+id/unit_group" android:orientation="horizontal">'
                     '<RadioButton android:id="@+id/unit_kcal" android:checked="true" android:text="kcal"/>'
                     '<RadioButton android:id="@+id/unit_kj" android:text="kJ"/>'
                     '</RadioGroup>'
                     '<TextView android:id="@+id/remaining" android:text="2000" android:maxLines="1" android:autoSizeTextType="uniform" android:autoSizeMinTextSize="14sp" android:autoSizeMaxTextSize="34sp"/>'
                     '<TextView android:id="@+id/form_error" android:accessibilityLiveRegion="polite"/>'
                     '<Button android:id="@+id/save_button" android:text="Save" android:contentDescription="Save record"/>'
                     '</LinearLayout></ScrollView>').encode(),
        }
        self.current = copy.deepcopy(self.original)
        self.current[GRADLE] = self.original[GRADLE].replace(b"versionCode = 2", b"versionCode = 3").replace(b'"1.1"', b'"2.0"')

    def expect_rejection(self, text=None):
        with self.assertRaises(audit.AuditError) as context:
            audit.audit_files(self.original, self.current)
        if text:
            self.assertIn(text, str(context.exception))

    def alter_layout(self, old, new):
        self.assertIn(old.encode(), self.current[LAYOUT])
        self.current[LAYOUT] = self.current[LAYOUT].replace(old.encode(), new.encode())

    def test_accepts_visual_attributes_grouping_and_appended_readme(self):
        self.alter_layout('android:text="Existing title"', 'android:text="Existing title" android:textSize="26sp" android:fontFamily="sans-serif-medium"')
        self.alter_layout('<TextView android:id="@+id/title"', '<FrameLayout android:padding="20dp"><TextView android:id="@+id/title"')
        self.alter_layout('android:fontFamily="sans-serif-medium"/>', 'android:fontFamily="sans-serif-medium"/></FrameLayout>')
        self.current["README.md"] += b"\nVersion 2.0 visual delivery\n"
        self.current[COLORS] = self.current[COLORS].replace(b"#FFFFFF", b"#F7F5EF")
        self.assertTrue(audit.audit_files(self.original, self.current)["passed"])

    def test_deleted_or_changed_text_is_rejected(self):
        for replacement in ('', 'android:text="New title"'):
            with self.subTest(replacement=replacement):
                self.current[LAYOUT] = self.original[LAYOUT]
                self.alter_layout('android:text="Existing title"', replacement)
                self.expect_rejection("Content")

    def test_id_type_order_and_accessibility_changes_are_rejected(self):
        for old, new in [('@+id/title', '@+id/another'), ('<Button ', '<TextView '),
                         ('android:contentDescription="Save record"', 'android:contentDescription="Different"'),
                         ('android:accessibilityLiveRegion="polite"', 'android:accessibilityLiveRegion="none"')]:
            with self.subTest(change=old):
                self.current[LAYOUT] = self.original[LAYOUT]
                self.alter_layout(old, new)
                self.expect_rejection("Content")

    def test_input_constraints_default_unit_and_interactivity_changes_are_rejected(self):
        for old, new in [('android:maxLength="14"', 'android:maxLength="15"'),
                         ('android:inputType="numberDecimal"', 'android:inputType="text"'),
                         ('android:checked="true"', 'android:checked="false"'),
                         ('android:text="Existing title"', 'android:text="Existing title" android:clickable="true"'),
                         ('android:text="Existing title"', 'android:text="Existing title" android:visibility="gone"'),
                         ('android:text="Save"', 'android:text="Save" android:enabled="false"')]:
            with self.subTest(change=new):
                self.current[LAYOUT] = self.original[LAYOUT]
                self.alter_layout(old, new)
                self.expect_rejection("Content")

    def test_new_interactive_container_is_rejected(self):
        self.alter_layout('<TextView android:id="@+id/title"', '<FrameLayout android:clickable="true"><TextView android:id="@+id/title"')
        self.alter_layout('android:text="Existing title"/>', 'android:text="Existing title"/></FrameLayout>')
        self.expect_rejection("Content")

    def test_new_container_content_is_rejected(self):
        self.alter_layout('<TextView android:id="@+id/title"', '<FrameLayout android:contentDescription="Added content"><TextView android:id="@+id/title"')
        self.alter_layout('android:text="Existing title"/>', 'android:text="Existing title"/></FrameLayout>')
        self.expect_rejection("Content")

    def test_new_zero_sized_container_cannot_hide_existing_content(self):
        self.alter_layout('<TextView android:id="@+id/title"', '<FrameLayout android:layout_height="0dp"><TextView android:id="@+id/title"')
        self.alter_layout('android:text="Existing title"/>', 'android:text="Existing title"/></FrameLayout>')
        self.expect_rejection("Zero-sized")

    def test_wrapping_radio_button_breaks_group_and_is_rejected(self):
        self.alter_layout('<RadioButton android:id="@+id/unit_kcal"', '<LinearLayout><RadioButton android:id="@+id/unit_kcal"')
        self.alter_layout('android:checked="true" android:text="kcal"/>', 'android:checked="true" android:text="kcal"/></LinearLayout>')
        self.expect_rejection("Input grouping")

    def test_truncation_and_restricted_autosizing_are_rejected(self):
        for old, new in [('android:text="Existing title"', 'android:text="Existing title" android:maxLines="1"'),
                         ('android:text="Existing title"', 'android:text="Existing title" android:ellipsize="end"'),
                         ('autoSizeMinTextSize="14sp"', 'autoSizeMinTextSize="20sp"'),
                         ('autoSizeMaxTextSize="34sp"', 'autoSizeMaxTextSize="28sp"'),
                         ('autoSizeTextType="uniform"', 'autoSizeTextType="none"'),
                         ('android:layout_width="wrap_content"', 'android:layout_width="0dp"')]:
            with self.subTest(change=new):
                self.current[LAYOUT] = self.original[LAYOUT]
                self.alter_layout(old, new)
                self.expect_rejection()

    def test_business_code_strings_existing_tests_launcher_and_new_files_are_protected(self):
        for path in [CODE, RES + "values/strings.xml", RES + "drawable/ic_launcher.xml",
                     APP + "src/test/java/com/example/caloriewidget/RepositoryTest.kt", "unexpected.txt"]:
            with self.subTest(path=path):
                before = self.current.copy()
                self.current[path] = self.current.get(path, b"") + b"changed"
                self.expect_rejection("outside")
                self.current = before

    def test_deletion_is_rejected_even_for_approved_visual_file(self):
        del self.current[COLORS]
        self.expect_rejection("deleted")

    def test_gradle_version_must_be_exact_and_dependencies_unchanged(self):
        for value in [self.original[GRADLE], self.current[GRADLE].replace(b"existing:1", b"existing:2"),
                      self.current[GRADLE].replace(b"versionCode = 3", b"versionCode = 4"),
                      self.current[GRADLE].replace(b'"2.0"', b'"2.1"')]:
            with self.subTest(value=value):
                old = self.current[GRADLE]
                self.current[GRADLE] = value
                self.expect_rejection()
                self.current[GRADLE] = old

    def test_readme_may_only_append_and_values_cannot_add_strings_or_input_flags(self):
        for path, value in [
            ("README.md", b"Rewritten README\n"),
            (COLORS, b'<resources><string name="new">Added content</string></resources>'),
            (THEMES, b'<resources><style name="Theme.App" parent="Theme.Material3.DayNight.NoActionBar"><item name="android:inputType">text</item></style></resources>'),
            (THEMES, b'<resources><style name="Theme.App" parent="Theme.Material3.DayNight.NoActionBar"><item name="android:visibility">gone</item></style></resources>'),
            (THEMES, b'<resources><style name="Theme.App" parent="Theme.Material3.DayNight"><item name="colorPrimary">@color/surface</item></style></resources>'),
        ]:
            with self.subTest(path=path, value=value):
                old = self.current[path]
                self.current[path] = value
                self.expect_rejection()
                self.current[path] = old

    def test_action_icon_only_color_can_change(self):
        path = RES + "drawable/ic_edit.xml"
        self.original[path] = ('<vector ' + A + '><path android:pathData="M1,2" android:fillColor="#111111"/></vector>').encode()
        self.current[path] = self.original[path].replace(b"#111111", b"@color/text_primary")
        self.assertTrue(audit.audit_files(self.original, self.current)["passed"])
        self.current[path] = self.current[path].replace(b"M1,2", b"M3,4")
        self.expect_rejection("beyond fill")

    def test_new_styles_may_only_use_visual_attributes(self):
        path = RES + "values/styles.xml"
        self.current[path] = b'<resources><style name="Widget.CalorieWidget.Primary" parent="Widget.Material3.Button"><item name="cornerRadius">16dp</item><item name="android:textAllCaps">false</item></style></resources>'
        self.assertTrue(audit.audit_files(self.original, self.current)["passed"])
        self.current[path] = self.current[path].replace(b"cornerRadius", b"android:focusable")
        self.expect_rejection("Nonvisual style")

    def test_git_working_tree_audit_includes_nonignored_untracked_files(self):
        parent = ROOT / ".tools/visual-only-audit-tests"
        parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=parent) as folder:
            repo = Path(folder)
            env = dict(os.environ, GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull,
                       GIT_AUTHOR_NAME="Synthetic Test", GIT_COMMITTER_NAME="Synthetic Test",
                       GIT_AUTHOR_EMAIL="contributors@example.invalid", GIT_COMMITTER_EMAIL="contributors@example.invalid",
                       GIT_AUTHOR_DATE="2000-01-01T00:00:00+0000", GIT_COMMITTER_DATE="2000-01-01T00:00:00+0000")
            def git(*args):
                return subprocess.check_output(["git", "-C", str(repo), *args], env=env, stderr=subprocess.PIPE)
            git("init", "-q")
            for path, value in self.original.items():
                file = repo / path
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_bytes(value)
            git("add", ".")
            git("commit", "-qm", "Synthetic baseline")
            baseline = git("rev-parse", "HEAD").decode().strip()
            for path, value in self.current.items():
                (repo / path).write_bytes(value)
            self.assertTrue(audit.audit_repository(repo, baseline)["passed"])
            code = repo / CODE
            code.chmod(0o755)
            with self.assertRaisesRegex(audit.AuditError, "executable mode"):
                audit.audit_repository(repo, baseline)
            code.chmod(0o644)
            (repo / "untracked-feature.kt").write_text("fun unexpectedFeature() {}\n")
            result = subprocess.run([sys.executable, str(SCRIPT), "--repo", str(repo), "--baseline", baseline],
                                    capture_output=True, text=True)
            self.assertEqual(1, result.returncode)
            self.assertFalse(json.loads(result.stdout)["passed"])
            self.assertIn("untracked-feature.kt", json.loads(result.stdout)["reason"])


if __name__ == "__main__":
    unittest.main()
