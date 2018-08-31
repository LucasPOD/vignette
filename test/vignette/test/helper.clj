(ns vignette.test.helper
  (:require
    [ring.mock.request :refer [request]]
    [clout.core :refer [route-compile route-matches]]))


(defn context-route-matches [ctx compiledroute match-request]
  (let [outer (route-matches
                (route-compile (str (first ctx) ":__rest") (merge (apply hash-map (rest ctx)) {:__rest #"|/.*"}))
                match-request)]
    (merge
      (route-matches compiledroute
                     (request :get (:__rest outer)))
      (dissoc outer :__rest))
    ))

(def jpeg-header [ -1 -40 -1 ])
(def png-header [80 78 71 13 10 26 10])
(def riff-header [82 73 70 70])
(def webp-header [87 69 66 80])
(def gif-header [0x47 0x49 0x46 0x38 0x39 0x61])
