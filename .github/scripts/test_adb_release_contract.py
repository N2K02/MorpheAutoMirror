from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class AdbReleaseContractTest(unittest.TestCase):
    def test_stable_publication_requires_a_merged_pr_to_main_adb(self):
        workflow = (ROOT / ".github/workflows/build.yml").read_text()
        release = workflow.split("\n  release:\n", 1)[1]
        self.assertIn("needs: verify", release)
        self.assertIn("github.event.pull_request.merged == true", release)
        self.assertIn("github.event.pull_request.base.ref == 'main-adb'", release)
        self.assertIn("github.event.pull_request.merge_commit_sha", release)
        self.assertNotIn("workflow_dispatch:", workflow)

    def test_experimental_publication_and_updater_match_adb(self):
        workflow = (ROOT / ".github/workflows/build.yml").read_text()
        updater = (ROOT / "app/src/main/java/dev/n2k02/morpheautomirror/LiveUpdater.kt").read_text()
        self.assertIn("experimental-adb-pr-", workflow)
        self.assertIn("MorpheAutoMirror-ADB-", workflow)
        self.assertIn("!github.event.pull_request.draft", workflow)
        self.assertNotIn("--clobber", workflow)
        self.assertIn('releaseTrack = "adb"', updater)


if __name__ == "__main__":
    unittest.main()
