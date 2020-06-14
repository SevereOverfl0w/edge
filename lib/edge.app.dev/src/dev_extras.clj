;; Copyright © 2016-2019, JUXT LTD.
(ns ^{:clojure.tools.namespace.repl/load false} dev-extras
  "This namespace provides all of the general-purpose dev utilities used from a
  `dev` namespace.  These vars are all available from `dev`."
  (:require
   [clojure.test :refer [run-tests]]
   [clojure.tools.namespace.repl :as repl]
   [edge.system :as system]
   [edge.system.meta :as system.meta]
   [integrant.core :as ig]
   io.aviso.ansi
   clojure.tools.deps.alpha.repl))

(when (try
        (Class/forName "org.slf4j.bridge.SLF4JBridgeHandler")
        (catch ClassNotFoundException _
          false))
  (eval
    `(do
       (org.slf4j.bridge.SLF4JBridgeHandler/removeHandlersForRootLogger)
       (org.slf4j.bridge.SLF4JBridgeHandler/install))))

(when (try
        (require 'figwheel.main.logging)
        true
        (catch Throwable _))
  ;; Undo default logger being extremely fine grained in figwheel,
  ;; in order to configure figwheel to delegate to slf4j.
  (let [l @(resolve 'figwheel.main.logging/*logger*)]
    ((resolve 'figwheel.main.logging/remove-handlers) l)
    (.setUseParentHandlers l true)))

(defmacro ^:private proxy-ns
  [ns & vars]
  (cons `do
        (map (fn [v] `(do (def ~v ~(symbol (str ns) (str v)))
                          (alter-meta!
                            (resolve '~v)
                            merge
                            {:original-line (:line (meta (resolve '~v)))}
                            (select-keys (meta (resolve '~(symbol (str ns) (str v))))
                                         [:doc :file :line :column :arglists])
                            (meta ~v))))
             vars)))
 
(proxy-ns clojure.tools.deps.alpha.repl add-lib)

(def system-config "The :ig/system key used to create `system`" nil)
(def system "After starting your dev system, this will be the system that was started.  You can use this to get individual components and test them in the repl." nil)
(def ^:private preparer nil)
(def ^:private fixtures (atom []))
(def ^:private known-lifecycles
  #{:start-once :start-always :stop-once :stop-always})

(defn add-dev-system-fixture!
  [lifecycle key f]
  (assert (known-lifecycles lifecycle) (str "Unknown lifecycle " lifecycle))
  (swap! fixtures
         (fn [fixtures]
           (conj (remove (fn [[l k]] (= [l k] [lifecycle key])) fixtures)
                 [lifecycle key f])))
  key)

(defn remove-dev-system-fixture!
  ([key]
   (swap! fixtures #(remove (fn [[_ k]] (= key k)) %))
   nil)
  ([lifecycle key]
   (swap! fixtures #(remove (fn [fixture] (= fixture [lifecycle key]))))
   nil))

(defn dev-system-fixtures
  []
  (sort-by first @fixtures))

(defn- wrap-fixtures
  [root-f fs]
  ((reduce (fn [root-f f] #(f root-f)) root-f fs)))

(defn set-prep!
  "Set the opts passed to `aero.core/read-config` for the development system.

  Example: `(set-prep! {:profile :dev :features [:in-memory-postgres]})`"
  [aero-opts]
  (alter-var-root #'preparer (constantly #(system/system-config aero-opts))))

(set-prep! {:profile :dev})

(defn- prep-error
  []
  (Error. "No system preparer function found."))

(defn- prep
  []
  (if-let [prep preparer]
    (do (alter-var-root #'system-config (fn [_] (prep))) :prepped)
    (throw (prep-error))))

(defn- halt-system
  [system]
  (when system
    (wrap-fixtures
      #(ig/halt! system)
      (map last (filter (fn [[lifecycle _]] (#{:stop-once :stop-always} lifecycle)) @fixtures)))))

(defn- build-system
  [build wrap-ex]
  (try
    (build)
    (catch clojure.lang.ExceptionInfo ex
      (if-let [system (:system (ex-data ex))]
        (try
          (ig/halt! system)
          (catch clojure.lang.ExceptionInfo halt-ex
            (throw (wrap-ex ex halt-ex)))))
      (throw ex))))

(defn- init-system
  [config]
  (wrap-fixtures
    (fn []
      (build-system
        #(ig/init config)
        #(ex-info "Config failed to init; also failed to halt failed system"
                  {:init-exception %1}
                  %2)))
    (map last (filter (fn [[lifecycle _]] (#{:start-once :start-always} lifecycle)) @fixtures))))

(defn- resume-system
  [config system]
  (wrap-fixtures
    (fn []
      (build-system
        #(ig/resume config system)
        #(ex-info "Config failed to resume; also failed to halt failed system"
                  {:resume-exception %1}
                  %2)))
    (map last (filter (fn [[lifecycle _]] (= :start-always lifecycle)) @fixtures))))

(defn init
  []
  (alter-var-root #'system (fn [sys]
                             (halt-system sys)
                             (init-system system-config)))
  :initiated)

(defn go
  "Start the dev system, and output any useful information about the system
  which was just started.  For example, it will output where to open your
  browser to see the application and link to your figwheel auto-test page."
  []
  (prep)
  (let [res (init)]
    (doseq [message (system.meta/useful-infos system-config system)]
      (println (io.aviso.ansi/yellow (format "[Edge] %s" message))))
    (println (str (io.aviso.ansi/yellow "[Edge] Now make code changes, then enter ")
                  (io.aviso.ansi/bold-yellow "(reset)")
                  (io.aviso.ansi/yellow " here")))
    res))

(defn clear
  "Stop the system and clear the system variable."
  []
  (alter-var-root #'system (fn [sys] (halt-system sys) nil))
  (alter-var-root #'system-config (constantly nil))
  :cleared)

(defn halt
  "Stop the system, if running"
  []
  (halt-system system)
  (alter-var-root #'system (constantly nil))
  :halted)

(defn suspend
  "Like halt, but doesn't completely stop some components.  This makes the components faster to start again, but means they may not be completely stopped (e.g. A web server might still have the port in use)"
  []
  (when system
    (wrap-fixtures
      #(ig/suspend! system)
      (map last (filter (fn [[lifecycle _]] (= :stop-always lifecycle)) @fixtures))))
  :suspended)

(defn resume
  "Like `go`, but works on a system suspended with `suspend`."
  []
  (if-let [prep preparer]
    (let [cfg (prep)]
      (alter-var-root #'system-config (constantly cfg))
      (alter-var-root #'system (fn [sys]
                                 (if sys
                                   (resume-system cfg sys)
                                   (init-system cfg))))
      (doseq [message (system.meta/useful-infos system-config system)]
        (println (io.aviso.ansi/yellow (format "[Edge] %s" message))))
      :resumed)
    (throw (prep-error))))

(defn reset
  "Suspend the system, reload changed code, and start the system again"
  []
  (suspend)
  (repl/refresh :after `resume))

(defn reset-all
  "Suspend the system, reload all code, and start the system again"
  []
  (suspend)
  (repl/refresh-all :after `resume))

(defn- test-namespaces
  []
  (keep (fn [[ns vars]]
          (when (some (comp :test meta) vars) ns))
        (map (juxt identity (comp vals ns-publics))
             (all-ns))))

(defn test-all
  "Run all tests"
  []
  (apply run-tests (test-namespaces)))

(defn reset-and-test
  "Reset the system, and run all tests."
  []
  (reset)
  (time (test-all)))

(defn cljs-repl
  "Start a ClojureScript REPL, will attempt to automatically connect to the
  correct figwheel build.  If not possible, will throw and it should be
  provided as an argument."
  ([]
   ;; ensure system is started - this could be less effectful perhaps?
   (go)
   (if (try
         (require 'figwheel-sidecar.repl-api)
         (catch java.io.FileNotFoundException _
           false))
     (eval
       `(do
          (require 'figwheel-sidecar.repl-api)
          (figwheel-sidecar.repl-api/cljs-repl)))
     (eval
       `(do
          (require 'figwheel.main.api)
          (require 'figwheel.main)
          (require 'figwheel.repl)
          (let [builds# (keys @figwheel.main/build-registry)]
            (if (= (count builds#) 1)
              (binding [figwheel.repl/*server* true]
                (figwheel.main.api/cljs-repl (first builds#)))
              (throw (ex-info "A build must be specified, please call with an argument"
                              {:builds builds#}))))))))
  ([build-id]
   ;; Register build with figwheel
   (go)
   ;; Assume figwheel main
   (eval
     `(do
        (require 'figwheel.main.api)
        (require 'figwheel.repl)
        (binding [figwheel.repl/*server* true]
          (figwheel.main.api/cljs-repl ~build-id))))))
