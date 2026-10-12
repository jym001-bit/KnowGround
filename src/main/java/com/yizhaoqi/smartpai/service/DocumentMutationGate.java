package com.yizhaoqi.smartpai.service;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Coordinates direct document changes with the final index snapshot. */
public final class DocumentMutationGate {
    private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock(true);

    private DocumentMutationGate() {
    }

    public static EmbeddingSwitchGate.Permit read() {
        LOCK.readLock().lock();
        return LOCK.readLock()::unlock;
    }

    public static EmbeddingSwitchGate.Permit write() {
        LOCK.writeLock().lock();
        return LOCK.writeLock()::unlock;
    }
}
