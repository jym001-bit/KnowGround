<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';
import ChatList from './modules/chat-list.vue';
import InputBox from './modules/input-box.vue';
import ConversationSidebar from './modules/conversation-sidebar.vue';
import ReferencePreviewPage from './modules/reference-preview-page.vue';

const route = useRoute();
const showReferencePreview = computed(() => route.query.preview === 'reference');
const sidebarCollapsed = ref(window.innerWidth < 900);
</script>

<template>
  <div v-if="showReferencePreview" class="h-full">
    <ReferencePreviewPage />
  </div>
  <div v-else class="knowledge-workspace h-full bg-layout p-3">
    <div
      class="relative h-full flex overflow-hidden rounded-2xl bg-container shadow-[0_1px_2px_rgba(82,72,54,0.04),0_8px_28px_rgba(82,72,54,0.06)] dark:bg-[#1c1c1c] dark:shadow-[0_1px_2px_rgba(0,0,0,0.5),0_8px_28px_rgba(0,0,0,0.3)]"
    >
      <ConversationSidebar v-model:collapsed="sidebarCollapsed" />
      <div class="relative min-w-0 flex flex-col flex-1">
        <header class="workspace-heading" :class="{ 'with-toggle': sidebarCollapsed }">
          <div><strong>知识问答</strong><span>从知识中发现答案</span></div>
          <RouterLink to="/knowledge-base">知识库 ↗</RouterLink>
        </header>
        <button
          v-show="sidebarCollapsed"
          class="absolute left-3 top-3 z-20 h-9 w-9 inline-flex items-center justify-center rounded-xl bg-white text-#666 shadow-[0_1px_2px_rgba(15,23,42,0.06),0_4px_12px_rgba(15,23,42,0.08)] ring-1 ring-#0f172a14 transition-all duration-150 active:scale-95 hover:scale-105 dark:bg-#262626 dark:text-#bbb hover:text-[rgb(var(--primary-color))] dark:shadow-[0_1px_2px_rgba(0,0,0,0.4),0_4px_12px_rgba(0,0,0,0.4)] dark:ring-#ffffff14"
          aria-label="展开对话列表"
          @click="sidebarCollapsed = false"
        >
          <icon-material-symbols:left-panel-open-outline-rounded class="text-18px" />
        </button>
        <ChatList />
        <InputBox />
      </div>
    </div>
  </div>
</template>

<style scoped>
.workspace-heading {
  min-height: 62px;
  padding: 16px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e7ebe51f;
  gap: 12px;
}
.workspace-heading.with-toggle {
  padding-left: 62px;
}
.workspace-heading strong {
  font-size: 14px;
  font-weight: 600;
}
.workspace-heading div > span {
  margin-left: 14px;
  font-size: 11px;
  color: #869087;
}
.workspace-heading a {
  font-size: 12px;
  color: #8c806e;
}
@media (max-width: 640px) {
  .knowledge-workspace {
    padding: 4px;
  }
  .workspace-heading div > span {
    display: none;
  }
}
</style>
