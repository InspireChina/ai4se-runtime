# Litemall 三张 Story 串行真仓验证手册 v1

> 本手册用于在 `/Users/peng.lv/IdeaProjects/litemall` 的**隔离副本**中验证三张真实后台需求卡。
> 原始 customer seed 不运行 Maven、不写 `.ai4se/`、不接入 Runtime。每次正式运行均是一个新 LAB、一个新 worktree、一次 B-only 的 Codex 运行。

## 1. 目标、纪律与非目标

目标是验证一条小而真实的串行交付链：

```text
一次项目摸底与知识建槽
  → R-002 订单详情（交付并人工接受）
  → 新基线
  → R-003 商品编辑校验（交付并人工接受）
  → 新基线
  → R-004 售后列表（交付并人工接受）
  → 跨运行复盘
```

- Runtime 使用已审阅基线 `788be12812fd4e32223955d0303a18f103277a9f` 的直接后继，且记录实际 jar SHA；Adapter 固定 `codex`。
- 每张卡仅一次正式 `run`：不 retry、不 resume、不人工 ack、不在运行中补提示、不 push。
- 只在真实运行发现 P1（误交付、范围/数据安全、不可运行）时暂停并修 Runtime；单个 P2 只进入复盘 backlog。
- Delivery 的成功终态仅为本地 `AWAITING_HUMAN_ACCEPTANCE`；人工代码/证据审阅后才可 cherry-pick 到下一张卡的 seed。
- 本轮不碰支付、微信退款、库存扣减/回补、短信、数据库迁移、远端写操作。

## 2. 固定路径与安全前置条件

```bash
export AI4SE_ROOT=/Users/peng.lv/IdeaProjects/ai4se-runtime
export CUSTOMER_SEED=/Users/peng.lv/IdeaProjects/litemall
export LAB_ROOT=/Users/peng.lv/IdeaProjects/ai4se-real-story-litemall-serial
export VALIDATION_ROOT=/Users/peng.lv/IdeaProjects/ai4se-real-story-validation
```

运行前必须确认：

```bash
git -C "$CUSTOMER_SEED" status --porcelain
git -C "$CUSTOMER_SEED" rev-parse HEAD
java -version
mvn -version
```

预期 customer seed 工作树为空，且 JDK 为 8。任何非空输出都停止；不要清理、提交或 stash 用户原仓的内容。

### 2.1 本机容器开发数据库是硬前置条件

本轮采用已批准的**本机容器开发库模式**：项目沿用上游默认的 `127.0.0.1:3306/litemall` 与既有应用账号配置，但该逻辑库必须只运行在名为 `litemall-mysql` 的本机 Docker 容器内，不能是宿主机 MySQL，更不能是生产/共享数据库。

启动前确认：

```bash
docker inspect --format '{{.State.Running}} {{range .NetworkSettings.Ports}}{{.}}{{end}}' litemall-mysql
docker exec litemall-mysql mysql -ulitemall -plitemall123456 -Nse 'SELECT DATABASE(), COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()' litemall
```

预期容器运行、3306 映射到本机、数据库名为 `litemall`、且表结构已存在。数据库名、容器 image digest、volume 名、检查命令及结果记入 evidence；不记录 root 凭据。

上游 `litemall_schema.sql` 的开头含有 `drop database` 与 `drop user`，**禁止直接执行该脚本**。首次初始化只能跳过该文件前七行后导入 schema，再导入 table/data SQL；本轮已经完成该安全初始化。不得自动重导、重置或恢复数据库；这类会覆盖开发数据的操作必须由操作者明确授权。

该模式是“真实本机开发环境”，不是每张卡新建一个数据库。代价是可复现性低于逐卡专用库：每张正式运行都必须记录数据库状态，并且在基线测试和 Runtime 运行期间禁止其他写入者。

## 3. Phase A：项目启动、事实摸底与知识包

### 3.1 创建隔离 source clone

```bash
mkdir -p "$LAB_ROOT"
git clone --no-local "$CUSTOMER_SEED" "$LAB_ROOT/source"
export SOURCE_REPO="$LAB_ROOT/source"
export B0="$(git -C "$SOURCE_REPO" rev-parse HEAD)"
git -C "$SOURCE_REPO" status --porcelain
```

在 `source` 内执行 onboarding；这是首次建槽，后续 Story 不重复 onboard：

```bash
"$AI4SE_ROOT/scripts/onboard-repo.sh" "$SOURCE_REPO"
```

