import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';

import UserLogin from './components/UserLogin.tsx';
import AdminLogin from './components/AdminLogin.tsx';
import Register from './components/Register.tsx';
import Dashboard from './components/Dashboard.tsx';
import AdminDashboard from './components/AdminDashboard.tsx';

interface PrivateRouteProps {
  children: React.ReactNode;
  requiredRole?: string;
}

function App() {
  // Role-based route protection
  const PrivateRoute: React.FC<PrivateRouteProps> = ({ children, requiredRole }) => {
    const token = localStorage.getItem('jwt_token');
    const userRole = localStorage.getItem('user_role');
    
    if (!token) {
      return <Navigate to="/login" />;
    }
    
    if (requiredRole && userRole !== requiredRole) {
      // If user tries to access admin, redirect to user dashboard
      // If admin tries to access user, redirect to admin dashboard
      return <Navigate to={userRole === 'ADMIN' ? '/admin' : '/dashboard'} />;
    }
    
    return <>{children}</>;
  };

  return (
    <Router>
      <div style={{ fontFamily: 'Arial, sans-serif', backgroundColor: '#f4f4f9', minHeight: '100vh' }}>
        <Routes>
          {/* Public Routes */}
          <Route path="/" element={<Navigate to="/login" />} />
          <Route path="/login" element={<UserLogin />} />
          <Route path="/admin-login" element={<AdminLogin />} />
          <Route path="/register" element={<Register />} />

          {/* Protected User Route */}
          <Route 
            path="/dashboard" 
            element={
              <PrivateRoute requiredRole="USER">
                <Dashboard />
              </PrivateRoute>
            } 
          />

          {/* Protected Admin Route */}
          <Route 
            path="/admin" 
            element={
              <PrivateRoute requiredRole="ADMIN">
                <AdminDashboard />
              </PrivateRoute>
            } 
          />
        </Routes>
      </div>
    </Router>
  );
}

export default App;
