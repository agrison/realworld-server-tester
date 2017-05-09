(ns realworld-server-tester.core
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.data :as data]
            [slingshot.slingshot :as sl]
            [clansi]
            [clojure.repl :refer [doc]]
            [clj-time.format :as tf])
  (:use [clojure.pprint]
        [realworld-server-tester.utils])
  (:import com.github.javafaker.Faker)
  (:gen-class))

(def base-url (atom ""))

(defn mk-url [endpoint] (str @base-url endpoint))

(defn exchange [f url data & [token]]
  "Exchange information with the server with a predefined function (get/post/put).
  The :body is converted from json to edn with keys as keywords."
  (update-in
   (f (mk-url url)
      {:body         (json/write-str data)
       :content-type :json
       :headers      {"Authorization" (str "Token " token)}
       :accept       :json})
   [:body] #(sl/try+
              (json/read-str % :key-fn keyword)
              (catch Exception e {}))))

(defn safe-exchange [f url data & [token]]
  (sl/try+
   (exchange f url data token)
   (catch Object r
     r)))

(defn compare-errors [errors-map error-fields]
  "Compare the fields in the errors map which is returned from the server,
   to a list of expected fields in error."
  (let [k (set (keys errors-map))
        err (set error-fields)
        diff (data/diff k err)]
    (= (nth diff 2) err)))

(defn ensure-validation-error
  "Ensure that exchanging information with the server with invalid information
   returns a status code 422 with a JSON body containing an 'errors' field
   and compare the content of the 'errors' field with a known list of errors"
  [f url data error-fields & [token]]
  (sl/try+
   (exchange f url data token)
   (catch Object r
     (let [status (:status r)
           body (json/read-str (:body r) :key-fn keyword)
           errors (:errors body)]
       (when-not (and (= status 422)
                (not (nil? errors))
                (compare-errors errors error-fields))
         {:error           "Error validation failed"
          :expected-errors error-fields
          :expected-status 422
          :status          status
          :body            body})))))

(def error-count (atom 0))

