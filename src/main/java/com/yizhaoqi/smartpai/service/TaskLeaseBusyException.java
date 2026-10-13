package com.yizhaoqi.smartpai.service;

public class TaskLeaseBusyException extends RuntimeException {
    public TaskLeaseBusyException(String taskId) {
        super("文件任务仍被其他消费者持有: " + taskId);
    }
}
