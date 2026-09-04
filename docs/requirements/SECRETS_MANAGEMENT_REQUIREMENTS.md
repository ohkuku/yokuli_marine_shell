# Yokuli OS 本地密钥保险库需求 / Local Secrets Vault Requirements

状态：`SUPPORTING_NON_CONSTRUCTION_CONTRACT`。这是冻结的安全基础设施边界，不定义 Launcher Stage，也不授权海事功能施工。中文为主，英文为对照。

## 中文（主文）

### 目标

Yokuli OS 需要一个可以随仓库发布的 Bash 工具，用来维护未来的地图、天气、NMEA 云服务和发布系统凭据。仓库可以包含密文和解密工具，但绝不能包含主口令或明文 secret。

本方案不是自创密码算法。它使用 `age` 的口令加密保护一把随机生成的 identity，再用该 identity 对应的 recipient 加密整个 JSON vault：

- `secrets/identity.age`：由用户的新强口令加密，可提交；
- `secrets/recipient.txt`：公开 recipient，可提交；
- `secrets/vault.json.age`：只包含密文，可提交；
- 主口令：只由用户记忆或保存在仓库之外的密码管理器中，永不写入文件、命令行参数、环境变量或日志；
- 明文 identity 和明文 vault：只存在于权限受限的临时目录中，并在命令退出时尽力清理。

### 强制安全合同

1. 用户在聊天、Issue、日志或其他共享介质中公开过的口令一律视为泄露，不能用于初始化。
2. `init` 必须交互式调用 `age -p`，用户应使用全新的长随机口令；可以直接按 Enter 让 `age` 生成随机词组，并将它存入个人密码管理器。
3. `set NAME` 必须通过隐藏的标准输入读取 value；value 不能成为进程参数。
4. vault key 只允许 POSIX 环境变量名：`[A-Za-z_][A-Za-z0-9_]*`，并拒绝 `PATH`、动态加载器、语言运行时选项等会改变子进程执行方式的控制变量。value 是非空单行字符串。
5. `run -- command ...` 只向受信任的子进程注入变量，不生成 `.env`，不使用 `source` 或 `eval`。
6. `get` 是唯一会把一个 secret 写到标准输出的读取命令；调用者必须承担终端回滚、重定向和 shell 历史风险。
7. `copy` 只写入本地剪贴板，不回显；用户必须理解云同步剪贴板的风险。
8. 所有改动先写新密文，再在同一目录替换正式 vault；并发写由锁目录拒绝。
9. 工具必须拒绝 identity／recipient／vault 三件套不完整的状态、非 armored age envelope、格式错误的 recipient，并检查 Git 中是否跟踪了常见明文 secret 文件。
10. 日志、错误信息和 CI 测试不得包含真实 secret；自动测试只使用临时目录和假加密器，验证工作流而不冒充密码学测试。
11. 主口令丢失且没有仓库外备份时，vault 不可恢复。主口令泄露时，仅更换主口令不能撤销旧 Git 历史中的密文；必须同时轮换所有上游 API key。
12. 本地 vault 不替代 GitHub Actions Secrets、environment protection 或未来可用的 OIDC。CI 需要 secret 时应由 GitHub 注入，不能自动解锁仓库内 vault。

### 命令合同

```text
yokuli-secrets.sh doctor
yokuli-secrets.sh init
yokuli-secrets.sh set NAME
yokuli-secrets.sh remove NAME
yokuli-secrets.sh list
yokuli-secrets.sh get NAME
yokuli-secrets.sh copy NAME
yokuli-secrets.sh run -- command [args...]
yokuli-secrets.sh rotate
```

`doctor` 在尚未初始化时可以成功，但必须明确报告 `UNINITIALIZED`。除 `doctor`、`init` 和帮助外，其余命令都要求完整 vault。

### 非目标

- 不把 secret 打进 APK；移动端运行时 secret 不能靠客户端加密获得真正保密性。
- 不支持共享主口令；团队协作应迁移到专用 secrets manager 或每人独立 recipient。
- 不宣称在 SSD、快照或日志型文件系统上可以物理安全擦除临时数据。
- 不替代 API 提供方的撤销、权限最小化、过期和轮换机制。

## English translation

Yokuli OS ships a Bash interface and encrypted artifacts, never a master passphrase or plaintext secret. A newly generated age identity is passphrase-encrypted in `identity.age`; its public recipient encrypts the JSON vault. Values enter through hidden stdin, never command arguments. `run` exports them only to a trusted child process without creating or sourcing a dotenv file. Writes are locked and replace ciphertext only after successful encryption; partial vaults and tracked plaintext secret files are rejected.

The master passphrase must be new, strong, and kept outside the repository. Losing it without an external backup makes the vault unrecoverable. If it or an API key is exposed, rotate the provider credentials: rewrapping the identity cannot revoke decryptable ciphertext already present in Git history. This local vault does not replace GitHub Actions Secrets, protected environments, OIDC, or server-side secret storage.
