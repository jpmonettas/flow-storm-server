(ns flow-storm-debugger.ui.events.refs
  (:require [flow-storm-debugger.ui.subs.refs :refer [apply-patches]]))

(defn select-ref [{:keys [selected-ref-id] :as db} ref-id]
  (-> db
      (assoc :selected-ref-id ref-id)))

(defn remove-ref [{:keys [selected-ref-id flows] :as db} ref-id]
  (let [db' (-> db
                (update  :refs dissoc ref-id))]
    (cond-> db'
      (= selected-ref-id ref-id) (assoc :selected-ref-id (-> db' :refs keys first)))))

(defn remove-selected-ref [{:keys [selected-ref-id] :as db}]
  (remove-ref db selected-ref-id))

(defn remove-all-refs [{:keys [refs] :as db}]
  (reduce (fn [r ref-id]
            (remove-ref r ref-id))
          db
          (keys refs)))

(defn selected-ref-first [{:keys [selected-ref-id] :as db}]
  (assoc-in db [:refs selected-ref-id :patches-applied] 0))

(defn selected-ref-prev [{:keys [selected-ref-id] :as db}]
  (update-in db [:refs selected-ref-id :patches-applied] dec))

(defn selected-ref-next [{:keys [selected-ref-id] :as db}]
  (update-in db [:refs selected-ref-id :patches-applied] inc))

(defn selected-ref-last [{:keys [selected-ref-id refs] :as db}]
  (assoc-in db [:refs selected-ref-id :patches-applied] (count refs)))

(defn selected-ref-squash [{:keys [selected-ref-id refs] :as db}]
  (update-in db [:refs selected-ref-id]
             (fn [{:keys [init-val patches patches-applied] :as ref}]
               (let [unsquashed-count 3
                     squash-count (- (count patches) unsquashed-count)
                     to-squash (take squash-count patches)
                     squashed-init-val (apply-patches init-val to-squash)]
                 (-> ref
                     (assoc :init-val squashed-init-val)
                     (assoc :patches (into [] (drop squash-count patches)))
                     (assoc :patches-applied unsquashed-count))))))

(defn set-selected-ref-value-panel-type [{:keys [selected-ref-id] :as db} t]
  (assoc-in db [:refs selected-ref-id :value-panel-type] t))
