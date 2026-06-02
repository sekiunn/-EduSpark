# Open WebUI 页面结构与样式分析

> 本文档专注于 AI 对话界面的 UI 布局和样式，便于仿制

---

## 一、整体页面结构

```
┌──────────────────────────────────────────────────────────────────┐
│                         chat-container                            │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │                      PaneGroup (水平分割)                    │  │
│  │  ┌─────────────────────────────┐ ┌──────────────────────┐  │  │
│  │  │        Pane (主区域)        │ │   ChatControls      │  │  │
│  │  │  ┌───────────────────────┐ │ │   (可折叠侧边栏)    │  │  │
│  │  │  │       Navbar          │ │ │                    │  │  │
│  │  │  ├───────────────────────┤ │ │  - 系统提示词      │  │  │
│  │  │  │                       │ │ │  - 模型参数        │  │  │
│  │  │  │   chat-pane           │ │ │  - 工具配置        │  │  │
│  │  │  │  ┌─────────────────┐  │ │ │  - 聊天导出        │  │  │
│  │  │  │  │ messages-container│ │ │                    │  │  │
│  │  │  │  │  (消息滚动区域)   │  │ │                    │  │  │
│  │  │  │  │                 │  │ │ │                    │  │  │
│  │  │  │  │   Messages       │  │ │ │                    │  │  │
│  │  │  │  │                 │  │ │ │                    │  │  │
│  │  │  │  └─────────────────┘  │ │ │                    │  │  │
│  │  │  │                       │ │ │                    │  │  │
│  │  │  │  ┌─────────────────┐  │ │ │                    │  │  │
│  │  │  │  │  MessageInput   │  │ │ │                    │  │  │
│  │  │  │  │  (输入区域)      │  │ │ │                    │  │  │
│  │  │  │  └─────────────────┘  │ │ │                    │  │  │
│  │  │  │                       │ │ │                    │  │  │
│  │  │  └───────────────────────┘ │ └──────────────────────┘  │  │
│  │  └─────────────────────────────┘                           │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

---

## 二、主容器样式 (Chat.svelte)

### 外层容器
```html
<div
  id="chat-container"
  class="
    h-screen                     <!-- 全屏高度 -->
    max-h-[100dvh]              <!-- 最大100%动态视口高度 -->
    transition-width            <!-- 宽度过渡动画 -->
    duration-200                <!-- 200ms过渡时长 -->
    ease-in-out                 <!-- 缓动曲线 -->
    w-full                      <!-- 全宽 -->
    max-w-full                  <!-- 最大全宽 -->
    flex                        <!-- Flex布局 -->
    flex-col                    <!-- 垂直排列 -->
  "
>
```

### 消息区域容器
```html
<div
  id="chat-pane"
  class="
    flex                         <!-- Flex布局 -->
    flex-col                     <!-- 垂直排列 -->
    flex-auto                    <!-- 自动占据剩余空间 -->
    z-10                         <!-- z-index层级 -->
    w-full                       <!-- 全宽 -->
    @container                   <!-- 容器查询支持 -->
    overflow-auto                <!-- 内容溢出滚动 -->
  "
>
```

### 消息滚动区域
```html
<div
  id="messages-container"
  class="
    pb-2.5                       <!-- 底部内边距 -->
    flex                         <!-- Flex布局 -->
    flex-col                     <!-- 垂直排列 -->
    justify-between              <!-- 两端对齐 -->
    w-full                       <!-- 全宽 -->
    flex-auto                    <!-- 自动占据剩余空间 -->
    overflow-auto                <!-- 溢出滚动 -->
    h-0                          <!-- 初始高度0，由内容撑开 -->
    max-w-full                   <!-- 最大全宽 -->
    z-10                         <!-- z-index层级 -->
    scrollbar-hidden             <!-- 隐藏滚动条(自定义样式) -->
  "
>
```

---

## 三、导航栏样式 (Navbar)

```html
<div
  class="
    flex                         <!-- Flex布局 -->
    items-center                 <!-- 垂直居中 -->
    justify-between              <!-- 水平两端对齐 -->
    px-4                         <!-- 左右内边距16px -->
    py-2                         <!-- 上下内边距8px -->
    border-b                     <!-- 底部边框 -->
    border-gray-100              <!-- 边框颜色 -->
    dark:border-gray-800         <!-- 暗色模式边框 -->
    bg-white/50                  <!-- 半透明白色背景 -->
    dark:bg-gray-900/50          <!-- 暗色模式背景 -->
    backdrop-blur                <!-- 背景模糊 -->
  "
