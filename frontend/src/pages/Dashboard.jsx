import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { getAccounts, createAccount, deposit, withdraw, transfer } from '../services/api'
import Navbar from '../components/Navbar'

const fmt = (n) => `₹${Number(n || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`
const uid = () => `key-${Date.now()}-${Math.random().toString(36).slice(2, 11)}`
const TONE = { SAVINGS: ['var(--green-light)', 'var(--green-dark)'], CURRENT: ['var(--blue-light)', 'var(--blue)'], CREDIT: ['var(--violet-light)', 'var(--violet)'] }
const TITLE = { create: 'Create new account', deposit: 'Deposit money', withdraw: 'Withdraw money', transfer: 'Transfer money' }
const LABEL = { deposit: 'Deposit', withdraw: 'Withdrawal', transfer: 'Transfer' }
const DONE = { create: () => 'Account created', deposit: (a) => `${fmt(a)} deposited`, withdraw: (a) => `${fmt(a)} withdrawn`, transfer: (a) => `${fmt(a)} transferred` }
const FAIL = { create: 'Failed to create account', deposit: 'Deposit failed', withdraw: 'Withdrawal failed', transfer: 'Transfer failed' }

const Money = ({ v }) => {
  const [whole, dec] = fmt(v).split('.')
  return <>{whole}<span className="paise">.{dec}</span></>
}

