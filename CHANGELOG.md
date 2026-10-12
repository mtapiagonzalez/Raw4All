# Historial de cambios

## 0.6.17 — 2026-10-12

- Revisión local de seguridad documentada en `SECURITY_AUDIT.md`. Se eliminan los extras de automatización y configuración externa del launcher; las capturas requieren interacción normal.
- App no depurable y sin registros detallados en la compilación final. Firma de desarrollo conservada para actualizar la instalación existente.
- Compilación con carpetas nuevas de clases/DEX para excluir restos de compilaciones anteriores. Manejo de errores al publicar JPEG sin terminar la actividad.
- Comprobaciones locales, rechazo de automatización por intents, cierre de cámara/micrófono al salir, videos W/ST, JPEG y RAW verificados en el teléfono. La revisión no constituye una certificación independiente.

## 0.6.16 — 2026-10-12

- Identificador independiente `com.secure.mathai.raw4all`: deja de ocupar el paquete de Expert RAW. Es una instalación nueva respecto a 0.6.15; los ajustes y datos privados no se migran automáticamente.
- Se elimina la declaración de `SYSTEM_CAMERA`. La app conserva Camera2, su firma propia y los IDs W/ST 56/54.
- Investigación en S918BXXSAFZG1: un diagnóstico con el paquete `com.secure.mathai.raw4all.accessprobe` recibió 89 fotogramas Full HD en W y 83 en ST, sin modificar la comprobación local. La política acepta varios nombres que empiezan por `com.sec` o `com.samsung`; no se ha demostrado independencia de esa política ni compatibilidad con otros firmwares.
- App completa validada bajo el paquete separado: videos W/ST Full HD con pista AAC y guardado en galería; DNG W 8160×6120 abierto y revelado con LibRaw. Documentación técnica en `CAMERA_ACCESS.md`.

## 0.6.15 — 2026-10-12

- MATH-AI con separación RGB de las letras completas, desplazamientos de color más amplios y cortes horizontales cada 65 ms. El efecto permanece durante la mayor parte del splash y termina con el texto limpio.
- Mantiene la geometría original del splash: el logo y el texto base no cambian de tamaño ni de posición.
- Compatibilidad con la marca que One UI dibuja como fondo de una vista. Distorsión RGB comprobada en la grabación del arranque y confirmada por el usuario.

## 0.6.14 — 2026-10-12

- Zoom conservado por lente solo mientras la cámara permanece abierta. Al salir de la app o cerrarla, se restablece al valor inicial: W/frontal 1× y ST 10×.
- Se ignoran los valores de zoom persistidos por versiones anteriores. Las reconfiguraciones internas de cámara no restablecen el zoom durante el uso.
- Compilación e instalación verificadas. En el teléfono, al volver desde Inicio: W pasó de 6,1× a 1× y ST de 17,2× a 10×.

## 0.6.13 — 2026-10-12

- Splash sin sustitución por un segundo dibujo: mantiene el logo y MATH-AI del sistema hasta abrir la cámara, evitando saltos de tamaño y posición.
- Glitch limitado a franjas de color superpuestas sobre la imagen real de MATH-AI, usando sus dimensiones y transformación originales. Si el sistema no expone esa imagen, conserva el splash sin efecto. Mantiene la duración de 1,2 s.
- Compilación, firma e instalación verificadas; grabación del arranque en el S23 Ultra revisada por fotogramas: logo y marca mantienen tamaño y posición hasta aparecer la cámara.

## 0.6.12 — 2026-10-12

- Cuenta atrás en una capa independiente: el número parte enorme frente al visor y se aleja en perspectiva hasta su posición debajo del indicador del temporizador. Parpadeo strobe y resplandor únicamente sobre el número.
- El label «3 s» o «10 s» permanece fijo y visible; ya no se reemplaza ni se anima durante la cuenta atrás. Cancelar o salir oculta inmediatamente el efecto.

## 0.6.11 — 2026-10-12