>
```

---

## 四、输入框样式 (MessageInput)

### 输入框外层容器
```html
<div
  id="message-input-container"
  class="
    flex-1                       <!-- 自动占据剩余空间 -->
    flex                         <!-- Flex布局 -->
    flex-col                     <!-- 垂直排列 -->
    relative                     <!-- 相对定位 -->
    w-full                       <!-- 全宽 -->
    shadow-lg                    <!-- 大阴影 -->
    rounded-3xl                  <!-- 大圆角24px -->
    border                       <!-- 边框 -->
    <!-- 临时聊天模式：虚线边框 -->
    border-dashed
    border-gray-100
    dark:border-gray-800
    hover:border-gray-200        <!-- 悬停边框颜色 -->
    focus-within:border-gray-200 <!-- 聚焦边框颜色 -->
    <!-- 普通模式：实线边框 -->
    border-gray-100/30
    dark:border-gray-850/30
    hover:border-gray-200
    focus-within:border-gray-100
    hover:dark:border-gray-800
    focus-within:dark:border-gray-800
    transition                   <!-- 过渡效果 -->
    px-1                         <!-- 左右内边距4px -->
    bg-white/5                   <!-- 半透明背景 -->
    dark:bg-gray-500/5           <!-- 暗色模式半透明背景 -->
    backdrop-blur-sm              <!-- 背景模糊 -->
    dark:text-gray-100            <!-- 暗色模式文字颜色 -->
  "
>
```

### 输入框内部（RichTextInput）
```html
<div
  class="
    scrollbar-hidden              <!-- 隐藏滚动条 -->
    rtl:text-right               <!-- RTL模式右对齐 -->
    ltr:text-left                <!-- LTR模式左对齐 -->
    bg-transparent               <!-- 透明背景 -->
    dark:text-gray-100           <!-- 暗色模式文字 -->
    outline-hidden               <!-- 移除轮廓 -->
    w-full                       <!-- 全宽 -->
    pb-1                         <!-- 底部内边距4px -->
    px-1                         <!-- 左右内边距4px -->
    resize-none                  <!-- 禁用调整大小 -->
    h-fit                        <!-- 高度自适应 -->
    max-h-96                     <!-- 最大高度384px -->
    overflow-auto                <!-- 溢出滚动 -->
  "
>
```

### 工具栏按钮区域
```html
<div
  class="
    flex                         <!-- Flex布局 -->
    justify-between              <!-- 两端对齐 -->
    mt-0.5                       <!-- 上外边距2px -->
    mb-2.5                       <!-- 下外边距10px -->
    mx-0.5                       <!-- 左右外边距2px -->
    max-w-full                   <!-- 最大全宽 -->
  "
>
```

### 发送按钮
```html
<button
  class="
    bg-black                     <!-- 黑色背景 -->
    text-white                   <!-- 白色文字 -->
    hover:bg-gray-900             <!-- 悬停深灰背景 -->
    dark:bg-white                <!-- 暗色模式白色背景 -->
    dark:text-black               <!-- 暗色模式黑色文字 -->
    dark:hover:bg-gray-100        <!-- 暗色模式悬停灰白 -->
    transition                    <!-- 过渡效果 -->
    rounded-full                  <!-- 全圆角 -->
    p-1.5                        <!-- 内边距6px -->
    self-center                  <!-- 垂直居中 -->
  "
>
```

---

## 五、消息样式

### 用户消息
```html
<div
  class="
    flex                         <!-- Flex布局 -->
    justify-end                  <!-- 右对齐(用户消息) -->
    mb-4                         <!-- 下外边距16px -->
  "
>
  <div
    class="
      inline-block                <!-- 行内块 -->
      p-3                        <!-- 内边距12px -->
      rounded-2xl                <!-- 圆角16px -->
      rounded-br-sm              <!-- 右下小圆角 -->
      bg-blue-500                <!-- 蓝色背景 -->
      text-white                 <!-- 白色文字 -->
      max-w-[80%]               <!-- 最大宽度80% -->
    "
  >
