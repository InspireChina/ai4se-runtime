# 客户模型工具接入 Runbook v1 · Terminal Host Bridge

> 适用：客户在本机或云桌面使用任何**能够执行经批准本地命令**的模型工具。
> 当前发行 Profile 是 `terminal-host`；它是 Claude、Cursor、Codex、OMP 和其它工具的
> 共同最小能力，不把它伪称为某一厂商的原生插件。

## 0. 先确认边界

- 只在客户批准的电脑/云桌面、客户代码仓和客户模型网关中执行；
- 不上传客户源码、密钥、Cookie、原型截图或知识到 AI4SE 产品仓；
- Bridge 不启动第二个模型进程。白天的 Discovery/Specification 由当前打开的客户模型完成；
- 无人值守仅在 Plan 被人批准后启动，且客户机器必须已批准对应 CLI/API Adapter；
- 运行只会 local commit，绝不 push。

## 1. 取得 Runtime 发行包

在受信任来源获取已构建的 `ai4se-runtime.jar`，放在客户允许的工具目录。例如：

```bash
export AI4SE_JAR=/opt/ai4se/ai4se-runtime.jar
java -jar "$AI4SE_JAR" --help
```

从源码构建仅适用于客户明确允许构建工具链的场景：

```bash
git clone <approved-ai4se-runtime-repository>
cd ai4se-runtime
mvn -pl ai4se-demo -am package -DskipTests
export AI4SE_JAR="$PWD/ai4se-demo/target/ai4se-runtime.jar"
```

## 2. 进入客户仓并安装薄 Host Profile

```bash
cd /path/to/customer-repo
java -jar "$AI4SE_JAR" install \
  --workspace "$PWD" \
  --host terminal-host \
  --runtime-jar "$AI4SE_JAR"
```

这只生成 `.ai4se/host/installation.properties` 和 `.ai4se/host/AI4SE-HOST.md`。
如果同名文件内容不同，安装会拒绝覆盖。先让客户确认差异，不要用删除或强制覆盖绕过。

把 `AI4SE-HOST.md` 的内容作为当前客户工具的项目 Skill/Rule/Command 指令：

- Claude：放进客户批准的 Claude 项目 Skill/Command；
- Cursor：放进客户批准的项目 Rule/Command；
- Codex/OMP：作为项目级 Skill/Tool 指令或通过其本地工具入口调用；
- 其它工具：将文件内容粘贴为该工具的项目规则，并只允许其执行文中列出的命令。

AI4SE 不自动改写这些厂商配置，因为客户环境的配置位置、权限和审计政策不同。

## 3. 确定性建槽与当前模型化摸底

首次项目接入：

```bash
java -jar "$AI4SE_JAR" onboard \
  --workspace "$PWD" \
  --runtime-root /path/to/ai4se-runtime-source
```

先人工核对 `.ai4se/repository/entries.yaml` 的真实构建、测试、启动入口；不能把“命令能运行”
当作“测试已执行”。之后在当前模型会话中发出：

```text
请按 .ai4se/host/AI4SE-HOST.md 执行项目摸底。先准备 Discovery Package；
只读 Package 中列出的 P1 和必要源码；建立 source-grounded knowledge candidate；
不得修改业务源码或 verified knowledge。
```

当前模型执行：

```bash
java -jar "$AI4SE_JAR" bridge prepare-discovery \
  --workspace "$PWD" --candidate initial-repository --scope repository
```

模型读取输出的 `package/model-input.md`，只写：

```text
.ai4se/knowledge-candidates/initial-repository/candidate.yaml
.ai4se/knowledge-candidates/initial-repository/documents/*.md
```

然后模型/操作者提交：

```bash
java -jar "$AI4SE_JAR" bridge submit-discovery \
  --workspace "$PWD" --candidate initial-repository --scope repository
```

只有显示 `next=HUMAN_KNOWLEDGE_APPROVAL` 后，真实知识负责人才能执行：

