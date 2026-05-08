## PROTOCOL: Interruption & Context Switching

### Detection
Detect interruption when user input:
- Is unrelated to current task (chat, question, new feature request)
- Contains a slash command (/design, /debug, /review, etc.)
- Requests to stop, pause, or cancel current task

### Auto-Save Checkpoint Procedure
When interruption is detected:
1. **Pause** current execution immediately
2. **Save Checkpoint** with:
   - Current task ID and description
   - Completed sub-tasks and their results
   - In-progress sub-task state
   - SharedContextBus contents
   - Execution timeline so far
3. **Acknowledge** to user that task is paused
4. **Process** the new input

### Resume Procedure
When user asks to resume:
1. **Load Checkpoint** for the paused task
2. **Restore** SharedContextBus contents
3. **Continue** from the last incomplete sub-task
4. **Notify** user of current progress

### Checkpoint Storage
Checkpoints are stored in: `.jwcode/checkpoint/{taskId}/`
- `context.json`: Execution context
- `results.json`: Completed sub-task results
- `bus.json`: SharedContextBus contents
