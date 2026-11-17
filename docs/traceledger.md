# TraceLedger: replayable, explainable mule-account detection

TraceLedger adds mule-account detection to the event-sourced ledger. Because every payment is an immutable event, any flag can be **replayed**: the system rebuilds the account exactly as it stood when the flag was raised, runs the same rule or model version again, and shows whether it reaches the same answer. An analyst, auditor or regulator can check any decision later, even after the account has moved on.

Built for the St Aloysius × RBIH FinTech Innovation Hackathon 2026, Track 1 (digital payments fraud: mule accounts).

## What it does

1. **Screens every ledger event** once the payment has committed. Screening only raises flags for an analyst. It never blocks or reverses a payment.
2. **Three detectors**, each versioned:

   | Detector | Fires when | Catches |
   |---|---|---|
   | `RAPID_PASS_THROUGH` v1 (rule) | On money out: at least ₹10,000 came in during the last 30 minutes and at least 80% of it has gone out | Pass-through mules, layering hops |
   | `FAN_IN_NEW_ACCOUNT` v1 (rule) | On money in: an account under 7 days old has been paid by at least 5 different accounts in 24 hours | Collector mules |
   | `ANOMALY_MODEL` v1 (Isolation Forest) | On money out: the account's last 7 days look unusual compared with normal customers | All three, without a fixed threshold per pattern |

3. **Explains every flag.** Rules state their facts ("₹58,100 arrived in the 30 minutes up to this debit; ₹53,452 (92%) left in the same window"). The model names the features that drove its score, compared with what is typical: "Time money stays: 0 min (typical 3 days), 22% of the signal".
4. **Shows the evidence**: the transactions behind the flag, with the other party's name masked.
5. **Records analyst decisions** (hold, clear, escalate, with a note) as new rows that are never edited. Each records the rule version and model version the analyst was looking at.
6. **Replays any flag** with `GET /api/fraud/flags/{id}/replay`, and reports whether the score, reasons and evidence match exactly.
7. **Cooldown**: after a detector flags an account, it stays quiet on that account for an hour, so one pattern opens one case rather than one per payment.

## The anomaly model

- **Features**, over the 7 days up to the payment: money received, share passed on, number of different senders, time money stays (amount-weighted median of the time from the last credit to each debit), and account age.
- **Isolation Forest** written in Java (`fraud/model/IsolationForest.java`): 100 trees, 256-point samples, fixed seed 42. It is trained without labels on a simulated ledger (seed 7, 3,000 accounts), and the alert threshold is the 99th percentile of training scores.
- **Feature contributions**: each split on the path that isolates the account credits its feature, weighted more when the path is short.
- **Reproducible by design.** The same data and seed always build the same forest. The full recipe is the model version stored on every flag: `iforest-v1:t100-s256-seed42-alert0.01:sim7x3000`. Replay refuses to run if a different model is loaded, instead of giving a misleading answer.

## Simulator and measured results

`fraud/sim/TransactionSimulator.java` generates a labelled ledger from a seed:

- **Ordinary customers**: salaried, students, merchants, new customers.
- **Ordinary customers who look suspicious**:
  - 4% of salaried customers sweep their salary to savings within minutes.
  - 8% of new customers get money from 5–9 friends on one day.
  - Merchants are paid by dozens of customers every day.
- **Planted mules, about 2.4% of accounts**:
  - pass-through
  - collectors (new accounts paid by 6–18 victims)
  - layering chains of four accounts
  - slow mules that stay below every threshold

`DetectorEvaluation` runs the detectors exactly as live screening does (same lookback, same cooldown) on a **held-out** simulation (seed 2026, 5,000 accounts, 122 mules, about 300,000 events). An account counts as caught if any flag was raised on it.

| Detector | Precision | Recall | Pass-through | Collector | Layering | Slow |
|---|---|---|---|---|---|---|
| Rapid pass-through rule | 50% | 59% | 100% | 0% | 100% | 0% |
| Many payers → new account rule | 32% | 29% | 0% | 100% | 0% | 0% |
| Anomaly model | 39% | 84% | 90% | 100% | 97% | 0% |
| **All detectors** | **29%** | **88%** | 100% | 100% | 100% | 0% |

`GET /api/fraud/evaluation` serves the same numbers, and the review screen shows them. `DetectorEvaluationTest` fails if a change moves them below this baseline.

**Known gaps, stated plainly:**
- **Slow mules** (forwarding after 3–8 hours, fewer than 5 senders) are missed by every detector. Network features such as shared counterparties, and longer windows, are the next step.
- **Precision of 29%** means about 2 in 3 flags are ordinary customers who look unusual. That's why flags go to an analyst rather than blocking payments.
- **Simulated data:** these numbers come from synthetic data. Real-world performance has to be measured on real labelled cases.

## Try it

**Review screen:** sign in as an analyst and open **Mule review**. Analysts are users with `ROLE_ANALYST`, or emails listed in `traceledger.analyst-emails` (comma-separated).

**Demo:** press **Play a mule ring through the ledger**. This runs real payments through the normal transaction service:
1. An ordinary customer is paid a salary and pays bills.
2. 8 people pay a brand-new account.
3. The new account passes 92% down two layering hops.
4. The last hop withdraws it as cash.

The mule and both hops are flagged. The ordinary customer, the employer and the payers are not. Replay any flag to see it reproduced exactly.

`POST /api/fraud/demo/mule-ring` writes demo payments into the ledger it runs against. Set `traceledger.demo.enabled=false` on any deployment whose ledger must not receive them.

**Run locally without MySQL** (in-memory H2, test settings; analyst email `lead.analyst@bank.test`, register it with any password):

```bash
mvn spring-boot:test-run
```
```bash
cd frontend && npm run dev
```

## API (analysts only)

| Method and path | Purpose |
|---|---|
| `GET /api/fraud/flags` | Latest 200 flags with their status |
| `GET /api/fraud/flags/{id}` | One flag with its decisions |
| `GET /api/fraud/flags/{id}/evidence` | The evidence transactions, names masked |
| `GET /api/fraud/flags/{id}/replay` | Rebuild the flag as of when it was raised |
| `POST /api/fraud/flags/{id}/decisions` | `{"decision": "HOLD" \| "CLEAR" \| "ESCALATE", "note": "..."}` |
| `GET /api/fraud/model` | Model version, threshold, features and typical values |
| `GET /api/fraud/evaluation` | Precision and recall on the held-out simulation |
| `POST /api/fraud/demo/mule-ring` | Play the demo scenario through the ledger |

## Still to build

- **Gemini case summary:** personal data masked first (`fraud/Masking.java`), read-only, never able to change a score or decision.
- **Network features** for slow mules and rings: shared counterparties, and money flowing between flagged accounts.
- **Model cross-check:** compare this Java Isolation Forest against scikit-learn on the same simulated data, and try supervised models now that labelled simulation data exists.
