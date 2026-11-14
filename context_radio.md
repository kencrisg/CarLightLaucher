A continuación tienes la ficha técnica exacta y las condiciones del sistema extraídas a bajo nivel:
    
    ---
    
    ### 1. Ficha Técnica y Limitaciones de Hardware
    - **Plataforma / Placa Base:** Topway TS7 (plataforma OEM `sp7731e_1h10_native` / `SC7731e_2_IRD_K_V1.1.0`).
    - **Procesador (SoC):** Unisoc / Spreadtrum SL8141E / SC7731e (Quad-Core ARM Cortex-A7 @ 1.3 GHz, arquitectura de 32 bits `armeabi-v7a`).
    - **GPU:** ARM Mali-T820 / Mali-400 (Soporte OpenGL ES 3.0).
    - **Memoria RAM Física:** 1.0 GB LPDDR3 (con 800 MB de ZRAM swap). 
      - *Límite de Heap por app (`heapgrowthlimit`):* 128 MB.
      - *Presupuesto de RAM para este launcher:* **Máximo 35 MB - 45 MB**. No debe saturar la memoria para evitar que el Low Memory Killer (LMK)
      cierre apps en segundo plano (navegación GPS o música).
    - **Almacenamiento:** 16 GB eMMC (partición `/data` de ~13 GB).
    - **Pantalla y Resolución:** 
      - Resolución fija: **1024 x 600 píxeles** (orientación horizontal apaisada / landscape fija).
      - Densidad: `160 dpi` (densidad `mdpi`).
      - Factor de forma: Pantalla táctil capacitiva estándar 16:9 (controlador Goodix).
    
    ---
    
    ### 2. Entorno de Software y Sistema Operativo
    - **Versión de Android Real:** **Android 8.1.0 Oreo (API Level 27)**.
      - `minSdkVersion`: 26 o 27.
      - `targetSdkVersion`: 27 o 28 (compatible con Android 8.1 sin APIs obsoletas ni sobrecarga de Jetpack Compose moderno).
    - **Google Play Services:** **Completamente inexistentes / desactivados**. No se debe depender de Firebase, Google Play Services, Google Maps
    API, ni librerías de Google.
    - **Entorno Automotriz:** Sin sensores telefónicos (ni acelerómetro, ni giroscopio, ni ranura SIM activa). La velocidad se calcula por el chip
    GNSS nativo (`/dev/gnss`) o GPS estándar de Android (`android.location.LocationManager`).
    
    ---
    
    ### 3. Launcher Actual y Ecosistema de Fábrica a Reemplazar
    - **Launcher de Fábrica:** **Variety Launcher** (paquete: `com.dofun.variety`, tema: `launcher.variety.theme.plugin.ts6`).
    - **Firmware Base:** Topway `V12.1.1_20220628.154408_THEME1`.
    - **Por qué fallan los temas externos:** La radio rechaza temas con división de pantallas complejas (*one-screen-three-display* o temas para
    TS10) porque el firmware valida la placa base (`TS7`) y la resolución exacta (1024x600).
    - **Integración con Hardware del Vehículo (Aplicaciones propietarias de Topway):**
      El launcher debe comunicarse mediante Intents estándar o llamadas a paquetes del sistema con las siguientes apps instaladas de fábrica:
      - **Radio FM física:** `com.tw.radio`
      - **Bluetooth Audio y Llamadas:** `com.tw.bt`
      - **Reproductor Multimedia USB:** `com.tw.media`
      - **Ecualizador de Audio (Chip PT2313):** `com.tw.eq`
      - **Cámara de Retroceso / DVR:** `com.tw.reverse` / `com.tw.auxin`
      - **Ajustes de Fábrica y Vehículo:** `com.dofun.carsetting` / `com.tw.carchoose`
      - **CarPlay / Android Auto USB:** ZLink (`rw.zlink.bt.type = built_in`)
    
    ---
    
    ### 4. Guía de Rendimiento y Efectos Visuales (Imprescindible)
    Dado que la CPU es Cortex-A7 (32-bit) y la RAM es de solo 1 GB:
    1. **Tecnología de Interfaz:** Usar vistas tradicionales nativas de Android en XML con `ViewBinding` o Java/Kotlin nativo ultra optimizado.
    **Evitar Jetpack Compose** (consume demasiada RAM y tiempo de compilación para este procesador).
    2. **Efectos Visuales permitidos:**
       - Transiciones basadas en `ValueAnimator` o `DynamicAnimation` (animaciones de físicas ligeras).
       - Fondos estáticos o con gradientes vectoriales simples.
       - Modos Día / Noche basados en temas de recursos (`values-night/styles.xml`) o disparados por la señal de luces/hora.
    3. **Efectos Prohibidos:**
       - **NO usar Blur en tiempo real** (desenfoque por software / RenderScript satura la GPU Mali-400 y causa caídas de cuadros).
       - **NO usar videos de fondo ni shaders pesados de OpenGL**.
       - **NO cargar imágenes sin comprimir o sin escalar:** Todas las imágenes deben decodificarse en el tamaño exacto con `inSampleSize` o
       formato `RGB_565`.
    4. **Disposición Recomendada de la Pantalla (1024x600):**
       - **Panel Izquierdo / Barra lateral:** Accesos directos verticales (Home, Navegación GPS offline, Radio, Música, Ajustes, Aplicaciones).
       - **Zona Central:** Tarjeta principal intercambiable (Velocímetro digital por GPS, Reloj analógico/digital, o carátula de música actual).
       - **Zona Derecha / Inferior:** Controles rápidos multimedia y barra de estado de volumen / brillo.

    ---

    ### Requerimientos para trabajar:
    1. Diseña un proyecto Android en Gradle que compile con `minSdk 26` y `targetSdk 27`.
    2. Proporciona el `AndroidManifest.xml` con las categorías `android.intent.category.HOME` y `DEFAULT`, asegurando que arranque automáticamente
    al encender el auto.
    3. El código debe priorizar bajo consumo de CPU, cero consumo de batería (no aplica deep sleep en auto pero sí evitar calentamiento) y no
    superar los 40 MB de memoria de trabajo.

    ¿Entendido? Confírmame si tienes claras las limitaciones y comencemos a definir la estructura del proyecto.
  ──────
  ### 📌 Notas de respaldo para tu desarrollo:

  • Las propiedades de bajo nivel completas de la radio están respaldadas en backup_radio/getprop.txt.
  • La lista completa de paquetes del sistema automotriz está en backup_radio/packages.txt.

/Users/kencrisg/Development/cleanyourtv