(ns realworld-server-tester.utils
  (:require [clj-http.client :as client]
            [clansi]
            [clj-time.format :as tf]
            [clj-time.coerce :as tc])
  (:import com.github.javafaker.Faker))

(def POST client/post)
(def PUT client/put)
(def GET client/get)
(def DELETE client/delete)

(def VALID-CHARS
  (map char (concat (range 48 58) (range 66 91) (range 97 123))))

(defn- random-char []
  (nth VALID-CHARS (rand (count VALID-CHARS))))

(defn- random-str [length]
  (apply str (take length (repeatedly random-char))))

(defn mk-name [] (str (-> (Faker.) .name .firstName)) "-" (random-str 3))
(defn mk-password [] (-> (Faker.) .internet .password))
(defn mk-email [] (str (random-str 3) (-> (Faker.) .internet .emailAddress)))
(defn mk-title [] (str (-> (Faker.) .book .title) "-" (random-str 3)))
(defn mk-description [] (-> (Faker.) .gameOfThrones .dragon))
(defn mk-body [] (-> (Faker.) .gameOfThrones .quote))

(defn title [sentence]
  (println (clansi/style (str "\n" sentence ":") :cyan :underline)))

(defn iso8601? [s]
  (= s (tf/unparse (tf/formatter :date-time) (tf/parse s))))

(defn desc-time-ordered? [field coll]
  (apply >= (map #(tc/to-long (tf/parse (field %))) coll)))

(defn all-articles-are-tagged-with
  [tag articles]
  (every? (fn [article] (some (partial = tag) (:tagList article))) articles))

(defn all-articles-are-by-author
  [author articles]
  (every? (fn [article] (= author (:username (:author article)))) articles))

(defn all-articles-are-by-one-of-authors
  [authors articles]
  (every? (fn [article] (some (partial = (:username (:author article))) authors)) articles))

(defn all-articles-are-not-by-author
  [author articles]
  (every? (fn [article] (not= author (:username (:author article)))) articles))
