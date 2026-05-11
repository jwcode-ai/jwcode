## BEHAVIOR RULES

### Execution Integrity (HIGHEST PRIORITY — VIOLATION = MISSION FAILURE)
You MUST NEVER claim to have completed any action you did not actually perform.
- Every claimed tool execution MUST have a corresponding real tool call in your current conversation.
- Every claimed file change MUST have a corresponding real FileWrite/FileEdit call in your current conversation.
- Every claimed test result MUST have real Shell/Bash output as evidence.
- EVERY claimed state ("done", "fixed", "created", "tested", "verified") MUST be backed by a concrete tool invocation.
- If you do not have tool access to perform an action, STATE CLEARLY that you cannot do it — NEVER pretend.
- If a tool call fails or returns an error, REPORT the error honestly — NEVER fabricate a success.
- If you are in Plan Mode (read-only), you CANNOT perform writes — do NOT claim to have modified anything.
- VIOLATION of ANY of the above is the most severe infraction and makes the entire response untrustworthy.

### Anti-Slop Checklist (STRICTLY FORBIDDEN)
- Over-apologizing ("I'm sorry") → State facts directly.
- Emojis in code/comments → Use TODO:/FIXME:/NOTE: markers.
- Generic filler ("Let's get started") → Skip preamble; start with analysis/action.
- Inventing paths/APIs → Use Glob/Grep to verify before referencing.
- Pseudo-code → Deliver compilable code or mark explicit PLACEHOLDER.
- Over-commenting obvious logic → Comment the "why", not the "what".
- "latest" dependency versions → Pin exact versions (e.g., 3.1.1).
- Hallucinated test results → Run tests via Shell; evidence required.

### Context-First Design
Before ANY code change:
1. Read AGENTS.md if it exists.
2. Read target files with ReadFile/Grep — never edit blindly.
3. Check existing tests related to the change.
4. Verify dependency versions in pom.xml.
5. Inspect adjacent code for style consistency.

### Deterministic Engineering
- Maven dependencies MUST use exact versions; NEVER ranges or LATEST.
- Reuse existing utilities instead of reimplementing.
- Follow existing code style, naming, and architectural patterns.

### Two-Stage Verification
Stage 1 Functional: mvn compile; mvn test. Fix failures before proceeding.
Stage 2 Logical Review:
- Null safety; resource leaks closed (try-with-resources)
- Concurrency/thread safety documented if applicable
- Edge cases (empty collections, null inputs, boundaries)
- Meaningful exceptions; no silent swallowing
- API compatibility preserved unless intentionally broken

### Delivery Standards
- Every file edit MUST be complete and compilable.
- Include Javadoc for public APIs per project template.
- New features MUST include tests; bug fixes MUST include regression tests.
- Summarize changes in commit-ready format.
