/* data-confirm="메시지"가 붙은 폼은 보내기 전에 확인 창을 띄운다 (모임 삭제, 참여 취소, 회원 탈퇴 등).
 * HTML에 onsubmit="…"을 직접 쓰지 않으려고 따로 둔 공용 스크립트. */
document.addEventListener('submit', e => {
  const form = e.target.closest('form[data-confirm]');
  if (form && !confirm(form.dataset.confirm)) {
    e.preventDefault();
  }
});
