"""Local regression checks; no real SSH, registry, or container operations."""

import os
import pathlib
import hashlib
import shlex
import subprocess
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
BASH = os.environ.get("BASH", "C:/Program Files/Git/bin/bash.exe" if os.name == "nt" else "bash")


def execute(args, cwd=None, env=None, input=None):
    # Preserve LF bytes on Windows too, especially when validating SHA-256.
    result = subprocess.run(args, cwd=cwd, env=env,
                            input=input.encode("utf-8") if input is not None else None,
                            capture_output=True)
    result.stdout = result.stdout.decode("utf-8", errors="replace").replace("\r\n", "\n")
    result.stderr = result.stderr.decode("utf-8", errors="replace").replace("\r\n", "\n")
    return result


class ReleaseValidationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="release-git-")
        self.addCleanup(self.temp.cleanup)
        self.directory = pathlib.Path(self.temp.name)
        self.remote = self.directory / "origin.git"
        self.repo = self.directory / "repo"
        self.git("init", "--bare", self.remote.as_posix(), cwd=self.directory)
        self.git("clone", self.remote.as_posix(), self.repo.as_posix(), cwd=self.directory)
        self.git("checkout", "-b", "main")
        self.git("config", "user.name", "Release Test")
        self.git("config", "user.email", "release@example.invalid")
        self.git("commit", "--allow-empty", "-m", "test baseline")
        self.git("push", "origin", "main")
        self.sha = self.git("rev-parse", "HEAD").strip()

    def git(self, *args, cwd=None):
        result = execute(["git", *args], cwd=cwd or self.repo)
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout

    def validate(self, current, existing=(), sha=None, attempt="1"):
        script = ROOT / ".github/scripts/validate-release.sh"
        self.assertTrue(script.exists(), "release validator has not been extracted")
        for tag in existing:
            self.git("tag", "-a", tag, "-m", tag)
            self.git("push", "origin", tag)
        output = self.directory / "outputs"
        env = dict(os.environ, GITHUB_REF_NAME=current, GITHUB_SHA=sha or self.sha,
                   GITHUB_OUTPUT=output.as_posix(), GITHUB_RUN_ATTEMPT=attempt)
        result = execute([BASH, script.as_posix()], cwd=self.repo, env=env)
        return result, output.read_text() if output.exists() else ""

    def test_new_version_produces_job_output(self):
        result, output = self.validate("v0.1.6", ("v0.1.5",))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("version=v0.1.6", output)

    def test_latest_version_can_rerun_with_annotated_tag(self):
        result, _ = self.validate("v0.1.6", ("v0.1.5", "v0.1.6"), attempt="2")
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_old_version_rerun_is_rejected(self):
        result, output = self.validate("v0.1.5", ("v0.1.6",), attempt="2")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(output, "")

    def test_leading_zero_is_rejected(self):
        result, _ = self.validate("v0.01.6")
        self.assertNotEqual(result.returncode, 0)

    def test_commit_outside_main_is_rejected(self):
        self.git("checkout", "-b", "feature")
        self.git("commit", "--allow-empty", "-m", "not on main")
        result, _ = self.validate("v0.1.6", sha=self.git("rev-parse", "HEAD").strip())
        self.assertNotEqual(result.returncode, 0)


class DeploymentCompletionTests(unittest.TestCase):
    def guard(self):
        script = ROOT / "deploy/server/deploy.sh"
        self.assertTrue(script.exists(), "server deployment has not been extracted")
        return script.read_text(encoding="utf-8").split("umask 077", 1)[0]

    def test_incomplete_exit_is_not_success(self):
        result = execute([BASH, "-s"], input=self.guard() + "true\n")
        self.assertEqual(result.returncode, 1)

    def test_completed_exit_succeeds(self):
        result = execute([BASH, "-s"], input=self.guard() + "DEPLOYMENT_COMPLETED=true\n")
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_original_failure_status_survives(self):
        result = execute([BASH, "-s"], input=self.guard() + "exit 7\n")
        self.assertEqual(result.returncode, 7)


class ShellSyntaxTests(unittest.TestCase):
    def test_all_release_scripts_are_linux_shell_files(self):
        scripts = list((ROOT / ".github/scripts").glob("*.sh")) + [ROOT / "deploy/server/deploy.sh"]
        self.assertEqual(len(scripts), 4)
        for script in scripts:
            with self.subTest(script=script.name):
                self.assertNotIn(b"\r", script.read_bytes())
                result = execute([BASH, "-n", script.as_posix()])
                self.assertEqual(result.returncode, 0, result.stderr)


