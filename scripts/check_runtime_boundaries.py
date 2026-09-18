#!/usr/bin/env python3
"""检查活动 Shell 与进程内运行时的源码边界；不替代 Kotlin 编译或行为验证。"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path
import re
import sys


@dataclass(frozen=True)
class Finding:
    rule: str
    path: str
    line: int
    token: str
    reason: str


@dataclass(frozen=True)
class ExceptionEntry:
    rule: str
    path: str
    line: int
    token: str
    reason: str


# 必须精确到规则、仓库相对路径、行号和命中内容，并解释保留原因。
# 不允许目录通配/全文件豁免；失效条目会失败。当前没有源码边界例外。
EXCEPTIONS: tuple[ExceptionEntry, ...] = ()

SOURCE_SUFFIXES = {".kt", ".java"}
REQUIRED_ROOTS = (
    "app-shell/src/rebuild",
    "core/runtime-contract/src",
    "runtime/marine-local/src",
)
TOKEN = re.compile(r"`[^`\n]+`|[A-Za-z_]\w*|[^\s]", re.UNICODE)
SHELL_IMPLEMENTATIONS = {
    "MainViewModel", "LegacyMarineController", "LocalMarineServices", "LocalMarineContentService",
    "InProcessMarineSystem", "VoyageSessionCoordinator", "MarineSystemBindings", "LegacyMarineRuntime",
}
DATABASE_IMPLEMENTATIONS = {
    "AppDatabase", "AnchorDatabase", "DatabaseProvider", "RoomDatabase", "Room", "Dao",
}
SHELL_PACKAGE = re.compile(r"\bcom\s*\.\s*yokuli\s*\.\s*marine\s*\.\s*shell\b")
NON_PORTABLE_PACKAGE = re.compile(
    r"\b(?:androidx?|com\s*\.\s*google\s*\.\s*android)\s*\."
    r"|\bcom\s*\.\s*yokuli\s*\.\s*anchorwatch\b"
)


def code_only(source: str, kotlin: bool = True) -> str:
    """遮罩注释和字符串内容，保留位置及字符串插值里的代码，供词法规则使用。"""
    chars = list(source)
    length = len(source)

    def mask(start: int, end: int) -> None:
        for index in range(start, end):
            if chars[index] not in "\r\n":
                chars[index] = " "

    def quoted(start: int, delimiter: str) -> int:
        raw = len(delimiter) == 3
        index = start + len(delimiter)
        mask(start, index)
        while index < length:
            if source.startswith(delimiter, index):
                mask(index, index + len(delimiter))
                return index + len(delimiter)
            if not raw and source[index] == "\\":
                end = min(index + 2, length)
                mask(index, end)
                index = end
            elif kotlin and delimiter != "'" and source.startswith("${", index):
                mask(index, index + 1)
                index = code(index + 2, stop_at_brace=True)
            elif kotlin and delimiter != "'" and source[index] == "$":
                match = re.match(r"[A-Za-z_]\w*", source[index + 1:])
                mask(index, index + 1)
                index += 1 + (len(match.group()) if match else 0)
            else:
                mask(index, index + 1)
                index += 1
        return index

    def code(start: int, stop_at_brace: bool = False) -> int:
        index = start
        while index < length:
            if source.startswith("//", index):
                end = source.find("\n", index)
                end = length if end == -1 else end
                mask(index, end)
                index = end
            elif source.startswith("/*", index):
                end, depth = index + 2, 1
                while end < length and depth:
                    if kotlin and source.startswith("/*", end):
                        depth += 1
                        end += 2
                    elif source.startswith("*/", end):
                        depth -= 1
                        end += 2
                    else:
                        end += 1
                mask(index, end)
                index = end
            elif source.startswith('"""', index):
                index = quoted(index, '"""')
            elif source[index] in "\"'":
                index = quoted(index, source[index])
            elif stop_at_brace and source[index] == "{":
                index = code(index + 1, stop_at_brace=True)
            elif stop_at_brace and source[index] == "}":
                return index + 1
            else:
                index += 1
        return index

    code(0)
    return "".join(chars)


def production_sources(base: Path) -> list[Path]:
    """扫描全部源文件，不挑选已迁移文件；排除构建产物和测试 source set。"""
    files = []
    if not base.exists():
        return files
    for path in base.rglob("*"):
        if not path.is_file() or path.suffix not in SOURCE_SUFFIXES:
            continue
        parts = path.relative_to(base).parts
        if any(part in {"build", ".gradle", ".git"} for part in parts):
            continue
        # app-shell/src/rebuild 从根扫描；其他模块只扫描 src/<source-set>。
        def is_test_source_set(name: str) -> bool:
            return name.startswith(("test", "androidTest")) or name.endswith("Test")

        if "src" in parts:
            source_set = parts[parts.index("src") + 1]
            if is_test_source_set(source_set):
                continue
        elif base.name != "rebuild" and base.name != "src":
            continue
        elif base.name == "src" and parts and is_test_source_set(parts[0]):
            continue
        files.append(path)
    return sorted(files)


