(ns vignette.http.routes
  (:require [cheshire.core :refer :all]
            [clojure.java.io :as io]
            [clout.core :refer [route-compile route-matches]]
            [compojure.core :refer [routes GET ANY]]
            [compojure.route :refer [files]]
            [environ.core :refer [env]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [response status charset header]]
            [slingshot.slingshot :refer [try+ throw+]]
            [vignette.http.legacy.routes :as hlr]
            [vignette.http.middleware :refer :all]
            [vignette.protocols :refer :all]
            [vignette.storage.core :refer :all]
            [vignette.storage.protocols :refer :all]
            [vignette.util.external-hotlinking :refer :all]
            [vignette.util.image-response :refer :all]
            [vignette.util.query-options :refer :all]
            [vignette.util.regex :refer :all]
            [vignette.util.thumbnail :as u]))

(def original-route
  (route-compile "/:wikia:image-type/:top-dir/:middle-dir/:original/revision/:revision"
                 {:wikia wikia-regex
                  :image-type image-type-regex
                  :top-dir top-dir-regex
                  :middle-dir middle-dir-regex
                  :original original-regex
                  :revision revision-regex}))

(def thumbnail-route
  (route-compile "/:wikia:image-type/:top-dir/:middle-dir/:original/revision/:revision/:thumbnail-mode/width/:width/height/:height"
                 {:wikia wikia-regex
                  :image-type image-type-regex
                  :top-dir top-dir-regex
                  :middle-dir middle-dir-regex
                  :original original-regex
                  :revision revision-regex
                  :thumbnail-mode thumbnail-mode-regex
                  :width size-regex
                  :height size-regex}))

(def window-crop-route
  (route-compile "/:wikia:image-type/:top-dir/:middle-dir/:original/revision/:revision/:thumbnail-mode/width/:width/x-offset/:x-offset/y-offset/:y-offset/window-width/:window-width/window-height/:window-height"
                 {:wikia wikia-regex
                  :image-type image-type-regex
                  :top-dir top-dir-regex
                  :middle-dir middle-dir-regex
                  :original original-regex
                  :revision revision-regex
                  :thumbnail-mode "window-crop"
                  :width size-regex
                  :x-offset size-regex-allow-negative
                  :window-width size-regex
                  :y-offset size-regex-allow-negative
                  :window-height size-regex}))

(def window-crop-fixed-route
  (route-compile "/:wikia:image-type/:top-dir/:middle-dir/:original/revision/:revision/:thumbnail-mode/width/:width/height/:height/x-offset/:x-offset/y-offset/:y-offset/window-width/:window-width/window-height/:window-height"
                 {:wikia wikia-regex
                  :image-type image-type-regex
                  :top-dir top-dir-regex
                  :middle-dir middle-dir-regex
                  :original original-regex
                  :revision revision-regex
                  :thumbnail-mode "window-crop-fixed"
                  :width size-regex
                  :height size-regex
                  :x-offset size-regex-allow-negative
                  :window-width size-regex
                  :y-offset size-regex-allow-negative
                  :window-height size-regex}))

(def scale-to-width-route
  (route-compile "/:wikia:image-type/:top-dir/:middle-dir/:original/revision/:revision/:thumbnail-mode/:width"
                 {:wikia wikia-regex
                  :image-type image-type-regex
                  :top-dir top-dir-regex
                  :middle-dir middle-dir-regex
                  :original original-regex
                  :revision revision-regex
                  :thumbnail-mode "scale-to-width"
                  :width size-regex}))

(declare original-request->file
         handle-thumbnail
         handle-original
         route->offset
         route->thumbnail-map
         route->original-map
         route->thumbnail-auto-height-map
         route->options
         route-params->image-type
         route->image-type)

