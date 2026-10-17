import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { register } from '../services/api'
import { useAuth } from '../context/AuthContext'

const POINTS = [
  ['Immutable event history', 'Every change is appended, never edited.'],
  ['Double-entry bookkeeping', 'Each transfer posts a matching debit and credit.'],
  ['Time travel', 'Query the balance at any moment in history.'],
]

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
    <div className="auth">
      <aside className="auth-side">
        <h1>Open an account<br />in seconds.</h1>
        <ul style={{ padding: 0 }}>
          {POINTS.map(([title, text]) => (
            <li key={title} className="point">
              <span className="dot" />
              <div><strong>{title}</strong><span>{text}</span></div>
            </li>
          ))}
        </ul>
      </aside>

      <main className="auth-main">
        <div className="auth-card">
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 28 }}>
            <div className="logo">₹</div>
            <div>
              <div style={{ fontWeight: 800, fontSize: 18 }}>LedgerApp</div>
              <div style={{ fontSize: 12, color: 'var(--text-muted)', fontWeight: 600 }}>Event-sourced payment ledger</div>
            </div>
          </div>

          <h2 style={{ fontSize: 26, fontWeight: 800, letterSpacing: '-.02em', marginBottom: 4 }}>Create account</h2>
          <p style={{ color: 'var(--text-muted)', fontSize: 14, marginBottom: 22 }}>Start managing your finances</p>

          {error && <div className="error-msg">{error}</div>}

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label className="label">Full name</label>
              <input className="input" type="text" placeholder="Your name" autoComplete="name"
                value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} required />
            </div>
            <div className="form-group">
              <label className="label">Email</label>
              <input className="input" type="email" placeholder="you@example.com" autoComplete="email"
                value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required />
            </div>
            <div className="form-group">
              <label className="label">Password</label>
              <input className="input" type="password" placeholder="Min 6 characters" autoComplete="new-password" minLength={6}
                value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required />
            </div>
            <button className="btn btn-primary" type="submit" disabled={loading} style={{ width: '100%', padding: 12, fontSize: 15, marginTop: 4 }}>
              {loading ? 'Creating account…' : 'Create account'}
            </button>
          </form>

          <p style={{ textAlign: 'center', marginTop: 22, fontSize: 14, color: 'var(--text-muted)' }}>
            Already have an account? <Link to="/login" style={{ color: 'var(--green-dark)', fontWeight: 700 }}>Sign in</Link>
          </p>
          <p style={{ textAlign: 'center', marginTop: 8, fontSize: 13, color: 'var(--text-muted)' }}>
            Just looking around? <Link to="/login" style={{ color: 'var(--text)', fontWeight: 700 }}>Use the demo account</Link>
          </p>
        </div>
      </main>
    </div>
  )
}