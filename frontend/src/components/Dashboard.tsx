import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { API_ENDPOINTS } from '../config/api';

interface Train {
    trainNumber: string; trainName: string; from: string; to: string;
    departure: string; arrival: string; duration: string;
    availableSeats: number; price: number; type: string;
}

interface Booking {
    id: number; trainNumber: string; trainName: string; from: string; to: string;
    departure: string; seats: number; totalPrice: number; bookingDate: string;
    pnr: string; status: string; passengerName: string;
}

const FALLBACK_TRAINS: Train[] = [
    { trainNumber: 'EXP-12951', trainName: 'Mumbai Rajdhani', from: 'Mumbai', to: 'Delhi',
      departure: '16:00', arrival: '08:00+1', duration: '16h', availableSeats: 100, price: 1500, type: 'Rajdhani' },
    { trainNumber: 'EXP-12952', trainName: 'Shatabdi Express', from: 'Mumbai', to: 'Pune',
      departure: '07:00', arrival: '10:30', duration: '3h 30m', availableSeats: 85, price: 450, type: 'Shatabdi' },
    { trainNumber: 'EXP-12953', trainName: 'Duronto Express', from: 'Mumbai', to: 'Bangalore',
      departure: '20:00', arrival: '10:00+1', duration: '14h', availableSeats: 120, price: 1200, type: 'Duronto' },
    { trainNumber: 'EXP-12954', trainName: 'Garib Rath Express', from: 'Delhi', to: 'Kolkata',
      departure: '22:30', arrival: '07:15+1', duration: '8h 45m', availableSeats: 42, price: 650, type: 'Express' },
];

const typeColor: Record<string, string> = {
    'Rajdhani': '#EF4444', 'Shatabdi': '#3B82F6', 'Duronto': '#10B981', 'Express': '#F59E0B',
};

const getSeatsColor = (seats: number) => {
    if (seats > 80) return '#10B981';
    if (seats > 30) return '#F59E0B';
    return '#EF4444';
};

// Store userId in sessionStorage for consistency within a session
const getSessionUserId = () => {
    let uid = sessionStorage.getItem('session_user_id');
    if (!uid) { uid = String(Math.floor(Math.random() * 9000) + 1000); sessionStorage.setItem('session_user_id', uid); }
    return parseInt(uid);
};

