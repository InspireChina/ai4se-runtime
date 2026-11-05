# Story templates

`.story/<id>/` 过程结构。开 Story：

```bash
./scripts/open-story.sh /path/to/customer-repo <story-id>
```

| 路径 | 说明 |
|------|------|
| [`_template/requirement.md`](./_template/requirement.md) | 种子需求（raw / goal / in_out_scope / **acceptance**） |
| `.story/<id>/packages/` | 各阶段 Context Package（由 02 Builder 写出） |

合同：通路验证手册 S2；字段细节见 Analysis 合同。
