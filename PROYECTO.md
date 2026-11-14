# CarLight Launcher

Launcher liviano, al estilo de Android Auto, para la radio **Topway TS7** (Android 8.1, 1 GB RAM, 1024×600).
Reemplaza al launcher de fábrica (`com.dofun.variety`) por una pantalla principal con tarjetas de **Radio FM**, **Música Bluetooth** y **Navegación (Organic Maps)**, más un dock de apps.

> Ficha técnica completa del equipo: [context_radio.md](context_radio.md).
> Volcados crudos de la radio (APKs, logcat, dumpsys): `radio_dump/` (no se versiona).

---

## 0. Compilar e instalar

Requisitos: JDK 17, Android SDK (platform 36). Versiones: Gradle 9.3.1 (wrapper), AGP 8.12.0, Kotlin 2.1.20.

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # app/build/outputs/apk/release/app-release.apk (R8, ~56 KB, firmado con clave debug)

# Instalar en la radio (si hay varios dispositivos, usar -s)
adb -s 192.168.110.217:5555 install -r app/build/outputs/apk/release/app-release.apk

# Abrirlo sin hacerlo predeterminado (para probar)
adb -s 192.168.110.217:5555 shell am start -n com.carlight.launcher/.ui.HomeActivity

# Hacerlo launcher predeterminado (luego pulsar Home)
adb -s 192.168.110.217:5555 shell cmd package set-home-activity com.carlight.launcher/.ui.HomeActivity

# Volver al launcher de fábrica
adb -s 192.168.110.217:5555 shell cmd package set-home-activity com.dofun.variety/com.dofun.overseasvariety.Launcher

# Permisos del mapa en vivo (panel flotante + detección de app al frente)
adb -s 192.168.110.217:5555 shell appops set com.carlight.launcher SYSTEM_ALERT_WINDOW allow
adb -s 192.168.110.217:5555 shell settings put secure enabled_accessibility_services com.carlight.launcher/com.carlight.launcher.overlay.ForegroundWatcher
adb -s 192.168.110.217:5555 shell settings put secure accessibility_enabled 1
adb -s 192.168.110.217:5555 shell pm grant com.carlight.launcher android.permission.ACCESS_FINE_LOCATION
# Próxima maniobra (lee la notificación de Organic Maps) y nombre del contacto en llamadas
adb -s 192.168.110.217:5555 shell cmd notification allow_listener com.carlight.launcher/com.carlight.launcher.nav.NavNotificationListener
adb -s 192.168.110.217:5555 shell pm grant com.carlight.launcher android.permission.READ_CONTACTS
# Brillo de día/noche
adb -s 192.168.110.217:5555 shell appops set com.carlight.launcher WRITE_SETTINGS allow
# Frecuencia de la radio (la app de fábrica solo la escribe en el log)
adb -s 192.168.110.217:5555 shell pm grant com.carlight.launcher android.permission.READ_LOGS

