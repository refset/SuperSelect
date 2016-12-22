(ns zelector.content-script.ux
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [chromex.logging :refer-macros [log info warn error group group-end]]
            [cljs.core.async :refer [<! >! put! chan]]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [goog.object :as gobj]
            [goog.dom :as gdom]
            [goog.string :as gstr]
            [goog.string.format]
            [cljsjs.jquery]
            [jayq.core :as j]
            [zelector.common.bgx :as bgx]
            [zelector.common.trav :as trav]
            [zelector.common.util :as util]
            [zelector.content-script.parser :as parser]))

; --- util ---
(defn curr-window-size []
  (let [$win (j/$ js/window)]
    [(.width $win) (.height $win)]))

(defn css-transition-group [props children]
  (js/React.createElement
    js/React.addons.CSSTransitionGroup
    (clj->js (merge props {:children children}))))

; --- actions ---
; todo orchestrate w/ :remote?
(defn save-buffer! []
  (let [st @state
        {:keys [buff]} st
        as-record (map :content buff)]
    (comment bgx/post-record! as-record)))

; --- ui functions ---
(defn single-rect? [range]
  (<= (count (trav/range->client-rects range)) 1))

(def partition-range*
  "Split range for UX; general goal is to split range so that each result resides in
  single client rect but optimizing toward keeping number of splits small."
  (util/resettable-memoize
    (fn
      [range]
      {:pre [(vector? range)]}
      ; get rid of empty ranges
      (filter (complement trav/empty-range?)
              ; split each range until none have same parent and then trim each range to exclude spaces at ends
              (mapcat #(-> % trav/trim-range trav/partition-range-by*)
                      ; split ranges until none have > 1 client rects
                      (trav/partition-range-with* range single-rect?))))))

(defn combine-ranges* [mark over]
  (trav/grow-ranger (trav/combine-ranges mark over) util/punctuation-char?))

(defn reset-memoizations!
  "Reset memoization state for fns that are sensitive to window dimensions, etc."
  []
  (trav/partition-range-by* :reset!)
  (trav/partition-range-with* :reset!)
  (partition-range* :reset!))

