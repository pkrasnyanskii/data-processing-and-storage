(ns task-c6)

;;;an empty route map
;;;it is enough to use either forward or backward part (they correspond to each other including shared reference to number of tickets)
;;;:forward is a map with route start point names as keys and nested map as values
;;;each nested map has route end point names as keys and route descriptor as values
;;;each route descriptor is a map (structure in fact) of the fixed structure where 
;;;:price contains ticket price
;;;and :tickets contains reference to tickets number
;;;:backward has the same structure but start and end points are reverted 
(def empty-map
  {:forward {},
   :backward {}})

(defn route
  "Add a new route (route) to the given route map
   route-map - route map to modify
   from - name (string) of the start point of the route
   to - name (string) of the end poiunt of the route
   price - ticket price
   tickets-num - number of tickets available"
  [route-map from to price tickets-num]
  (let [tickets (ref tickets-num :validator (fn [state] (>= state 0))),     ;reference for the number of tickets 
        orig-source-desc (or (get-in route-map [:forward from]) {}),
        orig-reverse-dest-desc (or (get-in route-map [:backward to]) {}),
        route-desc {:price price,                                            ;route descriptor
                   :tickets tickets},
        source-desc (assoc orig-source-desc to route-desc),
        reverse-dest-desc (assoc orig-reverse-dest-desc from route-desc)]
    (-> route-map
      (assoc-in [:forward from] source-desc)
      (assoc-in [:backward to] reverse-dest-desc))))

(def transact-cnt
  "Сколько раз стартовало тело транзакции бронирования (с учётом перезапусков)."
  (atom 0))

(defn- legs-from
  "Рейсы из города city, на которые ещё остались билеты: [куда {:price .. :tickets ..}].
   Читаем ref'ы внутри транзакции, поэтому видим согласованный снимок."
  [route-map city]
  (filter (fn [[_ leg]] (pos? @(leg :tickets)))
          (get-in route-map [:forward city])))

(defn- cheapest-path
  "Алгоритм Дейкстры по рейсам, на которые ещё есть билеты.
   Возвращает {:price .. :path (города после старта) :legs (описания рейсов)}
   или nil, если долететь нельзя. Сама ничего не меняет."
  [route-map from to]
  (loop [queue (sorted-set [0 from])        ; [цена города, город], самый дешёвый первым
         price-to {from 0}
         came-by {}]                        ; город -> [откуда, рейс]
    (if-let [[price city :as head] (first queue)]
      (cond
        (= city to)
        (loop [city to, path '(), legs '()]
          (if (= city from)
            {:price price :path path :legs legs}
            (let [[prev leg] (came-by city)]
              (recur prev (cons city path) (cons leg legs)))))

        ;; в очереди остался устаревший вариант - до города уже добрались дешевле
        (> price (price-to city))
        (recur (disj queue head) price-to came-by)

        :else
        (let [[queue price-to came-by]
              (reduce (fn [[queue price-to came-by] [next-city leg]]
                        (let [new-price (+ price (leg :price))]
                          (if (< new-price (price-to next-city Long/MAX_VALUE))
                            [(conj queue [new-price next-city])
                             (assoc price-to next-city new-price)
                             (assoc came-by next-city [city leg])]
                            [queue price-to came-by])))
                      [(disj queue head) price-to came-by]
                      (legs-from route-map city))]
          (recur queue price-to came-by)))
      nil)))

(defn book-tickets
  "Tries to book tickets and decrement appropriate references in route-map atomically
   returns map with either :price (for the whole route) and :path (a list of destination names) keys 
          or with :error key that indicates that booking is impossible due to lack of tickets"
  [route-map from to]
  (if (= from to)
    {:path '(), :price 0}
    (dosync
      (swap! transact-cnt inc)              ; атом не откатывается, считает и перезапуски
      (if-let [{:keys [price path legs]} (cheapest-path route-map from to)]
        (do (doseq [leg legs]
              (alter (leg :tickets) dec))
            {:price price, :path path})
        {:error :no-tickets}))))

;;;cities
(defn make-spec1
  "Карта рейсов с полным запасом билетов. Нужна функцией, а не значением: билеты
   тратятся, и повторный запуск run на старой карте не нашёл бы ни одного."
  []
  (-> empty-map
    (route "City1" "Capital"    200 5)
    (route "Capital" "City1"    250 5)
    (route "City2" "Capital"    200 5)
    (route "Capital" "City2"    250 5)
    (route "City3" "Capital"    300 3)
    (route "Capital" "City3"    400 3)
    (route "City1" "Town1_X"    50 2)
    (route "Town1_X" "City1"    150 2)
    (route "Town1_X" "TownX_2"  50 2)
    (route "TownX_2" "Town1_X"  150 2)
    (route "Town1_X" "TownX_2"  50 2)
    (route "TownX_2" "City2"    50 3)
    (route "City2" "TownX_2"    150 3)
    (route "City2" "Town2_3"    50 2)
    (route "Town2_3" "City2"    150 2)
    (route "Town2_3" "City3"    50 3)
    (route "City3" "Town2_3"    150 2)))

(defn booking-future [route-map from to init-delay loop-delay]
  (future 
    (Thread/sleep init-delay) 
    (loop [bookings []]
      (Thread/sleep loop-delay)
      (let [booking (book-tickets route-map from to)]
        (if (booking :error)
          bookings
          (recur (conj bookings booking)))))))

(defn print-bookings [name ft]
  (println (str name ":") (count ft) "bookings")
  (doseq [booking ft]
    (println "price:" (booking :price) "path:" (booking :path) )))

(defn run []
  ;;try to tune timeouts in order to all the customers gain at least one booking 
  (reset! transact-cnt 0)
  (let [spec (make-spec1),
        f1 (booking-future spec "City1" "City3" 0 10),
        f2 (booking-future spec "City1" "City2" 0 10),
        f3 (booking-future spec "City2" "City3" 0 10),
        r1 @f1, r2 @f2, r3 @f3]
    (print-bookings "City1->City3:" r1)
    (print-bookings "City1->City2:" r2)
    (print-bookings "City2->City3:" r3)
    ;; каждый покупатель делает ещё один, последний, неудачный вызов book-tickets
    (let [calls (+ (count r1) (count r2) (count r3) 3)]
      (println "Total (re-)starts:" @transact-cnt)
      (println "Transaction calls:" calls "restarts:" (- @transact-cnt calls)))))

(defn -main [& _]
  (run)
  (shutdown-agents))
