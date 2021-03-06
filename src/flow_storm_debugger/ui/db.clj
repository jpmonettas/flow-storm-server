(ns flow-storm-debugger.ui.db
  (:require [cljfx.api :as fx]
            [clojure.core.cache :as cache]))

#_(defonce *state
  (atom (fx/create-context {:flows {}
                            :refs {}
                            :taps {}
                            :selected-flow-id nil
                            :selected-ref-id nil
                            :selected-tap-id nil
                            :selected-tool-idx 0
                            :stats {:connected-clients 0
                                    :received-traces-count 0}
                            :open-dialog nil}
                           cache/lru-cache-factory)))
