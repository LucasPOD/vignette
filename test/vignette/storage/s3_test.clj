(ns vignette.storage.s3-test
  (:require [aws.sdk.s3 :as s3]
            [clojure.java.io :as io]
            [midje.sweet :refer :all]
            [pantomime.mime :refer [mime-type-of]]
            [vignette.storage.protocols :refer :all]
            [vignette.storage.s3 :refer :all])
  (:import [com.amazonaws.services.s3.model AmazonS3Exception]))

(facts :s3 :get-object
  (get-object (create-s3-storage-system ..creds..) "bucket" "a/ab/image.jpg") => ..object..
  (provided
    (safe-get-object ..creds.. "bucket" "a/ab/image.jpg") => {:content ..stream..
                                                              :metadata {:content-length ..length..
                                                                         :content-type ..content-type..}}
    (create-stored-object ..stream.. {:content-length ..length..
                                          :content-type ..content-type..}) => ..object..)

  (get-object (create-s3-storage-system ..creds..) "bucket" "a/ab/image.jpg") => falsey
  (provided
    (s3/get-object ..creds.. "bucket" "a/ab/image.jpg") => {})

  (get-object (create-s3-storage-system ..creds..) "bucket" "d/do/does-not-exist.jpg") => falsey
  (provided
    (s3/get-object ..creds.. "bucket" "d/do/does-not-exist.jpg") =throws=> (let [e (AmazonS3Exception. "foo")]
                                                                             (.setStatusCode e 404)
                                                                             e)))

(facts :s3 :put-object
  (put-object (create-s3-storage-system ..creds..) ..resource.. "bucket" "a/ab/image.jpg") => ..response..
  (provided
    (file-stream ..resource..) => ..file..
    (content-type ..resource..) => ..content-type..
    (s3/put-object ..creds.. "bucket" "a/ab/image.jpg" ..file.. {:content-type ..content-type..}) => ..response..)

  ; this may not be realistic. we'll probably get an error before we get nil
  (put-object (create-s3-storage-system ..creds..) ..resource.. "bucket" "a/ab/image.jpg") => nil
  (provided
    (file-stream ..resource..) => ..file..
    (content-type ..resource..) => ..content-type..
    (s3/put-object ..creds.. "bucket" "a/ab/image.jpg" ..file.. {:content-type ..content-type..}) => nil))