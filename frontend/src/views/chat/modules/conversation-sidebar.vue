<script setup lang="ts">
defineOptions({
  name: 'ConversationSidebar'
});

const collapsed = defineModel<boolean>('collapsed', { default: false });

const chatStore = useChatStore();
const { conversationId, sessionsLoading, filteredSessions, activeTab } = storeToRefs(chatStore);

onMounted(() => {
  chatStore.loadSessions();
});

function handleCollapse() {
  collapsed.value = true;
}

function handleNewChat() {
  chatStore.createNewSession();
}

function handleSelect(cid: string) {
  chatStore.switchSession(cid);
}

function handleArchive(cid: string) {
  chatStore.archiveSession(cid);
}

function handleUnarchive(cid: string) {
  chatStore.unarchiveSession(cid);
}

function setActiveTab(tab: 'active' | 'archived') {
  activeTab.value = tab;
}

function formatDate(dateStr?: string) {
  if (!dateStr) return '';
  const date = dayjs(dateStr);
  const now = dayjs();
  if (date.isSame(now, 'day')) {
    return date.format('HH:mm');
  }
  if (date.isSame(now, 'week')) {
    return date.format('ddd');
  }
  if (date.isSame(now, 'year')) {
    return date.format('MM-DD');
  }
  return date.format('YYYY-MM-DD');
}
</script>

<template>
  <div
    class="conversation-panel relative h-full shrink-0 flex flex-col overflow-hidden transition-[width] duration-200 ease-out"
    :data-collapsed="collapsed"
    :class="collapsed ? 'w-0 min-w-0 border-r-0' : 'w-[260px] min-w-[260px]'"
  >
    <div class="flex w-[260px] flex-1 flex-col overflow-hidden" :class="{ 'pointer-events-none invisible': collapsed }">
      <div class="flex items-center justify-between px-4 pt-4 pb-2">
        <span class="text-15px font-600">对话列表</span>
        <div class="flex items-center gap-1">
          <NButton type="primary" size="small" secondary @click="handleNewChat">
            <template #icon>
              <icon-material-symbols:add-rounded />
            </template>
            新对话
          </NButton>
          <NButton text size="tiny" aria-label="收起对话列表" @click="handleCollapse">
            <template #icon>
              <icon-material-symbols:left-panel-close-outline-rounded />
            </template>
          </NButton>
        </div>
      </div>

      <!-- Tabs -->
      <div class="session-tabs mx-4 mb-2 flex rounded-lg p-0.5">
        <button
          type="button"
          class="session-tab flex-1 cursor-pointer rounded-md px-3 py-1.5 text-center text-14px font-500 transition-all"
          :class="{ 'is-active': activeTab === 'active' }"
          @click="setActiveTab('active')"
        >
          活跃
        </button>
        <button
          type="button"
          class="session-tab flex-1 cursor-pointer rounded-md px-3 py-1.5 text-center text-14px font-500 transition-all"
          :class="{ 'is-active': activeTab === 'archived' }"
          @click="setActiveTab('archived')"
        >
          已归档
        </button>
      </div>

      <!-- List -->
      <div class="flex-1 overflow-y-auto px-2">
        <NSpin :show="sessionsLoading" class="h-full">
          <TransitionGroup name="session-list" tag="div">
            <div
              v-if="filteredSessions.length === 0 && !sessionsLoading"
              class="flex flex-col items-center justify-center gap-3 py-16"
            >
              <icon-material-symbols:chat-outline-rounded class="text-36px color-#ccc dark:color-#444" />
              <span class="empty-session-label text-15px">{{ activeTab === 'active' ? '暂无对话记录' : '暂无归档对话' }}</span>
            </div>

            <div
              v-for="session in filteredSessions"
              :key="session.conversationId"
              class="session-row group mx-1 mb-0.5 flex cursor-pointer items-center gap-2.5 rounded-lg px-3 py-2.5 transition-all"
              :class="{ 'is-current': session.conversationId === conversationId }"
              @click="handleSelect(session.conversationId)"
            >
              <div
                class="session-icon shrink-0 flex h-7 w-7 items-center justify-center rounded-lg text-15px"
              >
                <icon-material-symbols:chat-outline-rounded />
              </div>
              <div class="min-w-0 flex-1">
                <div class="session-title truncate text-15px font-500">
                  {{ session.title }}
                </div>
                <div class="session-date text-13px">{{ formatDate(session.updatedAt) }}</div>
              </div>

              <!-- Action button -->
              <NPopconfirm v-if="activeTab === 'active'" @positive-click="handleArchive(session.conversationId)">
                <template #trigger>
                  <NButton
                    class="shrink-0 transition-opacity"
                    :class="session.conversationId === conversationId ? '' : 'opacity-0 group-hover:opacity-100'"
                    text
                    size="tiny"
                    @click.stop
                  >
                    <template #icon>
                      <icon-material-symbols:archive-outline-rounded class="text-15px color-#999 hover:color-#666" />
                    </template>
                  </NButton>
                </template>
                归档后可在「已归档」中找回
              </NPopconfirm>
              <NButton
                v-else
                class="shrink-0 opacity-0 transition-opacity group-hover:opacity-100"
                text
                size="tiny"
                @click.stop="handleUnarchive(session.conversationId)"
              >
                <template #icon>
                  <icon-material-symbols:unarchive-outline-rounded class="text-15px color-#999 hover:color-#666" />
                </template>
              </NButton>
            </div>
          </TransitionGroup>
        </NSpin>
      </div>
    </div>
  </div>
