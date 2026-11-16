# 🚗 CarLight Launcher

[![Android](https://img.shields.io/badge/Android-8.1%2B%20(API%2027)-3DDC84?logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-7F52FF?logo=kotlin&logoColor=white)](#)
[![Architecture](https://img.shields.io/badge/Arch-armeabi--v7a-blue)](#)
[![Footprint](https://img.shields.io/badge/RAM%20PSS-%E2%89%A4%2040%20MB-success)](#)
[![Developer](https://img.shields.io/badge/Developed%20by-KencrigDev-black?style=flat&logo=github)](https://github.com/kencrisg)

> **CarLight Launcher** es un launcher ultraligero y de alto rendimiento con interfaz estilo **Android Auto**, diseñado y optimizado específicamente para unidades principales y autorradios chinas basadas en plataformas **Topway TS7** (Android 8.1 Oreo, 1 GB de RAM, pantalla táctil panorámica 1024×600).

---

## 🌟 Características Principales

### 🗺️ Mapa en Vivo Integrado (Organic Maps)
- **Overlay inteligente con perforación táctil:** Utiliza una arquitectura de superposición del sistema (`TYPE_APPLICATION_OVERLAY`) que deja a **Organic Maps** activo en primer plano rindiendo a pantalla completa, mientras el panel de CarLight flota sobre él con un hueco transparente (`WallpaperHoleDrawable`) y regiones táctiles excluidas (`TouchRegion`).
- **Navegación fluida:** Gestos multitáctiles y arrastre directos en el mapa central sin interrupciones.
- **Auto-centrado y GPS Ready:** Detecta el estado del GPS antes de abrir el mapa para evitar arranques con pantalla descalibrada/mapamundi, asistiendo la pulsación de centrado mediante servicios de accesibilidad.

### 📻 Radio FM Nativa Topway
- **Control directo vía AIDL:** Comunicación directa con el centro de comandos de fábrica Topway (`com.tw.service.xt` / `ITWCommandAidl`).
- **Sincronización en tiempo real:** Lectura de frecuencia y dial activo mediante monitoreo de eventos y logs nativos del SoC Spreadtrum (`LogcatRadioWatcher`), ofreciendo respuesta instantánea sin interferir con el chip de hardware FM.
- **Acceso rápido:** Cambio de emisora, búsqueda y visualización de presintonías P1-P6 directamente en el panel.

### 🎵 Música Bluetooth (A2DP / AVRCP)
- **Control de reproducción:** Anterior, Reproducir/Pausa, Siguiente integrados mediante sesión AVRCP nativa (`MediaBrowser` / `MediaController`).
- **Metadatos en vivo:** Título de pista y artista obtenidos vía broadcasts propietarios de Topway (`com.android.getelementattrrsp`, `com.tw.btmusic.info`).
- **Detección de estado:** Indicador visual de conexión de teléfono inteligente y apertura de la app BT con un toque.

### ⚡ Rendimiento Extremo y Simulación de Vidrio (Glassmorphism)
- **Presupuesto estricto de memoria:** Consume **≤ 40 MB PSS** de RAM, permitiendo multitarea fluida en radios de solo 1 GB de RAM física.
- **Sin Compose ni AppCompat:** Construido con Vistas nativas puras de Android (`android.app.Activity` + `ViewBinding`), minimizando tiempos de inicio (cold boot < 1.5s) y reduciendo la carga en CPUs Quad-Core Cortex-A7.
- **Vidrio sintético de costo cero de GPU:** Genera un fondo pre-desenfocado a 1/8 de resolución (`RGB_565`) combinado con bordes iluminados y degradados tonales, logrando un acabado visual moderno sin saturar la GPU Mali-T820.

### 🚗 Trip Computer & Utilidades de Conducción
- **Velocímetro GPS animado:** Indicador visual en arco de 0 a 180 km/h (o mph), con histéresis y filtrado de ruido en parado (< 3 km/h).
- **Alerta de velocidad:** Notificación visual y auditiva cuando se supera el límite configurado.
- **Computadora de a bordo:** Medición de distancia recorrida, velocidad media, velocidad máxima y tiempo de viaje.
- **Mantenimiento y Combustible ("Mi Auto"):** Control de odómetro, registro de repostajes con cálculo de consumo real (km/l o km/gal), autonomía estimada y alertas de mantenimiento (cambio de aceite, filtros, neumáticos, frenos).

### 🌓 Modo Día / Noche y Control de Brillo
- Modos: Automático (por horario o aprendizaje de sensor de luces), Día y Noche.
- Ajuste y persistencia directa de brillo en `Settings.System.SCREEN_BRIGHTNESS`.
- Asistente de aprendizaje de señal de faros del auto mediante `TWUtil` (rango amplio de IDs MCU Topway).

### 🛡️ Tolerancia a Fallos y Convivencia con el Sistema
- **Integración con Marcha Atrás:** Detección instantánea de la cámara de retroceso (`ReverseActivity` y `onReverseStatus`) ocultando el panel para priorizar la maniobra.
- **Control de Volumen:** Efecto peek traslúcido y deshabilitación táctil temporal mientras el diálogo de volumen del MCU está activo.
- **Auto-recuperación (A11ySelfHeal):** Monitorea y restaura los servicios de accesibilidad si el administrador de tareas agresivo de Topway mata procesos en segundo plano.

---

## 📐 Vista del Panel Principal (1024×600)

```text
┌──────────────┬──────────────────────────────────────────┬──────────────┐
│   ( 48 )     │                                    ⛶  ⟳  │   📻 RADIO   │
│    km/h      │                                          │    101.5     │
├──────────────┤               MAPA EN VIVO               │   MHz · P4   │
│    12:45     │                                          │   ⏪  ◯  ⏩  │
│  jueves 12   │           (Organic Maps central          ├──────────────┤
├──────────────┤              a toda la altura,           │  ◉   ◉   ◉   │
│  ♪ BLUETOOTH │               100% táctil)               │ Maps Radio BT│
│   Canción    │                                          │  ◉   ◉   ◉   │
│  ⏮  (▶)  ⏭  │                                          │ Mús Conf Apps│
└──────────────┴──────────────────────────────────────────┴──────────────┘
    240 dp                Centro (~484×520 px)                 240 dp
```

---

## 🛠️ Tecnologías y Requisitos

| Componente | Requisito / Detalle |
|---|---|
| **Lenguaje** | Kotlin 2.1.20 |
| **Android SDK** | `minSdk 27` (Android 8.1 Oreo) / `targetSdk 27` |
| **Plataforma Hardware** | Topway TS7 (Allwinner / Spreadtrum SC7731E / Cortex-A7) |
| **Arquitectura NDK** | `armeabi-v7a` exclusivamente |
| **Compilación** | Gradle 9.3.1, Android Gradle Plugin 8.12.0, JDK 17 |
| **Optimizador** | R8 (Minificación y encogimiento de recursos activado) |

---

## 🚀 Compilación e Instalación

### 1. Compilar el APK

```bash
# Compilar versión Debug
./gradlew assembleDebug

# Compilar versión Release optimizada con R8 (firmada con debug key para fácil instalación)
./gradlew assembleRelease
# Salida: app/build/outputs/apk/release/app-release.apk
```

### 2. Instalar vía ADB (WiFi o USB)

Conéctate a la IP de la radio:
```bash
adb connect 192.168.110.217:5555
```

Instala el APK generado:
```bash
adb -s 192.168.110.217:5555 install -r app/build/outputs/apk/release/app-release.apk
```

### 3. Otorgar Permisos de Sistema

Para que el overlay sobre el mapa, la lectura de radio y los controles automáticos funcionen a la perfección, otorga los permisos necesarios mediante ADB:

```bash
# Permiso de superposición (ventana flotante)
adb shell appops set com.carlight.launcher SYSTEM_ALERT_WINDOW allow

# Servicio de accesibilidad (para supervisar la app en primer plano y auto-centrar)
adb shell settings put secure enabled_accessibility_services com.carlight.launcher/com.carlight.launcher.overlay.ForegroundWatcher
adb shell settings put secure accessibility_enabled 1

# Permiso de ubicación para velocímetro e instrumentación GPS
adb shell pm grant com.carlight.launcher android.permission.ACCESS_FINE_LOCATION

# Ajustes del sistema (control de brillo día/noche)
adb shell appops set com.carlight.launcher WRITE_SETTINGS allow

# Lectura de logs (para capturar frecuencias de radio FM en tiempo real)
adb shell pm grant com.carlight.launcher android.permission.READ_LOGS

# Listener de notificaciones (para recibir próximas maniobras de Organic Maps)
adb shell cmd notification allow_listener com.carlight.launcher/com.carlight.launcher.nav.NavNotificationListener
```

### 4. Establecer como Launcher Predeterminado

```bash
# Fijar CarLight Launcher como pantalla de inicio
adb shell cmd package set-home-activity com.carlight.launcher/.ui.HomeActivity

# (Opcional) Si deseas volver al launcher de fábrica en cualquier momento:
# adb shell cmd package set-home-activity com.dofun.variety/com.dofun.overseasvariety.Launcher
```

---

## 📁 Estructura del Código

```text
app/src/main/
├── aidl/com/tw/service/xt/aidl/   # Interfaces AIDL clonadas de Topway (ITWCommandAidl, callbacks)
├── java/com/carlight/launcher/
│   ├── apps/                      # Repositorio de aplicaciones, caché e IconPack dinámico
│   ├── calls/                     # Monitor de llamadas HFP y overlay para telefonía
│   ├── car/                       # Mantenimiento, odómetro y gestión de combustible (Mi Auto)
│   ├── drive/                     # TripComputer y cálculo de métricas de viaje
│   ├── gps/                       # Sincronización GPS y automatización de centrado en mapa
│   ├── media/                     # Clientes de audio Bluetooth A2DP/AVRCP
│   ├── nav/                       # Interacción con Organic Maps y listener de maniobras
│   ├── night/                     # Controlador día/noche y detector de señal de faros
│   ├── overlay/                   # Ventana flotante, TouchRegion, ForegroundWatcher y A11y
│   ├── prefs/                     # Preferencias y persistencia SharedPreferences
│   ├── radio/                     # Fuentes de radio FM Topway y visor de logcat
│   ├── speed/                     # Proveedor de velocidad por satélite
│   ├── topway/                    # Clientes de comandos Topway AIDL
│   └── ui/                        # HomeActivity, SettingsActivity, Dashboard y efectos visuales
└── res/                           # Diseños XML optimizados, paletas día/noche y drawables vectoriales
```

---

## 👨‍💻 Autor y Créditos

Proyecto implementado por:
- **KencrigDev** - *Ingeniería inversa, desarrollo del launcher y optimizaciones de bajo nivel para Topway TS7.*
- GitHub: [@kencrisg](https://github.com/kencrisg)

---

## 📄 Licencia

Este proyecto se distribuye bajo fines de desarrollo personal y optimización de dispositivos automotrices de recursos limitados. Consulta el repositorio para más detalles sobre términos y licencias.
