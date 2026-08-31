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

  const isActive = (path) => location.pathname === path

  return (
    <nav style={{
      background: 'white',
      borderBottom: '1px solid var(--border)',
      padding: '0 24px',
      height: '64px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      position: 'sticky',
      top: 0,
      zIndex: 100
    }}>
      {/* Left — logo + nav links */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '24px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', cursor: 'pointer' }} onClick={() => navigate('/dashboard')}>
          <div style={{ width: '36px', height: '36px', background: 'var(--green)', borderRadius: '10px', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'white', fontSize: '18px', fontWeight: '700' }}>₹</div>
          <span style={{ fontWeight: '700', fontSize: '18px', color: 'var(--text)' }}>LedgerApp</span>
        </div>

        {/* Nav links */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
          <button
            onClick={() => navigate('/dashboard')}
            style={{
              background: 'none',
              border: 'none',
              padding: '6px 12px',
              borderRadius: '8px',
              fontSize: '14px',
              fontWeight: '500',
              cursor: 'pointer',
              color: isActive('/dashboard') ? 'var(--green)' : 'var(--text-muted)',
              background: isActive('/dashboard') ? 'var(--green-light)' : 'transparent',
            }}
          >
            Dashboard
          </button>

          <button
            onClick={() => navigate('/ai')}
            style={{
              background: isActive('/ai') ? 'var(--green-light)' : 'transparent',
              border: 'none',
              padding: '6px 12px',
              borderRadius: '8px',
              fontSize: '14px',
              fontWeight: '500',
              cursor: 'pointer',
              color: isActive('/ai') ? 'var(--green)' : 'var(--text-muted)',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
            }}
          >
            <span style={{
              fontSize: '10px',
              fontWeight: '700',
              background: 'linear-gradient(135deg, #4f6ef7, #7c3aed)',
              color: 'white',
              padding: '2px 5px',
              borderRadius: '4px',
              letterSpacing: '0.03em',
            }}>AI</span>
            Tools
          </button>
        </div>
      </div>

      {/* Right — user info + logout */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
        <div style={{ textAlign: 'right' }}>
          <div style={{ fontSize: '14px', fontWeight: '500' }}>{user?.fullName}</div>
          <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{user?.email}</div>
        </div>
        <div style={{ width: '36px', height: '36px', background: 'var(--green-light)', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--green-dark)', fontWeight: '600', fontSize: '16px' }}>
          {user?.fullName?.charAt(0)}
        </div>
        <button className="btn btn-outline" onClick={handleLogout} style={{ padding: '7px 16px' }}>
          Logout
        </button>
      </div>
    </nav>
  )
}
