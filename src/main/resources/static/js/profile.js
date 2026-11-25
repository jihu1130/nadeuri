/* 프로필 설정 화면 (templates/profile.html)
 * - 사진을 고르면 브라우저에서 가운데를 정사각형으로 잘라 640px로 줄인 뒤 올린다
 *   (휴대폰 사진의 회전 정보가 반영되고, 큰 사진도 빠르게 올라간다. 서버가 한 번 더 다듬어 저장)
 * - 자기소개 글자 수 표시
 */
(() => {
  const form = document.getElementById('photoForm');
  const input = document.getElementById('photoInput');
  const submit = document.getElementById('photoSubmit');
  const preview = document.getElementById('photoPreview');
  const error = document.getElementById('photoError');
  const MAX_BYTES = Number(form.dataset.maxBytes);
  const SERVER_LIMIT = 12 * 1024 * 1024; // 이보다 크면 서버가 요청을 못 받으므로 줄이지 못했을 때는 막는다
  const SIZE = 640;
  const TYPES = ['image/jpeg', 'image/png', 'image/gif'];

  function showError(msg) {
    error.textContent = msg;
    error.hidden = !msg;
  }

  function showPreview(blob) {
    const img = document.createElement('img');
    img.alt = '새 프로필 사진 미리보기';
    img.src = URL.createObjectURL(blob);
    preview.replaceChildren(img);
  }

  // 가운데 정사각형으로 잘라 줄인다. 투명한 PNG는 PNG로, 나머지는 JPEG로
  async function squareResize(file) {
    const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
    const side = Math.min(bitmap.width, bitmap.height);
    const size = Math.min(SIZE, side);
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = size;
    const ctx = canvas.getContext('2d');
    const png = file.type === 'image/png';
    if (!png) { ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, size, size); }
    ctx.drawImage(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side, 0, 0, size, size);
    bitmap.close?.();
    const type = png ? 'image/png' : 'image/jpeg';
    const blob = await new Promise(r => canvas.toBlob(r, type, 0.9));
    if (!blob) throw new Error('resize failed');
    return new File([blob], png ? 'profile.png' : 'profile.jpg', { type });
  }

  input.addEventListener('change', async () => {
    submit.disabled = true;
    showError('');
    const file = input.files[0];
    if (!file) return;
    if (!TYPES.includes(file.type)) { showError('JPG, PNG, GIF 사진만 올릴 수 있어요.'); input.value = ''; return; }
    let upload = file;
    try {
      upload = await squareResize(file);
      const dt = new DataTransfer();
      dt.items.add(upload);
      input.files = dt.files;
    } catch {
      // 줄이지 못하면(오래된 브라우저 등) 원본을 그대로 올린다
      if (file.size > SERVER_LIMIT) { showError('사진은 5MB까지 올릴 수 있어요.'); input.value = ''; return; }
    }
    if (upload.size > MAX_BYTES) { showError('사진은 5MB까지 올릴 수 있어요.'); input.value = ''; return; }
    showPreview(upload);
    submit.disabled = false;
  });

  form.addEventListener('submit', () => { submit.disabled = true; submit.textContent = '올리는 중…'; });

  const bio = document.getElementById('bio');
  const count = document.getElementById('bioCount');
  const updateCount = () => { count.textContent = bio.value.length; };
  bio.addEventListener('input', updateCount);
  updateCount();
})();
