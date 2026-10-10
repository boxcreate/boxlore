"""Exercise the actual required-check shell without GitHub or credentials."""

import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import textwrap
import unittest


def page(nodes, next_page=False, cursor=None):
    return {"data": {"repository": {"pullRequest": {"reviewThreads": {
        "nodes": nodes, "pageInfo": {"hasNextPage": next_page, "endCursor": cursor},
    }}}}}


def thread(resolved=False, outdated=False, author="coderabbitai[bot]"):
    return {"isResolved": resolved, "isOutdated": outdated, "comments": {"nodes": [{
        "author": {"login": author}, "url": "https://example.invalid/review", "body": "Fix this finding",
    }]}}


class ReviewThreadsTest(unittest.TestCase):
    def run_gate(self, pages):
        self.assertIsNotNone(shutil.which("jq"), "jq is required, as on the Actions runner")
        root = Path(__file__).resolve().parents[2]
        workflow = (root / ".github/workflows/coderabbit-threads-resolved.yml").read_text()
        shell = textwrap.dedent(workflow.split("        run: |\n", 1)[1])
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            (directory / "pages.json").write_text(json.dumps(pages))
            gh = directory / "gh"
            gh.write_text("#!" + sys.executable + "\n" + textwrap.dedent('''\
                import json
                import os
                from pathlib import Path
                root = Path(os.environ["FIXTURE_DIR"])
                count = root / "count"
                index = int(count.read_text()) if count.exists() else 0
                count.write_text(str(index + 1))
                print(json.dumps(json.loads((root / "pages.json").read_text())[index]))
                '''))
            gh.chmod(0o755)
            env = dict(os.environ, PATH=temp + os.pathsep + os.environ["PATH"], FIXTURE_DIR=temp,
                       EVENT_NAME="pull_request", PR_NUMBER_EVENT="1107", PR_NUMBER_INPUT="", HEAD_SHA_PR="fixture", REPO="boxcreate/boxlore")
            return subprocess.run(["bash", "-c", shell], env=env, capture_output=True, text=True, timeout=10)

    def test_unresolved_bot_finding_fails_without_stdin(self):
        result = self.run_gate([page([thread()])])
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("1 unresolved CodeRabbit", result.stdout)

    def test_resolved_outdated_and_other_authors_do_not_block(self):
        result = self.run_gate([page([thread(resolved=True), thread(outdated=True), thread(author="maintainer")])])
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("open_coderabbit_threads=0", result.stdout)

    def test_all_pages_are_counted(self):
        result = self.run_gate([page([], next_page=True, cursor="next"), page([thread()])])
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("1 unresolved CodeRabbit", result.stdout)

    def test_invalid_or_incomplete_api_responses_fail_closed(self):
        for response in ({"errors": [{"message": "denied"}]}, {}, page([], next_page=True), page([{"isResolved": False}])):
            with self.subTest(response=response):
                result = self.run_gate([response])
                self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
                self.assertIn("::error::", result.stdout)


if __name__ == "__main__":
    unittest.main()
