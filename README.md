# Dashchan Continued
Dashchan Continued - форк приложения [Dashchan](https://github.com/Mishiranu/Dashchan).
Создан для исправления некоторых багов и добавления некоторого дополнительного функционала.


## Ссылки для загрузки
Измененный клиент: [DashchanContinued.apk](https://github.com/f77/Dashchan-Extensions/raw/master/update/package/Dashchan.apk)
Расширение двача с лайками: [Dvach.apk](https://github.com/f77/Dashchan-Extensions/raw/master/update/package/DashchanDvach.apk)

## Список изменений клиента и отличий от оригинала:
[CHANGELOG.md](https://github.com/f77/Dashchan/blob/master/CHANGELOG.md)

## Некоторые заметки
- Начиная с версии `2.22.0` Dashchan Continued имеет отличное от оригинала имя пакета, таким образом, их можно устанавливать одновременно. Однако, не все оригинальные модули могут быть видимы приложением-форком, и наоборот, оригинал может не видеть сторонние версии модулей, это происходит из-за различий в API. В любом случае, это не ведет к каким бы то ни было потерям данных, максимум, приложения не будут или будут плохо работать с модулями, не предназначенными для них. **Теперь вы можете иметь одновременно установленными оба приложения: и Dashchan, и Dashchan Continued.**
- Если вы обновились и обнаружили, что теперь у вас 2 приложения Dashchan Continued, вам следует удалить то, которое стояло раньше (имя пакета `com.mishiranu.dashchan`), и оставить новое добавившееся (имя пакета `com.f77.dashchan`). Также следует удалить все установленные модули и скачать заново (с этой страницы скачивается только модуль `dvach` с лайками, все остальное, что вам треубется, со страницы [оригинального приложения](https://github.com/Mishiranu/Dashchan)).
- Оригинальное приложение, начиная с версии `3.0`, хранит бекап своих настроек в той директории, которую выбрал пользователь. Форк 2.0-версии (как и оригинал 2.0-версии) хранит бекапы настроек в директории `Downloads/Dashchan`. Таким образом, если вы выбрали в 3.0-приложении директорию `Downloads/Dashchan`, у них становится общая папка для бекапов и становится возможным восстановить настройки от одного приложения в другом. Однако, в некоторых случаях при данной операции могут возникнуть ошибки, так что делайте это на свой страх и риск.
- Когда-то для форка были изменены модули `arhivach`, `diochan`, `dvach`, `fiftyfive`. Однако, никаких приложений, кроме клиента и модуля `dvach`, качать с этой страницы не рекомендуется.


# Настройка
Если вы восстанавливали бекап настроек от оригинального приложения, то, возможно, вам потребуется их изменить для полноценной работы приложения:
1. Выключите частичную загрузку тредов, чтобы изменения лайков отображались сразу же после перезагрузки страницы. (Настройки -> Форумы -> 2ch.hk -> Частичная загрузка тредов).
2. Настройки -> Форумы -> 2ch.hk -> Тип капчи -> reCAPTCHA 2
3. Общие -> Использовать JavaScript для ReCAPTCHA -> убрать галочку.

## License

Dashchan is licensed under the [Apache License, version 2.0](LICENSE).
