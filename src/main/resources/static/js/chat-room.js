/* 모임 채팅 화면 (templates/chat/room.html). 모임 UUID와 내 UUID는 <main class="chat-shell" data-gathering-id data-me>에서 읽는다 */
const shell = document.querySelector(".chat-shell");
const GATHERING_ID = shell.dataset.gatheringId;   // 모임 공개 UUID
const ME = shell.dataset.me || null;              // 내 공개 UUID
/* ===== 모임 채팅 =====
 * 1) 지난 메시지: GET /api/gatherings/{id}/messages (최근 50개, "이전 메시지 더 보기"로 더)
 * 2) 새 메시지: WebSocket /ws/gatherings/{id}/chat 에 {"content": "..."} 를 보내고,
 *    서버가 같은 방 모두에게 {"type":"message","message":{...}} 를 보낸다.
 * 3) 연결이 끊기면 1·2·4·8…초(최대 30초) 뒤 다시 연결하고, 그사이 놓친 메시지를 ?after= 로 채운다.
 * 4) 입력 중: 글을 쓰는 동안 2초마다 {"type":"typing","typing":true}, 다 지우거나 보내거나 3초 동안 멈추면 typing:false.
 *    다른 사람의 typing은 마지막 신호 뒤 4초가 지나면 저절로 사라진다.
 * 5) 도배 방지: 1초에 5개를 넘게 보내면 서버가 3초 동안 막고 {"type":"rate_limited"}를 보낸다.
 *    막힌 글은 입력칸에 되돌려 놓고, 남은 초를 보여 주며 보내기 버튼을 잠근다.
 * 6) 삭제: 내 말풍선에 마우스를 올리거나(휴대폰은 말풍선을 누르면) "수정·삭제"가 보인다. 확인 창(confirm.js) 뒤
 *    {"type":"delete","id":N}을 보내고, 서버가 방 모두에게 {"type":"deleted","id":N}을 보내면 "삭제된 메시지예요"로 바꾼다.
 * 7) 수정: "수정"을 누르면 입력칸에 원래 내용이 들어가고(위에 "메시지 수정 중"), 보내면 {"type":"edit","id","content"}.
 *    서버가 {"type":"edited","message"}를 보내면 내용을 바꾸고 "수정됨"을 붙인다. "수정됨"을 누르면
 *    GET /api/gatherings/{id}/messages/{메시지 id}/revisions 로 이전 내용을 보여 준다 (참여자 누구나).
 * 8) @멘션: GET /api/gatherings/{id}/chat/members 로 참여자를 받아, 입력칸에서 "@" 뒤에 글자를 치면 목록을 띄운다.
 *    말풍선의 "@닉네임"은 강조하고, 나를 부른 메시지는 줄 전체를 노랗게 표시한다. 서버는 글을 그대로 저장한다.
 */
const $ = id => document.getElementById(id);
const log = $('chatLog'), list = $('chatList'), input = $('chatInput'), sendBtn = $('chatSend');
const seen = new Set();        // 이미 그린 메시지 id (중복 방지)
let oldestId = null, newestId = null;
let socket = null, retry = 0, retryTimer = null;

