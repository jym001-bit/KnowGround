package com.yizhaoqi.smartpai.config;

/** The stable read/write name; physical indices are kept for rollback. */
public final class KnowledgeIndex {
    public static final String ALIAS = "knowledge_base_current";
    public static final String LEGACY = "knowledge_base";

    private KnowledgeIndex() {
    }
}
