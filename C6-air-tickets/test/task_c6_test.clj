(ns task-c6-test
  (:require [clojure.test :refer :all]
            [task-c6 :refer [empty-map route book-tickets make-spec1]]))

(defn- tickets [route-map from to]
  @(get-in route-map [:forward from to :tickets]))

(deftest same-city
  (is (= {:path '() :price 0} (book-tickets (make-spec1) "City1" "City1"))))

(deftest direct-route
  (let [m (-> empty-map (route "A" "B" 70 2))]
    (is (= {:price 70 :path '("B")} (book-tickets m "A" "B")))
    (is (= 1 (tickets m "A" "B")))))

(deftest routes-are-directed
  (let [m (-> empty-map (route "A" "B" 70 2))]
    (is (contains? (book-tickets m "B" "A") :error))))

(deftest unknown-city
  (let [m (-> empty-map (route "A" "B" 70 2))]
    (is (contains? (book-tickets m "A" "Z") :error))
    (is (contains? (book-tickets m "Z" "A") :error))))

(deftest cheaper-wins-even-with-more-transfers
  (let [m (-> empty-map
              (route "A" "D" 100 5)
              (route "A" "B" 10 5)
              (route "B" "C" 10 5)
              (route "C" "D" 10 5))]
    (is (= {:price 30 :path '("B" "C" "D")} (book-tickets m "A" "D")))))

(deftest expensive-route-when-cheap-is-sold-out
  (let [m (-> empty-map
              (route "A" "D" 100 5)
              (route "A" "B" 10 1)
              (route "B" "D" 10 1))]
    (is (= 20 (:price (book-tickets m "A" "D"))))
    (is (= 100 (:price (book-tickets m "A" "D"))))))

(deftest booking-from-the-task-map
  (let [m (make-spec1)]
    (is (= {:price 250 :path '("Town1_X" "TownX_2" "City2" "Town2_3" "City3")}
           (book-tickets m "City1" "City3")))
    (is (= 1 (tickets m "City1" "Town1_X")))
    (is (= 1 (tickets m "City2" "Town2_3")))
    (is (= 5 (tickets m "City1" "Capital")))))

(deftest all-or-nothing
  ;; на последнем рейсе билетов нет: первые два рейса трогать нельзя
  (let [m (-> empty-map
              (route "A" "B" 10 3)
              (route "B" "C" 10 3)
              (route "C" "D" 10 0))]
    (is (contains? (book-tickets m "A" "D") :error))
    (is (= 3 (tickets m "A" "B")))
    (is (= 3 (tickets m "B" "C")))))

(deftest sells-exactly-as-many-as-there-are
  ;; 40 покупателей борются за 2 пересадочных маршрута по 5 билетов
  (let [m (-> empty-map
              (route "A" "B" 10 5)
              (route "B" "C" 10 5)
              (route "A" "C" 100 5))
        results (doall (pmap (fn [_] (book-tickets m "A" "C")) (range 40)))
        sold (remove :error results)]
    (is (= 10 (count sold)))
    (is (= 5 (count (filter #(= 20 (:price %)) sold))))
    (is (= 5 (count (filter #(= 100 (:price %)) sold))))
    (is (every? zero? [(tickets m "A" "B") (tickets m "B" "C") (tickets m "A" "C")]))))

(deftest concurrent-bookings-never-oversell-shared-leg
  ;; у двух разных маршрутов общий рейс B->C с 6 билетами
  (let [m (-> empty-map
              (route "A" "B" 10 100)
              (route "X" "B" 10 100)
              (route "B" "C" 10 6))
        calls (doall (concat (repeat 20 ["A" "C"]) (repeat 20 ["X" "C"])))
        results (doall (pmap (fn [[from to]] (book-tickets m from to)) calls))]
    (is (= 6 (count (remove :error results))))
    (is (zero? (tickets m "B" "C")))
    (is (= 6 (+ (- 100 (tickets m "A" "B")) (- 100 (tickets m "X" "B")))))))
