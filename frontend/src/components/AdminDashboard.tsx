import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { API_ENDPOINTS } from '../config/api';

interface OsMetrics {
    fcfsQueueSize: number;
    sjfQueueSize: number;
    roundRobinQueueSize: number;
    priorityQueueSize: number;
    activeReaders: number;
}

const SCHEDULERS = [
    { key: 'FCFS',     label: 'FCFS',     full: 'First Come First Serve', icon: '📋', color: '#3B82F6', type: 'Non-preemptive', ds: 'FIFO Queue',      metricKey: 'fcfsQueueSize' },
    { key: 'SJF',      label: 'SJF',      full: 'Shortest Job First',     icon: '⚡', color: '#F59E0B', type: 'Non-preemptive', ds: 'Priority Queue',  metricKey: 'sjfQueueSize' },
    { key: 'RR',       label: 'RR',       full: 'Round Robin (q=2s)',     icon: '🔄', color: '#8B5CF6', type: 'Preemptive',     ds: 'Circular Queue',  metricKey: 'roundRobinQueueSize' },
    { key: 'PRIORITY', label: 'PRIORITY', full: 'Priority Scheduling',    icon: '⭐', color: '#EF4444', type: 'Preemptive',     ds: 'Max-Heap',        metricKey: 'priorityQueueSize' },
] as const;

const OS_CONCEPTS = [
    { icon: '🔒', label: 'Process Control Block (PCB)',        check: true },
    { icon: '📋', label: 'FCFS, SJF, RR, Priority Scheduling', check: true },
    { icon: '🛡️', label: 'Mutex & Critical Sections',          check: true },
    { icon: '👥', label: 'Readers-Writers Problem',            check: true },
    { icon: '🔄', label: 'Producer-Consumer Problem',          check: true },
    { icon: '📊', label: '5-State Process Model',              check: true },
];

