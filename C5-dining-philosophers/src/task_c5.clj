(ns task-c5)

;; Философы сидят по кругу, вилка i лежит между философом i и философом i+1.
;; Каждая вилка - ref со счётчиком: сколько раз её брали.
;; Обед - одна транзакция, которая берёт обе вилки (alter) и ждёт внутри dine-ms.
;; Пока транзакция не закоммитилась, вилки "заняты" ей: соседу, который полезет
;; в ту же вилку, придётся перезапуститься.

(def default-config
  {:philosophers 5
   :think-ms 10                 ; сколько философ думает перед каждым обедом
   :dine-ms 50                  ; сколько длится обед
   :meals 20                    ; сколько раз каждый философ пообедает
   :grab-pause-ms 0})           ; пауза между левой и правой вилкой (для опытов с livelock)

(defn make-forks [n]
  (vec (repeatedly n #(ref 0))))

(defn- dine!
  "Один обед. attempts увеличивается при каждом запуске тела транзакции,
   поэтому перезапусков = attempts - число удавшихся обедов."
  [attempts left right dine-ms grab-pause-ms]
  (dosync
    (swap! attempts inc)
    (alter left inc)
    (when (pos? grab-pause-ms)
      (Thread/sleep grab-pause-ms))
    (alter right inc)
    (Thread/sleep dine-ms)))

(defn- philosopher
  [attempts forks i {:keys [think-ms dine-ms meals grab-pause-ms]}]
  (let [left (forks i)
        right (forks (mod (inc i) (count forks)))]
    (future
      (dotimes [_ meals]
        (Thread/sleep think-ms)
        (dine! attempts left right dine-ms grab-pause-ms)))))

(defn run-dinner
  "Запускает ужин и ждёт, пока все наедятся. Параметры - см. default-config.
   Возвращает:
     :elapsed-ms - общее время
     :attempts   - сколько раз стартовали тела транзакций
     :restarts   - сколько из них были перезапусками
     :fork-uses  - значения счётчиков вилок"
  ([] (run-dinner {}))
  ([config]
   (let [{:keys [philosophers meals] :as config} (merge default-config config)
         forks (make-forks philosophers)
         attempts (atom 0)
         started (System/nanoTime)
         diners (doall (for [i (range philosophers)]
                         (philosopher attempts forks i config)))]
     (run! deref diners)
     {:elapsed-ms (/ (- (System/nanoTime) started) 1e6)
      :attempts @attempts
      :restarts (- @attempts (* philosophers meals))
      :fork-uses (mapv deref forks)})))

(defn lower-bound-ms
  "Оценка снизу: за столом одновременно едят не больше floor(n/2) философов."
  [{:keys [philosophers think-ms dine-ms meals grab-pause-ms]}]
  (max (* meals (+ think-ms dine-ms))
       (/ (* philosophers meals (+ dine-ms grab-pause-ms)) (quot philosophers 2))))

;; ---- опыты ----

(defn- print-header []
  (println (format "%4s %10s %10s %10s %9s" "n" "time, ms" "bound, ms" "attempts" "restarts")))

(defn- print-row [config result]
  (println (format "%4d %10.0f %10.0f %10d %9d"
                   (:philosophers config)
                   (:elapsed-ms result)
                   (double (lower-bound-ms config))
                   (:attempts result)
                   (:restarts result))))

(defn experiment
  "Прогоняет ужин для каждого числа философов из ns и печатает таблицу."
  [ns config]
  (let [config (merge default-config config)]
    (println "config:" (dissoc config :philosophers))
    (print-header)
    (doseq [n ns]
      (let [config (assoc config :philosophers n)]
        (print-row config (run-dinner config))))))

(defn -main [& args]
  (if (seq args)
    ;; lein run <philosophers> <think-ms> <dine-ms> <meals> [grab-pause-ms]
    (let [[n think dine meals pause] (map #(Long/parseLong %) args)
          config {:philosophers n :think-ms think :dine-ms dine :meals meals
                  :grab-pause-ms (or pause 0)}
          result (run-dinner config)]
      (print-header)
      (print-row (merge default-config config) result)
      (println "fork uses:" (:fork-uses result)))
    (do
      (println "== even and odd number of philosophers ==")
      (experiment [2 3 4 5 6 7 8] {})
      (println "\n== no thinking, long dinner: maximum contention ==")
      (experiment [4 5] {:think-ms 0 :dine-ms 100 :meals 10})
      (println "\n== pause between the left and the right fork ==")
      (experiment [4 5] {:think-ms 0 :dine-ms 50 :meals 10 :grab-pause-ms 50})))
  (shutdown-agents))
