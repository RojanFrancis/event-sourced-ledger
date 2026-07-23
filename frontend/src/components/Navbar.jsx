import { useAuth } from '../context/AuthContext'
import { useNavigate } from 'react-router-dom'

export default function Navbar() {
  const { user, logoutUser } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logoutUser()
    navigate('/login')
  }

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
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        <div style={{ width: '36px', height: '36px', background: 'var(--green)', borderRadius: '10px', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'white', fontSize: '18px', fontWeight: '700' }}>₹</div>
        <span style={{ fontWeight: '700', fontSize: '18px', color: 'var(--text)' }}>LedgerApp</span>
      </div>

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