```

### AI 助手消息
```html
<div
  class="
    flex                         <!-- Flex布局 -->
    justify-start                <!-- 左对齐(AI消息) -->
    mb-4                         <!-- 下外边距16px -->
  "
>
  <div
    class="
      inline-block                <!-- 行内块 -->
      p-3                        <!-- 内边距12px -->
      rounded-2xl                <!-- 圆角16px -->
      rounded-bl-sm              <!-- 左下小圆角 -->
      bg-gray-100                <!-- 灰色背景 -->
      dark:bg-gray-800           <!-- 暗色模式背景 -->
      dark:text-gray-100         <!-- 暗色模式文字 -->
      text-gray-900              <!-- 深色文字 -->
      max-w-[80%]               <!-- 最大宽度80% -->
    "
  >
```

---

## 六、完整页面 HTML 结构

```html
<!-- ==================== 主容器 ==================== -->
<div
  id="chat-container"
  class="h-screen max-h-[100dvh] w-full max-w-full flex flex-col"
>
  <!-- ==================== 导航栏 ==================== -->
  <Navbar class="navbar-styles">
    <!-- Logo, 标题, 分享/设置按钮 -->
  </Navbar>

  <!-- ==================== 主内容区 ==================== -->
  <div
    id="chat-pane"
    class="flex flex-col flex-auto z-10 w-full @container overflow-auto"
  >
    <!-- ============== 消息滚动区域 ============== -->
    <div
      id="messages-container"
      class="pb-2.5 flex flex-col justify-between w-full flex-auto overflow-auto h-0 max-w-full z-10 scrollbar-hidden"
    >
      <!-- 消息列表 -->
      <Messages>
        <!-- 单条消息循环 -->
      </Messages>
    </div>

    <!-- ============== 输入区域 ============== -->
    <div class="pb-2 z-10">
      <form class="message-input-form">
        <!-- 输入框容器 -->
        <div class="input-container rounded-3xl border...">
          <!-- 已上传文件预览 -->
          <div class="file-previews flex flex-wrap gap-2...">
            <!-- 图片/文件预览项 -->
          </div>

          <!-- 文本输入区 -->
          <div class="text-input-area">
            <textarea
              id="chat-input"
              class="scrollbar-hidden resize-none h-fit max-h-96..."
              placeholder="发送消息..."
            />
          </div>

          <!-- 工具栏 -->
          <div class="toolbar flex justify-between...">
            <!-- 左侧工具按钮 -->
            <div class="left-tools flex items-center gap-2">
              <button class="icon-button...">+</button>           <!-- 添加文件 -->
              <button class="icon-button...">@</button>           <!-- 提及 -->
              <button class="icon-button...">#</button>           <!-- 知识库 -->
            </div>

            <!-- 右侧发送按钮 -->
            <div class="right-actions">
              <button class="send-button bg-black text-white...">
                发送
              </button>
            </div>
          </div>
        </div>
      </form>
    </div>
  </div>

  <!-- ==================== 侧边控制面板(可选) ==================== -->
  <ChatControls class="control-pane...">
    <!-- 系统提示词、参数配置、工具选择 -->
  </ChatControls>
