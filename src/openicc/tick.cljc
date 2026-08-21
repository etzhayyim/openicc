(ns openicc.tick
  "The durable outer loop — how openicc runs unattended and forever.

  A long-lived agent is not an agent with a long-running function. The
  `build-actor` contract in this workspace is explicit about it: one run is one
  bounded operation, and continuity comes from the *outside* — a lease, a tick,
  a budget, a checkpoint, and crash recovery. An infinite internal loop cannot
  be audited (there is no per-step record), cannot be bounded (there is no place
  to check the budget), and cannot be resumed (there is no checkpoint).

  So `run-tick` does exactly one bounded thing and returns. Something else calls
  it again. Everything it needs to resume is in its return value.

  ## Persistence without a single point of failure

  `:agent.lease/*` is what makes several nodes safe to run at once. A node holds
  the lease or it does not act; the lease is a compare-and-set against the *ref*
  plane, which is the inga quorum, so two nodes cannot both believe they hold it.
  Combined with gate G7 this gives the property the owner asked for: bots run
  autonomously, permanently, on several machines, and no single one of them can
  publish an allegation."
  (:require [openicc.bot.assembly :as assembly]
            [openicc.bot.defence :as defence]
            [openicc.bot.registry :as registry]
            [openicc.governor :as gov]))

(def phases
  "One tick advances a situation by exactly one phase. Slicing it this way is
  what keeps a run bounded and a crash cheap — a tick that died mid-phase is
  retried from the phase boundary, not from the beginning."
  [:intake :elements :jurisdiction :admissibility :interests
   :rebuttal :ballot :certify :compose :dispatch :anchor])

(defn next-phase
  "The phase after `phase`, or nil at the end. Written as a fold over `phases`
  rather than host interop so the same code runs on the JVM, on ClojureScript
  and in the Kotoba/WASM guest — the runtime priority in CLAUDE.md is not a
  preference, and `.indexOf` would have quietly pinned this namespace to one
  host."
  [phase]
  (second (drop-while #(not= phase %) phases)))

(defn- over-budget? [{:keys [spent limit]}]
  (and limit spent (>= spent limit)))

(defn lease-held?
  "`:agent.lease/*`. A lease is held when this node owns it and it has not
  expired. Both halves matter: an owner check alone lets a partitioned node act
  forever, and an expiry check alone lets two nodes act at once."
  [{:keys [owner expires-at]} node-id now]
  (boolean (and (= owner node-id)
                expires-at now
                (neg? (compare (str now) (str expires-at))))))

(defn run-tick
  "Advance one situation by one phase.

  `ctx` supplies the injected world: `:hash-fn`, `:now`, `:node-id`,
  `:quorum-met?`, `:advisor`, `:dispatch-fn`, `:anchor-fn`. Nothing is
  defaulted — a defaulted `dispatch-fn` is a dispatch nobody chose.

  Returns `{:state :did :ledger :halt-reason}`. `:did` names the phase actually
  executed, or nil, and `:halt-reason` says why nothing happened. A tick that
  returns `{:did nil}` with no reason is indistinguishable from a tick that
  worked, which is the failure mode this workspace keeps paying for."
  [{:keys [dossier phase ledger lease budget] :as state}
   {:keys [hash-fn now node-id quorum-met? dispatch-fn anchor-fn] :as ctx}]
  (cond
    (not (lease-held? lease node-id now))
    (assoc state :did nil :halt-reason {:halt :lease-not-held
                                        :lease-owner (:owner lease)
                                        :node node-id})

    (over-budget? budget)
    (assoc state :did nil :halt-reason {:halt :budget-exhausted :budget budget})

    :else
    (case phase
      :rebuttal
      (let [r (defence/refute dossier ctx)]
        (assoc state
               :dossier (assoc dossier :dossier/rebuttal r)
               :phase (next-phase phase)
               :did :rebuttal
               :halt-reason nil))

      :ballot
      (let [cid (assembly/dossier-cid hash-fn dossier)
            adm (gov/admit dossier now)
            b   (assembly/ballot {:signer node-id :dossier-cid cid
                                  :admitted? (:admitted? adm)
                                  :refusals (:refusals adm) :at now})]
        (assoc state
               :ballot b
               :dossier-cid cid
               :phase (next-phase phase)
               :did :ballot
               :halt-reason nil))

      :certify
      (let [cid  (or (:dossier-cid state) (assembly/dossier-cid hash-fn dossier))
            cert (assembly/certify {:dossier-cid cid
                                    :ballots (:ballots state)
                                    :quorum-met? quorum-met?
                                    :node-count (:node-count ctx)
                                    :at now})
            d'   (assembly/attach dossier cert)
            adm  (gov/admit d' now)]
        (if (:admitted? adm)
          (assoc state :dossier d' :phase (next-phase phase) :did :certify
                 :ledger (registry/append hash-fn ledger
                                          (registry/entry {:kind :commit :bot :assembly
                                                           :subject-cid cid :decision :admitted
                                                           :inputs [cid] :at now}))
                 :halt-reason nil)
          ;; A hold is an entry. The next tick can read why, and so can a human.
          (assoc state :dossier d' :did :certify
                 :ledger (registry/append hash-fn ledger
                                          (registry/entry {:kind :hold :bot :governor
                                                           :subject-cid cid :decision :refused
                                                           :refusals (:refusals adm)
                                                           :inputs [cid] :at now}))
                 :halt-reason {:halt :governor-refused
                               :explain (gov/explain adm)})))

      :dispatch
      (let [adm (gov/admit dossier now)]
        (if-not (:admitted? adm)
          (assoc state :did nil :halt-reason {:halt :governor-refused
                                              :explain (gov/explain adm)})
          (let [res (dispatch-fn (:dossier/dispatch dossier))]
            (assoc state
                   :phase (next-phase phase)
                   :did :dispatch
                   :ledger (registry/append hash-fn ledger
                                            (registry/entry {:kind :dispatch :bot :communication
                                                             :subject-cid (get-in dossier [:dossier/dispatch :dispatch/payload-cid])
                                                             :decision res :at now}))
                   :halt-reason nil))))

      :anchor
      (if-not (registry/should-anchor?
               {:ledger-length (count ledger)
                :last-anchored-length (:last-anchored-length state)
                :last-anchored-at (:last-anchored-at state)
                :now now
                :every-n (:anchor-every-n ctx 32)})
        (assoc state :did nil :halt-reason {:halt :anchor-not-due
                                            :ledger-length (count ledger)})
        (let [a (registry/anchor-record {:head-cid (:dossier-cid state)
                                         :cert (get-in dossier [:dossier/quorum :cert])
                                         :ledger-length (count ledger)
                                         :chain (:anchor-chain ctx)
                                         :at now})
              receipt (anchor-fn a)]
          (assoc state
                 :last-anchored-length (count ledger)
                 :last-anchored-at now
                 :did :anchor
                 :ledger (registry/append hash-fn ledger
                                          (registry/entry {:kind :anchor :bot :registry
                                                           :subject-cid (:dossier-cid state)
                                                           :decision receipt :at now}))
                 :halt-reason nil)))

      ;; The analytic phases are advanced by the caller, which owns the bots'
      ;; inputs. The loop does not invent facts.
      (assoc state :did nil :halt-reason {:halt :phase-not-driven-by-loop
                                          :phase phase}))))

(defn checkpoint
  "What must survive a crash. Deliberately small and deliberately explicit —
  anything not listed here is recomputed, and if it cannot be recomputed it
  belongs on this list."
  [state]
  (select-keys state [:dossier :dossier-cid :phase :ledger :ballots
                      :last-anchored-length :last-anchored-at]))
