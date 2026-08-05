# B 套签收指针（本仓只留指针，不落客户正文）

> 证据在**客户仓 / 脱敏仓** `.story/<id>/pathway-evidence/`。本文件只记录水位与如何复跑。

## 口径

| 项 | 状态 |
|----|------|
| A 套 V3/V4（脚本化 verify / fixture Dev） | ✅ 编排绿（≠ 通路通） |
| **B 脱敏仓** V3/V4（真 `mvn test` + Dev Adapter **脊骨**） | ✅ `hybrid_adapter_dev` |
| B Dev 实现 | ⚠️ 默认 **`FunctionalModelCliAdapter` 预置 writeFixed**（证明脊骨，**不**证明非预置模型开发） |
| Analysis/Plan 经 Adapter | ❌ 仍 fixture（披露） |
| S5 人验收 | ⚠️ 证据 `kind: FIXTURE`（控制面门闸绿，真人闸未签） |
| 现场真客户仓 | ❌ 未跑 |
| 对外「通路通」 | ❌ **禁止**用本指针勾选 |

手册：B = 真仓**或**脱敏仓。脱敏绿 ≠ 现场通。  
`signoff_claim: adapter_spine_wiring_functional` = Package→Adapter 接线已通；**八问 Q3 不得勾「非预置 Dev」**。

## 复跑

```bash
mvn -pl ai4se-orchestration -am test \
  -Dtest=PathwayBSuiteSignOffIntegrationTest,PathwayAdapterOnSpineIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

夹具：`ai4se-orchestration/src/test/resources/b-suite-desensitized/`。

## 签收覆盖（脊骨级，非通路通）

- V3/V4：Dev **Package → Adapter 一次** → 真 `mvn -q test`；失败不 Retry  
- 证据：`spine_mode: hybrid_adapter_dev` · `adapter_invoked: true` · `adapter_kind: functional_hook` · `execution/adapter-dev-round-*.md`  
- Verify：嵌入 AC+Diff；`verdict_basis: customer_entry_exit_code`  
- 披露：`discovery_prepared_by_runner: true` · `human_acceptance_kind: fixture`  

## 现场真仓 / 非预置 Dev 怎么接

1. onboard 一次 → 开 Story + 真实 Acceptance  
2. `PathwayRunner` · `suite("B")` · `devAdapter(CursorCliAdapter|ClaudeCliAdapter)`（非 Functional 预置）· `verifyCommand` ∈ entries  
3. 证据核对 `adapter_kind: model_cli`、真人 S5 后再谈通路签收；本指针只记日期与结论  
