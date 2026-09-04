<div align="center">

  <img src="assets/ic_launcher.webp" width="240" height="240" alt="YumaPlayer Icon" style="border-radius: 28px; box-shadow: 0 8px 24px rgba(0,0,0,0.3);" />

  <h1>YumaPlayer (Yuma)</h1>

  <p align="center">
    <a href="README.md">
      <img src="https://img.shields.io/badge/🇺🇸%20English-6366f1?style=for-the-badge&labelColor=1e1e2e" alt="English" />
    </a>
    <a href="README_RU.md">
      <img src="https://img.shields.io/badge/🇷🇺%20Русский-6366f1?style=for-the-badge&labelColor=1e1e2e" alt="Russian" />
    </a>
  </p>


  <p align="center">
    <strong>Гибридный музыкальный плеер для Android с акцентом на удобство, дизайн и качественный звук.</strong>
    <br />
    <em>Spotify UI + Алгоритмы рекомендаций + База YouTube Music + Hi-Res Lossless (FLAC).</em>
  </p>

  <p align="center">
    <a href="#-галерея-интерфейса-showcase"><b>Скриншоты</b></a> •
    <a href="#-основные-возможности"><b>Возможности</b></a> •
    <a href="#-архитектура-и-документация"><b>Архитектура</b></a> •
    <a href="#-установка-и-загрузка"><b>Скачать</b></a> •
    <a href="#-поддержать-проект-support"><b>Донат</b></a>
  </p>

  <div>
    <img src="https://img.shields.io/github/v/release/MuwMx/YumaPlayer?style=for-the-badge&color=6366f1&labelColor=1e1e2e&logo=github" alt="Latest Release" />
    <img src="https://img.shields.io/github/stars/MuwMx/YumaPlayer?style=for-the-badge&color=6366f1&labelColor=1e1e2e&logo=github" alt="GitHub Stars" />
    <img src="https://img.shields.io/github/downloads/MuwMx/YumaPlayer/total?style=for-the-badge&color=6366f1&labelColor=1e1e2e&logo=github" alt="Downloads" />
    <img src="https://img.shields.io/badge/License-GPLv3-6366f1?style=for-the-badge&logo=gnu&logoColor=white&labelColor=1e1e2e" alt="License: GPLv3" />
    <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white&labelColor=1e1e2e" alt="Android 8.0+" />
    <a href="https://t.me/yumaplayer"><img src="https://img.shields.io/badge/Telegram-Community-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white&labelColor=1e1e2e" alt="Telegram" /></a>
  </div>

</div>

---

## 🌟 О проекте

**YumaPlayer** — это независимый открытый музыкальный плеер для Android, объединяющий каталоги и функции двух ведущих платформ со стримингом и воспроизведением локальных треков в Hi-Res качестве.

Проект вырос как глубокая переработка **ArchiveTune** (с наработками от **Metrolist** и **SimpMusic**), вдохновлен механикой очередей **PixelPlayer**, сетевым взаимодействием со Spotify из **Meld** и стримингом FLAC из **Stash**. На этом фундаменте создана модульная архитектура из 13 модулей, собственная дизайн-система **YDS 2.1** и плавная жестовая модель с аппаратным ускорением.

Никаких платных подписок, встроенной рекламы, аналитики и закрытых трекеров. Учетные данные шифруются локально на устройстве (AES-256-GCM via Google Tink), а исходный код полностью открыт под лицензией GPL-3.0.

---

## 📸 Галерея интерфейса (Showcase)

