<p align="center">
  <img src="icon.png" alt="Raw4All" width="140">
</p>

# Raw4All

**Video con audio y fotografía RAW de 50 MP para explorar las cámaras del Galaxy S23 Ultra, sin root.**

**Versión 0.6.17 · Experimental** · [Cambios](CHANGELOG.md) · [Seguridad](SECURITY.md) · [Guía para contribuir](AGENTS.md)

> [!IMPORTANT]
> **Desde 0.6.16, Raw4All usa su propio paquete: `com.secure.mathai.raw4all`.**
>
> Ya no ocupa `com.samsung.android.app.galaxyraw`, por lo que desaparece el conflicto de identificador con Expert RAW. Sigue dependiendo de una política de prefijos del firmware probado; no hay acceso garantizado con cualquier nombre ni respaldo oficial de Samsung. Es una instalación nueva: concede los permisos de nuevo y configura tus preferencias. La app anterior no se elimina ni migra sus datos privados automáticamente.

## Por qué existe

El proyecto nació después de que se me cayera el celular. Tras reiniciarse, la aplicación de cámara normal dejó de funcionar, pero los sensores W y ST todavía daban imagen en Expert RAW. El objetivo fue **poder grabar video con esos sensores desde una aplicación propia**.

La idea nació al investigar Shizuku como una posible vía para acceder a los IDs privados de cámara sin rootear. Raw4All terminó funcionando con Camera2 y nuestra propia firma, sin instalar Shizuku. Su código no forma parte de este repositorio.

Las aplicaciones del fabricante pueden contar con privilegios ligados a su firma o instalación. La investigación inicial utilizó el identificador de Expert RAW. Más adelante comprobamos acceso W/ST con un paquete separado que empieza por `com.sec`, sin obtener el permiso `SYSTEM_CAMERA` ni la identidad de Samsung. La consulta de autorización también aceptó variantes que empiezan por `com.samsung`; las pruebas indican una regla de prefijos más amplia que el nombre exacto de Expert RAW, aunque no se reconstruyó por completo su implementación nativa. La captura RAW de alta resolución también utiliza un adaptador del framework Samsung presente en el teléfono. Este comportamiento depende del firmware.

**Proyecto independiente, sin afiliación, patrocinio ni respaldo oficial de Samsung.** Apoyamos a Samsung y valoramos sus dispositivos y tecnologías; Raw4All nació para aprovechar un teléfono que seguimos disfrutando.

La investigación y validación del paquete separado están documentadas en [Acceso con paquete independiente](CAMERA_ACCESS.md): incluye las dos comprobaciones de autorización, consultas de prefijos y pruebas de video/RAW de 0.6.16.

## Qué puedes hacer

| Función | Estado actual |
| --- | --- |
| Video | Full HD, H.264, micrófono AAC opcional; pausa y continuación en el mismo archivo. |
| Velocidad | 30 fps comprobados en W/ST. El selector ofrece 60 fps únicamente cuando cámara y codificador lo permiten; no están disponibles en las rutas W/ST probadas. |
| Formato | Auto, 9:16 o 16:9. Durante la grabación quedan fijos formato, orientación y escala. |
| Fotografía | JPEG y **50 MP (RAW)** en W: DNG de 8160 × 6120. |
| Lentes y zoom | W de 1× a 8×, ST de 10× a 20× y opción frontal. Al salir o cerrar, el zoom vuelve al valor inicial de la lente. |
| Estabilización | Automática, desactivada u óptica, según disponibilidad. Horizonte fijo opcional para video. |
| Controles | Enfoque al tocar, bloqueo AF/AE, compensación de exposición, cuadrículas y temporizador de 3/10 s. |
| Grabación | Contador con milisegundos, medidor de audio, almacenamiento libre y estimación de minutos disponibles. Sin duración máxima fija de la app, sujeta al almacenamiento y al dispositivo. |
| Galería | Capturas en `DCIM/Raw4All`, miniaturas de la sesión y acceso a la galería del teléfono. |

La interfaz usa controles flotantes en vertical y horizontal. Puedes ocultarlos durante el video: el tiempo queda dentro de **Mostrar UI** y el medidor de audio permanece abajo. El splash conserva fondo negro, logo y distorsión RGB en **MATH-AI**.

## Usar la cámara

