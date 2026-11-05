# Sprint-5.5 Architecture Review — Review Foundation

工程审查结论（非设计文档；不改 Frozen Architecture；不改 Kernel）。

## Verdict

新增 **工程能力**：稳定 ReviewPackage 结构 + 零依赖生成器。不新增 Runtime 业务能力。

## Layout

```
review-package/           # 根目录规范（已提交）
  SCHEMA.md               # v1.0 契约
  _template/              # 占位模板
  generated/              # 生成输出（gitignored）
ai4se-review-tools/       # 独立模块（无 Kernel/Engine/common）
scripts/generate-review-package.sh
```

## Generator

- 输入：`--repo-root`、可选 `--package-id`
- 输出：`manifest.json` + 五段 markdown
- `changed-files.md`：尽力用本地 `git`；失败则占位
- 禁止：AI、GitHub API、Domain 对象

## Isolation

Maven enforcer 禁止依赖：`ai4se-kernel` / `runtime-engine` / `worker-api` / `workers` / `common` / `demo`。