# Revertir esos permisos (el estado previo era: sin servicios de accesibilidad, accessibility_enabled=0)
adb -s 192.168.110.217:5555 shell appops set com.carlight.launcher SYSTEM_ALERT_WINDOW default
adb -s 192.168.110.217:5555 shell settings delete secure enabled_accessibility_services
adb -s 192.168.110.217:5555 shell settings put secure accessibility_enabled 0
adb -s 192.168.110.217:5555 shell pm revoke com.carlight.launcher android.permission.READ_LOGS
```

**Emulador de pruebas:** AVD `CarLight_TS7` (1024×600, 160 dpi, 1 GB RAM, API 30, porque no hay imagen API 27 instalada). En el emulador no existen `com.tw.radio` ni `com.tw.bt`, así que las tarjetas usan datos **DEMO**.

---

## 1. Objetivos y restricciones

| Restricción | Valor | Consecuencia en el diseño |
|---|---|---|
| CPU | Cortex-A7 ×4 @ 1.3 GHz, 32 bits | Solo `armeabi-v7a`; nada de trabajo pesado en el hilo UI |
| RAM | 1 GB (+800 MB ZRAM) | **Presupuesto del launcher: ≤ 40 MB PSS** |
| GPU | Mali-T820 (`ro.opengles.version=196610` → GLES 3.2) | Sin blur en tiempo real, sin shaders ni video de fondo |
| Pantalla | 1024×600, 160 dpi (mdpi → 1 dp = 1 px) | Diseño al píxel, solo horizontal |
| Android | 8.1 (API 27) | `minSdk 27`, `targetSdk 27` |
| Google | Sin GMS | Nada de Firebase, Play Services, Google Maps |
| Contexto | Uso en el auto | Botones grandes (≥ 64 dp), alto contraste, arranque rápido |

---

## 2. Tecnología elegida

| Área | Elección | Motivo |
|---|---|---|
| Lenguaje | Kotlin | Nativo y sin runtime extra |
| UI | Vistas XML + ViewBinding, **sin Compose ni AppCompat** | Menor RAM y arranque más rápido en Cortex-A7. La Activity extiende `android.app.Activity` |
| Layout | ConstraintLayout / FrameLayout | Es la única dependencia de UI |
| Integración Topway | AIDL propio (copia de la interfaz de `com.tw.service.xt`) + reflexión para `android.tw.john.TWUtil` | Ver §4 |
| Integración BT | `MediaBrowser` / `MediaController` del framework | Es API estándar (AVRCP) |
| Build | Gradle (Kotlin DSL), R8 con minify y shrinkResources, `abiFilters "armeabi-v7a"` | APK chico |
| Persistencia | `SharedPreferences` | Dock, destinos y última emisora. No hace falta una base de datos |
| Prueba | Emulador AVD API 27, 1024×600 mdpi + la radio real por ADB WiFi | |

**Efecto "vidrio":** se simula con un fondo de pantalla **ya desenfocado** (PNG redimensionado a 1024×600, `RGB_565`) y tarjetas con degradados semitransparentes y esquinas redondeadas. Cero costo de GPU.

---

## 3. Diseño de la pantalla principal (1024×600)

Inspirado en Android Auto: mapa protagonista, tarjetas de vidrio con esquinas de 24 dp, etiqueta de origen en mayúsculas (BLUETOOTH, RADIO FM), botón ▶ grande relleno con el acento, botones tonales y botones redondos flotando sobre el mapa.

```
┌─────────┬──────────────────────────┬─────────┐
│  ( 48 ) │                     ⛶    │ 📻 RADIO│
│  km/h   │                     ⟳    │ 101.5   │
├─────────┤                          │ MHz · P4│
│  20:41  │      MAPA EN VIVO        │ ⏪◯◯⏩ │
│ jue 12  │   (toda la altura,       ├─────────┤
├─────────┤    táctil)    ▲          │ ◉  ◉  ◉ │
│ ♪ BLUET.│                          │ Maps Radio BT
│ Canción │                          │ ◉  ◉  ◉ │
│ ⏮ (▶) ⏭ │                          │ Mús Conf Apps
└─────────┴──────────────────────────┴─────────┘
  240 dp     centro (≈484×520, x=512)    240 dp