- Cuenta atrás fotográfica: cada número entra a 2,3 veces su tamaño y se reduce suavemente, con destello blanco y desvanecimiento. Permanece debajo de los minutos en ambas orientaciones; cancelar o salir restablece la apariencia.
- Contador de video con milisegundos (`00:09.347`), también dentro de Mostrar UI. Usa tiempo monotónico, descuenta pausas y actualiza la pantalla aproximadamente 30 veces por segundo; la comprobación de almacenamiento sigue limitada a una vez por segundo.
- Comprobación del reloj: transiciones de minutos/horas, pausas repetidas, reinicio y formato de milisegundos.

## 0.6.10 — 2026-10-12

- MATH-AI con dos pulsos glitch cian/rosa durante el splash de 1,2 s; logo de 112 dp y fondo negro.
- Selector **50 MP (RAW)** en W: captura RAW10 real de 8160 × 6120, DNG sin compresión de unos 100 MB y guardado en Galería. Conserva precisión de 10 bits, calibración de color, balance de blancos, exposición, ISO y orientación.
- Inicialización de alta resolución mediante el adaptador Samsung del sistema, salida con opción 2 y calentamiento de vista previa antes de disparar. Recuperación abriendo de nuevo la cámara normal; límite de espera de 15 s y limpieza de archivos incompletos.
- RAW usa el sensor completo en 4:3 y 1×; JPEG y video mantienen sus resoluciones y zoom. La orientación queda bloqueada durante la captura RAW.
- Temporizador fotográfico debajo de los minutos disponibles, blanco y en negrita, con pulso de opacidad durante la cuenta atrás. Un segundo disparo cancela; salir de la cámara detiene la animación.
- El botón Foto se convierte en pausa/continuar durante la grabación, con iconos sólidos. MediaRecorder pausa audio y video en el mismo archivo; el contador visible y el de Mostrar UI excluyen el tiempo en pausa.
- Validación: fotogramas reales de 50 MP recibidos, DNG publicado en MediaStore, abierto y revelado con LibRaw; comprobación RAW10 de todos los valores de 0 a 1023 y filas malformadas.

## 0.6.9 — 2026-10-12

- Logo de splash reducido a 112 × 112 dp; conserva lienzo transparente de 288 dp, fondo negro, MATH-AI y duración mínima de 1,2 s.
- Compilación, firma, instalación y arranque en frío correctos; recorte visual pendiente de confirmación en One UI.

## 0.6.8 — 2026-10-12

- Splash con duración mínima de 1,2 s desde la creación inicial de la actividad, solicitada por el usuario; sin bloquear el hilo principal ni repetir la espera en recreaciones con estado guardado.
- Compilación, firma e instalación verificadas. Arranque en frío correcto: 1,554 s totales, incluyendo el inicio del proceso.

## 0.6.7 — 2026-10-12

- Splash: logo de 128 dp dentro de un lienzo transparente fijo de 288 dp, evitando que el dibujo llene el área recortada del sistema; fondo del ícono explícitamente transparente. Pantalla negra y marca MATH-AI conservadas, sin demoras añadidas.
- Compilación, firma e instalación verificadas. Arranque en frío correcto (459 ms hasta la actividad); apariencia final en One UI pendiente de confirmación.

## 0.6.6 — 2026-10-12

- Añade tres rayos a la ampolleta del ícono de restablecer; ajusta el dibujo dentro de sus límites para evitar recortes.
- Compilación, firma e instalación verificadas. Diseño confirmado por el usuario en el teléfono.

## 0.6.5 — 2026-10-12

- Ícono de restablecer actualizado: círculo casi cerrado con flecha, marcas de enfoque centrales y ampolleta en la abertura superior derecha. Conserva tamaño y acción del botón.
- Compilación, firma e instalación verificadas; ícono revisado en captura del teléfono.

## 0.6.4 — 2026-10-12

- Divide el espacio anterior de ajustes en dos botones: tuerca y restablecer enfoque/exposición, con símbolo de enfoque y flecha circular. Restablecer libera AF/AE y devuelve la compensación a 0 EV.
- Selector de guías con miniaturas: ninguna, tercios, proporción áurea, diagonales, centro y 4 × 4. Conserva la selección local y migra la cuadrícula anterior a tercios.
- Guías dibujadas solo en la vista previa, sin incorporarlas al archivo. Distribución compartida entre vertical y horizontal.
- Compilación, firma e instalación verificadas; botones y selector revisados en el teléfono en vertical.

## 0.6.3 — 2026-10-12

