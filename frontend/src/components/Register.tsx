import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import axios from 'axios';
import { API_ENDPOINTS } from '../config/api';

const Register: React.FC = () => {
    const [username, setUsername] = useState<string>('');
    const [password, setPassword] = useState<string>('');
    const [confirmPassword, setConfirmPassword] = useState<string>('');
    const [role, setRole] = useState<string>('USER');
    const [showPassword, setShowPassword] = useState<boolean>(false);
    const [error, setError] = useState<string>('');
    const [success, setSuccess] = useState<string>('');
    const [loading, setLoading] = useState<boolean>(false);
    const navigate = useNavigate();

    const passwordStrength = () => {
        if (password.length === 0) return { score: 0, label: '', color: '' };
        if (password.length < 6) return { score: 1, label: 'Weak', color: '#EF4444' };
        if (password.length < 10) return { score: 2, label: 'Fair', color: '#F59E0B' };
        if (password.length >= 10 && /[^a-zA-Z0-9]/.test(password)) return { score: 4, label: 'Strong', color: '#10B981' };
        return { score: 3, label: 'Good', color: '#3B82F6' };
    };

    const strength = passwordStrength();

    const handleRegister = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setSuccess('');
        if (password !== confirmPassword) { setError('Passwords do not match!'); return; }
        if (password.length < 6) { setError('Password must be at least 6 characters'); return; }
        setLoading(true);
        try {
            const response = await axios.post(API_ENDPOINTS.REGISTER, { username, password, role });
            if (response.data.startsWith('SUCCESS')) {
                setSuccess('Account created! Redirecting to login...');
                setTimeout(() => navigate(role === 'ADMIN' ? '/admin-login' : '/login'), 2000);
            } else {
                setError(response.data);
            }
        } catch (err: unknown) {
            const axiosErr = err as { response?: { data?: string } };
            setError(axiosErr.response?.data ?? 'Server connection error. Is Spring Boot running?');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={S.page}>
            <div style={S.blob1} />
            <div style={S.blob2} />

            <div style={S.container} className="fade-up">
                {/* Left info panel */}
                <div style={S.leftPanel}>
                    <div style={S.logoRow}>
                        <div style={S.logoBox}>🚄</div>
                        <span style={S.logoText}>RailBooker</span>
                    </div>
                    <h2 style={S.infoTitle}>Join the platform</h2>
                    <p style={S.infoSub}>
                        Create your account to book train tickets instantly with our OS-powered scheduling system.
                    </p>
                    <div style={S.infoStats}>
                        {[
                            { num: '100+', label: 'Seats Protected by Mutex' },
                            { num: '4', label: 'Scheduling Algorithms' },
                            { num: '∞', label: 'Concurrent Bookings' },
                        ].map(s => (
                            <div key={s.label} style={S.stat}>
                                <div style={S.statNum}>{s.num}</div>
                                <div style={S.statLabel}>{s.label}</div>
                            </div>
                        ))}
                    </div>
                </div>

                {/* Divider */}
                <div style={S.verticalDivider} />

                {/* Right form */}
                <div style={S.rightPanel}>
                    <h2 style={S.formTitle}>Create Account</h2>
                    <p style={S.formSub}>Fill in the details to get started</p>

                    {error && (
                        <div className="alert alert-error" style={{ marginBottom: 20 }}>
                            <span>⚠️</span> {error}
                        </div>
                    )}
                    {success && (
                        <div className="alert alert-success" style={{ marginBottom: 20 }}>
                            <span>✅</span> {success}
                        </div>
                    )}

                    <form onSubmit={handleRegister} style={S.form}>
                        {/* Role selector */}
                        <div style={S.roleGrid}>
                            {[
                                { value: 'USER', icon: '🎫', label: 'Passenger', desc: 'Book & manage tickets' },
                                { value: 'ADMIN', icon: '⚙️', label: 'Admin', desc: 'Monitor OS system' },
                            ].map(r => (
                                <button
                                    key={r.value}
                                    type="button"
                                    onClick={() => setRole(r.value)}
                                    style={{
                                        ...S.roleCard,
                                        ...(role === r.value ? S.roleCardActive : {}),
                                    }}
                                    id={`role-${r.value.toLowerCase()}`}
                                >
                                    <div style={S.roleIcon}>{r.icon}</div>
                                    <div style={S.roleLabel}>{r.label}</div>
                                    <div style={S.roleDesc}>{r.desc}</div>
                                </button>
                            ))}
                        </div>

                        <div>
                            <label className="input-label">Username</label>
                            <div className="input-wrapper">
                                <span className="input-icon">👤</span>
                                <input
                                    className="input-field"
                                    type="text"
                                    placeholder="Choose a username"
                                    value={username}
                                    onChange={e => setUsername(e.target.value)}
                                    required
                                    id="reg-username"
                                    autoComplete="username"
                                />
                            </div>
                        </div>

                        <div>
                            <label className="input-label">Password</label>
                            <div className="input-wrapper">
                                <span className="input-icon">🔑</span>
                                <input
                                    className="input-field"
                                    type={showPassword ? 'text' : 'password'}
                                    placeholder="Create a password"
                                    value={password}
                                    onChange={e => setPassword(e.target.value)}
                                    required
                                    id="reg-password"
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
                            {/* Password strength bar */}
                            {password.length > 0 && (
                                <div style={{ marginTop: 8 }}>
                                    <div style={{ display: 'flex', gap: 4, marginBottom: 4 }}>
                                        {[1,2,3,4].map(i => (
                                            <div key={i} style={{
                                                flex: 1, height: 4, borderRadius: 99,
                                                background: i <= strength.score ? strength.color : 'rgba(255,255,255,0.08)',
                                                transition: 'background 0.3s',
                                            }} />
                                        ))}
                                    </div>
                                    <span style={{ fontSize: 12, color: strength.color, fontWeight: 600 }}>
                                        {strength.label}
                                    </span>
                                </div>
                            )}
                        </div>

                        <div>
                            <label className="input-label">Confirm Password</label>
                            <div className="input-wrapper">
                                <span className="input-icon">🔒</span>
                                <input
                                    className="input-field"
                                    type={showPassword ? 'text' : 'password'}
                                    placeholder="Confirm your password"
                                    value={confirmPassword}
                                    onChange={e => setConfirmPassword(e.target.value)}
                                    required
                                    id="reg-confirm-password"
                                    style={{
                                        borderColor: confirmPassword && confirmPassword !== password
                                            ? 'rgba(239,68,68,0.6)' : undefined,
                                    }}
                                />
                                {confirmPassword && (
                                    <span style={{
                                        position: 'absolute', right: 16, top: '50%',
                                        transform: 'translateY(-50%)',
                                        fontSize: 14,
                                    }}>
                                        {confirmPassword === password ? '✅' : '❌'}
                                    </span>
                                )}
                            </div>
                        </div>

                        <button
                            type="submit"
                            disabled={loading}
                            className="btn-primary"
                            style={{ width: '100%', padding: '15px', fontSize: 15, marginTop: 4 }}
                            id="register-submit"
                        >
                            {loading ? (
                                <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10 }}>
                                    <span className="spin" style={{ display: 'inline-block', fontSize: 18 }}>⟳</span>
                                    Creating Account...
                                </span>
                            ) : `🚀 Create ${role === 'ADMIN' ? 'Admin' : ''} Account`}
                        </button>
                    </form>

                    <p style={S.signInText}>
                        Already have an account?{' '}
                        <Link to="/login" style={S.link}>Sign in →</Link>
                    </p>
                </div>
            </div>
        </div>
    );
};

