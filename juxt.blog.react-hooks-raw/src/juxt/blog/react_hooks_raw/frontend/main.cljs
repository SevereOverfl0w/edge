(ns ^:figwheel-hooks juxt.blog.react-hooks-raw.frontend.main
  (:require-macros
    [juxt.blog.react-hooks-raw.frontend.main :refer [html]])
  (:require
    [com.usehooks :refer [useKeyPress]]))

(defn e
  [el props & children]
  (apply js/React.createElement el (clj->js props) children))

(defn Example
  []
  (let [[count setCount] (js/React.useState 0)]
    (e "div" nil
       (e "p" nil "You clicked " count " times")
       (e "button"
          {:onClick
           (fn [e]
             (setCount (inc count)))}
          "Click Me"))))

(defn ExampleHicada
  []
  (let [[count setCount] (js/React.useState 0)]
    (html
      [:div
       [:p "You clicked " count " times"]
       [:button {:onClick (fn [e]
                            (setCount (inc count)))}
        "Click Me"]])))

(defn counter-reducer
  [state action]
  (case (:type action)
    :inc (update state :count inc)
    :dec (update state :count dec)))

(defn Counter
  [initial-state]
  (let [[state dispatch] (js/React.useReducer counter-reducer
                                              (js->clj initial-state :keywordize-keys true))]
    (html
      [:*
       "Count: " (:count state)
       [:button {:onClick #(dispatch {:type :inc})} "+"]
       [:button {:onClick #(dispatch {:type :dec})} "-"]])))

(defn CounterF
  [initial-state]
  (let [[state dispatch] (js/React.useReducer (fn [state action]
                                                (apply (first action) state (rest action)))
                                              (js->clj initial-state
                                                       :keywordize-keys true))]
    (html
      [:*
       "Count: " (:count state)
       [:button {:onClick #(dispatch [update :count inc])} "+"]
       [:button {:onClick #(dispatch [update :count dec])} "-"]])))

(defn Effect
  []
  (let [[count setCount] (js/React.useState 0)]
    (js/React.useEffect (fn []
                          (set! (.-title js/document)
                                (str "You clicked " count " times"))
                          identity))
    
    (html
      [:div
       [:p "You clicked " count " times"]
       [:button {:onClick (fn [e] (setCount (inc count)))}
        "Click Me"]])))

(defn EmojiKeys
  []
  (let [happyPress (useKeyPress "h")
        sadPress (useKeyPress "s")
        robotPress (useKeyPress "r")
        foxPress (useKeyPress "f")]
    (html
      [:div
       [:div
        "["
        (when happyPress "😊")
        (when sadPress "😢")
        (when robotPress "🤖")
        (when foxPress "🦊")
        "]"]
       [:div "h, s, r, f"]])))

(defn useLens
  [a f]
  (let [[value update-value] (js/React.useState (f @a))]
    (js/React.useEffect
      (fn []
        (let [k (gensym "useLens")]
          (add-watch a k
                     (fn [_ _ _ new-state]
                       (update-value (f new-state))))
          (fn []
            (remove-watch a k)))))
    value))

(defonce state-atom (atom {:foo 1
                           :bar 1
                           :baz 1}))

(defn Stateful
  []
  (let [x (useLens state-atom #(* (:foo %) (:bar %)))
        all (useLens state-atom identity)]
    (html
      [:div
       "foo*bar = " [:strong x]
       [:code [:pre (prn-str all)]]
       [:button
        {:onClick #(swap! state-atom update :foo inc)}
        "More foo"]
       [:button
        {:onClick #(swap! state-atom update :bar inc)}
        "More bar"]
       [:button
        {:onClick #(swap! state-atom update :baz inc)}
        "More baz"]])))

(defn Root
  []
  (html
    [:*
     [:h1 "Root"]
     [:h2 "Example"]
     [:> Example nil]
     [:h2 "ExampleHicada"]
     [:> ExampleHicada nil]
     [:h2 "Counter"]
     [:> Counter #js {:count 5}]
     [:h2 "CounterF"]
     [:> CounterF #js {:count 6}]
     [:h2 "Effect"]
     [:> Effect nil]
     [:h2 "EmojiKeys"]
     [:> EmojiKeys nil]
     [:h2 "Stateful"]
     [:> Stateful nil]]))

(defn create-factory
  [el & props]
  (js/React.createFactory el props))

(defn mount
  []
  (doseq [[id el] [["example" (create-factory Example)]
                   ["example-hicada" (create-factory ExampleHicada)]
                   ["counter" (create-factory Counter)]
                   ["counter-f" (create-factory CounterF)]
                   ["effect" (create-factory Effect)]
                   ["emoji-keys" (create-factory EmojiKeys)]
                   ["stateful" (create-factory Stateful)]]]
    (js/ReactDOM.render
      (el)
      (js/document.getElementById id))))

;; This is called once
(defonce init (mount))

;; This is called every time you make a code change
(defn ^:after-load reload []
  (mount))
