// 나들이 AI 도우미 (화면 오른쪽 아래 버튼 + 대화창). fragments/assistant.html 과 짝.
// 대화 기록은 서버에 저장하지 않고 이 페이지(sessionStorage)에만 두었다가, 질문할 때 최근 것만 함께 보낸다.
(function () {
  const root = document.querySelector('.assistant');
  if (!root) return;

  const toggle = root.querySelector('.assistant-toggle');
  const panel = root.querySelector('.assistant-panel');
  const closeBtn = root.querySelector('.assistant-close');
  const log = root.querySelector('.assistant-log');
  const form = root.querySelector('.assistant-form');
  const input = form.querySelector('textarea');
  const sendBtn = form.querySelector('.assistant-send');
  const loggedIn = root.dataset.loggedIn === 'true';
  const csrf = root.dataset.csrf;
  const csrfHeader = root.dataset.csrfHeader;

  const MAX_HISTORY = 12;          // 서버 제한과 같게 (AssistantRequest.MAX_MESSAGES)
  const STORE_KEY = 'nadeuri.assistant.history';
  let history = loadHistory();      // [{role: 'user'|'assistant', text}]
  let busy = false;
  let position = null;              // {lat, lng} — 근처 모임·날씨 기준

  history.forEach(m => addMessage(m.role === 'user' ? 'user' : 'bot', m.text));
  enableDrag();

  toggle.addEventListener('click', () => setOpen(panel.hidden));
  closeBtn.addEventListener('click', () => setOpen(false));
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape' && !panel.hidden) setOpen(false);
  });

  log.addEventListener('click', e => {
    const example = e.target.closest('[data-example]');
    if (example) ask(example.dataset.example);
  });

  form.addEventListener('submit', e => {
    e.preventDefault();
    ask(input.value);
  });
  input.addEventListener('keydown', e => {
    // Enter 보내기, Shift+Enter 줄바꿈, 한글 조합 중 Enter는 무시
    if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
      e.preventDefault();
      ask(input.value);
    }
  });
  input.addEventListener('input', autoGrow);

  function setOpen(open) {
    panel.hidden = !open;
    toggle.setAttribute('aria-expanded', String(open));
    root.classList.toggle('is-open', open);
    if (open) {
      if (!loggedIn) showLoginNotice();
      locate();
      input.focus();
      scrollToEnd();
    }
  }

  async function ask(raw) {
    const text = (raw || '').trim();
    if (!text || busy) return;
    if (!loggedIn) {
      showLoginNotice();
      return;
    }
    if (text.length > 500) {
      addMessage('error', '질문은 500자 이내로 적어 주세요.');
      return;
    }
    input.value = '';
    autoGrow();
    addMessage('user', text);
    history.push({role: 'user', text});
    saveHistory();

    setBusy(true);
    const typing = addMessage('bot typing', '답을 찾는 중');
    try {
      const body = {messages: history.slice(-MAX_HISTORY)};
      // 대화가 길어 잘렸으면 첫 메시지가 사용자 질문이 아닐 수 있어 맞춘다
      while (body.messages.length && body.messages[0].role !== 'user') body.messages.shift();
      if (position) Object.assign(body, position);

      const headers = {'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest'};
      if (csrf && csrfHeader) headers[csrfHeader] = csrf;
      const res = await fetch('/api/assistant/chat', {method: 'POST', headers, body: JSON.stringify(body)});
      const data = await res.json().catch(() => ({}));
      typing.remove();
      if (!res.ok) {
        if (res.status === 401) {
          showLoginNotice();
        } else {
          addMessage('error', data.message || 'AI 도우미가 지금 응답하지 못했어요. 잠시 후 다시 시도해 주세요.');
        }
        history.pop(); // 답을 못 받은 질문은 기록에서 빼서 다시 물어볼 수 있게
        saveHistory();
        return;
      }
      addMessage('bot', data.reply);
      history.push({role: 'assistant', text: data.reply});
      saveHistory();
    } catch (err) {
      typing.remove();
      addMessage('error', '인터넷 연결을 확인한 뒤 다시 시도해 주세요.');
      history.pop();
      saveHistory();
    } finally {
      setBusy(false);
      input.focus();
    }
  }

  // 말풍선 하나 추가. 사용자·AI 글은 textContent로만 넣고, 모임 주소(/gatherings/UUID)만 링크로 바꾼다
  function addMessage(kind, text) {
    const div = document.createElement('div');
    div.className = 'assistant-msg ' + kind;
    if (kind === 'bot') {
      appendWithLinks(div, text);
    } else {
      div.textContent = text;
    }
    log.appendChild(div);
    scrollToEnd();
    return div;
  }

  function appendWithLinks(el, text) {
    const re = /\/gatherings\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi;
    let last = 0;
    let m;
    while ((m = re.exec(text)) !== null) {
      el.appendChild(document.createTextNode(text.slice(last, m.index)));
      const a = document.createElement('a');
      a.href = m[0];
      a.textContent = '모임 보기';
      a.className = 'assistant-link';
      el.appendChild(a);
      last = m.index + m[0].length;
    }
    el.appendChild(document.createTextNode(text.slice(last)));
  }

  function showLoginNotice() {
    if (log.querySelector('.assistant-login')) return;
    const div = document.createElement('div');
    div.className = 'assistant-msg bot assistant-login';
    div.appendChild(document.createTextNode('AI 도우미는 로그인한 회원만 쓸 수 있어요. '));
    const a = document.createElement('a');
    a.href = '/login';
    a.className = 'assistant-link';
    a.textContent = '로그인하기';
    div.appendChild(a);
    log.appendChild(div);
    scrollToEnd();
  }

  // 기준 위치: 모임 상세면 그 모임 장소, 아니면 브라우저 위치(이미 허용한 경우 빠르게), 실패하면 서버가 여의도 기준
  function locate() {
    if (position) return;
    const detailMap = document.getElementById('detailMap');
    if (detailMap && detailMap.dataset.lat && detailMap.dataset.lng) {
      position = {lat: Number(detailMap.dataset.lat), lng: Number(detailMap.dataset.lng)};
      return;
    }
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(
      p => { position = {lat: p.coords.latitude, lng: p.coords.longitude}; },
      () => {},
      {enableHighAccuracy: false, timeout: 5000, maximumAge: 10 * 60 * 1000}
    );
  }

  // 대화창 위쪽(노란 머리 부분)을 잡고 끌어서 옮긴다. 옮긴 위치는 이 탭에서 기억하고,
  // 머리 부분을 두 번 누르면 원래 자리(오른쪽 아래)로 돌아간다.
  function enableDrag() {
    const head = panel.querySelector('.assistant-head');
    const POS_KEY = 'nadeuri.assistant.position';
    const MARGIN = 8;
    let drag = null;

    restorePosition();

    head.addEventListener('pointerdown', e => {
      if (e.button !== 0 || e.target.closest('button')) return; // 닫기 버튼은 그대로 눌리게
      const rect = panel.getBoundingClientRect();
      drag = {dx: e.clientX - rect.left, dy: e.clientY - rect.top};
      head.setPointerCapture(e.pointerId);
      panel.classList.add('is-dragging');
      e.preventDefault();
    });
    head.addEventListener('pointermove', e => {
      if (!drag) return;
      moveTo(e.clientX - drag.dx, e.clientY - drag.dy);
    });
    const end = () => {
      if (!drag) return;
      drag = null;
      panel.classList.remove('is-dragging');
      savePosition();
    };
    head.addEventListener('pointerup', end);
    head.addEventListener('pointercancel', end);

    head.addEventListener('dblclick', e => {
      if (e.target.closest('button')) return;
      resetPosition();
    });

    // 창 크기가 바뀌어 화면 밖으로 나가면 안쪽으로 당긴다
    window.addEventListener('resize', () => {
      if (!panel.classList.contains('is-moved')) return;
      const rect = panel.getBoundingClientRect();
      moveTo(rect.left, rect.top);
    });

    function moveTo(x, y) {
      const w = panel.offsetWidth;
      const h = panel.offsetHeight;
      const maxX = Math.max(MARGIN, window.innerWidth - w - MARGIN);
      const maxY = Math.max(MARGIN, window.innerHeight - h - MARGIN);
      panel.classList.add('is-moved');
      panel.style.left = Math.round(Math.min(Math.max(x, MARGIN), maxX)) + 'px';
      panel.style.top = Math.round(Math.min(Math.max(y, MARGIN), maxY)) + 'px';
    }

    function resetPosition() {
      panel.classList.remove('is-moved');
      panel.style.left = '';
      panel.style.top = '';
      try { sessionStorage.removeItem(POS_KEY); } catch (e) { /* 무시 */ }
    }

    function savePosition() {
      try {
        sessionStorage.setItem(POS_KEY, JSON.stringify({x: parseInt(panel.style.left, 10), y: parseInt(panel.style.top, 10)}));
      } catch (e) { /* 저장 못 해도 옮기기는 됨 */ }
    }

    function restorePosition() {
      try {
        const saved = JSON.parse(sessionStorage.getItem(POS_KEY) || 'null');
        if (saved && Number.isFinite(saved.x) && Number.isFinite(saved.y)) {
          // 숨겨진 상태에서는 크기를 잴 수 없으니, 처음 열 때 화면 안쪽으로 맞춘다
          panel.classList.add('is-moved');
          panel.style.left = saved.x + 'px';
          panel.style.top = saved.y + 'px';
          toggle.addEventListener('click', () => requestAnimationFrame(() => {
            if (!panel.hidden && panel.classList.contains('is-moved')) moveTo(parseInt(panel.style.left, 10), parseInt(panel.style.top, 10));
          }), {once: true});
        }
      } catch (e) { /* 무시 */ }
    }
  }

  function setBusy(on) {
    busy = on;
    sendBtn.disabled = on;
    input.setAttribute('aria-busy', String(on));
  }

  function autoGrow() {
    input.style.height = 'auto';
    input.style.height = Math.min(input.scrollHeight, 120) + 'px';
  }

  function scrollToEnd() {
    log.scrollTop = log.scrollHeight;
  }

  function loadHistory() {
    try {
      const saved = JSON.parse(sessionStorage.getItem(STORE_KEY) || '[]');
      return Array.isArray(saved) ? saved.filter(m => m && typeof m.text === 'string').slice(-MAX_HISTORY) : [];
    } catch (e) {
      return [];
    }
  }

  function saveHistory() {
    try {
      history = history.slice(-MAX_HISTORY);
      sessionStorage.setItem(STORE_KEY, JSON.stringify(history));
    } catch (e) { /* 저장 못 해도 대화는 계속 */ }
  }
})();
