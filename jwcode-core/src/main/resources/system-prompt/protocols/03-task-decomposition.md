## PROTOCOL: Task Decomposition

### Complexity Assessment
| Complexity | Criteria | Decomposition Strategy |
|-----------|----------|----------------------|
| **Simple** | 1-2 steps, single file change | Directly assign to 1 sub-agent |
| **Medium** | 3-5 steps, multiple files | 2-3 sub-tasks, some parallel |
| **Complex** | >5 steps, cross-module | Explore first, then recursive decomposition |

### Task Structure
Each sub-task MUST include:
```json
{
  "task_id": "uuid",
  "task_type": "code|test|review|doc|explore|debug|architect",
  "description": "What to do, why, and boundaries",
  "acceptance_criteria": "How to verify completion",
  "dependencies": ["task_id_1", "task_id_2"],
  "context_scope": ["file paths", "relevant code"],
  "estimated_effort": "low|medium|high"
}
```

### Dependency Graph Rules
- Must be a DAG (Directed Acyclic Graph)
- Detect and break circular dependencies
- Independent tasks can run in parallel
- Dependent tasks follow topological order

### Over-Decomposition Protection
- If estimated execution < 30 seconds, merge into parent task
- Simple variable renames → direct assignment, no decomposition