</div>
```

---

## 七、关键样式类汇总

### 布局类
| 类名 | 作用 |
|------|------|
| `flex` | Flex布局 |
| `flex-col` | 垂直排列 |
| `flex-auto` | 自动扩展 |
| `flex-1` | 占据剩余空间 |
| `items-center` | 垂直居中 |
| `justify-between` | 两端对齐 |
| `justify-end` | 右对齐 |
| `justify-start` | 左对齐 |

### 尺寸类
| 类名 | 作用 |
|------|------|
| `h-screen` | 全屏高度 |
| `w-full` | 全宽 |
| `max-w-[80%]` | 最大宽度80% |
| `max-h-96` | 最大高度384px |
| `p-4` | 内边距16px |
| `m-2` | 外边距8px |
| `gap-2` | 间距8px |

### 圆角类
| 类名 | 作用 |
|------|------|
| `rounded-lg` | 大圆角8px |
| `rounded-xl` | 更大圆角12px |
| `rounded-2xl` | 圆角16px |
| `rounded-3xl` | 圆角24px |
| `rounded-full` | 全圆角(圆形) |

### 颜色类
| 类名 | 作用 |
|------|------|
| `bg-white` | 白色背景 |
| `bg-gray-100` | 浅灰背景 |
| `bg-gray-900` | 深灰背景 |
| `bg-blue-500` | 蓝色背景 |
| `text-gray-700` | 灰色文字 |
| `dark:bg-gray-900` | 暗色模式背景 |
| `dark:text-gray-100` | 暗色模式文字 |

### 交互类
| 类名 | 作用 |
|------|------|
| `hover:bg-gray-100` | 悬停背景 |
| `focus:outline-none` | 聚焦轮廓隐藏 |
| `focus-within:border-blue-500` | 子元素聚焦时边框 |
| `transition` | 过渡动画 |
| `duration-200` | 过渡时长200ms |

### 阴影类
| 类名 | 作用 |
|------|------|
| `shadow-sm` | 小阴影 |
| `shadow-md` | 中阴影 |
| `shadow-lg` | 大阴影 |
| `shadow-none` | 无阴影 |

### 溢出类
| 类名 | 作用 |
|------|------|
| `overflow-auto` | 溢出滚动 |
| `overflow-hidden` | 溢出隐藏 |
| `truncate` | 文本截断 |
| `line-clamp-1` | 限制1行 |

---

## 八、完整可运行示例

### HTML + Tailwind CSS

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <script src="https://cdn.tailwindcss.com"></script>
  <style>
    /* 自定义滚动条隐藏 */
    .scrollbar-hidden::-webkit-scrollbar {
      height: 0;
      width: 0;
    }
  </style>
</head>
<body class="bg-gray-50 dark:bg-gray-900">
  <div id="app" class="h-screen max-h-[100dvh] flex flex-col">
    <!-- 导航栏 -->
    <nav class="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/80 backdrop-blur-md sticky top-0 z-50">
      <div class="flex items-center gap-3">
        <div class="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center text-white font-bold">AI</div>
        <span class="font-semibold text-gray-900 dark:text-white">智能对话</span>
      </div>
      <div class="flex items-center gap-2">
        <button class="p-2 rounded-full hover:bg-gray-100 dark:hover:bg-gray-800 transition">
          <svg class="w-5 h-5 text-gray-600 dark:text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z"/>
          </svg>
        </button>
      </div>
    </nav>

    <!-- 消息区域 -->
    <div id="messages-container" class="flex-1 overflow-y-auto p-4 space-y-4 scrollbar-hidden">
      <!-- 用户消息 -->
      <div class="flex justify-end">
        <div class="max-w-[80%] p-3 rounded-2xl rounded-br-sm bg-blue-500 text-white">
          你好，请介绍一下你自己
        </div>
      </div>

      <!-- AI 消息 -->
      <div class="flex justify-start">
        <div class="max-w-[80%] p-3 rounded-2xl rounded-bl-sm bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-gray-100">
          你好！我是 AI 助手。我可以帮助你完成各种任务，包括回答问题、提供建议、编写代码等。有什么我可以帮助你的吗？
        </div>
      </div>

      <!-- 加载中 -->
      <div class="flex justify-start">
        <div class="max-w-[80%] p-3 rounded-2xl rounded-bl-sm bg-gray-100 dark:bg-gray-800">
          <div class="flex gap-1">
            <div class="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style="animation-delay: 0ms"></div>
            <div class="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style="animation-delay: 150ms"></div>
            <div class="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style="animation-delay: 300ms"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="p-4 border-t border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900">
      <div class="max-w-4xl mx-auto">
        <form class="flex flex-col gap-3">
          <!-- 输入框容器 -->
          <div class="flex flex-col relative w-full shadow-lg rounded-3xl border border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600 focus-within:border-blue-500 dark:focus-within:border-blue-500 transition p-1 bg-white/50 dark:bg-gray-800/50 backdrop-blur-sm">
            <!-- 文件预览区(可选) -->
            <div class="flex flex-wrap gap-2 px-3 pt-2">
              <!-- <div class="flex items-center gap-2 px-2 py-1 bg-gray-100 dark:bg-gray-700 rounded-lg text-sm">文件</div> -->
            </div>

            <!-- 文本输入 -->
            <div class="flex items-end px-3 pb-1">
              <textarea
                id="chat-input"
                class="flex-1 bg-transparent dark:text-gray-100 outline-none resize-none h-10 max-h-40 py-2 scrollbar-hidden"
                placeholder="输入消息..."
                rows="1"
              ></textarea>
            </div>

            <!-- 工具栏 -->
            <div class="flex justify-between items-center px-3 pb-2">
              <div class="flex items-center gap-1">
                <!-- 添加按钮 -->
                <button type="button" class="p-2 rounded-full hover:bg-gray-100 dark:hover:bg-gray-700 transition text-gray-600 dark:text-gray-400">
                  <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"/>
                  </svg>
                </button>
                <!-- 图片按钮 -->
                <button type="button" class="p-2 rounded-full hover:bg-gray-100 dark:hover:bg-gray-700 transition text-gray-600 dark:text-gray-400">
                  <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"/>
                  </svg>
                </button>
              </div>

              <!-- 发送按钮 -->
              <button
                type="submit"
                class="bg-blue-500 hover:bg-blue-600 text-white rounded-full p-2 transition shadow-md hover:shadow-lg"
              >
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"/>
                </svg>
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  </div>

  <script>
    // 简单交互示例
    const input = document.getElementById('chat-input');
    const container = document.getElementById('messages-container');

    // 自动调整输入框高度
    input.addEventListener('input', function() {
      this.style.height = 'auto';
      this.style.height = Math.min(this.scrollHeight, 160) + 'px';
    });

    // 模拟发送
    document.querySelector('form').addEventListener('submit', function(e) {
      e.preventDefault();
      const text = input.value.trim();
      if (!text) return;

      // 添加用户消息
      addMessage('user', text);
      input.value = '';
      input.style.height = 'auto';

      // 模拟AI响应
      setTimeout(() => {
        addMessage('assistant', '这是一条模拟的 AI 响应消息。');
      }, 1000);
    });

    function addMessage(role, content) {
      const div = document.createElement('div');
      div.className = role === 'user'
        ? 'flex justify-end'
        : 'flex justify-start';
      div.innerHTML = `
        <div class="max-w-[80%] p-3 rounded-2xl ${role === 'user'
          ? 'rounded-br-sm bg-blue-500 text-white'
          : 'rounded-bl-sm bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-gray-100'}">
          ${content}
        </div>
      `;
      container.appendChild(div);
      container.scrollTop = container.scrollHeight;
    }
  </script>
</body>
</html>
```

