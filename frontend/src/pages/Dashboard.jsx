import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { getAccounts, createAccount, deposit, withdraw, transfer } from '../services/api'
import Navbar from '../components/Navbar'

const fmt = (amt) => `₹${Number(amt).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`
const uid = () => `key-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`

export default function Dashboard() {
  const [accounts, setAccounts] = useState([])
  const [loading, setLoading] = useState(true)
  const [modal, setModal] = useState(null) // 'create' | 'deposit' | 'withdraw' | 'transfer'
  const [selectedAccount, setSelectedAccount] = useState(null)
  const [form, setForm] = useState({})
  const [msg, setMsg] = useState({ type: '', text: '' })
  const [submitting, setSubmitting] = useState(false)
  const navigate = useNavigate()

  const loadAccounts = async () => {
    try {
      const res = await getAccounts()
      setAccounts(res.data)
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadAccounts() }, [])

  const openModal = (type, account = null) => {
    setModal(type)
    setSelectedAccount(account)
    setForm({})
    setMsg({ type: '', text: '' })
  }

  const closeModal = () => { setModal(null); setSelectedAccount(null); setForm({}) }

  const showMsg = (type, text) => {
    setMsg({ type, text })
    setTimeout(() => setMsg({ type: '', text: '' }), 3000)
  }

  const handleCreate = async (e) => {
    e.preventDefault()
    setSubmitting(true)
    try {
      await createAccount({ accountType: form.accountType || 'SAVINGS', currency: 'INR' })
      await loadAccounts()
      closeModal()
      showMsg('success', 'Account created successfully!')
    } catch (err) {
      showMsg('error', err.response?.data?.message || 'Failed to create account')
    } finally { setSubmitting(false) }
  }

  const handleDeposit = async (e) => {
    e.preventDefault()
    setSubmitting(true)
    try {
      await deposit({ accountId: selectedAccount.id, amount: parseFloat(form.amount), description: form.description || 'Deposit', idempotencyKey: uid() })
      await loadAccounts()
      closeModal()
      showMsg('success', `₹${form.amount} deposited successfully!`)
    } catch (err) {
      showMsg('error', err.response?.data?.message || 'Deposit failed')
    } finally { setSubmitting(false) }
  }

  const handleWithdraw = async (e) => {
    e.preventDefault()
    setSubmitting(true)
    try {
      await withdraw({ accountId: selectedAccount.id, amount: parseFloat(form.amount), description: form.description || 'Withdrawal', idempotencyKey: uid() })
      await loadAccounts()
      closeModal()
      showMsg('success', `₹${form.amount} withdrawn successfully!`)
    } catch (err) {
      showMsg('error', err.response?.data?.message || 'Insufficient funds or error')
    } finally { setSubmitting(false) }
  }

  const handleTransfer = async (e) => {
    e.preventDefault()
    setSubmitting(true)
    try {
      await transfer({ fromAccountId: selectedAccount.id, toAccountId: form.toAccountId, amount: parseFloat(form.amount), description: form.description || 'Transfer', idempotencyKey: uid() })
      await loadAccounts()
      closeModal()
      showMsg('success', `₹${form.amount} transferred successfully!`)
    } catch (err) {
      showMsg('error', err.response?.data?.message || 'Transfer failed')
    } finally { setSubmitting(false) }
  }

  const totalBalance = accounts.reduce((sum, a) => sum + (a.currentBalance || 0), 0)

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)' }}>
      <Navbar />

      <div style={{ maxWidth: '1100px', margin: '0 auto', padding: '32px 24px' }}>

        {/* Success/Error Message */}
        {msg.text && (
          <div className={msg.type === 'success' ? 'success-msg' : 'error-msg'} style={{ marginBottom: '20px' }}>
            {msg.text}
          </div>
        )}

        {/* Stats Row */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '16px', marginBottom: '32px' }}>
          <div className="card" style={{ background: 'linear-gradient(135deg, #00C853, #00796B)', color: 'white', border: 'none' }}>
            <div style={{ fontSize: '13px', opacity: 0.85, marginBottom: '8px' }}>Total Balance</div>
            <div style={{ fontSize: '28px', fontWeight: '700' }}>{fmt(totalBalance)}</div>
            <div style={{ fontSize: '12px', opacity: 0.75, marginTop: '4px' }}>Across all accounts</div>
          </div>
          <div className="card">
            <div style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '8px' }}>Total Accounts</div>
            <div style={{ fontSize: '28px', fontWeight: '700', color: 'var(--text)' }}>{accounts.length}</div>
            <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '4px' }}>Active accounts</div>
          </div>
          <div className="card">
            <div style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '8px' }}>System</div>
            <div style={{ fontSize: '14px', fontWeight: '600', color: 'var(--green)' }}>● Event-Sourced</div>
            <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '4px' }}>Immutable audit trail</div>
          </div>
        </div>

        {/* Accounts Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
          <h2 style={{ fontSize: '18px', fontWeight: '600' }}>Your Accounts</h2>
          <button className="btn btn-primary" onClick={() => openModal('create')}>+ New Account</button>
        </div>

        {/* Accounts Grid */}
        {loading ? (
          <div className="card" style={{ textAlign: 'center', padding: '48px', color: 'var(--text-muted)' }}>Loading accounts...</div>
        ) : accounts.length === 0 ? (
          <div className="card" style={{ textAlign: 'center', padding: '48px' }}>
            <div style={{ fontSize: '40px', marginBottom: '12px' }}>💳</div>
            <div style={{ fontWeight: '600', marginBottom: '8px' }}>No accounts yet</div>
            <div style={{ color: 'var(--text-muted)', fontSize: '14px', marginBottom: '20px' }}>Create your first account to get started</div>
            <button className="btn btn-primary" onClick={() => openModal('create')}>Create Account</button>
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: '16px' }}>
            {accounts.map(account => (
              <div key={account.id} className="card" style={{ cursor: 'pointer', transition: 'box-shadow 0.2s' }}
                onMouseEnter={e => e.currentTarget.style.boxShadow = '0 4px 20px rgba(0,200,83,0.15)'}
                onMouseLeave={e => e.currentTarget.style.boxShadow = 'none'}>

                {/* Account Header */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '20px' }}>
                  <div>
                    <div style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '4px' }}>{account.accountType} ACCOUNT</div>
                    <div style={{ fontSize: '13px', color: 'var(--text-muted)', fontFamily: 'monospace' }}>{account.id.substring(0, 8)}...</div>
                  </div>
                  <span className={`badge ${account.status === 'ACTIVE' ? 'badge-green' : 'badge-red'}`}>{account.status}</span>
                </div>

                {/* Balance */}
                <div style={{ marginBottom: '20px' }}>
                  <div style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '4px' }}>Current Balance</div>
                  <div style={{ fontSize: '26px', fontWeight: '700', color: 'var(--green)' }}>{fmt(account.currentBalance)}</div>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>Derived from event history</div>
                </div>

                {/* Action Buttons */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '8px', marginBottom: '12px' }}>
                  <button className="btn btn-primary" style={{ padding: '8px', fontSize: '13px' }}
                    onClick={(e) => { e.stopPropagation(); openModal('deposit', account) }}>Deposit</button>
                  <button className="btn btn-outline" style={{ padding: '8px', fontSize: '13px' }}
                    onClick={(e) => { e.stopPropagation(); openModal('withdraw', account) }}>Withdraw</button>
                  <button className="btn" style={{ padding: '8px', fontSize: '13px', background: 'var(--blue-light)', color: 'var(--blue)', border: 'none' }}
                    onClick={(e) => { e.stopPropagation(); openModal('transfer', account) }}>Transfer</button>
                </div>

                <button onClick={() => navigate(`/account/${account.id}`)}
                  style={{ width: '100%', padding: '8px', background: 'none', border: '1px solid var(--border)', borderRadius: '8px', cursor: 'pointer', fontSize: '13px', color: 'var(--text-muted)' }}>
                  View Audit Trail →
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Modal */}
      {modal && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 200, padding: '20px' }}
          onClick={closeModal}>
          <div className="card" style={{ width: '100%', maxWidth: '420px', position: 'relative' }} onClick={e => e.stopPropagation()}>
            <button onClick={closeModal} style={{ position: 'absolute', top: '16px', right: '16px', background: 'none', border: 'none', cursor: 'pointer', fontSize: '20px', color: 'var(--text-muted)' }}>✕</button>

            {modal === 'create' && (
              <>
                <h3 style={{ fontSize: '18px', fontWeight: '600', marginBottom: '20px' }}>Create New Account</h3>
                <form onSubmit={handleCreate}>
                  <div className="form-group">
                    <label className="label">Account Type</label>
                    <select className="input" value={form.accountType || 'SAVINGS'} onChange={e => setForm({ ...form, accountType: e.target.value })}>
                      <option value="SAVINGS">Savings</option>
                      <option value="CURRENT">Current</option>
                      <option value="CREDIT">Credit</option>
                    </select>
                  </div>
                  <button className="btn btn-primary" type="submit" disabled={submitting} style={{ width: '100%', padding: '12px' }}>
                    {submitting ? 'Creating...' : 'Create Account'}
                  </button>
                </form>
              </>
            )}

            {modal === 'deposit' && (
              <>
                <h3 style={{ fontSize: '18px', fontWeight: '600', marginBottom: '4px' }}>Deposit Money</h3>
                <p style={{ color: 'var(--text-muted)', fontSize: '13px', marginBottom: '20px' }}>Current balance: {fmt(selectedAccount?.currentBalance)}</p>
                <form onSubmit={handleDeposit}>
                  <div className="form-group">
                    <label className="label">Amount (₹)</label>
                    <input className="input" type="number" min="1" placeholder="Enter amount" value={form.amount || ''} onChange={e => setForm({ ...form, amount: e.target.value })} required />
                  </div>
                  <div className="form-group">
                    <label className="label">Description (optional)</label>
                    <input className="input" type="text" placeholder="e.g. Salary credit" value={form.description || ''} onChange={e => setForm({ ...form, description: e.target.value })} />
                  </div>
                  <button className="btn btn-primary" type="submit" disabled={submitting} style={{ width: '100%', padding: '12px' }}>
                    {submitting ? 'Processing...' : 'Deposit'}
                  </button>
                </form>
              </>
            )}

            {modal === 'withdraw' && (
              <>
                <h3 style={{ fontSize: '18px', fontWeight: '600', marginBottom: '4px' }}>Withdraw Money</h3>
                <p style={{ color: 'var(--text-muted)', fontSize: '13px', marginBottom: '20px' }}>Available balance: {fmt(selectedAccount?.currentBalance)}</p>
                <form onSubmit={handleWithdraw}>
                  <div className="form-group">
                    <label className="label">Amount (₹)</label>
                    <input className="input" type="number" min="1" placeholder="Enter amount" value={form.amount || ''} onChange={e => setForm({ ...form, amount: e.target.value })} required />
                  </div>
                  <div className="form-group">
                    <label className="label">Description (optional)</label>
                    <input className="input" type="text" placeholder="e.g. ATM withdrawal" value={form.description || ''} onChange={e => setForm({ ...form, description: e.target.value })} />
                  </div>
                  <button className="btn btn-danger" type="submit" disabled={submitting} style={{ width: '100%', padding: '12px' }}>
                    {submitting ? 'Processing...' : 'Withdraw'}
                  </button>
                </form>
              </>
            )}

            {modal === 'transfer' && (
              <>
                <h3 style={{ fontSize: '18px', fontWeight: '600', marginBottom: '4px' }}>Transfer Money</h3>
                <p style={{ color: 'var(--text-muted)', fontSize: '13px', marginBottom: '20px' }}>From: {selectedAccount?.accountType} • Balance: {fmt(selectedAccount?.currentBalance)}</p>
                <form onSubmit={handleTransfer}>
                  <div className="form-group">
                    <label className="label">To Account</label>
                    <select className="input" value={form.toAccountId || ''} onChange={e => setForm({ ...form, toAccountId: e.target.value })} required>
                      <option value="">Select account</option>
                      {accounts.filter(a => a.id !== selectedAccount?.id).map(a => (
                        <option key={a.id} value={a.id}>{a.accountType} — {fmt(a.currentBalance)}</option>
                      ))}
                    </select>
                  </div>
                  <div className="form-group">
                    <label className="label">Amount (₹)</label>
                    <input className="input" type="number" min="1" placeholder="Enter amount" value={form.amount || ''} onChange={e => setForm({ ...form, amount: e.target.value })} required />
                  </div>
                  <div className="form-group">
                    <label className="label">Description (optional)</label>
                    <input className="input" type="text" placeholder="e.g. Rent payment" value={form.description || ''} onChange={e => setForm({ ...form, description: e.target.value })} />
                  </div>
                  <button className="btn btn-primary" type="submit" disabled={submitting} style={{ width: '100%', padding: '12px' }}>
                    {submitting ? 'Transferring...' : 'Transfer'}
                  </button>
                </form>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
