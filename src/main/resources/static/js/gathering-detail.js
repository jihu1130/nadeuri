/* 모임 상세 화면 (templates/gathering/detail.html): 장소 지도와 카카오맵 길찾기 링크.
 * 좌표·장소 이름은 <div id="detailMap" data-lat data-lng data-place>에서 읽는다. */
const mapEl = document.getElementById('detailMap');
const lat = Number(mapEl.dataset.lat);
const lng = Number(mapEl.dataset.lng);
const placeName = mapEl.dataset.place || '모임 장소';

// 카카오맵 앱/웹에서 길찾기 (지도가 뜨지 않아도 링크는 쓸 수 있게 먼저 만든다)
document.getElementById('directions').href =
  `https://map.kakao.com/link/to/${encodeURIComponent(placeName)},${lat},${lng}`;

// 카카오 지도 SDK를 못 불러오면(키·도메인 문제, 네트워크) 지도 칸만 비워 둔다
if (window.kakao && kakao.maps) {
  kakao.maps.load(() => {
    const pos = new kakao.maps.LatLng(lat, lng);
    const map = new kakao.maps.Map(mapEl, { center: pos, level: 3 });
    map.setZoomable(false); // 페이지 스크롤 중 지도가 확대되지 않게
    new kakao.maps.Marker({ position: pos }).setMap(map);
  });
}