```

- **Mapa al centro y a toda la altura.** Organic Maps centra tu posición en el centro de la pantalla; con columnas laterales iguales (240 dp), la flecha queda centrada. El hueco mide unos 484×520 px, un 60 % más que el diseño anterior. ⛶ (pantalla completa) y ⟳ (recargar) flotan sobre el mapa y son "islas" táctiles dentro del hueco.
- **Izquierda:** km/h, hora y Bluetooth. **Derecha:** radio y cuadrícula de apps 3×2 (5 ranuras configurables con pulsación larga, más "Apps").
- **Tarjetas BT y radio: un toque en cualquier parte abre la app**, para poder usarlas conduciendo. La única excepción es la fila de controles, que consume los toques entre botones.
- **Velocímetro compacto:** usa el GPS estándar (`LocationManager`, 1 Hz, solo mientras el panel está visible). Arco de 240°, 0–180 km/h, animado. Por debajo de 3 km/h marca 0 para filtrar el ruido estando detenido.
- **Tema día/noche:** paletas tomadas del tema de fábrica, en `values/` (día) y `values-night/` (noche). El botón ☀/☾/A está en la cabecera del cajón de apps y alterna entre Automático (noche de 18:30 a 06:30), Día y Noche.

### 3.0 Ajustes

Se abren con el ⚙ de la tarjeta de la hora (o pulsación larga sobre ella) y con el ⚙ del cajón de apps. Es una pantalla con el mismo fondo y vidrio que el panel: categorías a la izquierda y controles de 52 dp o más a la derecha ([SettingsActivity](app/src/main/java/com/carlight/launcher/ui/SettingsActivity.kt)).

| Categoría | Opciones |
|---|---|
| Apariencia | Tema Automático / Claro / Oscuro. Horario del automático (día y noche, pasos de 30 min). **Color de fondo**: Violeta, Océano, Grafito, Bosque o Atardecer, cada uno con versión de día y de noche ([WallpaperRenderer.STYLES](app/src/main/java/com/carlight/launcher/ui/glass/WallpaperRenderer.kt)). Color de acento: Violeta, Azul, Cian, Verde, Naranja, Rosa ([Accent](app/src/main/java/com/carlight/launcher/ui/Accent.kt)). Fondo: Ciudad o Solo luces |
| Unidades | km/h o mph (convierte la velocidad y ajusta la escala). Escala del velocímetro. Hora en 24 h o 12 h |
| Panel | **Estilo de iconos**: Elegantes (glifo blanco sobre degradado propio de cada app), Monocromo (glifo con el color de acento sobre vidrio) u Originales ([IconPack](app/src/main/java/com/carlight/launcher/apps/IconPack.kt)). Las apps sin glifo propio llevan su icono original dentro del mismo marco. Restablecer los accesos de la cuadrícula |
| Sistema | Estado de los permisos (panel flotante, accesibilidad, ubicación, logs) y versión |

Cada cambio se guarda al instante en [Prefs](app/src/main/java/com/carlight/launcher/prefs/Prefs.kt). Al volver, el panel se reconstruye si cambió `appearanceSignature()` o el tema efectivo. Mientras Ajustes está abierto, la capa flotante se oculta.

### 3.0.1 Modo noche y brillo (Ajustes → Pantalla)
- **El modo noche se activa por** *Horario* (día y noche configurables) o por *Luces del auto*. Lo usan el tema automático y el brillo ([NightController](app/src/main/java/com/carlight/launcher/night/NightController.kt)).
- **Brillo:** de día y de noche en %. Se escribe en `Settings.System.SCREEN_BRIGHTNESS`, igual que el servicio de Topway (`saveBrightness`). Requiere `WRITE_SETTINGS` (appops).
- **Luces del auto:** el mensaje del MCU que indica los faros no está documentado. [HeadlightDetector](app/src/main/java/com/carlight/launcher/night/HeadlightDetector.kt) escucha `TWUtil` en un rango amplio de ids (0x0100–0x03FF, 0x0500–0x05FF, 0xFF00–0xFF1F) y **aprende** la señal con un asistente de dos pasos (luces apagadas → encendidas). Guarda id, campo y valor en `headlight_signal`. Si nunca llega señal, usa el horario como respaldo.

### 3.0.2 Conducción (Ajustes → Conducción)
- **Computadora de viaje** ([TripComputer](app/src/main/java/com/carlight/launcher/drive/TripComputer.kt)): distancia, tiempo en marcha, velocidad media y máxima. GPS a 1 Hz mientras vive el proceso. Viaje nuevo tras 30 min detenido o con pulsación larga en la tarjeta del velocímetro. Un toque en esa tarjeta alterna entre el velocímetro y el viaje.
- **Alerta de velocidad:** límite configurable. Al superarlo, el arco se pone rojo y suena un aviso (opcional), con histéresis de 3 km/h.
- **Llamadas sobre el mapa** ([CallMonitor](app/src/main/java/com/carlight/launcher/calls/CallMonitor.kt), [CallOverlay](app/src/main/java/com/carlight/launcher/calls/CallOverlay.kt)): broadcast HFP de AOSP `AG_CALL_CHANGED` (estado y número). Contestar y colgar con `btAccept`/`btHangUP` del centro de comandos. Si la app BT de fábrica toma la pantalla durante la llamada, se vuelve al mapa. ⏳ Sin probar con una llamada real.
- **Próxima maniobra** ([NavNotificationListener](app/src/main/java/com/carlight/launcher/nav/NavNotificationListener.kt)): lee la notificación de navegación de Organic Maps (título = distancia, texto = calle, icono grande = flecha) y la muestra en un banner verde sobre el mapa. ⏳ Sin probar con una ruta real.

### 3.0.3 Mi auto (mantenimiento y gasolina)
El auto (Optra) no envía datos del motor a la radio, así que todo sale del GPS y de lo que registra el usuario ([CarData](app/src/main/java/com/carlight/launcher/car/CarData.kt)). Es la misma `SettingsActivity` en modo auto, abierta por el `activity-alias` `.car.CarActivity`, que aparece como app "Mi auto" en el cajón y se puede poner en la cuadrícula.
- **Odómetro:** el usuario ingresa una vez el km del tablero; [TripComputer](app/src/main/java/com/carlight/launcher/drive/TripComputer.kt) suma los metros del GPS. Se corrige al registrar una carga con otro km.
- **Mantenimiento:** aceite (5.000 km / 6 meses), filtro de aire, rotación de llantas, frenos y matrícula. Lo que llegue primero entre km y meses; intervalos editables; botón "Hecho". El más urgente aparece como aviso en la tarjeta de la hora (ámbar = se acerca, rojo = vencido) y al tocarlo abre Mi auto.
- **Gasolina:** registro de cargas (galones, total, km, tanque lleno). Consumo real con el método de tanque lleno a tanque lleno, costo por km, gasto del mes y autonomía estimada (capacidad del tanque × km/gal − km desde la última carga llena).

### 3.1 Mapa en vivo: cómo funciona

Android 8.1 no permite incrustar la ventana de otra app, y **Organic Maps deja de dibujar cuando no está al frente** (lo comprobamos: los gestos llegan, pero la imagen se congela). Por eso se invierten las capas:

1. **Organic Maps queda en primer plano** (`RESUMED`, dibujando) a pantalla completa.
2. **El panel de CarLight es una capa flotante** (`TYPE_APPLICATION_OVERLAY`, [DashboardOverlay](app/src/main/java/com/carlight/launcher/overlay/DashboardOverlay.kt)) encima, con un **hueco transparente** sobre la tarjeta del mapa ([WallpaperHoleDrawable](app/src/main/java/com/carlight/launcher/ui/glass/WallpaperHoleDrawable.kt)).
3. **La región táctil de la capa excluye el hueco** ([TouchRegion](app/src/main/java/com/carlight/launcher/overlay/TouchRegion.kt), API oculta por reflexión + `FLAG_NOT_TOUCH_MODAL`): los gestos del hueco van a Organic Maps.
4. **[ForegroundWatcher](app/src/main/java/com/carlight/launcher/overlay/ForegroundWatcher.kt)** (servicio de accesibilidad que solo escucha `TYPE_WINDOW_STATE_CHANGED`) informa qué Activity está al frente. [DashboardController](app/src/main/java/com/carlight/launcher/overlay/DashboardController.kt) muestra el panel si está Organic Maps o HomeActivity, y lo **oculta** con cualquier otra app (radio, BT, llamada, **cámara de retroceso**).
5. **[HomeActivity](app/src/main/java/com/carlight/launcher/ui/HomeActivity.kt)** es solo el punto de entrada: al pulsar Home trae Organic Maps al frente y muestra el panel. Sin permiso de superposición o sin Organic Maps, muestra el panel ella misma (sin mapa en vivo).
6. **⛶** en la cabecera: oculta el panel y deja Organic Maps a pantalla completa (búsqueda, rutas). **Home** vuelve al panel.

Medido en la radio: CarLight 15 MB PSS y Organic Maps 130 MB, con 340 MB de RAM libres.

> Se descartó el modo *freeform* (ventanas libres): exige cambiar un ajuste del sistema y reiniciar, añade una barra de título a la ventana y, al tocar el launcher, Android reordena las tareas y Organic Maps se pausa igual.
- **Vidrio:** `WallpaperRenderer` dibuja el fondo **una vez** (gradiente, orbes de luz y skyline; neón de noche). `Blur` desenfoca una copia a 1/8 (128×75, unos 38 KB). `GlassDrawable` pinta en cada tarjeta la porción desenfocada que queda detrás, más tinte, brillo y borde. Es vidrio real sin blur en tiempo real. Medido en el emulador: unos 37 MB PSS y 3 ms de GPU por cuadro.

- **Tarjeta BT:** muestra título, artista y estado; controles anterior / play-pausa / siguiente. Al tocar el área de texto se abre `com.tw.bt`.
- **Tarjeta FM:** muestra la frecuencia actual y 6 presintonías; controles anterior / siguiente y búsqueda. Al tocarla se abre `com.tw.radio`.
- **Dock:** 5 accesos configurables (pulsación larga para cambiar) + "Apps", que abre un cajón con todas las apps.
- **Sincronizar con Topway (pendiente):** la radio expone `persist.tw.theme.mode` (`night`), que se podría leer para seguir el tema del sistema.

---

## 4. Hallazgos de la ingeniería inversa (12-nov-2026)

### 4.1 Launcher y sistema
- Hoy resuelven `HOME` dos actividades: `com.dofun.variety/com.dofun.overseasvariety.Launcher` (de fábrica) y `com.android.settings.FallbackHome`.
- `persist.nwd.launcher.default = com.dofun.variety`: el firmware fija el launcher por defecto. **Riesgo:** puede volver a imponer Variety al arrancar. Plan: elegir nuestro launcher como predeterminado y, si el firmware lo pisa, desactivar Variety (`pm disable-user --user 0 com.dofun.variety`). Hay que probarlo en la fase 1.
- `com.dofun.variety` está empaquetado con un protector (`com.stub.StubApp`, 360 Jiagu), así que no se puede analizar ni hace falta.
- `persist.tw.dvr = com.google.android.apps.maps`: es la app de navegación que tiene configurada el sistema de Topway. Si se cambia a Organic Maps, probablemente el botón NAV del volante abra Organic Maps.
- Organic Maps está instalado como **`app.organicmaps.web`** (versión descargada de la web, no de F-Droid ni Play).

### 4.2 Arquitectura de audio y radio de Topway
- Todas las apps de Topway (`com.tw.radio`, `com.tw.bt`, `com.tw.service`, `com.tw.core`) se ejecutan como **`android.uid.system`** (firmadas con la clave de plataforma).
- `com.tw.service` es quien **pide el foco de audio** por cualquier fuente (radio, BT, etc.).
- **El sintonizador FM es el chip interno del SoC Spreadtrum**, no el MCU. Lo maneja `com.tw.radio` por JNI (`com.android.fmradio.FmNative`, logs `FMLIB_*`, ruta de audio `digital-fm`). **Nuestra app no debe tocar el sintonizador directamente**, porque entraría en conflicto con `com.tw.radio`.
- Las frecuencias se representan como entero ×10: `1015` = 101.5 MHz.
- `com.tw.radio` habla con el MCU mediante la API oculta del framework **`android.tw.john.TWUtil`**:
  - `TWUtil.open(short[] ids)`, `addHandler(tag, Handler)`, `write(id, arg1[, arg2[, obj]])`.
  - IDs que abre la radio: `0x0109, 0x0112, 0x0301, 0x0302, 0x0201, 0x9E0A`.
  - En el logcat, al cambiar de emisora llegan `0x0301`, `0x0201 (arg1=1)` y `0x9E0A (arg1=n.º de presintonía)`, y la radio sintoniza `station = 1015`.
  - `onResume` escribe `0x0201, 0x0112, 0x0109, 0x0301` con arg `0xFF`, lo que parece ser "pedir estado actual".
- La radio guarda su estado en archivos: **`/data/tw/radio_freq`**, **`/data/tw/radio_freqs`** y **`/data/tw/radio_name_*`**. Falta comprobar si son legibles para una app normal.

### 4.3 El centro de comandos `com.tw.service.xt` (la pieza clave)
`com.tw.radio` y `com.tw.bt` se conectan a un servicio AIDL:

- Paquete: `com.tw.service.xt`
- Servicio: `com.tw.service.xt.CommandService`, acción de bind `com.tw.service.xt.CommandService.Bind`
- Interfaz: `com.tw.service.xt.aidl.ITWCommandAidl`

Funciona como un **centro de despacho**. Las apps de radio, BT y música registran un *callback* (`IRadioCallBack`, `IBTCallBack`, `IMusicCallBack`). Cuando alguien llama `radioNext()` en el centro, este se lo reenvía a la app de radio. **Si el servicio está exportado, nuestro launcher puede controlar la radio y el BT sin permisos especiales.**

Interfaz reconstruida (el orden define el código de transacción, verificado en el bytecode):

```aidl
// com/tw/service/xt/aidl/ITWCommandAidl.aidl
interface ITWCommandAidl {
    void registerTWCommandCallback(ITWCommandCallbackAidl cb);   // 1
    void unRegisterTWCommandCallback(ITWCommandCallbackAidl cb); // 2
    void registerRadioCallBack(IRadioCallBack cb);               // 3
    void unRegisterRadioCallBack(IRadioCallBack cb);             // 4
    void registerMusicCallBack(IMusicCallBack cb);               // 5
    void unRegisterMusicCallBack(IMusicCallBack cb);             // 6
    void registerVideoCallBack(IVideoCallBack cb);               // 7
    void unRegisterVideoCallBack(IVideoCallBack cb);             // 8
    void registerBTCallBack(IBTCallBack cb);                     // 9
    void unRegisterBTCallBack(IBTCallBack cb);                   // 10
    void radioNext();                                            // 11
    void radioPre();                                             // 12
    void radioSearchNext();                                      // 13
    void radioSearchPre();                                       // 14
    void radioSetChannel(int ch);                                // 15
    void radioOpenChannel(String ch);                            // 16
    void radioBand(int band);                                    // 17
    void radioBandChange();                                      // 18
    void btCall(String number);                                  // 19
    void btAccept();                                             // 20
    void btHangUP();                                             // 21
    void btOpenMusic();                                          // 22
    void btPlayMusic();                                          // 23
    void btPre();                                                // 24
    void btNext();                                               // 25
    void btGetPhoneStatus();                                     // 26
    void btSetPhoneStatus(int s);                                // 27
    void btGetConnectedStatus();                                 // 28
    void btSetConnectedStatus(int s);                            // 29
    void btSetCallStatus(int s, String a, String b);             // 30
    void musicNext();                                            // 31
    void musicPre();                                             // 32
    void musicPlay();                                            // 33
    void musicPause();                                           // 34
    void musicMode(int m);                                       // 35
    void videoNext();                                            // 36
    void videoPre();                                             // 37
    void videoPlay();                                            // 38
    void videoPause();                                           // 39
    void volumeUp();                                             // 40
    void volumeDown();                                           // 41
    void volumeMute();                                           // 42
    void volumeMax();                                            // 43
    void volumeMin();                                            // 44
    void volumeValue(int v);                                     // 45
    void getVolume();                                            // 46
    void getVolumeStatus();                                      // 47
    void goHome();                                               // 48
    void back();                                                 // 49
    void killProject(String pkg);                                // 50
    void OpenProject(String pkg);                                // 51
    void getReverseStatus();                                     // 52
    void getSleepStatus();                                       // 53
    void setVolumeMute(boolean m);                               // 54
    void mediaNext();                                            // 55
    void mediaPre();                                             // 56
    void mediaPlay();                                            // 57
    void mediaPause();                                           // 58
    void lightUp();                                              // 59
    void lightDown();                                            // 60
    void lightMax();                                             // 61
    void lightMin();                                             // 62
    void lightValue(int v);                                      // 63
    void setWifi(boolean on);                                    // 64
    void closeScreen();                                          // 65
    void openScreen();                                           // 66
    void extendedInterface(in Bundle b);                         // 67
}

