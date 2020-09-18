(ns timbre-json-appender.core-test
  (:require [clojure.test :refer [deftest is testing] :as t]
            [timbre-json-appender.core :as sut]
            [jsonista.core :as json]
            [taoensso.timbre :as timbre]))

(timbre/set-config! {:level :info
                     :appenders {:json (sut/json-appender)}})

(def object-mapper (json/object-mapper {:decode-key-fn true}))

(defn parse-string [str]
  (json/read-value str object-mapper))

(deftest only-message
  (is (= "Hello" (:msg (parse-string (with-out-str (timbre/info "Hello")))))))

(deftest only-args
  (let [log (parse-string (with-out-str (timbre/info :status 200 :duration 5)))]
    (is (= 200 (-> log :args :status)))
    (is (= 5 (-> log :args :duration)))))

(deftest message-and-args
  (let [log (parse-string (with-out-str (timbre/info "Task done" :duration 5)))]
    (is (= "Task done" (:msg log)))
    (is (= 5 (-> log :args :duration)))))

(deftest unserializable-value
  (testing "in a field"
    (is (= {} (-> (parse-string (with-out-str (timbre/info :a (Object.))))
                  :args
                  :a))))
  (testing "in ExceptionInfo"
    (is (= {} (-> (parse-string (with-out-str (timbre/info (ex-info "poks" {:a (Object.)}))))
                  :err
                  :data
                  :a)))))

(deftest exception
  (is (= "poks" (-> (parse-string (with-out-str (timbre/info (Exception. "poks") "Error")))
                    :err
                    :cause))))

(deftest format-string
  (is (= "Hello World!" (-> (parse-string (with-out-str (timbre/infof "Hello %s!" "World")))
                            :msg)))
  (let [log (parse-string (with-out-str (timbre/infof "%s %d%% ready" "Upload" 50 :role "admin")))]
    (is (=  "Upload 50% ready"
            (:msg log)))
    (is (= {:role "admin"}
           (:args log)))))

(deftest inline-args
  (let [inline-args-config {:level :info
                            :appenders {:json (sut/json-appender {:inline-args? true})}}]
    (testing "simple"
      (let [log (parse-string (with-out-str
                                (timbre/with-config inline-args-config
                              (timbre/info "plop" :a 1))))]
        (is (= "plop" (:msg log)))
        (is (= 1 (:a log)))))
    (testing "with format"
      (let [log (parse-string (with-out-str
                                (timbre/with-config inline-args-config
                                  (timbre/infof "count: %d" 1 :a 1))))]
        (is (= "count: 1" (:msg log)))
        (is (= 1 (:a log)))))
    (testing "no args"
      (let [log (parse-string (with-out-str
                                (timbre/with-config inline-args-config
                                  (timbre/info "test"))))]
        (is (= "test" (:msg log)))
        (is (= #{:timestamp :level :thread :msg} (set (keys log))))))
    (testing "only args"
      (let [log (parse-string (with-out-str
                                (timbre/with-config inline-args-config
                                  (timbre/info :a 1))))]
        (is (= 1 (:a log)))
        (is (= #{:timestamp :level :thread :a} (set (keys log))))))))
