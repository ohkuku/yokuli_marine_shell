#!/usr/bin/env bash
set -euo pipefail

umask 077

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
default_repo_root="$(cd "$script_dir/../.." && pwd)"
repo_root="${YOKULI_REPO_ROOT:-$default_repo_root}"
secrets_dir="${YOKULI_SECRETS_DIR:-$repo_root/secrets}"
age_bin="${YOKULI_AGE_BIN:-age}"
age_keygen_bin="${YOKULI_AGE_KEYGEN_BIN:-age-keygen}"
jq_bin="${YOKULI_JQ_BIN:-jq}"

identity_encrypted="$secrets_dir/identity.age"
recipient_file="$secrets_dir/recipient.txt"
vault_encrypted="$secrets_dir/vault.json.age"
lock_dir="$secrets_dir/.yokuli-secrets.lock"

plain_files=()
staged_files=()
work_dir=''
stage_dir=''
lock_held=0
active_identity=''
active_vault=''

die() {
  printf '错误 / Error: %s\n' "$*" >&2
  exit 1
}

info() {
  printf '%s\n' "$*"
}

secure_remove() {
  local target="$1"
  [[ -f "$target" ]] || return 0
  if command -v shred >/dev/null 2>&1; then
    shred -u -- "$target" 2>/dev/null || rm -f -- "$target"
  elif rm -P "$target" >/dev/null 2>&1; then
    :
  else
    rm -f -- "$target"
  fi
}

