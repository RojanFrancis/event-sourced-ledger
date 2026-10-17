import { useState } from "react";
import { runReconciliation, askSettlementAgent, getAccounts } from "../services/api";

const cleanAI = (text = '') =>
  String(text)
    .replace(/\*\*/g, '')
    .replace(/`/g, '')
    .replace(/^\s*-{3,}\s*$/gm, '')
    .replace(/^#{1,6}\s*/gm, '')
    .replace(/^\s*[*-]\s+/gm, '• ')
    .replace(/\n{3,}/g, '\n\n')
    .trim();

const TAB = { RECONCILE: "reconcile", AGENT: "agent" };

export default function AIController() {
  const [activeTab, setActiveTab] = useState(TAB.RECONCILE);

  // ── Batch Reconciliation state ──────────────────────────────────────────────
  const [batchId, setBatchId] = useState("batch-001");
  const [records, setRecords] = useState(
    JSON.stringify([
      { transactionId: "txn-001", accountId: "acc-001", type: "CREDIT", amount: 5000 },
      { transactionId: "txn-001", accountId: "acc-002", type: "DEBIT",  amount: 5000 }
    ], null, 2)
  );
  const [recordsError, setRecordsError] = useState(null);
  const [recoLoading, setRecoLoading] = useState(false);
  const [recoResult, setRecoResult] = useState(null);
  const [recoError, setRecoError]   = useState(null);

  // ── Settlement Q&A state ────────────────────────────────────────────────────
  const [accountId, setAccountId] = useState("");
  const [question, setQuestion]   = useState("");
  const [agentLoading, setAgentLoading] = useState(false);
  const [agentAnswer, setAgentAnswer]   = useState(null);
  const [agentError, setAgentError]     = useState(null);

  // ── Reconcile handler ───────────────────────────────────────────────────────
  const handleReconcile = async () => {
    let parsedRecords;
    try {
      parsedRecords = JSON.parse(records);
      setRecordsError(null);
    } catch {
      setRecordsError("Invalid JSON — check the records format.");
      return;
    }
    setRecoLoading(true);
    setRecoResult(null);
    setRecoError(null);
    try {
      const data = await runReconciliation({ batchId, records: parsedRecords });
      setRecoResult(data);
    } catch (err) {
      setRecoError(err.response?.data?.message || err.message || "Reconciliation failed.");
    } finally {
      setRecoLoading(false);
    }
  };

  // ── Agent handler ───────────────────────────────────────────────────────────
  const handleAsk = async () => {
    if (!question.trim() || !accountId.trim()) return;
    setAgentLoading(true);
    setAgentAnswer(null);
    setAgentError(null);
    try {
      const data = await askSettlementAgent(accountId, question);
      setAgentAnswer(data);
    } catch (err) {
      setAgentError(err.response?.data?.message || err.message || "Agent query failed.");
    } finally {
      setAgentLoading(false);
    }
  };

  return (
    <div style={styles.page}>
      <div style={styles.container}>
        {/* Header */}
        <div style={styles.header}>
          <div style={styles.headerBadge}>AI Finance Controller</div>
          <h1 style={styles.title}>AI-Powered Ledger Tools</h1>
          <p style={styles.subtitle}>Gemini 3.6 Flash · Track 04 — Razorpay AI Builder 2026</p>
        </div>

        {/* Tabs */}
        <div style={styles.tabBar}>
          <button style={activeTab === TAB.RECONCILE ? styles.tabActive : styles.tab} onClick={() => setActiveTab(TAB.RECONCILE)}>
            Batch Reconciliation
          </button>
          <button style={activeTab === TAB.AGENT ? styles.tabActive : styles.tab} onClick={() => setActiveTab(TAB.AGENT)}>
            Settlement Q&amp;A Agent
          </button>
        </div>

        {/* ── Batch Reconciliation ─────────────────────────────────────────── */}
        {activeTab === TAB.RECONCILE && (
          <div style={styles.panel}>
            <div style={styles.panelHeader}>
              <h2 style={styles.panelTitle}>Batch Reconciliation</h2>
              <p style={styles.panelDesc}>
                Submit a batch of transaction records. Gemini checks double-entry balance,
                detects mismatches and anomalies, and returns an AI executive summary.
              </p>
            </div>

            {/* Batch ID */}
            <label style={styles.label}>Batch ID</label>
            <input
              style={{ ...styles.input, marginBottom: 16 }}
              value={batchId}
              onChange={e => setBatchId(e.target.value)}
              placeholder="batch-001"
            />

            {/* Records JSON */}
            <label style={styles.label}>Transaction Records (JSON array)</label>
            <textarea
              style={styles.textarea}
              value={records}
              onChange={e => setRecords(e.target.value)}
              rows={10}
            />
            {recordsError && <div style={styles.errorBox}>⚠ {recordsError}</div>}

            <button
              style={recoLoading ? styles.btnDisabled : styles.btn}
              onClick={handleReconcile}
              disabled={recoLoading}
            >
              {recoLoading ? "Running…" : "Run Reconciliation"}
            </button>

            {recoError && <div style={{ ...styles.errorBox, marginTop: 16 }}>⚠ {recoError}</div>}

            {recoResult && (
              <div style={styles.resultBox}>
                <div style={styles.resultHeader}><span style={styles.successDot} /> Reconciliation Complete</div>

                {/* Summary chips */}
                <div style={styles.chipRow}>
                  <Chip label="Total Records" value={recoResult.totalRecords} />
                  <Chip label="Matched" value={recoResult.matchedCount} />
                  <Chip label="Exceptions" value={recoResult.exceptionCount} accent={recoResult.exceptionCount > 0} />
                  <Chip label="Match Rate" value={recoResult.matchRate} />
                  <Chip label="Status" value={recoResult.status} />
                </div>

                {/* Financials */}
                <div style={styles.chipRow}>
                  <Chip label="Total Credits" value={`₹${recoResult.totalCredits}`} />
                  <Chip label="Total Debits"  value={`₹${recoResult.totalDebits}`} />
                  <Chip label="Net Balance"   value={`₹${recoResult.netBalance}`} accent={recoResult.netBalance < 0} />
                </div>

                {/* AI Summary */}
                {recoResult.aiSummary && (
                  <div style={styles.narrative}>
                    <div style={styles.narrativeLabel}>AI Executive Summary</div>
                    <p style={styles.narrativeText}>{cleanAI(recoResult.aiSummary)}</p>
                  </div>
                )}

                {/* Exceptions */}
                {recoResult.exceptions && recoResult.exceptions.length > 0 && (
                  <div style={{ marginTop: 16 }}>
                    <div style={styles.narrativeLabel}>Flagged Exceptions</div>
                    {recoResult.exceptions.map((ex, i) => (
                      <div key={i} style={styles.issueItem}>
                        <span style={{ ...styles.issueIdx, background: ex.severity === "HIGH" ? "#e05c4f20" : "#f59e0b20", color: ex.severity === "HIGH" ? "#e05c4f" : "#f59e0b" }}>{ex.severity}</span>
                        <div>
                          <div style={{ fontSize: 13, fontWeight: 600 }}>TxnID: {ex.transactionId} · {ex.type} · ₹{ex.amount}</div>
                          <div style={{ fontSize: 12, color: "#94a3b8", marginTop: 2 }}>{ex.reason}</div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {/* ── Settlement Q&A Agent ─────────────────────────────────────────── */}
        {activeTab === TAB.AGENT && (
          <div style={styles.panel}>
            <div style={styles.panelHeader}>
              <h2 style={styles.panelTitle}>Settlement Q&amp;A Agent</h2>
              <p style={styles.panelDesc}>
                Enter an account ID and ask any plain-English question.
                Gemini reads the real ledger events and explains.
              </p>
            </div>

            <label style={styles.label}>Account ID</label>
            <input
              style={{ ...styles.input, marginBottom: 12 }}
              value={accountId}
              onChange={e => setAccountId(e.target.value)}
              placeholder="Paste your account UUID here"
            />

            <label style={styles.label}>Question</label>
            <div style={styles.inputRow}>
              <input
                style={styles.input}
                value={question}
                onChange={e => setQuestion(e.target.value)}
                onKeyDown={e => e.key === "Enter" && handleAsk()}
                placeholder='e.g. "Why does this account have a negative balance?"'
              />
              <button
                style={agentLoading || !question.trim() || !accountId.trim() ? styles.btnDisabled : styles.btn}
                onClick={handleAsk}
                disabled={agentLoading || !question.trim() || !accountId.trim()}
              >
                {agentLoading ? "Thinking…" : "Ask"}
              </button>
            </div>

            {/* Example questions */}
            {!agentAnswer && !agentLoading && (
              <div style={styles.examples}>
                {[
                  "Is the current balance correct?",
                  "Are there any anomalies in this account?",
                  "What happened to this account recently?",
                ].map(ex => (
                  <button key={ex} style={styles.exampleChip} onClick={() => setQuestion(ex)}>{ex}</button>
                ))}
              </div>
            )}

            {agentError && <div style={{ ...styles.errorBox, marginTop: 16 }}>⚠ {agentError}</div>}

            {agentAnswer && (
              <div style={styles.resultBox}>
                <div style={styles.resultHeader}><span style={styles.successDot} /> Agent Response</div>
                <div style={styles.questionEcho}>Q: {agentAnswer.question}</div>
                <div style={styles.narrative}>
                  <div style={styles.narrativeLabel}>AI Answer</div>
                 <p style={styles.narrativeText}>{cleanAI(agentAnswer.answer)}</p>
                </div>
                {agentAnswer.reasoning && (
                  <div style={{ fontSize: 12, color: "#64748b", marginTop: 10 }}>
                    📊 {agentAnswer.reasoning} · {agentAnswer.dataUsed}
                  </div>
                )}
                <button style={styles.clearBtn} onClick={() => { setAgentAnswer(null); setQuestion(""); }}>
                  Ask another question
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function Chip({ label, value, accent }) {
  return (
    <div style={{ ...styles.chip, ...(accent ? styles.chipAccent : {}) }}>
      <div style={styles.chipLabel}>{label}</div>
      <div style={styles.chipValue}>{String(value)}</div>
    </div>
  );
}

const C = {
  bg: "#0f1117", surface: "#161b27", surfaceHover: "#1c2333",
  border: "#252d3d", accent: "#4f6ef7", accentWarn: "#e05c4f",
  text: "#e2e8f0", textMuted: "#64748b", textDim: "#94a3b8", success: "#22c55e",
};

const styles = {
  page: { minHeight: "100vh", background: C.bg, padding: "40px 20px", fontFamily: "'Inter','Segoe UI',system-ui,sans-serif", color: C.text },
  container: { maxWidth: 800, margin: "0 auto" },
  header: { marginBottom: 32 },
  headerBadge: { display: "inline-block", fontSize: 11, fontWeight: 600, letterSpacing: "0.08em", color: C.accent, border: `1px solid ${C.accent}40`, borderRadius: 4, padding: "3px 10px", marginBottom: 14, background: `${C.accent}12` },
  title: { fontSize: 28, fontWeight: 700, margin: "0 0 6px", letterSpacing: "-0.02em" },
  subtitle: { fontSize: 13, color: C.textMuted, margin: 0 },
  tabBar: { display: "flex", gap: 4, background: C.surface, border: `1px solid ${C.border}`, borderRadius: 10, padding: 4, marginBottom: 24 },
  tab: { flex: 1, padding: "9px 16px", border: "none", borderRadius: 7, background: "transparent", color: C.textMuted, fontSize: 14, fontWeight: 500, cursor: "pointer" },
  tabActive: { flex: 1, padding: "9px 16px", border: "none", borderRadius: 7, background: C.surfaceHover, color: C.text, fontSize: 14, fontWeight: 600, cursor: "pointer", boxShadow: "0 1px 3px rgba(0,0,0,0.3)" },
  panel: { background: C.surface, border: `1px solid ${C.border}`, borderRadius: 12, padding: "28px" },
  panelHeader: { marginBottom: 24 },
  panelTitle: { fontSize: 18, fontWeight: 600, margin: "0 0 8px" },
  panelDesc: { fontSize: 14, color: C.textDim, margin: 0, lineHeight: 1.55 },
  label: { display: "block", fontSize: 12, fontWeight: 600, color: C.textMuted, marginBottom: 6, letterSpacing: "0.04em" },
  input: { width: "100%", background: "#0f1117", border: `1px solid ${C.border}`, borderRadius: 8, padding: "10px 14px", fontSize: 14, color: C.text, outline: "none", boxSizing: "border-box" },
  textarea: { width: "100%", background: "#0f1117", border: `1px solid ${C.border}`, borderRadius: 8, padding: "12px 14px", fontSize: 12, color: C.textDim, outline: "none", fontFamily: "monospace", resize: "vertical", marginBottom: 16, boxSizing: "border-box" },
  inputRow: { display: "flex", gap: 10, marginBottom: 12 },
  btn: { background: C.accent, color: "#fff", border: "none", borderRadius: 8, padding: "10px 22px", fontSize: 14, fontWeight: 600, cursor: "pointer", marginTop: 4 },
  btnDisabled: { background: C.border, color: C.textMuted, border: "none", borderRadius: 8, padding: "10px 22px", fontSize: 14, fontWeight: 600, cursor: "not-allowed", marginTop: 4 },
  examples: { display: "flex", flexWrap: "wrap", gap: 8, marginTop: 8 },
  exampleChip: { background: "transparent", border: `1px solid ${C.border}`, borderRadius: 20, padding: "5px 14px", fontSize: 12, color: C.textDim, cursor: "pointer" },
  errorBox: { background: `${C.accentWarn}15`, border: `1px solid ${C.accentWarn}40`, borderRadius: 8, padding: "12px 16px", fontSize: 14, color: "#fca5a5" },
  resultBox: { marginTop: 20, background: "#0f1117", border: `1px solid ${C.border}`, borderRadius: 10, padding: "20px" },
  resultHeader: { display: "flex", alignItems: "center", gap: 8, fontSize: 13, fontWeight: 600, color: C.success, marginBottom: 16 },
  successDot: { width: 8, height: 8, borderRadius: "50%", background: C.success, display: "inline-block" },
  chipRow: { display: "flex", gap: 10, marginBottom: 14, flexWrap: "wrap" },
  chip: { background: C.surface, border: `1px solid ${C.border}`, borderRadius: 8, padding: "10px 14px", minWidth: 80 },
  chipAccent: { border: `1px solid ${C.accentWarn}50`, background: `${C.accentWarn}10` },
  chipLabel: { fontSize: 10, color: C.textMuted, marginBottom: 4, letterSpacing: "0.04em" },
  chipValue: { fontSize: 16, fontWeight: 700 },
  narrative: { background: C.surface, borderRadius: 8, padding: "14px 16px", marginTop: 8 },
  narrativeLabel: { fontSize: 11, fontWeight: 600, color: C.textMuted, letterSpacing: "0.06em", marginBottom: 8 },
  narrativeText: { fontSize: 14, color: C.textDim, margin: 0, lineHeight: 1.65, whiteSpace: "pre-wrap" },
  issueItem: { display: "flex", alignItems: "flex-start", gap: 12, padding: "10px 0", borderBottom: `1px solid ${C.border}` },
  issueIdx: { fontSize: 10, fontWeight: 700, padding: "3px 6px", borderRadius: 4, whiteSpace: "nowrap" },
  questionEcho: { fontSize: 13, color: C.textMuted, marginBottom: 12, fontStyle: "italic" },
  clearBtn: { marginTop: 16, background: "transparent", border: `1px solid ${C.border}`, borderRadius: 6, padding: "7px 14px", fontSize: 13, color: C.textDim, cursor: "pointer" },
};