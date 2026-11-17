import { useEffect, useMemo, useState } from 'react'
import Navbar from '../components/Navbar'
import { useAuth } from '../context/AuthContext'
import {
  getFlags, getFlagEvidence, replayFlag, decideFlag, getEvaluation, getModelInfo, runMuleRing
} from '../services/api'

const fmt = (n) => `₹${Number(n || 0).toLocaleString('en-IN', { maximumFractionDigits: 0 })}`
const fmtTime = (d) => new Date(d).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'medium' })
const pct = (x) => `${Math.round((x || 0) * 100)}%`
const shortId = (id) => (id ? `…${id.slice(-6)}` : '')

const RULES = {
  FAN_IN_NEW_ACCOUNT: { label: 'Many payers → new account', kind: 'Rule' },
  RAPID_PASS_THROUGH: { label: 'Rapid pass-through', kind: 'Rule' },
  ANOMALY_MODEL: { label: 'Anomaly model', kind: 'Model' },
}
const PATTERNS = {
  MULE_PASS_THROUGH: 'Pass-through',
  MULE_COLLECTOR: 'Collector',
  MULE_LAYERING: 'Layering',
  MULE_SLOW: 'Slow (below thresholds)',
}
const STATUS_CLASS = { OPEN: 'st-open', HOLD: 'st-hold', CLEAR: 'st-clear', ESCALATE: 'st-esc' }
const ruleName = (id) => RULES[id]?.label || id

