(ns openicc.chain.inga
  "The only namespace that reaches `inga`.

  Everything else in openicc takes `quorum-met?`, `hash-fn` and the ref store as
  injected values. This is where they come from in a real deployment, and
  keeping it to one file is what lets the rest of the library be pure `.cljc`
  with no consensus dependency — which is also what lets the test suite run in
  milliseconds without a network.

  We do not reimplement the quorum arithmetic or certificate verification.
  `inga.quorum` and `inga.head` already own them, and a second implementation of
  a consensus predicate is a second answer to the question the consensus plane
  exists to answer."
  (:require [inga.quorum :as quorum]
            [openicc.chain :as chain]))

(defn quorum-predicate
  "Bind `inga.quorum` to a profile and hand back the `(fn [signers] boolean)`
  that `openicc.bot.assembly/certify` and gate G7 read.

  Two things here are easy to get wrong and both fail silently.

  **Threshold versus set size.** `inga.quorum/->predicate` reads an integer as a
  THRESHOLD (`at-least`), while `for-set-size` reads it as n and derives the
  threshold. On the same numeral those differ by one vote. openicc has a
  membership list, so it passes n to `for-set-size` and never hands a bare
  integer to `->predicate`.

  **Head counting has no Sybil resistance.** `:head-count` is correct only for a
  MANAGED validator set — one where who may hold a key is decided outside the
  protocol. That is openicc's situation (nodes are enrolled by the assembly), so
  it is the default, but it is named rather than assumed: a deployment that
  opens admission must move to `:stake-weighted` or it hands a supermajority to
  whoever can mint identities. Given what these signatures authorise, that is
  not a theoretical concern.

  `members` is required. A quorum over an unspecified membership is a majority
  of whoever happened to answer."
  [{:keys [members profile bonds]}]
  (when (empty? members)
    (throw (ex-info "a quorum needs an explicit member set"
                    {:openicc/refusal :quorum-without-members})))
  (let [member-set (set members)
        ;; `(or profile ...)` rather than an `:or` default in the destructuring:
        ;; `:or` fires only on an ABSENT key, so a caller that threads an
        ;; unset option through as `:profile nil` — which `context` below does —
        ;; gets nil and falls through to the unknown-profile throw. Measured
        ;; 2026-08-22 against real inga.
        q (case (or profile :head-count)
            :head-count     (quorum/for-set-size (count member-set))
            :stake-weighted (do (when (empty? bonds)
                                  (throw (ex-info "a stake-weighted quorum needs a bond source"
                                                  {:openicc/refusal :quorum-without-bonds})))
                                (quorum/stake-weighted bonds member-set))
            (throw (ex-info "unknown quorum profile"
                            {:openicc/refusal :unknown-quorum-profile
                             :profile profile
                             :known quorum/profiles})))
        pred (quorum/->predicate q)]
    (with-meta
      (fn [signers]
        ;; Signers outside the enrolled membership are dropped before counting.
        ;; Counting them would let an unenrolled key contribute to a quorum,
        ;; which is the whole property the membership list exists to provide.
        (boolean (pred (into #{} (filter member-set) signers))))
      {:openicc/profile (quorum/profile q)
       :openicc/members (count member-set)})))

(defn context
  "Assemble the injected world for `openicc.tick/run-tick`. Validates the plane
  configuration first and refuses rather than degrading: a deployment that
  silently falls back to a single-vendor ref plane is the exact failure
  ADR-2608039000 exists to prevent, and it would still pass every test.

  `opts` is returned enriched rather than rebuilt, so the caller's own injected
  values (`:hash-fn`, `:now`, `:node-id`, `:dispatch-fn`, `:anchor-fn`) pass
  through untouched. None of them is defaulted here: a defaulted `dispatch-fn`
  is a destination nobody chose."
  [{:keys [config members profile] :as opts}]
  (let [config (or config chain/default-config)
        v (chain/validate config)]
    (when-not (:ok? v)
      (throw (ex-info (chain/describe config)
                      {:openicc/refusal :invalid-chain-config
                       :problems (:problems v)})))
    (assoc opts
           :config config
           :quorum-met? (quorum-predicate {:members members :profile profile})
           :node-count (count members)
           :anchor-chain (get-in config [:anchor :chain])
           :anchor-every-n (get-in config [:anchor :every-n] 32))))
