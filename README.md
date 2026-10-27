# Whitegram для Android

![Whitegram](https://img.shields.io/badge/Whitegram-v12.1.1.0--LTS-blue.svg)
![License: GPL v2](https://img.shields.io/badge/License-GPLv2-green.svg)
![Android](https://img.shields.io/badge/Android-6.0%2B-brightgreen.svg)

**Whitegram** — это высокопроизводительный неофициальный клиент Telegram для Android со встроенными модулями обхода блокировок (Xray-core и qWDTT). Проект обеспечивает стабильное и защищённое соединение, сохраняя весь привычный функционал Telegram.

---

## Основные возможности

- **Встроенное ядро Xray-core**: Нативная поддержка современных протоколов **VLESS** (включая Reality/XTLS), **VMess**, **Trojan**, **Shadowsocks** и **SOCKS5**.
- **Интеграция qWDTT**: Поддержка туннелирования трафика через TURN-серверы на базе проекта [proxy-turn-vk-android](https://github.com/SpaceNeuroX/proxy-turn-vk-android).
- **Менеджер подписок**: Быстрое добавление и фоновое обновление списков серверов по ссылкам (`https://...`).
- **Умная ротация и Ping-тест**: Автоматическая проверка задержки и мгновенное переключение на рабочий и быстрый сервер.
- **Интерфейс и дизайн**: Поддержка адаптивных и монохромных иконок для Android 12+ (Material You), раздельные светлые и тёмные темы.
- **Канал обновлений (LTS)**: Встроенная система ручной проверки обновлений через GitHub Releases с автоматическим обходом блокировок через активное прокси.

---

## Правовая информация и дисклеймер

Whitegram является **неофициальным** клиентом, использующим [Telegram API](https://core.telegram.org/api) в полном соответствии с [Telegram API Terms of Service](https://core.telegram.org/api/terms).

*Telegram является зарегистрированной торговой маркой Telegram FZ-LLC. Проект Whitegram не связан, не одобрен и не спонсируется Telegram FZ-LLC.*

---

## Безопасность и приватность

- Все чаты и медиафайлы передаются через официальный протокол **MTProto 2.0**.
- Трафик туннелируется локально через внутренний контур (Xray / qWDTT) без доступа к незашифрованным данным.
- Приложение не собирает персональные данные, метрики или телеметрию.
- Исходный код полностью открыт под лицензией **GPL-2.0**.

---

## Использованные компоненты и благодарности

- [Telegram for Android](https://github.com/DrKLO/Telegram) — официальный клиент Telegram (GPL-2.0).
- [XTLS/Xray-core](https://github.com/XTLS/Xray-core) — ядро маршрутизации и обхода сетевых ограничений.
- [proxy-turn-vk-android](https://github.com/SpaceNeuroX/proxy-turn-vk-android) — реализация клиента и туннелирования qWDTT через TURN-серверы.

---

## Сборка из исходников

### Требования
- **JDK**: Java 17+ (Azul Zulu / OpenJDK)
- **Android SDK**: API 34+
- **Android NDK**: r21e / r23b
- **Gradle**: 8.x

### Команды для сборки
```bash
# Клонирование репозитория
git clone https://github.com/906009/whitegram.git
cd whitegram

# Сборка универсального APK (universal APK)
./gradlew assembleAfatRelease
```

Собранный APK-файл формируется в каталоге:  
`TMessagesProj_App/build/outputs/apk/afat/release/`

---

## Лицензия

Проект распространяется под лицензией **GNU General Public License v2.0 or later** (GPL-2.0).  
Подробности доступны в файле [LICENSE](LICENSE).

Copyright (C) 2013-2026 Telegram FZ-LLC  
Copyright (C) 2026 Whitegram Project
