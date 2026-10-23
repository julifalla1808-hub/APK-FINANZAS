package com.julifalla.finanzas;

import android.app.Notification;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Lee las notificaciones que llegan al panel (apps de bancos, billeteras o SMS del banco)
 * y registra ingresos y gastos. No se conecta a ningún banco ni a internet.
 */
public class BankListenerService extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            handle(sbn);
        } catch (RuntimeException e) {
            // Una notificación con formato inesperado nunca debe tumbar el servicio.
        }
    }

    private void handle(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        Bundle extras = n.extras;
        if (extras == null) return;
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (text == null || text.length() == 0) text = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (text == null) return;

        String t = title == null ? "" : title.toString();
        String body = text.toString();
        NotificationParser.Result r = NotificationParser.parse(t, body);
        if (r == null) return;

        Db db = Db.get(this);
        if (!db.touchApp(pkg, appLabel(pkg))) return;

        String raw = t + " | " + body;
        long now = System.currentTimeMillis();
        if (db.isDuplicate(raw, pkg, now)) return;
        db.addTx(r.amount, r.type, r.description, pkg, now, Db.ORIGIN_AUTO, raw, 0);
        FinanceWidget.refreshAll(this);
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
