import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { getAccount, getEvents, getBalance, getBalanceAsOf } from '../services/api'
import Navbar from '../components/Navbar'

const fmt = (amt) => `₹${Number(amt).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`
const fmtDate = (d) => new Date(d).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })

export default function AccountDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [account, setAccount] = useState(null)
  const [events, setEvents] = useState([])
  const [loading, setLoading] = useState(true)
  const [asOf, setAsOf] = useState('')
  const [asOfBalance, setAsOfBalance] = useState(null)
  const [querying, setQuerying] = useState(false)

  useEffect(() => {
    const load = async () => {
      try {
        const [accRes, eventsRes] = await Promise.all([getAccount(id), getEvents(id)])
        setAccount(accRes.data)
        setEvents(eventsRes.data)
      } catch (e) { console.error(e) }
      finally { setLoading(false) }
    }
    load()
  }, [id])

  const handlePointInTime = async (e) => {
    e.preventDefault()
    if (!asOf) return
    setQuerying(true)
    try {
      const formatted = new Date(asOf).toISOString().slice(0, 19)
      const res = await getBalanceAsOf(id, formatted)
      setAsOfBalance(res.data)
    } catch (e) { console.error(e) }
    finally { setQuerying(false) }
  }

  if (loading) return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)' }}>
      <Navbar />
      <div style={{ maxWidth: '900px', margin: '32px auto', padding: '0 24px', textAlign: 'center', color: 'var(--text-muted)' }}>Loading...</div>
    </div>
  )

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)' }}>
      <Navbar />
      <div style={{ maxWidth: '900px', margin: '0 auto', padding: '32px 24px' }}>

        {/* Back */}
        <button onClick={() => navigate('/dashboard')} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--green)', fontWeight: '500', fontSize: '14px', marginBottom: '20px', padding: 0 }}>
          ← Back to Dashboard
        </button>

        {/* Account Header */}
        <div className="card" style={{ marginBottom: '20px', background: 'linear-gradient(135deg, #00C853, #00796B)', border: 'none', color: 'white' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div>
              <div style={{ fontSize: '13px', opacity: 0.85, marginBottom: '6px' }}>{account?.accountType} ACCOUNT</div>
              <div style={{ fontSize: '13px', opacity: 0.7, fontFamily: 'monospace', marginBottom: '16px' }}>{account?.id}</div>
              <div style={{ fontSize: '13px', opacity: 0.85, marginBottom: '4px' }}>Current Balance</div>
              <div style={{ fontSize: '32px', fontWeight: '700' }}>{fmt(account?.currentBalance)}</div>
              <div style={{ fontSize: '12px', opacity: 0.75, marginTop: '4px' }}>Derived from {events.length} immutable event{events.length !== 1 ? 's' : ''}</div>
            </div>
            <div style={{ textAlign: 'right' }}>
              <div style={{ background: 'rgba(255,255,255,0.2)', padding: '6px 14px', borderRadius: '20px', fontSize: '13px' }}>{account?.status}</div>
              <div style={{ fontSize: '12px', opacity: 0.75, marginTop: '8px' }}>Owner: {account?.ownerName}</div>
            </div>
          </div>
        </div>

        {/* Point-in-Time Query */}
        <div className="card" style={{ marginBottom: '20px' }}>
          <h3 style={{ fontSize: '16px', fontWeight: '600', marginBottom: '4px' }}>⏱ Point-in-Time Balance Query</h3>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '16px' }}>Query what the balance was at any point in history — impossible with traditional CRUD systems.</p>
          <form onSubmit={handlePointInTime} style={{ display: 'flex', gap: '12px', alignItems: 'flex-end' }}>
            <div style={{ flex: 1 }}>
              <label className="label">Select date and time</label>
              <input className="input" type="datetime-local" value={asOf} onChange={e => setAsOf(e.target.value)} required />
            </div>
            <button className="btn btn-primary" type="submit" disabled={querying} style={{ whiteSpace: 'nowrap', padding: '10px 20px' }}>
              {querying ? 'Querying...' : 'Query Balance'}
            </button>
          </form>
          {asOfBalance && (
            <div style={{ marginTop: '16px', padding: '16px', background: 'var(--green-light)', borderRadius: '8px' }}>
              <div style={{ fontSize: '13px', color: 'var(--text-muted)' }}>Balance as of {fmtDate(asOfBalance.asOf)}</div>
              <div style={{ fontSize: '24px', fontWeight: '700', color: 'var(--green-dark)' }}>{fmt(asOfBalance.balance)}</div>
              <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '4px' }}>{asOfBalance.note}</div>
            </div>
          )}
        </div>

        {/* Event History */}
        <div className="card">
          <h3 style={{ fontSize: '16px', fontWeight: '600', marginBottom: '4px' }}>📋 Immutable Audit Trail</h3>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '20px' }}>Every event is permanently recorded and can never be modified or deleted.</p>

          {events.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '32px', color: 'var(--text-muted)' }}>No events yet. Make a deposit to get started.</div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {[...events].reverse().map((event, i) => (
                <div key={event.id} style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '16px', background: 'var(--bg)', borderRadius: '10px', border: '1px solid var(--border)' }}>
                  {/* Icon */}
                  <div style={{ width: '40px', height: '40px', borderRadius: '50%', background: event.eventType === 'CREDIT' ? 'var(--green-light)' : 'var(--red-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '18px', flexShrink: 0 }}>
                    {event.eventType === 'CREDIT' ? '↓' : '↑'}
                  </div>

                  {/* Details */}
                  <div style={{ flex: 1 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                      <div>
                        <div style={{ fontWeight: '500', fontSize: '14px' }}>{event.description || event.eventType}</div>
                        <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '2px' }}>{fmtDate(event.createdAt)}</div>
                        <div style={{ fontSize: '11px', color: 'var(--text-muted)', fontFamily: 'monospace', marginTop: '2px' }}>txn: {event.transactionId?.substring(0, 16)}...</div>
                      </div>
                      <div style={{ textAlign: 'right' }}>
                        <div style={{ fontWeight: '600', fontSize: '16px', color: event.eventType === 'CREDIT' ? 'var(--green)' : 'var(--red)' }}>
                          {event.eventType === 'CREDIT' ? '+' : '-'}{fmt(event.amount)}
                        </div>
                        <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '2px' }}>Balance: {fmt(event.balanceAfter)}</div>
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
