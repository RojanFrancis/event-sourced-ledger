import { useState } from "react";
import { runReconciliation, askSettlementAgent } from "../services/api";

const TAB = { RECONCILE: "reconcile", AGENT: "agent" };

export default function AIController() {
  const [activeTab, setActiveTab] = useState(TAB.RECONCILE);

  // Batch Reconciliation state
  const [recoLoading, setRecoLoading] = useState(false);
  const [recoResult, setRecoResult] = useState(null);
  const [recoError, setRecoError] = useState(null);

  // Settlement Q&A state
  const [question, setQuestion] = useState("");
  const [agentLoading, setAgentLoading] = useState(false);
  const [agentAnswer, setAgentAnswer] = useState(null);
  const [agentError, setAgentError] = useState(null);

  // ── Batch Reconciliation handler ──────────────────────────────────────────
  const handleReconcile = async () => {
    setRecoLoading(true);
    setRecoResult(null);
    setRecoError(null);
    try {
      const data = await runReconciliation();
      setRecoResult(data);
    } catch (err) {
      setRecoError(err.message || "Reconciliation failed.");
    } finally {
      setRecoLoading(false);
    }
  };

  // ── Settlement Q&A handler ────────────────────────────────────────────────
  const handleAsk = async () => {
    if (!question.trim()) return;
    setAgentLoading(true);
    setAgentAnswer(null);
    setAgentError(null);
    try {
      const data = await askSettlementAgent(question);
      setAgentAnswer(data);
    } catch (err) {
      setAgentError(err.message || "Agent query failed.");
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
          <p style={styles.subtitle}>
            Gemini 2.0 Flash · Track 04 — Razorpay AI Builder 2026
          </p>
        </div>

        {/* Tabs */}
        <div style={styles.tabBar}>
          <button
            style={activeTab === TAB.RECONCILE ? styles.tabActive : styles.tab}
            onClick={() => setActiveTab(TAB.RECONCILE)}
          >
            Batch Reconciliation
          </button>
          <button
            style={activeTab === TAB.AGENT ? styles.tabActive : styles.tab}
            onClick={() => setActiveTab(TAB.AGENT)}
          >
            Settlement Q&amp;A Agent
          </button>
        </div>

        {/* ── Tab: Batch Reconciliation ───────────────────────────────────── */}
        {activeTab === TAB.RECONCILE && (
          <div style={styles.panel}>
            <div style={styles.panelHeader}>
              <h2 style={styles.panelTitle}>Batch Reconciliation</h2>
              <p style={styles.panelDesc}>
                Runs Gemini over all ledger events to detect mismatches,
                duplicate entries, and balance anomalies across accounts.
              </p>
            </div>

            <button
              style={recoLoading ? styles.btnDisabled : styles.btn}
              onClick={handleReconcile}
              disabled={recoLoading}
            >
              {recoLoading ? "Running…" : "Run Reconciliation"}
            </button>

            {recoError && (
              <div style={styles.errorBox}>
                <span style={styles.errorIcon}>⚠</span> {recoError}
              </div>
            )}

            {recoResult && (
              <div style={styles.resultBox}>
                <div style={styles.resultHeader}>
                  <span style={styles.successDot} />
                  Reconciliation Complete
                </div>

                {/* Summary chips */}
                {recoResult.summary && (
                  <div style={styles.chipRow}>
                    {recoResult.summary.totalEvents !== undefined && (
                      <Chip label="Events" value={recoResult.summary.totalEvents} />
                    )}
                    {recoResult.summary.mismatches !== undefined && (
                      <Chip
                        label="Mismatches"
                        value={recoResult.summary.mismatches}
                        accent={recoResult.summary.mismatches > 0}
                      />
                    )}
                    {recoResult.summary.status && (
                      <Chip label="Status" value={recoResult.summary.status} />
                    )}
                  </div>
                )}

                {/* AI narrative */}
                {recoResult.analysis && (
                  <div style={styles.narrative}>
                    <div style={styles.narrativeLabel}>AI Analysis</div>
                    <p style={styles.narrativeText}>{recoResult.analysis}</p>
                  </div>
                )}

                {/* Issues list */}
                {recoResult.issues && recoResult.issues.length > 0 && (
                  <div style={styles.issueList}>
                    <div style={styles.narrativeLabel}>Flagged Issues</div>
                    {recoResult.issues.map((issue, i) => (
                      <div key={i} style={styles.issueItem}>
                        <span style={styles.issueIdx}>{i + 1}</span>
                        <span>{issue}</span>
                      </div>
                    ))}
                  </div>
                )}

                {/* Raw fallback */}
                {!recoResult.summary && !recoResult.analysis && (
                  <pre style={styles.rawPre}>
                    {JSON.stringify(recoResult, null, 2)}
                  </pre>
                )}
              </div>
            )}
          </div>
        )}

        {/* ── Tab: Settlement Q&A Agent ───────────────────────────────────── */}
        {activeTab === TAB.AGENT && (
          <div style={styles.panel}>
            <div style={styles.panelHeader}>
              <h2 style={styles.panelTitle}>Settlement Q&amp;A Agent</h2>
              <p style={styles.panelDesc}>
                Ask plain-English questions about your settlement data. Gemini
                reads your ledger context and responds with grounded answers.
              </p>
            </div>

            <div style={styles.inputRow}>
              <input
                style={styles.input}
                type="text"
                placeholder="e.g. Why is account #42 showing a negative balance?"
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && handleAsk()}
              />
              <button
                style={agentLoading || !question.trim() ? styles.btnDisabled : styles.btn}
                onClick={handleAsk}
                disabled={agentLoading || !question.trim()}
              >
                {agentLoading ? "Thinking…" : "Ask"}
              </button>
            </div>

            {/* Example prompts */}
            {!agentAnswer && !agentLoading && (
              <div style={styles.examples}>
                {[
                  "Show me all failed settlements from last week",
                  "Which account has the highest pending balance?",
                  "Are there any duplicate debit entries?",
                ].map((ex) => (
                  <button
                    key={ex}
                    style={styles.exampleChip}
                    onClick={() => setQuestion(ex)}
                  >
                    {ex}
                  </button>
                ))}
              </div>
            )}

            {agentError && (
              <div style={styles.errorBox}>
                <span style={styles.errorIcon}>⚠</span> {agentError}
              </div>
            )}

            {agentAnswer && (
              <div style={styles.resultBox}>
                <div style={styles.resultHeader}>
                  <span style={styles.successDot} />
                  Agent Response
                </div>
                <div style={styles.questionEcho}>Q: {question}</div>
                <div style={styles.narrative}>
                  <p style={styles.narrativeText}>
                    {typeof agentAnswer === "string"
                      ? agentAnswer
                      : agentAnswer.answer || JSON.stringify(agentAnswer, null, 2)}
                  </p>
                </div>
                <button
                  style={styles.clearBtn}
                  onClick={() => {
                    setAgentAnswer(null);
                    setQuestion("");
                  }}
                >
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

// ── Small reusable chip ───────────────────────────────────────────────────────
function Chip({ label, value, accent }) {
  return (
    <div style={{ ...styles.chip, ...(accent ? styles.chipAccent : {}) }}>
      <div style={styles.chipLabel}>{label}</div>
      <div style={styles.chipValue}>{value}</div>
    </div>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────
const C = {
  bg: "#0f1117",
  surface: "#161b27",
  surfaceHover: "#1c2333",
  border: "#252d3d",
  accent: "#4f6ef7",
  accentHover: "#3d5ae0",
  accentWarn: "#e05c4f",
  text: "#e2e8f0",
  textMuted: "#64748b",
  textDim: "#94a3b8",
  success: "#22c55e",
};

const styles = {
  page: {
    minHeight: "100vh",
    background: C.bg,
    padding: "40px 20px",
    fontFamily: "'Inter', 'Segoe UI', system-ui, sans-serif",
    color: C.text,
  },
  container: {
    maxWidth: 760,
    margin: "0 auto",
  },

  // Header
  header: { marginBottom: 32 },
  headerBadge: {
    display: "inline-block",
    fontSize: 11,
    fontWeight: 600,
    letterSpacing: "0.08em",
    color: C.accent,
    border: `1px solid ${C.accent}40`,
    borderRadius: 4,
    padding: "3px 10px",
    marginBottom: 14,
    background: `${C.accent}12`,
  },
  title: {
    fontSize: 28,
    fontWeight: 700,
    margin: "0 0 6px",
    letterSpacing: "-0.02em",
  },
  subtitle: {
    fontSize: 13,
    color: C.textMuted,
    margin: 0,
  },

  // Tabs
  tabBar: {
    display: "flex",
    gap: 4,
    background: C.surface,
    border: `1px solid ${C.border}`,
    borderRadius: 10,
    padding: 4,
    marginBottom: 24,
  },
  tab: {
    flex: 1,
    padding: "9px 16px",
    border: "none",
    borderRadius: 7,
    background: "transparent",
    color: C.textMuted,
    fontSize: 14,
    fontWeight: 500,
    cursor: "pointer",
    transition: "color 0.15s",
  },
  tabActive: {
    flex: 1,
    padding: "9px 16px",
    border: "none",
    borderRadius: 7,
    background: C.surfaceHover,
    color: C.text,
    fontSize: 14,
    fontWeight: 600,
    cursor: "pointer",
    boxShadow: "0 1px 3px rgba(0,0,0,0.3)",
  },

  // Panel
  panel: {
    background: C.surface,
    border: `1px solid ${C.border}`,
    borderRadius: 12,
    padding: "28px 28px",
  },
  panelHeader: { marginBottom: 24 },
  panelTitle: { fontSize: 18, fontWeight: 600, margin: "0 0 8px" },
  panelDesc: { fontSize: 14, color: C.textDim, margin: 0, lineHeight: 1.55 },

  // Button
  btn: {
    background: C.accent,
    color: "#fff",
    border: "none",
    borderRadius: 8,
    padding: "10px 22px",
    fontSize: 14,
    fontWeight: 600,
    cursor: "pointer",
    transition: "background 0.15s",
  },
  btnDisabled: {
    background: C.border,
    color: C.textMuted,
    border: "none",
    borderRadius: 8,
    padding: "10px 22px",
    fontSize: 14,
    fontWeight: 600,
    cursor: "not-allowed",
  },

  // Input row
  inputRow: { display: "flex", gap: 10, marginBottom: 16 },
  input: {
    flex: 1,
    background: "#0f1117",
    border: `1px solid ${C.border}`,
    borderRadius: 8,
    padding: "10px 14px",
    fontSize: 14,
    color: C.text,
    outline: "none",
  },

  // Example chips
  examples: { display: "flex", flexWrap: "wrap", gap: 8, marginTop: 4 },
  exampleChip: {
    background: "transparent",
    border: `1px solid ${C.border}`,
    borderRadius: 20,
    padding: "5px 14px",
    fontSize: 12,
    color: C.textDim,
    cursor: "pointer",
  },

  // Error
  errorBox: {
    marginTop: 16,
    background: `${C.accentWarn}15`,
    border: `1px solid ${C.accentWarn}40`,
    borderRadius: 8,
    padding: "12px 16px",
    fontSize: 14,
    color: "#fca5a5",
  },
  errorIcon: { marginRight: 8 },

  // Result
  resultBox: {
    marginTop: 20,
    background: "#0f1117",
    border: `1px solid ${C.border}`,
    borderRadius: 10,
    padding: "20px 20px",
  },
  resultHeader: {
    display: "flex",
    alignItems: "center",
    gap: 8,
    fontSize: 13,
    fontWeight: 600,
    color: C.success,
    marginBottom: 16,
  },
  successDot: {
    width: 8,
    height: 8,
    borderRadius: "50%",
    background: C.success,
    display: "inline-block",
  },

  // Chips row
  chipRow: { display: "flex", gap: 12, marginBottom: 20, flexWrap: "wrap" },
  chip: {
    background: C.surface,
    border: `1px solid ${C.border}`,
    borderRadius: 8,
    padding: "10px 16px",
    minWidth: 90,
  },
  chipAccent: {
    border: `1px solid ${C.accentWarn}50`,
    background: `${C.accentWarn}10`,
  },
  chipLabel: { fontSize: 11, color: C.textMuted, marginBottom: 4 },
  chipValue: { fontSize: 18, fontWeight: 700 },

  // Narrative
  narrative: {
    background: C.surface,
    borderRadius: 8,
    padding: "14px 16px",
    marginTop: 8,
  },
  narrativeLabel: {
    fontSize: 11,
    fontWeight: 600,
    color: C.textMuted,
    letterSpacing: "0.06em",
    marginBottom: 8,
  },
  narrativeText: {
    fontSize: 14,
    color: C.textDim,
    margin: 0,
    lineHeight: 1.65,
    whiteSpace: "pre-wrap",
  },

  // Issues
  issueList: { marginTop: 16 },
  issueItem: {
    display: "flex",
    alignItems: "flex-start",
    gap: 12,
    padding: "10px 0",
    borderBottom: `1px solid ${C.border}`,
    fontSize: 14,
    color: C.textDim,
  },
  issueIdx: {
    minWidth: 22,
    height: 22,
    borderRadius: "50%",
    background: `${C.accentWarn}20`,
    color: C.accentWarn,
    fontSize: 11,
    fontWeight: 700,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
  },

  // Q&A
  questionEcho: {
    fontSize: 13,
    color: C.textMuted,
    marginBottom: 12,
    fontStyle: "italic",
  },
  clearBtn: {
    marginTop: 16,
    background: "transparent",
    border: `1px solid ${C.border}`,
    borderRadius: 6,
    padding: "7px 14px",
    fontSize: 13,
    color: C.textDim,
    cursor: "pointer",
  },

  // Raw fallback
  rawPre: {
    fontSize: 12,
    color: C.textDim,
    overflowX: "auto",
    whiteSpace: "pre-wrap",
    wordBreak: "break-all",
    margin: 0,
  },
};
