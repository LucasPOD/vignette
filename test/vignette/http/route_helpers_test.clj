(ns vignette.http.route-helpers-test
  (:require [clojure.java.io :as io]
            [clout.core :refer (route-compile route-matches)]
            [midje.sweet :refer :all]
            [pantomime.mime :refer [mime-type-of]]
            [ring.mock.request :refer :all]
            [slingshot.slingshot :refer [try+]]
            [vignette.http.route-helpers :refer :all]
            [vignette.protocols :refer :all]
            [vignette.util.image-response :refer :all]
            [vignette.storage.protocols :as sp]
            [vignette.util.image-response :as ir]
            [vignette.media-types :as mt]
            [vignette.util.thumbnail :as u])
  (:import java.io.FileNotFoundException))

(def webp-support-request
  (assoc-in (request :get "/bucket/a/ab/ropes.jpg/revision/12345/resize/width/10/height/10") [:headers "accept"] "image/webp"))

(def no-webp-support-request
  (request :get "/bucket/a/ab/ropes.jpg/revision/12345/resize/width/10/height/10"))

(def request-with-format
  (assoc-in (request :get "/bucket/a/ab/ropes.jpg/revision/12345/resize/width/10/height/10") [:query-params :format] "jpg"))

(def request-without-format
  (request :get "/bucket/a/ab/ropes.jpg/revision/12345/resize/width/10/height/10"))

(def original-image-params {})
(def original-forced-image-params {:request-type :thumbnail, :thumbnail-mode "type-convert"})

(facts :handle-thumbnail
       (handle-thumbnail ..store.. ..params.. ..request..) => ..response..
       (provided
         (u/get-or-generate-thumbnail ..store.. ..params..) => ..thumb..
         (create-image-response ..thumb.. ..params..) => ..response..)

       (handle-thumbnail ..store.. ..params.. ..request..) => ..error..
       (provided
         (u/get-or-generate-thumbnail ..store.. ..params..) => nil
         (error-response 404 ..params..) => ..error..))

(facts :handle-original
  (let [original-image-params {}
        original-forced-image-params {:request-type :thumbnail, :thumbnail-mode "type-convert"}]
    (handle-original ..store.. original-image-params ..request..) => ..response..
    (provided
      (sp/get-thumbnail ..store.. original-forced-image-params) => nil
      (sp/get-original ..store.. original-forced-image-params) => ..original..
      (sp/content-type ..original..) => ..mime_type..
      (u/is-passthrough-required ..mime_type.. original-forced-image-params) => true
      (create-image-response ..original.. {}) => ..response..
      )

    (handle-original ..store.. original-image-params ..request..) => ..error..
    (provided
      (u/get-or-generate-thumbnail ..store.. original-forced-image-params) => nil
      (error-response 404 original-image-params) => ..error..)

    (try+
      (handle-original ..store.. original-image-params ..request..)
      (catch [:type :convert-error] e
             (:response-code e))) => 404
    (provided
      (sp/get-thumbnail ..store.. original-forced-image-params) => nil
      (sp/get-original ..store.. original-forced-image-params) => nil)))

(facts :handle-delete
  (let [original-image-params {}]
    (handle-delete ..store.. original-image-params ..request..) =>
    {:body "Forbidden", :headers {"X-Thumbnailer" "Vignette"}, :status 403}

    (handle-delete ..store.. original-image-params {:headers {"x-wikia-internal-request" ""}}) =>
    {:body "", :headers {"X-Thumbnailer" "Vignette"}, :status 200}
    (provided
      (sp/delete-thumbnails ..store.. original-image-params) => true)

    (handle-delete ..store.. original-image-params {:headers {"x-wikia-internal-request" ""}}) =>
    {:body "Server Error", :headers {"X-Thumbnailer" "Vignette"}, :status 500}
    (provided
      (sp/delete-thumbnails ..store.. original-image-params) =throws=> (Exception. "Any exception"))
    ))

(facts :route-params->image-type
       (route-params->image-type {:image-type ""}) => "images"
       (route-params->image-type {:image-type "/images"}) => "images"
       (route-params->image-type {:image-type "/avatars"}) => "avatars")

(facts :autodetect-request-format
       (autodetect-request-format no-webp-support-request {}) => {}
       (autodetect-request-format no-webp-support-request {:format "png"}) => {:format "png"}
       (autodetect-request-format webp-support-request {}) => {:format mt/webp-format}
       (autodetect-request-format webp-support-request {:format "jpeg"}) => {:format "jpeg"}
       (autodetect-request-format webp-support-request {:format "original"}) => {}
       (autodetect-request-format no-webp-support-request {:format "original"}) => {})

(facts :route->webp-request-format
       (route->webp-request-format {:options {}} webp-support-request) => {:options {:format mt/webp-format}, :requested-format nil}
       (route->webp-request-format {:options {:format "jpg"}} webp-support-request) => {:options {:format "jpg"}, :requested-format "jpg"}
       (route->webp-request-format {:options {:format "jpg"}} no-webp-support-request) => {:options {:format "jpg"}, :requested-format "jpg"}
       (route->webp-request-format {:options {}} no-webp-support-request) => {:options {}, :requested-format nil}
       (route->webp-request-format {:options {:format "original"}} webp-support-request) => {:options {}, :requested-format "original"}
       (route->webp-request-format {:options {:format "original"}} no-webp-support-request) => {:options {}, :requested-format "original"})