cleanup_plaintext() {
  local target
  if ((${#plain_files[@]})); then
    for target in "${plain_files[@]}"; do
      secure_remove "$target"
    done
  fi
  plain_files=()
  if [[ -n "$work_dir" && -d "$work_dir" ]]; then
    rmdir "$work_dir" 2>/dev/null || true
  fi
  work_dir=''
}

cleanup() {
  local target
  cleanup_plaintext
  if ((${#staged_files[@]})); then
    for target in "${staged_files[@]}"; do
      [[ -f "$target" ]] && rm -f -- "$target"
    done
  fi
  staged_files=()
  if [[ -n "$stage_dir" && -d "$stage_dir" ]]; then
    rmdir "$stage_dir" 2>/dev/null || true
  fi
  stage_dir=''
  if [[ "$lock_held" -eq 1 && -d "$lock_dir" ]]; then
    rmdir "$lock_dir" 2>/dev/null || true
  fi
  lock_held=0
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

usage() {
  cat <<'USAGE'
Yokuli OS 本地密钥保险库 / Local secrets vault

用法 / Usage:
  yokuli-secrets.sh doctor
  yokuli-secrets.sh init
  yokuli-secrets.sh set NAME
  yokuli-secrets.sh remove NAME
  yokuli-secrets.sh list
  yokuli-secrets.sh get NAME
  yokuli-secrets.sh copy NAME
  yokuli-secrets.sh run -- command [args...]
  yokuli-secrets.sh rotate

安全规则 / Safety:
  - 主口令只允许在 age 的交互提示中输入，不能放进参数或环境变量。
  - set 从隐藏输入读取值；get 会明确输出 secret；run 只应用于受信任的命令。
  - Master passphrases are accepted only by age's interactive prompt.
  - set reads hidden input; get intentionally prints a secret; run trusted commands only.
USAGE
}

command_exists() {
  if [[ "$1" == */* ]]; then
    [[ -x "$1" ]]
  else
    command -v "$1" >/dev/null 2>&1
  fi
}

require_dependencies() {
  command_exists "$age_bin" || die "缺少 age；macOS 可运行 brew install age / age is required"
  command_exists "$age_keygen_bin" || die "缺少 age-keygen / age-keygen is required"
  command_exists "$jq_bin" || die "缺少 jq；macOS 自带或可运行 brew install jq / jq is required"
  command_exists base64 || die "缺少 base64 / base64 is required"
}

ensure_secrets_dir() {
  mkdir -p "$secrets_dir"
  chmod 700 "$secrets_dir"
}

vault_file_count() {
  local count=0
  [[ -f "$identity_encrypted" ]] && count=$((count + 1))
  [[ -f "$recipient_file" ]] && count=$((count + 1))
  [[ -f "$vault_encrypted" ]] && count=$((count + 1))
  printf '%s\n' "$count"
}

require_consistent_state() {
  local count
  count="$(vault_file_count)"
  [[ "$count" -eq 0 || "$count" -eq 3 ]] || die "保险库三件套不完整；不要猜测恢复，请从可信提交或备份恢复 / partial vault"
}

require_initialized() {
  require_consistent_state
  [[ "$(vault_file_count)" -eq 3 ]] || die "尚未初始化；先运行 init / vault is not initialized"
  validate_encrypted_artifact "$identity_encrypted"
  validate_encrypted_artifact "$vault_encrypted"
  read_recipient >/dev/null
}

validate_name() {
  local name="${1:-}"
  [[ "$name" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || die "NAME 必须是合法环境变量名 / invalid variable name"
}

validate_injection_name() {
  local name="${1:-}"
  validate_name "$name"
  case "$name" in
    PATH|IFS|CDPATH|ENV|BASH_ENV|BASHOPTS|SHELLOPTS|LD_PRELOAD|LD_LIBRARY_PATH|DYLD_*|PYTHONPATH|PYTHONHOME|NODE_OPTIONS|RUBYOPT|PERL5OPT|JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|GRADLE_OPTS)
      die "拒绝会改变进程执行方式的变量名 / process-control variable is forbidden: $name"
      ;;
  esac
}

acquire_lock() {
  ensure_secrets_dir
  if ! mkdir "$lock_dir" 2>/dev/null; then
    die "已有写操作或遗留锁：${lock_dir}；确认没有进程运行后再人工删除 / vault is locked"
  fi
  lock_held=1
}

new_plain_work_dir() {
  [[ -z "$work_dir" ]] || die "内部错误：临时目录已存在 / internal temporary directory collision"
  work_dir="$(mktemp -d "${TMPDIR:-/tmp}/yokuli-secrets.XXXXXX")"
  chmod 700 "$work_dir"
}

new_stage_dir() {
  [[ -z "$stage_dir" ]] || die "内部错误：写入目录已存在 / internal staging directory collision"
  stage_dir="$(mktemp -d "$secrets_dir/.write.XXXXXX")"
  chmod 700 "$stage_dir"
}

register_plain_file() {
  plain_files+=("$1")
}

register_staged_file() {
  staged_files+=("$1")
}

validate_vault_json() {
  "$jq_bin" -e '
    type == "object" and
    all(to_entries[];
      (.key | test("^[A-Za-z_][A-Za-z0-9_]*$")) and
      (.value | type == "string") and
      (.value | length > 0) and
      (.value | test("[\\r\\n]") | not)
    )
  ' "$1" >/dev/null || die "vault 格式无效，只允许非空单行字符串 / invalid vault schema"
}

read_recipient() {
  local recipient
  recipient="$(tr -d '\r\n' < "$recipient_file")"
  [[ "$recipient" =~ ^age1[0-9a-z]+$ ]] || die "recipient.txt 无效 / invalid age recipient"
  printf '%s\n' "$recipient"
}

validate_encrypted_artifact() {
  local target="$1" first_line=''
  IFS= read -r first_line < "$target" || true
  [[ "$first_line" == '-----BEGIN AGE ENCRYPTED FILE-----' ]] \
    || die "不是 armored age 密文：${target#"$repo_root/"} / invalid encrypted artifact"
}

decrypt_vault() {
  require_dependencies
  require_initialized
  new_plain_work_dir
  active_identity="$work_dir/identity.txt"
  active_vault="$work_dir/vault.json"
  register_plain_file "$active_identity"
  register_plain_file "$active_vault"
  "$age_bin" -d -o "$active_identity" "$identity_encrypted"
  chmod 600 "$active_identity"
  "$age_bin" -d -i "$active_identity" -o "$active_vault" "$vault_encrypted"
  chmod 600 "$active_vault"
  validate_vault_json "$active_vault"
}

encrypt_vault_replacement() {
  local plaintext="$1"
  local recipient staged_vault
  validate_vault_json "$plaintext"
  recipient="$(read_recipient)"
  new_stage_dir
  staged_vault="$stage_dir/vault.json.age"
  register_staged_file "$staged_vault"
  "$age_bin" -a -r "$recipient" -o "$staged_vault" "$plaintext"
  chmod 600 "$staged_vault"
  mv -f "$staged_vault" "$vault_encrypted"
  staged_files=()
  rmdir "$stage_dir"
  stage_dir=''
}

mode_of() {
  if stat -f '%Lp' "$1" >/dev/null 2>&1; then
    stat -f '%Lp' "$1"
  else
    stat -c '%a' "$1"
  fi
}

check_permissions() {
  local target mode
  for target in "$identity_encrypted" "$recipient_file" "$vault_encrypted"; do
    mode="$(mode_of "$target")"
    [[ $((10#$mode % 100)) -eq 0 ]] || die "权限过宽：${target#"$repo_root/"} 是 ${mode}，需要仅 owner 可读写 / unsafe permissions"
  done
}

check_tracked_plaintext() {
  local tracked
  if ! command_exists git || ! git -C "$repo_root" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    return 0
  fi
  tracked="$(git -C "$repo_root" ls-files -- \
    '.env' '.env.*' ':(exclude).env.example' 'local.properties' 'keystore.properties' \
    '*.jks' '*.keystore' '*.p12' '*.pfx' 'google-services.json' \
    'secrets/*.json' 'secrets/*.env' 'secrets/identity.txt' 'secrets/*.plain*' \
    2>/dev/null || true)"
  [[ -z "$tracked" ]] || die "Git 正在跟踪疑似明文 secret 文件：$tracked / tracked plaintext secret candidate"
}

command_doctor() {
  local count candidate
  require_dependencies
  ensure_secrets_dir
  require_consistent_state
  check_tracked_plaintext
  if [[ -d "$lock_dir" ]]; then
    die "发现写入锁：$lock_dir / active or stale write lock"
  fi
  for candidate in "$secrets_dir"/.write.*; do
    [[ ! -e "$candidate" ]] || die "发现未完成的密文写入：$candidate / abandoned staged write"
  done
  count="$(vault_file_count)"
  if [[ "$count" -eq 0 ]]; then
    info 'UNINITIALIZED — 工具可用，尚未创建个人 vault / tool ready; personal vault not created'
  else
    validate_encrypted_artifact "$identity_encrypted"
    validate_encrypted_artifact "$vault_encrypted"
    read_recipient >/dev/null
    check_permissions
    "$age_bin" --version
    "$jq_bin" --version
    info 'OK — 加密文件、权限与 Git 明文检查通过 / encrypted vault checks passed'
  fi
}

command_init() {
  local identity_plain vault_plain recipient staged_identity staged_recipient staged_vault
  require_dependencies
  ensure_secrets_dir
  require_consistent_state
  [[ "$(vault_file_count)" -eq 0 ]] || die "vault 已存在，init 不会覆盖 / refusing to overwrite existing vault"
  acquire_lock
  new_plain_work_dir
  new_stage_dir

  identity_plain="$work_dir/identity.txt"
  vault_plain="$work_dir/vault.json"
  staged_identity="$stage_dir/identity.age"
  staged_recipient="$stage_dir/recipient.txt"
  staged_vault="$stage_dir/vault.json.age"
  register_plain_file "$identity_plain"
  register_plain_file "$vault_plain"
  register_staged_file "$staged_identity"
  register_staged_file "$staged_recipient"
  register_staged_file "$staged_vault"

  "$age_keygen_bin" -o "$identity_plain" >/dev/null 2>&1
  chmod 600 "$identity_plain"
  "$age_keygen_bin" -y "$identity_plain" > "$staged_recipient"
  chmod 600 "$staged_recipient"
  recipient="$(tr -d '\r\n' < "$staged_recipient")"
  [[ "$recipient" =~ ^age1[0-9a-z]+$ ]] || die "age-keygen 没有生成有效 recipient / invalid generated recipient"

  printf '{}\n' > "$vault_plain"
  chmod 600 "$vault_plain"
  info '请设置一个从未公开过的新强口令；可直接按 Enter 采用 age 生成的随机词组。'
  info 'Set a new, never-shared passphrase; press Enter to let age generate one.'
  "$age_bin" -p -a -o "$staged_identity" "$identity_plain"
  "$age_bin" -a -r "$recipient" -o "$staged_vault" "$vault_plain"
  chmod 600 "$staged_identity" "$staged_vault"

  mv "$staged_identity" "$identity_encrypted"
  mv "$staged_recipient" "$recipient_file"
  mv "$staged_vault" "$vault_encrypted"
  staged_files=()
  rmdir "$stage_dir"
  stage_dir=''
  info '初始化完成 / Initialized. Commit only identity.age, recipient.txt, and vault.json.age.'
}

command_set() {
  local name="$1" secret_value updated
  validate_injection_name "$name"
  acquire_lock
  decrypt_vault
  printf '请输入 %s（输入隐藏）/ Enter value (hidden): ' "$name" >&2
  IFS= read -r -s secret_value || die "没有读取到 value / no value received"
  printf '\n' >&2
  [[ -n "$secret_value" ]] || die "value 不能为空 / value must not be empty"
  [[ "$secret_value" != *$'\n'* && "$secret_value" != *$'\r'* ]] || die "value 必须是单行 / value must be one line"

  updated="$work_dir/updated.json"
  register_plain_file "$updated"
  printf '%s' "$secret_value" | "$jq_bin" --arg key "$name" --rawfile value /dev/stdin '.[$key] = $value' "$active_vault" > "$updated"
  unset secret_value
  encrypt_vault_replacement "$updated"
  info "已保存 $name / saved"
}

command_remove() {
  local name="$1" updated
  validate_name "$name"
  acquire_lock
  decrypt_vault
  "$jq_bin" -e --arg key "$name" 'has($key)' "$active_vault" >/dev/null || die "不存在该 key / key not found: $name"
  updated="$work_dir/updated.json"
  register_plain_file "$updated"
  "$jq_bin" --arg key "$name" 'del(.[$key])' "$active_vault" > "$updated"
  encrypt_vault_replacement "$updated"
  info "已删除 $name / removed"
}

command_list() {
  decrypt_vault
  "$jq_bin" -r 'keys[]' "$active_vault"
}

command_get() {
  local name="$1"
  validate_name "$name"
  decrypt_vault
  printf '警告：secret 将写到标准输出 / Warning: secret is being written to stdout.\n' >&2
  "$jq_bin" -er --arg key "$name" 'if has($key) then .[$key] else error("key not found") end' "$active_vault" \
    || die "不存在该 key / key not found: $name"
}

decode_base64() {
  if printf 'eA==' | base64 --decode >/dev/null 2>&1; then
    base64 --decode
  else
    base64 -D
  fi
}

command_copy() {
  local name="$1" clipboard_bin encoded
  validate_name "$name"
  decrypt_vault
  "$jq_bin" -e --arg key "$name" 'has($key)' "$active_vault" >/dev/null || die "不存在该 key / key not found: $name"
  if [[ -n "${YOKULI_CLIPBOARD_BIN:-}" ]]; then
    clipboard_bin="$YOKULI_CLIPBOARD_BIN"
  elif command_exists pbcopy; then
    clipboard_bin='pbcopy'
  elif command_exists wl-copy; then
    clipboard_bin='wl-copy'
  else
    die "找不到 pbcopy 或 wl-copy / no supported clipboard command"
  fi
  encoded="$("$jq_bin" -r --arg key "$name" '.[$key] | @base64' "$active_vault")"
  printf '%s' "$encoded" | decode_base64 | "$clipboard_bin"
  unset encoded
  info "已复制 ${name}；注意同步剪贴板风险 / copied; beware clipboard sync"
}

command_run() {
  local key encoded secret_value
  [[ "${1:-}" == '--' ]] || die "run 需要 -- 分隔命令 / run requires --"
  shift
  [[ "$#" -gt 0 ]] || die "run 缺少命令 / command is required"
  decrypt_vault
  while IFS= read -r key; do
    validate_injection_name "$key"
    encoded="$("$jq_bin" -r --arg key "$key" '.[$key] | @base64' "$active_vault")"
    secret_value="$(printf '%s' "$encoded" | decode_base64)"
    export "$key=$secret_value"
    unset encoded secret_value
  done < <("$jq_bin" -r 'keys[]' "$active_vault")

  cleanup_plaintext
  trap - EXIT INT TERM HUP
  exec "$@"
}

command_rotate() {
  local identity_plain expected_recipient actual_recipient staged_identity
  require_dependencies
  require_initialized
  acquire_lock
  new_plain_work_dir
  new_stage_dir
  identity_plain="$work_dir/identity.txt"
  staged_identity="$stage_dir/identity.age"
  register_plain_file "$identity_plain"
  register_staged_file "$staged_identity"
  "$age_bin" -d -o "$identity_plain" "$identity_encrypted"
  chmod 600 "$identity_plain"
  expected_recipient="$(read_recipient)"
  actual_recipient="$("$age_keygen_bin" -y "$identity_plain")"
  [[ "$expected_recipient" == "$actual_recipient" ]] || die "identity 与 recipient 不匹配 / identity mismatch"
  info '请设置新的强口令。旧提交仍可能由旧口令解密；如有泄露还必须轮换上游 API key。'
  info 'Set a new strong passphrase. Old Git ciphertext remains; rotate provider keys after exposure.'
  "$age_bin" -p -a -o "$staged_identity" "$identity_plain"
  chmod 600 "$staged_identity"
  mv -f "$staged_identity" "$identity_encrypted"
  staged_files=()
  rmdir "$stage_dir"
  stage_dir=''
  info '主口令包装已更新 / passphrase wrapping rotated'
}

main() {
  local command="${1:-help}"
  [[ "$#" -eq 0 ]] || shift
  case "$command" in
    doctor)
      [[ "$#" -eq 0 ]] || die "doctor 不接受参数 / unexpected arguments"
      command_doctor
      ;;
    init)
      [[ "$#" -eq 0 ]] || die "init 不接受参数 / unexpected arguments"
      command_init
      ;;
    set)
      [[ "$#" -eq 1 ]] || die "用法：set NAME / usage: set NAME"
      command_set "$1"
      ;;
    remove)
      [[ "$#" -eq 1 ]] || die "用法：remove NAME / usage: remove NAME"
      command_remove "$1"
      ;;
    list)
      [[ "$#" -eq 0 ]] || die "list 不接受参数 / unexpected arguments"
      command_list
      ;;
    get)
      [[ "$#" -eq 1 ]] || die "用法：get NAME / usage: get NAME"
      command_get "$1"
      ;;
    copy)
      [[ "$#" -eq 1 ]] || die "用法：copy NAME / usage: copy NAME"
      command_copy "$1"
      ;;
    run)
      command_run "$@"
      ;;
    rotate)
      [[ "$#" -eq 0 ]] || die "rotate 不接受参数 / unexpected arguments"
      command_rotate
      ;;
    help|-h|--help)
      usage
      ;;
    *)
      usage >&2
      die "未知命令 / unknown command: $command"
      ;;
  esac
}

main "$@"