; --- ui components ---
(defui DebugInfo
  Object
  (render [this]
    (let [{:keys [captured/range over char freeze]} (om/props this)]
      (dom/div #js {:id      "zelector-debug"
                    :onClick #(.stopPropagation %)}
        (letfn [(fmt [[[sc so] [ec eo] :as range]]
                  (if range
                    (let [text (trav/range->str range) len (count text)]
                      (list
                        (str "[" char "]")
                        (dom/br #js {:key "1a"})
                        "---"
                        (dom/br #js {:key "1b"})
                        (gstr/format
                          "%s[%s]...%s[%s]"
                          (util/pretty-node (util/parent-node sc))
                          (util/node->sibling-index sc)
                          (util/pretty-node (util/parent-node ec))
                          (util/node->sibling-index ec))
                        (dom/br #js {:key "2a"})
                        "---"
                        (dom/br #js {:key "2b"})
                        (dom/span #js {:key   "3a"
                                       :style #js {:fontSize 8
                                                   :verticalAlign "sub"}} so)
                        (if (< len 50)
                          text (str (subs text 0 25) "..." (subs text (- len 25))))
                        (dom/span #js {:key   "3b"
                                       :style #js {:fontSize 8
                                                   :verticalAlign "sub"}} eo)
                        (dom/br #js {:key "4a"})
                        "---"
                        (dom/br #js {:key "4b"})
                        (dom/div
                          #js {:key "5z"
                               :className "zelector-debug-breakdown"}
                          (map-indexed
                            (fn [idx [[sc so] [ec eo] :as split]]
                              (list
                                "["
                                (dom/span #js {:key (str idx "a")
                                               :style #js {:fontSize 8
                                                           :verticalAlign "sub"}} so)
                                (trav/range->str split)
                                (dom/span #js {:key (str idx "b")
                                               :style #js {:fontSize 8
                                                           :verticalAlign "sub"}} eo)
                                (dom/span #js {:key (str idx "c")
                                               :style #js {:fontSize 8
                                                           :verticalAlign "super"}}
                                          (str (trav/valid-range? split)))
                                "]"))
                            (partition-range* range)))
                        (dom/br #js {:key "5a"})
                        "---"
                        (dom/br #js {:key "5b"})
                        (dom/div #js {:key "5"
                                      :onClick
                                      (fn [] (om/transact! this `[(debug/toggle-freeze)]))}
                          (if freeze "FROZEN" "ACTIVE"))))))]
          (fmt (or range over)))))))
(def debug-info (om/factory DebugInfo))

(defui BufferItem
  Object
  (render [this]
    (let [{:keys [index content]} (om/props this)
          length (count content)]
      (dom/li nil
        (dom/span #js {:className "zelector-buffer-item-index"} index)
        (dom/span #js {:className "zelector-buffer-item-content"}
          (if (< length 50)
            content (str (subs content 0 25)
                      "..." (subs content (- length 25)))))
        (dom/span #js {:className "zelector-buffer-item-delete fa fa-times-circle"
                       :onClick   #(om/transact! this `[(buffer/remove {:index ~index})])})))))
(def buffer-item (om/factory BufferItem {:keyfn :id}))

(defui BufferView
  Object
  (render [this]
    (let [{:keys [buffer active]} (om/props this)
          clear-buffer! #(om/transact! this `[(buffer/clear) :buff])]
      (dom/div
        #js {:id "zelector-buffer"}
        (if-not (empty? buffer)
          (dom/ul #js {:onClick #(.stopPropagation %)
                       :onMouseMove #(.stopPropagation %)}
                  (css-transition-group
                    {:transitionName "zelector-buffer-item"
                     :transitionEnterTimeout 500
                     :transitionLeaveTimeout 200}
                    (reverse
                      (map-indexed
                        #(buffer-item {:index %1 :id (:id %2) :content (:content %2)})
                        buffer)))))
        (dom/div #js {:className "zelector-action-bar"}
                 (dom/div #js {:style #js {}}
                          (dom/b nil "Zelector") ": "
                          (dom/span #js {:style #js {:cursor "pointer"}
                                         :onClick #(om/transact! this `[(z/update-durable {:z/active ~(not active)})])}
                                    (if active "active" "inactive")))
                 (dom/div #js {:style #js {:float "right"}}
                           (dom/a #js {:href "#" :title "Clear this buffer"
                                       :onClick #(clear-buffer!)} "clear")
                           (dom/a #js {:href "#" :title "Save this buffer to the backend"
                                       :onClick (fn [event]
                                                  (save-buffer!)
                                                  (clear-buffer!))} "save")))))))
(def buffer (om/factory BufferView))

(defui Zelector
  static om/IQuery
  (query [this] '[:over :mark :ch :buff :freeze :debug-active {:durable [:enabled :active]}])
  Object
  (componentDidMount [this]
    (letfn [(bind [target jq-event-type handler]
              (.bind (j/$ target) jq-event-type handler))]
      (bind js/window "resize.zelector" #(reset-memoizations!))
      (bind js/window "resize.zelector scroll.zelector" #(.forceUpdate this))
      (bind js/document "keydown.zelector"
            (fn [event]
              (let [{:keys [active]} (om/props this)
                    inp? (util/input-node? (.-target event))
                    key? (and (= (.toLowerCase (.-key event)) "z") (.-shiftKey event))]
                (if key? (log "k a i" active inp?))
                (when (and key? (or active (not inp?)))
                  (when active
                    (om/transact! this `[(mark/set {:value nil})]))
                  (om/transact! this '[(active/toggle)])))))))
  (componentWillUnmount [this]
    (-> js/window j/$ (.unbind ".zelector"))
    (-> js/document j/$ (.unbind ".zelector")))
  (render [this]
    (let [{:keys [over mark ch buff freeze debug-active]} (om/props this)
          {{:keys [:z/enabled :z/active]} :durable} (om/props this)
          combined (if (and mark over) (combine-ranges* mark over))
          [window-width window-height] (curr-window-size)
          glass-border-width 5]
      (when enabled
        (dom/div
          nil
          (buffer {:buffer buff :active active})
          (when active
            (dom/div
              #js {:id "zelector-glass"
                   :style
                   #js {:top 0
                        :left 0
                        :width (- window-width glass-border-width glass-border-width)
                        :height (- window-height glass-border-width glass-border-width)}
                   :onMouseMove (fn [event]
                                  (if-not freeze
                                    (let [glass (.-currentTarget event)
                                          client-x (.-clientX event)
                                          client-y (.-clientY event)
                                          ;; must set pointer events inline/immediately
                                          ;; how else do we capture caret position in
                                          ;; underlying doc/body. then must set it back.
                                          _ (set! (.-style.pointerEvents glass) "none")
                                          [container index] (util/point->caret-position client-x client-y)
                                          _ (set! (.-style.pointerEvents glass) "auto")]
                                      (when (and container (trav/visible-text-node? container))
                                        (let [text (.-textContent container)
                                              char (get text index)]
                                          (when-not (or (nil? char) (util/whitespace-char? char))
                                            (let [range (trav/position->word-range [container index])]
                                              (om/transact! this
                                                `[(ch/set {:value ~char})
                                                  (over/set {:value ~range})]))))))))
                   ; todo - instead of toString on combined, really need to strip long sequences of
                   ; todo      whitespace, convert paragraph, br, etc. breaks into newlines etc. -- how?
                   :onClick (fn [event]
                              (if mark
                                (om/transact! this `[(buffer/push {:value ~(trav/range->str combined)})
                                                     (mark/set {:value nil})])
                                (om/transact! this `[(mark/set {:value ~over})])))}
              (when combined
                (list
                  ; render full combined range as solid block (background)
                  (map-indexed
                    #(let [border-width 2]
                      (dom/div
                        #js {:key %1
                             :className "zelector-selection-base-rect"
                             :style #js {:borderWidth border-width
                                         :top (- (.-top %2) border-width)
                                         :left (- (.-left %2) border-width)
                                         :width (inc (.-width %2))
                                         :height (inc (.-height %2))}}))
                    (trav/range->client-rects combined))
                  ; render div and styled text for each char (or word or ...)
                  (for [[[sc _] [_ _] :as split] (partition-range* combined)
                        :let [parent-node (util/parent-node sc)
                              text-styles (util/elem->text-styles parent-node)]]
                    ; note: we map here over all client rects but split should have produced only one client rect
                    (map-indexed
                      (fn [i rect]
                        (let [border-width 0]               ; note: enable border-width to see range outlines
                          (dom/div
                            #js {:key i
                                 :className "zelector-selection-text-rect"
                                 :style (clj->js
                                          ; a couple of games to play here:
                                          ; - we own "text-overflow" and "white-space" in our css
                                          ;  - we translate "text-align" to "text-align-last" since
                                          ;    we are always guaranteed to be rendering single-line rects
                                          (merge (dissoc text-styles :textOverflow :whiteSpace :textAlign)
                                            {:textAlignLast (:textAlign text-styles)
                                             :borderWidth border-width
                                             :top (- (.-top rect) border-width)
                                             :left (- (.-left rect) border-width)
                                             :width (inc (.-width rect))
                                             :height (inc (.-height rect))}))}
                            (trav/range->str split))))
                      (trav/range->client-rects split)))))
              (when mark
                (map-indexed
                  #(let [border-width 2]
                    (dom/div
                      #js {:key %1
                           :className "zelector-selection-mark-rect"
                           :style #js {:borderWidth border-width
                                       :top (- (.-top %2) border-width)
                                       :left (- (.-left %2) border-width)
                                       :width (inc (.-width %2))
                                       :height (inc (.-height %2))}}))
                  (trav/range->client-rects mark)))
              (when over
                (map-indexed
                  #(let [border-width 2]
                    (dom/div
                      #js {:key %1
                           :className "zelector-selection-over-rect"
                           :style #js {:borderWidth border-width
                                       :top (- (.-top %2) border-width)
                                       :left (- (.-left %2) border-width)
                                       :width (inc (.-width %2))
                                       :height (inc (.-height %2))}}))
                  (trav/range->client-rects over)))
              (when debug-active
                (debug-info {:captured/range combined :over over :char ch :freeze freeze})))))))))