// Callback de estado que el centro envía a quien se registra
interface ITWCommandCallbackAidl {
    void onSystemVolume(int v);                                  // 1
    void onVolumeStatus(int s);                                  // 2
    void onBtPhoneStatus(int s);                                 // 3
    void onBtCallStatus(int s, String a, String b);              // 4
    void onBtConnectedStatus(int s);                             // 5
    void onReverseStatus(int s);                                 // 6
    void onSleepStatus(int s);                                   // 7
    void onExtendedInterface(in Bundle b);                       // 8
}

// Los implementan las apps de fábrica (las anoto como referencia)
interface IRadioCallBack { radioNext 1; radioPre 2; radioSearchNext 3; radioSearchPre 4;
                           radioSetChannel(String) 5; radioBand(int) 6; radioBandChange 7; extendedInterface(Bundle) 8 }
interface IBTCallBack    { btCall(String) 1; btAccept 2; btHangUP 3; btOpenMusic 4; btPlayMusic 5;
                           btPre 6; btNext 7; btGetPhoneStatus 8; btGetConnectedStatus 9; extendedInterface(Bundle) 10 }
interface IMusicCallBack { musicNext 1; musicPre 2; musicPlay 3; musicPause 4; musicMode(int) 5; extendedInterface(Bundle) 6 }
```

> Las interfaces de callback (`IRadioCallBack`, etc.) son las que implementan las apps de fábrica. **No las registramos nosotros**, porque eso le quitaría el control a la radio original.

### 4.3.1 Verificado con el dump del 12-nov (2.ª ronda)
| Pregunta | Resultado |
|---|---|
| `com.tw.service.xt/.CommandService` | ✅ `exported=true`, **sin permiso**, proceso `:remote`. Verificado en el manifest del APK |
| `/dev/tw` (TWUtil) | `crw-rw-rw-`, SELinux `u:object_r:tw_device:s0`. Los permisos Unix dejan pasar, pero **SELinux puede bloquear** a una app normal. Se prueba en la fase 4 |
| `/data/tw/radio_freq`, `radio_freqs` | ❌ `-rw------- system`: no se pueden leer |
| `/data/tw/radio_freqlist`, `music`, `navi_name` | ✅ `-rw-rw-rw-`: se pueden leer (lista de emisoras, música, app de navegación de Topway) |
| Sesión BT (`A2dpMediaBrowserService`) con música sonando | ⚠️ `state=0`, `active=false`, sin metadatos. AVRCP no reporta título; hay que investigarlo en la fase 3 |
| Pantalla útil | 1024×**540** dp (60 px de barras del sistema) |

> Nota: los archivos `bt_and_xt_services_analysis.txt` y `topway_aidl_api_full.txt` del dump incluyen métodos que **no existen** en el bytecode (`sendRadioFunction`, `sendKeyCode`, etc.). La fuente fiable es la interfaz de §4.3, extraída del dex.

### 4.3.2 Fuente de audio (radio ↔ BT)
Las apps de fábrica cambian la fuente del MCU al pasar al frente (`onResume`), enviando por `TWUtil` (`/dev/tw`):
- `write(0x9E00, fuente)`: *requestService*. Al salir envían `fuente | 0x80`.
- `write(0x9E11, 0xC0, fuente)`: *requestSource*.
- Fuentes: **radio = 10**, **música BT = 8**.

Si se controla desde el panel sin enviar esto, **suenan las dos fuentes**. [TwAudioSource](app/src/main/java/com/carlight/launcher/topway/TwAudioSource.kt) envía lo mismo por reflexión sobre `android.tw.john.TWUtil`. SELinux registra `avc: denied { write } ... untrusted_app` sobre `/dev/tw`, pero **está en modo Permissive**, así que se permite. Verificado: al pulsar ▶ de BT, la radio recibe la fuente 8 y ejecuta `FMR_set_mute [mute=1]`. Además, los controles de radio pausan el teléfono si está sonando.

> **No cachear la fuente.** El cambio se envía en cada toque, porque la fuente también cambia por fuera del panel (app BT, teléfono, volante). La primera versión lo cacheaba y, con el caché desactualizado, sonaban las dos (la app de radio se reactiva sola al sintonizar). Verificado en ambos sentidos el 12-nov-2026: radio → BT pausa por AVRCP y corta la salida (`playa2dpsinkcmd=0`); BT → la radio se silencia (`FMR_set_mute [mute=1]`).

> ⚠️ Si una actualización de firmware pusiera SELinux en *Enforcing*, el cambio de fuente dejaría de funcionar (volverían a sonar las dos).

### 4.4 Broadcasts de Topway encontrados
| Acción | Extras | Uso previsto |
|---|---|---|
| `com.tw.music.info` | `musicTitle`, `musicaArtist` (sic), `musicAlbum`, `musicPath` | Música USB (`com.tw.media`) |
| `com.tw.launcher.music_progress_duration` | `msg_music_progress`, `msg_music_duration` (int) | Progreso de la música USB |
| `com.tw.radio.stopscan` | — | Detener la búsqueda de emisoras |
| `com.tw.bt.av.play` | — | Reproducir audio BT |

### 4.5 Bluetooth
- El stack de Android tiene activa la sesión **`com.android.bluetooth/A2dpMediaBrowserService`** (A2DP Sink + AVRCP Controller). Es API estándar: con `MediaBrowser` → `MediaController` se obtienen título y artista (si el teléfono los envía) y los controles play/pausa/siguiente.
- `com.tw.bt` también usa `MediaSessionCompat` internamente, y el centro `xt` expone `btPlayMusic / btPre / btNext`.
- En el volcado capturado, la sesión estaba en `state=0` sin metadatos (probablemente no había música sonando todavía). **Hay que repetirlo** (ver §8).

---

## 5. Estrategia por tarjeta (de mejor a peor opción)

### Radio FM
| Nivel | Leer frecuencia | Controlar |
|---|---|---|
| A | `TWUtil` por reflexión: escuchar `0x0201` / `0x9E0A` | Centro `xt`: `radioNext/Pre/SearchNext/SetChannel` |
| B | Leer `/data/tw/radio_freq` (y `radio_freqs` para presintonías) con `FileObserver` | Centro `xt` |
| C | Mostrar la última frecuencia conocida (guardada) | Abrir `com.tw.radio` |

### Música Bluetooth
| Nivel | Leer metadatos | Controlar |
|---|---|---|
| A | `MediaBrowser` → `A2dpMediaBrowserService` | `MediaController.TransportControls` |
| B | `MediaSessionManager.getActiveSessions` (requiere `NotificationListenerService`, que se habilita con un comando ADB) | idem |
| C | Solo estado conectado/desconectado vía `ITWCommandCallbackAidl.onBtConnectedStatus` | Centro `xt`: `btPlayMusic/btPre/btNext` |

### Navegación
- Abrir: `packageManager.getLaunchIntentForPackage("app.organicmaps.web")`, con alternativa a `app.organicmaps`.
- Destinos rápidos: *deep link* de la API de Organic Maps, por ejemplo `om://route?sll=…&saddr=…&dll=lat,lon&daddr=Casa&type=vehicle`. **Falta verificar** contra la versión instalada.
- Opcional: cambiar `persist.tw.dvr` a Organic Maps (requiere probar si `setprop` funciona desde ADB).

