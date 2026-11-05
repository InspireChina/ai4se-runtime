# review-package/

工程产物目录规范（Sprint-5.5）。**不是** Runtime Kernel 对象，**不是** Frozen Architecture 文档。

## 用途

每次 Architecture Review 前，生成一份结构稳定的 **ReviewPackage**，供人工或后续工具填写/审阅。

## 目录约定

```text
review-package/
  README.md                 # 本说明（已提交）
  SCHEMA.md                 # 结构契约（已提交）
  _template/                # 稳定模板（已提交）
  generated/                # 生成输出（默认不提交）
    <packageId>/
      manifest.json
      architecture.md
      changed-files.md
      tech-debt.md
      roadmap.md
      review-request.md
```

## 生成

```bash
# 推荐
./scripts/generate-review-package.sh

# 或
mvn -pl ai4se-review-tools -q exec:java -Dexec.args="--repo-root ."
```

生成器模块：`ai4se-review-tools`（不依赖 Kernel / Engine）。

---

兼容：`ai4se-review-tools` 仍读写仓库根目录 `review-package/`。模板真源以本目录为准；修改后请同步 `_template` / `SCHEMA.md` 到 `review-package/` 直至工具改路径。
