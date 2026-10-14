package com.yizhaoqi.smartpai.service;

public class StaleFileTaskException extends RuntimeException {
    public StaleFileTaskException(String taskId) {
        super("文件任务已被超时恢复器接管: " + taskId);
    }
}
