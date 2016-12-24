(ns zelector.content-script.buffer-ui
  (:require [chromex.logging :refer-macros [log info warn error group group-end]]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [zelector.common.bgx :as bgx]))

(defn- css-transition-group [props children]
  (js/React.createElement
    js/React.addons.CSSTransitionGroup
    (clj->js (merge props {:children children}))))

(defn- open-workspace! []
  (bgx/post-message! {:action "workspace"}))

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
                       :onClick #(om/transact! this `[(buffer/remove {:index ~index})])})))))
(def buffer-item (om/factory BufferItem {:keyfn :id}))

(defui BufferView
  Object
  (render [this]
    (let [{:keys [buffer active flush-buffer-fn]} (om/props this)
          clear-buffer! #(om/transact! this `[(buffer/clear)])]
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
        (dom/div #js {:id "zelector-action-bar"}
          (dom/div #js {:id "zelector-action-bar-activator"}
            (dom/span #js {:onClick #(do
                                      (om/transact! this
                                        `[(durable/update {:z/active ~(not active)})])
                                      (when active
                                        (om/transact! this
                                          '[(z/put {:mark/mark nil})])))}
              (dom/span #js {:className "zelector-clickable"
                             :style #js {:fontWeight "bold"}} "Zelector"))
            (dom/span #js {} " (Shift+Z)"))
          (dom/div #js {:className "zelector-actions"
                        :style #js {:float "right"}}
            (dom/span #js {:className "zelector-action-link"
                           :title "Save this buffer to the backend"
                           :onClick (fn [e]
                                      (flush-buffer-fn buffer)
                                      (clear-buffer!))}
              (dom/span #js {:className "fa fa-save"}))
            (dom/span #js {:className "zelector-action-link"
                           :title "Clear this buffer"
                           :onClick #(clear-buffer!)}
              (dom/span #js {:className "fa fa-trash"}))
            (dom/span #js {:className "zelector-action-link"
                           :title "Open the workspace tab"
                           :onClick #(open-workspace!)}
              (dom/span #js {:className "fa fa-external-link-square"}))))))))
