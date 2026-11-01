(ns task-c3)

(def default-block-size 1000)

(defn pfilter
  "Параллельный вариант filter. Последовательность режется на блоки по block-size
   элементов, каждый блок фильтруется в своём future. Результат ленивый, порядок
   элементов сохраняется, вход может быть бесконечным.

   Заранее запускается не больше (число ядер + 2) блоков, как в pmap, поэтому на
   бесконечной последовательности работа не уходит дальше, чем нужно потребителю."
  ([pred coll]
   (pfilter pred coll default-block-size))
  ([pred coll block-size]
   (let [lookahead (+ 2 (.availableProcessors (Runtime/getRuntime)))
         tasks (map (fn [block] (future (doall (filter pred block))))
                    (partition-all block-size coll))
         ;; lead убегает от tasks на lookahead шагов: пока читаем результат
         ;; текущего блока, следующие уже считаются
         drain (fn drain [tasks lead]
                 (lazy-seq
                   (if (seq lead)
                     (concat @(first tasks)
                             (drain (rest tasks) (rest lead)))
                     ;; хвост: новых блоков больше нет, просто дочитываем
                     (apply concat (map deref tasks)))))]
     (drain tasks (drop lookahead tasks)))))

;; ---- демонстрация ----

(defn heavy?
  "Намеренно медленная проверка на простоту (пробное деление)."
  [n]
  (and (> n 1)
       (loop [d 2]
         (cond (> (* d d) n) true
               (zero? (mod n d)) false
               :else (recur (inc d))))))

(defn -main [& _]
  (let [limit 300000
        numbers (range 1000000000 (+ 1000000000 limit))]
    (println "Cores:" (.availableProcessors (Runtime/getRuntime)))
    (println "\nfilter, finite sequence")
    (let [result (time (doall (filter heavy? numbers)))]
      (println "found:" (count result)))
    (doseq [block [100 1000 10000]]
      (println "\npfilter, block size" block)
      (let [result (time (doall (pfilter heavy? numbers block)))]
        (println "found:" (count result))))
    (println "\nfirst 2000 matches from an infinite sequence")
    (println "filter:")
    (time (doall (take 2000 (filter heavy? (iterate inc 1000000000)))))
    (println "pfilter:")
    (time (doall (take 2000 (pfilter heavy? (iterate inc 1000000000) 500)))))
  (shutdown-agents))
