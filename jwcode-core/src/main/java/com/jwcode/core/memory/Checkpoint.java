package com.jwcode.core.memory;

import java.util.List;

public record Checkpoint(
    String intent,
    String nextAction,
    String constraints,
    List<TaskNode> taskTree,
    String currentWork,
    List<String> involvedFiles,
    String crossTaskFindings,
    String errorsAndFixes,
    String runtimeState,
    String designDecisions,
    String miscNotes
) {
    public Checkpoint {
        taskTree = taskTree == null ? List.of() : List.copyOf(taskTree);
        involvedFiles = involvedFiles == null ? List.of() : List.copyOf(involvedFiles);
    }

    public static Checkpoint empty() {
        return new Checkpoint("", "", "", List.of(), "", List.of(), "", "", "", "", "");
    }
}