; --- remote ---
(defn- remote
  "Handle remote/send. Assume singleton vector of read/mutate queries.
  Assume reads are for single/flat props (i.e., a :join with single/flat
  props). Assume mutate is update with single params map."
  [{:keys [remote]} cb]
  (let [ast (-> remote om/query->ast :children first)]
    (log "remote[" (:type ast) "]" (:params ast))
    (case (:type ast)
      :call (bgx/post-message! {:action "config"
                                :params (:params ast)})
      :join (bgx/post-message! {:action "config"}))))

; --- state ---
(defonce
  state
  (atom {:ch nil
         :over nil
         :mark nil
         :freeze nil
         :active false
         :debug-active true
         :buff []
         :flags {:debugged nil
                 :frozen nil}
         :durable {:z/enabled true
                   ;:z/active false
                   } ; until proven otherwise.
         }))

(def reconciler
  (om/reconciler
    {:state  state
     :parser (om/parser {:read parser/read :mutate parser/mutate})
     :send remote}))

; --- background ---
; handle messages like, e.g.,
;   {action: "config",
;    params: {z/enabled: true,
;             z/active: true}}
; note: any "config" actions we'll merge the data directly into
;       our application/reconciler :durable state state.
(defn handle-message! [msg]
  (let [{:keys [action params]} (util/js->clj* msg)]
    (log "merge:" (print-str {:durable params}))
    (case action
      "config" (om.next/merge! reconciler {:durable params})
      nil)))

(defn backgound-connect! []
  (let [port (bgx/connect!)]
    (go-loop []
      (when-let [msg (<! port)]
        (handle-message! msg)
        (recur)))))

; --- lifecycle ---
(defn install-glass-mount! []
  (-> (j/$ "<div id=\"zelector-glass-mount\">")
    (.appendTo "body")
    (aget 0)))

(defn init! []
  (backgound-connect!)
  (om/add-root! reconciler Zelector (install-glass-mount!)))

; --- for sandbox ---
(defn init-basic!
  "Init w/o a browser extension env."
  []
  (om/add-root! reconciler Zelector (install-glass-mount!)))

(defn destroy-basic! []
  (let [mount (gdom/getElement "zelector-glass-mount")]
    (om/remove-root! reconciler mount)
    (gdom/removeNode mount)))