- Tuerca vectorial de 26 dp y panel de ajustes con tarjetas redondeadas, íconos consistentes y estados actualizados sin cerrar el panel.
- Micrófono encendido en menta, micrófono tachado para Sin audio, reloj de temporizador y cuadrícula ampliada. Restablecer enfoque y luz conserva su acción y queda sin ícono, pendiente de elección.
- Compilación, firma e instalación verificadas; panel e íconos revisados visualmente en vertical. Contenido desplazable para pantallas de menor altura.

## 0.6.2 — 2026-10-12

- Disparador basado en el SVG aportado: aro multicolor vectorial, centro blanco en foto, rojo en video y cuadrado rojo redondeado al grabar. Estado presionado con centro reducido.
- Conserva área táctil y adaptación a vertical/horizontal. SVG fuente en `assets/design/shutter.svg`; aro convertido a recurso vectorial Android.
- Compilación, firma e instalación verificadas; estados foto y grabación revisados en capturas verticales del dispositivo.

## 0.6.1 — 2026-10-12

- Al ocultar la UI durante la grabación, el medidor de audio permanece visible abajo, centrado en el área segura y ampliado de 81 a 104 dp. Al mostrarla vuelve sobre el texto de video.
- Refresco inmediato al alternar controles. Compilación, firma e instalación verificadas; captura de pantalla confirmó posición inferior y barras activas en vertical. Posicionamiento compartido con horizontal; validación visual horizontal pendiente.

## 0.6.0 — 2026-10-12

- Gestos de exposición vertical, enfoque puntual, bloqueo al mantener y liberación con doble toque; indicadores breves.
- Zoom progresivo por pellizco, deslizador y accesos al tocar el valor; selector W/ST/frontal entre tomas.
- Medidor de audio de doce segmentos en colores suaves, centrado sobre la descripción de video en ambas disposiciones. Micrófono opcional desde Ajustes, antes de grabar.
- Estimación de minutos por espacio y bitrate, fija durante la grabación; recalculada al abrir, guardar o cambiar ajustes relevantes.
- Preferencias persistentes de lente, zoom por lente, foto/video, micrófono, cuadrícula y temporizador, además de los ajustes anteriores.
- Temporizador fotográfico de 3/10 segundos (segundo toque cancela), cuadrícula y disparador con volumen.
- Compilación/firma e instalación verificadas. Frontal ID 3: clip de 3.978 s, H.264 1920×1080 y AAC. Vista vertical del medidor revisada; evaluación manual de gestos, giro frontal y temporizador pendiente.

## 0.5.23 — 2026-10-12

- Prueba opcional `probe_raw50` de RAW10 8160×6120 en W, basada en los metadatos del dispositivo; no añade una opción de 50 MP al usuario.
- En el S23 Ultra probado, tanto la sesión normal como la salida Samsung con opción 2 fueron rechazadas durante la configuración. No se obtuvo un fotograma de 50 MP. La vista previa se recuperó en ambas pruebas.
- Compilación, firma, instalación y recuperación de cámara verificadas. JPEG continúa siendo el único formato fotográfico implementado.

## 0.5.22 — 2026-10-12

- Splash nativa negra con el ícono centrado y MATH-AI abajo, sin espera artificial. Recursos alternativos para Android 10/11.
- Corrige el enlace de recursos en build.ps1 para admitir temas propios.
- Compilación y firma verificadas; instalación y arranque en frío correctos en el teléfono (502 ms hasta la actividad). Apariencia pendiente de confirmación visual.

## 0.5.21 — 2026-10-12

- Diagnóstico opcional de mapas de máxima resolución y metadatos del fabricante mediante `inspect_resolution`; aún no habilita fotografías de 50 MP.

## 0.5.20 — 2026-10-12

- Selector MP junto a W/ST en vertical y horizontal, basado en tamaños JPEG anunciados; recuerda la selección por cámara.
- Sustituye la resolución fotográfica fija 1920×1080 por una selección inicial cercana a 12 MP, si está disponible. Alternativa de 2 MP si la combinación de sesión falla.

## 0.5.19 — 2026-10-12

- El botón de galería abre Samsung Gallery si no hay capturas en la sesión; si existen, conserva la galería de sesión. Alternativa genérica para otros dispositivos.
- Botón Ir a Galería en el cuadro de capturas de la sesión.

