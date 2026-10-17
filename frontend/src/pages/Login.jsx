import { useState, useEffect } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { login } from '../services/api'
import { useAuth } from '../context/AuthContext'

const DEMO = { email: 'demo@ledger.dev', password: 'Demo@12345' }
const POINTS = [
  ['Immutable event history', 'Every change is appended, never edited.'],
  ['Double-entry bookkeeping', 'Each transfer posts a matching debit and credit.'],
  ['AI finance controller', 'Gemini reconciles batches and answers questions.'],
]

export default function Login() {
  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [slow, setSlow] = useState(false)
  const { loginUser } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (!loading) { setSlow(false); return }
    const t = setTimeout(() => setSlow(true), 3500)
    return () => clearTimeout(t)
  }, [loading])

  const signIn = async (creds) => {
    setError('')
    setLoading(true)
    try {
      const res = await login(creds)
      loginUser(res.data)
      navigate('/dashboard')
    } catch (err) {
      setError(err.response?.data?.message || 'Login failed')
    } finally {
      setLoading(false)
    }
  }

  const handleSubmit = (e) => { e.preventDefault(); signIn(form) }

  return (
    <div className="auth">
      <aside className="auth-side">
        <h1>Every rupee,<br />on the record.</h1>
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

          <h2 style={{ fontSize: 26, fontWeight: 800, letterSpacing: '-.02em', marginBottom: 4 }}>Welcome back</h2>
          <p style={{ color: 'var(--text-muted)', fontSize: 14, marginBottom: 22 }}>Sign in to your account</p>

          <button type="button" className="btn demo" onClick={() => signIn(DEMO)} disabled={loading}>
            {loading ? 'Signing in…' : 'Try the demo account'}
          </button>
          {slow && <div className="banner" style={{ marginTop: 12, marginBottom: 0 }}><span className="spin" />Waking up the server. The first sign-in can take up to a minute.</div>}

          <div className="or">or sign in with email</div>

          {error && <div className="error-msg">{error}</div>}

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label className="label">Email</label>
              <input className="input" type="email" placeholder="you@example.com" autoComplete="email"
                value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required />
            </div>
            <div className="form-group">
              <label className="label">Password</label>
              <input className="input" type="password" placeholder="••••••••" autoComplete="current-password"
                value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required />
            </div>
            <button className="btn btn-primary" type="submit" disabled={loading} style={{ width: '100%', padding: 12, fontSize: 15 }}>
              Sign in
            </button>
          </form>

          <p style={{ textAlign: 'center', marginTop: 22, fontSize: 14, color: 'var(--text-muted)' }}>
            Don't have an account? <Link to="/register" style={{ color: 'var(--green-dark)', fontWeight: 700 }}>Register</Link>
          </p>
        </div>
      </main>
    </div>
  )
}