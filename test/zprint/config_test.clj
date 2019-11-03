(ns zprint.config-test
  (:require [expectations :refer :all]
            [zprint.core :refer :all]
            [zprint.zprint :refer :all]
            [zprint.config :refer :all]
            [zprint.finish :refer :all]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.repl :refer :all]
            [clojure.string :as str]
            [rewrite-clj.parser :as p :only [parse-string parse-string-all]]
            [rewrite-clj.node :as n]
            [rewrite-clj.zip :as z :only [edn*]])
  (:import (com.sun.net.httpserver HttpHandler HttpServer)
           (java.net InetSocketAddress)
           (java.io File)
           (java.util Date)))

;; Keep some of the test from wrapping so they still work
;!zprint {:comment {:wrap? false} :fn-map {"more-of" :arg1}}

;
; Keep tests from configuring from any $HOME/.zprintrc or local .zprintrc
;

(set-options! {:configured? true})

;;
;; # :fn-*-force-nl tests
;;

(expect "(if :a\n  :b\n  :c)"
        (zprint-str "(if :a :b :c)"
                    {:parse-string? true, :fn-force-nl #{:arg1-body}}))

(expect "(if :a\n  :b\n  :c)"
        (zprint-str "(if :a :b :c)"
                    {:parse-string? true, :fn-gt2-force-nl #{:arg1-body}}))

(expect "(if :a :b)"
        (zprint-str "(if :a :b)"
                    {:parse-string? true, :fn-gt2-force-nl #{:arg1-body}}))

(expect "(assoc {} :a :b)"
        (zprint-str "(assoc {} :a :b)"
                    {:parse-string? true, :fn-gt3-force-nl #{:arg1-pair}}))

(expect "(assoc {}\n  :a :b\n  :c :d)"
        (zprint-str "(assoc {} :a :b :c :d)"
                    {:parse-string? true, :fn-gt3-force-nl #{:arg1-pair}}))

(expect
  "(:require [boot-fmt.impl :as impl]\n          [boot.core :as bc]\n          [boot.util :as bu])"
  (zprint-str
    "(:require [boot-fmt.impl :as impl] [boot.core :as bc] [boot.util :as bu])"
    {:parse-string? true, :fn-map {":require" :force-nl-body}}))

(expect
  "(:require\n  [boot-fmt.impl :as impl]\n  [boot.core :as bc]\n  [boot.util :as bu])"
  (zprint-str
    "(:require [boot-fmt.impl :as impl] [boot.core :as bc] [boot.util :as bu])"
    {:parse-string? true, :fn-map {":require" :flow}}))

(expect
  "(:require\n  [boot-fmt.impl :as impl]\n  [boot.core :as bc]\n  [boot.util :as bu])"
  (zprint-str
    "(:require [boot-fmt.impl :as impl] [boot.core :as bc] [boot.util :as bu])"
    {:parse-string? true, :fn-map {":require" :flow-body}}))

;;
;; # Style tests
;;

;;
;; First, let's ensure that we know how to do these tests!
;;

(expect false (:justify? (:binding (get-options))))

;;
;; This is how you do config tests, without altering the configuration
;; for all of the rest of the tests.
;;

(expect true
        (redef-state [zprint.config]
                     (set-options! {:style :justified})
                     (:justify? (:binding (get-options)))))

;;
;; And this shows that it leaves things alone!
;;

(expect false (:justify? (:binding (get-options))))

;;
;; Now, to the actual tests
;;

(expect (more-of options
          true (:justify? (:binding options))
          true (:justify? (:map options))
          true (:justify? (:pair options)))
        (redef-state [zprint.config]
                     (set-options! {:style :justified})
                     (get-options)))

(expect (more-of options
          0 (:indent (:binding options))
          1 (:indent-arg (:list options))
          0 (:indent (:map options))
          0 (:indent (:pair options))
          :none ((:fn-map options) "apply")
          :none ((:fn-map options) "assoc")
          :none ((:fn-map options) "filter")
          :none ((:fn-map options) "filterv")
          :none ((:fn-map options) "map")
          :none ((:fn-map options) "mapv")
          :none ((:fn-map options) "reduce")
          :none ((:fn-map options) "remove")
          :none-body ((:fn-map options) "with-meta"))
        (redef-state [zprint.config]
                     (set-options! {:style :community})
                     (get-options)))

(expect (more-of options
          true (:justify? (:binding options))
          true (:justify? (:map options))
          true (:justify? (:pair options))
          0 (:indent (:binding options))
          1 (:indent-arg (:list options))
          0 (:indent (:map options))
          0 (:indent (:pair options))
          :none ((:fn-map options) "apply")
          :none ((:fn-map options) "assoc")
          :none ((:fn-map options) "filter")
          :none ((:fn-map options) "filterv")
          :none ((:fn-map options) "map")
          :none ((:fn-map options) "mapv")
          :none ((:fn-map options) "reduce")
          :none ((:fn-map options) "remove")
          :none-body ((:fn-map options) "with-meta"))
        (redef-state [zprint.config]
                     (set-options! {:style [:community :justified]})
                     (get-options)))

(expect (more-of options
          true (:nl-separator? (:extend options))
          true (:flow? (:extend options))
          0 (:indent (:extend options)))
        (redef-state [zprint.config]
                     (set-options! {:style :extend-nl})
                     (get-options)))

(expect
  (more-of options
    true (:nl-separator? (:map options))
    0 (:indent (:map options)))
  (redef-state [zprint.config] (set-options! {:style :map-nl}) (get-options)))

(expect
  (more-of options
    true (:nl-separator? (:pair options))
    0 (:indent (:pair options)))
  (redef-state [zprint.config] (set-options! {:style :pair-nl}) (get-options)))

(expect (more-of options
          true (:nl-separator? (:binding options))
          0 (:indent (:binding options)))
        (redef-state [zprint.config]
                     (set-options! {:style :binding-nl})
                     (get-options)))

;;
;; # Test set element addition and removal
;;

; Define a new style

(expect
  (more-of options
    {:extend {:modifiers #{"stuff"}}}
    (:tst-style-1 (:style-map options)))
  (redef-state [zprint.config]
               (set-options! {:style-map {:tst-style-1
                                            {:extend {:modifiers #{"stuff"}}}}})
               (get-options)))

; Apply a new style (which adds a set element)

(expect
  (more-of options #{"static" "stuff"} (:modifiers (:extend options)))
  (redef-state [zprint.config]
               (set-options! {:style-map {:tst-style-1
                                            {:extend {:modifiers #{"stuff"}}}}})
               (set-options! {:style :tst-style-1})
               (get-options)))

; Remove a set element

(expect
  (more-of options #{"stuff"} (:modifiers (:extend options)))
  (redef-state [zprint.config]
               (set-options! {:style-map {:tst-style-1
                                            {:extend {:modifiers #{"stuff"}}}}})
               (set-options! {:style :tst-style-1})
               (set-options! {:remove {:extend {:modifiers #{"static"}}}})
               (get-options)))

; Do the explained-options work?

; Add and remove something

(expect
  (more-of options #{"stuff"} (:value (:modifiers (:extend options))))
  (redef-state [zprint.config]
               (set-options! {:style-map {:tst-style-1
                                            {:extend {:modifiers #{"stuff"}}}}})
               (set-options! {:style :tst-style-1})
               (set-options! {:remove {:extend {:modifiers #{"static"}}}})
               (get-explained-all-options)))

; Add without style

(expect (more-of options
          #{:force-nl :flow :noarg1 :noarg1-body :force-nl-body :binding
            :arg1-force-nl :flow-body}
          (:value (:fn-force-nl options)))
        (redef-state [zprint.config]
                     (set-options! {:fn-force-nl #{:binding}})
                     (get-explained-all-options)))
;
; Tests for argument types that include options maps 
; in :fn-map --> [<arg-type> <options-map>]
;

; Does defproject have an options map, and is it correct?

(expect (more-of options
          true
          (vector? ((:fn-map options) "defproject"))
          {:vector {:wrap? false}}
          (second ((:fn-map options) "defproject")))
        (redef-state [zprint.config] (get-options)))

; Can we set an options map on let?

(expect (more-of options
          true
          (vector? ((:fn-map options) "let"))
          {:width 99}
          (second ((:fn-map options) "let")))
        (redef-state [zprint.config]
                     (set-options! {:fn-map {"let" [:binding {:width 99}]}})
                     (get-options)))

; Will we get an exception when setting an invalid options map?

(expect Exception
        (redef-state [zprint.config]
                     (set-options! {:fn-map {"let" [:binding {:width "a"}]}})
                     (get-options)))

; Will we get an exception when setting an invalid options map inside of an otherwise
; valid options map?

(expect
  Exception
  (redef-state [zprint.config]
               (set-options!
                 {:fn-map {"xx" [:arg1-body
                                 {:fn-map {":export"
                                             [:flow {:list {:hang true}}]}}]}})
               (get-options)))

;; Test config loading via URL

(def url-cache-path (str *cache-path* File/separator "urlcache"))
; New URL
(expect (more-of options
                 1
                 (get options :max-depth))
        (let [options-file (File/createTempFile "load-options" "1")
              cache-file   (io/file url-cache-path (str "nohost_" (hash (str (.toURL options-file)))))]
          (.delete cache-file)
          (spit options-file (print-str {:max-depth 1}))
          (redef-state [zprint.config]
                       (load-options! (.toURL options-file))
                       (.delete cache-file)
                       (get-options))))

; Extend with set-options
(expect (more-of options
                 2
                 (get options :max-depth)
                 22
                 (get options :max-length))
        (let [options-file (File/createTempFile "load-options" "2")
              cache-file   (io/file url-cache-path (str "nohost_" (hash (str (.toURL options-file)))))]
          (.delete cache-file)
          (spit options-file (print-str {:max-depth 2}))
          (redef-state [zprint.config]
                       (set-options! {:max-length 22})
                       (load-options! (.toURL options-file))
                       (.delete cache-file)
                       (get-options))))

; Cached
(expect (more-of options
                 3
                 (get options :max-depth))
        (let [options-file (File/createTempFile "load-options" "3")
              cache-file   (io/file url-cache-path (str "nohost_" (hash (str (.toURL options-file)))))]
          (.delete cache-file)
          (spit options-file (print-str {:max-depth 3}))
          (redef-state [zprint.config]
                       (load-options! (.toURL options-file))
                       (while (not (.exists cache-file))    ;default 5 min cache created async in ms
                         (Thread/sleep 10))
                       (spit options-file (print-str {:max-depth 33})) ;unused remote
                       (load-options! (.toURL options-file))
                       (.delete cache-file)
                       (get-options))))

; Expired cache, get rempte
(expect (more-of options
                 44
                 (get options :max-depth))
        (let [options-file (File/createTempFile "load-options" "4")
              cache-file   (io/file url-cache-path (str "nohost_" (hash (str (.toURL options-file)))))]
          (.delete cache-file)
          (spit options-file (print-str {:max-depth 4}))
          (redef-state [zprint.config]
                       (load-options! (.toURL options-file))
                       (while (not (.exists cache-file))
                         (Thread/sleep 10))
                       (spit cache-file (print-str {:expires 0 :options {:max-depth 4}})) ;expire cache
                       (spit options-file (print-str {:max-depth 44})) ;used remote
                       (load-options! (.toURL options-file))
                       (.delete cache-file)
                       (get-options))))

; Good url, corrupt cache
(expect (more-of options
                 5
                 (get options :max-depth))
        (let [options-file (File/createTempFile "load-options" "5")
              cache-file   (io/file url-cache-path (str "nohost_" (hash (str (.toURL options-file)))))]
          (.delete cache-file)
          (spit options-file (print-str {:max-depth 5}))
          (redef-state [zprint.config]
                       (spit cache-file "{bad-cache")       ;corrupt edn
                       (load-options! (.toURL options-file))
                       (.delete cache-file)
                       (get-options))))

; Bad url, no cache
(expect Exception
        (redef-state [zprint.config]
                     (load-options! "http://b.a.d.u.r.l")
                     (get-options)))

; Write url, bad content, no cache
(expect Exception
        (let [options-file (File/createTempFile "url-bad-content" "1")]
          (spit options-file "{bad-content")
          (redef-state [zprint.config]
                       (load-options! (.toURL options-file)))))

; Bad url, but cache
(expect (more-of options
                 6
                 (get options :max-depth))
        (let [options-file (File/createTempFile "load-options" "6")
              cache-file   (io/file url-cache-path (str "nohost_" (hash (str (.toURL options-file)))))]
          (.delete cache-file)
          (spit options-file (print-str {:max-depth 6}))
          (redef-state [zprint.config]
                       (load-options! (.toURL options-file))
                       (while (not (.exists cache-file))
                         (Thread/sleep 10))
                       (.delete options-file)               ;break url
                       (load-options! (.toURL options-file))
                       (.delete cache-file)
                       (get-options))))

; Bad url, expired cache
(expect (more-of options
                 7
                 (get options :max-depth))
        (let [options-file (File/createTempFile "load-options" "7")
              cache-file   (io/file url-cache-path (str "nohost_" (hash (str (.toURL options-file)))))]
          (.delete cache-file)
          (redef-state [zprint.config]
                       (spit cache-file (print-str {:expires 0 :options {:max-depth 7}})) ;expire cache
                       (.delete options-file)               ;break url
                       (try (load-options! (.toURL options-file))
                            (finally (.delete cache-file)))
                       (get-options))))

; max-age for cache expiry and overrides Expires, else Expires by itself sets cache
(expect (more-of [options cache1 cache2]
                 true (<= (System/currentTimeMillis) (:expires cache1) (+ 1e7 (System/currentTimeMillis)))
                 true (<= (System/currentTimeMillis) (:expires cache1) (.getTime (Date. (- 2999 1900) 9 19))
                          (:expires cache2) (.getTime (Date. (- 2999 1900) 9 23))))
        (let [body          "{:max-depth 8}"
              first-request (atom true)
              http-server   (doto (HttpServer/create (InetSocketAddress. "0.0.0.0" 0) 0) ;any port will do
                              (.createContext "/cfg"
                                              (reify HttpHandler
                                                (handle [this ex]
                                                  (if @first-request (.add (.getResponseHeaders ex) "Cache-Control" (format "max-age=%s" (int 1e4))))
                                                  (.add (.getResponseHeaders ex) "Expires" "Wed, 21 Oct 2999 00:00:00 GMT")
                                                  (.sendResponseHeaders ex 200 (count body))
                                                  (reset! first-request false)
                                                  (doto (io/writer (.getResponseBody ex))
                                                    (.write body)
                                                    (.close)))))
                              (.setExecutor nil)
                              (.start))
              server        (.getAddress http-server)
              url           (format "http://0.0.0.0:%s/cfg" (.getPort server))]
          (let [cache-file (io/file url-cache-path (str "0.0.0.0_" (hash url)))]
            (.delete cache-file)
            (redef-state [zprint.config]
                         (load-options! url)
                         (let [cache1 (-> cache-file slurp edn/read-string)]
                           (.delete cache-file)
                           (load-options! url)
                           (let [cache2 (-> cache-file slurp edn/read-string)]
                             (.stop http-server 0)
                             (.delete cache-file)
                             [(get-options) cache1 cache2]))))))