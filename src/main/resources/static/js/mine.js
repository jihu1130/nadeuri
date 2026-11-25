/* 내 모임 화면 (templates/gathering/mine.html)
 * 끝나지 않은 모임을 이미 최대 개수만큼 열었으면 "+ 새 모임"을 눌러도 만들기 화면으로 가지 않고 안내를 보여 준다. */
const limitedButton = document.getElementById('newGatheringLimited');
if (limitedButton) {
  const notice = document.getElementById('hostedLimitNotice');
  limitedButton.addEventListener('click', () => {
    notice.hidden = false;
    // 이미 보이고 있으면 한 번 깜빡여서 눌렀다는 걸 알 수 있게
    notice.classList.remove('is-flash');
    void notice.offsetWidth;
    notice.classList.add('is-flash');
  });
}