const pad = n => String(n).padStart(2, '0');
const WEEK = ['일', '월', '화', '수', '목', '금', '토'];
const dayKey = d => `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
const dayLabel = d => `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEK[d.getDay()]})`;
const timeLabel = d => {
  const h = d.getHours();
  return `${h < 12 ? '오전' : '오후'} ${h % 12 === 0 ? 12 : h % 12}:${pad(d.getMinutes())}`;
};

function setConn(text, state) {
  const el = $('chatConn');
  el.textContent = text;
  el.dataset.state = state;
  updateSendButton();
}
function isOpen() {
  return socket && socket.readyState === WebSocket.OPEN;
}
function updateSendButton() {
  sendBtn.disabled = !isOpen() || !input.value.trim() || Date.now() < blockedUntil;
}

/* ----- 도배 방지 (서버가 막으면 남은 초를 보여 주고 보내기를 잠근다) ----- */
let blockedUntil = 0, blockTimer = null;

function startBlock(ms, message) {
  blockedUntil = Date.now() + ms;
  $('chatForm').classList.add('is-limited');
  clearInterval(blockTimer);
  const tick = () => {
    const left = Math.ceil((blockedUntil - Date.now()) / 1000);
    if (left <= 0) {
      clearInterval(blockTimer);
      blockTimer = null;
      $('chatForm').classList.remove('is-limited');
      showError('');
      updateSendButton();
      return;
    }
    showError(`너무 빨리 보내고 있어요. ${left}초 뒤에 다시 보낼 수 있어요.`);
    updateSendButton();
  };
  tick();
  blockTimer = setInterval(tick, 250);
}

/* ----- 입력 중 표시 ----- */
const TYPING_SEND_EVERY = 2000;   // 내가 쓰는 동안 이 간격으로 "입력 중"을 보낸다
const TYPING_IDLE = 3000;         // 이만큼 안 치면 "멈춤"을 보낸다
const TYPING_EXPIRE = 4000;       // 다른 사람의 "입력 중"은 마지막 신호 뒤 이만큼 지나면 지운다
let typingSentAt = 0, typingIdleTimer = null, iAmTyping = false;
const typists = new Map();        // 공개 UUID → { nickname, timer }

function sendTyping(typing) {
  if (!isOpen()) return;
  socket.send(JSON.stringify({ type: 'typing', typing }));
}
function noteMyTyping() {
  if (editing) return;   // 고치는 중에는 "입력 중"을 보내지 않는다
  const hasText = !!input.value.trim();
  clearTimeout(typingIdleTimer);
  if (!hasText) {
    stopMyTyping();
    return;
  }
  const now = Date.now();
  if (!iAmTyping || now - typingSentAt >= TYPING_SEND_EVERY) {
    sendTyping(true);
    iAmTyping = true;
    typingSentAt = now;
  }
  typingIdleTimer = setTimeout(stopMyTyping, TYPING_IDLE);
}
function stopMyTyping() {
  clearTimeout(typingIdleTimer);
  if (iAmTyping) sendTyping(false);
  iAmTyping = false;
  typingSentAt = 0;
}

function onTyping(data) {
  const u = data.user || {};
  if (!u.userId || u.userId === ME) return;
  const prev = typists.get(u.userId);
  if (prev) clearTimeout(prev.timer);
  if (data.typing) {
    typists.set(u.userId, { nickname: u.nickname || '누군가', timer: setTimeout(() => removeTypist(u.userId), TYPING_EXPIRE) });
  } else {
    typists.delete(u.userId);
  }
  renderTyping();
}
function removeTypist(userId) {
  const t = typists.get(userId);
  if (!t) return;
  clearTimeout(t.timer);
  typists.delete(userId);
  renderTyping();
}
function clearTypists() {
  typists.forEach(t => clearTimeout(t.timer));
  typists.clear();
  renderTyping();
}
/** "가나님이 입력 중…", "가나님, 다라님이 입력 중…", "가나님 외 2명이 입력 중…" */
function renderTyping() {
  const el = $('chatTyping');
  const names = [...typists.values()].map(t => t.nickname);
  if (!names.length) {
    el.hidden = true;
    el.textContent = '';
    return;
  }
  const label = names.length === 1 ? `${names[0]}님이 입력 중…`
    : names.length === 2 ? `${names[0]}님, ${names[1]}님이 입력 중…`
    : `${names[0]}님 외 ${names.length - 1}명이 입력 중…`;
  const stick = nearBottom();
  el.textContent = '';
  const dots = document.createElement('span');
  dots.className = 'chat-typing-dots';
  dots.setAttribute('aria-hidden', 'true');
  dots.append(document.createElement('i'), document.createElement('i'), document.createElement('i'));
  el.append(dots, document.createTextNode(label));
  el.hidden = false;
  if (stick) scrollToBottom();   // 줄이 생기며 메시지 칸이 줄어도 맨 아래를 보던 사람은 계속 맨 아래
}
function showError(text) {
  const el = $('chatError');
  el.textContent = text;
  el.hidden = !text;
}
function nearBottom() {
  return log.scrollHeight - log.scrollTop - log.clientHeight < 80;
}
function scrollToBottom() {
  log.scrollTop = log.scrollHeight;
}

/* ----- @멘션: 참여자 목록, 말풍선 안 강조, 입력칸 자동완성 ----- */
let members = [];          // [{ userId, nickname, profileImageUrl }] (닉네임 가나다순)
let myNickname = null;

async function loadMembers() {
  try {
    const r = await fetch(`/api/gatherings/${GATHERING_ID}/chat/members`, { headers: { 'X-Requested-With': 'XMLHttpRequest' } });
    if (!r.ok) return;
    members = await r.json();
    myNickname = members.find(m => m.userId === ME)?.nickname ?? null;
    // 닉네임이 바뀌었거나 새로 들어온 사람이 있을 수 있으니 이미 그린 말풍선도 다시 강조한다
    list.querySelectorAll('.chat-msg:not(.is-deleted)').forEach(row => {
      const bubble = row.querySelector('.chat-bubble');
      fillBubble(row, bubble, bubble.textContent);
    });
  } catch { /* 자동완성만 안 될 뿐 채팅은 된다 */ }
}

/**
 * 말풍선 글 채우기. "@닉네임"이 참여자 닉네임과 맞으면 강조한다 (띄어쓰기가 있는 닉네임도 가장 긴 것부터 맞춘다).
 * 사용자 글은 텍스트 노드로만 넣는다 (HTML로 해석되지 않게). 나를 부른 메시지면 줄에 is-mention-me.
 */
function fillBubble(row, bubble, text) {
  bubble.textContent = '';
  const names = [...new Set(members.map(m => m.nickname).filter(Boolean))].sort((a, b) => b.length - a.length);
  let mentionsMe = false, plain = '', i = 0;
  while (i < text.length) {
    const at = text[i] === '@' && (i === 0 || /\s/.test(text[i - 1]));
    const name = at ? names.find(n => text.startsWith(n, i + 1)) : null;
    if (name) {
      if (plain) bubble.appendChild(document.createTextNode(plain));
      plain = '';
      const span = document.createElement('span');
      span.className = 'chat-mention' + (name === myNickname ? ' is-me' : '');
      span.textContent = '@' + name;
      bubble.appendChild(span);
      if (name === myNickname) mentionsMe = true;
      i += name.length + 1;
    } else {
      plain += text[i++];
    }
  }
  if (plain) bubble.appendChild(document.createTextNode(plain));
  row.classList.toggle('is-mention-me', mentionsMe && !row.classList.contains('is-mine'));
}

// 자동완성: 입력칸에서 "@" 뒤에 글자를 치면 참여자 목록이 뜬다 (↑↓ 고르기, Enter·Tab 넣기, Esc 닫기)
const mentionBox = $('chatMentions');
let mentionStart = -1, mentionItems = [], mentionIndex = 0;

function updateMentions() {
  const caret = input.selectionStart;
  const before = input.value.slice(0, caret);
  const m = /(^|\s)@([^\s@]{0,20})$/.exec(before);
  if (!m || input.selectionStart !== input.selectionEnd) return closeMentions();
  const query = m[2].toLowerCase();
  mentionItems = members
    .filter(x => x.userId !== ME && x.nickname && x.nickname.toLowerCase().includes(query))
    .sort((a, b) => (b.nickname.toLowerCase().startsWith(query) ? 1 : 0) - (a.nickname.toLowerCase().startsWith(query) ? 1 : 0))
    .slice(0, 6);
  if (!mentionItems.length) return closeMentions();
  mentionStart = caret - m[2].length - 1;
  mentionIndex = 0;
  renderMentions();
}

function renderMentions() {
  mentionBox.textContent = '';
  mentionItems.forEach((x, i) => {
    const li = document.createElement('li');
    li.className = 'chat-mention-option' + (i === mentionIndex ? ' is-active' : '');
    li.id = `chatMention${i}`;
    li.setAttribute('role', 'option');
    li.setAttribute('aria-selected', String(i === mentionIndex));
    const avatar = document.createElement(x.profileImageUrl ? 'img' : 'span');
    avatar.className = 'chat-mention-avatar';
    if (x.profileImageUrl) {
      avatar.src = x.profileImageUrl;
      avatar.alt = '';
      avatar.referrerPolicy = 'no-referrer';
    } else {
      avatar.textContent = x.nickname.slice(0, 1);
      avatar.setAttribute('aria-hidden', 'true');
    }
    const name = document.createElement('span');
    name.textContent = x.nickname;
    li.append(avatar, name);
    // mousedown: 입력칸 초점을 잃기 전에 고른다
    li.addEventListener('mousedown', e => {
      e.preventDefault();
      pickMention(i);
    });
    mentionBox.appendChild(li);
  });
  mentionBox.hidden = false;
  input.setAttribute('aria-expanded', 'true');
  input.setAttribute('aria-activedescendant', `chatMention${mentionIndex}`);
}

function closeMentions() {
  if (mentionBox.hidden) return;
  mentionBox.hidden = true;
  mentionBox.textContent = '';
  mentionItems = [];
  mentionStart = -1;
  input.setAttribute('aria-expanded', 'false');
  input.removeAttribute('aria-activedescendant');
}

function pickMention(i) {
  const x = mentionItems[i];
  if (!x || mentionStart < 0) return;
  const caret = input.selectionStart;
  const insert = '@' + x.nickname + ' ';
  input.value = input.value.slice(0, mentionStart) + insert + input.value.slice(caret);
  const pos = mentionStart + insert.length;
  input.setSelectionRange(pos, pos);
  closeMentions();
  autosize();
  updateSendButton();
  input.focus();
}

/** 자동완성이 열려 있을 때의 키. 처리했으면 true */
function mentionKey(e) {
  if (mentionBox.hidden || e.isComposing) return false;
  if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
    const step = e.key === 'ArrowDown' ? 1 : -1;
    mentionIndex = (mentionIndex + step + mentionItems.length) % mentionItems.length;
    renderMentions();
  } else if (e.key === 'Enter' || e.key === 'Tab') {
    pickMention(mentionIndex);
  } else if (e.key === 'Escape') {
    closeMentions();
  } else {
    return false;
  }
  e.preventDefault();
  return true;
}

const DELETED_TEXT = '삭제된 메시지예요';

/** 말풍선을 "삭제된 메시지"로 바꾼다 (수정·삭제 버튼과 "수정됨"도 없앤다) */
function markDeleted(row) {
  if (!row || row.classList.contains('is-deleted')) return;
  if (editing && editing.row === row) cancelEdit();
  row.classList.add('is-deleted');
  row.classList.remove('show-actions', 'is-mention-me');
  row.querySelector('.chat-bubble').textContent = DELETED_TEXT;
  row.querySelector('.chat-actions')?.remove();
  row.querySelector('.chat-edited')?.remove();
}

/** 고친 메시지를 반영한다: 새 내용 + "수정됨" */
function markEdited(m) {
  const row = list.querySelector(`.chat-msg[data-id="${Number(m.id)}"]`);
  if (!row || row.classList.contains('is-deleted')) return;
  fillBubble(row, row.querySelector('.chat-bubble'), m.content);
  if (!row.querySelector('.chat-edited')) row.querySelector('.chat-meta').prepend(editedButton(m.id));
}

/** "수정됨" — 누르면 이전 내용을 보여 준다 (참여자 누구나) */
function editedButton(id) {
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'chat-edited';
  btn.textContent = '수정됨';
  btn.title = '수정 전 내용 보기';
  btn.addEventListener('click', e => {
    e.stopPropagation();
    showHistory(id);
  });
  return btn;
}

/* ----- 수정 기록 창 ----- */
let historyDialog = null;

function fullTimeLabel(d) {
  return `${d.getMonth() + 1}월 ${d.getDate()}일 ${timeLabel(d)}`;
}

async function showHistory(id) {
  if (!historyDialog) {
    historyDialog = document.createElement('dialog');
    historyDialog.className = 'chat-history';
    historyDialog.setAttribute('aria-labelledby', 'chatHistoryTitle');
    const box = document.createElement('div');
    box.className = 'chat-history-box';
    const title = document.createElement('p');
    title.className = 'chat-history-title';
    title.id = 'chatHistoryTitle';
    title.textContent = '수정 기록';
    const items = document.createElement('ol');
    items.className = 'chat-history-list';
    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'btn secondary chat-history-close';
    close.textContent = '닫기';
    close.addEventListener('click', () => historyDialog.close());
    box.append(title, items, close);
    historyDialog.appendChild(box);
    historyDialog.addEventListener('click', e => { if (e.target === historyDialog) historyDialog.close(); });
    document.body.appendChild(historyDialog);
  }
  const items = historyDialog.querySelector('.chat-history-list');
  items.textContent = '';
  try {
    const versions = await api(`/${Number(id)}/revisions`);
    if (!versions.length) {
      const li = document.createElement('li');
      li.textContent = '삭제된 메시지예요.';
      items.appendChild(li);
    }
    // 지금 내용이 맨 위, 그 아래로 예전 내용
    versions.slice().reverse().forEach(v => {
      const li = document.createElement('li');
      li.className = 'chat-history-item' + (v.current ? ' is-current' : '');
      const meta = document.createElement('span');
      meta.className = 'chat-history-meta';
      meta.textContent = (v.current ? '지금 · ' : '') + fullTimeLabel(new Date(v.writtenAt));
      const text = document.createElement('p');
      text.className = 'chat-history-text';
      text.textContent = v.content;
      li.append(meta, text);
      items.appendChild(li);
    });
    historyDialog.showModal();
  } catch {
    showError('수정 기록을 불러오지 못했어요.');
  }
}

/* ----- 수정 모드: 입력칸에 원래 내용을 넣고, 보내면 고친다 (Esc·취소로 그만두기) ----- */
let editing = null;   // { id, row, draft }

function startEdit(row, content) {
  if (editing) cancelEdit();
  editing = { id: Number(row.dataset.id), row, draft: input.value };
  stopMyTyping();
  row.classList.add('is-editing');
  row.classList.remove('show-actions');
  input.value = content ?? row.querySelector('.chat-bubble').textContent;
  $('chatEditing').hidden = false;
  $('chatForm').classList.add('is-editing');
  sendBtn.textContent = '수정';
  autosize();
  updateSendButton();
  input.focus();
  input.setSelectionRange(input.value.length, input.value.length);
}

function cancelEdit() {
  if (!editing) return;
  const draft = editing.draft;
  endEdit();
  input.value = draft;   // 수정하기 전에 쓰던 글은 되돌려 놓는다
  autosize();
  updateSendButton();
}

function endEdit() {
  if (!editing) return;
  editing.row.classList.remove('is-editing');
  editing = null;
  $('chatEditing').hidden = true;
  $('chatForm').classList.remove('is-editing');
  sendBtn.textContent = '보내기';
}

/** 내 메시지 옆 "수정"·"삭제" 버튼 */
function actionsEl(row, id) {
  const box = document.createElement('span');
  box.className = 'chat-actions';
  const edit = document.createElement('button');
  edit.type = 'button';
  edit.className = 'chat-act';
  edit.textContent = '수정';
  edit.addEventListener('click', e => {
    e.stopPropagation();
    startEdit(row);
  });
  box.append(edit, deleteForm(id));
  return box;
}

/** 내 메시지 옆 "삭제" 버튼. 확인 창은 confirm.js가 data-confirm을 보고 띄운다 */
function deleteForm(id) {
  const form = document.createElement('form');
  form.className = 'chat-del-form';
  form.dataset.confirm = '메시지를 삭제할까요? 모든 참여자 화면에서 "삭제된 메시지예요"로 바뀌고 되돌릴 수 없어요.';
  const btn = document.createElement('button');
  btn.type = 'submit';
  btn.className = 'chat-del danger';
  btn.textContent = '삭제';
  form.appendChild(btn);
  form.addEventListener('submit', e => {
    // 처음 누를 때는 confirm.js가 막고 확인 창을 띄운다. 확인을 누르면 confirmed 표시와 함께 다시 들어온다
    if (form.dataset.confirmed !== '1') return;
    e.preventDefault();
    if (!isOpen()) {
      showError('연결이 끊겨서 지우지 못했어요. 다시 연결되면 해 주세요.');
      return;
    }
    socket.send(JSON.stringify({ type: 'delete', id }));
  });
  return form;
}

/** 메시지 한 줄 요소. 사용자 글은 textContent로만 넣는다 (HTML로 해석되지 않게) */
function messageEl(m) {
  const mine = m.userId === ME;
  const row = document.createElement('div');
  row.className = 'chat-msg' + (mine ? ' is-mine' : '');
  row.dataset.id = m.id;
  row.dataset.user = m.userId;
  const at = new Date(m.createdAt);
  row.dataset.day = dayKey(at);

  if (!mine) {
    const avatar = document.createElement(m.profileImageUrl ? 'img' : 'span');
    avatar.className = 'chat-avatar';
    if (m.profileImageUrl) {
      avatar.src = m.profileImageUrl;
      avatar.alt = '';
      avatar.referrerPolicy = 'no-referrer';
    } else {
      avatar.textContent = (m.nickname || '?').slice(0, 1);
      avatar.setAttribute('aria-hidden', 'true');
    }
    row.appendChild(avatar);
  }
  const body = document.createElement('div');
  body.className = 'chat-body';
  if (!mine) {
    const name = document.createElement('span');
    name.className = 'chat-name';
    name.textContent = m.nickname || '알 수 없음';
    body.appendChild(name);
  }
  const line = document.createElement('div');
  line.className = 'chat-line';
  const bubble = document.createElement('p');
  bubble.className = 'chat-bubble';
  if (m.deleted) {
    bubble.textContent = DELETED_TEXT;
    row.classList.add('is-deleted');
  } else {
    fillBubble(row, bubble, m.content);
  }
  const time = document.createElement('time');
  time.className = 'chat-time';
  time.dateTime = m.createdAt;
  time.textContent = timeLabel(at);
  // 시간 칸: 고친 메시지면 시간 위에 "수정됨"
  const meta = document.createElement('span');
  meta.className = 'chat-meta';
  if (m.edited && !m.deleted) meta.appendChild(editedButton(m.id));
  meta.appendChild(time);
  line.append(bubble, meta);
  if (mine && !m.deleted) {
    line.appendChild(actionsEl(row, m.id));
    // 마우스가 없는 휴대폰: 내 말풍선을 누르면 "수정·삭제"가 보였다 숨었다 한다
    bubble.addEventListener('click', () => {
      list.querySelectorAll('.chat-msg.show-actions').forEach(el => { if (el !== row) el.classList.remove('show-actions'); });
      row.classList.toggle('show-actions');
    });
  }
  body.appendChild(line);
  row.appendChild(body);
  return row;
}

function dayDivider(d) {
  const el = document.createElement('div');
  el.className = 'chat-day';
  el.dataset.dayDivider = dayKey(d);
  el.textContent = dayLabel(d);
  return el;
}

/** 날짜 구분선과 "같은 사람이 이어서 보낸 글" 표시를 처음부터 다시 맞춘다 */
function relayout() {
  list.querySelectorAll('.chat-day').forEach(el => el.remove());
  let prev = null;
  list.querySelectorAll('.chat-msg').forEach(el => {
    if (!prev || prev.dataset.day !== el.dataset.day) {
      list.insertBefore(dayDivider(new Date(el.querySelector('time').dateTime)), el);
      el.classList.remove('is-cont');
    } else {
      el.classList.toggle('is-cont', prev.dataset.user === el.dataset.user);
    }
    prev = el;
  });
  $('chatEmpty').hidden = seen.size > 0;
}

/** 아래(새 메시지) 또는 위(이전 메시지)에 붙인다 */
function add(messages, where) {
  const fresh = messages.filter(m => !seen.has(m.id));
  if (!fresh.length) return 0;
  fresh.forEach(m => seen.add(m.id));
  const frag = document.createDocumentFragment();
  fresh.forEach(m => frag.appendChild(messageEl(m)));
  if (where === 'top') list.insertBefore(frag, list.firstChild); else list.appendChild(frag);
  const ids = [...seen];
  oldestId = Math.min(...ids);
  newestId = Math.max(...ids);
  relayout();
  return fresh.length;
}

async function api(query) {
  const r = await fetch(`/api/gatherings/${GATHERING_ID}/messages${query}`, { headers: { 'X-Requested-With': 'XMLHttpRequest' } });
  if (r.status === 403) throw new Error('forbidden');
  if (!r.ok) throw new Error('load failed');
  return r.json();
}

async function loadLatest() {
  const messages = await api('');
  add(messages, 'bottom');
  $('chatMore').hidden = messages.length < 50;
  scrollToBottom();
}

$('chatMore').addEventListener('click', async () => {
  const btn = $('chatMore');
  btn.disabled = true;
  const before = log.scrollHeight;
  try {
    const messages = await api(`?before=${oldestId}`);
    add(messages, 'top');
    btn.hidden = messages.length < 50;
    log.scrollTop += log.scrollHeight - before;   // 보고 있던 위치 유지
  } catch {
    showError('이전 메시지를 불러오지 못했어요.');
  } finally {
    btn.disabled = false;
  }
});

function connect() {
  clearTimeout(retryTimer);
  setConn(retry ? '다시 연결 중…' : '연결 중…', 'connecting');
  const proto = location.protocol === 'https:' ? 'wss' : 'ws';
  socket = new WebSocket(`${proto}://${location.host}/ws/gatherings/${GATHERING_ID}/chat`);

  socket.addEventListener('open', async () => {
    const reconnect = retry > 0;
    retry = 0;
    setConn('연결됨', 'open');
    showError('');
    if (reconnect && newestId != null) {
      try {
        const stick = nearBottom();
        add(await api(`?after=${newestId}`), 'bottom');
        if (stick) scrollToBottom();
      } catch { /* 다음 메시지가 오면 괜찮다 */ }
    }
  });
  socket.addEventListener('message', e => {
    const data = JSON.parse(e.data);
    if (data.type === 'message') {
      const stick = nearBottom() || data.message.userId === ME;
      // 처음 보는 사람(새로 참여)이면 @멘션 목록을 새로 받는다
      if (data.message.userId && !members.some(x => x.userId === data.message.userId)) loadMembers();
      add([data.message], 'bottom');
      removeTypist(data.message.userId);   // 보냈으면 더 이상 입력 중이 아님
      if (stick) scrollToBottom();
    } else if (data.type === 'edited') {
      markEdited(data.message);
    } else if (data.type === 'deleted') {
      markDeleted(list.querySelector(`.chat-msg[data-id="${Number(data.id)}"]`));
    } else if (data.type === 'typing') {
      onTyping(data);
    } else if (data.type === 'rate_limited') {
      // 막힌 글을 잃지 않게 입력칸이 비어 있으면 되돌려 놓는다 (수정이었으면 수정 모드로)
      const editRow = data.editId ? list.querySelector(`.chat-msg[data-id="${Number(data.editId)}"]`) : null;
      if (editRow && !editRow.classList.contains('is-deleted') && !editing && !input.value.trim()) {
        startEdit(editRow, data.content);
      } else if (data.content && !input.value.trim()) {
        input.value = data.content;
        autosize();
      }
      startBlock(Number(data.retryAfterMs) || 3000, data.message);
    } else if (data.type === 'error') {
      showError(data.message);
    }
  });
  socket.addEventListener('close', async e => {
    clearTypists();
    iAmTyping = false;
    // 1008: 서버가 참여자가 아니라서 끊음(참여 취소·모임 삭제), 또는 로그아웃·세션 만료로 Tomcat이 끊음
    if (e.code === 1008) {
      stopForGood(STOPPED);
      return;
    }
    // 다시 붙기 전에 아직 볼 수 있는지 확인한다. 로그인이 풀렸으면(403) 계속 다시 시도해도 소용없다
    if (retry > 0) {
      try {
        await api(`?after=${newestId ?? 0}`);
      } catch (err) {
        if (err.message === 'forbidden') {
          stopForGood(STOPPED);
          return;
        }
      }
    }
    retry++;
    const wait = Math.min(30, 2 ** (retry - 1));
    setConn(`연결 끊김 · ${wait}초 뒤 다시 연결`, 'closed');
    retryTimer = setTimeout(connect, wait * 1000);
  });
}

