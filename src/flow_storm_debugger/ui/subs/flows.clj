(ns flow-storm-debugger.ui.subs.flows
  (:require [cljfx.api :as fx]
            [flow-storm-debugger.highlighter :refer [highlight-expr]]
            [flow-storm-debugger.ui.utils :as utils]
            [taoensso.timbre :as log]))

(defn flows [context]
  (log/debug "[SUB] flows firing")
  (fx/sub-val context :flows))

(defn all-flows-traces [context]
  (log/debug "[SUB] flows-traces firing")
  (let [flows (fx/sub-val context :flows)]
   (->> flows
        vals
        (mapcat (fn [{:keys [flow-id flow-name traces]}]
                  (map #(assoc % :flow-name flow-name) traces))))))

(defn selected-flow [context]
  (log/debug "[SUB] selected-flow firing")
  (let [selected-flow-id (fx/sub-val context :selected-flow-id)
        flows (fx/sub-ctx context flows)]
    (-> (get flows selected-flow-id)
        (assoc :id selected-flow-id))))

(defn selected-flow-forms [context]
  (log/debug "[SUB] selected-flow-forms firing")
  (let [selected-flow (fx/sub-ctx context selected-flow)]
    (:forms selected-flow)))

(defn selected-flow-current-trace [context]
  (log/debug "[SUB] selected-flow-current-trace firing")
  (let [{:keys [traces trace-idx]} (fx/sub-ctx context selected-flow)]
    (get traces trace-idx)))

(defn selected-flow-forms-pprinted [context]
  (log/debug "[SUB] selected-flow-forms-pprinted firing")
  (let [forms (fx/sub-ctx context selected-flow-forms)]
    (->> (vals forms)
         (mapv (fn [form]
                 (update form :form-str utils/pprint-form-for-html))))))

(defn selected-flow-forms-highlighted [context]
  (log/debug "[SUB] selected-flow-forms-highlighted firing")
  (let [forms (fx/sub-ctx context selected-flow-forms-pprinted)
        current-trace (fx/sub-ctx context selected-flow-current-trace)]
    (->> forms
         (sort-by :timestamp >)
         (mapv (fn [{:keys [form-id form-str]}]
                 (let [h-form-str (cond-> form-str

                                    ;; if it is the current-one, highlight it
                                    (= form-id (:form-id current-trace))
                                    (highlight-expr (:coor current-trace) "<b id=\"expr\" class=\"hl\">" "</b>"))]
                   
                   [form-id h-form-str]))))))

(defn flow-comparator [f1 f2]
  (log/debug "[SUB] flow-comparator firing")
  (compare (:timestamp f1) (:timestamp f2)))

(defn flows-tabs [context]
  (log/debug "[SUB] flows-tabs firing")
  (let [flows (fx/sub-ctx context flows)]
    (->> flows
         (sort-by second flow-comparator)
         (map (fn [[flow-id flow]]
                [flow-id (:flow-name flow)])))))

(defn selected-flow-result-panel-content [context pprint?]
  (log/debug "[SUB] selected-flow-result-panel-content firing")
  (let [content (:result-panel-content (fx/sub-ctx context selected-flow))]
    {:val (if pprint?
            (utils/pprint-form-str content)
            (-> content
                utils/read-form))}))

(defn selected-flow-result-panel-type [context]
  (log/debug "[SUB] selected-flow-result-panel-type firing")
  (or (:result-panel-type (fx/sub-ctx context selected-flow))
      :pprint))

(defn coor-in-scope? [scope-coor current-coor]
  (log/debug "[SUB] coor-in-scope? firing")
  (if (empty? scope-coor)
    true
    (every? true? (map = scope-coor current-coor))))

(defn trace-locals [{:keys [coor form-id timestamp]} bind-traces]
  (log/debug "[SUB] trace-locals firing")
  (let [in-scope? (fn [bt]
                    (and (= form-id (:form-id bt))
                         (coor-in-scope? (:coor bt) coor)
                         (<= (:timestamp bt) timestamp)))]
    (when-not (empty? coor)
      (->> bind-traces
           (reduce (fn [r {:keys [symbol value] :as bt}]
                     (if (in-scope? bt)
                       (assoc r symbol value)
                       r))
                   {})))))

(defn selected-flow-bind-traces [context]
  (log/debug "[SUB] selected-flow-bind-traces firing")
  (let [sel-flow (fx/sub-ctx context selected-flow)]
    (:bind-traces sel-flow)))

(defn selected-flow-current-locals [context]
  (log/debug "[SUB] selected-flow-current-locals firing")
  (let [{:keys [result] :as curr-trace} (fx/sub-ctx context selected-flow-current-trace)
        bind-traces (fx/sub-ctx context selected-flow-bind-traces)
        locals-map (trace-locals curr-trace bind-traces)]
    (->> locals-map
         (into [])
         (sort-by first)
         (into [["=>" result true]]))))

(defn selected-flow-similar-traces [context]
  (log/debug "[SUB] selected-flow-similar-traces firing")
  (let [{:keys [traces trace-idx]} (fx/sub-ctx context selected-flow)
        traces (into [] (map-indexed (fn [idx t] (assoc t :trace-idx idx :selected? (= idx trace-idx)))) traces)
        {:keys [form-id coor]} (get traces trace-idx)
        current-coor (get-in traces [trace-idx :coor])
        similar-traces (into []
                             (filter (fn similar [t]
                                       (and (= (:form-id t) form-id)
                                            (= (:coor t)    coor)
                                            (:result t))))
                             traces)]
    similar-traces))

(defn empty-flows? [context]
  (log/debug "[SUB] empty-flows? firing")
  (empty? (fx/sub-val context :flows)))

(defn fn-call-trace? [trace]
  (log/debug "[SUB] fn-call-trace? firing")
  (:args-vec trace))

(defn ret-trace? [trace]
  (log/debug "[SUB] ret-trace? firing")
  (and (:result trace)
       (:outer-form? trace)))

(defn build-tree-from-traces [traces]
  (log/debug "[SUB] build-tree-from-traces firing")
  (loop [[t & r] (rest traces)
         tree (-> (first traces)
                  (assoc :childs []))
         path [:childs]]
    (let [last-child-path (into path [(count (get-in tree path))])]
      (cond
        (nil? t) tree
        (fn-call-trace? t) (recur r
                                  (update-in tree last-child-path #(merge % (assoc t :childs [])))
                                  (into last-child-path [:childs]))
        (ret-trace? t) (let [ret-pointer (vec (butlast path))]
                         (recur r
                                (if (empty? ret-pointer)
                                  (merge tree t)
                                  (update-in tree ret-pointer merge t ))
                                (vec (butlast (butlast path)))))))))

(defn selected-flow-traces [context]
  (log/debug "[SUB] selected-flow-traces firing")
  (:traces (fx/sub-ctx context selected-flow)))

(defn selected-flow-errors [context]
  (log/debug "[SUB] selected-flow-errors firing")
  (let [{:keys [traces trace-idx]} (fx/sub-ctx context selected-flow)]
    (->> (filter :err traces)
         (reduce (fn [r {:keys [coor form-id] :as t}]
                   ;; if it is a exception that is bubling up, discard it
                   ;; only keep exception origin traces
                   (if (some #(and (= form-id (:form-id t))
                                   (utils/parent-coor? coor (:coor %))) r)
                     r
                     (conj r (cond-> t
                               (= (:trace-idx t) trace-idx) (assoc :selected? true)))))
                 []))))

(defn selected-flow-trace-idx [context]
  (log/debug "[SUB] selected-flow-trace-idx firing")
  (:trace-idx (fx/sub-ctx context selected-flow)))

(defn fn-call-traces [context]
  (log/debug "[SUB] fn-call-traces firing")
  (let [traces  (fx/sub-ctx context selected-flow-traces)
        call-traces (->> traces
                         (map-indexed (fn [idx t]
                                        (if (fn-call-trace? t)
                                          (assoc t :call-trace-idx idx)
                                          (assoc t :ret-trace-idx idx))))
                         (filter (fn [t] (or (fn-call-trace? t)
                                             (ret-trace? t)))))]
    (when (some #(:fn-name %) call-traces)
      (build-tree-from-traces call-traces))))

