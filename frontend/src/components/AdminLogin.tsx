import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import axios from 'axios';
import { API_ENDPOINTS } from '../config/api';

const AdminLogin: React.FC = () => {
    const [username, setUsername] = useState<string>('');
    const [password, setPassword] = useState<string>('');
    const [showPassword, setShowPassword] = useState<boolean>(false);
    const [error, setError] = useState<string>('');
    const [loading, setLoading] = useState<boolean>(false);
    const navigate = useNavigate();

    const handleLogin = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setLoading(true);
        try {
            const response = await axios.post(API_ENDPOINTS.LOGIN, { username, password });
            if (response.data.error) {
                setError(response.data.error);
            } else if (response.data.role !== 'ADMIN') {
                setError('Access denied. Admin credentials required.');
            } else {
                localStorage.setItem('jwt_token', response.data.token);
                localStorage.setItem('user_role', response.data.role);
                localStorage.setItem('username', response.data.username);
                navigate('/admin');
            }
        } catch {
            setError('Server connection error. Is Spring Boot running?');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={S.page}>
            {/* Background grid */}
            <div style={S.grid} />
            {/* Glow blobs */}
            <div style={S.glowRed} />
            <div style={S.glowPurple} />

            <div style={S.content} className="fade-up">
                {/* Header badge */}
                <div style={S.badge}>
                    <span style={S.badgeDot} />
                    Restricted Access — Admin Only
                </div>

                {/* Logo */}
                <div style={S.logoRow}>
                    <div style={S.logoBox}>⚙️</div>
                    <div>
                        <div style={S.logoTitle}>OS Core Console</div>
                        <div style={S.logoSub}>Railway Booking System</div>
                    </div>
                </div>

                <div style={S.card}>
                    <div style={S.cardHead}>
                        <h2 style={S.cardTitle}>Admin Sign In</h2>
                        <p style={S.cardSub}>Access the OS monitoring dashboard</p>
                    </div>

                    {error && (
                        <div className="alert alert-error" style={{ marginBottom: 20 }}>
                            <span>⚠️</span> {error}
                        </div>
                    )}

                    <form onSubmit={handleLogin} style={S.form}>
                        <div>
                            <label className="input-label" style={{ color: '#7C84A0' }}>Admin Username</label>
                            <div className="input-wrapper">
                                <span className="input-icon">🛡️</span>
                                <input
                                    className="input-field"
                                    type="text"
                                    placeholder="Enter admin username"
                                    value={username}
                                    onChange={e => setUsername(e.target.value)}
                                    required
                                    id="admin-username"
                                    autoComplete="username"
                                />
                            </div>
                        </div>

                        <div>
                            <label className="input-label" style={{ color: '#7C84A0' }}>Admin Password</label>
                            <div className="input-wrapper">
                                <span className="input-icon">🔐</span>
                                <input
                                    className="input-field"
                                    type={showPassword ? 'text' : 'password'}
                                    placeholder="Enter admin password"
                                    value={password}
                                    onChange={e => setPassword(e.target.value)}
                                    required
                                    id="admin-password"
                                    autoComplete="current-password"
                                    style={{ paddingRight: 50 }}
                                />
                                <button
                                    type="button"
                                    onClick={() => setShowPassword(!showPassword)}
                                    style={S.eyeBtn}
                                    tabIndex={-1}
                                >
                                    {showPassword ? '🙈' : '👁️'}
                                </button>
                            </div>
                        </div>

                        <button
                            type="submit"
                            disabled={loading}
                            id="admin-login-submit"
                            style={S.submitBtn}
                        >
                            {loading ? (
                                <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10 }}>
                                    <span className="spin" style={{ display: 'inline-block', fontSize: 18 }}>⟳</span>
                                    Authenticating...
                                </span>
                            ) : '🔓 Access Dashboard'}
                        </button>
                    </form>

                    {/* OS concept pills */}
                    <div style={S.osPills}>
                        {['FCFS', 'SJF', 'Round Robin', 'Mutex', 'Readers-Writers'].map(c => (
                            <span key={c} style={S.pill}>{c}</span>
                        ))}
                    </div>
                </div>

                <div style={S.footer}>
                    <Link to="/login" style={S.backLink}>← Back to User Login</Link>
                </div>
            </div>
        </div>
    );
};

