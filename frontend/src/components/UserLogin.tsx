import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import axios from 'axios';
import { API_ENDPOINTS } from '../config/api';

const useIsMobile = () => {
    const [isMobile, setIsMobile] = useState(window.innerWidth < 768);
    useEffect(() => {
        const handler = () => setIsMobile(window.innerWidth < 768);
        window.addEventListener('resize', handler);
        return () => window.removeEventListener('resize', handler);
    }, []);
    return isMobile;
};

const UserLogin: React.FC = () => {
    const [username, setUsername] = useState<string>('');
    const [password, setPassword] = useState<string>('');
    const [showPassword, setShowPassword] = useState<boolean>(false);
    const [error, setError] = useState<string>('');
    const [loading, setLoading] = useState<boolean>(false);
    const navigate = useNavigate();
    const isMobile = useIsMobile();

    const handleLogin = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setLoading(true);
        try {
            const response = await axios.post(API_ENDPOINTS.LOGIN, { username, password });
            if (response.data.error) {
                setError(response.data.error);
            } else if (response.data.role === 'ADMIN') {
                setError('Access denied. Please use admin login.');
            } else {
                localStorage.setItem('jwt_token', response.data.token);
                localStorage.setItem('user_role', response.data.role);
                localStorage.setItem('username', response.data.username);
                navigate('/dashboard');
            }
        } catch {
            setError('Server connection error. Is Spring Boot running?');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={{ ...S.page, flexDirection: isMobile ? 'column' : 'row' }}>
            {/* Animated bg blobs */}
            <div style={S.blob1} />
            <div style={S.blob2} />
            <div style={S.blob3} />

            {/* Left hero panel — hidden on mobile */}
            {!isMobile && (
                <div style={S.hero}>
                    <div style={{ animation: 'slideRight 0.6s ease-out' }}>
                        <div style={S.logoRow}>
                            <div style={S.logoCircle}>🚄</div>
                            <span style={S.logoText}>RailBooker</span>
                        </div>
                        <h1 style={S.heroTitle}>
                            Travel Smarter,<br />
                            <span className="gradient-text">Book Faster.</span>
                        </h1>
                        <p style={S.heroSub}>
                            India's most intelligent railway reservation system, built with real OS scheduling algorithms.
                        </p>
                        <div style={S.featureList}>
                            {[
                                { icon: '⚡', title: 'Instant Booking', desc: 'Real-time seat allocation using OS mutex locks' },
                                { icon: '🔒', title: 'Bank-level Security', desc: 'JWT-secured with Spring Security' },
                                { icon: '🧠', title: 'Smart Scheduling', desc: 'FCFS, SJF, Round Robin & Priority algorithms' },
                            ].map(f => (
                                <div key={f.title} style={S.featureItem}>
                                    <div style={S.featureIconBox}>{f.icon}</div>
                                    <div>
                                        <div style={S.featureTitle}>{f.title}</div>
                                        <div style={S.featureDesc}>{f.desc}</div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                    <div style={S.trainAnim} className="float">🚂</div>
                </div>
            )}

            {/* Right login panel */}
            <div style={{ ...S.formSide, flex: isMobile ? '1' : '0 0 420px', padding: isMobile ? '0' : '40px 30px', alignItems: isMobile ? 'stretch' : 'center', justifyContent: isMobile ? 'flex-start' : 'center' }}>
                <div style={{ ...S.card, borderRadius: isMobile ? '0 0 24px 24px' : 24, padding: isMobile ? '36px 24px 40px' : '44px 40px', boxShadow: isMobile ? '0 8px 40px rgba(0,0,0,0.5)' : '0 30px 80px rgba(0,0,0,0.6), 0 0 0 1px rgba(108,99,255,0.1)' }} className="fade-up">

                    {/* Logo shown only on mobile (inside card) */}
                    {isMobile && (
                        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 28 }}>
                            <div style={{ ...S.logoCircle, width: 40, height: 40, fontSize: 20, borderRadius: 12 }}>🚄</div>
                            <span style={{ ...S.logoText, fontSize: 18 }}>RailBooker</span>
                        </div>
                    )}

                    <div style={S.cardTop}>
                        <h2 style={{ ...S.cardTitle, fontSize: isMobile ? 24 : 28 }}>Welcome back</h2>
                        <p style={S.cardSub}>Sign in to your account to continue</p>
                    </div>

                    {error && (
                        <div className="alert alert-error" style={{ marginBottom: 20 }}>
                            <span>⚠️</span> {error}
                        </div>
                    )}

                    <form onSubmit={handleLogin} style={S.form}>
                        <div>
                            <label className="input-label">Username</label>
                            <div className="input-wrapper">
                                <span className="input-icon">👤</span>
                                <input
                                    className="input-field"
                                    type="text"
                                    placeholder="Enter your username"
                                    value={username}
                                    onChange={e => setUsername(e.target.value)}
                                    required
                                    id="username"
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
                                    placeholder="Enter your password"
                                    value={password}
                                    onChange={e => setPassword(e.target.value)}
                                    required
                                    id="password"
                                    autoComplete="current-password"
                                    style={{ paddingRight: 50 }}
                                />
                                <button type="button" onClick={() => setShowPassword(!showPassword)} style={S.eyeBtn} tabIndex={-1}>
                                    {showPassword ? '🙈' : '👁️'}
                                </button>
                            </div>
                        </div>

                        <button type="submit" disabled={loading} className="btn-primary"
                            style={{ width: '100%', padding: '15px', fontSize: 16, marginTop: 4 }} id="login-submit">
                            {loading ? (
                                <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10 }}>
                                    <span className="spin" style={{ display: 'inline-block', fontSize: 18 }}>⟳</span>
                                    Signing in...
                                </span>
                            ) : '→ Sign In'}
                        </button>

                        <div className="divider">or</div>

                        <p style={S.registerText}>
                            Don't have an account?{' '}
                            <Link to="/register" style={S.link}>Create one →</Link>
                        </p>
                    </form>

                    <div style={S.adminBox}>
                        <Link to="/admin-login" style={S.adminLink}>
                            <span>⚙️</span> Admin Portal
                        </Link>
                    </div>
                </div>

                {/* Mobile feature pills below the card */}
                {isMobile && (
                    <div style={{ padding: '20px 20px 40px', display: 'flex', flexDirection: 'column', gap: 10 }}>
                        {[
                            { icon: '⚡', title: 'Instant Booking', desc: 'Real-time OS mutex locks' },
                            { icon: '🔒', title: 'Bank-level Security', desc: 'JWT + Spring Security' },
                            { icon: '🧠', title: 'Smart Scheduling', desc: 'FCFS, SJF, RR & Priority' },
                        ].map(f => (
                            <div key={f.title} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 16px', background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.06)', borderRadius: 14 }}>
                                <div style={{ fontSize: 18, width: 36, height: 36, background: 'rgba(108,99,255,0.15)', borderRadius: 10, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>{f.icon}</div>
                                <div>
                                    <div style={{ fontSize: 13, fontWeight: 700, color: '#F0F2FF' }}>{f.title}</div>
                                    <div style={{ fontSize: 11, color: '#9BA3BF' }}>{f.desc}</div>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
};

const S: Record<string, React.CSSProperties> = {
    page: {
        display: 'flex',
        minHeight: '100vh',
        position: 'relative',
        overflow: 'hidden',
        background: 'linear-gradient(135deg, #0D0F1A 0%, #12141F 60%, #0a0c16 100%)',
    },
    blob1: { position: 'fixed', top: -200, left: -200, width: 500, height: 500, borderRadius: '50%', background: 'radial-gradient(circle, rgba(108,99,255,0.18) 0%, transparent 70%)', pointerEvents: 'none' },
    blob2: { position: 'fixed', bottom: -150, right: 200, width: 400, height: 400, borderRadius: '50%', background: 'radial-gradient(circle, rgba(0,212,170,0.12) 0%, transparent 70%)', pointerEvents: 'none' },
    blob3: { position: 'fixed', top: '40%', left: '45%', width: 300, height: 300, borderRadius: '50%', background: 'radial-gradient(circle, rgba(108,99,255,0.08) 0%, transparent 70%)', pointerEvents: 'none' },
    hero: { flex: '1 1 55%', display: 'flex', flexDirection: 'column' as const, justifyContent: 'center', padding: '60px 70px', position: 'relative' },
    logoRow: { display: 'flex', alignItems: 'center', gap: 14, marginBottom: 48 },
    logoCircle: { width: 48, height: 48, background: 'linear-gradient(135deg, #6C63FF, #4F46E5)', borderRadius: 14, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 24, boxShadow: '0 4px 20px rgba(108,99,255,0.5)' },
    logoText: { fontSize: 22, fontWeight: 800, color: '#F0F2FF', fontFamily: "'Space Grotesk', sans-serif", letterSpacing: '-0.02em' },
    heroTitle: { fontSize: 'clamp(36px, 4vw, 56px)', fontWeight: 900, lineHeight: 1.15, color: '#F0F2FF', letterSpacing: '-0.03em', marginBottom: 24, fontFamily: "'Space Grotesk', sans-serif" },
    heroSub: { fontSize: 17, color: '#9BA3BF', lineHeight: 1.7, maxWidth: 420, marginBottom: 48 },
    featureList: { display: 'flex', flexDirection: 'column' as const, gap: 20 },
    featureItem: { display: 'flex', alignItems: 'flex-start', gap: 16, padding: '16px 20px', background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 14, backdropFilter: 'blur(10px)' },
    featureIconBox: { fontSize: 24, width: 44, height: 44, background: 'rgba(108,99,255,0.15)', borderRadius: 12, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 },
    featureTitle: { fontSize: 15, fontWeight: 700, color: '#F0F2FF', marginBottom: 4 },
    featureDesc: { fontSize: 13, color: '#9BA3BF', lineHeight: 1.5 },
    trainAnim: { position: 'absolute' as const, bottom: 40, right: 40, fontSize: 48, opacity: 0.15 },
    formSide: { display: 'flex', flexDirection: 'column' as const },
    card: { background: 'rgba(24,26,40,0.92)', border: '1px solid rgba(255,255,255,0.08)', width: '100%', maxWidth: 420, backdropFilter: 'blur(20px)' },
    cardTop: { marginBottom: 28 },
    cardTitle: { fontWeight: 800, color: '#F0F2FF', fontFamily: "'Space Grotesk', sans-serif", letterSpacing: '-0.02em', marginBottom: 8 },
    cardSub: { fontSize: 15, color: '#9BA3BF' },
    form: { display: 'flex', flexDirection: 'column' as const, gap: 20 },
    eyeBtn: { position: 'absolute' as const, right: 14, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', fontSize: 16, padding: 4, color: '#9BA3BF' },
    registerText: { textAlign: 'center' as const, fontSize: 14, color: '#9BA3BF' },
    link: { color: '#8B84FF', textDecoration: 'none', fontWeight: 600 },
    adminBox: { marginTop: 20, paddingTop: 20, borderTop: '1px solid rgba(255,255,255,0.07)', textAlign: 'center' as const },
    adminLink: { display: 'inline-flex', alignItems: 'center', gap: 8, color: '#5C6480', textDecoration: 'none', fontSize: 13, fontWeight: 500, padding: '8px 16px', borderRadius: 8 },
};

export default UserLogin;
