# J2 — синхронизированный доступ к списку

Родительская нить кладёт вводимые строки в голову связного списка, настраиваемое число дочерних
нитей бесконечно сортируют его пузырьком. Два варианта: собственный связный список с захватом
по узлам (fine-grained) и `ArrayList`, обёрнутый в `Collections.synchronizedList()`.

## Как запустить

Интерактивный режим, свой список:
```bash
./gradlew runCustom --args="--impl custom --threads 3 --within-delay 300 --between-delay 700"
```
Интерактивный режим, `synchronizedList`:
```bash
./gradlew runSync --args="--impl sync --threads 3 --within-delay 300 --between-delay 700"
```
Enter на пустой строке — печать списка, Ctrl+Z — выход и печать суммарного числа шагов.

Бенчмарк:
```bash
./gradlew runBenchmark --args="--impl custom --threads 4 --within-delay 20 --between-delay 20 --benchmark --duration-seconds 10 --seed-size 30"
```
(`--impl sync` — для второй реализации)

Тесты:
```bash
./gradlew test
```

## Формулы

`w` — within-delay, `b` — between-delay (в секундах), `K` — число дочерних нитей, `T` — время.

- Свой список: `S_custom ≈ K · T / (w + b)` — рост почти линеен по `K`.
- `synchronizedList`: `S_sync ≈ min(K · T/(w+b), T/w)` — упирается в потолок `1/w` шагов/с,
  начиная с `K* = 1 + b/w` нитей.

## Результаты

`w = b = 20ms`, seedSize = 30, duration = 8s (`K* = 2`, потолок sync = 50 шаг/с):

| K | custom: теория | custom: измерено | sync: теория | sync: измерено |
|---|---|---|---|---|
| 1 | 25 | 24.9 | 25 | 24.9 |
| 2 | 50 | 49.4 | 50 | 49.5 |
| 4 | 100 | 97.2 | 50 | 49.6 |
| 8 | 200 | 190.5 | 50 | 50.2 |

Свой список масштабируется почти линейно с числом нитей. `synchronizedList` выходит на потолок
уже при `K=2` и дальше не растёт — на время `within-delay` каждого шага список занят целиком,
узкое местом независимо от числа нитей.