const Dashboard: React.FC = () => {
    const navigate = useNavigate();
    const username = localStorage.getItem('username') || 'Passenger';
    const userId   = getSessionUserId();

    const [trains, setTrains]               = useState<Train[]>(FALLBACK_TRAINS);
    const [searchFrom, setSearchFrom]       = useState('');
    const [searchTo, setSearchTo]           = useState('');
    const [searchDate, setSearchDate]       = useState('');
    const [selectedTrain, setSelectedTrain] = useState<Train | null>(null);
    const [seatsToBook, setSeatsToBook]     = useState(1);
    const [passengerName, setPassengerName] = useState('');
    const [passengerAge, setPassengerAge]   = useState('');
    const [bookingHistory, setBookingHistory] = useState<Booking[]>([]);
    const [showModal, setShowModal]         = useState(false);
    const [bookingSuccess, setBookingSuccess] = useState(false);
    const [isLoading, setIsLoading]         = useState(false);
    const [activeTab, setActiveTab]         = useState<'search' | 'bookings'>('search');
    const [activeScheduler, setActiveScheduler] = useState('FCFS');
    const [trainsLoaded, setTrainsLoaded]   = useState(false);

    const token = localStorage.getItem('jwt_token');
    const authHeader = { Authorization: `Bearer ${token}` };

    // Fetch live train data from DB
    const fetchTrains = useCallback(async () => {
        try {
            const res = await axios.get(API_ENDPOINTS.TRAINS(userId), { headers: authHeader });
            setTrains(res.data);
            setTrainsLoaded(true);
        } catch { /* Fallback to static data if backend not running */ }
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    // Fetch booking history for this user
    const fetchBookings = useCallback(async () => {
        try {
            const res = await axios.get(API_ENDPOINTS.USER_BOOKINGS(userId), { headers: authHeader });
            setBookingHistory(res.data);
        } catch { /* silent */ }
    }, [userId]); // eslint-disable-line react-hooks/exhaustive-deps

    // Fetch active scheduler for modal note
    const fetchScheduler = useCallback(async () => {
        try {
            const res = await axios.get(API_ENDPOINTS.ADMIN_SCHEDULER, { headers: authHeader });
            setActiveScheduler(res.data.scheduler ?? 'FCFS');
        } catch { /* silent */ }
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => {
        if (!token) { navigate('/login'); return; }
        fetchTrains();
        fetchBookings();
        fetchScheduler();
        // Poll trains for live seat counts every 5s
        const poll = setInterval(fetchTrains, 5000);
        return () => clearInterval(poll);
    }, [navigate, token, fetchTrains, fetchBookings, fetchScheduler]);

    const handleLogout = () => { localStorage.clear(); navigate('/login'); };

    const filteredTrains = trains.filter(t => {
        const fromMatch = !searchFrom || t.from.toLowerCase().includes(searchFrom.toLowerCase());
        const toMatch   = !searchTo   || t.to.toLowerCase().includes(searchTo.toLowerCase());
        return fromMatch && toMatch;
    });

    const handleBook = async (e: React.FormEvent) => {
        e.preventDefault();
        setIsLoading(true);
        if (!token) { navigate('/login'); return; }
        try {
            const res = await axios.post(API_ENDPOINTS.BOOK, {
                userId,
                seatsNeeded: seatsToBook,
                isTatkal: false,
                trainNumber: selectedTrain!.trainNumber,
                passengerName,
            }, { headers: authHeader });

            if (res.data.success) {
                const booking: Booking = {
                    id: res.data.id ?? Date.now(),
                    trainNumber: selectedTrain!.trainNumber,
                    trainName: res.data.trainName ?? selectedTrain!.trainName,
                    from: res.data.from ?? selectedTrain!.from,
                    to: res.data.to ?? selectedTrain!.to,
                    departure: res.data.departure ?? selectedTrain!.departure,
                    seats: seatsToBook,
                    totalPrice: res.data.totalPrice ?? (selectedTrain!.price * seatsToBook + seatsToBook * 30),
                    bookingDate: res.data.bookingDate ?? new Date().toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }),
                    pnr: res.data.pnr,
                    status: res.data.status ?? 'Confirmed',
                    passengerName,
                };
                setBookingHistory(prev => [booking, ...prev]);
                // Optimistically reduce seat count in local state
                setTrains(prev => prev.map(t =>
                    t.trainNumber === selectedTrain!.trainNumber
                        ? { ...t, availableSeats: Math.max(0, t.availableSeats - seatsToBook) }
                        : t
                ));
                setBookingSuccess(true);
                setShowModal(false);
                setPassengerName(''); setPassengerAge(''); setSeatsToBook(1);
                setActiveTab('bookings');
            } else {
                alert(res.data.message || 'Booking failed.');
            }
        } catch {
            alert('Booking failed. Please ensure Spring Boot is running.');
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div style={S.page}>
            {/* Navbar */}
            <nav style={S.navbar}>
                <div style={S.navLeft}>
                    <div style={S.navLogo}>🚄</div>
                    <div>
                        <div style={S.navBrand}>RailBooker</div>
                        <div style={S.navTagline}>Railway OS System</div>
                    </div>
                </div>
                <div style={S.navCenter}>
                    {(['search', 'bookings'] as const).map(tab => (
                        <button
                            key={tab}
                            onClick={() => { setActiveTab(tab); setBookingSuccess(false); }}
                            style={{ ...S.navTab, ...(activeTab === tab ? S.navTabActive : {}) }}
                        >
                            {tab === 'search' ? '🔍 Find Trains' : `📋 My Bookings${bookingHistory.length ? ` (${bookingHistory.length})` : ''}`}
                        </button>
                    ))}
                </div>
                <div style={S.navRight}>
                    <div style={S.userPill}>
                        <span style={S.userAvatar}>{username[0].toUpperCase()}</span>
                        <span style={S.userName}>{username}</span>
                    </div>
                    <button onClick={handleLogout} className="btn-danger" style={{ fontSize: 13 }}>Sign Out</button>
                </div>
            </nav>

            <div style={S.main}>

                {/* ===== SEARCH TAB ===== */}
                {activeTab === 'search' && (
                    <div style={{ animation: 'fadeUp 0.4s ease-out' }}>
                        {/* Hero search bar */}
                        <div style={S.searchHero}>
                            <div style={S.heroGlow} />
                            <h1 style={S.heroTitle}>Where are you travelling?</h1>
                            <p style={S.heroSub}>Search from {trains.length} trains across India {trainsLoaded && <span style={{ color: '#10B981', fontSize: 12 }}>● Live</span>}</p>
                            <div style={S.searchBar}>
                                <div style={S.searchField}>
                                    <span style={S.searchIcon}>📍</span>
                                    <input style={S.searchInput} placeholder="From (e.g. Mumbai)" value={searchFrom}
                                        onChange={e => setSearchFrom(e.target.value)} id="search-from" />
                                </div>
                                <div style={S.searchDivider}>
                                    <div style={S.dividerLine} />
                                    <div style={S.swapIcon}>⇄</div>
                                    <div style={S.dividerLine} />
                                </div>
                                <div style={S.searchField}>
                                    <span style={S.searchIcon}>🏁</span>
                                    <input style={S.searchInput} placeholder="To (e.g. Delhi)" value={searchTo}
                                        onChange={e => setSearchTo(e.target.value)} id="search-to" />
                                </div>
                                <div style={S.searchField}>
                                    <span style={S.searchIcon}>📅</span>
                                    <input style={{ ...S.searchInput, colorScheme: 'dark' }} type="date" value={searchDate}
                                        onChange={e => setSearchDate(e.target.value)} id="search-date" />
                                </div>
                                <button className="btn-accent" style={{ padding: '0 32px', borderRadius: 14, fontSize: 15, fontWeight: 700 }}>Search</button>
                            </div>
                        </div>

                        {/* Train listing */}
                        <div style={S.listHeader}>
                            <h2 style={S.listTitle}>Available Trains</h2>
                            <span style={S.listCount}>{filteredTrains.length} trains found</span>
                        </div>

                        <div style={S.trainGrid}>
                            {filteredTrains.map(train => (
                                <div key={train.trainNumber} style={S.trainCard} className="card card-hover">
                                    {/* Card header */}
                                    <div style={S.tcHeader}>
                                        <div style={S.tcHeaderLeft}>
                                            <div style={{ ...S.typeBadge, background: `${typeColor[train.type]}22`, color: typeColor[train.type], border: `1px solid ${typeColor[train.type]}44` }}>
                                                {train.type}
                                            </div>
                                            <div style={S.tcTrainName}>{train.trainName}</div>
                                            <div style={S.tcTrainNo}>{train.trainNumber}</div>
                                        </div>
                                        <div style={S.tcPrice}>
                                            <div style={S.tcPriceLabel}>From</div>
                                            <div style={S.tcPriceValue}>₹{train.price.toLocaleString()}</div>
                                        </div>
                                    </div>

                                    {/* Route */}
                                    <div style={S.tcRoute}>
                                        <div style={S.tcStation}>
                                            <div style={S.tcTime}>{train.departure}</div>
                                            <div style={S.tcCity}>{train.from}</div>
                                        </div>
                                        <div style={S.tcMid}>
                                            <div style={S.durationLabel}>{train.duration}</div>
                                            <div style={S.routeLine}>
                                                <div style={S.routeDot} />
                                                <div style={S.routeTrack} />
                                                <div style={S.routeArrow}>›</div>
                                                <div style={{ ...S.routeDot, background: '#00D4AA' }} />
                                            </div>
                                            <div style={S.durationSub}>Direct</div>
                                        </div>
                                        <div style={{ ...S.tcStation, textAlign: 'right' as const }}>
                                            <div style={S.tcTime}>{train.arrival}</div>
                                            <div style={S.tcCity}>{train.to}</div>
                                        </div>
                                    </div>

                                    <hr style={{ border: 'none', borderTop: '1px solid rgba(255,255,255,0.06)', margin: '0 0 16px' }} />

                                    {/* Footer */}
                                    <div style={S.tcFooter}>
                                        <div style={S.tcSeats}>
                                            <span style={{ ...S.seatDot, background: getSeatsColor(train.availableSeats) }} />
                                            <span style={{ color: getSeatsColor(train.availableSeats), fontSize: 13, fontWeight: 600 }}>
                                                {train.availableSeats} seats available
                                            </span>
                                        </div>
                                        <button
                                            className="btn-primary"
                                            style={{ padding: '10px 24px', fontSize: 14, opacity: train.availableSeats === 0 ? 0.5 : 1 }}
                                            onClick={() => { if (train.availableSeats > 0) { setSelectedTrain(train); setShowModal(true); setBookingSuccess(false); } }}
                                            disabled={train.availableSeats === 0}
                                            id={`book-${train.trainNumber}`}
                                        >
                                            {train.availableSeats === 0 ? 'Sold Out' : 'Book Now →'}
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                )}

                {/* ===== BOOKINGS TAB ===== */}
                {activeTab === 'bookings' && (
                    <div style={{ animation: 'fadeUp 0.4s ease-out' }}>
                        {bookingSuccess && (
                            <div className="alert alert-success" style={{ marginBottom: 24, fontSize: 15 }}>
                                <span>🎉</span> Booking confirmed! Your PNR has been generated and saved.
                            </div>
                        )}
                        <div style={S.listHeader}>
                            <h2 style={S.listTitle}>My Bookings</h2>
                            {bookingHistory.length > 0 && <span style={S.listCount}>{bookingHistory.length} bookings</span>}
                        </div>

                        {bookingHistory.length === 0 ? (
                            <div style={S.emptyState}>
                                <div style={S.emptyIcon}>🎫</div>
                                <h3 style={S.emptyTitle}>No bookings yet</h3>
                                <p style={S.emptySub}>Your confirmed tickets will appear here</p>
                                <button className="btn-primary" style={{ marginTop: 20, padding: '12px 28px' }} onClick={() => setActiveTab('search')}>
                                    Find Trains
                                </button>
                            </div>
                        ) : (
                            <div style={S.bookingsList}>
                                {bookingHistory.map(b => (
                                    <div key={b.id} style={S.bookingCard} className="card">
                                        <div style={S.bookingCardLeft}>
                                            <div style={S.pnrLabel}>PNR</div>
                                            <div style={S.pnrValue}>{b.pnr}</div>
                                            <div style={S.bookingDate}>{b.bookingDate}</div>
                                        </div>
                                        <div style={S.bookingCardMid}>
                                            <div style={S.bTrainName}>{b.trainName}</div>
                                            <div style={S.bRoute}>
                                                <span style={S.bCity}>{b.from}</span>
                                                <span style={S.bArrow}> → </span>
                                                <span style={S.bCity}>{b.to}</span>
                                            </div>
                                            <div style={S.bDetails}>
                                                <span>👤 {b.passengerName}</span>
                                                <span>🕐 {b.departure}</span>
                                                <span>💺 {b.seats} seat{b.seats > 1 ? 's' : ''}</span>
                                            </div>
                                        </div>
                                        <div style={S.bookingCardRight}>
                                            <div style={S.bAmount}>₹{b.totalPrice.toLocaleString()}</div>
                                            <div className="badge badge-green" style={{ marginTop: 8 }}>✅ {b.status}</div>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                )}
            </div>

            {/* ===== BOOKING MODAL ===== */}
            {showModal && selectedTrain && (
                <div style={S.overlay} onClick={e => { if (e.target === e.currentTarget) setShowModal(false); }}>
                    <div style={S.modal} className="fade-up">
                        {/* Modal header */}
                        <div style={S.modalHeader}>
                            <div>
                                <h3 style={S.modalTitle}>Book Ticket</h3>
                                <p style={S.modalSub}>{selectedTrain.trainName} · {selectedTrain.trainNumber}</p>
                            </div>
                            <button onClick={() => setShowModal(false)} style={S.closeBtn}>✕</button>
                        </div>

                        {/* Route pill */}
                        <div style={S.routeSummary}>
                            <div style={S.routeCity}>{selectedTrain.departure}<br /><span style={S.rcSmall}>{selectedTrain.from}</span></div>
                            <div style={S.routeMid}>
                                <div style={S.routeLineSmall} />
                                <div style={S.durationTag}>{selectedTrain.duration}</div>
                            </div>
                            <div style={{ ...S.routeCity, textAlign: 'right' as const }}>{selectedTrain.arrival}<br /><span style={S.rcSmall}>{selectedTrain.to}</span></div>
                        </div>

                        <form onSubmit={handleBook} style={S.modalForm}>
                            <div>
                                <label className="input-label">Passenger Name</label>
                                <div className="input-wrapper">
                                    <span className="input-icon">👤</span>
                                    <input className="input-field" type="text" placeholder="Full name" value={passengerName}
                                        onChange={e => setPassengerName(e.target.value)} required id="passenger-name" />
                                </div>
                            </div>
                            <div>
                                <label className="input-label">Age</label>
                                <div className="input-wrapper">
                                    <span className="input-icon">🎂</span>
                                    <input className="input-field" type="number" placeholder="Your age" min={1} max={120}
                                        value={passengerAge} onChange={e => setPassengerAge(e.target.value)} required id="passenger-age" />
                                </div>
                            </div>
                            <div>
                                <label className="input-label">Number of Seats (max 6)</label>
                                <div style={S.seatSelector}>
                                    <button type="button" style={S.seatBtn} onClick={() => setSeatsToBook(s => Math.max(1, s - 1))}>−</button>
                                    <span style={S.seatCount}>{seatsToBook}</span>
                                    <button type="button" style={S.seatBtn} onClick={() => setSeatsToBook(s => Math.min(6, Math.min(s + 1, selectedTrain.availableSeats)))}>+</button>
                                    <span style={S.seatHint}>seat{seatsToBook > 1 ? 's' : ''}</span>
                                </div>
                            </div>

                            {/* Fare breakdown */}
                            <div style={S.fareBox}>
                                <div style={S.fareRow}>
                                    <span style={S.fareKey}>Base Fare</span>
                                    <span style={S.fareVal}>₹{selectedTrain.price.toLocaleString()}</span>
                                </div>
                                <div style={S.fareRow}>
                                    <span style={S.fareKey}>Seats × {seatsToBook}</span>
                                    <span style={S.fareVal}>×{seatsToBook}</span>
                                </div>
                                <div style={S.fareRow}>
                                    <span style={S.fareKey}>Convenience Fee</span>
                                    <span style={S.fareVal}>₹{(seatsToBook * 30).toLocaleString()}</span>
                                </div>
                                <hr style={{ border: 'none', borderTop: '1px solid rgba(255,255,255,0.08)', margin: '8px 0' }} />
                                <div style={{ ...S.fareRow, ...S.fareTotal }}>
                                    <span>Total Amount</span>
                                    <span style={{ color: '#00D4AA' }}>₹{(selectedTrain.price * seatsToBook + seatsToBook * 30).toLocaleString()}</span>
                                </div>
                            </div>

                            <button type="submit" disabled={isLoading} className="btn-accent"
                                style={{ width: '100%', padding: '15px', fontSize: 15 }} id="confirm-booking">
                                {isLoading ? (
                                    <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10 }}>
                                        <span className="spin" style={{ display: 'inline-block', fontSize: 18 }}>⟳</span>
                                        Processing... (Mutex Lock active)
                                    </span>
                                ) : '🎫 Confirm Booking'}
                            </button>

                            <div style={S.modalNote}>
                                🔒 Secured by OS Mutex Lock · Active Scheduler: <strong style={{ color: '#8B84FF' }}>{activeScheduler}</strong>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

const S: Record<string, React.CSSProperties> = {
    page: { minHeight: '100vh', background: '#0D0F1A', color: '#F0F2FF' },

    /* Navbar */
    navbar: {
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '0 32px', height: 64,
        background: 'rgba(18,20,31,0.95)',
        borderBottom: '1px solid rgba(255,255,255,0.07)',
        backdropFilter: 'blur(16px)',
        position: 'sticky' as const, top: 0, zIndex: 100,
    },
    navLeft: { display: 'flex', alignItems: 'center', gap: 12 },
    navLogo: {
        width: 38, height: 38,
        background: 'linear-gradient(135deg, #6C63FF, #4F46E5)',
        borderRadius: 10,
        display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 20,
    },
    navBrand: { fontSize: 16, fontWeight: 800, color: '#F0F2FF', fontFamily: "'Space Grotesk', sans-serif" },
    navTagline: { fontSize: 11, color: '#5C6480' },
    navCenter: { display: 'flex', gap: 4 },
    navTab: {
        background: 'none', border: 'none', color: '#9BA3BF',
        padding: '8px 18px', borderRadius: 8, fontSize: 14, fontWeight: 500, cursor: 'pointer',
    },
    navTabActive: {
        background: 'rgba(108,99,255,0.12)', color: '#8B84FF',
        border: '1px solid rgba(108,99,255,0.25)',
    },
    navRight: { display: 'flex', alignItems: 'center', gap: 12 },
    userPill: {
        display: 'flex', alignItems: 'center', gap: 8,
        background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)',
        borderRadius: 99, padding: '6px 14px 6px 8px',
    },
    userAvatar: {
        width: 28, height: 28, borderRadius: '50%',
        background: 'linear-gradient(135deg, #6C63FF, #00D4AA)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontSize: 13, fontWeight: 700, color: 'white',
    },
    userName: { fontSize: 13, fontWeight: 600, color: '#F0F2FF' },

    main: { maxWidth: 1200, margin: '0 auto', padding: '40px 24px' },

    /* Hero search */
    searchHero: {
        background: 'linear-gradient(135deg, rgba(108,99,255,0.12) 0%, rgba(0,212,170,0.06) 100%)',
        border: '1px solid rgba(108,99,255,0.2)',
        borderRadius: 24, padding: '48px 40px',
        marginBottom: 40, position: 'relative' as const, overflow: 'hidden',
    },
    heroGlow: {
        position: 'absolute' as const, top: -60, right: -60,
        width: 200, height: 200, borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(0,212,170,0.15) 0%, transparent 70%)',
        pointerEvents: 'none',
    },
    heroTitle: {
        fontSize: 'clamp(24px, 3vw, 36px)', fontWeight: 800,
        color: '#F0F2FF', marginBottom: 8,
        fontFamily: "'Space Grotesk', sans-serif",
        position: 'relative' as const, zIndex: 1,
    },
    heroSub: { fontSize: 15, color: '#9BA3BF', marginBottom: 32, position: 'relative' as const, zIndex: 1 },
    searchBar: {
        display: 'flex', alignItems: 'center', gap: 0,
        background: 'rgba(13,15,26,0.8)',
        border: '1px solid rgba(255,255,255,0.1)',
        borderRadius: 16, padding: 6, overflow: 'hidden',
        backdropFilter: 'blur(12px)',
        position: 'relative' as const, zIndex: 1,
    },
    searchField: { display: 'flex', alignItems: 'center', flex: 1, gap: 10, padding: '10px 16px' },
    searchIcon: { fontSize: 16, flexShrink: 0, opacity: 0.6 },
    searchInput: { background: 'none', border: 'none', outline: 'none', color: '#F0F2FF', fontSize: 15, width: '100%', fontFamily: "'Inter', sans-serif" },
    searchDivider: { display: 'flex', alignItems: 'center', flexShrink: 0, gap: 0 },
    dividerLine: { width: 1, height: 24, background: 'rgba(255,255,255,0.1)' },
    swapIcon: { fontSize: 18, color: '#9BA3BF', padding: '0 8px', cursor: 'pointer' },

    /* List header */
    listHeader: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 },
    listTitle: { fontSize: 22, fontWeight: 800, color: '#F0F2FF', fontFamily: "'Space Grotesk', sans-serif" },
    listCount: { fontSize: 13, color: '#9BA3BF', background: 'rgba(255,255,255,0.05)', padding: '4px 12px', borderRadius: 99, border: '1px solid rgba(255,255,255,0.08)' },

    /* Train grid */
    trainGrid: { display: 'flex', flexDirection: 'column' as const, gap: 16 },
    trainCard: { padding: '24px 28px' },
    tcHeader: { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 20 },
    tcHeaderLeft: { display: 'flex', flexDirection: 'column' as const, gap: 6 },
    typeBadge: { display: 'inline-block', padding: '3px 10px', borderRadius: 99, fontSize: 11, fontWeight: 700, letterSpacing: '0.04em', width: 'fit-content' },
    tcTrainName: { fontSize: 18, fontWeight: 800, color: '#F0F2FF', fontFamily: "'Space Grotesk', sans-serif" },
    tcTrainNo: { fontSize: 13, color: '#5C6480' },
    tcPrice: { textAlign: 'right' as const },
    tcPriceLabel: { fontSize: 11, color: '#9BA3BF', marginBottom: 4 },
    tcPriceValue: { fontSize: 26, fontWeight: 900, color: '#00D4AA', fontFamily: "'Space Grotesk', sans-serif" },
    tcRoute: { display: 'flex', alignItems: 'center', marginBottom: 20 },
    tcStation: { minWidth: 90 },
    tcTime: { fontSize: 22, fontWeight: 800, color: '#F0F2FF', fontFamily: "'Space Grotesk', sans-serif" },
    tcCity: { fontSize: 13, color: '#9BA3BF', marginTop: 2 },
    tcMid: { flex: 1, display: 'flex', flexDirection: 'column' as const, alignItems: 'center', padding: '0 24px' },
    durationLabel: { fontSize: 12, color: '#9BA3BF', fontWeight: 600, marginBottom: 8 },
    routeLine: { display: 'flex', alignItems: 'center', width: '100%', gap: 4 },
    routeDot: { width: 8, height: 8, borderRadius: '50%', background: '#6C63FF', flexShrink: 0 },
    routeTrack: { flex: 1, height: 2, background: 'linear-gradient(90deg, #6C63FF, #00D4AA)' },
    routeArrow: { fontSize: 16, color: '#9BA3BF', flexShrink: 0 },
    durationSub: { fontSize: 11, color: '#5C6480', marginTop: 6 },
    tcFooter: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' },
    tcSeats: { display: 'flex', alignItems: 'center', gap: 8 },
    seatDot: { width: 8, height: 8, borderRadius: '50%', display: 'inline-block' },

    /* Bookings tab */
    emptyState: { display: 'flex', flexDirection: 'column' as const, alignItems: 'center', padding: '80px 24px', textAlign: 'center' as const },
    emptyIcon: { fontSize: 64, marginBottom: 20, opacity: 0.4 },
    emptyTitle: { fontSize: 22, fontWeight: 700, color: '#F0F2FF', marginBottom: 10 },
    emptySub: { fontSize: 15, color: '#9BA3BF' },
    bookingsList: { display: 'flex', flexDirection: 'column' as const, gap: 16 },
    bookingCard: { display: 'flex', gap: 24, padding: '24px 28px', alignItems: 'center', background: '#181A28', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 18 },
    bookingCardLeft: { textAlign: 'center' as const, flexShrink: 0, padding: '16px 24px 16px 0', borderRight: '1px solid rgba(255,255,255,0.07)' },
    pnrLabel: { fontSize: 10, color: '#5C6480', fontWeight: 700, letterSpacing: '0.1em', marginBottom: 4 },
    pnrValue: { fontSize: 14, fontWeight: 800, color: '#8B84FF', fontFamily: 'monospace', marginBottom: 6 },
    bookingDate: { fontSize: 11, color: '#9BA3BF' },
    bookingCardMid: { flex: 1 },
    bTrainName: { fontSize: 17, fontWeight: 800, color: '#F0F2FF', marginBottom: 6, fontFamily: "'Space Grotesk', sans-serif" },
    bRoute: { marginBottom: 10 },
    bCity: { fontSize: 14, fontWeight: 600, color: '#F0F2FF' },
    bArrow: { color: '#5C6480' },
    bDetails: { display: 'flex', gap: 16, fontSize: 13, color: '#9BA3BF', flexWrap: 'wrap' as const },
    bookingCardRight: { textAlign: 'right' as const, flexShrink: 0 },
    bAmount: { fontSize: 22, fontWeight: 900, color: '#00D4AA', fontFamily: "'Space Grotesk', sans-serif" },

    /* Modal */
    overlay: { position: 'fixed' as const, inset: 0, background: 'rgba(0,0,0,0.8)', backdropFilter: 'blur(8px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 200, padding: 24 },
    modal: { background: '#181A28', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 24, padding: '36px 40px', width: '100%', maxWidth: 480, maxHeight: '90vh', overflowY: 'auto' as const, boxShadow: '0 40px 100px rgba(0,0,0,0.8)' },
    modalHeader: { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 24 },
    modalTitle: { fontSize: 22, fontWeight: 800, color: '#F0F2FF', fontFamily: "'Space Grotesk', sans-serif", marginBottom: 4 },
    modalSub: { fontSize: 13, color: '#9BA3BF' },
    closeBtn: { background: 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8, padding: '6px 10px', color: '#9BA3BF', fontSize: 16, cursor: 'pointer' },
    routeSummary: { display: 'flex', alignItems: 'center', background: 'rgba(108,99,255,0.08)', border: '1px solid rgba(108,99,255,0.2)', borderRadius: 14, padding: '16px 20px', marginBottom: 24 },
    routeCity: { fontSize: 18, fontWeight: 800, color: '#F0F2FF', fontFamily: "'Space Grotesk', sans-serif" },
    rcSmall: { fontSize: 12, color: '#9BA3BF', fontWeight: 400 },
    routeMid: { flex: 1, display: 'flex', flexDirection: 'column' as const, alignItems: 'center', gap: 6, padding: '0 16px' },
    routeLineSmall: { width: '100%', height: 2, background: 'linear-gradient(90deg, #6C63FF, #00D4AA)' },
    durationTag: { fontSize: 11, color: '#9BA3BF', fontWeight: 600 },
    modalForm: { display: 'flex', flexDirection: 'column' as const, gap: 20 },
    seatSelector: { display: 'flex', alignItems: 'center', gap: 16, marginTop: 4 },
    seatBtn: { width: 38, height: 38, borderRadius: 10, background: 'rgba(108,99,255,0.15)', border: '1px solid rgba(108,99,255,0.3)', color: '#8B84FF', fontSize: 18, fontWeight: 700, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' },
    seatCount: { fontSize: 24, fontWeight: 900, color: '#F0F2FF', minWidth: 32, textAlign: 'center' as const },
    seatHint: { fontSize: 14, color: '#9BA3BF' },
    fareBox: { background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 12, padding: '16px 20px' },
    fareRow: { display: 'flex', justifyContent: 'space-between', padding: '6px 0', fontSize: 14 },
    fareKey: { color: '#9BA3BF' },
    fareVal: { color: '#F0F2FF', fontWeight: 500 },
    fareTotal: { fontSize: 17, fontWeight: 800, color: '#F0F2FF', paddingTop: 8 },
    modalNote: { textAlign: 'center' as const, fontSize: 12, color: '#5C6480', marginTop: 4 },
};

export default Dashboard;
