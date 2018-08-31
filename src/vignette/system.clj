(ns vignette.system
  (:require [environ.core :refer [env]]
            [qbits.jet.server :as jet]
            [vignette.http.routes :refer [create-routes]]
            [vignette.http.jetty :refer [configure-jetty]]
            [vignette.protocols :refer :all]
            [vignette.setup :refer [image-routes]]
            [ring.middleware.reload :refer [wrap-reload]]
            [vignette.perfmonitoring.core :as perf])
  (:import [java.util.concurrent ArrayBlockingQueue]))

(def default-max-threads 150)
(def default-queue-size 9000)

(defrecord VignetteSystem [state]
  SystemAPI
  (stores [this]
    (-> this :state :stores))
  (start [this port]
    (perf/init)
    (swap! (:running (:state this))
           (fn [_]
             (jet/run-jetty
               {
                :ring-handler         (if (boolean (Boolean/valueOf (env :reload-on-request)))
                                        (do
                                          (println "Code will be reloaded on each request")
                                          (wrap-reload (create-routes (image-routes (stores this)))))
                                        (create-routes (image-routes (stores this))))
                :port                 port
                :configurator         configure-jetty
                :join?                false
                :send-server-version? false
                ; FIXME: update the readme
                :max-threads          (Integer. (env :vignette-server-max-threads default-max-threads))
                ; see https://wiki.eclipse.org/Jetty/Howto/High_Load#Thread_Pool
                :job-queue            (ArrayBlockingQueue.
                                        (Integer. (env :vignette-server-queue-size default-queue-size)))}))))
  (stop [this]
    (when-let [server @(:running (:state this))]
      (.stop server))))

(defn create-system
  [stores]
  (->VignetteSystem {:stores stores :running (atom nil)}))