- **Foto/Video:** cambia de modo antes de capturar. Durante el video, ese botón pasa a **pausar/continuar**; el disparador central detiene y guarda. Las pausas se descuentan del contador.
- **Formato:** toca Auto/9:16/16:9 para elegir. Mantén pulsado para seleccionar giro natural u horizonte fijo. El horizonte fijo es experimental, solo para video, y añade un recorte de 2,04×.
- **Enfoque y luz:** toca la escena para enfocar; mantén pulsado para bloquear AF/AE. Desliza verticalmente para ajustar exposición. Doble toque libera el bloqueo; el icono de flecha, enfoque y ampolleta restablece enfoque automático y exposición a cero.
- **Zoom:** pellizca, usa la barra o toca el valor para accesos rápidos. W llega a 8× en la ruta probada; no se anuncia 10× digital como disponible.
- **Ajustes:** activa/desactiva audio, elige temporizador o cuadrícula: tercios, proporción áurea, diagonales, centro o 4 × 4.
- **Temporizador:** el label «3 s»/«10 s» queda fijo. Cada número de la cuenta atrás entra grande y se aleja con strobe blanco. Otro toque cancela. Las teclas de volumen también disparan o inician/detienen video.
- **Galería:** muestra fotos y videos de la sesión. El diálogo incluye **Ir a Galería**; sin capturas en la sesión, abre directamente la galería del teléfono.

La app graba en primer plano: salir finaliza el video. El contador muestra `00:09.347` y horas cuando corresponde; se calcula con reloj monotónico y la pantalla se actualiza aproximadamente 30 veces por segundo. Los minutos disponibles son una estimación de almacenamiento, no una cuenta regresiva. Los ajustes se conservan salvo el zoom.

## Fotografías de 50 MP (RAW)

Selecciona **W → megapíxeles → 50 MP (RAW)** y dispara en modo Foto. La preparación tarda unos segundos y el DNG ocupa aproximadamente **100 MB**.

- Captura completa en **4:3 y 1×**, sin interpolar una fotografía JPEG. El zoom digital vuelve al seleccionar JPEG o video.
- Conserva las muestras RAW10 sin pérdida dentro de un contenedor DNG de 16 bits: la precisión original sigue siendo **10 bits**.
- Guarda calibración de color, balance de blancos, exposición, ISO y orientación. Es una captura individual; no reproduce el procesamiento multiframe, reducción de ruido ni correcciones ópticas de Expert RAW.
- Los archivos de prueba se abrieron y revelaron con LibRaw. El aspecto, las miniaturas y la edición dependen del visor compatible con DNG.

ST y frontal mantienen sus opciones JPEG. No se ofrecen 100/200 MP como capacidades comprobadas.

## Compatibilidad

Probado en **Samsung Galaxy S23 Ultra SM-S918B, Android 16, firmware S918BXXSAFZG1**. W usa el ID `56`, ST el `54`; también se comprobó captura de video con el ID frontal `3`. La opción frontal del menú se descubre mediante Camera2.

**Raw4All puede funcionar en este dispositivo y fallar en otro teléfono, incluso del mismo modelo, o después de una actualización del teléfono probado.** La compatibilidad depende de:

- **La autorización del firmware:** la política Samsung debe aceptar el paquete `com.secure.mathai.raw4all` para abrir las cámaras restringidas. Una actualización puede modificar esa política.
- **Los IDs y capacidades de cámara:** las rutas `56`/`54`, resoluciones, formatos y controles disponibles pueden cambiar o no existir en otros modelos o versiones del sistema.
- **El adaptador RAW del framework Samsung:** la captura de 50 MP usa una interfaz específica del dispositivo que puede variar entre firmwares. El video y el RAW deben comprobarse por separado.

El paquete propio elimina el conflicto de identificador con Expert RAW, pero **no elimina la dependencia del firmware ni demuestra compatibilidad universal**. Otros teléfonos y firmwares todavía no están validados; esto no significa que necesariamente fallen. Para reportar una prueba, incluye modelo exacto, versión de Android, compilación del firmware y resultados de W/ST, video con audio, JPEG y RAW cuando esté disponible.

UW y T no están habilitadas en este prototipo. No se garantiza el funcionamiento en otros modelos o firmwares. El proyecto sigue siendo experimental: las pruebas realizadas no equivalen a una evaluación exhaustiva de calidad, temperatura o grabación prolongada.

## Compilar e instalar

Requisitos en Windows:

