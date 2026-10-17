import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { getAccount, getEvents, getBalanceAsOf } from '../services/api'
import Navbar from '../components/Navbar'

const fmt = (n) => `₹${Number(n || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`
const fmtDate = (d) => new Date(d).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })
const FILTERS = [['ALL', 'All'], ['CREDIT', 'Credits'], ['DEBIT', 'Debits']]

const Money = ({ v }) => {
  const [whole, dec] = fmt(v).split('.')
  return <>{whole}<span className="paise">.{dec}</span></>
}

export default function AccountDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [account, setAccount] = useState(null)
  const [events, setEvents] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [filter, setFilter] = useState('ALL')
  const [copied, setCopied] = useState(false)
  const [asOf, setAsOf] = useState('')
  const [asOfBalance, setAsOfBalance] = useState(null)
  const [querying, setQuerying] = useState(false)
  const [queryError, setQueryError] = useState('')

  const load = async () => {
    setError(false)
    try {
      const [accRes, eventsRes] = await Promise.all([getAccount(id), getEvents(id)])
      setAccount(accRes.data)
      setEvents(eventsRes.data)
    } catch { setError(true) }
    finally { setLoading(false) }
  }

  useEffect(() => { load() }, [id])

  const handlePointInTime = async (e) => {
    e.preventDefault()
    if (!asOf) return
    setQuerying(true)
    setQueryError('')
    try {
      const formatted = new Date(asOf).toISOString().slice(0, 19)
      const res = await getBalanceAsOf(id, formatted)
      setAsOfBalance(res.data)
    } catch (err) {
      setAsOfBalance(null)
      setQueryError(err.response?.data?.message || 'Could not fetch the balance for that moment.')
    } finally { setQuerying(false) }
  }

  const copyId = () => {
    navigator.clipboard?.writeText(account.id)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  const isCredit = (ev) => ev.eventType === 'CREDIT'
  const credits = events.filter(isCredit).reduce((s, ev) => s + Number(ev.amount || 0), 0)
  const debits = events.filter((ev) => !isCredit(ev)).reduce((s, ev) => s + Number(ev.amount || 0), 0)
  const reconciles = events.length > 0 && Math.abs(credits - debits - Number(account?.currentBalance || 0)) < 0.01
  const shown = [...events].reverse().filter((ev) => filter === 'ALL' || (filter === 'CREDIT') === isCredit(ev))

  const shell = (children) => (
    <div style={{ minHeight: '100vh', background: 'var(--bg)' }}>
      <Navbar />
      <div style={{ maxWidth: 900, margin: '0 auto', padding: '32px 24px' }}>{children}</div>
    </div>
  )

  if (loading) return shell(<><div className="skel" style={{ height: 210, marginBottom: 20 }} /><div className="skel" style={{ height: 320 }} /></>)

  if (error) return shell(
    <div className="card" style={{ textAlign: 'center', padding: 48 }}>
      <div style={{ fontWeight: 800, marginBottom: 6 }}>Couldn't load this account</div>
      <div style={{ color: 'var(--text-muted)', fontSize: 14, marginBottom: 18 }}>The server may be waking up. Give it a moment and retry.</div>
      <button className="btn btn-primary" onClick={() => { setLoading(true); load() }}>Retry</button>
    </div>
  )

  return shell(
    <>
      <button onClick={() => navigate('/dashboard')} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--green-dark)', fontWeight: 700, fontSize: 14, marginBottom: 18, padding: 0 }}>
        ← Back to dashboard
      </button>

      <div className="card hero" style={{ marginBottom: 20 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
          <div>
            <small>{account?.accountType} account</small>
            <div className="big"><Money v={account?.currentBalance} /></div>
            <span className="pill">Derived from {events.length} immutable event{events.length === 1 ? '' : 's'}</span>
          </div>
          <div style={{ textAlign: 'right' }}>
            <span className="pill" style={{ background: 'rgba(0,200,83,.25)' }}>{account?.status}</span>
            <div style={{ fontSize: 13, opacity: 0.75, marginTop: 10 }}>Owner: {account?.ownerName}</div>
            <button className="idbtn" onClick={copyId} style={{ color: 'rgba(255,255,255,.7)', marginTop: 6 }}>
              {copied ? 'Copied!' : `${account?.id?.slice(0, 13)}… ⧉`}
            </button>
          </div>
        </div>
        {events.length > 0 && (
          <div className="stat-row">
            <span className="pill">Credits in {fmt(credits)}</span>
            <span className="pill">Debits out {fmt(debits)}</span>
            {reconciles && <span className="pill" style={{ background: 'rgba(0,200,83,.3)' }}>✓ Events reconcile with balance</span>}
          </div>
        )}
      </div>

      <div className="card" style={{ marginBottom: 20 }}>
        <h3 style={{ fontSize: 17, fontWeight: 800, marginBottom: 4 }}>Time travel: balance at any moment</h3>
        <p style={{ fontSize: 13, color: 'var(--text-muted)', marginBottom: 16 }}>Replay the event history up to any point in time. A plain CRUD table can't answer this, because it only stores the latest value.</p>
        <form onSubmit={handlePointInTime} style={{ display: 'flex', gap: 12, alignItems: 'flex-end', flexWrap: 'wrap' }}>
          <div style={{ flex: 1, minWidth: 220 }}>
            <label className="label">Date and time</label>
            <input className="input" type="datetime-local" value={asOf} onChange={(e) => setAsOf(e.target.value)} required />
          </div>
          <button className="btn btn-primary" type="submit" disabled={querying} style={{ padding: '11px 22px' }}>
            {querying ? 'Replaying…' : 'Query balance'}
          </button>
        </form>
        {queryError && <div className="error-msg" style={{ marginTop: 14, marginBottom: 0 }}>{queryError}</div>}
        {asOfBalance && (
          <div style={{ marginTop: 16, padding: 16, background: 'var(--green-light)', borderRadius: 12 }}>
            <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>Balance as of {fmtDate(asOfBalance.asOf)}</div>
            <div style={{ fontSize: 28, fontWeight: 800, color: 'var(--green-dark)' }}><Money v={asOfBalance.balance} /></div>
            {asOfBalance.note && <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 4 }}>{asOfBalance.note}</div>}
          </div>
        )}
      </div>

      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
          <div>
            <h3 style={{ fontSize: 17, fontWeight: 800, marginBottom: 4 }}>Immutable audit trail</h3>
            <p style={{ fontSize: 13, color: 'var(--text-muted)' }}>Every event is permanently recorded and can never be edited or deleted.</p>
          </div>
          <div className="tabs">
            {FILTERS.map(([key, label]) => (
              <button key={key} className={`tab${filter === key ? ' on' : ''}`} onClick={() => setFilter(key)}>{label}</button>
            ))}
          </div>
        </div>

        {events.length === 0 ? (
          <div style={{ textAlign: 'center', padding: 40, color: 'var(--text-muted)' }}>No events yet. Make a deposit to get started.</div>
        ) : shown.length === 0 ? (
          <div style={{ textAlign: 'center', padding: 40, color: 'var(--text-muted)' }}>No events match this filter.</div>
        ) : (
          <div className="timeline">
            {shown.map((ev) => {
              const cr = isCredit(ev)
              return (
                <div key={ev.id} className="ev">
                  <span className={`node ${cr ? 'cr' : 'dr'}`}>{cr ? '↓' : '↑'}</span>
                  <div>
                    <div style={{ fontWeight: 700, fontSize: 14 }}>
                      {ev.description || ev.eventType}
                      <span className={`kind ${cr ? 'cr' : 'dr'}`}>{cr ? 'CREDIT' : 'DEBIT'}</span>
                    </div>
                    <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 3 }}>{fmtDate(ev.createdAt)}</div>
                    <div style={{ fontSize: 11, fontFamily: 'var(--mono)', color: 'var(--text-muted)', marginTop: 3 }}>txn {ev.transactionId?.slice(0, 16)}…</div>
                  </div>
                  <div className="right" style={{ textAlign: 'right' }}>
                    <div style={{ fontWeight: 800, fontSize: 17, color: cr ? 'var(--green-dark)' : 'var(--red)' }}>{cr ? '+' : '−'}{fmt(ev.amount)}</div>
                    <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 3 }}>Balance after <strong style={{ color: 'var(--text)' }}>{fmt(ev.balanceAfter)}</strong></div>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </>
  )
}