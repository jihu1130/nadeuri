/* 홈 화면: 주변 모임 지도·목록, 반경 선택, 내 위치, 이 지역에서 다시 검색, 지금 날씨 (templates/index.html) */
const DEFAULT = { lat: 37.5283, lng: 126.9294 };
let radius = 5000;
let map = null;
let overlays = [];
let openInfo = null;
let myLocationOverlay = null;
let myPos = null;          // 마지막으로 확인한 내 위치 { lat, lng }
let toastTimer = null;
let requestVersion = 0;
const FOCUS_LEVEL = 4;     // 내 위치로 이동할 때 이보다 멀리 축소돼 있으면 이 정도로 확대
let searchPoint = DEFAULT;
let searchIsCurrent = false;

function fmt(dt) {
  const d = new Date(dt);
  return `${d.getMonth() + 1}월 ${d.getDate()}일 · ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}
/* 모임 상태 배지: 모집 중은 기본이라 표시하지 않고, 진행 중·모집 마감만 붙인다 */
const BADGE_CLASS = { ONGOING: 'ongoing', FULL: 'full' };
function badge(g) {
  const cls = BADGE_CLASS[g.status];
  return cls ? ` <span class="status-badge ${cls}">${esc(g.statusLabel)}</span>` : '';
}
/* 진행 중이면 끝나는 시간을, 아니면 시작 시간을 보여 준다 */
function when(g) {
  if (g.status !== 'ONGOING') return fmt(g.startAt);
  const e = new Date(g.endAt);
  return `${String(e.getHours()).padStart(2, '0')}:${String(e.getMinutes()).padStart(2, '0')}까지`;
}
function esc(s) {
  return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}
function showMapFallback() {
  document.getElementById('mapFallback').hidden = false;
}
function showMapToast(message) {
  const toast = document.getElementById('mapToast');
  toast.textContent = message;
  toast.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { toast.hidden = true; }, 5000);
}

// 내 위치는 파란 점 하나로 표시한다
function setLocation(lat, lng) {
  myPos = { lat, lng };
  if (!map) return;
  const pos = new kakao.maps.LatLng(lat, lng);
  map.setCenter(pos);
  if (map.getLevel() > FOCUS_LEVEL + 1) map.setLevel(FOCUS_LEVEL);

  if (myLocationOverlay) myLocationOverlay.setMap(null);
  const marker = document.createElement('div');
  marker.className = 'my-location-marker';
  marker.title = '내 위치';
  marker.innerHTML = '<span class="my-location"></span>';
  myLocationOverlay = new kakao.maps.CustomOverlay({ position: pos, content: marker, zIndex: 5 });
  myLocationOverlay.setMap(map);
  updateLocateButton();
}

// 지도 안 버튼: 다시 검색하지 않고 내 위치로 이동만 한다. 위치를 아직 모르면 새로 찾는다.
function focusMyLocation() {
  if (!myPos) { locate(); return; }
  if (!map) return;
  // 다른 지역을 검색해 둔 상태라면, 내 위치로 돌아온 뒤 "이 지역에서 다시 검색"을 띄울 수 있게 한다
  userMovedMap = true;
  if (map.getLevel() > FOCUS_LEVEL + 1) map.setLevel(FOCUS_LEVEL);
  map.panTo(new kakao.maps.LatLng(myPos.lat, myPos.lng));
  // 점을 잠깐 크게 깜빡여서 눈에 띄게 한다
  const marker = myLocationOverlay?.getContent();
  if (marker) {
    marker.classList.remove('is-flash');
    void marker.offsetWidth; // 애니메이션 다시 시작
    marker.classList.add('is-flash');
  }
}

// 지도 가운데에 내 위치가 있으면 버튼을 파랗게 채워 "지금 내 위치를 보고 있음"을 알려 준다
function updateLocateButton() {
  const button = document.getElementById('mapLocateButton');
  if (!map || !myPos) { button.classList.remove('is-centered'); return; }
  const proj = map.getProjection();
  const center = proj.containerPointFromCoords(map.getCenter());
  const me = proj.containerPointFromCoords(new kakao.maps.LatLng(myPos.lat, myPos.lng));
  button.classList.toggle('is-centered', Math.hypot(center.x - me.x, center.y - me.y) < 24);
}

function locationErrorMessage(err) {
  if (err && err.code === 1) return '브라우저에서 이 사이트의 위치 권한을 허용한 뒤 다시 시도하세요.';
  if (err && err.code === 3) return '위치 확인 시간이 초과되었습니다. 다시 시도하세요.';
  return '위치 정보를 확인할 수 없습니다. 여의도 기준 검색 결과를 표시합니다.';
}
/* 지금 날씨: 기온·습도·강수는 기상청 실제 관측(초단기실황), 하늘상태·강수확률은 예보.
 * 관측을 못 받으면 예보 값만 보여 준다. 좁은 화면에서는 마지막 칸(습도·강수)이 숨겨진다. */
function renderWeather(weather) {
  const chip = document.getElementById('weatherChip');
  if (!weather || !weather.available) {
    chip.innerHTML = '<span class="weather-dot"></span><span>날씨 정보를 표시할 수 없습니다</span>';
    chip.removeAttribute('title');
    return;
  }
  const extras = [];
  if (weather.humidity != null) extras.push(`습도 ${weather.humidity}%`);
  if (weather.rain1h > 0) extras.push(`1시간 ${weather.rain1h}mm`);
  else if (weather.precipitationProbability != null) extras.push(`강수 ${weather.precipitationProbability}%`);
  const label = weather.observed ? '지금' : '예보';
  chip.innerHTML = `<span class="weather-dot"></span><span><b class="weather-label">${label}</b> ${esc(weather.emoji)} ${esc(weather.description)} ${Math.round(weather.temperature)}°</span>`
    + (extras.length ? `<span class="weather-divider">·</span><span>${esc(extras.join(' · '))}</span>` : '');
  chip.title = weather.observed
    ? `기상청 ${weather.observedAt} 관측 (하늘상태·강수확률은 예보)`
    : '기상청 단기예보';
}
function renderEmpty(message, detail) {
  document.getElementById('list').innerHTML = `<div class="list-state empty-state"><span class="empty-sun" aria-hidden="true">＋</span><strong>${esc(message)}</strong><p>${esc(detail)}</p><a href="/gatherings/new" class="empty-cta">모임 등록 <span aria-hidden="true">→</span></a></div>`;
}
function renderError() {
  document.getElementById('list').innerHTML = '<div class="list-state empty-state"><span class="empty-sun" aria-hidden="true">!</span><strong>모임 목록을 불러오지 못했습니다</strong><p>연결 상태를 확인한 뒤 다시 시도하세요.</p><button type="button" class="retry-button" id="retryButton">다시 불러오기</button></div>';
  document.getElementById('retryButton').addEventListener('click', () => loadAt(searchPoint.lat, searchPoint.lng, searchIsCurrent));
}
// 검색 기준: true = 현재 위치, false = 기본 위치(여의도), 'area' = 지도에서 고른 지역
async function loadAt(lat, lng, isCurrent) {
  const version = ++requestVersion;
  document.getElementById('locationLabel').textContent = isCurrent === 'area'
    ? `지도에서 고른 지역 기준 · 반경 ${radius / 1000}km`
    : isCurrent
      ? `현재 위치 기준 · 반경 ${radius / 1000}km`
      : `현재 위치를 확인하지 못해 여의도 기준으로 검색 · 반경 ${radius / 1000}km`;
  drawSearchArea(lat, lng, isCurrent);
  hideResearchButton();
  document.getElementById('listTitle').textContent = '모임 목록';
  document.getElementById('resultCount').textContent = '…';
  document.getElementById('list').innerHTML = '<div class="list-state"><span class="loading-spinner"></span><p>모임 목록을 불러오는 중입니다.</p></div>';
  const [gatheringsResult, weatherResult] = await Promise.allSettled([
    fetch(`/api/gatherings?lat=${lat}&lng=${lng}&radius=${radius}`).then(r => { if (!r.ok) throw new Error('모임 요청 실패'); return r.json(); }),
    fetch(`/api/weather?lat=${lat}&lng=${lng}`).then(r => { if (!r.ok) throw new Error('날씨 요청 실패'); return r.json(); })
  ]);
  if (version !== requestVersion) return;
  renderWeather(weatherResult.status === 'fulfilled' ? weatherResult.value : null);
  if (gatheringsResult.status !== 'fulfilled') {
    document.getElementById('listTitle').textContent = '주변 모임';
    document.getElementById('resultCount').textContent = '—';
    gatherings = [];
    renderTagFilter();
    renderError();
    return;
  }
  gatherings = gatheringsResult.value;
  renderTagFilter();
  renderGatherings();
}

/* ===== 검색 결과 필터 =====
 * 받아 온 모임 목록 안에서 검색어·활동 종류·모집 중 여부로 걸러 목록과 지도 핀에 함께 적용한다.
 * (다시 불러오지 않고 화면에서만 거른다. 다시 검색해도 고른 필터는 유지된다)
 */
let gatherings = [];
const filters = { keyword: '', tag: '', openOnly: false };

function isFiltering() {
  return Boolean(filters.keyword || filters.tag || filters.openOnly);
}
function matchesFilter(g) {
  if (filters.tag && g.tag !== filters.tag) return false;
  if (filters.openOnly && g.status !== 'RECRUITING') return false;
  if (filters.keyword) {
    const text = `${g.title} ${g.placeName} ${g.tagLabel}`.toLowerCase();
    if (!text.includes(filters.keyword)) return false;
  }
  return true;
}

// 활동 종류 버튼: 지금 결과에 있는 종류만, 많은 순으로 보여 준다
function renderTagFilter() {
  const counts = new Map();
  gatherings.forEach(g => {
    const c = counts.get(g.tag) || { label: g.tagLabel, emoji: g.emoji, n: 0 };
    c.n++;
    counts.set(g.tag, c);
  });
  if (filters.tag && !counts.has(filters.tag)) filters.tag = ''; // 고른 종류가 새 결과에 없으면 전체로
  const chip = (tag, text, n) =>
    `<button type="button" class="filter-chip${filters.tag === tag ? ' is-active' : ''}" data-tag="${esc(tag)}" aria-pressed="${filters.tag === tag}">${text}<span class="filter-chip-count">${n}</span></button>`;
  document.getElementById('filterTags').innerHTML = chip('', '전체', gatherings.length)
    + [...counts].sort((a, b) => b[1].n - a[1].n)
        .map(([tag, c]) => chip(tag, `${esc(c.emoji)} ${esc(c.label)}`, c.n)).join('');
}

function resetFilters() {
  filters.keyword = '';
  filters.tag = '';
  filters.openOnly = false;
  document.getElementById('filterKeyword').value = '';
  document.getElementById('filterOpenOnly').checked = false;
  renderTagFilter();
  renderGatherings();
}

function renderGatherings() {
  document.getElementById('listTitle').textContent = `반경 ${radius / 1000}km 모임`;
  const shown = gatherings.filter(matchesFilter);
  document.getElementById('resultCount').textContent = isFiltering() && gatherings.length
    ? `${shown.length}/${gatherings.length}개`
    : `${gatherings.length}개`;
  document.getElementById('list').innerHTML = '';
  overlays.forEach(o => o.setMap(null));
  overlays = [];
  openInfo?.setMap(null);
  openInfo = null;
  if (!gatherings.length) {
    renderEmpty(`반경 ${radius / 1000}km 안에 예정된 모임이 없습니다.`, '검색 반경을 넓히거나 새 모임을 등록하세요.');
    return;
  }
  if (!shown.length) {
    document.getElementById('list').innerHTML = '<div class="list-state empty-state"><span class="empty-sun" aria-hidden="true">?</span><strong>조건에 맞는 모임이 없습니다</strong><p>검색어나 필터를 바꿔 보세요.</p><button type="button" class="retry-button" id="resetFiltersButton">필터 초기화</button></div>';
    document.getElementById('resetFiltersButton').addEventListener('click', resetFilters);
    return;
  }
  shown.forEach(g => {
    let pos = null;
    let info = null;
    const item = document.createElement('a');
    if (map) {
      pos = new kakao.maps.LatLng(g.latitude, g.longitude);
      const pin = document.createElement('button');
      pin.type = 'button';
      pin.className = 'map-pin';
      pin.setAttribute('aria-label', `${g.title} 모임 정보 보기`);
      pin.textContent = g.emoji;
      const pinOverlay = new kakao.maps.CustomOverlay({ position: pos, content: pin, yAnchor: 1, clickable: true, zIndex: 3 });
      pinOverlay.setMap(map);
      info = new kakao.maps.CustomOverlay({
        position: pos, yAnchor: 1.45, clickable: true, zIndex: 4,
        content: `<a class="map-info" href="/gatherings/${g.id}"><b>${esc(g.title)}</b>${badge(g)}<span>${esc(g.placeName)}</span><span>${when(g)} · ${g.currentPeople}/${g.maxPeople}명</span><span class="more">모임 자세히 보기 →</span></a>`
      });
      pin.addEventListener('click', e => { e.stopPropagation(); openInfo?.setMap(null); info.setMap(map); openInfo = info; selectListItem(item, isMapExpanded()); });
      overlays.push(pinOverlay, info);
    }
    item.className = 'meetup-item';
    item.href = `/gatherings/${g.id}`;
    item.innerHTML = `<span class="meetup-emoji">${esc(g.emoji)}</span><span class="meetup-copy"><span class="meetup-tag">${esc(g.tagLabel)}${badge(g)}</span><strong>${esc(g.title)}</strong><span class="meetup-meta">${esc(g.placeName)}</span><span class="meetup-time">${when(g)} <i>·</i> ${g.currentPeople}/${g.maxPeople}명</span></span><span class="meetup-arrow" aria-hidden="true">↗</span>`;
    if (map) {
      item.addEventListener('mouseenter', () => map.panTo(pos));
      /* 크게 보기 중에는 목록을 눌러도 상세 페이지로 가지 않고 지도에서 그 모임을 보여 준다
         (말풍선의 '모임 자세히 보기'로 상세 이동, Ctrl/⌘ 클릭은 그대로 새 탭) */
      item.addEventListener('click', e => {
        if (!isMapExpanded() || e.ctrlKey || e.metaKey || e.shiftKey || e.button !== 0) return;
        e.preventDefault();
        focusGathering(pos, info, item);
      });
    }
    document.getElementById('list').appendChild(item);
  });
}
function isMapExpanded() {
  return document.querySelector('.discover-grid').classList.contains('is-expanded');
}
// 목록에서 고른 모임을 표시하고, 필요하면 목록을 그 위치로 스크롤
function selectListItem(item, scroll) {
  document.querySelectorAll('.meetup-item.is-selected').forEach(el => el.classList.remove('is-selected'));
  item.classList.add('is-selected');
  if (scroll) item.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
}
// 지도를 모임 위치로 옮기고(멀리 축소돼 있으면 확대) 말풍선을 연다
function focusGathering(pos, info, item) {
  if (map.getLevel() > FOCUS_LEVEL) map.setLevel(FOCUS_LEVEL);
  map.panTo(pos);
  openInfo?.setMap(null);
  info.setMap(map);
  openInfo = info;
  selectListItem(item, false);
}
/* ===== 이 지역에서 다시 검색 =====
 * 사용자가 지도를 끌어서 검색한 곳에서 충분히 벗어나면 버튼을 보여 주고,
 * 누르면 지금 지도 가운데를 기준으로 같은 반경에서 다시 찾는다.
 * (목록에 마우스를 올려 지도가 움직이는 것처럼 코드가 옮긴 경우에는 띄우지 않는다)
 */
let userMovedMap = false;
let searchCircle = null;

let searchCenterOverlay = null;

// 검색한 범위를 옅은 원으로 보여 준다.
// 내 위치가 아닌 곳(지도에서 고른 지역·기본 위치)을 기준으로 찾았으면 그 기준점에 주황 점을 찍어 둔다.
function drawSearchArea(lat, lng, isCurrent) {
  if (!map) return;
  if (searchCircle) searchCircle.setMap(null);
  const center = new kakao.maps.LatLng(lat, lng);
  searchCircle = new kakao.maps.Circle({
    center, radius,
    strokeWeight: 1.5, strokeColor: '#c99a1e', strokeOpacity: 0.55, strokeStyle: 'shortdash',
    fillColor: '#f2c94c', fillOpacity: 0.06
  });
  searchCircle.setMap(map);

  searchCenterOverlay?.setMap(null);
  const showCenter = isCurrent !== true; // 내 위치 기준이면 파란 점이 이미 그 자리를 보여 준다
  document.getElementById('legendSearchCenter').hidden = !showCenter;
  if (!showCenter) return;
  if (!searchCenterOverlay) {
    const el = document.createElement('div');
    el.className = 'search-center-marker';
    el.title = '검색 기준 위치';
    el.innerHTML = '<span class="search-center-dot"></span>';
    searchCenterOverlay = new kakao.maps.CustomOverlay({ content: el, zIndex: 4 });
  }
  searchCenterOverlay.setPosition(center);
  searchCenterOverlay.setMap(map);
}

function hideResearchButton() {
  userMovedMap = false;
  document.getElementById('researchButton').hidden = true;
  syncResearchFlag();
}

/* 다시 검색할 기준점(지금 지도 가운데)을 깃발로 보여 준다.
 * 버튼이 떠 있는 동안만 보이고, 지도를 움직이면 가운데를 따라간다. */
let researchFlag = null;
function syncResearchFlag() {
  if (!map) return;
  const visible = !document.getElementById('researchButton').hidden;
  if (!visible) { researchFlag?.setMap(null); return; }
  if (!researchFlag) {
    const el = document.createElement('div');
    el.className = 'research-flag';
    el.title = '다시 검색할 위치';
    el.innerHTML = '<svg viewBox="0 0 32 40" width="32" height="40" aria-hidden="true">'
      + '<ellipse cx="7" cy="38" rx="5" ry="1.8" fill="rgba(40,35,15,.28)"/>'
      + '<path d="M7 38V4" stroke="#5c5542" stroke-width="2.4" stroke-linecap="round"/>'
      + '<path d="M8 4.5h19l-5 6.5 5 6.5H8z" fill="#f2c94c" stroke="#c99a1e" stroke-width="1.4" stroke-linejoin="round"/>'
      + '</svg>';
    // 깃대 아래 끝(x=7/32, y=38/40)이 정확히 지도 가운데에 오도록 기준점을 맞춘다
    researchFlag = new kakao.maps.CustomOverlay({ content: el, xAnchor: 7 / 32, yAnchor: 38 / 40, zIndex: 6 });
  }
  researchFlag.setPosition(map.getCenter());
  if (!researchFlag.getMap()) researchFlag.setMap(map);
}

// 지도 가운데와 검색 기준점 사이 거리(m)
function distanceFromSearch() {
  const c = map.getCenter();
  const toRad = d => d * Math.PI / 180;
  const dLat = toRad(c.getLat() - searchPoint.lat), dLng = toRad(c.getLng() - searchPoint.lng);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(searchPoint.lat)) * Math.cos(toRad(c.getLat())) * Math.sin(dLng / 2) ** 2;
  return 6371000 * 2 * Math.asin(Math.sqrt(a));
}

// 반경의 15%(최소 300m) 넘게 벗어나면 버튼을 띄운다. 다시 가까이 돌아오면 숨긴다.
function updateResearchButton() {
  if (!map || !userMovedMap) return;
  document.getElementById('researchButton').hidden = distanceFromSearch() < Math.max(300, radius * 0.15);
  syncResearchFlag();
}

function researchHere() {
  const c = map.getCenter();
  // 지도 가운데가 내 위치면("내 위치로" 버튼이 파랗게 채워진 상태) "현재 위치 기준"으로 검색한다
  const atMe = myPos && document.getElementById('mapLocateButton').classList.contains('is-centered');
  searchPoint = atMe ? myPos : { lat: c.getLat(), lng: c.getLng() };
  searchIsCurrent = atMe ? true : 'area';
  loadAt(searchPoint.lat, searchPoint.lng, searchIsCurrent);
}

// 첫 화면에서 현재 위치를 확인하고, 지도 안 버튼에서는 필요한 경우 위치를 다시 찾는다
function locate() {
  const buttons = [document.getElementById('mapLocateButton'), document.getElementById('myLocationSearchButton')].filter(Boolean);
  // 위치를 못 찾으면: 이미 알던 내 위치가 있으면 그대로 두고, 없으면 기본 위치(여의도)를 보여 준다
  const fallback = err => {
    if (err) showMapToast(locationErrorMessage(err));
    if (myPos) {
      searchPoint = myPos;
      searchIsCurrent = true;
    } else {
      searchPoint = DEFAULT;
      searchIsCurrent = false;
    }
    loadAt(searchPoint.lat, searchPoint.lng, searchIsCurrent);
  };
  if (!navigator.geolocation) { fallback({ code: 2 }); return; }
  buttons.forEach(b => { b.disabled = true; b.classList.add('is-loading'); });
  const done = () => buttons.forEach(b => { b.disabled = false; b.classList.remove('is-loading'); });
  navigator.geolocation.getCurrentPosition(pos => {
    const { latitude, longitude } = pos.coords;
    searchPoint = { lat: latitude, lng: longitude };
    searchIsCurrent = true;
    setLocation(latitude, longitude);
    loadAt(latitude, longitude, true);
    done();
  }, err => {
    fallback(err);
    done();
  }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 30000 });
}

document.getElementById('radiusSelect').addEventListener('change', e => { radius = Number(e.target.value); loadAt(searchPoint.lat, searchPoint.lng, searchIsCurrent); });
document.getElementById('mapLocateButton').addEventListener('click', focusMyLocation);
// 위쪽 버튼: 위치를 새로 찾고 그 자리에서 다시 검색한다 (지도 안 ⌖ 버튼은 이동만 함)
document.getElementById('myLocationSearchButton').addEventListener('click', () => locate());
document.getElementById('filterKeyword').addEventListener('input', e => {
  filters.keyword = e.target.value.trim().toLowerCase();
  renderGatherings();
});
document.getElementById('filterOpenOnly').addEventListener('change', e => {
  filters.openOnly = e.target.checked;
  renderGatherings();
});
document.getElementById('filterTags').addEventListener('click', e => {
  const chip = e.target.closest('.filter-chip');
  if (!chip) return;
  filters.tag = chip.dataset.tag;
  renderTagFilter();
  renderGatherings();
});

/* 지도 크게 보기: 지도와 검색 결과 목록을 화면 전체로 펼치고, 지도 크기를 다시 계산한 뒤 보던 위치를 유지한다 */
function setMapExpanded(expanded) {
  const panel = document.querySelector('.discover-grid');
  const button = document.getElementById('mapExpandButton');
  if (panel.classList.contains('is-expanded') === expanded) return;
  const center = map?.getCenter();
  panel.classList.toggle('is-expanded', expanded);
  document.body.classList.toggle('map-expanded', expanded);
  button.setAttribute('aria-pressed', String(expanded));
  button.setAttribute('aria-label', expanded ? '지도 작게 보기' : '지도 크게 보기');
  button.title = expanded ? '지도 작게 보기 (Esc)' : '지도 크게 보기';
  button.querySelector('.map-expand-label').textContent = expanded ? '작게 보기' : '크게 보기';
  if (map) {
    map.relayout();
    map.setCenter(center);
    updateLocateButton();
    updateResearchButton();
  }
}
document.getElementById('mapExpandButton').addEventListener('click', () =>
  setMapExpanded(!document.querySelector('.discover-grid').classList.contains('is-expanded')));
document.addEventListener('keydown', e => { if (e.key === 'Escape') setMapExpanded(false); });
if (window.kakao?.maps) {
  kakao.maps.load(() => {
    map = new kakao.maps.Map(document.getElementById('map'), { center: new kakao.maps.LatLng(DEFAULT.lat, DEFAULT.lng), level: 5 });
    map.addControl(new kakao.maps.ZoomControl(), kakao.maps.ControlPosition.RIGHT);
    kakao.maps.event.addListener(map, 'click', () => { openInfo?.setMap(null); openInfo = null; document.querySelectorAll('.meetup-item.is-selected').forEach(el => el.classList.remove('is-selected')); });
    kakao.maps.event.addListener(map, 'idle', () => { updateLocateButton(); updateResearchButton(); });
    kakao.maps.event.addListener(map, 'dragend', () => { userMovedMap = true; updateResearchButton(); });
    // 깃발이 떠 있으면 끌거나 확대·축소하는 동안에도 지도 가운데를 따라가게 한다
    kakao.maps.event.addListener(map, 'center_changed', () => { if (researchFlag?.getMap()) researchFlag.setPosition(map.getCenter()); });
    document.getElementById('researchButton').addEventListener('click', researchHere);
    locate();
  });
} else {
  searchPoint = DEFAULT;
  showMapFallback();
  document.getElementById('mapLocateButton').hidden = true;
  locate();
}
