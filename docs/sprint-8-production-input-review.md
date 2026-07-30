# Sprint-8 Production Input Layer — Review Package

> Kernel frozen. Loader / CLI live outside Runtime.  
> Evidence: `sample-input/` + `sample-workspace/` + `ProductionInputLoader` + `ai4se-runtime.jar`.

---

## 【A】Production Input Architecture

```text
input/ (files)
    ↓
ProductionInputLoader   ← Demo/CLI layer（非 Kernel）
    ↓
DeliveryScenario
    ↓
SerialDeliveryRunner    ← Demo 编排（非 StageRunner）
    ↓
Runtime.submit()        ← Kernel 不变：单步结算
    ↓
Worker (Shell / FileEdit)
```

| 层 | 职责 |
|----|------|
| **Input** | 人写的交付合同：需求、发现命令、计划、补丁、验收命令 |
| **Loader** | 读文件 → Scenario；**不**改 Task/Artifact 模型 |
| **Runtime** | 单次 submit 的 Task/Context/Trace/Artifact/Checkpoint/`RuntimeResult` |
| **Worker** | 真实副作用（写文件 / git / mvn） |
| **Demo CLI** | 拷贝 workspace、串六段、打印结果；可 `java -jar` |

---

## 【B】Input Contract

见 `ai4se-demo/sample-input/CONTRACT.md`。

```text
input/
  requirement.md     # 必填
  profile.yaml       # id, typeLabel?, projectId?
  verify.yaml        # command: …
  discovery.yaml     # 可选 command；缺省 echo skip-discovery
  plan.md            # → workspace PLAN.md
  patches/**         # → FileEdit 相对路径
```

**为何这样设计：**  
把原先锁在 Java `*Scenario` 里的「生产输入」外置成文件，使 Runtime 可被非 Java 作者驱动；字段最少、可手写、Loader 无第三方 YAML 引擎（FlatYaml）。

---

## 【C】Boundary Check

| 检查 | 结果 |
|------|------|
| Runtime 生命周期 / Artifact / Trace / Checkpoint / Worker SPI | **未改** |
| Scheduler / Workflow / StageRunner / Learning / Knowledge / Memory / Evolution | **未加** |
| 边界漂移 | Loader 与串行编排仍在 Demo；**无** Kernel 漂移 |
| 新风险 | CLI/jar 易被误称为「Runtime 产品」——实为 **Input+Demo 外壳** |

---

## 【D】Architecture Drift（谁干什么）

| 角色 | 承担 |
|------|------|
| **Runtime** | 单步结算与可观测 Result（冻结） |
| **Input** | 生产输入合同（人写） |
| **Demo/CLI** | Loader、workspace 拷贝、六段串联、fat jar 入口 |
| **Worker** | allowlist 内执行 |

---

## 【E】Lessons Learned（三个）

1. **脱离 Java Scenario ≠ 进 Kernel**：外置 input/ 即可证明「可被当作 Runtime 使用」。  
2. **生产输入的本质是补丁+验收命令**：Loader 只是搬运工；质量仍在人。  
3. **Fat jar 是入口不是平台**：`ai4se-runtime.jar` 打的是 Demo+Workers+Engine，不是新 Domain。

---

## 【F】Next Recommendation（仅一个）

**对真实（非 sample）Java 仓库跑一份人手写的 Delivery Bundle（input/），用现有 jar 验收。**

- **为什么：** Input Layer 已通；下一证据缺口是「真实仓库路径 + 真实补丁」，不是再抽象。  
- **为什么不是 StageRunner / Workflow / Scheduler：** 编排已在 CLI 外；瓶颈仍是内容。  
- **为什么不是 Knowledge / Learning / AI Worker / Resume：** 无新痛点证据；本 Sprint 禁令仍适用。

---

## How to run

```bash
mvn -pl ai4se-demo -am package
java -jar ai4se-demo/target/ai4se-runtime.jar \
  --workspace ai4se-demo/sample-workspace \
  --input ai4se-demo/sample-input

# or
./scripts/run-production.sh
```
