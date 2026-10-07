<script setup lang="ts">
import { NScrollbar } from 'naive-ui';
import { VueMarkdownItProvider } from '@/vendor/vue-markdown-shiki';
import ChatMessage from './chat-message.vue';

defineOptions({
  name: 'ChatList'
});

const chatStore = useChatStore();
const { list, sessionId, conversationId } = storeToRefs(chatStore);

const loading = ref(false);
const scrollbarRef = ref<InstanceType<typeof NScrollbar>>();

watch(() => [...list.value], scrollToBottom);

function scrollToBottom() {
  setTimeout(() => {
    scrollbarRef.value?.scrollBy({
      top: 999999999999999,
      behavior: 'auto'
    });
  }, 100);
}

const range = ref<[number, number] | null>(null);

function getRetrievalQueryFallback(index: number) {
  for (let i = index - 1; i >= 0; i -= 1) {
    const candidate = list.value[i];
    if (candidate?.role === 'user') {
      return candidate.content || '';
    }
  }
  return '';
}

const params = computed(() => {
  const p: Record<string, string> = {};
  if (range.value) {
    p.start_date = dayjs(range.value[0]).format('YYYY-MM-DD');
    p.end_date = dayjs(range.value[1]).format('YYYY-MM-DD');
  }
  if (conversationId.value) {
    p.conversationId = conversationId.value;
  }
  return p;
});

watchEffect(() => {
  getList();
});

async function getList() {
  loading.value = true;
  const { error, data } = await request<Api.Chat.Message[]>({
    url: 'users/conversation',
    params: params.value
  });
  if (!error) {
    list.value = data;
  }
  loading.value = false;
}

onMounted(() => {
  chatStore.scrollToBottom = scrollToBottom;
});

const showEmpty = computed(() => !loading.value && list.value.length === 0);

const suggestions = [
  { title: '快速了解', text: '帮我概括知识库中的主要内容', icon: '01', detail: '把长篇资料，变成清晰重点' },
  { title: '查找答案', text: '请查找与我的问题相关的制度和流程', icon: '02', detail: '从团队文档中寻找解答' },
  { title: '梳理行动', text: '根据相关文档，整理一份工作步骤清单', icon: '03', detail: '让知识成为下一步行动' }
];

function useSuggestion(text: string) {
  chatStore.input.message = text;
  document.querySelector<HTMLTextAreaElement>('.chat-input-shell textarea')?.focus();
}
</script>

<template>
  <Suspense>
    <div class="flex h-0 flex-1 flex-col">
      <!-- Date filter in header -->
      <Teleport defer to="#header-extra">
        <div v-if="!showEmpty" class="flex items-center px-4">
          <NForm :model="params" label-placement="left" :show-feedback="false" inline>
            <NFormItem label="时间" size="small">
              <NDatePicker v-model:value="range" type="daterange" clearable size="small" />
            </NFormItem>
          </NForm>
        </div>
      </Teleport>

      <!-- Empty state -->
      <div v-if="showEmpty" class="knowledge-welcome">
        <div class="welcome-kicker"><span /> YOUR KNOWLEDGE, CONNECTED</div>
        <SystemLogo class="welcome-logo" />
        <h1>让每一个答案，<span>都有依据。</span></h1>
        <p>从一个问题开始，探索属于你的知识库。</p>
        <div class="suggestion-grid">
          <button
            v-for="item in suggestions"
            :key="item.icon"
            class="suggestion-card"
            @click="useSuggestion(item.text)"
          >
            <span class="suggestion-number">{{ item.icon }} <span>↗</span></span>
            <strong>{{ item.title }}</strong>
            <small>{{ item.detail }}</small>
          </button>
        </div>
        <RouterLink to="/knowledge-base" class="welcome-library"
          >还没有资料？前往知识库添加文档 <span>→</span></RouterLink
        >
      </div>

      <!-- Message list -->
      <NScrollbar v-else ref="scrollbarRef" class="flex-1">
        <NSpin :show="loading">
          <div class="mx-auto w-full max-w-[960px] px-4">
            <VueMarkdownItProvider>
              <ChatMessage
                v-for="(item, index) in list"
                :key="index"
                :msg="item"
                :session-id="sessionId"
                :retrieval-query-fallback="getRetrievalQueryFallback(index)"
              />
            </VueMarkdownItProvider>
          </div>
        </NSpin>
      </NScrollbar>
    </div>
  </Suspense>
</template>

<style scoped>
.knowledge-welcome {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 30px 24px;
}
.welcome-kicker {
  display: flex;
  gap: 8px;
  align-items: center;
  color: #6d8077;
  font-size: 10px;
  letter-spacing: 2px;
  margin-bottom: 24px;
}
.welcome-kicker > span {
  height: 5px;
  width: 5px;
  background: #167565;
  border-radius: 50%;
}
.welcome-logo {
  animation: welcome-arrive 0.65s ease both;
  font-size: 52px;
  margin-bottom: 20px;
}
.knowledge-welcome h1 {
  color: #20382d;
  font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif;
  font-weight: 500;
  font-size: clamp(24px, 2.6vw, 38px);
  text-align: center;
  line-height: 1.5;
}
.knowledge-welcome h1 span {
  color: #167565;
}
.knowledge-welcome > p {
  color: #526559;
  margin: 12px 0 32px;
  font-size: 16px;
}
.suggestion-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  width: 100%;
  max-width: 690px;
}
.suggestion-card {
  border: 1px solid #e4e9e3;
  border-radius: 12px;
  background: #fafbf8;
  padding: 18px;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.2s,
    transform 0.2s;
}
.suggestion-card:hover {
  border-color: #7ca996;
  transform: translateY(-3px);
}
.suggestion-card:focus-visible {
  outline: 2px solid #167565;
  outline-offset: 3px;
}
.suggestion-number {
  display: flex;
  justify-content: space-between;
  color: #82988c;
  font-size: 14px;
  margin-bottom: 22px;
}
.suggestion-card strong {
  display: block;
  font-size: 17px;
  font-weight: 600;
}
.suggestion-card small {
  display: block;
  font-size: 14px;
  margin-top: 8px;
  color: #59695f;
  line-height: 1.6;
}
.welcome-library {
  margin-top: 24px;
  font-size: 15px;
  color: #526559;
}
.welcome-library:hover {
  color: #167565;
}
.dark .suggestion-card {
  background: #242c27;
  border-color: #3b473f;
}
.dark .knowledge-welcome h1 {
  color: #f0f5ed;
}
.dark .knowledge-welcome h1 span {
  color: #74bda6;
}
.dark .knowledge-welcome > p,
.dark .suggestion-card small,
.dark .welcome-library,
.dark .welcome-kicker {
  color: #a4b5aa;
}
@media (max-width: 640px) {
  .knowledge-welcome {
    justify-content: flex-start;
    padding: 24px 14px;
  }
  .welcome-kicker {
    font-size: 8px;
  }
  .suggestion-grid {
    grid-template-columns: 1fr;
    gap: 8px;
  }
  .suggestion-card {
    padding: 12px 16px;
  }
  .suggestion-number {
    float: right;
    gap: 16px;
    margin: 0;
  }
  .knowledge-welcome > p {
    margin-bottom: 20px;
  }
}
@keyframes welcome-arrive {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@media (prefers-reduced-motion: reduce) {
  .welcome-logo {
    animation: none;
  }
  .suggestion-card {
    transition: none;
  }
}
</style>
