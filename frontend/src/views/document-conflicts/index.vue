<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { NButton, NPopconfirm, NSpin, NTag } from 'naive-ui';
import {
  decideDocumentConflict,
  fetchConflictDetectionSettings,
  fetchDocumentConflictContext,
  fetchDocumentConflicts,
  undoDocumentConflict,
  updateConflictDetectionSettings
} from '@/service/api/document-conflict';
import type { ConflictContext, ConflictDetectionSettings, DocumentConflict } from '@/service/api/document-conflict';

type ListStatus = 'PENDING' | 'REVIEWED';

const activeStatus = ref<ListStatus>('PENDING');
const conflicts = ref<DocumentConflict[]>([]);
const loading = ref(false);
const loadFailed = ref(false);
const savingId = ref<number | null>(null);
const expandedId = ref<number | null>(null);
const contextLoadingId = ref<number | null>(null);
const contextErrors = ref<Record<number, boolean>>({});
const contexts = ref<Record<number, ConflictContext>>({});
const settings = ref<ConflictDetectionSettings | null>(null);
const settingsLoading = ref(false);
const settingsSaving = ref(false);
const settingsError = ref(false);
let loadSequence = 0;

const pendingCount = computed(() => (activeStatus.value === 'PENDING' ? conflicts.value.length : null));

async function loadSettings() {
  settingsLoading.value = true;
  settingsError.value = false;
  const { data, error } = await fetchConflictDetectionSettings();
  if (error || !data) {
    settingsError.value = true;
  } else {
    settings.value = data;
  }
  settingsLoading.value = false;
}

async function toggleLlmConfirmation() {
  if (!settings.value || settingsSaving.value) return;
  settingsSaving.value = true;
  const nextEnabled = !settings.value.llmConfirmationEnabled;
  const { data, error } = await updateConflictDetectionSettings(nextEnabled);
  settingsSaving.value = false;
  if (error || !data) return;
  settings.value = data;
  window.$message?.success(nextEnabled ? '已开启 LLM 二次确认' : '已关闭 LLM 二次确认');
}

async function loadConflicts() {
  loadSequence += 1;
  const sequence = loadSequence;
  loading.value = true;
  loadFailed.value = false;
  const { data, error } = await fetchDocumentConflicts(activeStatus.value);
  if (sequence !== loadSequence) return;
  if (error || !data) {
    loadFailed.value = true;
  } else {
    conflicts.value = data;
  }
  loading.value = false;
}

function changeStatus(status: ListStatus) {
  if (activeStatus.value === status) return;
  activeStatus.value = status;
  conflicts.value = [];
  expandedId.value = null;
  loadConflicts();
}

async function toggleContext(item: DocumentConflict) {
  if (expandedId.value === item.id) {
    expandedId.value = null;
    return;
  }
  expandedId.value = item.id;
  if (contexts.value[item.id]) return;
  await loadContext(item.id);
}

async function loadContext(id: number) {
  if (contextLoadingId.value === id) return;
  contextLoadingId.value = id;
  contextErrors.value[id] = false;
  const { data, error } = await fetchDocumentConflictContext(id);
  if (error || !data) {
    contextErrors.value[id] = true;
  } else {
    contexts.value[id] = data;
  }
  contextLoadingId.value = null;
}

async function decide(item: DocumentConflict, keepFileMd5?: string) {
  if (savingId.value !== null) return;
  savingId.value = item.id;
  const { error } = await decideDocumentConflict(item.id, keepFileMd5 ? { keepFileMd5 } : { dismiss: true });
  savingId.value = null;
  if (error) return;
  window.$message?.success(keepFileMd5 ? '审核完成，另一篇文档已停止参与检索' : '已驳回疑似冲突');
  await loadConflicts();
}

async function undo(item: DocumentConflict) {
  if (savingId.value !== null) return;
  savingId.value = item.id;
  const { error } = await undoDocumentConflict(item.id);
  savingId.value = null;
  if (error) return;
  window.$message?.success('已撤销审核决定');
  await loadConflicts();
}

function similarityText(value: number) {
  return `${(value * 100).toFixed(1)}%`;
}

function formatTime(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN') : '—';
}