脚本只会创建槽位和 Maven 入口草稿，**不等于知识库完成**。必须在隔离 source 内人工校正以下事实后再提交：

- `.ai4se/repository/entries.yaml`：
  - build：`mvn -q -DskipTests package`
  - test：`mvn clean test`
- `.ai4se/repository/baseline.md`：JDK 8、六个 Maven 模块、MySQL 依赖、全量测试可能有 DB/文件副作用；只写可定位的事实，不写改码建议。
- `.ai4se/knowledge/litemall-architecture.md`：模块边界、入口类、配置和数据库依赖。
- `.ai4se/knowledge/admin-order-state.md`：`AdminOrderService` 与 `OrderUtil` 的状态/后台操作事实、源文件路径。
- `.ai4se/knowledge/admin-goods-write-boundary.md`：`AdminGoodsService.validate/create/update` 的输入与写入顺序事实。
- `.ai4se/knowledge/admin-aftersale-state.md`：`AdminAftersaleController` 列表、审核、退款动作与状态事实。
- `.ai4se/index/knowledge.yaml`：为上述四份文档各登记 `id`、`path`、`kind`、`tags`、`refs`、`status: active`；tag 至少含 `litemall` 和对应域名。

知识正文必须带“来源文件路径 + 本次确认的 baseline SHA”，只记录代码/配置已证明的事实。不要把需求卡、模型猜测或优化建议写成知识事实。

建议索引格式：

```yaml
entries: []
- id: litemall-admin-order-state
  path: .ai4se/knowledge/admin-order-state.md
  kind: domain-fact
  tags: [litemall, admin, order]
  refs: [litemall-admin-api/src/main/java/org/linlinjava/litemall/admin/service/AdminOrderService.java, litemall-db/src/main/java/org/linlinjava/litemall/db/util/OrderUtil.java]
  status: active
```

### 3.2 基线构建、启动与事实确认

仅在 `source`（或后续独立 inventory worktree）且本机容器 DB 已就绪、后端服务已停止时执行：

```bash
cd "$SOURCE_REPO"
mvn -q -DskipTests package
mvn clean test
```

记录每条命令的 stdout、stderr、exit code、耗时、JDK/Maven 版本、容器 ID/image digest/volume，以及连接的数据库名（不含凭据）。若根测试存在不可接受的外部副作用，不能把它写入 `entries.yaml`；改用已证明的、最小但仍覆盖目标模块的正式测试入口，并在 baseline.md 说明原因。

可选的后端启动检查使用这个容器开发库：从 `litemall-all/target/` 找到 `*-exec.jar` 后启动；只确认进程成功启动和健康的只读入口。不得登录管理端执行支付、退款、发货或任何写接口。应用存在优惠券/拼团定时任务，故该启动检查结束后必须停掉后端；正式基线测试和 Runtime 运行时后端必须保持停止。

通过后提交一次**控制基线**（仅 `.ai4se/`、`.story/README.md` 与事实/入口材料）：

```bash
git add .ai4se .story/README.md
git commit -m "chore(ai4se): onboard litemall validation baseline"
export CONTROL_BASELINE="$(git rev-parse HEAD)"
```

## 4. 三张冻结需求卡

每张卡启动前都在新的 source/worktree 基线上创建：

```text
.ai4se/acceptance-probes/<story-id>/
  probes.properties
  ac1.sh ... acN.sh
```

探针必须由操作者在运行前写入并提交，必须位于 write scope 之外；`ac.count` 与 requirement 的 AC 数完全相等。探针只能读/运行构建产物或 operator-owned harness，不能以模型本轮新增测试作为唯一判定依据。

### R-002：后台订单详情操作投影

```text
story_id: litemall-admin-order-action-projection-r002
目标：后台订单详情让运营人员看到可读状态与当前可执行的后台动作。
预期改动：AdminOrderService、后台动作投影 DTO/工具类、专属单元测试。
```

- AC1：`GET /admin/order/detail` 的成功响应仍保留 `order`、`orderGoods`、`user`，并新增 `orderStatusText`。
- AC2：响应新增 `adminHandleOption`，且始终含 `pay`、`ship`、`refund`、`delete` 四个 boolean。
- AC3：操作矩阵必须与现有后台端点一致：`101→pay`、`201→ship`、`202→refund`、`102/103/203/401/402→delete`；其他支持的状态四项均为 false。
- AC4：不得把小程序用户动作 `OrderUtil.build()` 直接作为后台动作；查询不写库、不调支付/短信/库存服务。
- AC5：新增单元测试覆盖上述矩阵与响应兼容性；目标模块测试和已确认的正式测试入口均成功。

