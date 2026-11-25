/* data-confirm="메시지"가 붙은 폼은 보내기 전에 확인 창을 띄운다 (모임 삭제, 참여 취소, 회원 탈퇴 등).
 * 브라우저 기본 확인 창(confirm) 대신 사이트 모양에 맞춘 창(<dialog>)을 쓴다.
 * - 메시지의 첫 문장(…요?)은 제목, 나머지는 설명으로 나눠 보여 준다
 * - 확인 버튼 글자는 그 폼의 제출 버튼 글자를 그대로 쓴다 (예: "모임 삭제", "넘기기")
 * - 제출 버튼이 빨간(danger) 버튼이면 확인 버튼도 빨갛게
 * - Esc·바깥 누르기·취소 = 보내지 않음. 처음 초점은 "취소"에 둬서 실수로 Enter를 눌러도 안전하게
 * HTML에 onsubmit="…"을 직접 쓰지 않으려고 따로 둔 공용 스크립트. */
(() => {
  let dialog, title, body, ok, pending = null;

  function build() {
    dialog = document.createElement('dialog');
    dialog.className = 'confirm-dialog';
    dialog.setAttribute('aria-labelledby', 'confirmDialogTitle');
    dialog.setAttribute('aria-describedby', 'confirmDialogBody');
    dialog.innerHTML = `
      <form method="dialog" class="confirm-dialog-box">
        <p class="confirm-dialog-title" id="confirmDialogTitle"></p>
        <p class="confirm-dialog-body" id="confirmDialogBody"></p>
        <div class="confirm-dialog-actions">
          <button type="submit" value="cancel" class="btn secondary">취소</button>
          <button type="submit" value="ok" class="btn confirm-dialog-ok">확인</button>
        </div>
      </form>`;
    document.body.appendChild(dialog);
    title = dialog.querySelector('.confirm-dialog-title');
    body = dialog.querySelector('.confirm-dialog-body');
    ok = dialog.querySelector('.confirm-dialog-ok');

    // 창 바깥(어두운 배경)을 누르면 취소
    dialog.addEventListener('click', e => { if (e.target === dialog) dialog.close('cancel'); });
    dialog.addEventListener('close', () => {
      const p = pending;
      pending = null;
      if (!p || dialog.returnValue !== 'ok') return;
      p.form.dataset.confirmed = '1';
      // requestSubmit: 눌렀던 버튼과 입력값 검사를 그대로 살려서 다시 보낸다
      if (p.form.requestSubmit) p.form.requestSubmit(p.submitter || undefined);
      else HTMLFormElement.prototype.submit.call(p.form);
    });
  }

  // "모임을 삭제할까요? 참여자 기록도…" → 제목 "모임을 삭제할까요?" + 설명 "참여자 기록도…"
  function split(message) {
    const m = /^(.+?[?？!.])\s+(.+)$/s.exec(message.trim());
    return m ? [m[1], m[2]] : [message.trim(), ''];
  }

  function open(form, submitter) {
    if (!dialog) build();
    const button = submitter || form.querySelector('[type="submit"]');
    const [t, b] = split(form.dataset.confirm);
    title.textContent = t;
    body.textContent = b;
    body.hidden = !b;
    ok.textContent = (button?.textContent || '').trim() || '확인';
    ok.classList.toggle('danger', !!button?.classList.contains('danger'));
    pending = { form, submitter };
    dialog.returnValue = '';
    dialog.showModal();
  }

  document.addEventListener('submit', e => {
    const form = e.target.closest('form[data-confirm]');
    if (!form) return;
    if (form.dataset.confirmed === '1') { delete form.dataset.confirmed; return; } // 확인을 누른 뒤 다시 보내는 중
    e.preventDefault();
    if (typeof HTMLDialogElement === 'undefined') {
      // 아주 오래된 브라우저: 기본 확인 창으로
      if (confirm(form.dataset.confirm)) { form.dataset.confirmed = '1'; HTMLFormElement.prototype.submit.call(form); }
      return;
    }
    open(form, e.submitter);
  });
})();
