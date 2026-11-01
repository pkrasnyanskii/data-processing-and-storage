(ns task-c3-test
  (:require [clojure.test :refer :all]
            [task-c3 :refer [pfilter]]))

(deftest same-as-filter
  (doseq [block-size [1 3 10 100 5000]]
    (is (= (filter even? (range 1000))
           (pfilter even? (range 1000) block-size))
        (str "block-size " block-size))))

(deftest default-block-size-works
  (is (= (filter odd? (range 5000))
         (pfilter odd? (range 5000)))))

(deftest keeps-order
  (let [input (shuffle (range 2000))]
    (is (= (filter #(zero? (mod % 3)) input)
           (pfilter #(zero? (mod % 3)) input 7)))))

(deftest edge-cases
  (is (empty? (pfilter even? [] 10)))
  (is (empty? (pfilter even? [1 3 5] 10)))
  (is (= [2] (pfilter even? [1 2 3] 100)))
  (is (= [1 2 3] (pfilter number? [1 2 3] 1))))

(deftest works-on-infinite-sequence
  (is (= [0 3 6 9 12] (take 5 (pfilter #(zero? (mod % 3)) (range) 10))))
  (is (= (take 100 (filter even? (iterate inc 1)))
         (take 100 (pfilter even? (iterate inc 1) 16)))))

(deftest is-lazy
  (let [calls (atom 0)
        pred (fn [x] (swap! calls inc) true)
        result (pfilter pred (range) 10)]
    (Thread/sleep 100)
    (is (zero? @calls) "до обращения к результату работа не стартует")
    (doall (take 5 result))
    (Thread/sleep 200)
    ;; в работе не больше lookahead блоков, а не вся бесконечная последовательность
    (is (< @calls 10000))))

(deftest actually-parallel
  (let [slow-pred (fn [x] (Thread/sleep 5) (even? x))
        input (range 100)
        t0 (System/nanoTime)
        sequential (doall (filter slow-pred input))
        t1 (System/nanoTime)
        parallel (doall (pfilter slow-pred input 5))
        t2 (System/nanoTime)]
    (is (= sequential parallel))
    (is (< (- t2 t1) (* 0.7 (- t1 t0)))
        "параллельный вариант должен быть заметно быстрее")))
