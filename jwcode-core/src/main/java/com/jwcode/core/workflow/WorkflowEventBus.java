package com.jwcode.core.workflow;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class WorkflowEventBus {
    private final List<Consumer<WorkflowEvent>> subscribers = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<WorkflowEvent> subscriber) {
        if (subscriber != null) {
            subscribers.add(subscriber);
        }
    }

    public void publish(WorkflowEvent event) {
        for (Consumer<WorkflowEvent> subscriber : subscribers) {
            subscriber.accept(event);
        }
    }
}
