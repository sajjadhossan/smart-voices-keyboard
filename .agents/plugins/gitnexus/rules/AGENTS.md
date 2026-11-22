# GitNexus Code Intelligence Rules

This repository is indexed by GitNexus:
- Knowledge Graph location: `.gitnexus/`
- Coverage: Full AST indexing of JavaScript, Kotlin, Android, Desktop, and Tests.
- Health Check: `npx --yes gitnexus status`
- Re-index on updates: `npx --yes gitnexus analyze .`

Rules for Agent:
1. Before modifying or renaming any exported functions or constants, check blast radius: `npx --yes gitnexus impact <symbol>`.
2. Trace flow pathways using `npx --yes gitnexus context <symbol>`.
3. Keep the index up-to-date after major features or refactoring.
