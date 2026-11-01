(ns task-c4-test
  (:require [clojure.test :refer :all]
            [task-c4 :refer [storage factory supply-msg]]))

(defn- wait-until
  "Ждёт, пока (pred) станет истинным, но не дольше timeout-ms."
  [pred timeout-ms]
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (cond (pred) true
            (> (System/currentTimeMillis) deadline) false
            :else (do (Thread/sleep 10) (recur))))))

(defn- supply! [store amount]
  (send (store :worker) supply-msg amount))

(deftest factory-produces-after-full-bill
  (let [out (storage "Product" 0)
        plant (factory 2 30 out "Ore" 3)
        ore (storage "Ore" 0 plant)]
    (supply! ore 1)
    (supply! ore 1)
    (Thread/sleep 150)
    (is (zero? @(out :storage)) "двух единиц руды мало для цикла")
    (supply! ore 1)
    (is (wait-until #(= 2 @(out :storage)) 1000))
    (is (zero? @(ore :storage)) "вся руда ушла на производство")))

(deftest several-wares-in-one-bill
  (let [out (storage "Clock" 0)
        plant (factory 1 30 out "Lumber" 5 "Gears" 10)
        lumber (storage "Lumber" 0 plant)
        gears (storage "Gears" 0 plant)]
    (supply! lumber 5)
    (supply! gears 4)
    (supply! gears 6)
    (is (wait-until #(= 1 @(out :storage)) 1000))
    (is (and (zero? @(lumber :storage)) (zero? @(gears :storage))))))

(deftest storage-never-goes-negative
  ;; две фабрики делят один склад, на каждую пришло уведомление про одну и ту же партию
  (let [out-a (storage "A" 0)
        out-b (storage "B" 0)
        a (factory 1 10 out-a "Ore" 4)
        b (factory 1 10 out-b "Ore" 4)
        ore (storage "Ore" 0 a b)]
    (dotimes [_ 20] (supply! ore 2))
    (Thread/sleep 500)
    (is (nil? (agent-error (a :worker))))
    (is (nil? (agent-error (b :worker))))
    (is (nil? (agent-error (ore :worker))))
    (is (<= 0 @(ore :storage)))))

(deftest scarce-ware-is-shared
  ;; руды хватает не на всех: у "металла" потребность 10, у "шестерён" 4.
  ;; Обе фабрики должны выпускать продукт, а не одна забирать всё.
  (let [metal (storage "Metal" 0)
        gears (storage "Gears" 0)
        metal-plant (factory 1 10 metal "Ore" 10)
        gears-plant (factory 4 10 gears "Ore" 4)
        ore (storage "Ore" 0 metal-plant gears-plant)]
    (dotimes [_ 300]
      (supply! ore 2)
      (Thread/sleep 5))
    (is (wait-until #(and (pos? @(metal :storage)) (pos? @(gears :storage)))
                    3000)
        "обе фабрики должны получить руду")))