## 0.5.18 — 2026-10-12

- Galería interna de sesión con miniaturas de fotos y videos, más recientes primero; selección abre el visor del teléfono.
- Conserva lista al volver del visor y en recreación de actividad; una sesión nueva empieza vacía. Miniaturas en segundo plano y caché limitada.

## 0.5.17 — 2026-10-12

- Tiempo integrado dentro de Mostrar UI en una segunda línea, pequeño y rojo suave; botón alineado con el centro del encabezado.
- Conserva el último ángulo del sensor en modo natural y reinicia el filtro al reactivar Horizonte fijo.
- Icono R4A sobre fondo negro mediante recurso drawable.

## 0.5.16 — 2026-10-12

- Corrige la referencia angular de Horizonte fijo en horizontal, tanto en la vista previa como en la ruta GPU del video; elimina compensación duplicada de pantalla.
- Sustituye el icono por el diseño R4A proporcionado por el autor. Validación física horizontal pendiente.

## 0.5.15 — 2026-10-12

- Contador flotante bajo Mostrar UI al ocultar controles durante la grabación, actualizado cada segundo y respetando los márgenes del sistema.

## 0.5.14 — 2026-10-12

- Filtro angular One Euro adaptativo y continuo: reemplaza la tolerancia de 1,2° y el suavizado fijo.
- Pruebas sintéticas de ruido, giro rápido, cruce 359°/0°, tasas 50/100 Hz y reanudación aprobadas. Sensación física pendiente.

## 0.5.13 — 2026-10-12

- Alinea marcas de tiempo de cámara con el reloj monotónico de MediaRecorder. La normalización a cero probada en 0.5.12 no era compatible y se reemplazó.
- Conserva corrección de superficie, sensor fusionado y tolerancia contra ruido de 0.5.11. Prueba física de horizonte pendiente.

## 0.5.11 — 2026-10-12

- Corrige CaptureRequest para usar la misma superficie GPU configurada en la sesión.
- Horizonte utiliza GAME_ROTATION_VECTOR (giroscopio y acelerómetro), tolerancia de 1,2° y conserva el último ángulo cuando el teléfono está plano.
- Prueba W: ocho segundos guardados en Galería; fluidez y sentido con movimiento pendientes de confirmación física.

## 0.5.10 — 2026-10-12

- Invierte el sentido de Horizonte fijo en vista previa y procesamiento del video.
- Lectura de orientación con SENSOR_DELAY_GAME y menor suavizado para reducir el retraso. Fluidez y sentido pendientes de validación física.

## 0.5.9 — 2026-10-12

- Horizonte fijo opcional para video: compensación por GPU antes de codificar, sensor suavizado y recorte fijo 2,04×. Se mantiene giro natural como modo predeterminado.
- La opción se elige manteniendo pulsado el formato; no se aplica a fotografías. Validación física pendiente.

## 0.5.8 — 2026-10-12

- Solo Auto cambia de orientación con el teléfono. 9:16 y 16:9 manuales fijan interfaz y encuadre, conservando el giro físico de la escena.
- Conserva bloqueo de formato, orientación y escala durante la grabación. Prueba física pendiente.

## 0.5.7 — 2026-10-12

- Separa orientación de la vista previa/interfaz y formato del archivo: permite vista horizontal con archivo 9:16.
- Elimina el giro adicional experimental por sensor; utiliza la orientación de pantalla. Compilación e instalación verificadas; validación física pendiente.

## 0.5.6 — 2026-10-12

- Vista previa compensa el giro lateral en sentido contrario: 90° se compensa con 270°. Conserva cuadrantes estables y bloqueo al grabar. Confirmación física pendiente.

## 0.5.5 — 2026-10-12

- Giro lateral por cuadrantes con histéresis de 15° para evitar tambaleo y corrección del sentido respecto a la pantalla.
- Ángulo del sensor congelado durante la grabación, conservando orientación y escala. Validación física pendiente.

## 0.5.4 — pendiente de instalación

