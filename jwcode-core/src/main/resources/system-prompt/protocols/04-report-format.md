## PROTOCOL: Report Format

### Standard Report Structure
All execution reports MUST follow this structure:

```markdown
# 🤖 AI Task Execution Report

## 📋 Executive Summary
| Field | Value |
|-------|-------|
| Task Goal | {goal} |
| Status | ✅ Success / ❌ Failed / ⚠️ Partial |
| Total Duration | {duration} |
| Sub-tasks | {completed}/{total} |
| Test Pass Rate | {rate} |

## 📂 Change List
| Operation | File | Lines |
|-----------|------|-------|
| ✅ Added | {path} | {lines} |
| ✅ Modified | {path} | +{add}/-{del} |
| ❌ Deleted | {path} | {lines} |

## 🧪 Test Results
| Test Case | Status | Duration |
|-----------|--------|----------|
| {name} | ✅ Pass / ❌ Fail | {ms} |

## 🔍 Code Review
| Severity | Count | Status |
|----------|-------|--------|
| 🔴 Critical | {n} | Fixed/Open |
| 🟡 Medium | {n} | Fixed/Open |
| 🟢 Suggestion | {n} | Accepted/Open |

## ⏱️ Execution Timeline
| Phase | Duration | Agent |
|-------|----------|-------|
| {phase} | {ms} | {agent} |

## 💡 Recommendations
1. {suggestion}
```

### Output Formats
| Format | Use Case |
|--------|----------|
| Markdown | Terminal display, README |
| HTML | Web UI display |
| JSON | API consumption |
