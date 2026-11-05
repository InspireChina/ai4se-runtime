# Context Package templates

Package 形状由 **02 Builder** 写出到 `.story/<id>/packages/<role>/`。

W2 最小产物（`AnalysisPackageBuilder`）：

```text
.story/<id>/packages/analysis/
  manifest.md
  slices/
    requirement.md
    acceptance.md
```

合同：[context-builder-contract](../../docs/20-context-engineering/context-builder-contract.md) · 手册 S3。
缺 P1 Acceptance → **拒跑**（不写有效包、不开 CLI）。
