const seoulTime = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
});

export function formatKoreanTime(value) {
  if (!value) return '시각 정보 없음';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '시각 정보 없음' : `${seoulTime.format(date)} KST`;
}

export function formatNumber(value, currency) {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) return '—';
  const maximumFractionDigits = currency === 'KRW' ? 0 : 2;
  return new Intl.NumberFormat('ko-KR', { maximumFractionDigits, minimumFractionDigits: currency === 'USD' ? 2 : 0 }).format(Number(value));
}

export function formatChange(value, suffix = '') {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) return '—';
  const amount = Number(value);
  return `${amount > 0 ? '+' : ''}${new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 2 }).format(amount)}${suffix}`;
}

export function statusText(status) {
  if (status === 'LAST_KNOWN') return '업데이트 중 · 마지막 정상 데이터';
  if (status === 'UPDATING') return '업데이트 중';
  return '';
}