---

## 6. Estructura del código

```
app/src/main/
 ├─ aidl/com/tw/service/xt/aidl/        ← copia de las interfaces (§4.3), mismo paquete y orden
 ├─ java/…/carlight/
 │   ├─ ui/
 │   │   ├─ HomeActivity.kt              (única Activity, HOME + DEFAULT, singleTask)
 │   │   ├─ cards/RadioCardView.kt
 │   │   ├─ cards/BtCardView.kt
 │   │   ├─ cards/NavCardView.kt
 │   │   ├─ DockView.kt
 │   │   └─ AppDrawerDialog.kt
 │   ├─ topway/
 │   │   ├─ TwCommandClient.kt           (bind a com.tw.service.xt, reconexión automática)
 │   │   └─ TwUtilBridge.kt              (reflexión sobre android.tw.john.TWUtil, opcional)
 │   ├─ radio/
 │   │   ├─ RadioSource.kt               (interfaz: state Flow + comandos)
 │   │   ├─ TopwayRadioSource.kt         (niveles A/B)
 │   │   └─ FallbackRadioSource.kt       (nivel C)
 │   ├─ media/
 │   │   └─ BtMediaSource.kt             (MediaBrowser → A2dpMediaBrowserService)
 │   ├─ nav/MapsLauncher.kt              (intents + deep links de Organic Maps)
 │   ├─ apps/AppRepository.kt            (LauncherApps, caché de íconos escalados)
 │   └─ prefs/Settings.kt
 └─ res/  layout/, drawable/, values/, values-night/
```