<div align="center">
  <table>
    <tr>
      <td width="33%" align="center">
        <img src="assets/screenshots/fullplayer.png" alt="Full Player" width="100%" style="border-radius: 16px;" /><br />
        <sub><b>Главный плеер (FLAC & Glass)</b></sub>
      </td>
      <td width="33%" align="center">
        <img src="assets/screenshots/lyrics_screen.png" alt="3-Tier Lyrics" width="100%" style="border-radius: 16px;" /><br />
        <sub><b>Синхронная 3-уровневая Лирика</b></sub>
      </td>
      <td width="33%" align="center">
        <img src="assets/screenshots/queue.png" alt="Queue Management" width="100%" style="border-radius: 16px;" /><br />
        <sub><b>Очередь (Dual-Sheet & Haptic)</b></sub>
      </td>
    </tr>
    <tr>
      <td width="33%" align="center">
        <img src="assets/screenshots/artist_screen.png" alt="Artist Header" width="100%" style="border-radius: 16px;" /><br />
        <sub><b>Иммерсивный постер артиста</b></sub>
      </td>
      <td width="33%" align="center">
        <img src="assets/screenshots/artist_screen2.png" alt="Morphing Collapse" width="100%" style="border-radius: 16px;" /><br />
        <sub><b>Telegram-морфинг в каплю</b></sub>
      </td>
      <td width="33%" align="center">
        <img src="assets/screenshots/main_screen.png" alt="Home Screen" width="100%" style="border-radius: 16px;" /><br />
        <sub><b>Главная (Quick Picks & YDS)</b></sub>
      </td>
    </tr>
    <tr>
      <td width="33%" align="center">
        <img src="assets/screenshots/spotify.png" alt="Spotify Screen" width="100%" style="border-radius: 16px;" /><br />
        <sub><b>Spotify Feed & Рекомендации</b></sub>
      </td>
      <td width="33%" align="center">
        <img src="assets/screenshots/spotify_radio.png" alt="Spotify Radio" width="100%" style="border-radius: 16px;" /><br />
        <sub><b>Spotify Radio & Подборки</b></sub>
      </td>
      <td width="33%" align="center">
        <img src="assets/screenshots/spotify_dailymix.png" alt="Spotify Daily" width="100%" style="border-radius: 16px;" /><br />
        <sub><b>Spotify Daily Mixes</b></sub>
      </td>
    </tr>
  </table>
</div>

---

## ✨ Основные возможности

### 🔄 Два режима работы (Spotify + YouTube Music)
* **Spotify Home:** Персонализированный фид рекомендаций, миксы дня (Daily Mixes), Discover Weekly, подборки любимых исполнителей и прямой импорт ваших плейлистов/лайков из Spotify.
* **YouTube Music Home:** Классический поиск по всей базе YT Music, чарты, тематические подборки, радиостанции и быстрое переключение каналов.

### 🎧 Аудиофильский звук, FLAC и Воспроизведение
* **Lossless FLAC:** Стриминг и загрузка треков в формате FLAC (через Qobuz / QBDLX бэкбон) с указанием личного токена или публичных резолверов.
* **Бесшовный фоллбэк:** При отсутствии Lossless-версии трек гладко воспроизводится из YouTube Music (Opus / AAC).
* **Локальная музыка:** Полноценный встроенный плеер для аудиофайлов на памяти телефона (FLAC, MP3, AAC, OGG, WAV).
* **Оффлайн и Кэш:** Автоматическое кэширование прослушанных треков из YouTube Music для автономного прослушивания без интернета *(кэширование FLAC в активной разработке)*.
* **Тонкая настройка звука:** Встроенный 5-полосный эквалайзер + поддержка системных аудиоэффектов, нормализация громкости (EBU R128), плавный кроссфейд, изменение темпа и тональности, таймер сна.

### 🎤 Продвинутая Лирика и AI-перевод
* **3-уровневый синхрон:** Одновременный показ оригинального текста (Кандзи/Хангыль), транслитерации (Ромадзи) и перевода на русский язык.
* **Караоке-скролл:** Точная синхронизация по времени через 7+ провайдеров (LRCLIB, Kugou, Paxsenix, BetterLyrics, SimpMusic, Unison, YouLyPlus).
* **AI-перевод на лету:** Мгновенный перевод текста песен прямо во время воспроизведения.
* **Карточки цитат:** Возможность сохранить фрагмент текста как стильную картинку для соцсетей.

