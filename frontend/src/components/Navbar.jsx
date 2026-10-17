import { useAuth } from '../context/AuthContext'
import { useNavigate, useLocation } from 'react-router-dom'

export default function Navbar() {
  const { user, logoutUser } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const handleLogout = () => {
    logoutUser()
    navigate('/login')
  }

  const link = (path) => `nav-link${location.pathname === path ? ' active' : ''}`

  return (
    <nav className="nav">
      <div style={{ display: 'flex', alignItems: 'center', gap: 24 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer' }} onClick={() => navigate('/dashboard')}>
          <div className="logo" style={{ width: 36, height: 36, fontSize: 18, borderRadius: 10 }}>₹</div>
          <span style={{ fontWeight: 800, fontSize: 18, letterSpacing: '-.02em' }}>LedgerApp</span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <button className={link('/dashboard')} onClick={() => navigate('/dashboard')}>Dashboard</button>
          <button className={link('/ai')} onClick={() => navigate('/ai')}>
            <span className="ai-tag">AI</span>Tools
          </button>
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
        <div className="nav-user" style={{ textAlign: 'right' }}>
          <div style={{ fontSize: 14, fontWeight: 700 }}>{user?.fullName}</div>
          <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>{user?.email}</div>
        </div>
        <div style={{ width: 36, height: 36, background: 'var(--green-light)', borderRadius: '50%', display: 'grid', placeItems: 'center', color: 'var(--green-dark)', fontWeight: 800 }}>
          {user?.fullName?.charAt(0)}
        </div>
        <button className="btn btn-outline" onClick={handleLogout} style={{ padding: '7px 16px' }}>Logout</button>
      </div>
    </nav>
  )
}