import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { register } from '../services/api'
import { useAuth } from '../context/AuthContext'

export default function Register() {
  const [form, setForm] = useState({ fullName: '', email: '', password: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const { loginUser } = useAuth()
  const navigate = useNavigate()

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const res = await register(form)
      loginUser(res.data)
      navigate('/dashboard')
    } catch (err) {
      setError(err.response?.data?.message || 'Registration failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'linear-gradient(135deg, #00C853 0%, #00796B 100%)' }}>
      <div style={{ width: '100%', maxWidth: '420px', padding: '0 20px' }}>
        <div style={{ textAlign: 'center', marginBottom: '32px' }}>
          <div style={{ width: '56px', height: '56px', background: 'white', borderRadius: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 12px', fontSize: '28px' }}>₹</div>
          <h1 style={{ color: 'white', fontSize: '24px', fontWeight: '700' }}>LedgerApp</h1>
          <p style={{ color: 'rgba(255,255,255,0.8)', fontSize: '14px', marginTop: '4px' }}>Event-Sourced Payment Ledger</p>
        </div>

        <div className="card">
          <h2 style={{ fontSize: '20px', fontWeight: '600', marginBottom: '4px' }}>Create account</h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '14px', marginBottom: '24px' }}>Start managing your finances</p>

          {error && <div className="error-msg">{error}</div>}

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label className="label">Full Name</label>
              <input className="input" type="text" placeholder="Rojan Francis"
                value={form.fullName} onChange={e => setForm({ ...form, fullName: e.target.value })} required />
            </div>
            <div className="form-group">
              <label className="label">Email</label>
              <input className="input" type="email" placeholder="rojan@example.com"
                value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} required />
            </div>
            <div className="form-group">
              <label className="label">Password</label>
              <input className="input" type="password" placeholder="Min 6 characters"
                value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} required />
            </div>
            <button className="btn btn-primary" type="submit" disabled={loading}
              style={{ width: '100%', padding: '12px', fontSize: '15px', marginTop: '8px' }}>
              {loading ? 'Creating account...' : 'Create Account'}
            </button>
          </form>

          <p style={{ textAlign: 'center', marginTop: '20px', fontSize: '14px', color: 'var(--text-muted)' }}>
            Already have an account? <Link to="/login" style={{ color: 'var(--green)', fontWeight: '500' }}>Sign in</Link>
          </p>
        </div>
      </div>
    </div>
  )
}
