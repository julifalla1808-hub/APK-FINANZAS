package com.julifalla.finanzas;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

/**
 * Lee las notificaciones que llegan al panel (apps de bancos, billeteras o SMS del banco)
 * y registra ingresos y gastos. No se conecta a ningún banco ni a internet.
 */
public class BankListenerService extends NotificationListenerService {

    // Notification.EXTRA_MESSAGES (API 24); se compila contra API 23.
    private static final String EXTRA_MESSAGES = "android.messages";

    /** Pide a Android que vuelva a conectar el servicio (API 24+), por si el sistema lo detuvo. */
    public static void requestRebind(Context c) {
        try {
            NotificationListenerService.class.getMethod("requestRebind", ComponentName.class)
                    .invoke(null, new ComponentName(c, BankListenerService.class));
        } catch (Exception e) {
            // Sin acceso a notificaciones o versión sin soporte: no hay nada que hacer.
        }
    }

    @Override
    public void onListenerConnected() {
        Db.get(this).prefs().edit().putLong(Db.PREF_CONNECTED_AT, System.currentTimeMillis()).apply();
        // Recupera los movimientos que sigan en el panel (por ejemplo, si el servicio estuvo detenido).
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification sbn : active) safeHandle(sbn);
            }
        } catch (RuntimeException e) {
            // getActiveNotifications puede fallar justo al conectar; las nuevas se leerán igual.
        }
    }

    // Sobrescribe onListenerDisconnected() de API 24 (se compila contra API 23, por eso sin @Override).
    public void onListenerDisconnected() {
        requestRebind(this);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Db.get(this).prefs().edit().putLong(Db.PREF_LAST_EVENT_AT, System.currentTimeMillis()).apply();
        safeHandle(sbn);
    }

    private void safeHandle(StatusBarNotification sbn) {
        try {
            handle(sbn);
        } catch (RuntimeException e) {
            // Una notificación con formato inesperado nunca debe tumbar el servicio.
            try {
                Db.get(this).log(sbn.getPackageName(), appLabel(sbn.getPackageName()), "", "Error: " + e);
            } catch (RuntimeException ignored) {
                // nada
            }
        }
    }

    private static final class Segment {
        final String title;
        final String text;
        final long time;
        final boolean keyByTextOnly;

        Segment(String title, String text, long time, boolean keyByTextOnly) {
            this.title = title;
            this.text = text;
            this.time = time;
            this.keyByTextOnly = keyByTextOnly;
        }
    }

    private void handle(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        Bundle extras = n.extras;
        if (extras == null) return;

        long when = n.when > 0 ? n.when : sbn.getPostTime();
        String title = str(extras.getCharSequence(Notification.EXTRA_TITLE));
        String bigTitle = str(extras.getCharSequence(Notification.EXTRA_TITLE_BIG));
        if (title.isEmpty()) title = bigTitle;

        List<Segment> segments = new ArrayList<Segment>();

        // Conversaciones (SMS del banco en Mensajes, chats de billeteras): un movimiento por mensaje.
        Parcelable[] messages = extras.getParcelableArray(EXTRA_MESSAGES);
        if (messages != null) {
            for (Parcelable p : messages) {
                if (!(p instanceof Bundle)) continue;
                Bundle m = (Bundle) p;
                String t = str(m.getCharSequence("text"));
                long time = m.getLong("time", when);
                if (!t.isEmpty()) segments.add(new Segment(title, t, time, false));
            }
        }

        if (segments.isEmpty()) {
            String text = str(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
            if (text.isEmpty()) text = str(extras.getCharSequence(Notification.EXTRA_TEXT));
            String sub = str(extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
            String summary = str(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));
            if (text.isEmpty() && n.tickerText != null) text = n.tickerText.toString();
            String full = join(text, sub, summary);
            if (!full.isEmpty()) segments.add(new Segment(title, full, when, false));

            CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null && NotificationParser.parse(title, full) == null) {
                for (CharSequence l : lines) {
                    String t = str(l);
                    if (!t.isEmpty()) segments.add(new Segment(title, t, when, true));
                }
            }
        }

        for (Segment s : segments) process(pkg, s);
    }

    private void process(String pkg, Segment s) {
        String raw = (s.title.isEmpty() ? "" : s.title + " | ") + s.text;
        // Solo se guarda en el registro lo que podría ser dinero (texto con números).
        if (!hasDigit(raw)) return;

        Db db = Db.get(this);
        String label = appLabel(pkg);
        String shown = raw.length() > 300 ? raw.substring(0, 300) + "…" : raw;
        String key = pkg + "|" + (s.keyByTextOnly ? 0 : s.time) + "|" + raw.hashCode();
        NotificationParser.Result r = NotificationParser.parse(s.title, s.text);
        if (r == null) {
            if (db.markSeen(key)) db.log(pkg, label, shown, "No reconocida como ingreso o gasto");
            return;
        }
        if (!db.touchApp(pkg, label)) {
            if (db.markSeen(key)) db.log(pkg, label, shown, "Ignorada: app desactivada en Ajustes");
            return;
        }
        if (!db.markSeen(key)) return; // misma notificación actualizada o releída al reconectar
        long ts = Math.min(s.time, System.currentTimeMillis());
        if (db.isDuplicate(r.amount, r.type, ts)) {
            db.log(pkg, label, shown, "Ignorada: mismo monto registrado hace menos de 3 min (posible SMS + app)");
            return;
        }
        db.addTx(r.amount, r.type, r.description, pkg, ts, Db.ORIGIN_AUTO, raw, 0);
        db.log(pkg, label, shown, "Registrada: " + (r.type == NotificationParser.INCOME ? "ingreso " : "gasto ")
                + Db.money(r.amount));
        FinanceWidget.refreshAll(this);
    }

    private static boolean hasDigit(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isDigit(s.charAt(i))) return true;
        return false;
    }

    private static String str(CharSequence c) {
        return c == null ? "" : c.toString().trim();
    }

    private static String join(String... parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty() || b.indexOf(p) >= 0) continue;
            if (b.length() > 0) b.append(". ");
            b.append(p);
        }
        return b.toString();
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }
}