---

## 九、核心 Tailwind 配置参考

```javascript
// tailwind.config.js
module.exports = {
  content: ['./src/**/*.{html,js,svelte,ts}'],
  darkMode: 'class', // 或 'media'
  theme: {
    extend: {
      colors: {
        gray: {
          850: '#1f2937', // 自定义暗色模式中间色
        }
      },
      borderRadius: {
        '3xl': '1.5rem', // 24px
      },
      maxWidth: {
        '8xl': '88rem', // 1408px
      },
      animation: {
        'bounce-slow': 'bounce 1.5s infinite',
      }
    },
  },
  plugins: [
    require('@tailwindcss/typography'),
    require('@tailwindcss/container-queries'),
  ],
}
```

---

## 十、设计要点总结

1. **全屏布局**：使用 `h-screen` 和 `max-h-[100dvh]` 确保全屏且不溢出

2. **弹性布局**：`flex flex-col` 垂直排列，`flex-auto` 自动分配空间

3. **圆角设计**：
   - 输入框：`rounded-3xl` (24px)
   - 消息气泡：`rounded-2xl` (16px) + 一侧小圆角 `rounded-br-sm`

4. **颜色方案**：
   - 亮色：白色背景 + 灰色边框
   - 暗色：`gray-900` 背景 + `gray-800` 边框
   - 使用 `dark:` 前缀区分模式

5. **半透明效果**：`bg-white/50` + `backdrop-blur` 实现毛玻璃效果

6. **阴影层次**：`shadow-lg` 用于输入框，`shadow-md` 用于按钮

7. **过渡动画**：`transition` + `duration-200` 实现平滑交互

8. **内容宽度**：`max-w-6xl` (72rem) 限制内容最大宽度

9. **滚动条处理**：自定义 `.scrollbar-hidden` 隐藏默认滚动条

10. **响应式**：`@container` 容器查询支持自适应

---

*文档生成时间: 2026-03-25*