### R-003：商品编辑请求的防御性校验

```text
story_id: litemall-admin-goods-payload-validation-r003
目标：在写入前明确拒绝不完整或不合法的后台商品/SKU 请求，避免 NPE 与脏数据。
预期改动：AdminGoodsService（及必要的纯校验辅助类）、专属单元测试。
```

- AC1：缺少请求体、`goods`、`attributes`、`specifications` 或 `products` 时稳定返回参数错误，不抛 NPE。
- AC2：`products` 至少一条；每条 SKU 的 `number`、`price` 非空且不小于 0；库存/价格为 0 仍允许。
- AC3：每条 SKU 的 `specifications` 非空、至少一项，且不接受空白规格值。
- AC4：非法输入在二维码生成、商品/货品/购物车更新之前被拒绝；专属测试证明未发生写入型依赖调用。
- AC5：满足既有合法格式的请求保留 create/update 原有流程；目标模块测试和正式测试入口均成功。

### R-004：后台售后列表状态与操作投影

```text
story_id: litemall-admin-aftersale-action-projection-r004
目标：后台售后列表展示可读状态与合法的下一步后台动作，不让前端重复状态机。
预期改动：AdminAftersaleController 或薄服务/投影类、专属单元测试。
```

- AC1：`GET /admin/aftersale/list` 保持既有分页语义，列表项新增 `statusText` 与 `adminHandleOption`。
- AC2：`adminHandleOption` 始终含 `recept`、`reject`、`refund` 三个 boolean；`REQUEST→recept/reject`，`RECEPT→refund`。
- AC3：`INIT/REFUND/REJECT/CANCEL` 与未知状态均三项 false；未知状态必须失败关闭，不能误放行退款。
- AC4：列表查询严格只读：不更新售后或订单、不调用微信退款、不回库存、不发通知。
- AC5：新增单元测试覆盖状态矩阵、分页兼容性和只读约束；目标模块测试和正式测试入口均成功。

## 5. 每张卡的串行执行协议

对 R-002、R-003、R-004 重复下列步骤；绝不复用旧 B worktree、旧 evidence 或旧 `.story/<story-id>/run`。

1. 从上一张**已人工接受并已 cherry-pick** 的 delivery commit 创建新 source/worktree；重新记录 `baseline_commit`。R-002 使用 `CONTROL_BASELINE`。
2. 将本卡 requirement.md 写入新的 evidence 根，计算 SHA-256 后设为只读；编写并提交本卡 probes。所有 setup 提交完成后，B worktree 必须干净。
3. 确认后端已停止、无其他写入者后，在此新基线上重跑并记录 build/test；若失败或基线 SHA 改变，停止，不能拿上一张卡的摸底结论直接开发。
4. 建新的 B worktree，冻结 Runtime jar SHA、Codex binary/version、requirement SHA、probe SHA、write scope 和数据库名。
5. 启动一次 `ai4se-runtime.jar run --adapter codex`；建议预算为最多 2 个 Development rounds、15 分钟。运行中零人工干预，且后端服务保持停止。
6. 用 `scripts/real-story-collect-evidence.sh` 收集不可覆盖的原始证据；再独立重跑每条 frozen probe、审阅 diff 和 write scope。
7. 只有终态 `AWAITING_HUMAN_ACCEPTANCE`、全部 AC `PROVEN`、Review PASS、独立复测通过时，才人工接受并 cherry-pick Delivery commit 到下一张卡的 source。此后可启动后端，执行额外的人工只读/受控功能验证并单独记录；否则冻结本卡，写 retrospective 后再决定下一步。

## 6. 每张卡的 evidence 与复盘

每张卡 evidence 至少包含 requirement/probe/runtime SHA、基线测试、运行 stdout/stderr、`.story` 快照、每条 AC verdict、delivery patch、changed files、独立复测、scorecard、`REVIEW-REQUEST.md` 与总 SHA-256 清单。

在 `$VALIDATION_ROOT` 维护独立于原始 evidence 的：

```text
scorecard.csv
retrospectives/R-002-retrospective.md
retrospectives/R-003-retrospective.md
retrospectives/R-004-retrospective.md
improvement-register.md
```

每次复盘只登记有证据链接的事实：功能完成率（PROVEN AC / AC 总数）、首次交付、独立缺陷、逃逸缺陷、首个阻断阶段、wall time、rounds 和人工介入。只升级 P1 或跨两卡重复的 P2；不要在第一张失败后扩建并发调度或多 Agent。
