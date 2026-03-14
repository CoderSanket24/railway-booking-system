# Railway OS - Modern Frontend Setup

## 🎨 Features

### Beautiful Modern UI
- Gradient backgrounds with glassmorphism effects
- Smooth animations and transitions
- Responsive design
- Dark mode admin dashboard
- Real-time data visualization

### Pages
1. **Login Page** - Split-screen design with features showcase
2. **Register Page** - Clean registration form
3. **User Dashboard** - Booking interface with live stats
4. **Admin Dashboard** - Real-time OS metrics monitoring

## 🚀 Quick Start

### 1. Install Dependencies
```bash
cd frontend
npm install
```

### 2. Start Development Server
```bash
npm run dev
```

The frontend will start on `http://localhost:5173`

### 3. Ensure Backend is Running
Make sure your Spring Boot backend is running on `http://localhost:8080`

## 📦 Dependencies

- **React 19** - UI framework
- **React Router DOM** - Navigation
- **Axios** - HTTP client
- **TypeScript** - Type safety
- **Vite** - Build tool

## 🎯 Features by Page

### Login Page (`/login`)
- Split-screen design
- Left panel: Feature showcase
- Right panel: Login form
- Smooth animations
- Error handling

### Register Page (`/register`)
- Clean centered form
- Password confirmation
- Validation
- Success redirect

### User Dashboard (`/dashboard`)
- **Booking Form**:
  - Select number of seats
  - Choose scheduling algorithm (FCFS, SJF, RR, Priority)
  - Tatkal/Priority option
  - Producer-Consumer test button
  
- **Live Stats Cards**:
  - FCFS Queue Size
  - SJF Queue Size
  - Round Robin Queue Size
  - Priority Queue Size
  - Active Readers Count
  
- **System Console**:
  - Real-time server responses
  - Terminal-style display
  
- **Recent Bookings Log**:
  - Last 10 bookings
  - User ID, seats, scheduler type
  - Timestamp

### Admin Dashboard (`/admin`)
- **Overview Cards**:
  - Total processes
  - Active readers
  - Priority queue
  - System status
  
- **Scheduler Metrics** (4 cards):
  - FCFS Scheduler
  - SJF Scheduler
  - Round Robin Scheduler
  - Priority Scheduler
  - Each shows: queue size, progress bar, type, algorithm
  
- **Synchronization Section**:
  - Mutex lock status
  - Semaphore status
  - Active readers
  - Writers waiting
  
- **OS Concepts Checklist**:
  - All implemented concepts with checkmarks

## 🎨 Design System

### Colors
- Primary: `#667eea` (Purple)
- Secondary: `#764ba2` (Dark Purple)
- Success: `#2ecc71` (Green)
- Danger: `#e74c3c` (Red)
- Warning: `#f39c12` (Orange)
- Info: `#3498db` (Blue)

### Typography
- Font Family: Inter (Google Fonts)
- Weights: 400, 500, 600, 700, 800

### Effects
- Glassmorphism: `backdrop-filter: blur(10px)`
- Shadows: `0 10px 30px rgba(0, 0, 0, 0.2)`
- Border Radius: 8px, 12px, 16px, 20px
- Transitions: `all 0.3s ease`

## 🔄 Real-time Updates

### Dashboard Stats
- Updates every 2 seconds
- Fetches from `/api/stats`
- Shows live queue sizes

### Admin Monitor
- Updates every 1 second
- Fetches from `/api/admin/monitor`
- Connection status indicator
- Pulse animation when connected

## 🔐 Authentication

- JWT token stored in `localStorage`
- Token sent in `Authorization: Bearer <token>` header
- Protected routes redirect to login if no token
- Logout clears token and redirects

## 📱 Responsive Design

- Desktop: Full layout with sidebars
- Tablet: Stacked cards
- Mobile: Single column layout

## 🎭 Animations

- **Fade In**: Page load animations
- **Pulse**: Connection status indicator
- **Slide In**: Notification banners
- **Hover Effects**: Cards lift on hover
- **Button Press**: Scale down on click

## 🛠️ Build for Production

```bash
npm run build
```

Output will be in `dist/` folder

## 📊 API Integration

### Endpoints Used

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/auth/login` | POST | User login |
| `/api/auth/register` | POST | User registration |
| `/api/book` | POST | Book tickets |
| `/api/stats` | GET | Get queue statistics |
| `/api/admin/monitor` | GET | Get OS metrics |
| `/api/producer-consumer` | POST | Test producer-consumer |

### Request Format

**Login:**
```json
{
  "username": "user123",
  "password": "password"
}
```

**Book Ticket:**
```json
{
  "userId": 101,
  "seatsNeeded": 2,
  "schedulerType": "FCFS",
  "isTatkal": false
}
```

**Producer-Consumer:**
```json
{
  "trainNumber": "EXP-12951",
  "ticketsToGenerate": 20,
  "numConsumers": 3
}
```

## 🐛 Troubleshooting

### CORS Error
If you see CORS errors, ensure backend has:
```java
@CrossOrigin(origins = "http://localhost:5173")
```

### Connection Refused
- Check if backend is running on port 8080
- Check if MySQL is running

### Token Expired
- Login again to get new token
- Token is stored in localStorage

### Stats Not Updating
- Check browser console for errors
- Verify `/api/stats` endpoint is accessible
- Check network tab in DevTools

## 🎯 Testing the UI

1. **Start Backend**: `cd railway-os && ./mvnw.cmd spring-boot:run`
2. **Start Frontend**: `cd frontend && npm run dev`
3. **Register**: Create a new account
4. **Login**: Sign in with credentials
5. **Book Tickets**: Test different schedulers
6. **View Admin**: Check real-time metrics
7. **Test Producer-Consumer**: Click the button

## 📸 Screenshots

### Login Page
- Modern split-screen design
- Feature highlights on left
- Login form on right

### Dashboard
- Booking form with scheduler selection
- Live stats cards
- Terminal-style console
- Recent bookings log

### Admin Dashboard
- Dark theme
- Real-time metrics
- Scheduler visualizations
- OS concepts checklist

## 🚀 Next Steps

1. Add more visualizations (charts, graphs)
2. Add process state diagram
3. Add Gantt chart for scheduling
4. Add notification system
5. Add user profile page
6. Add booking history
7. Add export functionality

## 💡 Tips

- Use Chrome DevTools to inspect network requests
- Check console for any errors
- Use React DevTools extension
- Monitor backend console for OS logs
- Test with multiple browser tabs for concurrency

---

**Enjoy your modern Railway OS interface! 🚂✨**