const STOPPED = '채팅 연결이 끊겼어요. 로그인이 풀렸거나 이 모임에 더 이상 참여하고 있지 않아요. 새로 고침해서 확인해 주세요.';

/** 다시 연결해도 안 되는 경우: 재시도를 멈추고 이유를 보여 준다 */
function stopForGood(message) {
  clearTimeout(retryTimer);
  retry = 0;
  setConn('연결 끊김', 'closed');
  showError(message);
}

function send() {
  const text = input.value.trim();
  if (!text || !isOpen() || Date.now() < blockedUntil) return;
  if (editing) {
    const { id, row, draft } = editing;
    const unchanged = text === row.querySelector('.chat-bubble').textContent;
    endEdit();
    if (!unchanged) socket.send(JSON.stringify({ type: 'edit', id, content: text }));
    input.value = draft;   // 수정 전에 쓰던 글로 돌아간다
    autosize();
    updateSendButton();
    input.focus();
    return;
  }
  socket.send(JSON.stringify({ content: text }));
  iAmTyping = false;          // 서버가 메시지를 퍼뜨리면 다른 사람 화면에서 "입력 중"이 지워진다
  clearTimeout(typingIdleTimer);
  input.value = '';
  autosize();
  sendBtn.disabled = true;
  input.focus();
}