const AdminDashboard: React.FC = () => {
    const navigate = useNavigate();
    const adminName = localStorage.getItem('username') || 'Admin';

    const [metrics, setMetrics] = useState<OsMetrics>({ fcfsQueueSize: 0, sjfQueueSize: 0, roundRobinQueueSize: 0, priorityQueueSize: 0, activeReaders: 0 });
    const [isConnected, setIsConnected] = useState(true);
    const [currentScheduler, setCurrentScheduler] = useState('FCFS');
    const [schedulerChanging, setSchedulerChanging] = useState(false);
    const [uptimeSeconds, setUptimeSeconds] = useState(0);
    const [lastUpdated, setLastUpdated] = useState('');

    useEffect(() => {
        const token = localStorage.getItem('jwt_token');
        if (!token) { navigate('/login'); return; }

        const fetchData = async () => {
            try {
                const [metricsRes, schedulerRes] = await Promise.all([
                    axios.get(API_ENDPOINTS.ADMIN_MONITOR, { headers: { Authorization: `Bearer ${token}` } }),
                    axios.get(API_ENDPOINTS.ADMIN_SCHEDULER, { headers: { Authorization: `Bearer ${token}` } }),
                ]);
                setMetrics(metricsRes.data);
                setCurrentScheduler(schedulerRes.data.scheduler ?? schedulerRes.data);
                setIsConnected(true);
                setLastUpdated(new Date().toLocaleTimeString());
            } catch {
                setIsConnected(false);
            }
        };

        fetchData();
        const poll = setInterval(fetchData, 2000);
        const uptime = setInterval(() => setUptimeSeconds(s => s + 1), 1000);
        return () => { clearInterval(poll); clearInterval(uptime); };
    }, [navigate]);

    const changeScheduler = async (s: string) => {
        const token = localStorage.getItem('jwt_token');
        if (!token) return;
        setSchedulerChanging(true);
        try {
            await axios.post(API_ENDPOINTS.ADMIN_SCHEDULER, { scheduler: s }, { headers: { Authorization: `Bearer ${token}` } });
            setCurrentScheduler(s);
        } catch { /* silent */ }
        finally { setSchedulerChanging(false); }
    };

    const handleLogout = () => {
        localStorage.clear();
        navigate('/admin-login');
    };

    const totalProcesses = metrics.fcfsQueueSize + metrics.sjfQueueSize + metrics.roundRobinQueueSize + metrics.priorityQueueSize;
    const fmtUptime = `${Math.floor(uptimeSeconds / 60).toString().padStart(2, '0')}:${(uptimeSeconds % 60).toString().padStart(2, '0')}`;

    return (
        <div style={S.page}>
            {/* Grid background */}
            <div style={S.gridBg} />

            {/* Navbar */}
            <nav style={S.navbar}>
                <div style={S.navLeft}>
                    <div style={S.navLogo}>⚙️</div>
                    <div>
                        <div style={S.navBrand}>OS Core Console</div>
                        <div style={S.navStatus}>
                            <span style={{ ...S.statusDot, background: isConnected ? '#10B981' : '#EF4444', boxShadow: isConnected ? '0 0 6px #10B981' : '0 0 6px #EF4444' }} className={isConnected ? 'pulse' : ''} />
                            <span style={S.statusText}>{isConnected ? 'Connected' : 'Disconnected'}</span>
                            {lastUpdated && <span style={S.statusTime}>· Updated {lastUpdated}</span>}
                        </div>
                    </div>
                </div>
                <div style={S.navRight}>
                    <div style={S.uptimePill}>
                        <span style={{ color: '#9BA3BF', fontSize: 11 }}>UPTIME</span>
                        <span style={{ color: '#F0F2FF', fontWeight: 700, fontFamily: 'monospace', fontSize: 14 }}>{fmtUptime}</span>
                    </div>
                    <div style={S.adminPill}>
                        <span style={S.adminAvatar}>⚙</span>
                        <span style={S.adminName}>{adminName}</span>
                    </div>
                    <button onClick={handleLogout} className="btn-danger" style={{ fontSize: 13 }}>Sign Out</button>
                </div>
            </nav>

            <div style={S.main}>

                {/* Top stats row */}
                <div style={S.statsRow}>
                    {[
                        { icon: '📊', label: 'Total Processes', value: totalProcesses, color: '#6C63FF' },
                        { icon: '👥', label: 'Active Readers', value: metrics.activeReaders, color: '#10B981' },
                        { icon: '⭐', label: 'Priority Queue', value: metrics.priorityQueueSize, color: '#EF4444' },
                        { icon: '🔒', label: 'Mutex Status', value: 'Active', color: '#00D4AA' },
                    ].map(stat => (
                        <div key={stat.label} style={S.statCard} className="card">
                            <div style={S.statIconBox}>
                                <span style={{ fontSize: 22 }}>{stat.icon}</span>
                            </div>
                            <div>
                                <div style={{ ...S.statValue, color: stat.color }}>{stat.value}</div>
                                <div style={S.statLabel}>{stat.label}</div>
                            </div>
                        </div>
                    ))}
                </div>

                <div style={S.twoCol}>
                    {/* Scheduler config */}
                    <div style={S.schedulerConfig} className="card">
                        <div style={S.sectionHead}>
                            <h3 style={S.sectionTitle}>⚙️ Scheduler Configuration</h3>
                            <p style={S.sectionSub}>Select algorithm for all user booking processes</p>
                        </div>

                        <div style={S.schedulerBtns}>
                            {SCHEDULERS.map(sc => (
                                <button
                                    key={sc.key}
                                    onClick={() => changeScheduler(sc.key)}
                                    disabled={schedulerChanging}
                                    id={`scheduler-${sc.key.toLowerCase()}`}
                                    style={{
                                        ...S.schBtn,
                                        ...(currentScheduler === sc.key ? {
                                            ...S.schBtnActive,
                                            borderColor: sc.color,
                                            background: `${sc.color}18`,
                                        } : {}),
                                    }}
                                >
                                    <span style={S.schBtnIcon}>{sc.icon}</span>
                                    <span style={S.schBtnLabel}>{sc.label}</span>
                                    {currentScheduler === sc.key && (
                                        <span style={{ ...S.schBtnPill, background: sc.color }}>Active</span>
                                    )}
                                </button>
                            ))}
                        </div>

                        {/* Current scheduler detail */}
                        {(() => {
                            const active = SCHEDULERS.find(s => s.key === currentScheduler)!;
                            return (
                                <div style={{ ...S.activeSchDetail, borderColor: `${active.color}40`, background: `${active.color}0d` }}>
                                    <div style={{ fontSize: 28 }}>{active.icon}</div>
                                    <div>
                                        <div style={{ fontSize: 16, fontWeight: 700, color: active.color }}>{active.full}</div>
                                        <div style={{ fontSize: 13, color: '#9BA3BF', marginTop: 4 }}>
                                            {active.type} · {active.ds}
                                        </div>
                                    </div>
                                    <div style={{ ...S.activeSchDot, background: active.color, boxShadow: `0 0 10px ${active.color}` }} className="pulse" />
                                </div>
                            );
                        })()}
                    </div>

                    {/* Sync primitives */}
                    <div style={S.syncCard} className="card">
                        <div style={S.sectionHead}>
                            <h3 style={S.sectionTitle}>🔒 Synchronization Primitives</h3>
                            <p style={S.sectionSub}>OS-level thread safety mechanisms</p>
                        </div>
                        <div style={S.syncGrid}>
                            {[
                                { icon: '🔐', label: 'Mutex Lock',      value: 'Active',       color: '#10B981' },
                                { icon: '🚦', label: 'Semaphores',      value: 'Operational',  color: '#3B82F6' },
                                { icon: '👥', label: 'Active Readers',  value: metrics.activeReaders, color: '#F59E0B' },
                                { icon: '✍️', label: 'Writers Waiting', value: 0,              color: '#8B5CF6' },
                            ].map(item => (
                                <div key={item.label} style={S.syncItem}>
                                    <div style={S.syncIconBox}>{item.icon}</div>
                                    <div>
                                        <div style={S.syncLabel}>{item.label}</div>
                                        <div style={{ ...S.syncValue, color: item.color }}>{item.value}</div>
                                    </div>
                                </div>
                            ))}
                        </div>

                        {/* OS concepts */}
                        <div style={{ marginTop: 24, paddingTop: 20, borderTop: '1px solid rgba(255,255,255,0.07)' }}>
                            <div style={S.conceptsTitle}>📚 Implemented OS Concepts</div>
                            <div style={S.conceptsList}>
                                {OS_CONCEPTS.map(c => (
                                    <div key={c.label} style={S.conceptItem}>
                                        <span style={S.conceptCheck}>✅</span>
                                        <span style={{ fontSize: 13, color: '#9BA3BF' }}>{c.label}</span>
                                    </div>
                                ))}
                            </div>
                        </div>
                    </div>
                </div>

                {/* Scheduler queue cards */}
                <div style={S.schedulerGrid}>
                    {SCHEDULERS.map(sc => {
                        const queueSize = metrics[sc.metricKey as keyof OsMetrics] as number;
                        const fillPct = Math.min(queueSize * 10, 100);
                        return (
                            <div key={sc.key} style={{
                                ...S.schCard,
                                borderColor: currentScheduler === sc.key ? `${sc.color}50` : 'rgba(255,255,255,0.07)',
                            }} className="card">
                                <div style={S.schCardHeader}>
                                    <div style={{ ...S.schCardIcon, background: `${sc.color}1a`, border: `1px solid ${sc.color}33` }}>
                                        {sc.icon}
                                    </div>
                                    <div style={S.schCardTitle}>{sc.full}</div>
                                    {currentScheduler === sc.key && (
                                        <div style={{ ...S.activePill, background: `${sc.color}22`, color: sc.color, border: `1px solid ${sc.color}44` }}>
                                            ● Active
                                        </div>
                                    )}
                                </div>
                                <div style={S.schCardMetric}>
                                    <span style={{ ...S.schCardNum, color: sc.color }}>{queueSize}</span>
                                    <span style={S.schCardNumLabel}>processes</span>
                                </div>
                                <div className="progress-track">
                                    <div className="progress-fill" style={{ width: `${fillPct}%`, background: sc.color }} />
                                </div>
                                <div style={S.schCardInfo}>
                                    <span style={S.infoKey}>Type</span><span style={S.infoVal}>{sc.type}</span>
                                </div>
                                <div style={S.schCardInfo}>
                                    <span style={S.infoKey}>Data Structure</span><span style={S.infoVal}>{sc.ds}</span>
                                </div>
                            </div>
                        );
                    })}
                </div>

            </div>
        </div>
    );
};

