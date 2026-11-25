/* 모임 만들기·수정 화면: 날씨 보며 시간 고르기, 장소 검색·지도 선택, 끝나는 시간 안내 (templates/gathering/new.html) */
const $ = id => document.getElementById(id);
function esc(s) {
  return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}

/* ===== 날씨 보며 날짜·시간 고르기 =====
 * 날짜 줄(14일) → 시간 줄(0~23시, 기온 그래프 + 강수확률) → 분(00/15/30/45) → 요약
 * 예보는 /api/weather/forecast (기상청 단기예보, 대략 3~4일 뒤까지). 그 뒤 날짜도 고를 수 있지만 "예보 없음".
 */
const weatherPicker = (() => {
  const DAYS_SHOWN = 14;
  const HOUR_W = 58;                       // 시간 칸 너비(px) — CSS .wx-hour 와 같아야 함
  const WEEK = ['일', '월', '화', '수', '목', '금', '토'];
  const pad = n => String(n).padStart(2, '0');
  const ymd = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  const hourLabel = h => h === 0 ? '오전 12시' : h < 12 ? `오전 ${h}시` : h === 12 ? '오후 12시' : `오후 ${h - 12}시`;

  let forecast = { available: false, days: [] };
  let byDate = {};                         // 'YYYY-MM-DD' → day
  let loadedKey = null, requestNo = 0;

  // 처음 선택값: 수정·입력 오류로 다시 열면 그 시작 시간, 새 모임이면 2시간 뒤 정시
  const init = new Date(Date.now() + 2 * 3600e3); init.setMinutes(0, 0, 0);
  let sel = { date: ymd(init), hour: init.getHours(), minute: 0 };
  const preset = /^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})/.exec($('startAt').value || '');
  if (preset) sel = { date: preset[1], hour: +preset[2], minute: [0, 15, 30, 45].includes(+preset[3]) ? +preset[3] : 0 };

  function dateOf(s) { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); }
  function selectedDateTime() { const d = dateOf(sel.date); d.setHours(sel.hour, sel.minute, 0, 0); return d; }
  let byTime = new Map();                  // 정시(ms) → 시간별 예보 (날짜 경계를 넘어 찾기 위해)
  const HOUR_MS = 3600e3;
  function slotTime(dateStr, hour) { const d = dateOf(dateStr); d.setHours(hour, 0, 0, 0); return d.getTime(); }
  function hourInfo(dateStr, hour) {
    return byTime.get(slotTime(dateStr, hour)) || null;
  }
  /**
   * 그 시각의 예보. 정확한 값이 없으면 1시간 앞뒤 값을 "근처 예보"로 쓴다.
   * - 지금 이 시간: 이번 발표는 다음 정시부터 나와서 바로 다음 시간 값을 씀
   * - 4일 뒤처럼 3시간 간격인 날: 사이 시간은 가장 가까운 값을 씀
   * @returns {{info, approx}|null} null이면 정말 알 수 없음
   */
  function forecastFor(dateStr, hour) {
    const t = slotTime(dateStr, hour);
    if (byTime.has(t)) return { info: byTime.get(t), approx: false };
    const near = byTime.get(t + HOUR_MS) || byTime.get(t - HOUR_MS);
    return near ? { info: near, approx: true } : null;
  }
  // 예보가 몇 시간뿐인 날(예보 범위의 끝)은 하루 요약을 믿을 수 없으니 따로 표시한다
  const FEW_HOURS = 4;
  function dayName(d) {
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const diff = Math.round((d - today) / 86400e3);
    return diff === 0 ? '오늘' : diff === 1 ? '내일' : diff === 2 ? '모레' : WEEK[d.getDay()];
  }

  async function load(lat, lng, sourceLabel) {
    $('wxSource').textContent = `날씨 기준: ${sourceLabel}`;
    const key = `${(+lat).toFixed(2)},${(+lng).toFixed(2)}`;  // 약 1km 안은 같은 예보
    if (key === loadedKey) return;
    loadedKey = key;
    const no = ++requestNo;
    $('wxRange').textContent = '예보 불러오는 중…';
    try {
      const r = await fetch(`/api/weather/forecast?lat=${lat}&lng=${lng}`);
      if (!r.ok) throw new Error();
      const data = await r.json();
      if (no !== requestNo) return;
      forecast = data;
    } catch {
      if (no !== requestNo) return;
      forecast = { available: false, days: [] };
      loadedKey = null;
    }
    byDate = Object.fromEntries(forecast.days.map(d => [d.date, d]));
    byTime = new Map(forecast.days.flatMap(d => d.hours).map(h => [new Date(h.time).getTime(), h]));
    if (forecast.available && forecast.forecastUntil) {
      const u = new Date(forecast.forecastUntil);
      $('wxRange').textContent = `예보는 ${u.getMonth() + 1}월 ${u.getDate()}일 ${hourLabel(u.getHours())}까지 있어요`;
    } else {
      $('wxRange').textContent = '지금은 예보를 불러올 수 없어요';
    }
    render();
  }

  function renderDays() {
    const box = $('wxDays');
    box.innerHTML = '';
    const start = new Date(); start.setHours(0, 0, 0, 0);
    const dates = [];
    for (let i = 0; i < DAYS_SHOWN; i++) { const d = new Date(start); d.setDate(start.getDate() + i); dates.push(d); }
    // "더 먼 날짜"로 고른 날이 목록 밖이면 끝에 붙인다
    if (!dates.some(d => ymd(d) === sel.date)) dates.push(dateOf(sel.date));

    dates.forEach(d => {
      const key = ymd(d);
      const day = byDate[key];
      const isToday = key === ymd(new Date());
      // 오늘이 아닌데 예보가 몇 시간뿐이면(예: 자정 값 하나) 하루 요약 대신 "일부만 예보"
      const few = day && !isToday && day.hours.length < FEW_HOURS;
      const known = day && !few;
      const b = document.createElement('button');
      b.type = 'button';
      b.className = 'wx-day' + (known ? '' : ' no-forecast') + (d.getDay() === 0 ? ' sun' : d.getDay() === 6 ? ' sat' : '');
      b.setAttribute('aria-pressed', String(key === sel.date));
      const head = `<span class="wx-day-name">${dayName(d)}</span><span class="wx-day-date">${d.getMonth() + 1}/${d.getDate()}</span>`;
      b.innerHTML = known
        ? `${head}<span class="wx-day-icon" aria-hidden="true">${esc(day.emoji)}</span>
           <span class="wx-day-temp"><b>${Math.round(day.maxTemperature)}°</b> ${Math.round(day.minTemperature)}°</span>
           <span class="wx-day-pop">💧${day.maxPrecipitationProbability}%</span>`
        : `${head}<span class="wx-day-icon" aria-hidden="true">❔</span>
           <span class="wx-day-temp muted">${few ? '일부만 예보' : '예보 없음'}</span><span class="wx-day-pop"></span>`;
      b.setAttribute('aria-label', `${d.getMonth() + 1}월 ${d.getDate()}일 ${dayName(d)}` +
        (known ? `, ${day.description}, 최고 ${Math.round(day.maxTemperature)}도 최저 ${Math.round(day.minTemperature)}도, 강수확률 ${day.maxPrecipitationProbability}%`
               : few ? ', 일부 시간만 예보 있음' : ', 예보 없음'));
      b.addEventListener('click', () => {
        sel.date = key;
        // 오늘을 고르면 이미 지난 시각은 고를 수 없으니 다음 시각으로 옮긴다
        if (selectedDateTime() <= new Date()) sel.hour = Math.min(23, new Date().getHours() + 1);
        render();
      });
      box.appendChild(b);
    });
    box.querySelector('[aria-pressed="true"]')?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  }

  function renderHours() {
    const box = $('wxHours');
    const now = new Date();
    const temps = [];
    let html = '';
    for (let h = 0; h < 24; h++) {
      const d = dateOf(sel.date); d.setHours(h, 45, 0, 0);       // 그 시간대의 마지막 선택지(45분)까지 지났으면 막는다
      const past = d <= now;
      const exact = hourInfo(sel.date, h);
      if (exact) temps.push({ h, t: exact.temperature });   // 그래프는 실제 예보 값만 잇는다
      const pressed = h === sel.hour;
      // 칸 내용: 지난 시간 → 흐린 점 / 실제 예보 / 근처 예보(≈, 흐리게) / 정말 모름 → ❔
      const f = past ? null : forecastFor(sel.date, h);
      const info = f?.info;
      const cls = past ? ' is-past' : !f ? ' no-forecast' : f.approx ? ' approx' : '';
      const label = past ? ', 지난 시간'
        : !f ? ', 예보 없음'
        : `, ${f.approx ? '근처 시간 예보 ' : ''}${info.description} ${Math.round(info.temperature)}도, 강수확률 ${info.precipitationProbability}%`;
      html += `<button type="button" class="wx-hour${cls}${info?.rainy ? ' rainy' : ''}" data-hour="${h}"
                 aria-pressed="${pressed}" ${past ? 'disabled' : ''} aria-label="${hourLabel(h)}${label}">
                 <span class="wx-hour-temp">${info ? (f.approx ? '≈' : '') + Math.round(info.temperature) + '°' : ''}</span>
                 <span class="wx-hour-icon" aria-hidden="true">${past ? '·' : info ? esc(info.emoji) : '❔'}</span>
                 <span class="wx-hour-pop">${info ? info.precipitationProbability + '%' : past ? '' : '예보 없음'}</span>
                 <span class="wx-hour-time">${hourLabel(h)}</span>
               </button>`;
    }
    // 기온 꺾은선 그래프 (예보가 있는 시간만 잇는다)
    let svg = '';
    if (temps.length > 1) {
      const min = Math.min(...temps.map(p => p.t)), max = Math.max(...temps.map(p => p.t));
      const y = t => 34 - (max === min ? 0.5 : (t - min) / (max - min)) * 26;
      const pts = temps.map(p => `${p.h * HOUR_W + HOUR_W / 2},${y(p.t).toFixed(1)}`).join(' ');
      svg = `<svg class="wx-chart" width="${24 * HOUR_W}" height="40" aria-hidden="true">
               <polygon points="${temps[0].h * HOUR_W + HOUR_W / 2},40 ${pts} ${temps[temps.length - 1].h * HOUR_W + HOUR_W / 2},40" class="wx-chart-fill"/>
               <polyline points="${pts}" class="wx-chart-line"/>
             </svg>`;
    }
    box.innerHTML = svg + `<div class="wx-hour-row">${html}</div>`;
    box.querySelectorAll('.wx-hour').forEach(b => b.addEventListener('click', () => { sel.hour = +b.dataset.hour; fixMinute(); render(); }));
    // 고른 시간이 가운데 오도록 가로 스크롤 (새 칸이 그려진 다음 프레임에, 애니메이션 없이)
    const wrap = $('wxHoursWrap');
    requestAnimationFrame(() => wrap.scrollTo({
      left: Math.max(0, sel.hour * HOUR_W - wrap.clientWidth / 2 + HOUR_W / 2), behavior: 'instant'
    }));
  }

  // 고른 시각이 이미 지났으면 같은 시간대의 다음 분으로, 그래도 지났으면 다음 시간으로
  function fixMinute() {
    const now = new Date();
    for (const m of [sel.minute, 0, 15, 30, 45]) {
      const d = dateOf(sel.date); d.setHours(sel.hour, m, 0, 0);
      if (d > now) { sel.minute = m; return; }
    }
  }

  function renderMinutes() {
    const now = new Date();
    document.querySelectorAll('.wx-minutes [data-minute]').forEach(b => {
      const m = +b.dataset.minute;
      const d = dateOf(sel.date); d.setHours(sel.hour, m, 0, 0);
      b.disabled = d <= now;
      b.setAttribute('aria-pressed', String(m === sel.minute));
    });
  }

  function renderSummary() {
    const dt = selectedDateTime();
    // 번개 모임을 켜 둔 동안에는 시작 시간을 번개 버튼이 정한다 (예보를 늦게 받아 다시 그려도 덮어쓰지 않게)
    if (!$('flash').checked) {
      $('startAt').value = `${sel.date}T${pad(sel.hour)}:${pad(sel.minute)}`;
      updateEndHint();
    }
    const when = `${dt.getMonth() + 1}월 ${dt.getDate()}일 (${WEEK[dt.getDay()]}) ${hourLabel(sel.hour).replace('시', '')}:${pad(sel.minute)}`;
    const f = forecastFor(sel.date, sel.hour);
    const info = f?.info;
    const box = $('wxSummary');
    if (dt <= new Date()) {
      box.className = 'wx-summary warn';
      box.textContent = `${when} — 이미 지난 시간이에요. 다른 시간을 골라 주세요.`;
    } else if (info) {
      box.className = 'wx-summary' + (info.rainy ? ' rainy' : '');
      box.innerHTML = `<b>${esc(when)}</b><span>${esc(info.emoji)} ${esc(info.description)} ${f.approx ? '약 ' : ''}${Math.round(info.temperature)}° · 강수확률 ${info.precipitationProbability}%</span>`
        + (info.rainy ? '<span class="wx-summary-note">비·눈 소식이 있어요. 실내 장소나 다른 시간도 생각해 보세요.</span>' : '');
    } else {
      box.className = 'wx-summary';
      box.innerHTML = `<b>${esc(when)}</b><span>❔ <span class="muted">이 시간은 아직 날씨를 알 수 없어요 (기상청 예보는 약 4일 뒤까지 나와요)</span></span>`;
    }
  }

  function render() { renderDays(); renderHours(); renderMinutes(); renderSummary(); }

  document.querySelectorAll('.wx-minutes [data-minute]').forEach(b =>
    b.addEventListener('click', () => { sel.minute = +b.dataset.minute; render(); }));

  // 14일보다 먼 날짜: 달력 입력을 펼쳐서 고른다
  const far = $('wxFarDate');
  far.min = ymd(new Date());
  $('wxFarToggle').addEventListener('click', () => { far.hidden = !far.hidden; if (!far.hidden) far.focus(); });
  far.addEventListener('change', () => { if (far.value) { sel.date = far.value; fixMinute(); render(); } });

  render();
  return { load, render };
})();

