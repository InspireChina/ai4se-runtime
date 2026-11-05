# ReviewPackage Schema v1.0

稳定结构契约。生成器必须输出下列文件；内容可为占位，**文件名与相对路径不得变更**。

| 相对路径 | 必需 | 说明 |
|----------|------|------|
| `manifest.json` | 是 | 包元数据（schemaVersion、packageId、generatedAt、sections） |
| `architecture.md` | 是 | 架构视角摘要 / 不变式关注点 |
| `changed-files.md` | 是 | 变更文件清单（可由本地 git 填充） |
| `tech-debt.md` | 是 | 已知技术债 / TODO |
| `roadmap.md` | 是 | 近期路线与边界（本包不实现什么） |
| `review-request.md` | 是 | 请审阅人关注的问题清单 |

## manifest.json 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `schemaVersion` | string | 固定 `"1.0"` |
| `packageId` | string | 包目录名，如 `rp-20260728-184000` |
| `generatedAt` | string | ISO-8601 UTC |
| `generator` | string | `ai4se-review-tools` |
| `sections` | string[] | 固定五段：architecture, changed-files, tech-debt, roadmap, review-request |

## 禁止

- 不引入 Runtime Domain / Kernel 对象
- 不调用 GitHub API
- 不依赖 AI 服务
- 不把本目录当作 Frozen Architecture 源
