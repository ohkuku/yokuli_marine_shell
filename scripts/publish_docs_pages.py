#!/usr/bin/env python3
"""Stage the two public sites on gh-pages; only --push publishes.

The current working tree supplies generated docs, including uncommitted changes.
Existing unrelated gh-pages content is preserved. No CI, Pages setting, Git
configuration, credentials or large marine payload is created or changed.
"""
from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OWNED = ("index.html", ".nojekyll", "charts", "developers")
ALLOWED = {".html", ".css", ".js", ".json", ".svg", ".md", ".zip"}


def git(directory: Path, *arguments: str, allowed_codes: tuple[int, ...] = (0,)) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(["git", "-C", str(directory), *arguments], text=True, capture_output=True)
    if result.returncode not in allowed_codes:
        # Keep the exact Git diagnostic, but never print the command's remote URL.
        raise RuntimeError(f"git {arguments[0]} failed ({result.returncode}): {result.stderr.strip()}")
    return result


def source_files() -> list[tuple[Path, Path]]:
    source = json.loads((ROOT / "chart-library/catalogue.json").read_text(encoding="utf-8"))
    for generated in (ROOT / "docs/charts/catalogue.json", ROOT / "runtime/marine-local/src/main/assets/chart-store/catalogue.json"):
        if not generated.is_file() or json.loads(generated.read_text(encoding="utf-8")) != source:
            raise ValueError("Generate current assets first: python3 scripts/publish_chart_catalogue.py")
    files: list[tuple[Path, Path]] = []
    total = 0
    for name in OWNED:
        top = ROOT / "docs" / name
        if not top.exists() or top.is_symlink():
            raise ValueError(f"Missing or linked site source: docs/{name}")
        for path in ([top] if top.is_file() else sorted(top.rglob("*"))):
            relative = path.relative_to(ROOT / "docs")
            if path.is_symlink() or any(part.startswith(".") for part in relative.parts if part != ".nojekyll"):
                raise ValueError(f"Hidden or linked site input is not allowed: {relative}")
            if path.is_dir():
                continue
            if path.name != ".nojekyll" and path.suffix not in ALLOWED:
                raise ValueError(f"Not a public site asset: {relative}")
            if path.suffix == ".zip" and relative != Path("developers/yokuli-sdk-2.zip"):
                raise ValueError(f"Only the generated SDK archive may be published: {relative}")
            size = path.stat().st_size
            total += size
            if size > 16 * 1024 * 1024 or total > 64 * 1024 * 1024:
                raise ValueError("Site size limit exceeded; chart packages belong in Git LFS, not Pages")
            files.append((path, relative))
    return files


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--push", action="store_true", help="commit changed site assets and push gh-pages; default only stages")
    parser.add_argument("--remote", default="origin", help="existing source repository remote name (default: origin)")
    parser.add_argument("--message", default="docs: publish chart library and developer site", help="deployment commit message")
    args = parser.parse_args()
    if args.remote.startswith("-"):
        parser.error("remote must be an existing Git remote name")
    files = source_files()
    remote = git(ROOT, "remote", "get-url", args.remote).stdout.strip()
    push_remote = git(ROOT, "remote", "get-url", "--push", args.remote).stdout.strip()
    stage = Path(tempfile.mkdtemp(prefix="yokuli-pages-"))
    print(f"Site checkout: {stage}", flush=True)
    git(stage, "init", "--initial-branch=gh-pages")
    git(stage, "remote", "add", "origin", remote)
    git(stage, "remote", "set-url", "--push", "origin", push_remote)
    existing = git(stage, "ls-remote", "--exit-code", "--heads", "origin", "refs/heads/gh-pages", allowed_codes=(0, 2))
    if existing.returncode == 0:
        git(stage, "fetch", "--depth=1", "origin", "refs/heads/gh-pages")
        git(stage, "checkout", "-B", "gh-pages", "FETCH_HEAD")
    # Otherwise the freshly initialized branch is orphaned. No source checkout
    # files, history, .git directory, payloads or credentials are copied.
    for name in OWNED:
        target = stage / name
        if target.is_symlink() or target.is_file():
            target.unlink()
        elif target.is_dir():
            shutil.rmtree(target)
    for source, relative in files:
        target = stage / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
    git(stage, "add", "--", *OWNED)
    changes = git(stage, "diff", "--cached", "--quiet", allowed_codes=(0, 1)).returncode == 1
    if not changes:
        print("Site already matches gh-pages; no commit or push needed.")
    elif not args.push:
        print("Prepared and staged only. Review this checkout; rerun with --push to publish current docs.")
    else:
        git(stage, "commit", "-m", args.message)
        # No force: a concurrent deployment must be reconciled from its new HEAD.
        git(stage, "push", "origin", "HEAD:refs/heads/gh-pages")
        print(f"Published gh-pages commit {git(stage, 'rev-parse', '--short', 'HEAD').stdout.strip()}")
    print("GitHub Pages source: gh-pages / (root). This script does not change repository settings.")


if __name__ == "__main__":
    main()
