#!/usr/bin/env python3
"""生成活动应用和共享业务边界的声明索引；不把文档当作另一个接口实现。"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
roots = [
    'app-shell/src/rebuild/java', 'core/shell-contract/src/main', 'core/shell-engine/src/main',
    'ui/shell-compose/src/main', 'adapter/shell-storage/src/main',
    'legacy-marine/src/main/java/com/yokuli/anchorwatch/domain',
    'legacy-marine/src/main/java/com/yokuli/anchorwatch/data',
    'legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime',
    'legacy-marine/src/main/java/com/yokuli/anchorwatch/location',
    'legacy-marine/src/main/java/com/yokuli/anchorwatch/service',
]
files = sorted({p for base in roots for p in (root / base).rglob('*.kt')} |
               {root / 'legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt'})
pattern = re.compile(r'^\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:(?:public|internal|suspend|override|inline|open|abstract|data|sealed|enum|value)\s+)*(class|interface|object|fun)\s+([^({=]+)')
lines = ['# 生产接口与结构声明索引', '',
         '由 `python3 scripts/export_api_index.py` 从当前源码生成。包含活动重制应用、Shell 合同和所复用的业务领域/存储/运行时。'
         '遗留类中的保留 API 不代表其 UI 或功能仍启用；例如声纳历史类型仅为读取已有数据库而保留。', '',
         '本索引是源码定位工具，包含类型与方法声明，并非所有声明都是跨应用公共 API。'
         '完整参数、中文业务语义、公开边界和生命周期见 [总拓扑](OS_INTERFACE_TOPOLOGY.md) 及链接源码。'
         '显式 private 声明、旧 UI 和 Gradle 依赖 API 不在本索引范围。', '']
count = 0
for p in files:
    found = []
    for i, line in enumerate(p.read_text().splitlines(), 1):
        if re.search(r'\bprivate\b', line):
            continue
        match = pattern.match(line)
        if match:
            name = match.group(2).strip().replace('|', '\\|')
            rel = p.relative_to(root).as_posix()
            found.append(f'| `{match.group(1)} {name}` | [{p.name}:{i}](../../{rel}#L{i}) |')
    if found:
        lines += [f'## {p.relative_to(root)}', '', '| 声明 | 实现位置 |', '| --- | --- |', *found, '']
        count += len(found)
lines[2:2] = [f'共 {count} 项类型与方法声明；按源文件排序。', '']
(root / 'docs/product/API_INDEX.md').write_text('\n'.join(lines).rstrip() + '\n')
print(f'Indexed {count} declarations')
