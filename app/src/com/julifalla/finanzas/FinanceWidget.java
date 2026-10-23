package com.julifalla.finanzas;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Widget de pantalla de inicio: ingresos, gastos y presupuesto restante. */
public class FinanceWidget extends AppWidgetProvider {

    // PendingIntent.FLAG_IMMUTABLE (API 23); obligatorio desde Android 12.
    private static final int FLAG_IMMUTABLE = 0x04000000;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        Db.get(context).applyAutoFixed();
        update(context, manager, ids);
    }

    public static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, FinanceWidget.class));
        if (ids != null && ids.length > 0) update(context, manager, ids);
    }

    private static void update(Context context, AppWidgetManager manager, int[] ids) {
        Db db = Db.get(context);
        long now = System.currentTimeMillis();
        long[] d = Periods.day(now);
        long[] w = Periods.week(now);
        long[] m = Periods.month(now);
        double income = db.sum(NotificationParser.INCOME, m[0], m[1]);
        double spentMonth = db.sum(NotificationParser.EXPENSE, m[0], m[1]);
        double spentWeek = db.sum(NotificationParser.EXPENSE, w[0], w[1]);
        double spentDay = db.sum(NotificationParser.EXPENSE, d[0], d[1]);
        double bDay = db.budget(Db.PREF_BUDGET_DAY);
        double bWeek = db.budget(Db.PREF_BUDGET_WEEK);
        double bMonth = db.budget(Db.PREF_BUDGET_MONTH);
        double pending = db.pendingFixedThisMonth();

        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget);
        v.setTextViewText(R.id.w_income, Db.money(income));
        v.setTextViewText(R.id.w_expense, Db.money(spentMonth));
        v.setTextViewText(R.id.w_day, line("Hoy", spentDay, bDay));
        v.setProgressBar(R.id.w_day_bar, 100, percent(spentDay, bDay), false);
        v.setTextViewText(R.id.w_week, line("Semana", spentWeek, bWeek));
        v.setTextViewText(R.id.w_month, line("Mes", spentMonth, bMonth));
        v.setTextViewText(R.id.w_fixed, pending > 0 ? "Gastos fijos pendientes: " + Db.money(pending) : "");

        v.setOnClickPendingIntent(R.id.w_root, open(context, 10, null));
        v.setOnClickPendingIntent(R.id.w_add_income, open(context, 11, MainActivity.ACTION_ADD_INCOME));
        v.setOnClickPendingIntent(R.id.w_add_expense, open(context, 12, MainActivity.ACTION_ADD_EXPENSE));
        manager.updateAppWidget(ids, v);
    }

    static String line(String label, double spent, double budget) {
        if (budget <= 0) return label + ": gastado " + Db.money(spent);
        double left = budget - spent;
        return label + ": " + (left >= 0 ? "quedan " + Db.money(left) : "excedido " + Db.money(-left))
                + " de " + Db.money(budget);
    }

    static int percent(double spent, double budget) {
        if (budget <= 0) return 0;
        return (int) Math.min(100, Math.round(spent * 100 / budget));
    }

    private static PendingIntent open(Context context, int code, String action) {
        Intent i = new Intent(context, MainActivity.class);
        if (action != null) i.setAction(action);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE);
    }
}