def inspect_source(path: Path, root: Path) -> list[Finding]:
    relative = path.relative_to(root).as_posix()
    source = path.read_text(encoding="utf-8")
    code = code_only(source, kotlin=path.suffix == ".kt")
    findings: list[Finding] = []

    def add(rule: str, offset: int, token: str, reason: str) -> None:
        findings.append(Finding(rule, relative, source.count("\n", 0, offset) + 1, token, reason))

    if relative.startswith("app-shell/src/rebuild/"):
        tokens = list(TOKEN.finditer(code))
        for index, match in enumerate(tokens):
            name = match.group().strip("`")
            previous = tokens[index - 1].group() if index else ""
            following = tokens[index + 1].group() if index + 1 < len(tokens) else ""
            if name in SHELL_IMPLEMENTATIONS:
                add("SHELL_IMPLEMENTATION", match.start(), name,
                    "Shell 只能使用 MarineServices，不能引用 VM、控制器或本地实现。")
            if name == "vm" and (previous == "." or (
                index >= 2 and previous == ":" and tokens[index - 2].group() == ":"
            )):
                add("SHELL_VM_ACCESS", match.start(), name,
                    "禁止 .vm / ?.vm / ::vm；读投影或命令应经过 MarineServices。")
            if name in {"updateSettings", "updateVesselDataSettings"} and previous in {".", ":"}:
                add("SHELL_BROAD_PREFERENCES", match.start(), name,
                    "禁止整份旧设置回写；使用语言、船舶资料、声音或仪表布局的窄命令。")
            if name in {"startTrip", "pauseTrip", "resumeTrip", "endTrip"} and (
                (following == "(" and previous != "fun") or (
                    index >= 2 and previous == ":" and tokens[index - 2].group() == ":"
                )
            ):
                add("SHELL_RAW_VOYAGE_COMMAND", match.start(), name,
                    "航行启停必须经过共享 VoyageSessionService；不能绕过全局命令等待与确认。")
            if name in DATABASE_IMPLEMENTATIONS or re.fullmatch(r"[A-Za-z_]\w*Dao", name):
                add("SHELL_DATABASE", match.start(), name,
                    "数据库/DAO 类型、字段和工厂方法必须留在运行时实现层；实体 DTO 不受此规则禁止。")
            elif name in {"database", "getDatabase"} and previous == "." and following == "(":
                add("SHELL_DATABASE", match.start(), name,
                    "Shell 不得通过工厂或 EntryPoint 取得数据库；使用内容服务端口。")

    if relative.startswith(("core/", "runtime/")):
        for match in SHELL_PACKAGE.finditer(code):
            add("REVERSE_SHELL_REFERENCE", match.start(), re.sub(r"\s+", "", match.group()),
                "core/runtime 不能反向引用 app-shell 包。")

    if relative.startswith("core/runtime-contract/"):
        for match in NON_PORTABLE_PACKAGE.finditer(code):
            add("CONTRACT_PLATFORM_REFERENCE", match.start(), re.sub(r"\s+", "", match.group()),
                "runtime-contract 必须是纯 Kotlin；不能引用 Android、AndroidX 或 legacy 类型。")
    return findings


def check(root: Path) -> int:
    missing = [base for base in REQUIRED_ROOTS if not production_sources(root / base)]
    for base in missing:
        print(f"ERROR REQUIRED_SOURCE_ROOT {base}: 缺少生产源，不能以空目录宣称分层通过。")

    sources = sorted(set(
        production_sources(root / "app-shell/src/rebuild")
        + production_sources(root / "core")
        + production_sources(root / "runtime")
    ))
    findings = [finding for path in sources for finding in inspect_source(path, root)]
    used: set[ExceptionEntry] = set()
    failures = []
    for finding in findings:
        exception = next((entry for entry in EXCEPTIONS if (
            entry.rule, entry.path, entry.line, entry.token
        ) == (finding.rule, finding.path, finding.line, finding.token) and entry.reason.strip()), None)
        if exception is not None:
            used.add(exception)
            print(f"EXCEPTION {finding.path}:{finding.line} [{finding.rule}] {exception.reason}")
        else:
            failures.append(finding)
            print(f"ERROR {finding.path}:{finding.line} [{finding.rule}] {finding.token}: {finding.reason}")
    stale = set(EXCEPTIONS) - used
    for entry in sorted(stale, key=lambda value: (value.path, value.line, value.rule)):
        print(f"ERROR STALE_EXCEPTION {entry.path}:{entry.line} [{entry.rule}]: 删除失效或无原因的例外。")

    counts = {prefix: sum(path.relative_to(root).as_posix().startswith(prefix + "/") for path in sources)
              for prefix in ("app-shell/src/rebuild", "core", "runtime")}
    print("扫描生产源：" + "，".join(f"{prefix}={count}" for prefix, count in counts.items()))
    if failures or missing or stale:
        print(f"FAIL: {len(failures)} 处源码越界，{len(missing)} 个缺失源目录，{len(stale)} 个失效例外。")
        return 1
    print(f"PASS: {len(sources)} 个生产文件，{len(used)} 个显式例外。仅证明上述静态边界。")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1],
                        help="仓库根目录；默认使用脚本所在仓库。")
    args = parser.parse_args()
    return check(args.root.resolve())


if __name__ == "__main__":
    sys.exit(main())
