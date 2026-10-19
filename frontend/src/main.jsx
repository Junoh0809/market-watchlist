import React, { useCallback, useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { createApiClient } from './api.js';
import { formatChange, formatKoreanTime, formatNumber, statusText } from './format.js';
import './style.css';

const api = createApiClient(import.meta.env.VITE_API_URL || '/api/v1');

function Auth({ onLogin }) {
  const [mode, setMode] = useState('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setError('');
    setBusy(true);
    try {
      const credentials = { email: email.trim(), password };
      if (mode === 'signup') await api.request('/auth/signup', { method: 'POST', body: credentials });
      const result = await api.request('/auth/login', { method: 'POST', body: credentials });
      onLogin(result.accessToken);
    } catch (failure) {
      setError(failure.message);
    } finally {
      setBusy(false);
    }
  }

  return <main className="auth">
    <div className="brand">Market<span>ly</span></div>
    <h1>내 관심 종목을 한눈에</h1>
    <p>국내·미국 주식과 시장 흐름을 확인하세요.</p>
    <div className="auth-tabs" role="group" aria-label="계정 메뉴">
      <button type="button" className={mode === 'login' ? 'active' : ''} onClick={() => { setMode('login'); setError(''); }}>로그인</button>
      <button type="button" className={mode === 'signup' ? 'active' : ''} onClick={() => { setMode('signup'); setError(''); }}>회원가입</button>
    </div>
    <form onSubmit={submit}>
      <label>이메일<input value={email} onChange={event => setEmail(event.target.value)} type="email" autoComplete="email" required /></label>
      <label>비밀번호<input value={password} onChange={event => setPassword(event.target.value)} type="password" autoComplete={mode === 'signup' ? 'new-password' : 'current-password'} required /></label>
      <button disabled={busy}>{busy ? '처리 중…' : mode === 'signup' ? '계정 만들기' : '로그인'}</button>
    </form>
    {error && <p className="error" role="alert">{error}</p>}
  </main>;
}

function Timestamp({ label, value }) {
  if (!value) return <small>{label}: 시각 정보 없음</small>;
  return <small><span>{label}: {formatKoreanTime(value)}</span><span className="original-time">원본 {value}</span></small>;
}

function Dashboard({ token, onLogout }) {
  const [watchlist, setWatchlist] = useState([]);
  const [quotes, setQuotes] = useState([]);
  const [indices, setIndices] = useState([]);
  const [news, setNews] = useState([]);
  const [screenRefreshedAt, setScreenRefreshedAt] = useState(null);
  const [suggestions, setSuggestions] = useState([]);
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState(null);
  const [market, setMarket] = useState('US');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const searchSequence = useRef(0);

  const handleFailure = useCallback(failure => {
    if (failure.unauthorized) onLogout();
    else setError(failure.message);
  }, [onLogout]);

  const load = useCallback(async () => {
    try {
      const [watchlistResult, quotesResult, indicesResult, newsResult] = await Promise.all([
        api.request('/me/watchlist', { token }),
        api.request('/me/quotes', { token }),
        api.request('/market-indices', { token }),
        api.request('/me/news', { token }),
      ]);
      setWatchlist(watchlistResult.items);
      setQuotes(quotesResult.items);
      setIndices(indicesResult.items);
      setNews(newsResult.items);
      setScreenRefreshedAt(quotesResult.screenRefreshedAt);
      setError('');
    } catch (failure) {
      handleFailure(failure);
    }
  }, [token, handleFailure]);

  useEffect(() => {
    load();
    const interval = setInterval(load, 60_000);
    return () => clearInterval(interval);
  }, [load]);

  async function search(value, nextMarket = market) {
    const sequence = ++searchSequence.current;
    setQuery(value);
    setSelected(null);
    if (!value.trim()) { setSuggestions([]); return; }
    try {
      const result = await api.request(`/securities?market=${nextMarket}&query=${encodeURIComponent(value.trim())}&limit=20`, { token });
      if (sequence === searchSequence.current) setSuggestions(result.items);
    } catch (failure) {
      if (sequence === searchSequence.current) setSuggestions([]);
      handleFailure(failure);
    }
  }

  async function add(event) {
    event.preventDefault();
    const security = selected || suggestions.find(item => item.symbol.toLowerCase() === query.trim().toLowerCase());
    if (!security || security.market !== market) {
      setError('검색 결과에서 종목을 선택하세요.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      await api.request('/me/watchlist', { token, method: 'POST', body: { market: security.market, symbol: security.symbol } });
      searchSequence.current++;
      setQuery('');
      setSelected(null);
      setSuggestions([]);
      await load();
    } catch (failure) {
      handleFailure(failure);
    } finally {
      setBusy(false);
    }
  }

  async function remove(item) {
    setError('');
    try {
      await api.request(`/me/watchlist/${encodeURIComponent(item.market)}/${encodeURIComponent(item.symbol)}`, { token, method: 'DELETE' });
      await load();
    } catch (failure) {
      handleFailure(failure);
    }
  }

  async function logout() {
    try { await api.request('/auth/logout', { token, method: 'POST' }); }
    catch (failure) { if (!failure.unauthorized) setError(failure.message); }
    onLogout();
  }

  async function deleteAccount() {
    if (!window.confirm('계정과 관심 종목을 삭제할까요? 이 작업은 되돌릴 수 없습니다.')) return;
    setError('');
    try {
      await api.request('/me', { token, method: 'DELETE' });
      onLogout();
    } catch (failure) {
      handleFailure(failure);
    }
  }

  return <main className="app">
    <header><div className="brand">Market<span>ly</span></div><div className="account-actions"><button className="ghost" onClick={deleteAccount}>계정 삭제</button><button className="ghost" onClick={logout}>로그아웃</button></div></header>
    <section className="hero"><div><div className="eyebrow">MY MARKET DASHBOARD</div><h1>오늘의 시장을<br /><em>차분하게</em> 살펴보세요.</h1></div><div className="refresh">자동 갱신 · 60초<Timestamp label="화면 갱신" value={screenRefreshedAt} /></div></section>
    {error && <p className="error" role="alert">{error}</p>}
    <section className="grid">
      <div className="panel wide">
        <div className="panel-title"><h2>관심 종목</h2><form onSubmit={add} className="search-form"><label className="sr-only" htmlFor="market">시장</label><select id="market" value={market} onChange={event => { setMarket(event.target.value); search(query, event.target.value); }}><option value="US">미국</option><option value="KRX">국내</option></select><label className="sr-only" htmlFor="security-query">종목 검색</label><input id="security-query" value={query} onChange={event => search(event.target.value)} placeholder="이름 또는 종목 코드" autoComplete="off" /><button disabled={busy}>{busy ? '추가 중…' : '추가'}</button></form></div>
        {suggestions.length > 0 && <div className="suggestions" aria-label="종목 검색 결과">{suggestions.map(item => <button type="button" key={`${item.market}:${item.symbol}`} onClick={() => { searchSequence.current++; setSelected(item); setQuery(item.symbol); setSuggestions([]); }}>{item.name} <small>{item.market}:{item.symbol} · {item.currency}</small></button>)}</div>}
        {watchlist.length === 0 ? <div className="empty">관심 종목을 추가해보세요.</div> : <div className="quote-list">{watchlist.map(item => {
          const quote = quotes.find(entry => entry.security.market === item.market && entry.security.symbol === item.symbol);
          const stale = !quote || quote.status !== 'NORMAL';
          return <div className="quote" key={`${item.market}:${item.symbol}`}>
            <div className="quote-name"><strong>{item.name}</strong><small>{item.market}:{item.symbol} · {item.currency}</small><Timestamp label="시세 기준" value={quote?.sourceTimestamp} /><Timestamp label="수집" value={quote?.retrievedAt} /><small>출처: {quote?.source || '정보 없음'}</small>{stale && <small className="status">{statusText(quote?.status || 'UPDATING')}</small>}</div>
            <div className="price"><strong>{formatNumber(quote?.price, item.currency)} <small>{item.currency}</small></strong><small className={Number(quote?.change) < 0 ? 'negative' : 'positive'}>{formatChange(quote?.change)} · {formatChange(quote?.changeRate, '%')}</small></div>
            <button className="remove" aria-label={`${item.name} 관심 종목 삭제`} onClick={() => remove(item)}>×</button>
          </div>;
        })}</div>}
      </div>
      <div className="panel"><div className="panel-title"><h2>주요 지수</h2></div>{indices.length === 0 ? <div className="empty">지수 정보가 없습니다.</div> : indices.map(index => <div className="index" key={index.index}><div><strong>{index.name}</strong><Timestamp label="기준" value={index.sourceTimestamp} /><small>출처: {index.source || '정보 없음'}</small>{index.status !== 'NORMAL' && <small className="status">{statusText(index.status)}</small>}</div><div className="index-value"><strong>{formatNumber(index.value)}</strong><small className={Number(index.change) < 0 ? 'negative' : 'positive'}>{formatChange(index.change)}</small></div></div>)}</div>
      <div className="panel"><div className="panel-title"><h2>관련 뉴스</h2></div>{news.length === 0 ? <div className="empty">관심 종목을 추가하면 뉴스가 표시됩니다.</div> : news.map(item => <a className="news" href={item.url} target="_blank" rel="noopener noreferrer" key={item.id}><strong>{item.title}</strong><small>{item.market}:{item.symbol} · {item.source} · {item.provider}</small><Timestamp label="발행" value={item.publishedAt} /></a>)}</div>
    </section>
  </main>;
}

function App() {
  const [token, setToken] = useState(() => localStorage.getItem('token'));
  const onLogout = useCallback(() => { localStorage.removeItem('token'); setToken(null); }, []);
  if (!token) return <Auth onLogin={value => { localStorage.setItem('token', value); setToken(value); }} />;
  return <Dashboard token={token} onLogout={onLogout} />;
}

createRoot(document.getElementById('root')).render(<App />);