const S: Record<string, React.CSSProperties> = {
    page: {
        minHeight: '100vh',
        background: 'linear-gradient(135deg, #080A14 0%, #0D0F1A 100%)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 24,
        position: 'relative',
        overflow: 'hidden',
    },
    grid: {
        position: 'fixed' as const, inset: 0,
        backgroundImage: `
            linear-gradient(rgba(108,99,255,0.04) 1px, transparent 1px),
            linear-gradient(90deg, rgba(108,99,255,0.04) 1px, transparent 1px)
        `,
        backgroundSize: '48px 48px',
        pointerEvents: 'none',
    },
    glowRed: {
        position: 'fixed' as const,
        top: -100, right: -100,
        width: 400, height: 400, borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(239,68,68,0.12) 0%, transparent 70%)',
        pointerEvents: 'none',
    },
    glowPurple: {
        position: 'fixed' as const,
        bottom: -100, left: '30%',
        width: 350, height: 350, borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(108,99,255,0.15) 0%, transparent 70%)',
        pointerEvents: 'none',
    },
    content: {
        width: '100%',
        maxWidth: 440,
        display: 'flex',
        flexDirection: 'column' as const,
        alignItems: 'center',
        gap: 28,
        position: 'relative' as const,
        zIndex: 1,
    },
    badge: {
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        background: 'rgba(239,68,68,0.1)',
        border: '1px solid rgba(239,68,68,0.25)',
        color: '#FCA5A5',
        fontSize: 12,
        fontWeight: 600,
        letterSpacing: '0.05em',
        padding: '7px 16px',
        borderRadius: 99,
        textTransform: 'uppercase' as const,
    },
    badgeDot: {
        width: 7, height: 7, borderRadius: '50%',
        background: '#EF4444',
        boxShadow: '0 0 6px #EF4444',
        animation: 'pulse 2s ease-in-out infinite',
        display: 'inline-block',
    },
    logoRow: {
        display: 'flex',
        alignItems: 'center',
        gap: 14,
    },
    logoBox: {
        width: 52, height: 52,
        background: 'linear-gradient(135deg, rgba(108,99,255,0.3), rgba(79,70,229,0.3))',
        border: '1px solid rgba(108,99,255,0.4)',
        borderRadius: 14,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontSize: 26,
        backdropFilter: 'blur(10px)',
    },
    logoTitle: {
        fontSize: 18, fontWeight: 800,
        color: '#F0F2FF',
        fontFamily: "'Space Grotesk', sans-serif",
        letterSpacing: '-0.02em',
    },
    logoSub: { fontSize: 12, color: '#5C6480', marginTop: 2 },
    card: {
        width: '100%',
        background: 'rgba(18,20,31,0.95)',
        border: '1px solid rgba(255,255,255,0.08)',
        borderRadius: 24,
        padding: '40px 36px',
        backdropFilter: 'blur(20px)',
        boxShadow: '0 30px 80px rgba(0,0,0,0.7), 0 0 0 1px rgba(108,99,255,0.08)',
    },
    cardHead: { marginBottom: 28 },
    cardTitle: {
        fontSize: 26, fontWeight: 800,
        color: '#F0F2FF',
        fontFamily: "'Space Grotesk', sans-serif",
        letterSpacing: '-0.02em',
        marginBottom: 8,
    },
    cardSub: { fontSize: 14, color: '#9BA3BF' },
    form: { display: 'flex', flexDirection: 'column' as const, gap: 20 },
    eyeBtn: {
        position: 'absolute' as const,
        right: 14, top: '50%',
        transform: 'translateY(-50%)',
        background: 'none', border: 'none', cursor: 'pointer',
        fontSize: 16, padding: 4, color: '#9BA3BF',
    },
    submitBtn: {
        width: '100%', padding: '15px',
        background: 'linear-gradient(135deg, #6C63FF, #4F46E5)',
        color: 'white', border: 'none',
        borderRadius: 12, fontSize: 15, fontWeight: 700,
        cursor: 'pointer',
        marginTop: 4,
        boxShadow: '0 4px 20px rgba(108,99,255,0.4)',
        transition: 'all 0.25s ease',
        letterSpacing: '0.01em',
    },
    osPills: {
        display: 'flex',
        flexWrap: 'wrap' as const,
        gap: 8,
        marginTop: 28,
        paddingTop: 24,
        borderTop: '1px solid rgba(255,255,255,0.06)',
    },
    pill: {
        padding: '5px 12px',
        background: 'rgba(108,99,255,0.1)',
        border: '1px solid rgba(108,99,255,0.2)',
        borderRadius: 99,
        fontSize: 11,
        fontWeight: 600,
        color: '#8B84FF',
        letterSpacing: '0.03em',
    },
    footer: { textAlign: 'center' as const },
    backLink: {
        color: '#5C6480',
        textDecoration: 'none',
        fontSize: 14,
        fontWeight: 500,
        transition: 'color 0.2s',
    },
};

export default AdminLogin;
