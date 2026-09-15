"""云端签名流程测试：所有 keytool/git 写操作均模拟，本机不生成签名或编译 Google 渠道。"""
import contextlib
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import github_aab as cli
import google_aab_ci as ci


class SigningTests(unittest.TestCase):
    def test_configure_preserves_existing_password(self):
        with patch.object(cli, "gh", return_value=json.dumps([{"name": cli.SIGNING_SECRET}]).encode()) as gh:
            cli.configure_signing("owner/repo")
            self.assertEqual(1, gh.call_count)

    def test_existing_key_without_password_cannot_be_rekeyed(self):
        responses = [b'{"isEmpty":false}', json.dumps({"tree": [{"path": ci.KEYSTORE_PATH}]}).encode()]
        with patch.object(cli, "command", side_effect=responses), patch.object(cli, "gh", return_value=b"[]") as gh:
            with self.assertRaisesRegex(RuntimeError, "恢复原密码"):
                cli.configure_signing("owner/repo")
            self.assertEqual(1, gh.call_count)

    def test_local_execution_cannot_generate_keystore(self):
        with patch.dict(ci.os.environ, {"GITHUB_ACTIONS": "false"}), patch.object(ci.subprocess, "run") as run:
            with self.assertRaisesRegex(RuntimeError, "只允许在 GitHub Actions"):
                ci.ensure_signing()
            run.assert_not_called()

    def test_main_key_is_reused_without_generation_or_commit(self):
        with tempfile.TemporaryDirectory() as temp, contextlib.redirect_stdout(io.StringIO()):
            root = Path(temp)
            git_results = [b"", ci.KEYSTORE_PATH.encode(), b"existing-keystore", b"signing-sha"]
            with patch.object(ci, "ROOT", root), patch.dict(ci.os.environ, {
                "GITHUB_ACTIONS": "true", "ANDROID_SIGNING_STORE_PASSWORD": "test-only-password",
                "GITHUB_ENV": str(root / "github.env"),
            }), patch.object(ci, "git", side_effect=git_results), patch.object(ci, "publish_keystore") as publish, patch.object(ci.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, b"cert", b"")) as run:
                ci.ensure_signing()
                publish.assert_not_called()
                self.assertEqual(1, run.call_count)
                self.assertIn("-exportcert", run.call_args.args[0])
                self.assertEqual(b"existing-keystore", (root / ci.KEYSTORE_PATH).read_bytes())

    def test_failed_command_never_discloses_secret_input(self):
        result = subprocess.CompletedProcess([], 1, b"", b"sensitive error")
        with patch.object(cli.subprocess, "run", return_value=result):
            with self.assertRaises(RuntimeError) as failure:
                cli.command(["gh", "secret", "set", "TEST"], data=b"sensitive input")
            self.assertNotIn("sensitive", str(failure.exception))


class BoundaryTests(unittest.TestCase):
    def test_ci_entry_point_rejects_local_execution(self):
        with patch.dict(ci.os.environ, {"GITHUB_ACTIONS": "false"}):
            with self.assertRaisesRegex(RuntimeError, "只允许在 GitHub Actions"):
                ci.main()

    def test_dirty_checkout_never_dispatches(self):
        with patch.object(cli, "command", return_value=b" M app/build.gradle.kts\n"), patch.object(cli, "gh") as gh:
            with self.assertRaisesRegex(RuntimeError, "未提交"):
                cli.dispatch("owner/repo", "main", False, False)
            gh.assert_not_called()

    def test_unpushed_head_never_dispatches(self):
        with patch.object(cli, "command", side_effect=[b"", b"local-sha", b"remote-sha"]), patch.object(cli, "gh") as gh:
            with self.assertRaisesRegex(RuntimeError, "HEAD"):
                cli.dispatch("owner/repo", "main", False, False)
            gh.assert_not_called()

    def test_firebase_package_mismatch_fails_before_build(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            config = root / "app/src/google"
            config.mkdir(parents=True)
            (config / "config.properties").write_text("applicationId=com.expected\nversionName=1.0\nversionCode=1\n")
            (config / "google-services.json").write_text('{"client": []}')
            with patch.object(ci, "ROOT", root):
                with self.assertRaisesRegex(RuntimeError, "不匹配"):
                    ci.validate_channel()

    def test_missing_password_cannot_generate_signature(self):
        with patch.dict(ci.os.environ, {"GITHUB_ACTIONS": "true", "ANDROID_SIGNING_STORE_PASSWORD": ""}):
            with self.assertRaisesRegex(RuntimeError, "configure-signing"):
                ci.ensure_signing()

    def test_collect_rejects_path_traversal(self):
        with self.assertRaisesRegex(RuntimeError, "非法"):
            ci.collect("../../outside.aab")


if __name__ == "__main__":
    unittest.main()
