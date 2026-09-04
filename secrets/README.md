# Yokuli OS encrypted secrets

中文：此目录只允许提交下列三个由工具生成的文件，以及本说明：

- `identity.age` — 由个人主口令加密的随机 age identity；
- `recipient.txt` — 公共 recipient；
- `vault.json.age` — 使用 recipient 加密的 JSON vault。

不要在此目录手工创建 `.env`、`vault.json`、`identity.txt`、API key 或主口令。完整操作见 [`docs/SECRETS_MANAGEMENT.md`](../docs/SECRETS_MANAGEMENT.md)。

## English translation

Only the three generated encrypted/public artifacts above and this README may be committed. Never place a dotenv file, plaintext vault, plaintext identity, API key, or master passphrase here. See the bilingual runbook linked above.