</template>

<style scoped>
.conversation-panel {
  --conversation-text: rgb(var(--base-text-color));
  --conversation-muted: rgb(var(--text-color-2));
  --conversation-border: rgb(var(--border-color));
  --conversation-surface: rgb(var(--container-bg-color));
  --conversation-subtle: rgb(var(--layout-bg-color));
  background: var(--conversation-surface);
  border-right: 1px solid var(--conversation-border);
  color: var(--conversation-text);
}
.session-tabs { background: var(--conversation-subtle); }
.session-tab { border: 0; background: transparent; color: var(--conversation-muted); }
.session-tab.is-active {
  background: var(--conversation-surface);
  color: var(--conversation-text);
  box-shadow: 0 1px 4px rgb(var(--base-text-color) / 0.1);
}
.session-row:hover { background: rgb(var(--primary-color) / 0.06); }
.session-row.is-current { background: rgb(var(--primary-color) / 0.12); }
.session-icon { background: rgb(var(--primary-color) / 0.1); color: var(--conversation-muted); }
.session-row.is-current .session-icon { background: rgb(var(--primary-color) / 0.18); color: rgb(var(--primary-color)); }
.session-title { color: var(--conversation-text); }
.session-row.is-current .session-title { color: rgb(var(--primary-color)); font-weight: 600; }
.session-date, .empty-session-label { color: var(--conversation-muted); }
.conversation-panel :deep(.n-button--primary-type) { background: rgb(var(--primary-color) / 0.12); color: rgb(var(--primary-color)); }
.conversation-panel :deep(.n-button--primary-type:hover) { background: rgb(var(--primary-color) / 0.2); color: rgb(var(--primary-color)); }
.session-row:focus-visible, .session-tab:focus-visible {
  outline: 2px solid rgb(var(--primary-color));
  outline-offset: -2px;
}
@media (max-width: 900px) {
  .conversation-panel[data-collapsed='false'] {
    position: absolute;
    inset: 0 auto 0 0;
    z-index: 30;
    box-shadow: 12px 0 32px rgb(var(--base-text-color) / 0.12);
  }
}

.session-list-enter-active,
.session-list-leave-active {
  transition: all 0.2s ease;
}
.session-list-enter-from,
.session-list-leave-to {
  opacity: 0;
  transform: translateX(-8px);
}
</style>
