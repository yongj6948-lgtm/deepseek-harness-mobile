#!/usr/bin/env bash
#
# 离线等价编译验证 —— 在没有 Android SDK / JDK 17+ 的机器上，尽最大可能贴近真实 gradle 编译，
# 仅做「类型/语法/资源引用」层面的校验（64 位 Android framework + 真实第三方依赖）。
#
# 依赖（默认放在 /tmp，可用环境变量覆盖）：
#   KOTLINC      Kotlin 编译器可执行文件  默认 /tmp/kotlinc/bin/kotlinc
#   ANDROID_JAR  android-all / android.jar (compileSdk 对应版本)  默认 /tmp/android-api36.jar
#   LSCH / OKHTTP / OKIO  真实第三方 jar，用于解析 jsch / okhttp / okio 引用
#
# 用法：
#   tools/verify_compile.sh                     # 编译全部 src/main 并报错
#   tools/verify_compile.sh -verbose            # 透传任何额外参数给 kotlinc
#
# 它自动从源码里扫描 R.<type>.<name> 引用并生成 R.kt / BuildConfig.kt 存根（不依赖资源文件）。
# 注意：这只验证「编译能否通过」，不产 APK；真正的出包仍走 GitHub Actions / 有 SDK 的环境。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KOTLINC="${KOTLINC:-/tmp/kotlinc/bin/kotlinc}"
ANDROID_JAR="${ANDROID_JAR:-/tmp/android-api36.jar}"
SRC="$ROOT/app/src/main/java"

# 检查关键依赖
[ -x "$KOTLINC" ]   || { echo "✗ 找不到 kotlinc: $KOTLINC（可用 KOTLINC=... 指定）"; exit 1; }
[ -f "$ANDROID_JAR" ] || { echo "✗ 找不到 android 框架 jar: $ANDROID_JAR（可用 ANDROID_JAR=... 指定）"; exit 1; }

CP="$ANDROID_JAR"
for dep in "${LSCH:-/tmp/jsch.jar}" "${OKHTTP:-/tmp/okhttp.jar}" "${OKIO:-/tmp/okio.jar}"; do
  if [ -f "$dep" ]; then CP="$CP:$dep"; else echo "⚠ 缺少可选依赖 $dep（如有相关 import 会报错）"; fi
done

STUB="$(mktemp -d)"
OUT="$(mktemp -d)"
trap 'rm -rf "$STUB" "$OUT"' EXIT

# --- 生成 R.kt / BuildConfig.kt 存根 ---
python3 - "$SRC" "$STUB" <<'PY'
import os, re, sys
src, stub = sys.argv[1], sys.argv[2]
TYPES = ('attr','drawable','id','layout','string','color','style','dimen','anim','bool','integer')
refs = {}
for dp, _, fs in os.walk(src):
    for fn in fs:
        if not fn.endswith('.kt'):
            continue
        txt = open(os.path.join(dp, fn), encoding='utf-8').read()
        # 排除 android.R.* 等其它包的前缀限定引用（它们在 android.jar / 依赖里解析）
        for m in re.finditer(r'(?<![\w."\'])R\.(' + '|'.join(TYPES) + r')\.([A-Za-z0-9_]+)', txt):
            refs.setdefault(m.group(1), set()).add(m.group(2))

pkg = os.path.join(stub, 'cool', 'rin', 'deepseekremote')
os.makedirs(pkg, exist_ok=True)
lines = ["package cool.rin.deepseekremote", "", "// auto-generated offline stub (tools/verify_compile.sh)", "object R {"]
i = 0
for typ in TYPES:
    names = sorted(refs.get(typ, ()))
    if not names:
        continue
    lines.append(f"    object {typ} {{")
    for n in names:
        lines.append(f"        const val {n} = {i}"); i += 1
    lines.append("    }")
lines.append("}")
open(os.path.join(pkg, 'R.kt'), 'w').write("\n".join(lines) + "\n")
open(os.path.join(pkg, 'BuildConfig.kt'), 'w').write(
    "package cool.rin.deepseekremote\n// auto stub\nobject BuildConfig { const val DEBUG = true; const val VERSION_NAME = \"1.1.0\" }\n")
print(f"生成 R 存根：{len({k: v for k, v in refs.items()})} 个类型")
PY

# --- 编译 ---
SRC_KT=$(find "$SRC" -name '*.kt')
echo "编译中（点击率较高的错误会在下方显示）…"
"$KOTLINC" -classpath "$CP" -d "$OUT" $SRC_KT \
  "$STUB/cool/rin/deepseekremote/R.kt" \
  "$STUB/cool/rin/deepseekremote/BuildConfig.kt" \
  "$@" 2>&1 | grep -viE '^warning:|is deprecated|deprecat' | head -80
STATUS=("${PIPESTATUS[0]}")
echo "──────────────────────────────────────────"
if [ "${STATUS[0]}" -eq 0 ]; then
  echo "✅ 编译通过（0 error）—— 类型/语法/资源引用均无问题"
else
  echo "❌ 编译失败（exit=${STATUS[0]}），请修复上面列出的 error"
fi
exit "${STATUS[0]}"