Reglas de rendimiento:
- Los íconos se escalan a 64 px y se guardan en caché en `RGB_565`/`ARGB_8888` según si tienen transparencia. No se cargan íconos de apps que no están visibles.
- El reloj se actualiza con `ACTION_TIME_TICK`, nunca con un timer por segundo.
- No hay servicios en segundo plano propios: todo vive mientras vive la Activity HOME.
- Se mide con `adb shell dumpsys meminfo <paquete>` en cada fase; si supera 40 MB PSS, se revisa.

---

## 7. Fases

| Fase | Entregable | Cómo se verifica | ¿Necesita la radio? |
|---|---|---|---|
| **1. Esqueleto** ✅ en la radio (12-nov-2026) | Proyecto Gradle, `HomeActivity` como HOME, layout 1024×600 con tarjetas de prueba, reloj, dock, cajón de apps | Emulador + instalar en la radio y elegirlo como predeterminado; comprobar que sobrevive a un reinicio | Al final (prueba HOME) |
| **2. Navegación** ✅ mapa en vivo (12-nov-2026) | Organic Maps en vivo y táctil dentro de la tarjeta central (§3.1). Pendiente: destinos rápidos | Mapa se mueve al arrastrar; el panel se oculta al abrir otra app | Sí |
| **3. Bluetooth** 🟡 (12-nov-2026) | [A2dpBtSource](app/src/main/java/com/carlight/launcher/media/A2dpBtSource.kt): controles por la sesión AVRCP `A2dpMediaBrowserService`. **Título/artista**: el stack BT de Topway no los pone en la sesión; llegan en el broadcast `com.android.getelementattrrsp` (`trackTitle`, `artist`), con `com.tw.btmusic.info` como respaldo. Muestra "Sin teléfono" si no hay conexión | ✅ play/siguiente/anterior (confirmado por el usuario). ⏳ Títulos: falta confirmar con música sonando | Sí |
| **4. Radio FM** ✅ (12-nov-2026) | [TwCommandClient](app/src/main/java/com/carlight/launcher/topway/TwCommandClient.kt) (AIDL en `app/src/main/aidl`) + [TopwayRadioSource](app/src/main/java/com/carlight/launcher/radio/TopwayRadioSource.kt): controles `radioNext/radioPre/radioSearchNext/radioSearchPre`. Frecuencia y presintonía con [LogcatRadioWatcher](app/src/main/java/com/carlight/launcher/radio/LogcatRadioWatcher.kt) (`FMR_tune, [freq=…]` y `[181]--->N`, requiere `READ_LOGS`). Si la radio no reacciona en 2.5 s, se abre su app | Probado en la radio: siguiente → 93.5 (P5), anterior → 92.7 (P4), buscar → 93.1, con el mapa siempre al frente | Sí |
| **5. Pulido** | Día/noche, editor del dock, fondo de pantalla, ajuste de `persist.tw.dvr`, medición de RAM | `dumpsys meminfo` ≤ 40 MB; arranque en frío < 1.5 s | Sí |

