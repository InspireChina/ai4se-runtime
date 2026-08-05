# scripts/

| 脚本 | 域 | 说明 |
|------|----|------|
| `onboard-repo.sh` | 01 | 客户仓建 `.ai4se/` / `.story/` 槽位；无构建系统时 `build/test: unknown` |
| `open-story.sh` | 03/S2 | 开 `.story/<id>/requirement.md`（可填模板或传入种子） |
| `run-production.sh` | 08/demo | 历史启动辅助（预置 input，非 Story 主链） |
| `generate-review-package.sh` | templates/review | Review 包生成 |

## Pathway W1 / W2 / B-suite

**验收靠自动测试，不要人手复跑同一条 CLI。**

```bash
mvn -pl ai4se-context,ai4se-execution,ai4se-orchestration -am test
```

B 脱敏签收单独复跑：

```bash
mvn -pl ai4se-orchestration -am test \
  -Dtest=PathwayBSuiteSignOffIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

覆盖：onboard 多构建系统、Acceptance 拒跑、V3/V4 无人值守、**B 脱敏真 `mvn test`**。指针：[`../docs/90-status/b-suite-signoff-pointer.md`](../docs/90-status/b-suite-signoff-pointer.md)。

手工 CLI 仅调试用：

```bash
mvn -pl ai4se-demo -am package -DskipTests
JAR=ai4se-demo/target/ai4se-runtime.jar
./scripts/onboard-repo.sh /path/to/workspace
java -cp "$JAR" com.ai4se.runtime.demo.pathway.PathwayWaveMain \
  --workspace /path/to/workspace --wave w1
```
