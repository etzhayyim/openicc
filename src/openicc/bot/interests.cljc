(ns openicc.bot.interests
  "**司法の利益 bot** — Article 53(1)(c).

  The one limb of Article 53 that is written backwards, and the reason this bot
  exists as its own namespace rather than a branch inside the pipeline.

  53(1)(a) and (b) are hurdles: the Prosecutor proceeds *if* there is a
  reasonable basis and *if* the case is admissible. 53(1)(c) is not. It reads:
  the Prosecutor proceeds **unless**, taking into account the gravity of the
  crime and the interests of victims, there are *substantial reasons to believe*
  that an investigation would not serve the interests of justice.

  So the default is `proceed`. A pipeline that requires an affirmative
  interests-of-justice finding before continuing has inverted the Statute, and
  the inversion is invisible: it simply drops every situation for which nobody
  happened to write a positive case, which is most of them. Here, `:affirmative`
  means *no substantial reason to stand down was found*, and the rationale says
  so in those words so that a reader downstream cannot mistake it for a
  positive endorsement.

  The OTP's own 2007 and 2013 policy papers say this discretion is exceptional
  and has never been exercised to decline a situation. openicc records that as
  policy context, not as statutory law (gate G8's non-fabrication sibling)."
  (:require [openicc.record :as rec]))

(def standing-down-considerations
  "The two the Statute names, and nothing else. `interests of justice` is not a
  general-purpose policy slot — a bot that reads it as one will import whatever
  the operator happened to care about that week."
  [{:consideration/id :gravity-of-crime  :consideration/article "53(1)(c)"}
   {:consideration/id :interests-of-victims :consideration/article "53(1)(c)"}])

(defn assess
  "`substantial-reasons` is a collection of *stated* reasons to stand down.
  Empty means proceed — which is the Statute's default, not an omission."
  [{:keys [situation-id substantial-reasons gravity interests-of-victims]}
   {:keys [at]}]
  (let [reasons (vec (remove nil? substantial-reasons))
        verdict (if (seq reasons) :negative :affirmative)]
    (rec/finding
     {:bot          :interests
      :kind         :interests
      :verdict      verdict
      :situation-id situation-id
      :at           at
      :cites        ["53(1)(c)"]
      :rationale
      (str "Article 53(1)(c) is negatively framed: the presumption is to proceed. "
           "This finding of " (name verdict)
           (if (seq reasons)
             (str " records " (count reasons)
                  " substantial reason(s) to believe an investigation would NOT"
                  " serve the interests of justice: " (pr-str reasons) ".")
             (str " means NO substantial reason to stand down was identified."
                  " It is not an endorsement of the case and must not be read as one."))
           " Gravity considered: " (pr-str gravity)
           ". Interests of victims considered: " (pr-str interests-of-victims) ".")})))
