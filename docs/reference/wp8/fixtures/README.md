# WP8 Reference Schema Fixtures

状态：`SCHEMA_TEST_ONLY`。本目录的合成 capture metadata、geometry 和 motion 数值只用于证明 Draft 2020-12 schema 的状态条件；它们不是 WP8 Reference、Golden、设备采集或产品测量，不能复制到 `WP8_REFERENCE_MEASUREMENTS.json`。

CI 必须接受三份 `valid_*.json`，拒绝四份 `invalid_*.json`。`valid_human_reviewed.json` 的 review 只批准测试 fixture 本身，并对 canonical `measurementSets` 计算真实 SHA-256；它不把合成数值升级为 WP8 fidelity 证据。

本地验证前安装固定依赖，然后运行命名 Gate：

```text
python3 -m pip install --requirement .github/requirements/stage0-schema.txt
python3 .github/scripts/test_launcher_stage0_contract.py
```

## English translation

Status is `SCHEMA_TEST_ONLY`. Synthetic capture metadata, geometry, and motion values exercise the Draft 2020-12 state conditions only. They are not WP8 reference evidence, goldens, device captures, or product measurements and must never be copied into the real measurement document. CI accepts three valid fixtures and rejects four invalid fixtures. The human-reviewed fixture signs its canonical test measurement set but does not approve WP8 fidelity.