---

## 8. Datos que faltan de la radio

Comandos a ejecutar desde `~/Development/CarLightLaucher/radio_dump`, con la radio conectada (`adb connect 192.168.110.217:5555`):

```bash
# 1) APK del centro de comandos (la pieza clave)
adb pull "$(adb shell pm path com.tw.service.xt | sed 's/package://' | tr -d '\r')" com.tw.service.xt.apk
adb shell dumpsys package com.tw.service.xt > pkg_service_xt.txt

# 2) ¿Puede una app normal usar TWUtil y leer los archivos de la radio?
adb shell 'ls -l /dev | grep -i tw'          > perms.txt
adb shell 'ls -ld /data/tw; ls -l /data/tw' >> perms.txt 2>&1
adb shell 'cat /data/tw/radio_freq; echo; cat /data/tw/radio_freqs' >> perms.txt 2>&1

# 3) ¿El servicio BT de Android está exportado?
adb shell dumpsys package com.android.bluetooth | grep -B2 -A6 A2dpMediaBrowserService > bt_service.txt

# 4) Con música del teléfono SONANDO DE VERDAD por BT (dale play en el teléfono antes)
adb shell dumpsys media_session > media_session_bt_playing.txt
```

---

## 9. Riesgos y preguntas abiertas

1. **¿`com.tw.service.xt.CommandService` está exportado y sin permiso de firma?** Si no lo está, el control de radio y BT cae al nivel C (abrir la app de fábrica). Lo responde el punto 1 de §8.
2. **¿`TWUtil` funciona fuera del UID de sistema?** Depende de los permisos de `/dev/tw*`. Si no funciona, la frecuencia se lee de `/data/tw/radio_freq`; si tampoco, se usa el nivel C.
3. **El firmware puede volver a imponer Variety** por `persist.nwd.launcher.default`. Mitigación: desactivar Variety con `pm disable-user`. Hay que verificar que nada de Topway dependa de él (por ejemplo `com.tw.core`).
4. **Metadatos AVRCP:** dependen del teléfono; algunos no envían título y artista.
5. **Deep links de Organic Maps** en la versión `.web`: hay que verificar el esquema exacto.