### 🎨 Yuma Design System (YDS 2.1) и Плавность
* **Стеклянный интерфейс (Glass UI):** Полупрозрачные карточки (`GlassScaffold`, тонкие рамки 0.5dp, выверенная геометрия скруглений 22dp / 5dp).
* **Dual-Sheet жесты:** Независимые шторки плеера (свайп слева — Текст песни, свайп справа — Очередь) без конфликтов жестов и тряски списков.
* **Telegram Morphing Header:** Плавное сжатие постера артиста или альбома в аккуратную каплю в строке состояния при скролле без растягивания картинки.
* **Smart Freeze & Pre-warming:** Экраны открываются мгновенно, а невидимые анимации и фоновые процессы засыпают при сворачивании, сохраняя заряд аккумулятора и прохладу устройства.
* **Динамический Monet:** Адаптивная генерация темы и акцентов на основе палитры текущей обложки трека.

### 🌐 Интеграции и Удобство
* **Spotify Canvas & Apple Motion:** Зацикленные видео-фоны во время воспроизведения *(в разработке)*.
* **Распознавание музыки (Shazam):** Встроенный поиск играющего рядом трека по акустическому отпечатку *(в разработке)*.
* **Together (Совместное прослушивание):** Синхронное прослушивание музыки с друзьями через WebSockets.
* **Скробблинг:** Нативная поддержка Last.fm и ListenBrainz.
* **Discord Rich Presence:** Отображение играющего трека в статусе профиля Discord.
* **Android Auto & Виджеты:** Поддержка воспроизведения в автомобиле и виджеты на рабочем столе (Glance).

---

## 🌍 Локализация (Globalization)

YumaPlayer переведен на множество языков силами сообщества (Thanks Archivetune!).

<div align="center">
  <img src="https://img.shields.io/badge/Translations-In_Progress-6366f1?style=for-the-badge&logo=weblate&labelColor=1e1e2e" alt="Translation Status" />
</div>