(defn test-call
  ([sentence test-fn]
  (test-call sentence test-fn #(not (nil? %))))
  ([sentence test-fn is-ok]
  (let [t (System/currentTimeMillis)
        result (test-fn)
        ok (is-ok result)]
    (println (str "\t["
                  (if ok (clansi/style "PASS" :green)
                      (clansi/style "FAIL" :red))
                  "] " sentence ". "
                  (clansi/style (str (- (System/currentTimeMillis) t) "ms") :bright)))
    (flush)
    (when-not ok
      (do (when-not (nil? result) (pprint result))
          (swap! error-count inc))))))

(defn test-validation
  [sentence f url data error-fields & [token]]
  (test-call sentence #(ensure-validation-error f url data error-fields token) nil?))


; TODO: refactor, it's too complex and disgusting at the same time :-)
(defn has
  ([map field value]
    (has map field value nil nil))
  ([map field value value-fn-doc value-fn-value]
  "Returns nil if the given map has the desired value for a key.
   Otherwise returns a string ready to be printed, detailing what was expected and found.
   Works for simple values, and list.
   If map is a collection then the following field format is expected [:field index] or [:identity index]
   if the content is a simple list and not a map."
  (when-not
   (and (coll? map)
        (or (fn? value) (coll? field) (contains? map field))
        (or (fn? value) (coll? field) (not (nil? (field map))))
        (cond (fn? value) (value (if (= :identity field) map (field map)))
              (coll? field) (if (= :identity (first field))
                                (= value (map (second field)))
                                (= ((first field) (map (second field)))
                                   value))
              :else (= (field map) value)))
    (str "\tExpected field "
         (clansi/style (if (coll? field)
                           (str "[" (second field) "]" (if (= :identity (first field))
                                                           "<body>"
                                                           (str "." (name (first field)))))
                           (if (= :identity field) "<body>" (name field)))
                       :bright :underline)
         " to " (if (fn? value) "comply with" "be") ":\n\t\t"
            (clansi/style (if (fn? value)
                            (str value-fn-doc " `" value-fn-value "`") value) :green)
         "\n\tBut " (if (fn? value) "the following did not" "it was") ":\n\t\t" (clansi/style (if (coll? field)
                                                   (if (= :identity (first field))
                                                       (map (second field))
                                                       ((first field) (map (second field))))
                                                   (if (= :identity field) map (field map)))
                                               :red) "\n"))))

(defn print-error-if-any
  [body checks] (let [e (clojure.string/join checks)]
    (if (not= e "")
      (do (println e)
          (when-not (nil? body)
            (do (println (clansi/style "Server response:" :yellow))
                (pprint body)))
          true)
      false)))

(defn test-action
  ([method url data entity checks]
   (test-action method url data entity nil checks))

  ([method url data entity token checks]
   (let [r (safe-exchange method url data token)
         body (if (string? (:body r)) (json/read-str (:body r) :key-fn keyword) (:body r))
         body-entity (if (nil? entity) r (if (and (coll? body) (contains? body entity)) (entity body) body))
         all-checks (map #(has body-entity
                               (first %) (second %)) checks)]
     (when-not (print-error-if-any body all-checks)
       body))))

(defn register [email username password]
  (test-action POST "/users"
               {:user {:email email :username username :password password}} ; data
               :user ; returned entity
               [[:email email] [:username username] [:bio ""] [:image ""]])) ; checks

(defn login [email password]
  (test-action POST "/users/login"
               {:user {:email email :password password}} ; data
               :user ; returned entity
               [[:email email] [:username seq] [:token seq]])) ; checks

(defn current-user [email username token]
  (test-action GET "/user"
               nil ; data
               :user ; returned entity
               token
               [[:email email] [:username username] [:token token]])) ; checks

(defn update-user [token new-name]
  (test-action PUT "/user"
               {:user {:username new-name}} ; data
               :user ; returned entity
               token
               [[:username new-name] [:token token]])) ; checks

(defn unknown-profile []
  (let [r (safe-exchange GET (str "/profiles/" (mk-name) "-123456") nil)
        checks [(has r :status 404)]]
    (when-not (print-error-if-any nil checks)) {}))

(defn follow-user [name follow token]
  (test-action (if (true? follow) POST DELETE) (str "/profiles/" name "/follow")
               nil ; data
               :profile ; returned entity
               token
               [[:username name] [:following follow]])) ; checks

(defn get-profile [name following token]
  (test-action GET (str "/profiles/" name)
               nil ; data
               :profile ; returned entity
               token
               [[:username name] [:following following]])) ; checks

(defn unknown-article []
  (let [r (safe-exchange GET (str "/articles/" (mk-name) "-123456") nil)
        checks [(has r :status 404)]]
    (when-not (print-error-if-any nil checks)) {}))

(defn get-article [slug title tag-list favorited token]
  (test-action GET (str "/articles/" slug)
               nil ; data
               :article ; returned entity
               token
               [[:slug slug] [:title title] [:tagList tag-list]
                [:favorited favorited] [:author seq]
                [:createdAt iso8601?]])) ; checks

(defn create-article [title description body tag-list token]
  (test-action POST "/articles"
               {:article {:title title :description description
                          :body body :tagList tag-list}}
               :article
               token
               [[:title title] [:description description] [:slug seq]
                [:body body] [:tagList tag-list]]))

(defn update-article [slug title description body token]
  (test-action PUT (str "/articles/" slug)
               {:article {:title title :description description
                          :body body}}
               :article
               token
               [[:title title]
                [:description description] [:slug seq]
                [:body body]]))

(defn delete-article [slug token]
  (test-action DELETE (str "/articles/" slug) nil nil token []))

(defn test-forbidden [method url data token]
  (let [r (safe-exchange method url data token)
        checks [(has r :status 403)]]
    (when-not (print-error-if-any r checks)) {}))

(defn test-unauthorized [method url data token]
  (let [r (safe-exchange method url data token)
        checks [(has r :status 401)]]
    (when-not (print-error-if-any r checks)) {}))

(defn delete-unknown-article [token]
  (let [r (safe-exchange DELETE (str "/articles/" (mk-name) "-123456") nil token)
        checks [(has r :status 404)]]
    (when-not (print-error-if-any r checks)) {}))

(defn create-comment [slug body token]
  (test-action POST (str "/articles/" slug "/comments")
               {:comment {:body body}}
               :comment
               token
               [[:body body]]))

(defn delete-comment [slug id token]
  (let [r (safe-exchange DELETE (str "/articles/" slug "/comments/" id) nil token)
        checks [(has r :status 200)]]
    (when-not (print-error-if-any r checks)) {}))

(defn test-delete-another-user-comment [slug id token]
  (let [r (safe-exchange DELETE (str "/articles/" slug "/comments/" id) nil token)
        checks [(has r :status 403)]]
    (when-not (print-error-if-any r checks)) {}))

(defn test-get-comments [slug bodys]
  (test-action GET (str "/articles/" slug "/comments") nil
               :comments
               (map-indexed #(list [:body %1] %2) bodys)))

(defn test-get-tags [tags]
  (test-action GET (str "/tags") nil
               :tags
               (map-indexed #(list [:identity %1] %2) tags)))

(defn test-get-articles [tag author favorited limit token]
  (let [r (safe-exchange GET (str "/articles?"
                                  (when tag (str "tag=" tag "&"))
                                  (when author (str "author=" author "&"))
                                  (when favorited (str "favorited=" favorited "&"))
                                  (when limit (str "limit=" limit "&"))) nil token)
        body (get-in r [:body :articles])
        checks [(has r :status 200)
                (has body :identity (partial desc-time-ordered? :createdAt)
                     "All articles must be ordered desc by field" "createdAt")
                (when tag
                  (has body :identity (partial all-articles-are-tagged-with tag)
                       "All articles are tagged with" tag))
                (when author
                  (has body :identity (partial all-articles-are-by-author author)
                       "All articles are from author" author))
                (when favorited
                  (has body :identity #(every? (fn [a] (= (:username (:author a)))) %)
                       "All articles are favorited by user" favorited))
                (when limit
                  (has body :identity #(<= (count %) limit)
                       "Articles returned are at most" limit))]]
    (when-not (print-error-if-any nil checks)
      body)))

(defn test-get-feed [followed not-followed limit token]
  (let [r (safe-exchange GET (str "/articles/feed?"
                                  (when limit (str "limit=" limit "&"))) nil token)
        body (get-in r [:body :articles])
        checks [(has r :status 200)
                (has body :identity (partial desc-time-ordered? :createdAt)
                     "All articles must be ordered desc by field" "createdAt")
                (has body :identity (partial all-articles-are-by-one-of-authors followed)
                       "All articles are from one of these author" followed)
                (has body :identity (partial all-articles-are-not-by-author not-followed)
                     "No article is from author" not-followed)
                (when limit
                  (has body :identity #(<= (count %) limit)
                       "Articles returned are at most" limit))]]
    (when-not (print-error-if-any nil checks)
      body)))

(defn favorite-article [slug favorited token]
  (test-action (if (true? favorited) POST DELETE) (str "/articles/" slug "/favorite")
               nil
               :article
               token
               [[:slug slug] [:favorited favorited]]))

(defn test-register [email username password]
  (do
    (title "Registering")
    (test-call "Registering with valid information" #(register email username password))
    ; validations
    (test-validation "Can't register with invalid email" POST "/users" {:user {:email "foo" :username "foo" :password "foo"}} [:email])
    (test-validation "Can't register with invalid username" POST "/users" {:user {:email "foo@foo.com" :username "f o o" :password "foo"}} [:username])
    (test-validation "Can't register with missing email" POST "/users" {:user {:username "foo" :password "foo"}} [:email])
    (test-validation "Can't register with missing password" POST "/users" {:user {:email "foo@foo.com" :username "foo"}} [:password])
    (test-validation "Can't register with missing username" POST "/users" {:user {:email "foo@foo.com" :password "foo"}} [:username])
    (test-validation "Can't register with missing email & password" POST "/users" {:user {:username "foo"}} [:email :password])
    (test-validation "Can't register with missing email & username" POST "/users" {:user {:password "foo"}} [:email :username])
    (test-validation "Can't register with missing email & username & password" POST "/users" {:user {}} [:email :username :password])
    (test-validation "Can't register with empty email" POST "/users" {:user {:email "" :username "foo" :password "foo"}} [:email])
    (test-validation "Can't register with empty password" POST "/users" {:user {:email "foo@foo.com" :username "foo" :password ""}} [:password])
    (test-validation "Can't register with empty email & password" POST "/users" {:user {:email "" :username "foo" :password ""}} [:email :password])
    (test-validation "Can't register with empty username & password" POST "/users" {:user {:email "foo@foo.com" :username "" :password ""}} [:username :password])
    (test-validation "Can't register with empty email & username & password" POST "/users" {:user {:email "" :username "" :password ""}} [:email :username :password])
    (let [u (register (mk-email) (mk-name) (mk-password))]
      (test-validation "Can't register with already taken email" POST "/users" {:user {:email (:email u) :username (mk-name) :password "foo"}} [:email])
      (test-validation "Can't register with already taken username" POST "/users" {:user {:email (mk-email) :username (:username u) :password "foo"}} [:username])
      )
    ))

(defn test-login [email username password]
  (do
    (title "Login")
    (register email username password)
    (test-call "Login with valid information" #(login email password))
    ; validations
    (test-validation "Can't login with invalid email" POST "/users/login" {:user {:email "foo" :password "foo"}} [:email])
    (test-validation "Can't login with missing email" POST "/users/login" {:user {:password "foo"}} [:email])
    (test-validation "Can't login with missing password" POST "/users/login" {:user {:email "foo@foo.com"}} [:password])
    (test-validation "Can't login with missing email & password" POST "/users/login" {:user {}} [:email :password])
    (test-validation "Can't login with empty email" POST "/users/login" {:user {:email "" :password "foo"}} [:email])
    (test-validation "Can't login with empty password" POST "/users/login" {:user {:email "foo@foo.com" :password ""}} [:password])
    (test-validation "Can't login with empty email & password" POST "/users/login" {:user {:email "" :password ""}} [:email :password])
    (test-validation "Can't login with unknown email" POST "/users/login" {:user {:email "foo@foo.com" :password "lol"}} [:email])))

(defn test-user [email username password]
  (do
    (title "User")
    (register email username password)
    (let [token (get-in (login email password) [:user :token])
          other-user (:user (register (mk-email) (mk-name) (mk-password)))]
      ; current user
      (test-call "Can get current user" #(current-user email username token))
      (test-call "Can't get current user without auth" #(test-unauthorized GET "/user" nil nil))
      ; update user
      (test-call "Can update user" #(update-user token (mk-name)))
      (test-call "Can't update user without auth" #(test-unauthorized PUT "/user" {:user {}} nil))
      ; validation
      (test-validation "Can't update user with invalid email" PUT "/user" {:user {:email "foo"}} [:email] token)
      (test-validation "Can't update user with invalid username" PUT "/user" {:user {:username "f o o"}} [:username] token)
      (test-validation "Can't update user with blank email" PUT "/user" {:user {:email ""}} [:email] token)
      (test-validation "Can't update user with blank username" PUT "/user" {:user {:username ""}} [:username] token)
      (test-validation "Can't update user with email of another user" PUT "/user" {:user {:email (:email other-user)}} [:email] token)
      (test-validation "Can't update user with username of another user" PUT "/user" {:user {:username (:username other-user)}} [:username] token))))

(defn test-profile []
  (do
    (title "Profile")
    ; 404
    (test-call "Can't get the profile of an unknown user" unknown-profile)
    (let [user1 (:user (register (mk-email) (mk-name) (mk-password)))
          user2 (:user (register (mk-email) (mk-name) (mk-password)))]
      (test-call "User can get the profile of another user" #(get-profile (:username user2) false (:token user1)))
      (test-call "User can't follow without authentication" #(test-unauthorized POST (str "/profiles/" (:username user2) "/follow") nil nil))
      (test-call "User can follow another user" #(follow-user (:username user2) true (:token user1)))
      (test-call "User can see they're now following" #(get-profile (:username user2) true (:token user1)))
      (test-call "User can't unfollow without authentication" #(test-unauthorized DELETE (str "/profiles/" (:username user2) "/follow") nil nil))
      (test-call "User can unfollow another user" #(follow-user (:username user2) false (:token user1)))
      (test-call "User can see they're not following anymore" #(get-profile (:username user2) false (:token user1))))))

(defn test-article [email username password]
  (do
    (title "Article")
    ; 404
    (test-call "Can't get an unexisting article" #(unknown-article))
    (let [token (get-in (register email username password) [:user :token])
          other-user (:user (register (mk-email) (mk-name) (mk-password)))]
      ; create ok
      (test-call "Can create an article" #(create-article (mk-title) (mk-description) (mk-body) [] token))
      (let [a (:article (create-article (mk-title) (mk-description) (mk-body) [] token))]
        (test-call "Can retrieve an article" #(get-article (:slug a) (:title a) (:tagList a) (:favorited a) token)))
      (test-call "Can't create an article without auth" #(test-unauthorized POST "/articles" {:article {}} nil))
      ; validation
      (test-validation "Can't create an article with missing title" POST "/articles" {:article {:description "foo" :body "foo"}} [:title] token)
      (test-validation "Can't create an article with missing description" POST "/articles" {:article {:title "foo" :body "foo"}} [:description] token)
      (test-validation "Can't create an article with missing body" POST "/articles" {:article {:title "foo" :description "foo"}} [:body] token)
      (test-validation "Can't create an article with blank title" POST "/articles" {:article {:title "" :description "foo" :body "foo"}} [:title] token)
      (test-validation "Can't create an article with blank description" POST "/articles" {:article {:title "foo" :description "" :body "foo"}} [:description] token)
      (test-validation "Can't create an article with blank body" POST "/articles" {:article {:title "foo" :description "foo" :body ""}} [:body] token)
      ; update ok
      (let [article (:article (create-article (mk-title) (mk-description) (mk-body) [] token))]
        (test-call "Can create another article with same title" #(create-article (:title article) (:description article) (:body article) [] token))
        (test-call "Can update an article" #(update-article (:slug article) (:title article) "new description" "new body" token))
        (test-call "Can't update an article without auth" #(test-unauthorized PUT (str "/articles/" (:slug article)) {:article {}} nil))
        (test-validation "Can't update an article with blank title" PUT (str "/articles/" (:slug article)) {:article {:title ""}} [:title] token)
        (test-validation "Can't update an article with blank description" PUT (str "/articles/" (:slug article)) {:article {:description ""}} [:description] token)
        (test-validation "Can't update an article with blank body" PUT (str "/articles/" (:slug article)) {:article {:body ""}} [:body] token)
        (test-call "Can't update an article of someone else" #(test-forbidden PUT (str "/articles/" (:slug article)) {:article {}} (:token other-user)))
        (test-call "Can't delete an unexisting article" #(delete-unknown-article token))
        (test-call "Can't delete an article of someone else" #(test-forbidden DELETE (str "/articles/" (:slug article)) {:article {}} (:token other-user)))
        (test-call "Can't delete an article without auth" #(test-unauthorized DELETE (str "/articles/" (:slug article)) nil nil))
        (test-call "Can delete an article" #(delete-article (:slug article) token))
        (let [art (:article (create-article (mk-title) (mk-description) (mk-body) [] token))]
          (test-call "Can favorite an article" #(favorite-article (:slug art) true token))
          (test-call "Can unfavorite an article" #(favorite-article (:slug art) false token)))))))

(defn test-comments []
  (do
    (title "Comments")
    (let [u1 (:user (register (mk-email) (mk-name) (mk-password)))
          token1 (:token u1)
          u2 (:user (register (mk-email) (mk-name) (mk-password)))
          token2 (:token u2)
          a (:article (create-article (mk-title) (mk-description) (mk-body) [] token1))
          slug (:slug a)]
        (test-call "Author can add a comment" #(create-comment slug (mk-body) token1))
        (test-call "Another user can add a comment" #(create-comment slug (mk-body) token2))
        (test-call "Can't add a comment without auth" #(test-unauthorized POST (str "/articles/" slug "/comments") {:comment {}} nil))
        (test-validation "Can't comment an article with missing body" POST (str "/articles/" slug "/comments") {:comment {}} [:body] token1)
        (test-validation "Can't comment an article with blank body" POST (str "/articles/" slug "/comments") {:comment {:body ""}} [:body] token1)
        (let [c1 (:comment (create-comment slug (mk-body) token1))
              c2 (:comment (create-comment slug (mk-body) token1))
              a2 (:article (create-article (mk-title) (mk-description) (mk-body) [] token1))] ; create a third comment
          (test-call "Can delete a comment" #(delete-comment slug (:id c1) token1))
          (test-call "Can't delete a comment of someone else" #(test-delete-another-user-comment slug (:id c2) token2)))
        (let [article (:article (create-article (mk-title) (mk-description) (mk-body) [] token1))
              c1 (:comment (create-comment slug (mk-body) token1))
              c2 (:comment (create-comment slug (mk-body) token2))
              c3 (:comment (create-comment slug (mk-body) token1))]
          (test-call "Can retrieve comments in order" #(test-get-comments slug [(:body c3) (:body c2) (:body c1)]))))))

(defn test-tags []
  (do
    (title "Tags")
    (let [u (:user (register (mk-email) (mk-name) (mk-password)))]
      (create-article (mk-title) (mk-description) (mk-body) ["foo", "bar"] (:token u))
      (create-article (mk-title) (mk-description) (mk-body) ["bar", "bazz"] (:token u))
      (create-article (mk-title) (mk-description) (mk-body) ["bazz"] (:token u))
      (create-article (mk-title) (mk-description) (mk-body) ["foo", "bazz"] (:token u))
      (create-article (mk-title) (mk-description) (mk-body) ["lol"] (:token u))
      (test-call "Can retrieve tags" #(test-get-tags ["foo", "bar", "bazz", "lol"])))))


(defn test-articles []
  (do
    (title "Articles")
    (let [u1 (:user (register (mk-email) (mk-name) (mk-password)))
          u2 (:user (register (mk-email) (mk-name) (mk-password)))]
      (create-article (mk-title) (mk-description) (mk-body) ["java"] (:token u1))
      (create-article (mk-title) (mk-description) (mk-body) ["clojure", "java"] (:token u1))
      (create-article (mk-title) (mk-description) (mk-body) ["ocaml"] (:token u1))
      (create-article (mk-title) (mk-description) (mk-body) ["clojure", "ocaml"] (:token u1))
      (create-article (mk-title) (mk-description) (mk-body) ["redis"] (:token u1))
      (create-article (mk-title) (mk-description) (mk-body) ["redis"] (:token u2))
      (create-article (mk-title) (mk-description) (mk-body) ["mongodb", "nodejs"] (:token u2))
      (create-article (mk-title) (mk-description) (mk-body) ["haskell", "clojure"] (:token u2))
      (create-article (mk-title) (mk-description) (mk-body) ["haskell", "ocaml"] (:token u2))
      (create-article (mk-title) (mk-description) (mk-body) ["neo4j"] (:token u2))
      (test-call "Can retrieve articles with tag" #(test-get-articles "clojure" nil nil 20 nil))
      (test-call "Can retrieve articles with author" #(test-get-articles nil (:username u1) nil 20 nil))
      (test-call "Can retrieve articles with tag & author" #(test-get-articles "clojure" (:username u1) nil 20 nil))
      (test-call "Can retrieve articles with limit" #(test-get-articles "clojure" nil nil 2 nil)))))

(defn test-feed []
  (do
    (title "Feed")
    (let [u1 (:user (register (mk-email) (mk-name) (mk-password)))
          u2 (:user (register (mk-email) (mk-name) (mk-password)))
          u3 (:user (register (mk-email) (mk-name) (mk-password)))
          u4 (:user (register (mk-email) (mk-name) (mk-password)))]
      ; u1 follows u2 and u3, but not u4
      (follow-user (:username u2) true (:token u1))
      (follow-user (:username u3) true (:token u1))
      ; u2 creates some content
      (create-article (mk-title) (mk-description) (mk-body) [] (:token u2))
      (create-article (mk-title) (mk-description) (mk-body) [] (:token u2))
      ; u3 creates some content
      (create-article (mk-title) (mk-description) (mk-body) [] (:token u3))
      ; u4 creates some content
      (create-article (mk-title) (mk-description) (mk-body) [] (:token u4))
      (create-article (mk-title) (mk-description) (mk-body) [] (:token u4))
      (test-call "Can retrieve feed" #(test-get-feed [(:username u2) (:username u3)] (:username u4) 10 (:token u1))))))

(defn suite []
  (swap! realworld-server-tester.core/error-count (constantly 0))
  (test-register (mk-email) (mk-name) (mk-password))
  (test-login (mk-email) (mk-name) (mk-password))
  (test-user (mk-email) (mk-name) (mk-password))
  (test-profile)
  (test-article (mk-email) (mk-name) (mk-password))
  (test-comments)
  (test-tags)
  (test-articles)
  (test-feed)
  (println (str "\n" @error-count " errors.")))

(defn -main [& args]
  (do
    (swap! base-url (constantly (first args)))
    (println "RealWorld sample app backend tester")
    (println (str "\tTesting URL: " @base-url "\n\n"))
    (suite)))