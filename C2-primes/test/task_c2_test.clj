(ns task-c2-test
  (:require [clojure.test :refer :all]
            [task-c2 :refer [primes]]))

(defn- prime-by-division? [n]
  (and (> n 1)
       (not-any? #(zero? (mod n %)) (range 2 (inc (long (Math/sqrt n)))))))

(deftest first-primes
  (is (= [2 3 5 7 11 13 17 19 23 29] (take 10 primes))))

(deftest known-values
  (is (= 7919 (nth primes 999)))                            ; 1000-е простое
  (is (= 25 (count (take-while #(< % 100) primes))))
  (is (= 1229 (count (take-while #(< % 10000) primes)))))

(deftest is-lazy-and-infinite
  (is (instance? clojure.lang.LazySeq primes))
  (is (= 5 (count (take 5 primes))))
  (is (= 20000 (count (take 20000 primes)))))

(deftest only-primes-and-in-order
  (let [head (take-while #(< % 5000) primes)]
    (is (every? prime-by-division? head))
    (is (apply < head))
    ;; ни одного пропущенного простого
    (is (= (filter prime-by-division? (range 5000)) head))))