> Если вы хотите помочь с переводом приложения на ваш родной язык или улучшить текущие строки — присоединяйтесь к обсуждению в нашем [Telegram-канале](https://t.me/yumaplayer).

---

## 🏛️ Архитектура и Документация

Проект разбит на **13 независимых Gradle-модулей** по канонам Clean Architecture и UDF (Unidirectional Data Flow):

```
┌─────────────────────────────────────────────────────────────┐
│                   :app (Composition Root)                   │
└──────────────────────────────┬──────────────────────────────┘
                               │
       ┌───────────────────────┼──────────────────────┐
       ▼                       ▼                      ▼
┌──────────────┐       ┌──────────────┐ ┌───────────────────────────┐
│  :feature:*  │       │  :service:*  │ │  :lyrics:*  │ :spotifycore│
│ (Compose UI) │       │  (Playback)  │ │  :canvas    │ :shazamkit  │
└──────┬───────┘       └──────┬───────┘ └─────────────┬─────────────┘
       │                      │                       │
       └──────────────────────┼───────────────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                 :core:* (Domain, Data, Model)               │
└──────────────────────────────┬──────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 Low-Level Media Extraction                  │
└─────────────────────────────────────────────────────────────┘
```

📖 **Подробные гайды для разработчиков:**
* [Обзор Архитектуры (Architecture Overview)](docs/architecture/ARCHITECTURE.md)
* [Каталог модулей и граф зависимостей (Modules Directory)](docs/architecture/MODULES.md)
* [Архитектурные решения (ADR Records)](docs/architecture/DECISIONS.md)
* [Спецификация дизайн-системы (YDS 2.1 Guide)](docs/design/YDS.md) | [На русском](docs/design/YDS_ru.md)
* [Стандарты кода и правила (Yuma Rules)](docs/development/YUMA_RULES.md)

---

## 📥 Установка и Загрузка

<div align="center">
  <table>
    <thead>
      <tr>
        <th align="center">GitHub Releases</th>
        <th align="center">Telegram Channel</th>
        <th align="center">Obtainium</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td align="center">
          <a href="https://github.com/MuwMx/YumaPlayer/releases/latest">
            <img src="assets/badge_github.png" height="50" alt="Download on GitHub" />
          </a>
        </td>
        <td align="center">
          <a href="https://t.me/yumaplayer">
            <img src="https://img.shields.io/badge/Telegram-Channel-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" height="40" alt="Get in Telegram" />
          </a>
        </td>
        <td align="center">
          <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/MuwMx/YumaPlayer">
            <img src="https://img.shields.io/badge/Obtainium-Auto_Updates-purple?style=for-the-badge" height="40" alt="Add to Obtainium" />
          </a>
        </td>
      </tr>
    </tbody>
  </table>
</div>

### Сборка из исходников
```bash
git clone https://github.com/MuwMx/YumaPlayer.git
cd YumaPlayer
./gradlew assembleRelease
# Готовый APK появится в: app/build/outputs/apk/release/
```
*Требования:* Android Studio Ladybug (2024.2.1+), JDK 21, Android SDK 35+.

---

## ⚙️ Подключение аккаунтов

YumaPlayer полностью функционален без логина. Авторизация нужна только для подтягивания ваших личных плейлистов и рекомендаций:

<details>
<summary><b>🎵 Подключение Spotify</b></summary>

1. Откройте **Настройки** → **Аккаунт** → **Spotify** → Нажмите **Войти**.
2. Авторизуйтесь под своими учетными данными на открывшейся веб-странице.
3. Сессия будет автоматически сохранена и зашифрована на устройстве.
</details>

<details>
<summary><b>📺 Подключение YouTube Music</b></summary>

Доступно два удобных способа входа:
* **Способ 1 (Google Аккаунт):** Откройте **Настройки** → **Аккаунт** → **YouTube** → войдите через встроенное безопасное окно авторизации.
* **Способ 2 (Ввод токена / Cookie):** Если у вас включена двухфакторная аутентификация или не открывается браузер — перейдите в **Настройки** → **Аккаунт** → **Редактор токенов** и вставьте актуальные cookie сессии вручную.
</details>

---

## ☕ Поддержать проект (Support)

YumaPlayer распространяется бесплатно, не содержит рекламы и не продает подписки. Если плеер вам понравился и вы хотите поддержать автора:

<div align="center">
  <a href="https://ko-fi.com/muwmix">
    <img src="assets/buymeacoffee.png" height="45" alt="Support on Ko-fi" />
  </a>
  <br /><br />
  <p>
    <b>Solana (SOL, USDT, USDC):</b><br />
    <code>DT3ckdbNuiQMR1mrCpBXCMrhLB19GckVv3YfxsLLiF8z</code>
  </p>
  <p>
    <b>PayPal / Email Transfer:</b><br />
    <code>muwmix.coffee@gmail.com</code>
  </p>
</div>

---

## 🤝 Благодарности (Open-Source Credits)

Проект создан благодаря наработкам и свободному коду сообщества:

* **[ArchiveTune](https://github.com/rukamori/ArchiveTune)** — за отличный фундамент, плеерные наработки и базу настроек.
* **[Meld](https://github.com/FrancescoGrazioso/Meld)** — за концепцию и реализацию гибридного взаимодействия со Spotify.
* **[Stash](https://github.com/rawnaldclark/Stash)** — за вдохновение в организации Lossless FLAC стриминга и загрузок.
* **[Metrolist](https://github.com/mostafaalagamy/Metrolist)** & **[SimpMusic](https://github.com/maxrave-dev/SimpMusic)** — за первоначальные парсеры текстов и интерфейсные идеи.
* **[PixelPlayer](https://github.com/theovilardo/PixelPlayer)** — за идеи кинематики жестов и организацию очереди.
* **[QBDLX](https://github.com/ImAiiR/QobuzDownloaderX)** — за инфраструктуру прямого Lossless аудио.
* **[LRCLIB](https://lrclib.net/)** & **[BetterLyrics](https://better-lyrics.boidu.dev/)** — за открытую базу синхронизированной лирики.

---

## ⚖️ Лицензия и Отказ от ответственности

### Лицензия
YumaPlayer распространяется под свободной лицензией **GNU General Public License v3.0 (GPLv3)**. 
Вы можете свободно использовать, изучать, модифицировать и распространять код при условии сохранения авторских прав и открытия производного кода под аналогичной лицензией GPL-3.0.

### Отказ от ответственности (Disclaimer)
YumaPlayer — это независимый неофициальный сторонний клиент. Проект **не связан с Google LLC, YouTube Music или Spotify AB** и не одобрен ими. Приложение создано исключительно для личного использования и управления собственной медиатекой.

---

<p align="center">
  Разработано с ❤️ и вниманием к каждому кадру. Enjoy the music! 🎵
</p>