// 카카오 지도를 못 불러오면 기본 위치(여의도) 날씨라도 보여 준다
// (수정 화면처럼 저장된 장소가 있으면 그 장소 날씨)
if (!window.kakao?.maps) {
  if ($('latitude').value && $('longitude').value) {
    weatherPicker.load(+$('latitude').value, +$('longitude').value, `선택한 장소 · ${$('placeName').value}`);
  } else {
    weatherPicker.load(37.5283, 126.9294, '기본 위치(여의도)');
  }
}

window.kakao?.maps && kakao.maps.load(() => {
  const map = new kakao.maps.Map($('pickMap'), {
    center: new kakao.maps.LatLng(37.5283, 126.9294),
    level: 4
  });
  const marker = new kakao.maps.Marker();
  const places = new kakao.maps.services.Places();
  const geocoder = new kakao.maps.services.Geocoder();
  let myPos = null, myOverlay = null, toastTimer = null;

  // 수정·입력 오류로 다시 열었으면 저장된 장소를 지도에 표시하고 그 장소 날씨를,
  // 새 모임이면 장소를 고르기 전까지 지도 중심 날씨를 보여 준다
  const presetPlace = $('latitude').value && $('longitude').value;
  if (!presetPlace) {
    weatherPicker.load(37.5283, 126.9294, '지도 중심 (장소를 고르면 그 장소 날씨로 바뀌어요)');
  }

  function showToast(msg) {
    const t = $('pickMapToast');
    t.textContent = msg;
    t.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { t.hidden = true; }, 5000);
  }

  function showMyLocation(lat, lng) {
    myPos = new kakao.maps.LatLng(lat, lng);
    if (myOverlay) myOverlay.setMap(null);
    const dot = document.createElement('div');
    dot.className = 'my-location-marker';
    dot.title = '내 위치';
    dot.innerHTML = '<span class="my-location"></span>';
    myOverlay = new kakao.maps.CustomOverlay({ position: myPos, content: dot, zIndex: 5 });
    myOverlay.setMap(map);
  }

  // "내 위치로" 버튼: 지도를 내 위치로 옮긴다 (장소는 지도를 눌러 직접 고른다)
  function goToMyLocation(fromButton) {
    const button = $('pickLocateButton');
    if (!navigator.geolocation) { if (fromButton) showToast('이 브라우저에서는 위치를 찾을 수 없어요.'); return; }
    button.disabled = true;
    button.classList.add('is-loading');
    navigator.geolocation.getCurrentPosition(p => {
      showMyLocation(p.coords.latitude, p.coords.longitude);
      // 처음 열 때 이미 장소가 있으면(수정 화면) 지도를 내 위치로 끌고 가지 않는다
      if (fromButton || !$('latitude').value) {
        if (map.getLevel() > 4) map.setLevel(4);
        map.panTo(myPos);
      }
      if (!$('latitude').value) {
        weatherPicker.load(p.coords.latitude, p.coords.longitude, '내 위치 (장소를 고르면 그 장소 날씨로 바뀌어요)');
      }
      if (fromButton) showToast('내 위치로 옮겼어요. 지도를 눌러 모임 장소를 정하세요.');
      button.disabled = false;
      button.classList.remove('is-loading');
    }, err => {
      if (fromButton) {
        showToast(err && err.code === 1
          ? '위치 권한이 꺼져 있어요. 주소창 왼쪽 아이콘을 눌러 위치를 허용해 주세요.'
          : '지금은 내 위치를 찾을 수 없어요.');
      }
      button.disabled = false;
      button.classList.remove('is-loading');
    }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 30000 });
  }
  $('pickLocateButton').addEventListener('click', () => goToMyLocation(true));
  goToMyLocation(false); // 처음 열 때도 내 위치를 표시한다 (새 모임이면 지도도 옮김, 실패하면 조용히 넘어감)
  if (presetPlace) {
    const saved = new kakao.maps.LatLng(+$('latitude').value, +$('longitude').value);
    map.setCenter(saved);
    choose(saved, $('placeName').value || null);
  }

  function choose(latlng, name) {
    marker.setPosition(latlng);
    marker.setMap(map);
    map.panTo(latlng);
    $('latitude').value = latlng.getLat().toFixed(6);
    $('longitude').value = latlng.getLng().toFixed(6);
    if (name) $('placeName').value = name;
    weatherPicker.load(latlng.getLat(), latlng.getLng(), name ? `선택한 장소 · ${name}` : '선택한 장소');
  }

  // 지도 클릭 → 그 위치의 주소를 장소 검색 칸에 채움 (모임 장소 이름은 직접 적도록 건드리지 않음)
  let clickNo = 0;
  kakao.maps.event.addListener(map, 'click', e => {
    const latlng = e.latLng;
    const no = ++clickNo;
    choose(latlng);
    $('searchResults').innerHTML = '';
    $('placeSearch').value = '';
    $('placeSearch').placeholder = '주소 찾는 중…';
    geocoder.coord2Address(latlng.getLng(), latlng.getLat(), (result, status) => {
      if (no !== clickNo) return; // 그새 다른 곳을 눌렀으면 무시
      $('placeSearch').placeholder = '장소 검색 (예: 여의도 한강공원)';
      if (status !== kakao.maps.services.Status.OK) return;
      const a = result[0].road_address || result[0].address;
      $('placeSearch').value = a.address_name;
    });
  });

  // 키워드로 장소 검색 (지도 중심 근처 우선)
  function search() {
    const q = $('placeSearch').value.trim();
    const ul = $('searchResults');
    if (!q) { ul.innerHTML = ''; return; }
    places.keywordSearch(q, (data, status) => {
      if (status === kakao.maps.services.Status.ZERO_RESULT) {
        ul.innerHTML = '<li class="search-state">검색 결과가 없습니다.</li>';
        return;
      }
      if (status !== kakao.maps.services.Status.OK) {
        ul.innerHTML = '<li class="search-state">장소 검색에 실패했습니다. 다시 시도하세요.</li>';
        return;
      }
      ul.innerHTML = '';
      data.slice(0, 7).forEach(p => {
        const li = document.createElement('li');
        li.innerHTML = `<b>${esc(p.place_name)}</b><span class="meta">${esc(p.road_address_name || p.address_name)}</span>`;
        li.addEventListener('click', () => {
          choose(new kakao.maps.LatLng(p.y, p.x), p.place_name);
          map.setLevel(3);
          ul.innerHTML = '';
        });
        ul.appendChild(li);
      });
    }, { location: map.getCenter(), sort: kakao.maps.services.SortBy.ACCURACY });
  }

  $('searchBtn').addEventListener('click', search);
  $('placeSearch').addEventListener('keydown', e => {
    if (e.key === 'Enter') { e.preventDefault(); search(); } // 엔터로 폼이 제출되지 않게
  });
});