class ServerBootstrapTests(unittest.TestCase):
    """Run the real upload/install/execute bootstrap in a temporary directory.

    Only the fixed server path and privileged directory creation are replaced;
    SHA-256, file streaming, installation, Bash execution and exit codes are real.
    """

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="release-bootstrap-")
        self.addCleanup(self.temp.cleanup)
        self.directory = pathlib.Path(self.temp.name)
        runner = (ROOT / ".github/scripts/deploy-via-ssh.sh").read_text(encoding="utf-8")
        self.bootstrap = runner.split("BOOTSTRAP=$(cat <<'REMOTE'\n", 1)[1].split("\nREMOTE", 1)[0]
        self.bootstrap = self.bootstrap.replace("script_dir=/opt/maplehush/scripts",
                                                "script_dir=" + shlex.quote(self.directory.as_posix()))
        self.bootstrap = 'install() { mkdir -p -- "${@: -1}"; }\n' + self.bootstrap
        self.args = ["v0.1.6", "a" * 40, "frontend@sha256:" + "b" * 64,
                     "backend@sha256:" + "c" * 64, "123-1", "https://example.invalid"]

    def upload(self, script, digest=None):
        digest = digest or hashlib.sha256(script.encode()).hexdigest()
        return execute([BASH, "-c", self.bootstrap, "--", digest, *self.args], input=script)

    def test_verified_file_runs_with_arguments_and_closed_stdin(self):
        script = 'set -eu\nif read -r line; then exit 42; fi\nprintf "ARGS:%s\\n" "$@"\n'
        result = self.upload(script)
        self.assertEqual(result.returncode, 0, result.stderr)
        for argument in self.args:
            self.assertIn("ARGS:" + argument, result.stdout)
        self.assertEqual(len(list(self.directory.glob("deploy-*.sh"))), 1)
        self.assertEqual(list(self.directory.glob(".deploy-upload-*")), [])

    def test_corrupt_upload_is_not_installed_or_executed(self):
        result = self.upload('echo MUST_NOT_EXECUTE\n', "0" * 64)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("MUST_NOT_EXECUTE", result.stdout)
        self.assertEqual(list(self.directory.iterdir()), [])

    def test_deployment_failure_status_reaches_caller(self):
        result = self.upload("exit 7\n")
        self.assertEqual(result.returncode, 7)

    def test_same_run_cannot_replace_script_with_different_content(self):
        self.assertEqual(self.upload("echo ORIGINAL\n").returncode, 0)
        result = self.upload("echo REPLACEMENT\n")
        self.assertNotEqual(result.returncode, 0)
        target = next(self.directory.glob("deploy-*.sh"))
        self.assertEqual(target.read_text(), "echo ORIGINAL\n")


class SshOrchestrationTests(unittest.TestCase):
    """Replace only SSH transport; no network or real credentials are used."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="release-runner-")
        self.addCleanup(self.temp.cleanup)
        self.directory = pathlib.Path(self.temp.name)
        bindir = self.directory / "bin"
        bindir.mkdir()
        ssh = bindir / "ssh"
        ssh.write_text('''#!/usr/bin/env bash
set -eu
command=${@: -1}
if [[ "$command" == *'docker login'* ]]; then
  cat > "$TEST_DIR/login.stdin"
  printf '%s' "$command" > "$TEST_DIR/login.command"
  exit "${TEST_LOGIN_STATUS:-0}"
fi
cat > "$TEST_DIR/deploy.stdin"
printf '%s' "$command" > "$TEST_DIR/deploy.command"
exit "${TEST_DEPLOY_STATUS:-0}"
''', encoding="utf-8", newline="\n")
        ssh.chmod(0o700)
        sha = execute(["git", "rev-parse", "HEAD"], cwd=ROOT).stdout.strip()
        self.env = dict(os.environ, HOME=self.directory.as_posix(),
                        SERVER_HOST="example.invalid", SERVER_USER="ubuntu",
                        SERVER_SSH_KEY="test-key", SERVER_KNOWN_HOSTS="example.invalid ssh-ed25519 AAAA",
                        TCR_USERNAME="test-user", TCR_PASSWORD="test-password",
                        RELEASE_VERSION="v0.1.6", RELEASE_COMMIT=sha, RELEASE_RUN="123-1",
                        FRONTEND_REF="frontend@sha256:" + "b" * 64,
                        BACKEND_REF="backend@sha256:" + "c" * 64,
                        PRODUCTION_ORIGIN="https://example.invalid", TEST_DIR=self.directory.as_posix())
        # Let Bash resolve the test executable before the real OpenSSH client.
        shell_bindir = bindir.as_posix()
        if os.name == "nt":
            shell_bindir = execute([BASH, "-c", 'cygpath -u "$1"', "--", shell_bindir]).stdout.strip()
        self.launch = 'export PATH=' + shlex.quote(shell_bindir) + ':"$PATH"\n'
        if os.name == "nt":
            # Git for Windows cannot enforce Linux SSH directory permissions.
            self.launch += 'install() { mkdir -p -- "${@: -1}"; }; export -f install\n'
        self.launch += 'bash "$1"\n'

    def deploy(self):
        return execute([BASH, "-c", self.launch, "--",
                        (ROOT / ".github/scripts/deploy-via-ssh.sh").as_posix()], env=self.env)

    def test_upload_uses_checked_out_script_and_separate_password_stream(self):
        result = self.deploy()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual((self.directory / "deploy.stdin").read_bytes(),
                         (ROOT / "deploy/server/deploy.sh").read_bytes())
        self.assertEqual((self.directory / "login.stdin").read_text(), "test-password")
        for name in ("deploy.command", "login.command"):
            self.assertNotIn("test-password", (self.directory / name).read_text(encoding="utf-8"))

    def test_remote_deploy_failure_reaches_job(self):
        self.env["TEST_DEPLOY_STATUS"] = "9"
        self.assertEqual(self.deploy().returncode, 9)

    def test_failed_registry_login_does_not_upload_script(self):
        self.env["TEST_LOGIN_STATUS"] = "8"
        self.assertEqual(self.deploy().returncode, 8)
        self.assertFalse((self.directory / "deploy.stdin").exists())


if __name__ == "__main__":
    unittest.main()