export default function Dashboard() {
  const [accounts, setAccounts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [slow, setSlow] = useState(false)
  const [modal, setModal] = useState(null)
  const [acct, setAcct] = useState(null)
  const [form, setForm] = useState({})
  const [toast, setToast] = useState(null)
  const [busy, setBusy] = useState(false)
  const [copied, setCopied] = useState(null)
  const timer = useRef()
  const navigate = useNavigate()

  const load = async () => {
    setError(false)
    try { setAccounts((await getAccounts()).data) }
    catch { setError(true) }
    finally { setLoading(false) }
  }

  useEffect(() => {
    load()
    const t = setTimeout(() => setSlow(true), 3500)
    const onKey = (e) => e.key === 'Escape' && close()
    window.addEventListener('keydown', onKey)
    return () => { clearTimeout(t); window.removeEventListener('keydown', onKey) }
  }, [])

  const open = (type, account = null) => { setModal(type); setAcct(account); setForm({}) }
  const close = () => { setModal(null); setAcct(null); setForm({}) }
  const notify = (type, text) => {
    clearTimeout(timer.current)
    setToast({ type, text })
    timer.current = setTimeout(() => setToast(null), 3500)
  }
  const copy = (id) => {
    navigator.clipboard?.writeText(id)
    setCopied(id)
    setTimeout(() => setCopied(null), 1500)
  }

  const amount = parseFloat(form.amount)
  const over = (modal === 'withdraw' || modal === 'transfer') && amount > (acct?.currentBalance || 0)
  const blocked = busy || (modal !== 'create' && (!(amount > 0) || over))

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    const base = { amount, description: form.description || LABEL[modal], idempotencyKey: uid() }
    try {
      if (modal === 'create') await createAccount({ accountType: form.accountType || 'SAVINGS', currency: 'INR' })
      if (modal === 'deposit') await deposit({ accountId: acct.id, ...base })
      if (modal === 'withdraw') await withdraw({ accountId: acct.id, ...base })
      if (modal === 'transfer') await transfer({ fromAccountId: acct.id, toAccountId: form.toAccountId, ...base })
      const kind = modal
      await load()
      close()
      notify('success', DONE[kind](amount))
    } catch (err) {
      notify('error', err.response?.data?.message || FAIL[modal])
    } finally { setBusy(false) }
  }

  const total = accounts.reduce((sum, a) => sum + (a.currentBalance || 0), 0)

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)' }}>
      <Navbar />
      {toast && <div className={`toast ${toast.type}`} role="status">{toast.text}</div>}

      <div style={{ maxWidth: 1100, margin: '0 auto', padding: '32px 24px' }}>
        {loading && slow && (
          <div className="banner"><span className="spin" />Waking up the server. This demo runs on a free tier, so the first load can take up to a minute.</div>
        )}

        <div className="stats">
          <div className="card hero">
            <small>Total balance</small>
            <div className="big"><Money v={total} /></div>
            <span className="pill">Across {accounts.length} account{accounts.length === 1 ? '' : 's'}</span>
          </div>
          <div className="card">
            <div className="label">Accounts</div>
            <div style={{ fontSize: 32, fontWeight: 800 }}>{accounts.length}</div>
            <div style={{ fontSize: 13, color: 'var(--text-muted)', marginTop: 4 }}>Active accounts</div>
          </div>
          <div className="card">
            <div className="label">System</div>
            <div style={{ fontSize: 16, fontWeight: 800, color: 'var(--green-dark)' }}>● Event-sourced</div>
            <div style={{ fontSize: 13, color: 'var(--text-muted)', marginTop: 4 }}>Immutable audit trail</div>
          </div>
        </div>

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18 }}>
          <h2 style={{ fontSize: 20, fontWeight: 800 }}>Your accounts</h2>
          <button className="btn btn-primary" onClick={() => open('create')}>+ New account</button>
        </div>

        {loading ? (
          <div className="grid">{[1, 2, 3].map((i) => <div key={i} className="skel" />)}</div>
        ) : error ? (
          <div className="card" style={{ textAlign: 'center', padding: 48 }}>
            <div style={{ fontWeight: 800, marginBottom: 6 }}>Couldn't reach the server</div>
            <div style={{ color: 'var(--text-muted)', fontSize: 14, marginBottom: 18 }}>It may still be waking up. Give it a moment and retry.</div>
            <button className="btn btn-primary" onClick={() => { setLoading(true); load() }}>Retry</button>
          </div>
        ) : accounts.length === 0 ? (
          <div className="card" style={{ textAlign: 'center', padding: 48 }}>
            <div style={{ fontWeight: 800, marginBottom: 6 }}>No accounts yet</div>
            <div style={{ color: 'var(--text-muted)', fontSize: 14, marginBottom: 18 }}>Create your first account to get started.</div>
            <button className="btn btn-primary" onClick={() => open('create')}>Create account</button>
          </div>
        ) : (
          <div className="grid">
            {accounts.map((a) => {
              const [bg, fg] = TONE[a.accountType] || TONE.SAVINGS
              return (
                <div key={a.id} className="card acct">
                  <div className="acct-top">
                    <div className="ico" style={{ background: bg, color: fg }}>{a.accountType?.[0]}</div>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 800, fontSize: 14 }}>{a.accountType} account</div>
                      <button className="idbtn" onClick={() => copy(a.id)} title="Copy account ID">
                        {copied === a.id ? 'Copied!' : `${a.id.slice(0, 8)}… ⧉`}
                      </button>
                    </div>
                    <span className={`badge ${a.status === 'ACTIVE' ? 'badge-green' : 'badge-red'}`}>{a.status}</span>
                  </div>

                  <div className="label" style={{ marginBottom: 2 }}>Current balance</div>
                  <div className="bal" style={{ color: fg }}><Money v={a.currentBalance} /></div>
                  <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>Derived from event history</div>

                  <div className="actions">
                    <button className="btn btn-primary" onClick={() => open('deposit', a)}>Deposit</button>
                    <button className="btn btn-outline" onClick={() => open('withdraw', a)}>Withdraw</button>
                    <button className="btn" style={{ background: 'var(--blue-light)', color: 'var(--blue)' }} onClick={() => open('transfer', a)}>Transfer</button>
                  </div>
                  <button className="ghost" onClick={() => navigate(`/account/${a.id}`)}>View audit trail →</button>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {modal && (
        <div className="overlay" onClick={close}>
          <div className="card modal" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
            <button onClick={close} aria-label="Close" style={{ position: 'absolute', top: 16, right: 16, background: 'none', border: 'none', cursor: 'pointer', fontSize: 20, color: 'var(--text-muted)' }}>✕</button>
            <h3 style={{ fontSize: 20, fontWeight: 800, marginBottom: 4 }}>{TITLE[modal]}</h3>
            {acct && <p style={{ color: 'var(--text-muted)', fontSize: 13, marginBottom: 20 }}>{acct.accountType} · Balance {fmt(acct.currentBalance)}</p>}
            {!acct && <div style={{ height: 16 }} />}

            <form onSubmit={submit}>
              {modal === 'create' && (
                <div className="form-group">
                  <label className="label">Account type</label>
                  <select className="input" value={form.accountType || 'SAVINGS'} onChange={(e) => setForm({ ...form, accountType: e.target.value })}>
                    <option value="SAVINGS">Savings</option>
                    <option value="CURRENT">Current</option>
                    <option value="CREDIT">Credit</option>
                  </select>
                </div>
              )}

              {modal === 'transfer' && (
                <div className="form-group">
                  <label className="label">To account</label>
                  <select className="input" value={form.toAccountId || ''} onChange={(e) => setForm({ ...form, toAccountId: e.target.value })} required>
                    <option value="">Select account</option>
                    {accounts.filter((a) => a.id !== acct?.id).map((a) => (
                      <option key={a.id} value={a.id}>{a.accountType} · {fmt(a.currentBalance)}</option>
                    ))}
                  </select>
                </div>
              )}

              {modal !== 'create' && (
                <>
                  <div className="form-group">
                    <label className="label">Amount (₹)</label>
                    <input className="input" type="number" min="1" step="0.01" placeholder="Enter amount" autoFocus value={form.amount || ''} onChange={(e) => setForm({ ...form, amount: e.target.value })} required />
                    {modal === 'deposit' && (
                      <div className="chips">
                        {[500, 1000, 5000].map((v) => (
                          <button type="button" key={v} className="chip" onClick={() => setForm({ ...form, amount: String((parseFloat(form.amount) || 0) + v) })}>+{v.toLocaleString('en-IN')}</button>
                        ))}
                      </div>
                    )}
                  </div>
                  {over && <div className="error-msg">Amount is more than the available balance.</div>}
                  <div className="form-group">
                    <label className="label">Description (optional)</label>
                    <input className="input" type="text" placeholder="e.g. Rent payment" value={form.description || ''} onChange={(e) => setForm({ ...form, description: e.target.value })} />
                  </div>
                </>
              )}

              <button className={`btn ${modal === 'withdraw' ? 'btn-danger' : 'btn-primary'}`} type="submit" disabled={blocked} style={{ width: '100%', padding: 13 }}>
                {busy ? 'Processing…' : TITLE[modal]}
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}