const S: Record<string, React.CSSProperties> = {
    page: {
        minHeight: '100vh',
        background: 'linear-gradient(135deg, #0D0F1A 0%, #12141F 100%)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        padding: 24, position: 'relative', overflow: 'hidden',
    },
    blob1: {
        position: 'fixed' as const, top: -150, left: -100,
        width: 450, height: 450, borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(108,99,255,0.15) 0%, transparent 70%)',
        pointerEvents: 'none',
    },
    blob2: {
        position: 'fixed' as const, bottom: -100, right: -100,
        width: 400, height: 400, borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(0,212,170,0.1) 0%, transparent 70%)',
        pointerEvents: 'none',
    },
    container: {
        display: 'flex', gap: 0,
        background: 'rgba(18,20,31,0.95)',
        border: '1px solid rgba(255,255,255,0.07)',
        borderRadius: 28,
        backdropFilter: 'blur(20px)',
        boxShadow: '0 30px 80px rgba(0,0,0,0.6)',
        overflow: 'hidden',
        width: '100%',
        maxWidth: 820,
        position: 'relative' as const,
        zIndex: 1,
    },
    leftPanel: {
        flex: '0 0 300px',
        padding: '50px 40px',
        background: 'linear-gradient(160deg, rgba(108,99,255,0.15) 0%, rgba(0,212,170,0.05) 100%)',
        borderRight: '1px solid rgba(255,255,255,0.06)',
        display: 'flex', flexDirection: 'column' as const, gap: 28,
    },
    logoRow: {
        display: 'flex', alignItems: 'center', gap: 12,
    },
    logoBox: {
        width: 44, height: 44,
        background: 'linear-gradient(135deg, #6C63FF, #4F46E5)',
        borderRadius: 12,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontSize: 22,
    },
    logoText: {
        fontSize: 20, fontWeight: 800, color: '#F0F2FF',
        fontFamily: "'Space Grotesk', sans-serif",
    },
    infoTitle: {
        fontSize: 26, fontWeight: 800,
        color: '#F0F2FF',
        fontFamily: "'Space Grotesk', sans-serif",
        letterSpacing: '-0.02em',
        lineHeight: 1.2,
    },
    infoSub: { fontSize: 14, color: '#9BA3BF', lineHeight: 1.7 },
    infoStats: { display: 'flex', flexDirection: 'column' as const, gap: 16, marginTop: 8 },
    stat: {
        padding: '14px 18px',
        background: 'rgba(255,255,255,0.04)',
        border: '1px solid rgba(255,255,255,0.07)',
        borderRadius: 12,
    },
    statNum: { fontSize: 22, fontWeight: 900, color: '#8B84FF', fontFamily: "'Space Grotesk', sans-serif" },
    statLabel: { fontSize: 12, color: '#9BA3BF', marginTop: 4 },
    verticalDivider: {
        width: 1,
        background: 'rgba(255,255,255,0.06)',
        flexShrink: 0,
    },
    rightPanel: {
        flex: 1,
        padding: '50px 44px',
        display: 'flex', flexDirection: 'column' as const,
    },
    formTitle: {
        fontSize: 26, fontWeight: 800,
        color: '#F0F2FF',
        fontFamily: "'Space Grotesk', sans-serif",
        letterSpacing: '-0.02em',
        marginBottom: 8,
    },
    formSub: { fontSize: 14, color: '#9BA3BF', marginBottom: 28 },
    form: { display: 'flex', flexDirection: 'column' as const, gap: 18 },
    roleGrid: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 },
    roleCard: {
        display: 'flex', flexDirection: 'column' as const, alignItems: 'center',
        gap: 6, padding: '14px 12px',
        background: 'rgba(255,255,255,0.03)',
        border: '1.5px solid rgba(255,255,255,0.08)',
        borderRadius: 12, cursor: 'pointer',
        textAlign: 'center' as const,
        transition: 'all 0.2s ease',
    },
    roleCardActive: {
        background: 'rgba(108,99,255,0.12)',
        border: '1.5px solid rgba(108,99,255,0.5)',
        boxShadow: '0 0 20px rgba(108,99,255,0.15)',
    },
    roleIcon: { fontSize: 24 },
    roleLabel: { fontSize: 13, fontWeight: 700, color: '#F0F2FF' },
    roleDesc: { fontSize: 11, color: '#9BA3BF' },
    eyeBtn: {
        position: 'absolute' as const,
        right: 14, top: '50%', transform: 'translateY(-50%)',
        background: 'none', border: 'none', cursor: 'pointer',
        fontSize: 16, padding: 4, color: '#9BA3BF',
    },
    signInText: { textAlign: 'center' as const, fontSize: 14, color: '#9BA3BF', marginTop: 20 },
    link: { color: '#8B84FF', textDecoration: 'none', fontWeight: 600 },
};

export default Register;
