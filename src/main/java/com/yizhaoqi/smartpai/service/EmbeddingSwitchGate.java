package com.yizhaoqi.smartpai.service;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Keeps each query or vector write on one model/index pair during cutover. */
public final class EmbeddingSwitchGate {
    private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock(true);

    private EmbeddingSwitchGate() {
    }

    public static Permit read() {
        LOCK.readLock().lock();
        return LOCK.readLock()::unlock;
    }

    public static Permit write() {
        LOCK.writeLock().lock();
        return LOCK.writeLock()::unlock;
    }

    public interface Permit extends AutoCloseable {
        @Override
        void close();
    }
}