function autosize() {
  input.style.height = 'auto';
  input.style.height = Math.min(input.scrollHeight, 140) + 'px';
}

$('chatForm').addEventListener('submit', e => { e.preventDefault(); send(); });
input.addEventListener('keydown', e => {
  if (mentionKey(e)) return;   // @멘션 목록이 열려 있으면 ↑↓·Enter·Tab·Esc는 목록용
  // 한글 입력 조합 중의 Enter는 보내지 않는다 (마지막 글자가 두 번 가는 문제 방지)
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault();
    send();
  } else if (e.key === 'Escape' && editing) {
    e.preventDefault();
    cancelEdit();
  }
});
$('chatEditCancel').addEventListener('click', () => {
  cancelEdit();
  input.focus();
});
input.addEventListener('input', () => {
  autosize();
  updateSendButton();
  noteMyTyping();
  updateMentions();
});
// 커서를 옮기면 그 자리 기준으로 @멘션 목록을 다시 본다
input.addEventListener('click', updateMentions);
input.addEventListener('keyup', e => {
  if (e.key === 'ArrowLeft' || e.key === 'ArrowRight' || e.key === 'Home' || e.key === 'End') updateMentions();
});
// 다른 곳을 누르거나 탭을 떠나면 "입력 중"을 멈춘다
input.addEventListener('blur', () => {
  stopMyTyping();
  closeMentions();
});
window.addEventListener('pagehide', stopMyTyping);
// 탭을 다시 보면 끊긴 연결을 바로 다시 시도
document.addEventListener('visibilitychange', () => {
  if (!document.hidden && socket && socket.readyState === WebSocket.CLOSED && retryTimer && retry > 0) connect();
});

loadMembers();
loadLatest()
  .catch(err => showError(err.message === 'forbidden'
    ? '이 모임 채팅에 참여할 수 없어요.' : '지난 메시지를 불러오지 못했어요.'))
  .finally(connect);
