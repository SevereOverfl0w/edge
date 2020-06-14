(ns ^{:clojure.tools.namespace.repl/load false} edge.kick.repl
  (:require [dev-extras :refer [preparer]]
            [edge.kick.builder :refer [load-provider-namespaces]]
            [edge.system :as system]
            [juxt.kick.alpha.core :as kick]))

(defn start!*
  []
  (let [config (system/config preparer)
        kick-config (get-in config [:edge.kick/config])]
    (load-provider-namespaces kick-config)
    (kick/watch kick-config)))

(def ^:private kick nil)

(defn start!
  []
  (when-not kick
    (alter-var-root #'kick (constantly (start!*))))
  :started)

(defn stop!
  []
  (when kick (kick))
  (alter-var-root #'kick (constantly nil))
  :stopped)