(defn app-routes
  [system]
  [(GET scale-to-width-route
        request
        (handle-thumbnail system
                          (route->thumbnail-auto-height-map
                            (:route-params request)
                            request)))
   (GET window-crop-route
        request
        (handle-thumbnail system
                          (route->thumbnail-auto-height-map
                            (:route-params request)
                            request)))
   (GET window-crop-fixed-route
        request
        (handle-thumbnail system
                          (route->thumbnail-map
                            (:route-params request)
                            request)))
   (GET thumbnail-route
        request
        (handle-thumbnail system
                          (route->thumbnail-map
                            (:route-params request)
                            request)))
   (GET original-route
        request
        (handle-original system
                         (route->original-map
                           (:route-params request)
                           request)))])

(defn legacy-routes
  [system]
  [(GET hlr/thumbnail-route
         request
         (let [image-params (hlr/route->thumb-map (:route-params request))]
           (if-let [thumb (u/get-or-generate-thumbnail system image-params)]
             (create-image-response thumb image-params)
             (error-response 404 image-params))))
   (GET hlr/original-route
        request
        (let [image-params (hlr/route->original-map (:route-params request))]
          (if-let [file (original-request->file request system image-params)]
            (create-image-response file image-params)
            (error-response 404 image-params))))
   (GET hlr/timeline-route
        request
        (let [image-params (hlr/route->timeline-map (:route-params request))]
          (if-let [file (original-request->file request system image-params)]
            (create-image-response file image-params)
            (error-response 404 image-params))))
   (GET hlr/math-route
        request
        (let [image-params (hlr/route->original-map (:route-params request))]
          (if-let [file (original-request->file request system image-params)]
            (create-image-response file image-params)
            (error-response 404 image-params))))
   (GET hlr/interactive-maps-route
        request
        (let [image-params (hlr/route->interactive-maps-map (:route-params request))]
          (if-let [file (original-request->file request system image-params)]
            (create-image-response file image-params)
            (error-response 404 image-params))))
   (GET hlr/interactive-maps-marker-route
        request
        (let [image-params (hlr/route->interactive-maps-map (:route-params request))]
          (if-let [file (original-request->file request system image-params)]
            (create-image-response file image-params)
            (error-response 404 image-params))))
   (GET hlr/interactive-maps-thumbnail-route
        request
        (let [image-params (hlr/route->interactive-maps-thumbnail-map (:route-params request))]
          (if-let [thumb (u/get-or-generate-thumbnail system image-params)]
            (create-image-response thumb image-params)
            (error-response 404 image-params))))])

(defn all-routes
  [system]
  (-> (apply routes
             (concat (app-routes system)
                     (legacy-routes system)
                     (list
                       (GET "/ping" [] "pong")
                       (files "/static/")
                       (bad-request-path))))
      (wrap-params)
      (exception-catcher)
      (request-timer)
      (add-headers)))

(defn original-request->file
  [request system image-params]
  (if (force-thumb? request)
    (u/get-or-generate-thumbnail system (image-params->forced-thumb-params image-params))
    (get-original (store system) image-params)))


(defn handle-thumbnail
  [system image-params]
  (if-let [thumb (u/get-or-generate-thumbnail system image-params)]
    (create-image-response thumb image-params)
    (error-response 404 image-params)))

(defn handle-original
  [system image-params]
  (if-let [file (get-original (store system) image-params)]
    (create-image-response file image-params)
    (error-response 404 image-params)))

(defn route-params->image-type
  [route-params]
  (if (clojure.string/blank? (:image-type route-params))
    "images"
    (clojure.string/replace (:image-type route-params)
                            #"^\/(.*)"
                            "$1")))

(defn route->image-type
  [request-map]
  (assoc request-map :image-type (route-params->image-type request-map)))

(defn route->original-map
  [request-map request]
  (-> request-map
      (assoc :request-type :original)
      (route->image-type)
      (route->options request)))

(defn route->thumbnail-map
  [request-map request &[options]]
  (-> request-map
      (assoc :request-type :thumbnail)
      (route->image-type)
      (route->options request)
      (cond->
        options (merge options))))

(defn route->thumbnail-auto-height-map
  [request-map request]
  (route->thumbnail-map request-map request {:height :auto}))

(defn route->options
  "Extracts the query options and moves them to 'request-map'"
  [request-map request]
  (assoc request-map :options (extract-query-opts request)))