- PowerShell y el JDK de Android Studio; compilación probada con JDK 25.
- Android SDK Platform 35, Build Tools 36.0.0 y Platform Tools para ADB.
- Keystore local de desarrollo. El script usa `.android/debug.keystore` y las credenciales estándar de depuración; la clave existente del proyecto **no se distribuye**.

Desde la raíz del repo:

```powershell
.\build.ps1
.\check.ps1
adb install -r .\build\Raw4All-v0.6.17.apk
```

Puedes indicar ubicaciones propias con `build.ps1 -SdkPath ... -JavaPath ... -KeyStore ...`; `check.ps1` acepta `-JavaPath`. El script de compilación actual está preparado para firma de desarrollo, no para contraseñas de producción. No requiere Gradle ni las carpetas de investigación.

El APK y su `.sha256` se generan en `build/`, excluido de Git. El paquete local final se conserva en `dist/`, también excluido: sirve para distribuir el APK por separado del código. Comprueba un archivo con:

```powershell
Get-FileHash .\dist\Raw4All-v0.6.17.apk -Algorithm SHA256
```

Para preparar una versión para compartir, compila desde una carpeta de trabajo limpia, ejecuta `check.ps1` y conserva el APK junto a su `.sha256`. El hash del paquete preparado y las comprobaciones realizadas se registran en `dist/VALIDATION.md`. Los APK se distribuyen como archivos de una versión de GitHub; no hace falta incorporarlos al historial del código.

Una compilación propia con otra clave no puede actualizar la instalación existente. La versión y `versionCode` se definen en `AndroidManifest.xml`; el historial está en [CHANGELOG.md](CHANGELOG.md).

## Código y validación

```text
src/local/s23probe/   Cámara, UI, grabación, horizonte, RAW/DNG y reloj
res/                 Iconos, splash y recursos Android
assets/design/       SVG del disparador
checks/              Comprobaciones de fps, filtro, RAW10 y reloj
AndroidManifest.xml  Paquete, permisos, actividad y versión
build.ps1            Compilación, firma y hash del APK
check.ps1            Ejecución de comprobaciones locales
```

`check.ps1` comprueba selección de fps, filtrado del horizonte, desempaquetado RAW10 y reloj con pausas/milisegundos. Para cambios de cámara también hacen falta pruebas físicas: orientación, encuadre, audio, zoom, pausa y guardado. Para contribuir, incluye modelo, firmware, pasos y resultados; añade capturas cuando cambies la interfaz.

En el teléfono de prueba se verificaron W/ST, video Full HD con audio, pausa/continuación en un archivo, zoom al volver a abrir y guardado en galería. La fotografía DNG de 50 MP se abrió y reveló con LibRaw, y el efecto RGB del splash fue confirmado visualmente. Estas comprobaciones no sustituyen las pruebas necesarias en otros dispositivos.

El repositorio contiene código y recursos del proyecto. Se excluyen Shizuku, APKs y bibliotecas de Samsung, código decompilado, claves, registros y capturas personales de las pruebas.

## Confianza y seguridad

El manifest no solicita Internet; la implementación no incorpora anuncios ni analítica y guarda las capturas localmente. Cámara y micrófono se usan para las funciones visibles. Desde 0.6.16 no se declara `SYSTEM_CAMERA`.

**Revisión local de seguridad completada en 0.6.17:** se retiraron los extras externos de automatización y la app ya no es depurable ni emite registros detallados. El APK mantiene la firma de desarrollo existente; preparar una firma de distribución y una revisión independiente siguen pendientes. El alcance, las correcciones y las pruebas están en [SECURITY_AUDIT.md](SECURITY_AUDIT.md). Un hash verifica identidad de archivos, no certifica su seguridad. Consulta [SECURITY.md](SECURITY.md).

**Te invitamos a descargar el repositorio, revisarlo y compilar tu propia versión.** Puedes apoyarte en **Codex o Claude Code** para analizar permisos, entender la implementación, proponer mejoras y experimentar. Comprueba sus conclusiones y prueba los cambios en tu dispositivo.

## Créditos

- **MATH-AI · Mathias Tapia G.** — idea, creación, investigación, diseño y pruebas en el teléfono.
- **OpenAI Codex** — asistencia de IA en investigación técnica, implementación, diagnóstico y documentación.
- **Samsung y Android** — dispositivos y tecnologías que motivaron el proyecto; este agradecimiento no implica afiliación ni respaldo.
