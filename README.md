# Mis Finanzas (APK)

App Android para llevar tus finanzas **leyendo las notificaciones de tus bancos**, sin conectarse a ningún banco
y sin permiso de internet. Todo queda guardado solo en el teléfono.

**Descarga:** [`MisFinanzas.apk`](MisFinanzas.apk) (Android 7.0 o superior).

## Funciones

- **Total de ingresos** y **total gastado** (hoy, semana o mes) detectados automáticamente desde el panel de notificaciones
  (apps de bancos, billeteras como Nequi/Daviplata o SMS del banco).
- **Presupuesto editable**: mensual, semanal y diario, con barra de progreso y botón para calcular semanal/diario a partir del mensual.
- **Gastos fijos**: arriendo, servicios, suscripciones… con día de cobro, estado pagado/pendiente y opción de registrarlos automáticamente.
- **Movimientos**: historial por mes; puedes agregar, corregir o eliminar cualquier movimiento.
- **Widget** de pantalla de inicio: ingresos y gastos del mes, presupuesto restante de hoy/semana/mes, gastos fijos pendientes
  y botones rápidos «+ Ingreso» / «+ Gasto».
- **Probador**: pega el texto de una notificación para ver cómo la interpreta la app.

## Instalación

1. Copia `MisFinanzas.apk` al teléfono y ábrelo (acepta «instalar apps de origen desconocido»).
2. Abre **Mis Finanzas → Ajustes → Dar acceso** y activa *Mis Finanzas* en «Acceso a notificaciones».
   - En Android 13+ puede aparecer «Configuración restringida». Ve a **Información de la app → menú ⋮ →
     Permitir configuración restringida** y vuelve a activar el acceso.
3. Mantén pulsada la pantalla de inicio → **Widgets → Mis Finanzas** para agregar el widget.
4. En **Ajustes → Apps detectadas** desactiva las apps que no sean de tus bancos si alguna genera movimientos falsos.

## Compilar

Sin Gradle, con las herramientas de Android de Ubuntu:

```bash
sudo apt-get install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23
./build.sh
```

El script ejecuta las pruebas del lector de notificaciones (`app/test/ParserTest.java`) y firma con `keystore/finanzas.jks`
(guárdalo: las actualizaciones deben firmarse con la misma llave para instalarse encima de la versión anterior).
