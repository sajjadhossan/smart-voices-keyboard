<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **smart-voices-keyboard** (1971 symbols, 3576 relationships, 140 execution flows).

> Index stale? Run `node .gitnexus/run.cjs analyze --index-only` from the project root — it auto-selects an available runner. No `.gitnexus/run.cjs` yet? Bootstrap with `npx`, `bunx`, or `pnpm dlx` — e.g. `bunx gitnexus@latest analyze` (npm 11 npx crash; #1939).

## Always Do

- **MUST run impact before editing.** Use `impact({target: "symbolName", direction: "upstream"})` or `node .gitnexus/run.cjs impact "symbolName" --direction upstream --repo .`; report callers, processes, and risk. Never substitute grep for graph analysis.
- **MUST analyze graph changes before committing.** Use `detect_changes({scope: "all"})` (MCP) or `node .gitnexus/run.cjs detect-changes --scope all --repo .` (CLI fallback). `partial: true` or `truncated: true` is not a clean check — a zero means unseen, not unaffected; re-run it. For regression review: `detect_changes({scope: "compare", base_ref: "main"})` or `node .gitnexus/run.cjs detect-changes --scope compare --base-ref "main" --repo .`.
- MUST warn on HIGH/CRITICAL `risk` pre-edit; never use `riskSharedAxes` to waive a HIGH/CRITICAL `risk` warning. Compare File/symbol: MCP File omits axes; Graph-RAG expands File.
- **MUST treat `risk: UNKNOWN` as unresolved, not as low.** An empty caller set is not evidence the symbol is unused — it can also mean the callers are not resolvable by the index (plain-object property access, dynamic dispatch, cross-language calls). `impact` pairs `UNKNOWN` with a `riskNote` saying so. Confirm with a text search before treating the symbol as safe to change or delete; do not proceed on the strength of a zero.
- **MUST use `query({search_query: "concept"})` for concepts/flows, `context({name: "symbolName"})` for a named symbol, or `impact` for blast radius, on read-only callers, dependencies, imports, or execution flow.** Graph first; text search only for empty/`UNKNOWN`/literals.
- For security review, `explain({target: "fileOrSymbol"})` lists taint findings (source→sink flows; needs `analyze --pdg`).

## Never Do

- NEVER edit a function, class, or method before MCP/CLI impact analysis.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis, and never read `UNKNOWN` as an all-clear — it means the walk could not answer, which is the one verdict that requires confirming by other means.
- NEVER rename symbols with find-and-replace — use `rename` which understands the call graph.
- NEVER commit before MCP/CLI graph change analysis.

## Resources

| Resource | Use for |
| --- | --- |
| `gitnexus://repo/smart-voices-keyboard/context` | Codebase overview, check index freshness |
| `gitnexus://repo/smart-voices-keyboard/clusters` | All functional areas |
| `gitnexus://repo/smart-voices-keyboard/processes` | All execution flows |
| `gitnexus://repo/smart-voices-keyboard/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
| --- | --- |
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->

<!-- auto-intent-dispatch:start -->
# Global Automated Intent Dispatch & Proactive Performance Protocol

- **Default Proactive Speed & Optimization**: NEVER wait for the user to ask for speed or performance. By default, write highly optimized code (non-blocking I/O, indexed queries, zero-alloc patterns). When dealing with CPU bottlenecks or hot loops, proactively apply `fast-anything` or `rust-anything` (Rust hot-swap with 100% behavioral proof) to deliver maximum execution speed automatically.
- **Autonomous Token Minimization**: Proactively minimize context token usage using `graphify` (`query`, `path`, `explain`) and `gitnexus`. Query scoped knowledge graphs instead of loading heavy source files.
- **Replica Trigger**: When the user asks to replicate, clone, or build an app like an existing one (e.g. 'রেপ্লিকা বানাও', 'এর মতো অ্যাপ বানাও', 'build a replica', 'clone this app'), automatically trigger the clean-room `replica-*` pipeline (`replica-recon` -> `replica-architect` -> `replica-build` -> `replica-backend` -> `replica-test` -> `replica-diff` -> `replica-brand` -> `replica-deploy`) with zero-demo commercial-grade code.
- **Code Graph & Impact Trigger**: When the user asks how code works, what will break, or to trace a bug, automatically run `gitnexus` and `graphify` analysis (`impact`, `context`, `query`, `detect_changes`).
<!-- auto-intent-dispatch:end -->
