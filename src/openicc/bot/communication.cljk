(ns openicc.bot.communication
  "**通報起草 bot** — Article 15(2). The only thing openicc ever sends.

  An Article 15(2) communication is not an accusation made outside the system.
  The Statute invites it: *any individual, group or organization* may send
  information on alleged crimes to the Office of the Prosecutor, and the
  Prosecutor shall analyse its seriousness. That is why openicc has exactly one
  outbound channel and gate G8 refuses every other one — not the press, not the
  named party, not a public index. The Court decides; openicc submits.

  Everything this namespace produces is a **draft**. `compose` builds it,
  `openicc.governor/admit` decides whether it may leave, and dispatch happens in
  `openicc.loop` after a quorum certificate exists. There is no path from here
  to a socket.

  ## Language

  Gate G13 (Article 66, presumption of innocence) is enforced on the text this
  bot writes, so the phrasing below is not stylistic. A communication *requests
  examination of alleged conduct on stated sources*. It does not pronounce."
  (:require [kotoba.lang.text :as str]
            [openicc.record :as rec]
            [openicc.statute :as statute]))

(def channel (:test/channel statute/article-15))

(defn- section [title body]
  (str "## " title "\n\n" body "\n"))

(defn- render-sources [sources]
  (str/join "\n"
            (map-indexed
             (fn [i s]
               (str "  [" (inc i) "] "
                    (or (:source/title s) "(untitled)")
                    (when-let [p (:source/publisher s)] (str " — " p))
                    "\n      " (:source/url s)
                    (if-let [c (:source/cid s)]
                      (str "\n      cid: " c)
                      "\n      cid: (not pinned)")))
             sources)))

(defn compose
  "Build the draft. Returns a record, addressed by content when `hash-fn` is
  supplied in `ctx`."
  [{:keys [situation-id crime findings assertions] :as _dossier-view}
   {:keys [at hash-fn submitter-did language] :or {language "en"}}]
  (let [named   (filterv rec/names-individual? assertions)
        sources (into [] (comp (mapcat :assertion/sources) (distinct)) assertions)
        body
        (str
         (section "Nature of this submission"
                  (str "This is a communication under Article 15(2) of the Rome "
                       "Statute. It requests that the Office of the Prosecutor "
                       "analyse the seriousness of the information set out below. "
                       "It asserts no finding of guilt, and it is not an "
                       "accusation: the determination is the Court's alone "
                       "(Article 66)."))
         (section "Situation" (str (pr-str situation-id)
                                   "\nCrime relied upon: " (pr-str crime)
                                   " (Article " (:crime/article (statute/crime crime)) ")"))
         (section "Alleged conduct, with sources"
                  (str/join "\n\n"
                            (map-indexed
                             (fn [i a]
                               (str " " (inc i) ". It is alleged, on the sources cited, that "
                                    (:assertion/claim a)
                                    "\n    independent publishers: "
                                    (:assertion/independent-sources a)
                                    "\n    articles relied on: " (pr-str (:assertion/cites a))))
                             assertions)))
         (section "Jurisdiction"
                  (get-in findings [:jurisdiction :finding/rationale] "(not established)"))
         (section "Admissibility (Article 17)"
                  (get-in findings [:admissibility :finding/rationale] "(not established)"))
         (section "Interests of justice (Article 53(1)(c))"
                  (get-in findings [:interests :finding/rationale] "(not established)"))
         (section "Sources" (render-sources sources))
         (section "Limitations of this submission"
                  (str "This communication was assembled by an automated system "
                       "(openicc) from open sources. It has not been verified by "
                       "investigators, it contains no witness testimony, and no "
                       "source was obtained other than by lawful public access. "
                       "Persons named are named because open sources name them in "
                       "connection with the alleged conduct; that is not evidence "
                       "of responsibility.")))]
    (cond-> {:communication/kind         :article-15-2
             :communication/situation-id situation-id
             :communication/crime        crime
             :communication/language     language
             :communication/channel      channel
             :communication/submitter    submitter-did
             :communication/named-count  (count named)
             :communication/source-count (count sources)
             :communication/body         body
             :communication/at           at
             :communication/proposal?    true}
      hash-fn (->> (rec/address hash-fn)))))

(defn dispatch-request
  "The dispatch envelope the governor's gate G8 reads. Building it does not send
  it — `openicc.loop` will only act on it after `admit` passes and a quorum
  certificate exists."
  [communication]
  {:dispatch/channel      :article-15-2-otp
   :dispatch/to           (:otp/post channel)
   :dispatch/portal       (:otp/portal channel)
   :dispatch/language     (:communication/language communication)
   :dispatch/payload-cid  (:record/cid communication)
   :dispatch/sent?        false})
