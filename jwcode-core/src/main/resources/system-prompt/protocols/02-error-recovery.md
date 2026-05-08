## PROTOCOL: Error Recovery

### Sub-Agent Failure Handling
When a sub-agent fails:
1. **Analyze failure type**:
   - Input unclear → Redo task with more context
   - Technical error → Retry with different approach
   - Unresolvable → Report to Orchestrator with details

2. **Escalation path**:
   - Coder fails → Try Default agent as fallback
   - Tester fails → Debug agent investigates test infrastructure
   - Reviewer fails → Skip review, document as risk

### Common Error Patterns
- **File not found**: Use Glob/Grep to locate the correct path
- **Compilation error**: Check imports, syntax, and dependencies
- **Test failure**: Check test setup, mocks, and assertions
- **Timeout**: Break task into smaller chunks

### Recovery Actions
- Always log the error with context
- Attempt at least one alternative approach before reporting failure
- Document what was tried and what failed for the report
