(ns task-c5-test
  (:require [clojure.test :refer :all]
            [task-c5 :refer [run-dinner lower-bound-ms]]))

(def quick {:think-ms 2 :dine-ms 5 :meals 10})

(deftest everybody-eats-and-forks-are-counted
  (doseq [n [2 3 4 5 6 7]]
    (let [result (run-dinner (assoc quick :philosophers n))]
      ;; каждая вилка нужна двум соседям, каждый пообедал meals раз
      (is (= (repeat n (* 2 (:meals quick))) (:fork-uses result))
          (str n " philosophers")))))

(deftest restarts-are-never-negative
  (let [result (run-dinner (assoc quick :philosophers 5))]
    (is (>= (:attempts result) (* 5 (:meals quick))))
    (is (= (:restarts result) (- (:attempts result) (* 5 (:meals quick)))))
    (is (>= (:restarts result) 0))))

(deftest philosophers-cannot-eat-faster-than-the-table-allows
  (doseq [n [3 4 5]]
    (let [config (merge quick {:philosophers n})
          result (run-dinner config)]
      ;; небольшой допуск на округление таймеров
      (is (>= (:elapsed-ms result) (* 0.95 (lower-bound-ms (merge {:grab-pause-ms 0} config))))
          (str n " philosophers")))))

(deftest works-with-pause-between-forks
  (let [result (run-dinner {:philosophers 4 :think-ms 0 :dine-ms 5 :meals 5
                            :grab-pause-ms 5})]
    (is (= [10 10 10 10] (:fork-uses result)))))

(deftest default-config-is-usable
  (is (= 5 (count (:fork-uses (run-dinner {:meals 2 :dine-ms 5 :think-ms 1}))))))
