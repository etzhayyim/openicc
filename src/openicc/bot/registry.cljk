(ns openicc.bot.registry
  "**書記局 bot** — Article 43, the Registry. Custody of the record.

  Three planes, kept deliberately separate because conflating them is how a
  \"blockchain\" system ends up with a single database it cannot lose:

    block   the record itself, addressed by content (CID). Immutable.
    ref     which record is current. Decided by an inga 2f+1 quorum, never by
            one node and never by a conditional write against one vendor.
    anchor  a periodic commitment of the ref head to a public L1, so the chain
            can be checked by someone who trusts none of our nodes.

  ADR-2608039000 governs the first two: on a path that claims to be
  decentralised, nothing whose deletion loses data or breaks correctness may sit
  on a single-vendor primitive. The test is literal — *delete it right now; is
  anything lost?* The ledger here passes: it is a fold over content-addressed
  blocks, so it is a projection and can be rebuilt.

  The anchor plane is what the owner asked for on 2026-08-21 beyond the
  workspace default. It is deliberately **not** the source of truth: an L1
  anchor proves that a head existed by a certain block, which is exactly the
  property a named allegation needs — it cannot be quietly withdrawn later —
  without putting the record itself somewhere it costs gas to read."
  (:require [openicc.record :as rec]))

;; ---------------------------------------------------------------------------
;; Ledger — append-only, and a refusal is an entry
;; ---------------------------------------------------------------------------

(defn entry
  "One ledger entry. `:hold` entries carry the governor's refusals verbatim.

  A held dossier writes a record. This is the difference between a system that
  can be audited and one that merely says it was careful: silence is
  indistinguishable from never having run, and the next tick reproduces the
  same refusal with nobody the wiser."
  [{:keys [kind subject-cid decision refusals inputs at bot]}]
  {:ledger/kind        kind          ; :commit | :hold | :dispatch | :anchor
   :ledger/bot         bot
   :ledger/subject-cid subject-cid
   :ledger/decision    decision
   :ledger/refusals    (vec (or refusals []))
   :ledger/inputs      (vec (or inputs []))   ; content addresses of what was read
   :ledger/at          at})

(defn append
  "Append one entry, chaining it to the previous by content address. Pure — the
  caller owns durability."
  [hash-fn ledger e]
  (let [prev (:ledger/cid (peek ledger))
        e'   (rec/address hash-fn (assoc e :ledger/prev prev))]
    (conj (vec ledger) (assoc e' :ledger/cid (:record/cid e')))))

(defn intact?
  "Walk the chain. Returns `{:ok? :checked :broken-at}`.

  `:checked` is reported so that a zero-length ledger reads as `checked 0`
  rather than as a verified-intact chain — the same evidence floor the governor
  applies to its own gates."
  [hash-fn ledger]
  (loop [i 0 prev nil]
    (if (>= i (count ledger))
      {:ok? (pos? (count ledger)) :checked (count ledger) :broken-at nil
       :note (when (zero? (count ledger))
               "empty ledger: checked 0 entries, which is not the same as intact")}
      (let [e (nth ledger i)
            recomputed (rec/content-id hash-fn (dissoc e :record/cid :ledger/cid))]
        (if (and (= prev (:ledger/prev e)) (= recomputed (:ledger/cid e)))
          (recur (inc i) (:ledger/cid e))
          {:ok? false :checked i :broken-at i})))))

;; ---------------------------------------------------------------------------
;; Naming — the actor's graph is its key-derived IPNS name
;; ---------------------------------------------------------------------------

(defn graph-name
  "The authority for this actor's record is a signature over a key-derived IPNS
  name, not a server. `ipns-fn` is injected (it is crypto, and crypto does not
  live in this library)."
  [ipns-fn public-key]
  (ipns-fn public-key))

;; ---------------------------------------------------------------------------
;; Anchoring — the public L1 commitment
;; ---------------------------------------------------------------------------

(defn anchor-record
  "What gets committed to the public chain: the head CID, the quorum certificate
  that made it a head, and the ledger length it covers.

  The payload is a commitment, not the content. Publishing the allegations
  themselves on a public chain would make them unretractable by anyone including
  a person later shown to have been wrongly named, and Article 66 is not
  compatible with that."
  [{:keys [head-cid cert ledger-length at chain]}]
  {:anchor/head-cid      head-cid
   :anchor/cert          cert
   :anchor/ledger-length ledger-length
   :anchor/chain         chain           ; :ethereum | :filecoin
   :anchor/at            at
   :anchor/payload-note
   "commitment only — the head CID and its quorum certificate. Never the
    allegation text."})

(defn should-anchor?
  "Anchor on a cadence, not on every commit. `every-n` entries or `every-ms`
  elapsed, whichever comes first. Anchoring every commit is what turns an audit
  trail into a gas bill."
  [{:keys [ledger-length last-anchored-length last-anchored-at now
           every-n every-ms]
    :or   {every-n 32}}]
  (boolean
   (or (>= (- ledger-length (or last-anchored-length 0)) every-n)
       (and every-ms last-anchored-at now
            (>= (- now last-anchored-at) every-ms)))))
