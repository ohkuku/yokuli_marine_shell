# Yokuli OS 本地密钥保险库 / Local Secrets Vault

## 先说结论

“用一个自己掌握的主口令解开所有 API key”这个方向合理，但前提是不用自制加密、不把主口令写进仓库，并且主口令足够随机。任何已经发进聊天、Issue 或日志的口令都应永久弃用；即使随后删除消息，也不能再假定它只有本人知道。

本仓库采用 [`age`](https://github.com/FiloSottile/age) 和 `jq`。`age` 的口令模式会通过终端交互读取口令，并支持口令加密的 identity 文件；因此 Git 只保存加密 identity、公钥 recipient 和加密 vault。GitHub 也明确建议避免硬编码 secret，并在泄露后优先撤销或轮换真实凭据，而不是只清理历史：

- [age 官方 README](https://github.com/FiloSottile/age/blob/main/README.md)
- [GitHub secret scanning](https://docs.github.com/en/code-security/concepts/secret-security/secret-scanning)
- [GitHub 移除敏感数据指南](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)
- [GitHub push protection](https://docs.github.com/en/code-security/how-tos/secure-your-secrets/prevent-future-leaks/enable-push-protection)

## 安装与初始化（macOS）

```bash
brew install age jq
./scripts/secrets/yokuli-secrets.sh doctor
./scripts/secrets/yokuli-secrets.sh init
```

`init` 会先生成随机 identity，然后由 `age -p` 直接提示设置一个全新的主口令。推荐在提示处直接按 Enter，让 `age` 生成随机词组，再把词组保存在个人密码管理器中。不要把主口令放进 shell 命令、环境变量、脚本、笔记、截图或 GitHub。

初始化后检查并提交的只能是：

```bash
./scripts/secrets/yokuli-secrets.sh doctor
git add secrets/README.md secrets/identity.age secrets/recipient.txt secrets/vault.json.age
git diff --cached
```

主口令不在 Git 中；没有它就不能解开 `identity.age`。反过来，只有主口令但缺少这三个文件，也不能恢复 vault。

## 日常操作

当前 Phase 1 产品运行期凭据精确为一个 `GOOGLE_MAPS_ANDROID_API_KEY`。不按 dev/prod 拆分；OpenSeaMap 与本地海图导入不需要供应商 key，LINZ 不在本版本范围。Android 发布签名材料仍是独立的发布凭据。完整边界见 [`requirements/CHART_SOURCE_IMPORT_REQUIREMENTS.md`](requirements/CHART_SOURCE_IMPORT_REQUIREMENTS.md)。

新增或替换一个 key。value 在终端中隐藏，并通过标准输入进入工具：

```bash
./scripts/secrets/yokuli-secrets.sh set GOOGLE_MAPS_ANDROID_API_KEY
```

只列出名称，不显示 value：

```bash
./scripts/secrets/yokuli-secrets.sh list
```

把一个 value 复制到剪贴板，不回显：

```bash
./scripts/secrets/yokuli-secrets.sh copy GOOGLE_MAPS_ANDROID_API_KEY
```

明确输出一个 value。它会进入终端滚动区，谨慎使用：

```bash
./scripts/secrets/yokuli-secrets.sh get GOOGLE_MAPS_ANDROID_API_KEY
```

删除一个 key：

```bash
./scripts/secrets/yokuli-secrets.sh remove GOOGLE_MAPS_ANDROID_API_KEY
```

只给一个受信任的子进程注入 vault 中的全部变量，不落地 `.env`：

```bash
./scripts/secrets/yokuli-secrets.sh run -- ./gradlew assembleStandaloneDebug
```

环境变量可能被子进程、调试器、崩溃报告或同用户权限的进程读取，所以不要用 `run` 启动不受信任的程序。客户端 APK 内的长期 API key 仍可被提取；能放服务端的 secret 应放服务端，并限制权限、来源、额度和有效期。

工具拒绝 `PATH`、动态加载器变量、语言运行时启动选项等会改变子进程执行方式的名称。这不是完整的沙箱：Git 提交和脚本改动仍需审查，密文保密也不能替代仓库写权限、分支保护或提交来源控制。

修改 vault 后提交新密文：

```bash
git add secrets/vault.json.age
git diff --cached --stat
git commit
```

## 换主口令与事故处理

仅更换用于包装 identity 的主口令：

```bash
./scripts/secrets/yokuli-secrets.sh rotate
git add secrets/identity.age
git commit
```

`rotate` 保持 recipient 不变，所以不需要重写 vault。但是 Git 历史中的旧 `identity.age` 仍可能被旧口令解开。若主口令或任一密文可能泄露，正确顺序是：

1. 立即去 API 提供方撤销／轮换所有真实 key；
2. 用新强口令运行 `rotate`；
3. 审查 Git、Actions、终端日志、Issue、聊天和构建制品的暴露范围；
4. 按 GitHub 指南决定是否清理历史，并启用 secret scanning／push protection；
5. 提交新密文并验证旧 key 已失效。

如果主口令遗失且没有仓库外备份，设计上无法恢复。不要让脚本提供后门或“找回密码”。

工具会尽力删除临时明文，但 SSD、APFS 快照和日志型文件系统不能保证物理擦除。高风险环境应在加密磁盘、受控用户会话和最小权限主机上操作。

## GitHub Actions 边界

仓库 CI 只运行假加密器合同测试，不包含也不解锁个人 vault。未来 Actions 需要部署或签名凭据时，应使用 GitHub Actions Secrets、protected environments，或支持时使用 OIDC 短期凭据。不要把个人主口令新增为普通仓库文件，也不要为了 CI 把它硬编码到 workflow。

## English translation — quick guide

Install `age` and `jq`, run `doctor`, then `init`, and choose a brand-new strong passphrase only at age's interactive prompt. Commit only `identity.age`, `recipient.txt`, and `vault.json.age`. Phase 1 has one runtime credential, `GOOGLE_MAPS_ANDROID_API_KEY`; OpenSeaMap and local chart imports are keyless, while release signing remains separate. Use `set NAME`, `list`, `copy NAME`, `get NAME`, `remove NAME`, and `run -- command`; `get` prints a value and `run` exposes all values to a trusted child environment. `rotate` rewraps the identity but cannot invalidate ciphertext already in Git history. After exposure, revoke and rotate provider credentials first. CI uses a fake-crypto contract test and must use GitHub-managed secrets or OIDC for real automation credentials.
