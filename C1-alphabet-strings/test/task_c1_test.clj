(ns task-c1-test
  (:require [clojure.test :refer :all]
            [task-c1 :refer [strings]]))

(deftest example-from-task
  (is (= #{"ab" "ac" "ba" "bc" "ca" "cb"}
         (set (strings '("a" "b" "c") 2)))))

(deftest length-one
  (is (= #{"a" "b"} (set (strings '("a" "b") 1)))))

(deftest two-letters-alternate
  (is (= #{"abab" "baba"} (set (strings '("a" "b") 4)))))

(deftest count-matches-formula
  ;; k * (k-1)^(n-1) строк длины n над алфавитом из k букв
  (is (= (* 4 3 3 3) (count (strings '("a" "b" "c" "d") 4)))))

(deftest no-equal-neighbours
  (is (every? (fn [s] (every? (fn [[x y]] (not= x y)) (partition 2 1 s)))
              (strings '("a" "b" "c") 5))))

(deftest all-strings-are-distinct
  (let [result (strings '("a" "b" "c") 6)]
    (is (= (count result) (count (set result))))))
