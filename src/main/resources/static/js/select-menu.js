/*
 * 꾸민 드롭다운 (select-menu)
 * 브라우저 기본 <select> 목록은 모양을 바꿀 수 없어서, 같은 자리에 버튼 + 목록을 만들어 보여 준다.
 * 실제 값은 원래 <select>에 그대로 들어가므로 폼 전송·required 검사·change 이벤트는 기존과 같다.
 * 사용법: 이 스크립트를 넣으면 data-menu 속성이 있는 <select>가 자동으로 바뀐다.
 */
(function () {
  const EMOJI_START = /^(\p{Extended_Pictographic}(?:️|‍\p{Extended_Pictographic})*)\s*/u;
  const CHEVRON = '<svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg>';
  const CHECK = '<svg viewBox="0 0 24 24" width="15" height="15" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="m5 12.5 4.2 4.2L19 7"/></svg>';
  let seq = 0;

  // "🚶 산책" → { emoji: "🚶", text: "산책" }
  function splitLabel(label) {
    const m = label.match(EMOJI_START);
    return m ? { emoji: m[1], text: label.slice(m[0].length) } : { emoji: '', text: label };
  }

  function fill(el, label) {
    const { emoji, text } = splitLabel(label);
    el.textContent = '';
    if (emoji) {
      const e = document.createElement('span');
      e.className = 'select-menu-emoji';
      e.setAttribute('aria-hidden', 'true');
      e.textContent = emoji;
      el.appendChild(e);
    }
    const t = document.createElement('span');
    t.className = 'select-menu-text';
    t.textContent = text;
    el.appendChild(t);
  }

  function enhance(select) {
    if (select.dataset.menuReady) return;
    select.dataset.menuReady = '1';
    const id = `select-menu-${++seq}`;

    const wrap = document.createElement('div');
    wrap.className = 'select-menu';
    if (select.dataset.menu === 'compact') wrap.classList.add('is-compact');
    select.parentNode.insertBefore(wrap, select);
    wrap.appendChild(select);
    select.classList.add('select-menu-native');
    select.tabIndex = -1;

    const trigger = document.createElement('button');
    trigger.type = 'button';
    trigger.className = 'select-menu-trigger';
    trigger.setAttribute('aria-haspopup', 'listbox');
    trigger.setAttribute('aria-expanded', 'false');
    trigger.setAttribute('aria-controls', `${id}-list`);
    const labelText = select.getAttribute('aria-label') || (select.id && document.querySelector(`label[for="${select.id}"]`)?.textContent.trim());
    if (labelText) trigger.setAttribute('aria-label', labelText);
    const value = document.createElement('span');
    value.className = 'select-menu-value';
    trigger.appendChild(value);
    trigger.insertAdjacentHTML('beforeend', `<span class="select-menu-chevron">${CHEVRON}</span>`);
    wrap.appendChild(trigger);

    const list = document.createElement('ul');
    list.className = 'select-menu-list';
    list.id = `${id}-list`;
    list.setAttribute('role', 'listbox');
    list.tabIndex = -1;
    list.hidden = true;
    wrap.appendChild(list);

    let active = -1;
    const items = () => [...list.children];

    function render() {
      list.textContent = '';
      [...select.options].forEach((opt, i) => {
        const li = document.createElement('li');
        li.id = `${id}-opt-${i}`;
        li.className = 'select-menu-option';
        li.setAttribute('role', 'option');
        li.setAttribute('aria-selected', String(opt.selected));
        if (opt.disabled) li.setAttribute('aria-disabled', 'true');
        fill(li, opt.textContent.trim());
        li.insertAdjacentHTML('beforeend', `<span class="select-menu-check">${CHECK}</span>`);
        li.addEventListener('click', () => { if (!opt.disabled) choose(i); });
        li.addEventListener('mousemove', () => setActive(i, false));
        list.appendChild(li);
      });
      const sel = select.options[select.selectedIndex];
      fill(value, sel ? sel.textContent.trim() : '');
    }

    function setActive(i, scroll = true) {
      const all = items();
      if (!all.length) return;
      active = Math.max(0, Math.min(all.length - 1, i));
      all.forEach((li, n) => li.classList.toggle('is-active', n === active));
      list.setAttribute('aria-activedescendant', all[active].id);
      if (scroll) all[active].scrollIntoView({ block: 'nearest' });
    }

    function open() {
      if (!list.hidden) return;
      render();
      list.hidden = false;
      wrap.classList.add('is-open');
      trigger.setAttribute('aria-expanded', 'true');
      // 아래 공간이 모자라면 위로 펼친다
      const rect = trigger.getBoundingClientRect();
      const need = Math.min(list.scrollHeight, 360) + 12;
      wrap.classList.toggle('is-up', window.innerHeight - rect.bottom < need && rect.top > need);
      setActive(select.selectedIndex);
      list.focus({ preventScroll: true });
    }

    function close(focusTrigger = true) {
      if (list.hidden) return;
      list.hidden = true;
      wrap.classList.remove('is-open');
      trigger.setAttribute('aria-expanded', 'false');
      if (focusTrigger) trigger.focus({ preventScroll: true });
    }

    function choose(i) {
      const changed = select.selectedIndex !== i;
      select.selectedIndex = i;
      render();
      close();
      if (changed) select.dispatchEvent(new Event('change', { bubbles: true }));
    }

    trigger.addEventListener('click', () => (list.hidden ? open() : close()));
    trigger.addEventListener('keydown', e => {
      if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(e.key)) { e.preventDefault(); open(); }
    });
    list.addEventListener('keydown', e => {
      const n = items().length;
      if (e.key === 'ArrowDown') { e.preventDefault(); setActive(active + 1); }
      else if (e.key === 'ArrowUp') { e.preventDefault(); setActive(active - 1); }
      else if (e.key === 'Home') { e.preventDefault(); setActive(0); }
      else if (e.key === 'End') { e.preventDefault(); setActive(n - 1); }
      else if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); if (!select.options[active]?.disabled) choose(active); }
      else if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); close(); }
      else if (e.key === 'Tab') { close(false); }
      else if (e.key.length === 1) {
        // 글자를 치면 그 글자로 시작하는 항목으로 이동
        const k = e.key.toLowerCase();
        const opts = [...select.options];
        for (let s = 1; s <= n; s++) {
          const j = (active + s) % n;
          if (splitLabel(opts[j].textContent.trim()).text.toLowerCase().startsWith(k)) { setActive(j); break; }
        }
      }
    });
    list.addEventListener('focusout', e => { if (!wrap.contains(e.relatedTarget)) close(false); });
    document.addEventListener('pointerdown', e => { if (!wrap.contains(e.target)) close(false); });
    // 연결된 라벨을 누르면 브라우저가 숨겨 둔 기본 <select> 목록을 열어 버리므로(크롬),
    // 기본 동작을 막고 꾸민 목록을 대신 연다. (라벨 안의 꾸민 버튼을 누른 경우는 버튼이 직접 처리)
    [...(select.labels || [])].forEach(label => label.addEventListener('click', e => {
      if (wrap.contains(e.target)) return;
      e.preventDefault();
      trigger.focus({ preventScroll: true });
      open();
    }));
    // 숨긴 <select>가 직접 눌리거나 열리려 하면 막는다
    select.addEventListener('mousedown', e => { e.preventDefault(); trigger.focus(); open(); });
    // 코드가 값을 바꾸거나 포커스가 와도 화면이 맞도록
    select.addEventListener('focus', () => trigger.focus());
    select.addEventListener('change', render);
    select.addEventListener('invalid', () => wrap.classList.add('is-invalid'));
    render();
  }

  function init() { document.querySelectorAll('select[data-menu]').forEach(enhance); }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
