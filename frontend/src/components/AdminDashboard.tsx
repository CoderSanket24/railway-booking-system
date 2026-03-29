import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { API_ENDPOINTS } from '../config/api';

interface ProcessState {
    pid: number;
    oldState: string;
    newState: string;
    burstTime: number;
    type: string;
    timestamp: number;
}

interface OsMetrics {
    fcfsQueueSize: number;
    sjfQueueSize: number;
    roundRobinQueueSize: number;
    priorityQueueSize: number;
    activeReaders: number;
    recentProcesses?: ProcessState[];
    mutexState?: { isLocked: boolean; lockedByPid: number | null };
    ticketsInBuffer?: number;
    philosophers?: Record<string, string>;
}

const SCHEDULERS = [
    { key: 'FCFS',     label: 'FCFS',     full: 'First Come First Serve', icon: '📋', color: '#3B82F6', type: 'Non-preemptive', ds: 'FIFO Queue',      metricKey: 'fcfsQueueSize' },
    { key: 'SJF',      label: 'SJF',      full: 'Shortest Job First',     icon: '⚡', color: '#F59E0B', type: 'Non-preemptive', ds: 'Priority Queue',  metricKey: 'sjfQueueSize' },
    { key: 'RR',       label: 'RR',       full: 'Round Robin (q=2s)',     icon: '🔄', color: '#8B5CF6', type: 'Preemptive',     ds: 'Circular Queue',  metricKey: 'roundRobinQueueSize' },
    { key: 'PRIORITY', label: 'PRIORITY', full: 'Priority Scheduling',    icon: '⭐', color: '#EF4444', type: 'Preemptive',     ds: 'Max-Heap',        metricKey: 'priorityQueueSize' },
] as const;

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
        const poll = setInterval(fetchData, 1000); // Poll faster for live UI
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

    const startDiningPhilosophers = async () => {
        const token = localStorage.getItem('jwt_token');
        if (!token) return;
        try {
            await axios.post(API_ENDPOINTS.START_DINING_PHILOSOPHERS, {}, { headers: { Authorization: `Bearer ${token}` } });
        } catch { /* silent */ }
    }

    const handleLogout = () => {
        localStorage.clear();
        navigate('/admin-login');
    };

    const totalProcesses = metrics.fcfsQueueSize + metrics.sjfQueueSize + metrics.roundRobinQueueSize + metrics.priorityQueueSize;
    const fmtUptime = `${Math.floor(uptimeSeconds / 60).toString().padStart(2, '0')}:${(uptimeSeconds % 60).toString().padStart(2, '0')}`;

    const renderStateBadge = (state: string) => {
        const colors: any = {
            'NEW': { bg: '#3B82F620', col: '#3B82F6' },
            'READY': { bg: '#F59E0B20', col: '#F59E0B' },
            'RUNNING': { bg: '#8B5CF620', col: '#8B5CF6' },
            'WAITING': { bg: '#EF444420', col: '#EF4444' },
            'TERMINATED': { bg: '#10B98120', col: '#10B981' }
        };
        const st = colors[state] || { bg: '#ffffff20', col: '#fff' };
        return <span style={{ background: st.bg, color: st.col, padding: '4px 8px', borderRadius: '4px', fontSize: '11px', fontWeight: 'bold' }}>{state}</span>;
    }

    const mState = metrics.mutexState || { isLocked: false, lockedByPid: null };
    const philosopherStatus = metrics.philosophers || {};

    return (
        <div style={S.page}>
            <div style={S.gridBg} />
            
            {/* Navbar */}
            <nav style={S.navbar}>
                <div style={S.navLeft}>
                    <div style={S.navLogo}>🚄</div>
                    <div>
                        <div style={S.navBrand}>OS Live Concepts Tracker</div>
                        <div style={S.navStatus}>
                            <span style={{ ...S.statusDot, background: isConnected ? '#10B981' : '#EF4444', boxShadow: isConnected ? '0 0 6px #10B981' : '0 0 6px #EF4444' }} className={isConnected ? 'pulse' : ''} />
                            <span style={S.statusText}>{isConnected ? 'Connected to Spring Boot Kernel' : 'Disconnected'}</span>
                            {lastUpdated && <span style={S.statusTime}>· Updated {lastUpdated}</span>}
                        </div>
                    </div>
                </div>
                <div style={S.navRight}>
                    <div style={S.uptimePill}>
                        <span style={{ color: '#9BA3BF', fontSize: 11 }}>KERNEL UPTIME</span>
                        <span style={{ color: '#F0F2FF', fontWeight: 700, fontFamily: 'monospace', fontSize: 14 }}>{fmtUptime}</span>
                    </div>
                    <button onClick={handleLogout} className="btn-danger" style={{ fontSize: 13, background: '#ef444430', color: '#ef4444', border: '1px solid #ef4444', padding: '6px 12px', borderRadius: '6px', cursor: 'pointer' }}>Exit Simulator</button>
                </div>
            </nav>

            <div style={S.main}>
                
                {/* Visualizer Row */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '20px', marginBottom: '24px' }}>
                    
                    {/* Synchronization Area */}
                    <div style={S.card}>
                         <div style={S.sectionHead}>
                            <h3 style={S.sectionTitle}>🔒 Synchronization (Unit II)</h3>
                            <p style={S.sectionSub}>Mutex locks, Semaphores, Boundaries</p>
                        </div>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                            {/* Mutex */}
                            <div style={{ padding: '16px', background: mState.isLocked ? '#ef444420' : '#10b98120', border: `1px solid ${mState.isLocked ? '#ef4444' : '#10b981'}`, borderRadius: '12px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                                <div>
                                    <div style={{ fontSize: '13px', color: '#9ca3af' }}>Current Mutex State (Critical Section Guard)</div>
                                    <div style={{ fontSize: '18px', fontWeight: 'bold', color: mState.isLocked ? '#ef4444' : '#10b981' }}>
                                        {mState.isLocked ? `LOCKED (PID: ${mState.lockedByPid})` : 'UNLOCKED (Open)'}
                                    </div>
                                </div>
                                <div style={{ fontSize: '32px' }}>{mState.isLocked ? '🔴' : '🟢'}</div>
                            </div>

                            {/* Readers Writers */}
                            <div style={{ padding: '16px', background: '#3b82f620', border: '1px solid #3b82f6', borderRadius: '12px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                                <div>
                                    <div style={{ fontSize: '13px', color: '#9ca3af' }}>Readers-Writers (Availability Monitor)</div>
                                    <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#3b82f6' }}>
                                        {metrics.activeReaders} Active Reader(s)
                                    </div>
                                </div>
                                <div style={{ fontSize: '28px' }}>👓</div>
                            </div>
                            
                            {/* Producer Consumer */}
                            <div style={{ padding: '16px', background: '#f59e0b20', border: '1px solid #f59e0b', borderRadius: '12px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                                <div>
                                    <div style={{ fontSize: '13px', color: '#9ca3af' }}>Producer-Consumer Buffer (Tickets)</div>
                                    <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#f59e0b' }}>
                                        {metrics.ticketsInBuffer} items in buffer
                                    </div>
                                </div>
                                <div style={{ fontSize: '28px' }}>📦</div>
                            </div>
                        </div>
                    </div>

                    {/* PCB Table */}
                    <div style={{ ...S.card, maxHeight: '400px', overflowY: 'auto' }}>
                        <div style={S.sectionHead}>
                            <h3 style={S.sectionTitle}>Process Control Block (PCB) Logs</h3>
                            <p style={S.sectionSub}>Live stream of process state transitions</p>
                        </div>
                        <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                            <thead>
                                <tr style={{ borderBottom: '1px solid #374151', color: '#9ca3af', fontSize: '12px' }}>
                                    <th style={{ padding: '8px' }}>TIMESTAMP</th>
                                    <th style={{ padding: '8px' }}>PID</th>
                                    <th style={{ padding: '8px' }}>TYPE</th>
                                    <th style={{ padding: '8px' }}>BURST</th>
                                    <th style={{ padding: '8px' }}>TRANSITION</th>
                                </tr>
                            </thead>
                            <tbody>
                                {(metrics.recentProcesses || []).slice().reverse().map((p, i) => (
                                    <tr key={i} style={{ borderBottom: '1px solid #1f2937', fontSize: '13px' }}>
                                        <td style={{ padding: '12px 8px', color: '#6b7280' }}>
                                            {new Date(p.timestamp).toISOString().substring(11, 23)}
                                        </td>
                                        <td style={{ padding: '12px 8px', fontWeight: 'bold' }}>{p.pid}</td>
                                        <td style={{ padding: '12px 8px' }}>
                                            <span style={{ border: '1px solid #374151', padding: '2px 6px', borderRadius: '4px', fontSize: '10px' }}>{p.type}</span>
                                        </td>
                                        <td style={{ padding: '12px 8px', fontFamily: 'monospace' }}>{p.burstTime}ms</td>
                                        <td style={{ padding: '12px 8px' }}>
                                            {renderStateBadge(p.oldState)}
                                            <span style={{ color: '#6b7280', margin: '0 8px' }}>➔</span>
                                            {renderStateBadge(p.newState)}
                                        </td>
                                    </tr>
                                ))}
                                {(!metrics.recentProcesses || metrics.recentProcesses.length === 0) && (
                                    <tr>
                                        <td colSpan={5} style={{ padding: '24px', textAlign: 'center', color: '#6b7280' }}>
                                            No processes active. Submit a booking to see PCB transitions.
                                        </td>
                                    </tr>
                                )}
                            </tbody>
                        </table>
                    </div>
                </div>

                {/* Schedulers */}
                <div style={{ ...S.card, marginBottom: '24px' }}>
                    <div style={S.sectionHead}>
                        <h3 style={S.sectionTitle}>⚙️ CPU Scheduling (Unit III)</h3>
                        <p style={S.sectionSub}>Select algorithm for all incoming booking processes</p>
                    </div>

                    <div style={{ display: 'flex', gap: '16px', marginBottom: '24px' }}>
                        {SCHEDULERS.map(sc => (
                            <button
                                key={sc.key}
                                onClick={() => changeScheduler(sc.key)}
                                disabled={schedulerChanging}
                                style={{
                                    ...S.schBtn,
                                    flex: 1,
                                    ...(currentScheduler === sc.key ? {
                                        borderColor: sc.color,
                                        background: `${sc.color}18`,
                                    } : {}),
                                }}
                            >
                                <span style={S.schBtnIcon}>{sc.icon}</span>
                                <div style={{ textAlign: 'left' }}>
                                    <div style={{ ...S.schBtnLabel, color: currentScheduler === sc.key ? sc.color : '#F0F2FF' }}>{sc.label}</div>
                                    <div style={{ fontSize: '11px', color: '#9ca3af' }}>{sc.ds}</div>
                                </div>
                                {currentScheduler === sc.key && <span style={{ ...S.schBtnPill, background: sc.color }}>Active</span>}
                            </button>
                        ))}
                    </div>

                    {/* Scheduler queue visualizers */}
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '16px' }}>
                        {SCHEDULERS.map(sc => {
                            const queueSize = metrics[sc.metricKey as keyof OsMetrics] as number;
                            return (
                                <div key={sc.key} style={{ padding: '16px', background: '#111827', border: '1px solid #1f2937', borderRadius: '12px' }}>
                                    <div style={{ fontSize: '13px', color: '#9ca3af', marginBottom: '8px', display: 'flex', justifyContent: 'space-between' }}>
                                        <span>{sc.label} Queue</span>
                                        <span style={{ color: sc.color, fontWeight: 'bold' }}>{queueSize} wait</span>
                                    </div>
                                    <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap', minHeight: '30px' }}>
                                        {Array.from({ length: Math.min(queueSize, 20) }).map((_, i) => (
                                            <div key={i} style={{ width: '12px', height: '12px', background: sc.color, borderRadius: '2px', opacity: 0.8 }} />
                                        ))}
                                        {queueSize === 0 && <div style={{ fontSize: '11px', color: '#4b5563', fontStyle: 'italic' }}>Queue is empty</div>}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>

                {/* Dining Philosophers */}
                <div style={S.card}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
                        <div>
                            <h3 style={S.sectionTitle}>🍽️ Dining Philosophers Array (Deadlock Avoidance)</h3>
                            <p style={S.sectionSub}>Train crews needing multiple shared resources sequentially.</p>
                        </div>
                        <button onClick={startDiningPhilosophers} style={{ background: '#8b5cf6', color: '#fff', border: 'none', padding: '10px 20px', borderRadius: '8px', cursor: 'pointer', fontWeight: 'bold' }}>
                            Start New Simulation
                        </button>
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: '12px' }}>
                        {['0','1','2','3','4'].map(id => {
                            const state = philosopherStatus[id] || 'WAITING TO START';
                            let bg = '#1f2937';
                            let border = '#374151';
                            let emoji = '😴';
                            
                            if (state.includes('EATING')) { bg = '#10b98120'; border = '#10b981'; emoji = '🚂'; }
                            else if (state.includes('HUNGRY')) { bg = '#f59e0b20'; border = '#f59e0b'; emoji = '🤤'; }
                            else if (state.includes('THINKING')) { bg = '#3b82f620'; border = '#3b82f6'; emoji = '🤔'; }

                            return (
                                <div key={id} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '20px', background: bg, border: `2px solid ${border}`, borderRadius: '12px', textAlign: 'center' }}>
                                    <div style={{ fontSize: '32px', marginBottom: '8px' }}>{emoji}</div>
                                    <div style={{ fontWeight: 'bold', fontSize: '14px', marginBottom: '4px' }}>Crew {id}</div>
                                    <div style={{ fontSize: '11px', color: '#9ca3af' }}>{state}</div>
                                </div>
                            );
                        })}
                    </div>
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
        fontFamily: "'Space Grotesk', system-ui, sans-serif"
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
        borderBottom: '1px solid rgba(255,255,255,0.1)',
        backdropFilter: 'blur(16px)',
        position: 'sticky' as const, top: 0, zIndex: 100,
    },
    navLeft: { display: 'flex', alignItems: 'center', gap: 14 },
    navLogo: { fontSize: 28 },
    navBrand: { fontSize: 18, fontWeight: 800, color: '#F0F2FF' },
    navStatus: { display: 'flex', alignItems: 'center', gap: 6, marginTop: 3 },
    statusDot: { width: 7, height: 7, borderRadius: '50%', display: 'inline-block' },
    statusText: { fontSize: 12, color: '#9BA3BF', fontWeight: 500 },
    statusTime: { fontSize: 11, color: '#5C6480' },
    navRight: { display: 'flex', alignItems: 'center', gap: 12 },
    uptimePill: {
        display: 'flex', flexDirection: 'column' as const, alignItems: 'center',
        background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)',
        borderRadius: 8, padding: '4px 12px', gap: 2,
    },
    main: { maxWidth: 1400, margin: '0 auto', padding: '36px 28px', position: 'relative' as const, zIndex: 1 },
    card: { background: '#0f1423', border: '1px solid #1f2937', borderRadius: '16px', padding: '24px', boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)' },
    sectionHead: { marginBottom: '20px' },
    sectionTitle: { fontSize: '18px', fontWeight: 'bold', margin: '0 0 4px 0', display: 'flex', alignItems: 'center', gap: '8px' },
    sectionSub: { fontSize: '13px', color: '#9ca3af', margin: 0 },
    schBtn: {
        display: 'flex', alignItems: 'center', gap: '12px',
        padding: '12px 16px',
        background: 'rgba(255,255,255,0.03)',
        border: '1.5px solid rgba(255,255,255,0.08)',
        borderRadius: '12px', cursor: 'pointer',
        transition: 'all 0.2s ease', position: 'relative' as const,
    },
    schBtnIcon: { fontSize: 24 },
    schBtnLabel: { fontSize: 14, fontWeight: 'bold' },
    schBtnPill: {
        position: 'absolute' as const, top: 8, right: 8,
        padding: '2px 8px', borderRadius: '99px',
        fontSize: 10, fontWeight: 'bold', color: 'white',
    },
};

export default AdminDashboard;
