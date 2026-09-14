# Поточні контейнери: перевірка й пропозиції

Перевірено 11.09.2026 після основного аудиту. Пріоритет користувача — стабільність локального стеку; безпекові зміни відкладені. Контейнери не перезапускалися, дані й робочі Compose-файли не змінювалися. Підготовлено два окремі overrides, що пройшли `docker compose config --quiet` з поточними файлами проєктів.

## Що змінилося відносно попередніх висновків

Ревізії ті самі: autotest `d600648`, TCM `2b45098`, runner `b56ff10`. Тепер перевірені restart policy, реальні процеси, timestamps запуску БД, stop timeout, logging driver, Docker networks і поточне споживання ресурсів.

**Причина серії падінь TCM уточнена:** 11.09 між 07:31 і 07:34 UTC startup падав із `UnknownHostException: postgres`. PostgreSQL запустився о 07:34:13 UTC, готовий о 07:34:14; TCM успішно стартував о 07:34:30. У PostgreSQL фактична restart policy — `no`, у TCM і runner — `unless-stopped`. Це пояснює механізм падінь: application відновлюється раніше за недоступну БД. Подію, яка початково зупинила PostgreSQL/Docker Desktop, окремо не встановлено.

У PostgreSQL при startup є `database system was not properly shut down; automatic recovery in progress`. У всіх трьох контейнерів фактичний `StopTimeout=1`. Це привід збільшити час зупинки; сам по собі лог не доводить, що саме цей timeout спричинив попереднє переривання.

## Перший пакет: уже підготовлений

