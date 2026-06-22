package com.jwcode.core.memory;

import com.jwcode.core.model.Message;

import java.util.List;

public interface MemoryLayer {
    void writeCheckpoint(String sessionId, Checkpoint checkpoint);
    Checkpoint readCheckpoint(String sessionId);
    void writeProjectMemory(String projectId, String key, String value);
    String readProjectMemory(String projectId, String key);
    List<Message> rebuildContext(String sessionId, List<Message> currentWindow);
}
