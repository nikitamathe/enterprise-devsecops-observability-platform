import { Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './contexts/AuthContext'
import { useAuth } from './hooks/useAuth'
import Layout from './components/Layout'
import ErrorBoundary from './components/ErrorBoundary'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import DashboardPage from './pages/DashboardPage'
import AccountsPage from './pages/AccountsPage'
import AccountDetailPage from './pages/AccountDetailPage'
import TransactionsPage from './pages/TransactionsPage'
import NotificationsPage from './pages/NotificationsPage'

function PrivateRoute({ children }) {
  const { isAuthenticated } = useAuth()
  return isAuthenticated ? children : <Navigate to="/login" replace />
}

function PublicRoute({ children }) {
  const { isAuthenticated } = useAuth()
  return !isAuthenticated ? children : <Navigate to="/dashboard" replace />
}

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<PublicRoute><ErrorBoundary pageName="Login"><LoginPage /></ErrorBoundary></PublicRoute>} />
        <Route path="/register" element={<PublicRoute><ErrorBoundary pageName="Registration"><RegisterPage /></ErrorBoundary></PublicRoute>} />

        <Route path="/" element={<PrivateRoute><Layout /></PrivateRoute>}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<ErrorBoundary pageName="Dashboard"><DashboardPage /></ErrorBoundary>} />
          <Route path="accounts" element={<ErrorBoundary pageName="Accounts"><AccountsPage /></ErrorBoundary>} />
          <Route path="accounts/:accountId" element={<ErrorBoundary pageName="Account details"><AccountDetailPage /></ErrorBoundary>} />
          <Route path="transactions" element={<ErrorBoundary pageName="Transactions"><TransactionsPage /></ErrorBoundary>} />
          <Route path="notifications" element={<ErrorBoundary pageName="Notifications"><NotificationsPage /></ErrorBoundary>} />
        </Route>

        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </AuthProvider>
  )
}