function Evaluation({ evaluation, model }) {
  if (!evaluation) return <div className="card fr-eval"><div className="skel" style={{ height: 120 }} /></div>
  const rows = [...evaluation.detectors, evaluation.combined]
  return (
    <section className="card fr-eval" aria-labelledby="eval-h">
      <div className="fr-eval-head">
        <div>
          <h2 id="eval-h">Measured on held-out simulated data</h2>
          <p className="muted">
            {evaluation.accounts.toLocaleString('en-IN')} accounts over {evaluation.days} days,
            {' '}{evaluation.mules} planted mules, {evaluation.events.toLocaleString('en-IN')} ledger events.
            {' '}The model was trained on a different simulation.
          </p>
        </div>
        {model && <code className="fr-ver" title="Model version stored on every model flag">{model.version}</code>}
      </div>
      <div className="fr-table-wrap">
        <table className="fr-table">
          <thead>
            <tr>
              <th>Detector</th><th>Precision</th><th>Recall</th>
              {Object.values(PATTERNS).map((p) => <th key={p}>{p}</th>)}
            </tr>
          </thead>
          <tbody>
            {rows.map((d) => {
              const id = d.detector.replace(/ v\d+$/, '')
              return (
                <tr key={d.detector} className={d.detector === 'ALL' ? 'fr-total' : ''}>
                  <td>{d.detector === 'ALL' ? 'All detectors' : ruleName(id)}</td>
                  <td className="num">{pct(d.precision)}</td>
                  <td className="num">{pct(d.recall)}</td>
                  {Object.keys(PATTERNS).map((p) => (
                    <td key={p} className={`num ${(d.recallByPattern[p] || 0) >= 0.9 ? 'hit' : (d.recallByPattern[p] || 0) === 0 ? 'miss' : ''}`}>
                      {pct(d.recallByPattern[p])}
                    </td>
                  ))}
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      <p className="muted small">
        Precision: share of flagged accounts that are mules. Recall: share of mules flagged.
        {' '}Slow mules stay below every threshold today, so they are a known gap.
      </p>
    </section>
  )
}

function Replay({ replay }) {
  if (!replay) return null
  const same = (a, b) => JSON.stringify(a) === JSON.stringify(b)
  return (
    <div className={`fr-replay ${replay.matches ? 'ok' : 'bad'}`} role="status">
      <strong>{replay.matches ? 'Replay matches the original flag' : 'Replay differs from the original flag'}</strong>
      <ul>
        <li>Rebuilt from the ledger as of {fmtTime(replay.asOf)} with {replay.ruleId} v{replay.ruleVersion}, {replay.eventsConsidered} events considered</li>
        <li>Score: {replay.originalScore} then, {replay.replayedScore ?? 'no hit'} now {replay.originalScore === replay.replayedScore ? '✓' : '✗'}</li>
        <li>Reasons {same(replay.originalReasons, replay.replayedReasons) ? 'identical ✓' : 'differ ✗'}, evidence {same(replay.originalEvidence, replay.replayedEvidence) ? 'identical ✓' : 'differs ✗'}</li>
        <li>Balance at that moment: {fmt(replay.balanceAsOf)}</li>
      </ul>
    </div>
  )
}

function FlagDetail({ flag, onDecided }) {
  const [evidence, setEvidence] = useState(null)
  const [replay, setReplay] = useState(null)
  const [replaying, setReplaying] = useState(false)
  const [note, setNote] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    setEvidence(null); setReplay(null); setNote(''); setError('')
    getFlagEvidence(flag.id).then(setEvidence).catch(() => setError('Could not load the evidence.'))
  }, [flag.id])

  const doReplay = async () => {
    setReplaying(true); setError('')
    try { setReplay(await replayFlag(flag.id)) } catch (e) { setError(e.response?.data?.message || 'Replay failed.') }
    finally { setReplaying(false) }
  }

  const decide = async (decision) => {
    setSaving(true); setError('')
    try { onDecided(await decideFlag(flag.id, decision, note.trim() || null)); setNote('') }
    catch (e) { setError(e.response?.data?.message || 'Could not save the decision.') }
    finally { setSaving(false) }
  }

  const [headline, ...drivers] = flag.reasons
  const isModel = flag.ruleId === 'ANOMALY_MODEL'

  return (
    <article className="card fr-detail" aria-label="Flag detail">
      <header className="fr-detail-head">
        <div>
          <span className={`fr-kind ${isModel ? 'model' : 'rule'}`}>{RULES[flag.ruleId]?.kind || 'Rule'}</span>
          <h2>{ruleName(flag.ruleId)}</h2>
          <p className="muted">
            Account <code>{shortId(flag.accountId)}</code> · raised {fmtTime(flag.asOf)} · {flag.ruleId} v{flag.ruleVersion}
            {flag.modelVersion && <> · <code>{flag.modelVersion}</code></>}
          </p>
        </div>
        <div className="fr-score" aria-label={`Score ${flag.score}`}>
          <span>{Number(flag.score).toFixed(2)}</span><small>score</small>
        </div>
      </header>

      {error && <div className="error-msg">{error}</div>}

      <h3>Why it was flagged</h3>
      <ul className="fr-reasons">
        <li>{headline}</li>
        {drivers.map((r) => <li key={r}>{r}</li>)}
      </ul>

      <h3>Evidence <span className="muted">({flag.evidenceTransactionIds.length} transactions)</span></h3>
      {!evidence ? <div className="skel" style={{ height: 120 }} /> : (
        <div className="fr-table-wrap">
          <table className="fr-table fr-evidence">
            <thead><tr><th>Time</th><th>Direction</th><th className="num">Amount</th><th>Other party</th><th className="num">Balance after</th></tr></thead>
            <tbody>
              {evidence.map((e) => (
                <tr key={e.transactionId + e.direction}>
                  <td>{fmtTime(e.at)}</td>
                  <td><span className={`kind ${e.direction === 'CREDIT' ? 'cr' : 'dr'}`}>{e.direction === 'CREDIT' ? 'IN' : 'OUT'}</span></td>
                  <td className="num">{fmt(e.amount)}</td>
                  <td>{e.counterparty} {e.counterpartyAccountId && <code className="muted">{shortId(e.counterpartyAccountId)}</code>}</td>
                  <td className="num">{fmt(e.balanceAfter)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="fr-replay-row">
        <button className="btn btn-outline" onClick={doReplay} disabled={replaying}>
          {replaying ? 'Replaying…' : 'Replay as of the flag'}
        </button>
        <span className="muted small">Re-runs the same rule version on the ledger exactly as it stood then. Later activity is ignored.</span>
      </div>
      <Replay replay={replay} />

      <h3>Decision <span className={`fr-status ${STATUS_CLASS[flag.status]}`}>{flag.status}</span></h3>
      <label className="label" htmlFor="note">Note for the audit trail</label>
      <textarea id="note" className="input" rows={2} maxLength={1000} value={note}
                onChange={(e) => setNote(e.target.value)} placeholder="What you checked and why" />
      <div className="fr-actions">
        <button className="btn btn-danger" disabled={saving} onClick={() => decide('HOLD')}>Hold</button>
        <button className="btn btn-primary" disabled={saving} onClick={() => decide('CLEAR')}>Clear</button>
        <button className="btn fr-escalate" disabled={saving} onClick={() => decide('ESCALATE')}>Escalate</button>
      </div>
      {flag.decisions.length > 0 && (
        <ol className="fr-history">
          {flag.decisions.map((d) => (
            <li key={d.id || d.createdAt}>
              <span className={`fr-status ${STATUS_CLASS[d.decision]}`}>{d.decision}</span>
              {' '}by {d.analyst} · {fmtTime(d.createdAt)} · rule v{d.ruleVersion}{d.modelVersion ? ` · ${d.modelVersion}` : ''}
              {d.note && <div className="muted">“{d.note}”</div>}
            </li>
          ))}
        </ol>
      )}
      <p className="muted small">Decisions are appended, never edited, and record the rule and model version you reviewed.</p>
    </article>
  )
}

export default function FraudReview() {
  const { user } = useAuth()
  if (!user?.analyst) {
    return (
      <div style={{ minHeight: '100vh' }}>
        <Navbar />
        <main className="fr-page"><div className="card"><h1>Mule review</h1>
          <p className="muted" style={{ marginTop: 8 }}>This screen is for fraud analysts. Ask an administrator to add your email to the analyst list.</p>
        </div></main>
      </div>
    )
  }
  return <FraudReviewScreen />
}

function FraudReviewScreen() {
  const [flags, setFlags] = useState(null)
  const [selectedId, setSelectedId] = useState(null)
  const [filter, setFilter] = useState('OPEN')
  const [evaluation, setEvaluation] = useState(null)
  const [model, setModel] = useState(null)
  const [demo, setDemo] = useState(null)
  const [running, setRunning] = useState(false)
  const [error, setError] = useState('')

  const loadFlags = async (selectFirst) => {
    try {
      const data = await getFlags()
      setFlags(data)
      if (selectFirst && data.length) setSelectedId(data[0].id)
    } catch { setError('Could not load flags.') }
  }

  useEffect(() => {
    loadFlags(true)
    getModelInfo().then(setModel).catch(() => {})
    getEvaluation().then(setEvaluation).catch(() => {})
  }, [])

  const playDemo = async () => {
    setRunning(true); setError('')
    try {
      const result = await runMuleRing()
      setDemo(result)
      setFilter('OPEN')
      await loadFlags(false)
      const mule = result.flags.find((f) => f.accountRole.startsWith('Mule'))
      if (mule) setSelectedId(mule.flagId)
    } catch { setError('The demo scenario failed to run.') }
    finally { setRunning(false) }
  }

  const visible = useMemo(() => (flags || []).filter((f) => filter === 'ALL' || f.status === filter), [flags, filter])
  const selected = (flags || []).find((f) => f.id === selectedId)
  const counts = useMemo(() => {
    const c = { ALL: 0, OPEN: 0, HOLD: 0, CLEAR: 0, ESCALATE: 0 }
    ;(flags || []).forEach((f) => { c.ALL++; c[f.status]++ })
    return c
  }, [flags])

  const onDecided = (updated) => setFlags((prev) => prev.map((f) => (f.id === updated.id ? updated : f)))

  return (
    <div style={{ minHeight: '100vh' }}>
      <Navbar />
      <main className="fr-page">
        <div className="fr-top">
          <div>
            <h1>Mule review</h1>
            <p className="muted">TraceLedger flags accounts that behave like money mules. Every flag can be replayed exactly as it was raised.</p>
          </div>
          <button className="btn btn-primary" onClick={playDemo} disabled={running}>
            {running ? 'Playing the mule ring…' : 'Play a mule ring through the ledger'}
          </button>
        </div>

        {error && <div className="error-msg">{error}</div>}

        {demo && (
          <div className="banner fr-demo" role="status">
            <div>
              <strong>Mule ring played: {demo.transfers} payments, {demo.flags.length} flags raised</strong>
              <ul>{demo.story.map((s) => <li key={s}>{s}</li>)}</ul>
              <div className="muted small">
                Flagged: {[...new Set(demo.flags.map((f) => f.accountRole))].join(', ') || 'nobody'}.
                {' '}Not flagged: the ordinary customer, the employer and the 8 payers.
              </div>
            </div>
          </div>
        )}

        <Evaluation evaluation={evaluation} model={model} />

        <div className="fr-grid">
          <section className="card fr-queue" aria-labelledby="queue-h">
            <div className="fr-queue-head">
              <h2 id="queue-h">Flags</h2>
              <div className="tabs" role="tablist">
                {['OPEN', 'HOLD', 'ESCALATE', 'CLEAR', 'ALL'].map((s) => (
                  <button key={s} role="tab" aria-selected={filter === s} className={`tab ${filter === s ? 'on' : ''}`} onClick={() => setFilter(s)}>
                    {s === 'ALL' ? 'All' : s[0] + s.slice(1).toLowerCase()} <span className="muted">{counts[s]}</span>
                  </button>
                ))}
              </div>
            </div>
            {!flags ? <div className="skel" style={{ height: 200 }} /> : visible.length === 0 ? (
              <p className="muted fr-empty">No {filter === 'ALL' ? '' : filter.toLowerCase()} flags. Play the mule ring to raise some.</p>
            ) : (
              <ul className="fr-list">
                {visible.map((f) => (
                  <li key={f.id}>
                    <button className={`fr-item ${f.id === selectedId ? 'on' : ''}`} onClick={() => setSelectedId(f.id)}>
                      <div className="fr-item-top">
                        <span className={`fr-kind ${f.ruleId === 'ANOMALY_MODEL' ? 'model' : 'rule'}`}>{RULES[f.ruleId]?.kind || 'Rule'}</span>
                        <strong>{ruleName(f.ruleId)}</strong>
                        <span className={`fr-status ${STATUS_CLASS[f.status]}`}>{f.status}</span>
                      </div>
                      <div className="fr-item-meta">
                        <code>{shortId(f.accountId)}</code>
                        <span className="fr-bar" aria-hidden="true"><span style={{ width: `${Math.min(100, f.score * 100)}%` }} /></span>
                        <span className="num">{Number(f.score).toFixed(2)}</span>
                      </div>
                      <div className="muted small">{fmtTime(f.asOf)}</div>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>

          {selected ? <FlagDetail flag={selected} onDecided={onDecided} /> : (
            <div className="card fr-detail fr-empty"><p className="muted">Select a flag to see why it was raised.</p></div>
          )}
        </div>
      </main>
    </div>
  )
}