function decisionText(item: DocumentConflict) {
  if (item.status === 'DISMISSED') return '已驳回误报';
  if (item.status === 'KEEP_LEFT') return `保留 ${item.leftFileName}`;
  if (item.status === 'KEEP_RIGHT') return `保留 ${item.rightFileName}`;
  return '待审核';
}

onMounted(() => {
  loadConflicts();
  loadSettings();
});
</script>

<template>
  <div class="conflict-page">
    <header class="page-header">
      <div>
        <span class="page-eyebrow">知识库 · 内容治理</span>
        <h1>文档冲突审核</h1>
        <p>核对两篇文档对同一事实的说法，决定保留哪篇参与检索。</p>
      </div>
      <NButton secondary :loading="loading" @click="loadConflicts">刷新列表</NButton>
    </header>

    <section class="overview-grid" aria-label="审核概览与检测设置">
      <div class="review-overview">
        <div class="overview-mark">≠</div>
        <div class="overview-copy">
          <span class="panel-label">人工审核</span>
          <strong>{{ pendingCount === null ? '查看审核记录' : `${pendingCount} 条待审核` }}</strong>
          <span>相似内容只是线索。请先读双方原文，再决定是否冲突。</span>
        </div>
      </div>
      <div class="settings-card">
        <div class="settings-copy">
          <span class="panel-label">检测设置</span>
          <strong>LLM 二次确认</strong>
          <p>上传后的冲突扫描可先交给模型判断，再进入人工审核。开启会消耗上传者的 LLM 额度；只影响后续扫描。</p>
          <span v-if="settingsError" class="settings-error">设置读取失败，请重试。</span>
          <span v-else-if="settings" class="settings-status">
            当前：{{ settings.llmConfirmationEnabled ? '已开启' : '未开启' }}
            <template v-if="!settings.detectionEnabled">· 冲突检测已由服务配置关闭</template>
          </span>
          <span v-else class="settings-status">正在读取设置…</span>
        </div>
        <div class="settings-actions">
          <NButton v-if="settingsError" secondary @click="loadSettings">重试</NButton>
          <NPopconfirm v-else-if="settings" @positive-click="toggleLlmConfirmation">
            <template #trigger>
              <NButton
                :type="settings.llmConfirmationEnabled ? 'default' : 'primary'"
                :secondary="settings.llmConfirmationEnabled"
                :loading="settingsSaving"
                :disabled="!settings.detectionEnabled"
              >
                {{ settings.llmConfirmationEnabled ? '关闭 LLM 确认' : '开启 LLM 确认' }}
              </NButton>
            </template>
            {{
              settings.llmConfirmationEnabled
                ? '关闭后，新文档将只用规则检测并等待人工审核。'
                : '开启后，新文档的疑似冲突会调用 LLM 二次确认并消耗额度。'
            }}
          </NPopconfirm>
          <NButton v-else :loading="settingsLoading" disabled>读取中</NButton>
        </div>
      </div>
    </section>

    <section class="records-panel" aria-label="冲突记录">
      <div class="records-header">
        <div>
          <h2>冲突记录</h2>
          <p>选择一条记录，查看原文并处理。</p>
        </div>
        <nav class="status-switch" aria-label="冲突记录状态">
          <button type="button" :class="{ active: activeStatus === 'PENDING' }" @click="changeStatus('PENDING')">
            待审核
          </button>
          <button type="button" :class="{ active: activeStatus === 'REVIEWED' }" @click="changeStatus('REVIEWED')">
            已处理
          </button>
        </nav>
      </div>

      <div v-if="loadFailed" class="load-error" role="alert">
        列表加载失败。
        <NButton text type="primary" @click="loadConflicts">重新加载</NButton>
      </div>

      <NSpin :show="loading">
        <div v-if="!loading && !loadFailed && conflicts.length === 0" class="empty-state">
          <div class="empty-icon">✓</div>
          <strong>{{ activeStatus === 'PENDING' ? '暂无待审核冲突' : '暂无已处理记录' }}</strong>
          <p>
            {{
              activeStatus === 'PENDING'
                ? '新文档扫描到疑似冲突后，会在这里显示。'
                : '完成审核后，可以在这里查看并撤销决定。'
            }}
          </p>
        </div>

        <div v-else class="conflict-list">
          <article v-for="item in conflicts" :key="item.id" class="conflict-card">
            <header class="card-header">
              <div class="card-title-group">
                <span class="record-number">#{{ item.id }}</span>
                <h2>{{ item.conflictKey }}</h2>
                <NTag v-if="item.status === 'PENDING'" type="warning" size="small" round>待判断</NTag>
                <NTag v-else size="small" round>{{ decisionText(item) }}</NTag>
              </div>
              <div class="card-meta">
                <span>相似度 {{ similarityText(item.similarity) }}</span>
                <span>{{ formatTime(item.createdAt) }}</span>
              </div>
            </header>

            <div class="document-pair">
              <section class="document-side" aria-label="第一篇文档">
                <div class="document-kicker">文档 A · 分块 {{ item.leftChunkId }}</div>
                <strong :title="item.leftFileName">{{ item.leftFileName }}</strong>
                <span class="document-md5" :title="item.leftFileMd5">{{ item.leftFileMd5 }}</span>
                <div class="fact-value">{{ item.leftValue }}</div>
              </section>
              <div class="pair-divider" aria-hidden="true">VS</div>
              <section class="document-side" aria-label="第二篇文档">
                <div class="document-kicker">文档 B · 分块 {{ item.rightChunkId }}</div>
                <strong :title="item.rightFileName">{{ item.rightFileName }}</strong>
                <span class="document-md5" :title="item.rightFileMd5">{{ item.rightFileMd5 }}</span>
                <div class="fact-value">{{ item.rightValue }}</div>
              </section>
            </div>

            <div class="context-toggle">
              <NButton text type="primary" :loading="contextLoadingId === item.id" @click="toggleContext(item)">
                {{ expandedId === item.id ? '收起原文' : '查看双方原文与相邻分块' }}
              </NButton>
            </div>
            <div v-if="expandedId === item.id" class="context-panel">
              <div v-if="contextErrors[item.id]" class="context-error">
                原文加载失败。
                <NButton text type="primary" @click="loadContext(item.id)">重试</NButton>
              </div>
              <div v-else-if="contexts[item.id]" class="context-grid">
                <div>
                  <span>文档 A 原文</span>
                  <pre>{{ contexts[item.id].leftText || '暂无原文' }}</pre>
                </div>
                <div>
                  <span>文档 B 原文</span>
                  <pre>{{ contexts[item.id].rightText || '暂无原文' }}</pre>
                </div>
              </div>
            </div>

            <footer class="card-footer">
              <div v-if="item.status !== 'PENDING'" class="review-meta">
                {{ decisionText(item) }} · {{ formatTime(item.reviewedAt) }}
              </div>
              <div v-else class="review-meta">选择保留后，另一篇文档将停止参与检索。</div>
              <div v-if="item.status === 'PENDING'" class="action-group">
                <NPopconfirm @positive-click="decide(item, item.leftFileMd5)">
                  <template #trigger>
                    <NButton size="small" type="primary" :disabled="savingId !== null">保留文档 A</NButton>
                  </template>
                  保留「{{ item.leftFileName }}」，停止检索「{{ item.rightFileName }}」？
                </NPopconfirm>
                <NPopconfirm @positive-click="decide(item, item.rightFileMd5)">
                  <template #trigger>
                    <NButton size="small" secondary type="primary" :disabled="savingId !== null">保留文档 B</NButton>
                  </template>
                  保留「{{ item.rightFileName }}」，停止检索「{{ item.leftFileName }}」？
                </NPopconfirm>
                <NPopconfirm @positive-click="decide(item)">
                  <template #trigger>
                    <NButton size="small" quaternary :disabled="savingId !== null">驳回误报</NButton>
                  </template>
                  确认两篇文档没有冲突，继续允许检索？
                </NPopconfirm>
              </div>
              <NPopconfirm v-else @positive-click="undo(item)">
                <template #trigger>
                  <NButton size="small" secondary :disabled="savingId !== null">撤销决定</NButton>
                </template>
                撤销后该记录会回到待审核；此前停用的文档可能重新参与检索。确定继续？
              </NPopconfirm>
            </footer>
          </article>
        </div>
      </NSpin>
    </section>
  </div>
</template>

<style scoped src="./conflict-page.css"></style>
