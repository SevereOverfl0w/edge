(ns juxt.blog.react-hooks-raw.frontend.main
  (:require
    hicada.compiler))

(defmacro html
  [body]
  (hicada.compiler/compile body))