/* 진행 시간 아래에 "16:30에 끝나요"처럼 끝나는 시간을 보여 준다 */
function updateEndHint() {
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec($('startAt').value || '');
  const hint = $('endHint');
  if (!m || !hint) return;
  const start = new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5]);
  const end = new Date(start.getTime() + Number($('durationMinutes').value) * 60000);
  const hhmm = `${String(end.getHours()).padStart(2, '0')}:${String(end.getMinutes()).padStart(2, '0')}`;
  const sameDay = end.toDateString() === start.toDateString();
  hint.textContent = `${sameDay ? '' : `${end.getMonth() + 1}월 ${end.getDate()}일 `}${hhmm}에 끝나요. 끝나는 시간이 지나면 '종료'로 바뀌고 주변 검색에서 빠집니다.`;
}
$('durationMinutes').addEventListener('change', updateEndHint);
updateEndHint();

/* ===== 번개 모임 =====
 * 켜면 날씨 달력을 숨기고 "10분 뒤 ~ 2시간 뒤" 버튼으로 시작 시간을 정한다 (5분 단위로 올림).
 * 끄면 날씨 달력에서 골라 둔 시간으로 돌아간다. 2시간 안인지는 서버도 다시 확인한다.
 */
const flashForm = (() => {
  const toggle = $('flash');
  const box = $('flashStarts');
  const picker = document.querySelector('.wx-picker');
  const buttons = box.querySelectorAll('[data-after]');
  const pad = n => String(n).padStart(2, '0');
  let chosen = null;                       // 고른 버튼 (지금부터 몇 분 뒤)

  function startAfter(min) {
    const d = new Date(Date.now() + min * 60000);
    d.setSeconds(0, 0);
    d.setMinutes(Math.ceil(d.getMinutes() / 5) * 5);   // 60분이 되면 Date가 다음 시로 넘긴다
    return d;
  }
  function describe() {
    const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec($('startAt').value || '');
    const text = $('flashSummary');
    if (!m || chosen == null && !toggle.defaultChecked) { text.textContent = '언제 시작할지 골라 주세요.'; return; }
    const start = new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5]);
    const day = start.toDateString() === new Date().toDateString() ? '오늘' : '내일';
    text.innerHTML = `<b>⚡ ${day} ${pad(start.getHours())}:${pad(start.getMinutes())}</b>에 시작해요`;
  }
  function apply(min) {
    chosen = min;
    const d = startAfter(min);
    $('startAt').value = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
    buttons.forEach(b => b.setAttribute('aria-pressed', String(+b.dataset.after === min)));
    updateEndHint();
    describe();
  }
  function sync(byUser) {
    const on = toggle.checked;
    box.hidden = !on;
    picker.hidden = on;
    toggle.closest('form').classList.toggle('is-flash', on);
    if (on) {
      // 직접 켰으면 우선 30분 뒤로 맞춰 둔다. 수정·입력 오류로 다시 연 번개 모임은 저장된 시간을 그대로 둔다
      if (chosen != null) apply(chosen); else if (byUser) apply(30); else describe();
    } else {
      chosen = null;
      buttons.forEach(b => b.setAttribute('aria-pressed', 'false'));
      weatherPicker.render();              // 날씨 달력에서 골라 둔 시간으로 되돌린다
    }
  }

  buttons.forEach(b => b.addEventListener('click', () => apply(+b.dataset.after)));
  toggle.addEventListener('change', () => sync(true));
  sync(false);

  // "30분 뒤"는 보내는 때부터 30분 뒤가 되도록 저장 직전에 다시 맞춘다 (화면을 오래 열어 둔 경우)
  return { beforeSubmit() { if (toggle.checked && chosen != null) apply(chosen); } };
})();

// 머리말의 로그아웃 폼이 아니라 모임 폼에 건다 (예전엔 querySelector('form')이 로그아웃 폼을 잡았다)
$('flash').closest('form').addEventListener('submit', e => {
  flashForm.beforeSubmit();
  if (!$('latitude').value) {
    e.preventDefault();
    alert('모임 장소를 선택해 주세요. 장소를 검색해 고르거나 지도에서 위치를 눌러 주세요.');
  }
});
