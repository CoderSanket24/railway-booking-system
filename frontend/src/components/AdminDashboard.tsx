import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { API_ENDPOINTS } from '../config/api';

/* ─── Types ─── */
interface OsEvent {
  timestamp: number; type: string; unit: string;
  detail: string; pid: number; icon: string; success: boolean;
}
interface OsMetrics {
  fcfsQueueSize: number; sjfQueueSize: number;
  roundRobinQueueSize: number; priorityQueueSize: number;
  activeReaders: number;
  totalReaderSessions?: number;
  peakConcurrentReaders?: number;
  totalJobsDispatched?: number;
  mutexState?: { isLocked: boolean; lockedByPid: number | null };
  ticketsInBuffer?: number;
  recentProcesses?: { pid: number; type: string; newState: string }[];
  philosophers?: Record<string, string>;
}

const SCHEDULERS = [
  { key: 'FCFS',     label: 'FCFS',     icon: '📋', color: '#3B82F6', ds: 'FIFO Queue',      desc: 'First Come First Served — bookings processed in arrival order' },
  { key: 'SJF',      label: 'SJF',      icon: '⚡', color: '#F59E0B', ds: 'Min-Heap',        desc: 'Shortest Job First — smallest seat-count bookings go first' },
  { key: 'RR',       label: 'RR',       icon: '🔄', color: '#8B5CF6', ds: 'Circular Queue',  desc: 'Round Robin — each booking gets a fixed time quantum (10ms)' },
  { key: 'PRIORITY', label: 'Priority', icon: '⭐', color: '#EF4444', ds: 'Max-Heap',        desc: 'Priority — Tatkal bookings get higher priority than normal' },
] as const;

const EVENT_COLORS: Record<string, string> = {
  BOOKING: '#10B981', SCHEDULER: '#3B82F6', MUTEX: '#EF4444',
  MEMORY: '#8B5CF6', FILE_IO: '#06B6D4', TLB: '#F59E0B',
  BANKER: '#F97316', READER: '#6b7280',
};

const UNIT_LABELS: Record<string, string> = {
  'App': 'App', 'Unit-I': 'PCB', 'Unit-II': 'Sync',
  'Unit-III': 'CPU', 'Unit-IV': 'Deadlock', 'Unit-V': 'Memory', 'Unit-VI': 'I/O',
};

/* ─── Tiny reusable pieces ─── */
const StatPill: React.FC<{ label: string; value: string | number; color: string; icon: string }> = ({ label, value, color, icon }) => (
  <div style={{ background: `${color}11`, border: `1px solid ${color}33`, borderRadius: 12, padding: '12px 16px', display: 'flex', alignItems: 'center', gap: 10 }}>
    <span style={{ fontSize: 22 }}>{icon}</span>
    <div>
      <div style={{ fontSize: 10, color: '#6b7280', fontWeight: 700, textTransform: 'uppercase' as const, letterSpacing: '0.06em' }}>{label}</div>
      <div style={{ fontSize: 19, fontWeight: 800, color, fontFamily: 'monospace' }}>{value}</div>
    </div>
  </div>
);

