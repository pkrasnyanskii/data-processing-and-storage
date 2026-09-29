# J1 — Сервер генерации ключей

## Команды

Сгенерировать ключ и сертификат CA (нужно сделать один раз перед первым запуском сервера):

```bash
./gradlew runCaKeyGen --args="ca.key ca.crt \"NSU Course CA\""
```

Запустить сервер:

```bash
./gradlew runServer --args="--threads 4 --ca-key ca.key --issuer \"NSU Course CA\" --port 9090"
```

Запустить клиента (в отдельном терминале):

```bash
./gradlew runClient --args="--name alice --host localhost --port 9090"
```

Клиент с задержкой перед чтением ответа (симуляция медленного клиента):

```bash
./gradlew runClient --args="--name bob --host localhost --port 9090 --delay 5"
```

Клиент, обрывающий соединение сразу после отправки запроса (симуляция аварийного завершения):

```bash
./gradlew runClient --args="--name carol --host localhost --port 9090 --crash"
```

Прогнать тесты:

```bash
./gradlew test
```
