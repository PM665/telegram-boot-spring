'use strict';

// Telegram emulator UI: acts as a Telegram user through the emulator user API (/api).
(() => {
  const HISTORY_POLL_MS = 1000;
  const BOTS_POLL_MS = 3000;
  const el = (id) => document.getElementById(id);
  const ui = {
    userId: el('user-id'),
    username: el('username'),
    bot: el('bot'),
    botStatus: el('bot-status'),
    chat: el('chat'),
    placeholder: el('placeholder'),
    history: el('history'),
    composer: el('composer'),
    text: el('text'),
    send: el('send'),
    error: el('error'),
  };

  // Short description of non-text entries: the action name and brief info.
  const ACTIVITIES = {
    photo: (e) => `📷 Photo${e.text ? `: ${e.text}` : ''}${e.info ? ` (${e.info})` : ''}`,
    location: (e) => `📍 Location ${e.info}`,
    callback: (e) => `▶ Pressed “${e.text}”`,
    callback_answer: (e) => `🔔 Callback answer${e.text ? `: “${e.text}”` : ''}${e.info === 'alert' ? ' (alert)' : ''}`,
    edit_text: (e) => `✏️ Edited message #${e.messageId}: “${e.text}”`,
    edit_markup: (e) => `✏️ Changed buttons of message #${e.messageId}`,
    delete: (e) => `🗑 Deleted message #${e.messageId}`,
    pin: (e) => `📌 Pinned message #${e.messageId}`,
    unpin: (e) => (e.messageId ? `📌 Unpinned message #${e.messageId}` : `📌 Unpinned ${e.info}`),
  };

  let bots = [];
  let view = null; // chat of the selected bot and user
  let loadingHistory = false;
  let reloadHistory = false;

  async function api(method, path, body) {
    const response = await fetch(path, {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : {},
      body: body ? JSON.stringify(body) : undefined,
    });
    const data = await response.json().catch(() => null);
    if (!response.ok) throw new Error(data && data.message ? data.message : `HTTP ${response.status}`);
    return data;
  }

  function showError(error) {
    ui.error.textContent = error.message || String(error);
    ui.error.hidden = false;
    clearTimeout(showError.timer);
    showError.timer = setTimeout(() => { ui.error.hidden = true; }, 6000);
  }

  function div(className, text) {
    const node = document.createElement('div');
    node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  }

  const chatsPath = (bot) => `/api/bots/${encodeURIComponent(bot)}/chats`;
  const chatPath = (v) => `${chatsPath(v.bot)}/${v.userId}`; // private chat id is the user id

  function selectedUserId() {
    const id = Number(ui.userId.value);
    return Number.isInteger(id) && id > 0 ? id : null;
  }

  function profile(v) {
    const username = ui.username.value.trim().replace(/^@/, '');
    return username ? { userId: v.userId, username } : { userId: v.userId };
  }

  // ---- bots ----

  async function loadBots() {
    try {
      bots = await api('GET', '/api/bots');
    } catch (e) {
      return;
    }
    const names = bots.map((b) => b.username);
    if (names.join() !== Array.from(ui.bot.options, (o) => o.value).join()) {
      const selected = ui.bot.value || localStorage.getItem('emulator.bot');
      ui.bot.replaceChildren(...bots.map((b) => new Option(`@${b.username}`, b.username)));
      if (names.includes(selected)) ui.bot.value = selected;
      openChat();
    }
    const bot = bots.find((b) => b.username === ui.bot.value);
    ui.botStatus.classList.toggle('online', Boolean(bot && bot.online));
    ui.botStatus.title = bot && bot.online ? 'online' : 'offline';
  }

  // ---- chat ----

  async function openChat() {
    const bot = ui.bot.value;
    const userId = selectedUserId();
    if (view && view.bot === bot && view.userId === userId) return;
    const v = { bot, userId, started: false, lastSeq: 0, messages: new Map() };
    view = v;
    ui.history.replaceChildren();
    setComposer(false);
    if (!bot) return showPlaceholder(bots.length ? 'Select a bot.' : 'No bots yet. A bot appears here once it is started.');
    if (!userId) return showPlaceholder('Enter your user ID to chat with the bot.');
    localStorage.setItem('emulator.bot', bot);
    showPlaceholder('Loading…');
    try {
      const chats = await api('GET', `${chatsPath(bot)}?userId=${userId}`);
      if (v !== view) return;
      if (chats.length) {
        markStarted(v);
        await refreshHistory();
      } else {
        const start = document.createElement('button');
        start.className = 'primary';
        start.textContent = 'Start';
        start.addEventListener('click', () => startChat(v));
        showPlaceholder(`Start a chat with @${bot}`, start);
      }
    } catch (e) {
      if (v === view) showError(e);
    }
  }

  async function startChat(v) {
    try {
      await api('POST', chatsPath(v.bot), profile(v));
      if (v !== view) return;
      markStarted(v);
      await refreshHistory();
    } catch (e) {
      showError(e);
    }
  }

  function markStarted(v) {
    v.started = true;
    showPlaceholder();
    setComposer(true);
  }

  function showPlaceholder(text, ...nodes) {
    ui.placeholder.replaceChildren(...(text ? [text] : []), ...nodes);
  }

  function setComposer(enabled) {
    ui.text.disabled = !enabled;
    ui.send.disabled = !enabled;
  }

  async function refreshHistory() {
    const v = view;
    if (!v || !v.started) return;
    if (loadingHistory) {
      reloadHistory = true;
      return;
    }
    loadingHistory = true;
    try {
      const entries = await api('GET', `${chatPath(v)}/history?userId=${v.userId}&after=${v.lastSeq}`);
      if (v !== view) return;
      const atBottom = ui.chat.scrollHeight - ui.chat.scrollTop - ui.chat.clientHeight < 40;
      entries.forEach((entry) => render(v, entry));
      if (entries.length && atBottom) ui.chat.scrollTop = ui.chat.scrollHeight;
    } catch (e) {
      if (v === view) showError(e);
    } finally {
      loadingHistory = false;
      if (reloadHistory) {
        reloadHistory = false;
        refreshHistory();
      }
    }
  }

  // ---- history rendering ----

  function render(v, entry) {
    v.lastSeq = Math.max(v.lastSeq, entry.seq);
    const row = div(`entry from-${entry.from} ${entry.kind}`);
    row.dataset.seq = entry.seq;
    if (entry.kind === 'message') {
      row.append(div('bubble', entry.text));
      track(v, entry, row);
    } else {
      const text = div('activity-text', (ACTIVITIES[entry.action] || ((e) => e.action))(entry));
      if (entry.action === 'callback') text.title = `callback data: ${entry.info}`;
      row.append(text);
      if (entry.action === 'photo') track(v, entry, row);
      applyChange(v, entry);
    }
    ui.history.append(row);
  }

  // Bubbles keep the text as it was sent, edits are listed in the history. Buttons always show the current ones:
  // they are what the user can press.
  function track(v, entry, row) {
    const message = { row, buttons: div('buttons'), meta: div('meta'), date: entry.date, edited: false, deleted: false };
    row.append(message.buttons, message.meta);
    v.messages.set(entry.messageId, message);
    setButtons(v, entry.messageId, message, entry.buttons);
    updateMeta(entry.messageId, message);
  }

  function applyChange(v, entry) {
    const message = v.messages.get(entry.messageId);
    if (!message) return;
    if (entry.action === 'edit_text' || entry.action === 'edit_markup') {
      message.edited = true;
      setButtons(v, entry.messageId, message, entry.buttons);
    }
    if (entry.action === 'delete') {
      message.deleted = true;
      message.row.classList.add('deleted');
      message.buttons.querySelectorAll('button').forEach((b) => { b.disabled = true; });
    }
    updateMeta(entry.messageId, message);
  }

  function updateMeta(messageId, message) {
    const time = new Date(message.date).toLocaleTimeString();
    const state = message.deleted ? ' · deleted' : message.edited ? ' · edited' : '';
    message.meta.textContent = `#${messageId} · ${time}${state}`;
  }

  function setButtons(v, messageId, message, rows) {
    message.buttons.replaceChildren(...rows.map((buttons) => {
      const row = div('row');
      row.append(...buttons.map((b) => {
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = b.text;
        button.disabled = message.deleted || !b.callbackData;
        button.title = b.callbackData ? `callback data: ${b.callbackData}` : `link: ${b.url || ''}`;
        button.addEventListener('click', () => press(v, messageId, b.callbackData));
        return button;
      }));
      return row;
    }));
  }

  // ---- user actions ----

  async function press(v, messageId, data) {
    try {
      await api('POST', `${chatPath(v)}/callbacks`, { ...profile(v), messageId, data });
      refreshHistory();
    } catch (e) {
      showError(e);
    }
  }

  ui.composer.addEventListener('submit', async (event) => {
    event.preventDefault();
    const v = view;
    const text = ui.text.value;
    if (!v || !v.started || !text.trim()) return;
    try {
      await api('POST', `${chatPath(v)}/messages`, { ...profile(v), text });
      ui.text.value = '';
      refreshHistory();
    } catch (e) {
      showError(e);
    }
  });

  ui.userId.value = localStorage.getItem('emulator.userId') || '';
  ui.username.value = localStorage.getItem('emulator.username') || '';
  ui.userId.addEventListener('change', () => {
    localStorage.setItem('emulator.userId', ui.userId.value);
    openChat();
  });
  ui.username.addEventListener('change', () => localStorage.setItem('emulator.username', ui.username.value));
  ui.bot.addEventListener('change', () => {
    openChat();
    loadBots();
  });

  loadBots().then(openChat);
  setInterval(loadBots, BOTS_POLL_MS);
  setInterval(refreshHistory, HISTORY_POLL_MS);
})();
