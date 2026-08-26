# AI4SE Customer Host Bundle

This bundle is a local delivery control plane, not a model provider and not an IDE plugin.
It never installs a model CLI or changes customer repository source on its own.

Start with `docs/customer-host-bridge-day-one-guide.md`. The complete command contract and
stop rules are in `docs/customer-host-bridge-runbook-v1.md`.

For the normal path, run `bin/ai4se-flow full ...`. It invokes the selected approved Adapter and
pauses in the terminal only for real human decisions. `skills/ai4se-customer-delivery/` is the same
contract packaged as an optional Codex/OMP-style Skill; it does not replace the command or grant
approval authority.