```bash
java -jar "$AI4SE_JAR" approve-knowledge \
  --workspace "$PWD" --candidate initial-repository --actor <real-knowledge-owner>
```

批准不是自动提交。知识负责人核对新增文档和索引后，显式建立仅包含这一候选知识的**本地**基线：

```bash
java -jar "$AI4SE_JAR" checkpoint-knowledge \
  --workspace "$PWD" --candidate initial-repository
```

这个命令只会提交候选目录、其已验证知识文档和知识索引；若存在业务代码、另一候选或其它未知
脏文件会拒绝，绝不 push。它解决了“首轮知识已批准但下一张卡仍被干净工作树门禁挡住”的实际断点，
并保留审批与提交两个独立的人类控制点。

## 4. 附件、原型和需求卡

把截图、导出的原型 PDF、接口文档等先以本地文件保存。Browser Relay 只有在客户工具已经获取了
材料且客户允许保留本地快照时才能使用；URL 自身不是理解证据。

```bash
java -jar "$AI4SE_JAR" intake \
  --workspace "$PWD" \
  --story checkout-promotion-001 \
  --request-file /approved-input/checkout-promotion.md \
  --attachment /approved-input/checkout-wireframe.png \
  --attachment /approved-input/coupon-api.pdf
```

随后对当前模型说：

```text
执行 bridge prepare-specification；只阅读其 Package；根据原始需求、已捕获附件和代码证据，
产出 candidate requirement 或带代码证据的澄清问题。不要写业务源码，不要自行冻结。
```

```bash
java -jar "$AI4SE_JAR" bridge prepare-specification \
  --workspace "$PWD" --story checkout-promotion-001

# 模型写完受控 specification/ 后：
java -jar "$AI4SE_JAR" bridge submit-specification \
  --workspace "$PWD" --story checkout-promotion-001
```

若结果为 `HUMAN_SPEC_CLARIFICATION`，人必须真实回答：

```bash
java -jar "$AI4SE_JAR" answer-spec \
  --workspace "$PWD" --story checkout-promotion-001 \
  --answer "<product decision>" --actor <real-product-owner>
```

让当前模型再次 `prepare-specification` / `submit-specification`，直到得到候选规格。人审阅后冻结：

```bash
java -jar "$AI4SE_JAR" freeze-spec \
  --workspace "$PWD" --story checkout-promotion-001
```

## 5. 从冻结规格进入既有 Delivery 主链

后续严格沿用 [客户仓建库与首卡 Runbook](./customer-repository-discovery-runbook.md)：

1. `run` 进行 Analysis / Planning；
2. 真实回答 Analysis 的业务问题；
3. 审阅 Plan、冻结 probes、`approve-plan`；
4. 以客户批准的 Adapter `resume` 无人值守执行 Development → Verify → Defect → Review → local commit；
5. 人做最终验收，`accept` 或 `reject`；
6. 仅在交付确实影响知识时，创建候选知识刷新并由人批准。

不要把 `terminal-host` 的交互模型与正在运行的无人值守 Adapter 混为同一个会话；它们通过冻结的
`.story` Package 交接，而不是通过复制聊天上下文交接。

## 6. 停止规则

| 现象 | 正确动作 |
|---|---|
| URL 无法访问、登录失败、原型无快照 | 记录附件不可用，形成澄清；不猜测页面行为 |
| Bridge 发现业务源码、未知 `.ai4se` 改动或另一张未完成 Story | 停止并清理/提交无关改动；不放宽范围 |
| 模型未写合格知识/规格 | Bridge 拒绝；显示契约错误给当前模型一次修正，不推进阶段 |
| 没有客户批准的 CLI/API Adapter | 停在 Plan 后，不宣称可无人值守 |
| Review 非 PASS、Probe 未证明或测试环境故障 | 保留证据并停止；不自动推送或把失败改成成功 |
