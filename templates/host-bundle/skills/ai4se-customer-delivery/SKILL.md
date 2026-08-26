---
name: ai4se-customer-delivery
description: Run an AI4SE-controlled customer-repository delivery through the portable ai4se-flow command. Use when a user asks to onboard a customer repository or deliver a Story under AI4SE, not for ordinary code edits outside the workflow.
metadata:
  short-description: Controlled customer Story delivery
---

# AI4SE Customer Delivery

AI4SE is the delivery control plane; you are a selected model executor, not the workflow owner.
The user talks in business language. They must not be asked to memorize CLI commands, Package paths,
Story directory names or prompt templates.

## User-facing invocations

Treat these as calls to this Skill, whether they arrive as `$ai4se-customer-delivery`, `/ai4se`, or
ordinary natural language in an AI4SE-enabled customer repository:

- `摸底这个项目` / `建立项目知识库`
- `交付这个需求：<text or attachment>`
- `继续 <answer>`
- `验收 <story>：通过` / `拒绝 <story>：<reason>`

Do not ask the user which Runtime command to run. Resolve the current workspace from the working
directory and Runtime Jar from `.ai4se/host/installation.properties`. Use the installed `ai4se`
command internally. If no Host Profile exists, ask once for the approved AI4SE Bundle location; do
not invent paths or install software.

## First-project flow

For `摸底这个项目`, run the deterministic onboarding and Bridge Discovery flow. Read only the prepared
Package; create source-grounded candidate knowledge. Present candidate documents as a compact review
with Evidence, Working Boundary and Unknowns, then ask exactly one question: **“是否批准初始知识库？”**.
Only after an explicit yes may you run knowledge approval/checkpoint. Never modify business source.

## Story flow

For `交付这个需求`, create a stable Story id and capture the user text/local attachments with `intake`.
Prepare and submit Specification. Ask a question only if the Specification produces a concrete,
source-backed ambiguity. If the candidate is ready, present a compact specification review:

```text
目标 / 范围外 / 业务决策 / AC / 建议的最大写入范围
```

Ask **“是否冻结规格及该最大写入范围？”**. The user approves the business boundary; you then freeze
the spec and call the Production Runtime. Do not select a broader write scope than the one shown.

When Analysis raises a concrete question, display the exact numbered questions and options, record the
user answer, and resume. When Plan is ready, show Plan, Change Map, Constraints, Test Strategy and
frozen probes as one concise review and ask **“是否批准 Plan 并开始无人值守交付？”**. Only then may
Development → Verification → bounded defect repair → Review → local commit run unattended.

At terminal success, show the local commit, AC probe verdicts and Review decision, then ask for final
accept/reject. Never answer authority prompts yourself, never push, never alter state files, never
silently retry after a policy/verification/Adapter stop, and never switch Adapter without user choice.

## Terminal fallback

If the host cannot execute individual AI4SE commands reliably, use the installed `ai4se-flow full`
command. It exposes the same human authority points in the terminal. For later Stories with an already
approved knowledge base, add `--existing-knowledge`.

Do not use this Skill to bypass customer access policy for browser materials, attachments, credentials
or deployment.
