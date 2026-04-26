package com.jwcode.core.observability;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 默认的可观测管道实现 — 线程安全、观察器故障隔离。
 */
public class DefaultObservationPipeline implements ObservationPipeline {

    private static final Logger logger = Logger.getLogger(DefaultObservationPipeline.class.getName());

    private final List<Observer> observers = new CopyOnWriteArrayList<>();

    @Override
    public void publish(ObservationEvent event) {
        if (event == null) {
            return;
        }
        for (Observer observer : observers) {
            try {
                if (observer.isInterestedIn(event.getClass())) {
                    observer.onEvent(event);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING,
                    "Observer " + observer.getObserverName() + " failed to handle event " + event.getClass().getSimpleName(),
                    e);
            }
        }
    }

    @Override
    public void subscribe(Observer observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
            logger.fine("Subscribed observer: " + observer.getObserverName());
        }
    }

    @Override
    public void unsubscribe(Observer observer) {
        observers.remove(observer);
        logger.fine("Unsubscribed observer: " + (observer != null ? observer.getObserverName() : "null"));
    }

    @Override
    public List<Observer> getObservers() {
        return List.copyOf(observers);
    }

    public int observerCount() {
        return observers.size();
    }
}