const EventRow: React.FC<{ ev: OsEvent; isNew: boolean; stepNum?: number }> = ({ ev, isNew, stepNum }) => {
  const color = EVENT_COLORS[ev.type] || '#6b7280';
  const timeStr = new Date(ev.timestamp).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  return (
    <div style={{
      display: 'grid', gridTemplateColumns: stepNum != null ? '28px 26px 54px 66px 1fr 52px' : '26px 54px 66px 1fr 52px', gap: 8, alignItems: 'center',
      padding: '7px 12px', borderRadius: 8, marginBottom: 3,
      background: isNew ? `${color}18` : 'transparent',
      borderLeft: `3px solid ${ev.success ? color : '#EF4444'}`,
      transition: 'background 1.5s ease', fontSize: 12,
    }}>
      {stepNum != null && (
        <span style={{ fontSize: 9, color: '#4b5563', fontFamily: 'monospace', fontWeight: 700, textAlign: 'right' as const }}>
          {String(stepNum).padStart(2, '0')}
        </span>
      )}
      <span style={{ fontSize: 14, textAlign: 'center' as const }}>{ev.icon}</span>
      <span style={{ color: '#6b7280', fontFamily: 'monospace', fontSize: 10 }}>{timeStr}</span>
      <span style={{ background: `${color}22`, color, padding: '2px 5px', borderRadius: 4, fontWeight: 700, fontSize: 10, textAlign: 'center' as const }}>
        {UNIT_LABELS[ev.unit] || ev.unit}
      </span>
      <span style={{ color: ev.success ? '#E5E7EB' : '#FCA5A5', fontSize: 12, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' as const }}>{ev.detail}</span>
      <span style={{ color: '#6b7280', fontSize: 10, textAlign: 'right' as const, fontFamily: 'monospace' }}>P{ev.pid}</span>
    </div>
  );
};

/* ─── Main ─── */
const AdminDashboard: React.FC = () => {
  const navigate = useNavigate();
  const [metrics, setMetrics] = useState<OsMetrics>({
    fcfsQueueSize: 0, sjfQueueSize: 0, roundRobinQueueSize: 0, priorityQueueSize: 0,
    activeReaders: 0, totalReaderSessions: 0, peakConcurrentReaders: 0,
  });
  const [events, setEvents] = useState<OsEvent[]>([]);
  const [newIds, setNewIds]  = useState<Set<number>>(new Set());
  const [scheduler, setScheduler]     = useState('FCFS');
  const [schedChanging, setSchedChanging] = useState(false);
  const [autoMode, setAutoMode]       = useState(true);
  const [lastReason, setLastReason]   = useState('');
  const [connected, setConnected]     = useState(true);
  const [uptime, setUptime]           = useState(0);
  const [lastUpdated, setLastUpdated] = useState('');
  const [activePanel, setActivePanel] = useState<'live' | 'schedulers' | 'metrics'>('live');
  const [paused, setPaused]           = useState(false);
  const [filterType, setFilterType]   = useState('ALL');
  const [orderNewest, setOrderNewest] = useState(false); // false = newest at BOTTOM (chronological)
  const prevTs = useRef<Set<number>>(new Set());

  const token = localStorage.getItem('jwt_token');
  // Stable ref so useEffect deps don't trigger infinite loop
  const authH = useRef({ headers: { Authorization: `Bearer ${token}` } });

  useEffect(() => {
    if (!token) { navigate('/admin-login'); return; }
    const fetchAll = async () => {
      try {
        const [mRes, sRes, eRes] = await Promise.all([
          axios.get(API_ENDPOINTS.ADMIN_MONITOR,              authH.current),
          axios.get(API_ENDPOINTS.ADMIN_SCHEDULER,            authH.current),
          axios.get(`${API_ENDPOINTS.LIVE_EVENTS}?limit=100`, authH.current),
        ]);
        setMetrics(mRes.data);
        const sd = sRes.data;
        setScheduler(sd.scheduler ?? 'FCFS');
        setAutoMode(sd.autoMode ?? true);
        setLastReason(sd.lastReason ?? '');
        if (!paused) {
          const evs: OsEvent[] = eRes.data;
          const fresh = new Set(evs.filter(e => !prevTs.current.has(e.timestamp)).map(e => e.timestamp));
          setNewIds(fresh);
          setEvents(evs);
          evs.forEach(e => prevTs.current.add(e.timestamp));
          setTimeout(() => setNewIds(new Set()), 2000);
        }
        setConnected(true);
        setLastUpdated(new Date().toLocaleTimeString());
      } catch { setConnected(false); }
    };
    fetchAll();
    const poll   = setInterval(fetchAll, 400);   // 400ms — read lock held 300ms, >75% catch rate
    const uptick = setInterval(() => setUptime(u => u + 1), 1000);
    return () => { clearInterval(poll); clearInterval(uptick); };
  }, [navigate, token, paused]); // eslint-disable-line

  const changeScheduler = async (s: string) => {
    setSchedChanging(true);
    try {
      await axios.post(API_ENDPOINTS.ADMIN_SCHEDULER, { scheduler: s }, authH.current);
      setScheduler(s);
      setAutoMode(false);
    } finally { setSchedChanging(false); }
  };
  const toggleAutoMode = async () => {
    setSchedChanging(true);
    try {
      const res = await axios.post(API_ENDPOINTS.ADMIN_SCHEDULER_AUTO, { auto: !autoMode }, authH.current);
      setAutoMode(res.data.autoMode ?? !autoMode);
    } finally { setSchedChanging(false); }
  };
  const clearEvents   = async () => { await axios.post(API_ENDPOINTS.LIVE_EVENTS_CLEAR, {}, authH.current); setEvents([]); prevTs.current.clear(); };
  const handleLogout  = () => { localStorage.clear(); navigate('/admin-login'); };

  const fmtUp = `${String(Math.floor(uptime / 3600)).padStart(2,'0')}:${String(Math.floor((uptime % 3600)/60)).padStart(2,'0')}:${String(uptime % 60).padStart(2,'0')}`;
  const mx    = metrics.mutexState || { isLocked: false, lockedByPid: null };
  const totalJobsDispatched = metrics.totalJobsDispatched ?? 0;

  // Map scheduler key → correct metrics field
  const getQueueSize = (key: string) => {
    if (key === 'FCFS')     return metrics.fcfsQueueSize ?? 0;
    if (key === 'SJF')      return metrics.sjfQueueSize ?? 0;
    if (key === 'RR')       return metrics.roundRobinQueueSize ?? 0;
    if (key === 'PRIORITY') return metrics.priorityQueueSize ?? 0;
    return 0;
  };

  const displayed = (() => {
    const filtered = filterType === 'ALL' ? events : events.filter(e => e.type === filterType);
    // Backend sends newest-first; flip to oldest-first (chronological) by default
    return orderNewest ? filtered : [...filtered].reverse();
  })();

  // Auto-scroll ref for chronological view
  const feedEndRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!orderNewest && feedEndRef.current) {
      feedEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [events.length, orderNewest]);

  /* Aggregated stats derived from real events (no simulation) */
  const countOf  = (type: string) => events.filter(e => e.type === type).length;
  const successOf = (type: string) => events.filter(e => e.type === type && e.success).length;
  const tlbTotal   = countOf('TLB');
  const tlbHits    = events.filter(e => e.type === 'TLB' && e.detail.includes('hit')).length;
  const tlbHitRate = tlbTotal > 0 ? Math.round((tlbHits / tlbTotal) * 100) : 0;
  const mutexLocks = events.filter(e => e.type === 'MUTEX' && e.detail.includes('acquiring')).length;
  const attempted  = events.filter(e => e.type === 'BOOKING' && e.detail.includes('New booking request')).length;
  const confirmed  = events.filter(e => e.type === 'BOOKING' && e.detail.includes('CONFIRMED')).length;
  const fileWrites = events.filter(e => e.type === 'FILE_IO' && e.detail.includes('Writing')).length;
  const memAllocs  = events.filter(e => e.type === 'MEMORY' && (e.detail.includes('Allocating') || e.detail.includes('allocated'))).length;
  const bankerChecks = countOf('BANKER');

  /* Per-type event stats for sidebar */
  const typeSummary = Object.entries(EVENT_COLORS)
    .map(([t, c]) => ({ type: t, color: c, count: countOf(t) }))
    .filter(s => s.count > 0);

  /* ── Panels ── */
  const PANELS = [
    { id: 'live' as const,       label: '📡 Live OS Feed',      active: '#10B981' },
    { id: 'schedulers' as const, label: '⚙️ Schedulers & Sync', active: '#3B82F6' },
    { id: 'metrics' as const,    label: '📊 OS Metrics',        active: '#8B5CF6' },
  ];

  return (
    <div style={{ minHeight: '100vh', background: '#080A14', color: '#F0F2FF', fontFamily: "'Inter',system-ui,sans-serif" }}>
      {/* Grid background */}
      <div style={{ position: 'fixed', inset: 0, backgroundImage: 'linear-gradient(rgba(108,99,255,0.03) 1px,transparent 1px),linear-gradient(90deg,rgba(108,99,255,0.03) 1px,transparent 1px)', backgroundSize: '48px 48px', pointerEvents: 'none', zIndex: 0 }} />

      {/* Navbar */}
      <nav style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 28px', height: 60, background: 'rgba(8,10,20,0.96)', borderBottom: '1px solid rgba(255,255,255,0.08)', backdropFilter: 'blur(16px)', position: 'sticky', top: 0, zIndex: 200 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <span style={{ fontSize: 26 }}>🚄</span>
          <div>
            <div style={{ fontSize: 15, fontWeight: 800, fontFamily: "'Space Grotesk',sans-serif" }}>RailBooker OS</div>
            <div style={{ fontSize: 11, color: '#6b7280' }}>Admin — Live OS Monitor</div>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginLeft: 16, padding: '3px 10px', background: connected ? '#10B98115' : '#EF444415', border: `1px solid ${connected ? '#10B98133' : '#EF444433'}`, borderRadius: 99 }}>
            <span style={{ width: 6, height: 6, borderRadius: '50%', background: connected ? '#10B981' : '#EF4444', display: 'inline-block' }} />
            <span style={{ fontSize: 11, color: connected ? '#10B981' : '#EF4444', fontWeight: 600 }}>{connected ? `Connected · ${lastUpdated}` : 'Disconnected'}</span>
          </div>
        </div>

        {/* Panel tabs */}
        <div style={{ display: 'flex', gap: 4, background: '#0a0d1a', border: '1px solid #1f2937', borderRadius: 10, padding: 4 }}>
          {PANELS.map(p => (
            <button key={p.id} onClick={() => setActivePanel(p.id)} style={{ padding: '7px 16px', border: 'none', borderRadius: 7, cursor: 'pointer', fontWeight: 700, fontSize: 12, background: activePanel === p.id ? p.active : 'transparent', color: activePanel === p.id ? '#fff' : '#6b7280', transition: 'all 0.2s' }}>
              {p.label}
            </button>
          ))}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 8, padding: '4px 12px', textAlign: 'center' as const }}>
            <div style={{ fontSize: 9, color: '#6b7280', fontWeight: 700, textTransform: 'uppercase' as const, letterSpacing: '0.06em' }}>Uptime</div>
            <div style={{ fontSize: 13, fontWeight: 800, fontFamily: 'monospace', color: '#F0F2FF' }}>{fmtUp}</div>
          </div>
          <button onClick={handleLogout} style={{ background: '#ef444415', color: '#ef4444', border: '1px solid #ef444433', borderRadius: 8, padding: '7px 14px', fontSize: 12, fontWeight: 700, cursor: 'pointer' }}>Exit</button>
        </div>
      </nav>

      <div style={{ maxWidth: 1440, margin: '0 auto', padding: '20px 24px', position: 'relative', zIndex: 1 }}>

        {/* Stat bar — always visible, all real data */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(6,1fr)', gap: 10, marginBottom: 20 }}>
          <StatPill label="Active Scheduler" value={scheduler}                          color="#6C63FF" icon="⚙️" />
          <StatPill label="Jobs Dispatched"   value={totalJobsDispatched}                        color="#3B82F6" icon="🚀" />
          <StatPill label="Active Readers"    value={metrics.activeReaders}                       color="#10B981" icon="👓" />
          <StatPill label="Mutex"             value={mx.isLocked ? `LOCKED P${mx.lockedByPid}` : 'OPEN'} color={mx.isLocked ? '#EF4444' : '#10B981'} icon={mx.isLocked ? '🔒' : '🔓'} />
          <StatPill label="Buffer Items"      value={metrics.ticketsInBuffer ?? 0}       color="#F59E0B" icon="📦" />
          <StatPill label="OS Events"         value={events.length}                      color="#8B5CF6" icon="📡" />
        </div>

        {/* ══ LIVE OS FEED ══ */}
        {activePanel === 'live' && (
          <div style={{ animation: 'fadeUp 0.3s ease-out', display: 'grid', gridTemplateColumns: '1fr 300px', gap: 16 }}>

            {/* Feed */}
            <div style={{ background: '#0f1423', border: '1px solid #1f2937', borderRadius: 16, overflow: 'hidden' }}>
              <div style={{ padding: '14px 18px', borderBottom: '1px solid #1f2937', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div>
                  <div style={{ fontWeight: 800, fontSize: 15, display: 'flex', alignItems: 'center', gap: 8 }}>
                    📡 Real-Time OS Event Feed
                    {!paused && events.length > 0 && <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#10B981', display: 'inline-block' }} />}
                  </div>
                  <div style={{ fontSize: 12, color: '#6b7280', marginTop: 2 }}>
                    Events fired by real user bookings — not simulations
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 6 }}>
                  <select value={filterType} onChange={e => setFilterType(e.target.value)} style={{ padding: '5px 8px', background: '#111827', border: '1px solid #374151', borderRadius: 6, color: '#F0F2FF', fontSize: 11 }}>
                    <option value="ALL">All Events</option>
                    {Object.keys(EVENT_COLORS).map(t => <option key={t} value={t}>{t}</option>)}
                  </select>
                   <button onClick={() => setOrderNewest(o => !o)} style={{ padding: '5px 10px', background: '#06B6D422', color: '#06B6D4', border: '1px solid #06B6D444', borderRadius: 6, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                    {orderNewest ? '↑ Newest First' : '↓ Oldest First'}
                  </button>
                  <button onClick={() => setPaused(p => !p)} style={{ padding: '5px 10px', background: paused ? '#F59E0B22' : '#6C63FF22', color: paused ? '#F59E0B' : '#8B84FF', border: `1px solid ${paused ? '#F59E0B44' : '#6C63FF44'}`, borderRadius: 6, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                    {paused ? '▶ Resume' : '⏸ Pause'}
                  </button>
                  <button onClick={clearEvents} style={{ padding: '5px 10px', background: '#EF444415', color: '#EF4444', border: '1px solid #EF444433', borderRadius: 6, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>🗑 Clear</button>
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '26px 54px 66px 1fr 52px', gap: 8, padding: '5px 12px', background: '#0a0d1a', fontSize: 10, color: '#4b5563', fontWeight: 700, textTransform: 'uppercase' as const, letterSpacing: '0.06em' }}>
                <span /><span>Time</span><span>Unit</span><span>OS Event (triggered by real booking)</span><span>PID</span>
              </div>

              <div style={{ height: 500, overflowY: 'auto', padding: '8px 10px' }}>
                {displayed.length === 0 ? (
                  <div style={{ display: 'flex', flexDirection: 'column' as const, alignItems: 'center', justifyContent: 'center', height: '100%', color: '#4b5563' }}>
                    <div style={{ fontSize: 48, marginBottom: 12 }}>📡</div>
                    <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 6, color: '#6b7280' }}>Waiting for real booking events…</div>
                    <div style={{ fontSize: 13, textAlign: 'center' as const, maxWidth: 300, lineHeight: 1.6 }}>
                      Go to the <strong style={{ color: '#6C63FF' }}>user dashboard</strong>, book a ticket, and every OS mechanism that runs will appear here live.
                    </div>
                  </div>
                ) : displayed.map((ev, i) => (
                  <EventRow key={`${ev.timestamp}-${i}`} ev={ev} isNew={newIds.has(ev.timestamp)} stepNum={!orderNewest ? i + 1 : undefined} />
                ))}
                <div ref={feedEndRef} />
              </div>
            </div>

            {/* Right sidebar */}
            <div style={{ display: 'flex', flexDirection: 'column' as const, gap: 12 }}>
              {/* Event breakdown */}
              <div style={{ background: '#0f1423', border: '1px solid #1f2937', borderRadius: 14, padding: 16 }}>
                <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 12 }}>📊 Event Breakdown</div>
                {typeSummary.length === 0
                  ? <div style={{ fontSize: 12, color: '#4b5563' }}>No events yet</div>
                  : typeSummary.map(s => (
                    <div key={s.type} style={{ marginBottom: 8 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 3 }}>
                        <span style={{ fontSize: 12, color: s.color, fontWeight: 700 }}>{s.type}</span>
                        <span style={{ fontSize: 12, color: '#9BA3BF', fontFamily: 'monospace' }}>{s.count}</span>
                      </div>
                      <div style={{ height: 3, background: '#1f2937', borderRadius: 3 }}>
                        <div style={{ height: '100%', width: `${Math.min(100, (s.count / Math.max(events.length, 1)) * 100)}%`, background: s.color, borderRadius: 3, transition: 'width 0.5s' }} />
                      </div>
                    </div>
                  ))
                }
              </div>

              {/* PCB transitions */}
              <div style={{ background: '#0f1423', border: '1px solid #1f2937', borderRadius: 14, padding: 16, flex: 1 }}>
                <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 10 }}>🗂 PCB State Transitions</div>
                <div style={{ maxHeight: 220, overflowY: 'auto' }}>
                  {(metrics.recentProcesses || []).slice().reverse().slice(0, 15).map((p, i) => {
                    const col = p.newState === 'TERMINATED' ? '#10B981' : p.newState === 'RUNNING' ? '#3B82F6' : '#F59E0B';
                    return (
                      <div key={i} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '5px 0', borderBottom: '1px solid #1f2937', fontSize: 11 }}>
                        <span style={{ color: '#8B5CF6', fontWeight: 700, fontFamily: 'monospace' }}>P{p.pid}</span>
                        <span style={{ color: '#6b7280' }}>{p.type}</span>
                        <span style={{ color: col, fontWeight: 700 }}>{p.newState}</span>
                      </div>
                    );
                  })}
                  {(!metrics.recentProcesses || metrics.recentProcesses.length === 0) && <div style={{ fontSize: 12, color: '#4b5563' }}>No processes yet</div>}
                </div>
              </div>

              {/* Flow legend */}
              <div style={{ background: 'linear-gradient(135deg,#6C63FF11,#06B6D411)', border: '1px solid #6C63FF33', borderRadius: 14, padding: 14 }}>
                <div style={{ fontSize: 11, fontWeight: 700, color: '#8B84FF', marginBottom: 8 }}>🔗 One Booking Fires:</div>
                {[
                  ['⚙️', 'CPU Scheduler',  'dispatches booking process'],
                  ['🔒', 'Mutex Lock',      'guards seat table'],
                  ['🧠', 'Memory Alloc',   '64KB session block'],
                  ['✅', "Banker's Algo",   'deadlock safety check'],
                  ['💿', 'File I/O',        'indexed record → disk'],
                  ['⚡', 'TLB Lookup',      'page-table cache hit'],
                ].map(([icon, name, detail]) => (
                  <div key={String(name)} style={{ display: 'flex', gap: 8, marginBottom: 5 }}>
                    <span style={{ fontSize: 13 }}>{icon}</span>
                    <div>
                      <span style={{ fontSize: 11, fontWeight: 700, color: '#E5E7EB' }}>{name}</span>
                      <span style={{ fontSize: 10, color: '#6b7280' }}> — {detail}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* ══ SCHEDULERS & SYNC ══ */}
        {activePanel === 'schedulers' && (
          <div style={{ animation: 'fadeUp 0.3s ease-out', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>

            {/* CPU Scheduling */}
            <div style={{ background: '#0f1423', border: '1px solid #1f2937', borderRadius: 16, padding: 22 }}>
              {/* Header + AUTO/MANUAL toggle */}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 }}>
                <div style={{ borderLeft: '3px solid #3B82F6', paddingLeft: 12 }}>
                  <div style={{ fontWeight: 800, fontSize: 15 }}>⚙️ CPU Scheduling — Unit III</div>
                  <div style={{ fontSize: 12, color: '#6b7280', marginTop: 2 }}>
                    {autoMode
                      ? 'AUTO mode — system selects best algorithm per booking context'
                      : 'MANUAL mode — admin-chosen algorithm for all bookings'}
                  </div>
                </div>
                {/* AUTO / MANUAL pill toggle */}
                <button
                  onClick={toggleAutoMode}
                  disabled={schedChanging}
                  style={{
                    display: 'flex', alignItems: 'center', gap: 6,
                    background: autoMode ? '#10B98122' : '#F59E0B22',
                    color: autoMode ? '#10B981' : '#F59E0B',
                    border: `1.5px solid ${autoMode ? '#10B98144' : '#F59E0B44'}`,
                    borderRadius: 20, padding: '6px 14px', fontWeight: 800, fontSize: 12, cursor: 'pointer',
                    transition: 'all 0.2s'
                  }}>
                  <span style={{ width: 8, height: 8, borderRadius: '50%', background: autoMode ? '#10B981' : '#F59E0B', display: 'inline-block' }} />
                  {autoMode ? '⚡ AUTO' : '🔧 MANUAL'}
                </button>
              </div>

              {/* Decision reasoning banner — only in AUTO mode */}
              {autoMode && (
                <div style={{
                  background: '#3B82F611', border: '1px solid #3B82F633',
                  borderRadius: 10, padding: '10px 14px', marginBottom: 14
                }}>
                  <div style={{ fontSize: 10, color: '#6b7280', fontWeight: 700, marginBottom: 4, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                    🤖 Last Auto-Decision
                  </div>
                  <div style={{ fontSize: 12, color: '#E5E7EB', lineHeight: 1.5 }}>
                    <span style={{ color: '#3B82F6', fontWeight: 800 }}>[AUTO→{scheduler}]</span>{' '}
                    {lastReason || 'Book a ticket to see the auto-selection reasoning'}
                  </div>
                </div>
              )}

              {/* Decision rules table — always visible */}
              <div style={{ background: '#0a0d1a', border: '1px solid #1f2937', borderRadius: 10, padding: '10px 14px', marginBottom: 14 }}>
                <div style={{ fontSize: 10, color: '#6b7280', fontWeight: 700, marginBottom: 8, textTransform: 'uppercase', letterSpacing: '0.05em' }}>📋 Auto-Selection Rules</div>
                {[
                  { trigger: 'Tatkal booking',              algo: 'PRIORITY', color: '#EF4444', reason: 'High-priority process preempts normal queue' },
                  { trigger: '≥ 3 concurrent bookings',     algo: 'RR',       color: '#10B981', reason: 'Time-sharing prevents convoy effect' },
                  { trigger: 'Train is 85%+ full',          algo: 'SJF',      color: '#F59E0B', reason: 'Drain shortest jobs on scarce resource' },
                  { trigger: 'Single-seat booking (1)',     algo: 'SJF',      color: '#F59E0B', reason: 'Shortest burst → min avg waiting time' },
                  { trigger: 'Group booking (≥ 5 seats)',   algo: 'FCFS',     color: '#6C63FF', reason: 'Arrival order, no long-job penalty' },
                  { trigger: 'Standard booking (2-4)',      algo: 'FCFS',     color: '#6C63FF', reason: 'Simple, fair, no starvation' },
                ].map(r => (
                  <div key={r.trigger} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '4px 0', borderBottom: '1px solid #1a1f2e', fontSize: 11 }}>
                    <span style={{ color: '#4b5563', fontSize: 10, minWidth: 160 }}>{r.trigger}</span>
                    <span style={{ background: `${r.color}22`, color: r.color, border: `1px solid ${r.color}44`, borderRadius: 4, padding: '1px 6px', fontWeight: 800, fontSize: 10, minWidth: 60, textAlign: 'center' as const }}>{r.algo}</span>
                    <span style={{ color: '#4b5563', fontSize: 10 }}>{r.reason}</span>
                  </div>
                ))}
              </div>

              {/* Algorithm cards */}
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                {SCHEDULERS.map(sc => {
                  const qSize = getQueueSize(sc.key);
                  const isActive = scheduler === sc.key;
                  const isAutoSelected = autoMode && isActive;
                  return (
                    <button key={sc.key}
                      onClick={() => changeScheduler(sc.key)}
                      disabled={schedChanging}
                      title={autoMode ? 'Click to switch to MANUAL and force this algorithm' : ''}
                      style={{
                        padding: '14px 12px',
                        border: `1.5px solid ${isActive ? sc.color : '#374151'}`,
                        background: isActive ? `${sc.color}18` : 'rgba(255,255,255,0.02)',
                        borderRadius: 12, cursor: 'pointer', textAlign: 'left' as const,
                        position: 'relative' as const, transition: 'all 0.2s'
                      }}>
                      {/* ACTIVE badge */}
                      {isActive && (
                        <span style={{
                          position: 'absolute' as const, top: 6, right: 8,
                          background: isAutoSelected ? '#10B981' : sc.color,
                          color: '#000', fontSize: 9, fontWeight: 800,
                          padding: '1px 5px', borderRadius: 4
                        }}>
                          {isAutoSelected ? '⚡ AUTO' : 'MANUAL'}
                        </span>
                      )}
                      <div style={{ fontSize: 22, marginBottom: 4 }}>{sc.icon}</div>
                      <div style={{ fontWeight: 800, fontSize: 13, color: isActive ? sc.color : '#F0F2FF' }}>{sc.label}</div>
                      <div style={{ fontSize: 10, color: '#6b7280', marginBottom: 3 }}>{sc.ds}</div>
                      <div style={{ fontSize: 10, color: '#4b5563', marginBottom: 6, lineHeight: 1.3 }}>{sc.desc}</div>
                      {/* Queue depth dots */}
                      <div style={{ display: 'flex', gap: 3, flexWrap: 'wrap' as const, minHeight: 10 }}>
                        {Array.from({ length: Math.min(qSize, 12) }).map((_, j) => (
                          <div key={j} style={{ width: 7, height: 7, background: sc.color, borderRadius: 2, opacity: 0.7 }} />
                        ))}
                        {qSize === 0 && <span style={{ fontSize: 9, color: '#4b5563', fontStyle: 'italic' }}>Queue empty</span>}
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Synchronisation */}
            <div style={{ display: 'flex', flexDirection: 'column' as const, gap: 12 }}>
              <div style={{ background: '#0f1423', border: '1px solid #1f2937', borderRadius: 16, padding: 22 }}>
                <div style={{ borderLeft: '3px solid #F59E0B', paddingLeft: 12, marginBottom: 18 }}>
                  <div style={{ fontWeight: 800, fontSize: 15 }}>🔒 Synchronization — Unit II</div>
                  <div style={{ fontSize: 12, color: '#6b7280', marginTop: 2 }}>Live state from concurrent access during real bookings</div>
                </div>
                {[
                  {
                    label: 'Seat Table Mutex (CriticalSectionGuard)',
                    sub: 'Prevents double-booking under concurrent requests',
                    big: mx.isLocked ? `🔴 LOCKED — P${mx.lockedByPid} in critical section` : '🟢 UNLOCKED — Seat table open',
                    color: mx.isLocked ? '#EF4444' : '#10B981',
                  },
                  {
                    label: 'Readers-Writers — Train Availability',
                    sub: 'Multiple users can read concurrently; bookings wait for exclusive write lock',
                    big: `${metrics.activeReaders} concurrent reader${metrics.activeReaders !== 1 ? 's' : ''} active`,
                    extra: `Total sessions: ${metrics.totalReaderSessions ?? 0}  ·  Peak concurrent: ${metrics.peakConcurrentReaders ?? 0}`,
                    color: '#10B981',
                  },
                  {
                    label: 'Producer-Consumer Buffer (Ticket Queue)',
                    sub: 'Booking requests produced by users, consumed by scheduler threads',
                    big: `${metrics.ticketsInBuffer ?? 0} items in buffer`,
                    color: '#F59E0B',
                  },
                ].map((item: any) => (
                  <div key={item.label} style={{ padding: '14px 16px', background: `${item.color}08`, border: `1px solid ${item.color}22`, borderRadius: 10, marginBottom: 10 }}>
                    <div style={{ fontSize: 11, color: '#6b7280', marginBottom: 4 }}>{item.label}</div>
                    <div style={{ fontSize: 10, color: '#4b5563', marginBottom: 6 }}>{item.sub}</div>
                    <div style={{ fontSize: 15, fontWeight: 800, color: item.color }}>{item.big}</div>
                    {item.extra && <div style={{ fontSize: 10, color: '#6b7280', marginTop: 5, fontFamily: 'monospace' }}>{item.extra}</div>}
                  </div>
                ))}
              </div>
            </div>

            {/* Dining Philosophers — full width */}
            <div style={{ gridColumn: '1 / -1', background: '#0f1423', border: '1px solid #1f2937', borderRadius: 16, padding: 22 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 }}>
                <div style={{ borderLeft: '3px solid #8B5CF6', paddingLeft: 12 }}>
                  <div style={{ fontWeight: 800, fontSize: 15 }}>🍽️ Dining Philosophers — Real Concurrent Booking Contention</div>
                  <div style={{ fontSize: 12, color: '#6b7280', marginTop: 2 }}>
                    4 trains = 4 philosophers. Each booking acquires 2 adjacent platform locks before accessing the DB.
                    Asymmetric rule (Train 3 picks right-first) prevents circular wait. <strong style={{ color: '#10B981' }}>Updates during real concurrent bookings — not a simulation.</strong>
                  </div>
                </div>
                <div style={{ fontSize: 11, color: '#4b5563', background: '#1a1f2e', border: '1px solid #374151', borderRadius: 8, padding: '8px 14px', textAlign: 'center' as const }}>
                  <div style={{ fontSize: 9, color: '#6b7280', marginBottom: 2 }}>PROTOCOL</div>
                  <div style={{ color: '#8B5CF6', fontWeight: 700 }}>Asymmetric Ordering</div>
                  <div style={{ fontSize: 10, color: '#4b5563', marginTop: 2 }}>Train 0-2: left→right</div>
                  <div style={{ fontSize: 10, color: '#F59E0B', marginTop: 1 }}>Train 3: right→left ✦</div>
                </div>
              </div>

              {/* 4 Train/Philosopher cards */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 16 }}>
                {['0','1','2','3'].map(id => {
                  const state   = (metrics.philosophers || {})[id] || 'IDLE';
                  const isEating  = state.includes('EATING');
                  const isHungry  = state.includes('HUNGRY');
                  const isAsymm   = id === '3';
                  const color  = isEating ? '#10B981' : isHungry ? '#F59E0B' : '#374151';
                  const emoji  = isEating ? '🚂' : isHungry ? '🤤' : '😴';
                  const trains = ['Mumbai Rajdhani', 'Shatabdi Express', 'Duronto Express', 'Garib Rath Exp.'];
                  const tNum   = ['EXP-12951','EXP-12952','EXP-12953','EXP-12954'];
                  const left   = parseInt(id);
                  const right  = (parseInt(id) + 1) % 4;
                  const lockOrder = isAsymm ? `Lock ${right} → Lock ${left}` : `Lock ${left} → Lock ${right}`;
                  return (
                    <div key={id} style={{ textAlign: 'center' as const, padding: '18px 14px', background: `${color}11`, border: `1.5px solid ${color}44`, borderRadius: 12, position: 'relative' as const }}>
                      {isAsymm && (
                        <div style={{ position: 'absolute' as const, top: 8, right: 8, fontSize: 9, background: '#F59E0B22', color: '#F59E0B', border: '1px solid #F59E0B44', borderRadius: 4, padding: '2px 5px', fontWeight: 700 }}>
                          REVERSED ✦
                        </div>
                      )}
                      <div style={{ fontSize: 30, marginBottom: 6 }}>{emoji}</div>
                      <div style={{ fontWeight: 800, fontSize: 13, color: '#E5E7EB' }}>Train {id}</div>
                      <div style={{ fontSize: 10, color: '#6b7280', marginBottom: 8 }}>{trains[parseInt(id)]}</div>
                      <div style={{ fontSize: 9, color: '#4b5563', fontFamily: 'monospace', marginBottom: 6 }}>{tNum[parseInt(id)]}</div>
                      <div style={{ display: 'inline-block', background: `${color}22`, color, border: `1px solid ${color}44`, borderRadius: 6, padding: '3px 8px', fontSize: 11, fontWeight: 700, marginBottom: 8 }}>
                        {isEating ? 'EATING' : isHungry ? 'HUNGRY' : 'THINKING'}
                      </div>
                      <div style={{ fontSize: 9, color: '#4b5563' }}>Needs: Lock {left} + Lock {right}</div>
                      <div style={{ fontSize: 9, color: '#6b7280', marginTop: 2 }}>Order: {lockOrder}</div>
                      <div style={{ fontSize: 10, color: '#6b7280', marginTop: 6, minHeight: 28, lineHeight: 1.4 }}>{
                        state === 'IDLE'
                          ? 'No booking in progress'
                          : state.split('—')[1]?.trim() || state
                      }</div>
                    </div>
                  );
                })}
              </div>

              {/* Platform Lock Ring */}
              <div style={{ background: '#0a0d1a', border: '1px solid #1f2937', borderRadius: 10, padding: '14px 18px' }}>
                <div style={{ fontSize: 11, color: '#6b7280', fontWeight: 700, marginBottom: 10, textTransform: 'uppercase' as const, letterSpacing: '0.05em' }}>
                  🔗 Platform Lock Ring (Circular Shared Resources)
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 0, justifyContent: 'center', flexWrap: 'wrap' as const }}>
                  {[0,1,2,3].map(i => {
                    // A lock is "in use" if any adjacent philosopher is EATING or HUNGRY
                    const stateL = (metrics.philosophers || {})[String(i)] || 'IDLE';
                    const stateR = (metrics.philosophers || {})[String((i + 3) % 4)] || 'IDLE';
                    const locked = stateL.includes('EATING') || stateR.includes('EATING') || stateL.includes('HUNGRY') || stateR.includes('HUNGRY');
                    const color  = locked ? '#EF4444' : '#10B981';
                    return (
                      <React.Fragment key={i}>
                        <div style={{ textAlign: 'center' as const }}>
                          <div style={{ fontSize: 18, marginBottom: 2 }}>{locked ? '🔒' : '🔓'}</div>
                          <div style={{ fontSize: 9, color, fontWeight: 700 }}>Lock {i}</div>
                          <div style={{ fontSize: 8, color: '#4b5563' }}>T{i}↔T{(i+3)%4}</div>
                        </div>
                        {i < 3 && <div style={{ flex: 1, height: 2, background: 'linear-gradient(90deg, #374151, #4b5563)', margin: '0 8px', minWidth: 24, marginBottom: 10 }} />}
                      </React.Fragment>
                    );
                  })}
                  <div style={{ flex: 1, height: 2, background: 'linear-gradient(90deg, #4b5563, #374151)', margin: '0 8px', minWidth: 24, marginBottom: 10 }} />
                  <div style={{ fontSize: 9, color: '#4b5563', alignSelf: 'flex-end', marginBottom: 10 }}>↩ (circular)</div>
                </div>
                <div style={{ fontSize: 11, color: '#4b5563', textAlign: 'center' as const, marginTop: 4 }}>
                  Book tickets on different trains <strong style={{ color: '#8B5CF6' }}>simultaneously</strong> to see lock contention live
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ══ OS METRICS (derived from real events, zero simulation) ══ */}
        {activePanel === 'metrics' && (
          <div style={{ animation: 'fadeUp 0.3s ease-out' }}>
            <div style={{ padding: '10px 16px', background: '#10B98111', border: '1px solid #10B98133', borderRadius: 10, marginBottom: 16, fontSize: 12, color: '#10B981' }}>
              ℹ All numbers below are derived from real OS events fired during actual user bookings — no synthetic data.
            </div>

            {/* Summary cards */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 12, marginBottom: 16 }}>
              {[
                { label: 'Total Bookings Attempted',    value: attempted,     color: '#10B981', icon: '🎫',  detail: `${confirmed} confirmed` },
                { label: 'Mutex Acquisitions',           value: mutexLocks,                  color: '#EF4444', icon: '🔒', detail: `on seat table` },
                { label: 'TLB Hit Rate',                 value: `${tlbHitRate}%`,            color: '#F59E0B', icon: '⚡', detail: `${tlbHits}/${tlbTotal} lookups` },
                { label: "Banker's Safety Checks",       value: bankerChecks,                color: '#F97316', icon: '✅', detail: `deadlock checks run` },
                { label: 'Memory Allocations',           value: memAllocs,                   color: '#8B5CF6', icon: '🧠', detail: `session blocks (64KB each)` },
                { label: 'File I/O Writes (Disk)',       value: fileWrites,                  color: '#06B6D4', icon: '💿', detail: `booking records persisted` },
                { label: 'Reader-Lock Acquisitions',     value: successOf('READER'),         color: '#3B82F6', icon: '👓', detail: `train table reads` },
                { label: 'Scheduler Dispatches',         value: countOf('SCHEDULER'),        color: '#6C63FF', icon: '⚙️', detail: `via ${scheduler}` },
              ].map(c => (
                <div key={c.label} style={{ background: '#0f1423', border: `1px solid ${c.color}33`, borderRadius: 14, padding: '18px 16px' }}>
                  <div style={{ fontSize: 26, marginBottom: 8 }}>{c.icon}</div>
                  <div style={{ fontSize: 28, fontWeight: 900, color: c.color, fontFamily: 'monospace', lineHeight: 1 }}>{c.value}</div>
                  <div style={{ fontSize: 12, fontWeight: 700, color: '#E5E7EB', margin: '6px 0 3px' }}>{c.label}</div>
                  <div style={{ fontSize: 11, color: '#6b7280' }}>{c.detail}</div>
                </div>
              ))}
            </div>

            {/* TLB breakdown */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <div style={{ background: '#0f1423', border: '1px solid #1f2937', borderRadius: 14, padding: 18 }}>
                <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 14 }}>⚡ TLB Performance (Unit V — Memory)</div>
                {[
                  { label: 'TLB Hits',  value: tlbHits,            color: '#10B981', pct: tlbTotal > 0 ? (tlbHits / tlbTotal) * 100 : 0 },
                  { label: 'TLB Misses',value: tlbTotal - tlbHits,  color: '#EF4444', pct: tlbTotal > 0 ? ((tlbTotal - tlbHits) / tlbTotal) * 100 : 0 },
                ].map(r => (
                  <div key={r.label} style={{ marginBottom: 12 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                      <span style={{ fontSize: 13, color: r.color, fontWeight: 700 }}>{r.label}</span>
                      <span style={{ fontSize: 13, fontFamily: 'monospace', color: '#9BA3BF' }}>{r.value} ({Math.round(r.pct)}%)</span>
                    </div>
                    <div style={{ height: 8, background: '#1f2937', borderRadius: 8 }}>
                      <div style={{ height: '100%', width: `${r.pct}%`, background: r.color, borderRadius: 8, transition: 'width 0.6s' }} />
                    </div>
                  </div>
                ))}
                <div style={{ marginTop: 10, padding: '8px 12px', background: '#F59E0B11', border: '1px solid #F59E0B33', borderRadius: 8, fontSize: 12, color: '#F59E0B' }}>
                  EMAT = {tlbHitRate}% × 10ns + {100 - tlbHitRate}% × 100ns = {Math.round(tlbHitRate * 0.1 + (100 - tlbHitRate) * 1)}ns effective access time
                </div>
              </div>

              {/* Booking pipeline breakdown */}
              <div style={{ background: '#0f1423', border: '1px solid #1f2937', borderRadius: 14, padding: 18 }}>
                <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 14 }}>🎫 Booking Pipeline (per ticket)</div>
                {[
                  { step: '1. Reader lock acquired',   unit: 'Unit II',  color: '#3B82F6' },
                  { step: '2. TLB lookup for seat map',unit: 'Unit V',   color: '#F59E0B' },
                  { step: '3. Scheduler dispatches',   unit: 'Unit III', color: '#6C63FF' },
                  { step: "4. Banker's safety check",  unit: 'Unit IV',  color: '#F97316' },
                  { step: '5. Mutex lock (write)',      unit: 'Unit II',  color: '#EF4444' },
                  { step: '6. Memory allocated',       unit: 'Unit V',   color: '#8B5CF6' },
                  { step: '7. Seat count updated',     unit: 'App',      color: '#10B981' },
                  { step: '8. File record written',    unit: 'Unit VI',  color: '#06B6D4' },
                  { step: '9. Mutex unlocked',         unit: 'Unit II',  color: '#374151' },
                  { step: '10. Memory freed',          unit: 'Unit V',   color: '#374151' },
                ].map((s, i) => (
                  <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '5px 0', borderBottom: '1px solid #1f2937' }}>
                    <span style={{ fontSize: 10, color: '#4b5563', fontFamily: 'monospace', minWidth: 16 }}>{i + 1}</span>
                    <span style={{ flex: 1, fontSize: 12, color: '#E5E7EB' }}>{s.step.replace(/^\d+\.\s/, '')}</span>
                    <span style={{ background: `${s.color}22`, color: s.color, padding: '2px 6px', borderRadius: 4, fontSize: 10, fontWeight: 700 }}>{s.unit}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default AdminDashboard;