- Auto recupera la orientación por sensor; seleccionar Auto desactiva el seguimiento lateral fijo.
- Separación adicional de 14 dp entre W/ST y la fila de estabilización en vertical.
- Botón Ocultar UI / Mostrar UI durante la grabación, sin modificar la escala de la vista previa.
- Seguimiento lateral usa el ángulo real del sensor en la vista previa; no se incorpora al video de MediaRecorder. Compilación Java verificada, prueba física pendiente.

## 0.5.3 — 2026-10-12

- Vista previa a pantalla completa mediante recorte central uniforme en ambas orientaciones.
- Orientación fija usa orientación canónica incluso al activarse con el teléfono invertido. Validación física pendiente.

## 0.5.2 — 2026-10-12

- Etiqueta de zoom centrada vertical y horizontalmente en su fila.
- Orientación fija opcional, independiente de la estabilización; menú al mantener pulsado el formato. Permite escena invertida al girar el teléfono 180°. Prueba física pendiente.

## 0.5.1 — 2026-10-12

- Destello visual breve al comenzar una captura fotográfica; no activa el flash físico.
- Contador oculto en modo foto e iconos vectoriales de cámara, video, detener y galería. El botón de galería abre la última captura mediante el visor del teléfono.
- Controles flotantes también en horizontal; vista previa independiente de los paneles y sin recortar los laterales.
- Botones de fps/estabilización/enfoque con el mismo ancho para centrar la fila y su relación con W/ST.
- Opción Giro libre: fija la orientación de la interfaz y usa OIS si está disponible, con EIS desactivada, para permitir inclinación de la escena. Pendiente de prueba física.

## 0.5.0 — 2026-10-12

### Cambios

- Selector de 30/60 fps con comprobación de rangos AE, tiempos mínimos de preview/grabación y capacidad del codificador H.264.
- FPS aplicados tanto a la solicitud Camera2 como a MediaRecorder; parámetros de sesión configurados antes de abrirla.
- Interruptor de estabilización ON/OFF, independiente de la orientación.
- Formato, fps y estabilización bloqueados durante la grabación; preferencias conservadas entre aperturas.
- Aviso verde menta al publicar el video en la galería.
- Interfaz horizontal con disparador en columna lateral, controles en una barra inferior y contador/estado sobre la vista previa.
- Interfaz vertical con controles flotantes, sin título ni panel sólido; almacenamiento sobre el contador, información y créditos accesibles mediante ⓘ.
- Vista previa escalada proporcionalmente, sin recortar los laterales. El formato completo 9:16 puede dejar espacio superior/inferior en pantallas más alargadas.
- Enfoque puntual y medición AE, indicador AF basado en resultados de captura, bloqueo por pulsación prolongada y restablecimiento con AF Auto. Los puntos de medición contemplan rotación y zoom.
- Contador restablecido y almacenamiento actualizado al finalizar el video.
- Versión visible manteniendo pulsado el título, APK con versión en el nombre y hash SHA-256.
- README con compatibilidad destacada, origen de la investigación, créditos y revisión de seguridad pendiente.

### Validación

Compilación, firma e instalación verificadas. Comprobaciones de contador y política de fps aprobadas. W y ST anuncian hasta 30 fps en la ruta de captura probada; 60 fps se muestra como no disponible. Se observó bloqueo AF/AE confirmado por resultados de captura y la interfaz horizontal completa con imagen derecha fue confirmada por el usuario.

Una prueba ST sin estabilización produjo H.264 Full HD (~30 fps), AAC a 48 kHz y 11,925 s de duración. Decodificación completa sin errores y audio no silencioso. Captura posterior con contador 00:00; registro de publicación en MediaStore. W volvió a reportar EIS=1 tras reactivar estabilización. Esto no sustituye una auditoría de seguridad ni valida grabaciones prolongadas.

## 0.4.0 — Prototipo anterior

- Full HD con audio AAC, modos de foto/video y guardado mediante MediaStore.
- Formatos Auto/9:16/16:9, giro de interfaz y escala uniforme de preview.
- Orientación y escala bloqueadas durante la grabación, comprobadas con el usuario.
- Zoom W 1×–8× y ST 10×–20×.
- W y ST reportaron estabilización electrónica activa en los resultados de captura.

El prototipo usaba `versionName=raw4all-0.4`. A partir de 0.5.0 se utiliza MAJOR.MINOR.PATCH.