1. **PostgreSQL: `restart: unless-stopped`.** Додати узгоджений автозапуск БД. Зберегти існуючий PostgreSQL healthcheck і `depends_on: service_healthy` TCM. Не вважати порядок `docker compose up` повноцінною обробкою недоступності БД після restart Docker Engine: потрібна також стійкість startup/reconnect у застосунку. [Docker: порядок запуску](https://docs.docker.com/compose/how-tos/startup-order/), [restart policies](https://docs.docker.com/reference/compose-file/services/#restart).

2. **Runner: `init: true`.** Зараз Java — PID 1; два `headless_shell` мають PPID 1 і стан `Z`. Init перехоплює сигнали й прибирає zombie-процеси. Це конкретне покращення для поточного Playwright runner. Воно не замінює cleanup процесів у коді. [Docker init](https://docs.docker.com/reference/compose-file/services/#init), [Playwright Docker](https://playwright.dev/java/docs/docker#recommended-docker-configuration).

3. **Нормальна зупинка:** PostgreSQL 60 с, TCM 30 с, runner 60 с через `stop_grace_period`. Це запропоновані стартові значення, не виміряний час завершення. Код runner ще не вміє надійно зберігати чергу й виконувати drain активного прогону, тому збільшення timeout не гарантує завершення тестів. [Docker stop_grace_period](https://docs.docker.com/reference/compose-file/services/#stop_grace_period).

4. **Healthcheck TCM і runner.** Перевірено, що `wget /login` працює всередині TCM, `curl /api/v1/health` — усередині runner і повертає 200. Ці probes показують HTTP liveness. Для readiness потрібно додати відповідні endpoints/перевірки коду, бо `/login` не доводить працездатність БД, а runner зараз безумовно повертає UP. Сам статус unhealthy не є командою Docker перезапустити контейнер.

5. **Ротація stdout/stderr Docker.** Фактично `json-file` з порожнім набором options. Пропозиція — `max-size: 10m`, `max-file: 3` на кожен контейнер. Нове налаштування застосовується при recreation; це окремо від Maven logs і Allure-файлів. [Docker JSON logging](https://docs.docker.com/engine/logging/drivers/json-file/).

Файли:

- [tcm.container-reliability.yml](C:/Users/gigam/IdeaProjects/erp-auto-test/docs/audits/2026-09-11/tcm.container-reliability.yml) — шар над поточним TCM compose.
- [runner.container-reliability.yml](C:/Users/gigam/IdeaProjects/erp-auto-test/docs/audits/2026-09-11/runner.container-reliability.yml) — шар над runner compose **і docker-compose.dev.yml**, які реально використані в поточному контейнері.

Overrides пройшли статичну Compose-валідацію. Поведінку після recreation ще не перевірено. Вони не змінюють images, порти, мережі, env credentials, volumes чи CPU/RAM. Їх потрібно застосовувати поза активним прогоном: поточний runner тримає queue/status у пам’яті. Зберегти наявні volumes і не використовувати `down -v`. Це умови коректної міграції, а не необхідність змінювати дані під час поточної перевірки.

## Другий пакет: мережа й зберігання

**Одна спільна Docker-мережа для TCM і runner.** Зараз TCM/БД — `tcm-net`, runner — `erp-test-runner_default`; обидва application викликають один одного через `host.docker.internal` та опубліковані порти Windows. Приєднати runner до спільної мережі й змінити URL на `http://tcm:8080` та `http://test-runner:8080`. ERP/VPN mappings залишаються окремою залежністю. Docker підтримує service-name DNS між Compose-проєктами через shared external network. Це спрощення маршруту; воно саме по собі не усуває startup PostgreSQL. [Docker networking](https://docs.docker.com/compose/how-tos/networking/#connecting-multiple-compose-projects).

TCM уже має альтернативний service `test-runner` у профілі `with-runner`. Обрати один підтримуваний спосіб запуску ранера й уникати одночасного ввімкнення обох service definitions. Поточний standalone runner використовує правильний для Windows Linux-volume поверх `target`; TCM profile-варіант не має аналогічного overlay й не є рівнозначною заміною без доопрацювання.

**Керовані volumes для build і результатів.** Поточний `target` — анонімний Docker volume; код — bind mount із Windows; Maven cache і run logs — named volumes. Linux volume для build варто зберегти, дати йому явне ім’я та окремо виділити artifacts за `runId`. Перехід на новий volume потребує плану збереження/перенесення історичних результатів; просте перейменування mount не переносить дані.

У `allure-results` досі 15G, run logs — 11M. У Linux VM filesystem зараз близько 24G використано і 933G логічно доступно: негайне заповнення filesystem не підтверджене, а доступність диска Windows-хоста окремо не вимірювалась. Перша мета — коректна ізоляція результатів і контроль росту. Docker log rotation не видаляє Allure. Retention робити за завершеними runs після визначення політики збереження, без загального `docker volume prune`.

## Ресурси та images

Docker Desktop бачить 16 CPU / 15.3 GiB RAM. У поточному idle snapshot: TCM ~631 MiB, PostgreSQL ~71 MiB, runner ~379 MiB; runner обмежений 2 CPU / 4 GiB. У поточному Docker state OOMKilled=false. Даних пікового споживання під час regression немає, тому збільшення CPU/RAM зараз не обґрунтоване. Виміряти JVM + Maven + browser разом під навантаженням, тоді задати budgets TCM/runner і залишити пам’ять браузеру та native allocations. Не задавати один великий глобальний Xmx для всіх Java-процесів контейнера.

`/dev/shm` у runner — 64 MiB. Playwright рекомендує враховувати IPC/shared memory для Chromium. Перевірити браузер під UI regression; за потреби протестувати більший `shm_size` (наприклад, 1 GiB як експеримент) або рекомендований Playwright IPC-режим. Виявлені два zombies не є доказом нестачі shared memory; цю зміну не включено до першого пакета. [Playwright Docker](https://playwright.dev/java/docs/docker#recommended-docker-configuration).

Playwright image і Maven dependency зараз узгоджені на 1.50.0. Оновлювати їх одним перевіреним набором із smoke UI; довільна заміна image окремо від dependency може порушити пошук browser binaries. У TCM runtime встановлено Maven, хоча deployment використовує remote runner. Після остаточного закріплення remote-режиму його можна прибрати. Для runner додати `.dockerignore`, підтримувати версійовані images та зафіксувати test revision у manifest прогону. [Playwright: version matching](https://playwright.dev/java/docs/docker#image-tags).

## Що потребує змін у коді

Persistence черги, deadline, cancel під час preflight, import acknowledgement і JSONL serialization не виправляються налаштуваннями контейнера. Так само per-run Allure потребує зміни шляхів у команді/конфігурації тестів. Ці роботи залишаються в основному аудиті.

**Рекомендація:** спочатку застосувати перевірені overrides в заплановане вікно без активних runs; перевірити запуск і чисту зупинку. Потім окремо змінювати мережу й volumes. Зафіксувати recovery-тест для restart Docker Desktop і перевірити, що PostgreSQL піднімається автоматично, TCM стає healthy, а після короткого UI run не накопичуються zombies. Окремий container на кожен прогін можна розглянути після стабілізації state/outbox; для поточного послідовного локального сценарію це не перша необхідна зміна.
