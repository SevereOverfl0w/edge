(ns dev
  (:require{{#kick}}
    [edge.kick.repl]{{/kick}}
    [dev-extras :refer :all]))

{{#kick}}(add-dev-system-fixture!
  :start-once ::front-end
  (fn [start]
    (edge.kick.repl/start!)
    (start)))

(add-dev-system-fixture!
  :stop-once ::front-end
  (fn [start]
    (start)
    (edge.kick.repl/stop!)))

{{/kick}}
;; Add your helpers here
