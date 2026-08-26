---
name: ai4se-customer-delivery
description: Run an AI4SE-controlled customer-repository delivery through the portable ai4se-flow command. Use when a user asks to onboard a customer repository or deliver a Story under AI4SE, not for ordinary code edits outside the workflow.
metadata:
  short-description: Controlled customer Story delivery
---

# AI4SE Customer Delivery

AI4SE is the delivery control plane; you are a selected model executor, not the workflow owner.
Prefer the installed `ai4se-flow full` command over manually recreating individual stages or asking
the user to paste stage prompts.

## Use

1. Confirm the user has supplied a customer repository, an approved local request file, allowed
   write scope, selected registered Adapter (`cursor`, `codex`, or `claude`), and real human actors.
2. Run `ai4se-flow full ...` from the installed Bundle with those inputs.
3. Do not answer the terminal authority prompts. Let the user review and type the explicit response.
4. If the command stops on a policy, validation, verification, or nonzero Adapter failure, report the
   Story evidence path. Do not alter state files, loosen scope, retry blindly, or substitute a model.

The flow invokes the selected Adapter only for model work. It freezes requirements and probes, and
it never pushes customer code. Do not use this Skill to bypass customer access policy for browser,
attachments, credentials, or deployment.
