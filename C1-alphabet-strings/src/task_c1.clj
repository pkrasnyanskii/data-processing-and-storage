(ns task-c1)

(defn- extensions
  "Все способы дописать к слову одну букву так, чтобы она не повторила последнюю."
  [alphabet word]
  (map #(str word %)
       (remove #(.endsWith word %) alphabet)))

(defn strings
  "Все строки длины n из букв алфавита (список односимвольных строк),
   в которых нет двух одинаковых символов подряд."
  [alphabet n]
  ;; Начинаем с пустой строки и n раз удлиняем каждое слово на одну букву.
  (reduce (fn [words _]
            (apply concat (map #(extensions alphabet %) words)))
          '("")
          (range n)))

(defn -main [& _]
  (println (strings '("a" "b" "c") 2))
  (println (count (strings '("a" "b" "c") 10))))
