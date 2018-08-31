(ns vignette.storage.s3-test
  (:require [aws.sdk.s3 :as s3]
            [clojure.java.io :as io]
            [midje.sweet :refer :all]
            [pantomime.mime :refer [mime-type-of]]
            [vignette.storage.protocols :refer :all]
            [vignette.storage.s3 :refer :all])
  (:import [com.amazonaws.services.s3.model AmazonS3Exception]))

(facts :s3 :add-timeouts
  (add-timeouts :get {}) => (just {:conn-timeout integer? :socket-timeout integer?})
  (add-timeouts :put {}) => (just {:conn-timeout integer? :socket-timeout integer?}))

(facts :s3 :get-object
  (get-object (create-s3-storage-system ..creds..) "bucket" "a/ab/image.jpg") => ..object..
  (provided
    (add-timeouts :get ..creds..) => ..timeout-creds..
    (safe-get-object ..timeout-creds.. "bucket" "a/ab/image.jpg") => {:content ..stream..
                                                                      :metadata {:content-length ..length..
                                                                                 :content-type ..content-type..}
                                                                      :key "a/ab/image.jpg"}
    (create-stored-object ..stream.. {:content-length ..length..
                                          :content-type ..content-type..} "image.jpg") => ..object..)

  (get-object (create-s3-storage-system ..creds..) "bucket" "a/ab/image.jpg") => falsey
  (provided
    (add-timeouts :get ..creds..) => ..timeout-creds..
    (s3/get-object ..timeout-creds.. "bucket" "a/ab/image.jpg") => {})

  (get-object (create-s3-storage-system ..creds..) "bucket" "d/do/does-not-exist.jpg") => falsey
  (provided
    (add-timeouts :get ..creds..) => ..timeout-creds..
    (s3/get-object ..timeout-creds.. "bucket" "d/do/does-not-exist.jpg") =throws=> (let [e (AmazonS3Exception. "foo")]
                                                                                     (.setStatusCode e 404)
                                                                                     e)))

(facts :s3 :put-object
  (put-object (create-s3-storage-system ..creds..) ..resource.. "bucket" "a/ab/image.jpg") => ..response..
  (provided
    (s3/bucket-exists? ..creds.. "bucket") => true
    (add-timeouts :put ..creds..) => ..timeout-creds..
    (file-stream ..resource..) => ..file..
    (content-type ..resource..) => ..content-type..
    (s3/put-object ..timeout-creds.. "bucket" "a/ab/image.jpg" ..file.. {:content-type ..content-type..}) => ..response..)

  ; this may not be realistic. we'll probably get an error before we get nil
  (put-object (create-s3-storage-system ..creds..) ..resource.. "bucket" "a/ab/image.jpg") => nil
  (provided
    (s3/bucket-exists? ..creds.. "bucket") => true
    (add-timeouts :put ..creds..) => ..timeout-creds..
    (file-stream ..resource..) => ..file..
    (content-type ..resource..) => ..content-type..
    (s3/put-object ..timeout-creds.. "bucket" "a/ab/image.jpg" ..file.. {:content-type ..content-type..}) => nil)

 (put-object (create-s3-storage-system ..creds..) ..resource.. "bucket" "a/ab/image.jpg") => ..response..
 (provided
   (s3/bucket-exists? ..creds.. "bucket") => false
   (s3/create-bucket ..creds.. "bucket" ) => ..response..
   (add-timeouts :put ..creds..) => ..timeout-creds..
   (file-stream ..resource..) => ..file..
   (content-type ..resource..) => ..content-type..
   (s3/put-object ..timeout-creds.. "bucket" "a/ab/image.jpg" ..file.. {:content-type ..content-type..}) => ..response..))

(facts :s3 :object-exists
  (object-exists? (create-s3-storage-system ..creds..) ..bucket.. ..path..) => true
  (provided
    (s3/list-objects ..creds.. ..bucket.. {:prefix ..path.. :max-keys 1}) => {:not-empty-map true})

  (object-exists? (create-s3-storage-system ..creds..) ..bucket.. ..path..) => false
  (provided
    (s3/list-objects ..creds.. ..bucket.. {:prefix ..path.. :max-keys 1}) => nil)

  (object-exists? (create-s3-storage-system ..creds..) ..bucket.. ..path..) => false
  (provided
    (s3/list-objects ..creds.. ..bucket.. {:prefix ..path.. :max-keys 1}) =throws=> (let [e (AmazonS3Exception. "test")]
                                                                                      (.setErrorCode e "AccessDenied") e))

  (object-exists? (create-s3-storage-system ..creds..) ..bucket.. ..path..) => (throws AmazonS3Exception)
  (provided
    (s3/list-objects ..creds.. ..bucket.. {:prefix ..path.. :max-keys 1}) =throws=> (let [e (AmazonS3Exception. "test")]
                                                                                      (.setErrorCode e "Other") e)))
