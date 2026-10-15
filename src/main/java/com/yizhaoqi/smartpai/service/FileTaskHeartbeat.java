package com.yizhaoqi.smartpai.service;

/** Only file-consumer threads have a heartbeat; query embeddings do not touch task rows. */
public final class FileTaskHeartbeat {
    private static final ThreadLocal<Runnable> CURRENT = new ThreadLocal<>();

    private FileTaskHeartbeat() { }

    public static Scope bind(Runnable pulse) {
        if (CURRENT.get() != null) throw new IllegalStateException("文件任务心跳重复绑定");
        CURRENT.set(pulse);
        return () -> CURRENT.remove();
    }

    public static void pulse() {
        Runnable callback = CURRENT.get();
        if (callback != null) callback.run();
    }

    public interface Scope extends AutoCloseable {
        @Override void close();
    }
}
