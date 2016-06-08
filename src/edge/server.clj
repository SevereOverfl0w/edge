;; Copyright © 2016, JUXT LTD.

(ns edge.server
  (:require
   [bidi.vhosts :refer [make-handler vhosts-model]]
   [clojure.tools.logging :refer :all]
   [com.stuartsierra.component :refer [Lifecycle using]]
   [schema.core :as s]
   edge.api
   edge.web
   [yada.yada :refer [handler resource] :as yada]))

(defn routes []
  [""
   [(edge.web/content-routes {})
    (edge.api/api-routes {})
    [true (handler nil)]]])

(defrecord WebServer [port listener]
  Lifecycle
  (start [component]
    (if listener
      component ; idempotence
      (let [vhosts-model
            (vhosts-model
             [{:scheme :http :host (format "localhost:%d" port)}
              (routes)])
            listener (yada/listener vhosts-model {:port port})]
        (infof "Started web-server on port %s" (:port listener))
        (assoc component :listener listener))))

  (stop [component]
    (when-let [close (get-in component [:listener :close])]
      (close))
    (dissoc component :listener)))

(defn new-web-server []
  (map->WebServer {}))