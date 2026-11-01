(ns task-c2)

;; Решето Эратосфена без верхней границы.
;; Вместо вычёркивания чисел в массиве храним таблицу composites:
;; составное число -> простые, которые его "перепрыгивают" на этом шаге.
;; Когда доходим до n:
;;   - если n есть в таблице, оно составное, и каждый его простой делитель p
;;     переносим на следующее кратное n + p;
;;   - если нет, n простое: выдаём его и записываем в таблицу его квадрат
;;     (меньшие кратные уже вычеркнуты меньшими простыми).
(defn- sieve [n composites]
  (lazy-seq
    (if-let [factors (get composites n)]
      (sieve (inc n)
             (reduce (fn [table p] (update table (+ n p) (fnil conj []) p))
                     (dissoc composites n)
                     factors))
      (cons n (sieve (inc n) (assoc composites (* n n) [n]))))))

(def primes
  "Ленивая бесконечная последовательность простых чисел."
  (sieve 2 {}))

(defn -main [& args]
  (let [n (if (seq args) (Integer/parseInt (first args)) 20)]
    (println (take n primes))))