const S: Record<string, React.CSSProperties> = {
    page: {
        minHeight: '100vh',
        background: '#080A14',
        color: '#F0F2FF',
        position: 'relative' as const,
    },
    gridBg: {
        position: 'fixed' as const, inset: 0,
        backgroundImage: `
            linear-gradient(rgba(108,99,255,0.035) 1px, transparent 1px),
            linear-gradient(90deg, rgba(108,99,255,0.035) 1px, transparent 1px)
        `,
        backgroundSize: '48px 48px',
        pointerEvents: 'none',
        zIndex: 0,
    },
    navbar: {
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '0 32px', height: 64,
        background: 'rgba(8,10,20,0.95)',
        borderBottom: '1px solid rgba(255,255,255,0.07)',
        backdropFilter: 'blur(16px)',
        position: 'sticky' as const, top: 0, zIndex: 100,
    },
    navLeft: { display: 'flex', alignItems: 'center', gap: 14 },
    navLogo: {
        width: 38, height: 38,
        background: 'rgba(108,99,255,0.2)',
        border: '1px solid rgba(108,99,255,0.3)',
        borderRadius: 10,
        display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 20,
    },
    navBrand: { fontSize: 16, fontWeight: 800, color: '#F0F2FF', fontFamily: "'Space Grotesk', sans-serif" },
    navStatus: { display: 'flex', alignItems: 'center', gap: 6, marginTop: 3 },
    statusDot: { width: 7, height: 7, borderRadius: '50%', display: 'inline-block' },
    statusText: { fontSize: 12, color: '#9BA3BF', fontWeight: 500 },
    statusTime: { fontSize: 11, color: '#5C6480' },
    navRight: { display: 'flex', alignItems: 'center', gap: 12 },
    uptimePill: {
        display: 'flex', flexDirection: 'column' as const, alignItems: 'center',
        background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)',
        borderRadius: 8, padding: '6px 14px', gap: 2,
    },
    adminPill: {
        display: 'flex', alignItems: 'center', gap: 8,
        background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)',
        borderRadius: 99, padding: '5px 14px 5px 8px',
    },
    adminAvatar: {
        width: 26, height: 26, borderRadius: '50%',
        background: 'rgba(108,99,255,0.3)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontSize: 13, color: '#8B84FF',
    },
    adminName: { fontSize: 13, fontWeight: 600, color: '#F0F2FF' },

    main: { maxWidth: 1400, margin: '0 auto', padding: '36px 28px', position: 'relative' as const, zIndex: 1 },

    statsRow: { display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16, marginBottom: 28 },
    statCard: {
        display: 'flex', alignItems: 'center', gap: 18, padding: '22px 24px',
        background: '#0D1020', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 16,
    },
    statIconBox: {
        width: 48, height: 48, borderRadius: 12,
        background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
    },
    statValue: { fontSize: 28, fontWeight: 900, fontFamily: "'Space Grotesk', sans-serif", lineHeight: 1 },
    statLabel: { fontSize: 12, color: '#9BA3BF', marginTop: 4 },

    twoCol: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20, marginBottom: 24 },
    schedulerConfig: { padding: '28px', background: '#0D1020', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 20 },
    sectionHead: { marginBottom: 24 },
    sectionTitle: { fontSize: 17, fontWeight: 800, color: '#F0F2FF', fontFamily: "'Space Grotesk', sans-serif", marginBottom: 6 },
    sectionSub: { fontSize: 13, color: '#9BA3BF' },
    schedulerBtns: { display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 10, marginBottom: 20 },
    schBtn: {
        display: 'flex', alignItems: 'center', gap: 10,
        padding: '12px 16px',
        background: 'rgba(255,255,255,0.03)',
        border: '1.5px solid rgba(255,255,255,0.08)',
        borderRadius: 12, cursor: 'pointer',
        transition: 'all 0.2s ease', position: 'relative' as const,
    },
    schBtnActive: {},
    schBtnIcon: { fontSize: 20 },
    schBtnLabel: { fontSize: 13, fontWeight: 700, color: '#F0F2FF' },
    schBtnPill: {
        position: 'absolute' as const, top: 6, right: 8,
        padding: '2px 8px', borderRadius: 99,
        fontSize: 10, fontWeight: 700, color: 'white',
    },
    activeSchDetail: {
        display: 'flex', alignItems: 'center', gap: 16,
        padding: '16px 20px', borderRadius: 12,
        border: '1px solid',
        position: 'relative' as const,
    },
    activeSchDot: {
        position: 'absolute' as const, right: 20, top: '50%', transform: 'translateY(-50%)',
        width: 10, height: 10, borderRadius: '50%',
    },

    syncCard: { padding: '28px', background: '#0D1020', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 20 },
    syncGrid: { display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 14 },
    syncItem: {
        display: 'flex', alignItems: 'center', gap: 14,
        padding: '14px 16px',
        background: 'rgba(255,255,255,0.025)',
        border: '1px solid rgba(255,255,255,0.06)',
        borderRadius: 12,
    },
    syncIconBox: { fontSize: 22 },
    syncLabel: { fontSize: 12, color: '#9BA3BF', marginBottom: 3 },
    syncValue: { fontSize: 16, fontWeight: 800, fontFamily: "'Space Grotesk', sans-serif" },
    conceptsTitle: { fontSize: 14, fontWeight: 700, color: '#F0F2FF', marginBottom: 12 },
    conceptsList: { display: 'flex', flexDirection: 'column' as const, gap: 8 },
    conceptItem: { display: 'flex', alignItems: 'center', gap: 10, padding: '8px 12px', background: 'rgba(255,255,255,0.025)', borderRadius: 8 },
    conceptCheck: { fontSize: 14 },

    schedulerGrid: { display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16 },
    schCard: { padding: '22px 24px', background: '#0D1020', borderRadius: 18, border: '1px solid', transition: 'border-color 0.3s' },
    schCardHeader: { display: 'flex', alignItems: 'center', gap: 12, marginBottom: 18, flexWrap: 'wrap' as const },
    schCardIcon: { width: 38, height: 38, borderRadius: 10, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18, flexShrink: 0 },
    schCardTitle: { fontSize: 12, fontWeight: 700, color: '#9BA3BF', flex: 1 },
    activePill: { padding: '3px 10px', borderRadius: 99, fontSize: 11, fontWeight: 700 },
    schCardMetric: { display: 'flex', alignItems: 'baseline', gap: 8, marginBottom: 14 },
    schCardNum: { fontSize: 40, fontWeight: 900, fontFamily: "'Space Grotesk', sans-serif", lineHeight: 1 },
    schCardNumLabel: { fontSize: 13, color: '#9BA3BF' },
    schCardInfo: { display: 'flex', justifyContent: 'space-between', padding: '7px 0', borderTop: '1px solid rgba(255,255,255,0.06)', fontSize: 12 },
    infoKey: { color: '#5C6480' },
    infoVal: { color: '#F0F2FF', fontWeight: 600 },
};

export default AdminDashboard;
