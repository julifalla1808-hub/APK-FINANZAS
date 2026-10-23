package com.julifalla.finanzas;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Almacenamiento local (SQLite + preferencias). Nada sale del teléfono. */
public final class Db extends SQLiteOpenHelper {

    public static final String ORIGIN_AUTO = "auto";
    public static final String ORIGIN_MANUAL = "manual";
    public static final String ORIGIN_FIXED = "fijo";

    public static final String PREF_BUDGET_DAY = "budget_day";
    public static final String PREF_BUDGET_WEEK = "budget_week";
    public static final String PREF_BUDGET_MONTH = "budget_month";
    public static final String PREF_AUTO_FIXED = "auto_fixed";
    public static final String PREF_CONNECTED_AT = "listener_connected_at";
    public static final String PREF_LAST_EVENT_AT = "listener_last_event_at";

    private static Db instance;

    public static synchronized Db get(Context c) {
        if (instance == null) instance = new Db(c.getApplicationContext());
        return instance;
    }

    private final Context context;

    private Db(Context c) {
        super(c, "finanzas.db", null, 2);
        context = c;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tx (id INTEGER PRIMARY KEY AUTOINCREMENT, amount REAL NOT NULL, type INTEGER NOT NULL,"
                + " description TEXT, source TEXT, ts INTEGER NOT NULL, origin TEXT, raw TEXT, fixed_id INTEGER)");
        db.execSQL("CREATE INDEX tx_ts ON tx(ts)");
        db.execSQL("CREATE TABLE fixed (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, amount REAL NOT NULL,"
                + " day INTEGER NOT NULL, active INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE apps (pkg TEXT PRIMARY KEY, label TEXT, enabled INTEGER NOT NULL DEFAULT 1, last_seen INTEGER)");
        createV2(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) createV2(db);
    }

    private static void createV2(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS seen (key TEXT PRIMARY KEY, ts INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS log (id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL,"
                + " pkg TEXT, label TEXT, text TEXT, outcome TEXT)");
    }

    // ---------------------------------------------------------------- diagnóstico

    /** Marca una notificación como procesada; devuelve false si ya se había procesado antes. */
    public boolean markSeen(String key) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        db.delete("seen", "ts<?", new String[]{String.valueOf(now - 7L * 24 * 3600 * 1000)});
        ContentValues v = new ContentValues();
        v.put("key", key);
        v.put("ts", now);
        return db.insertWithOnConflict("seen", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    public static final class LogEntry {
        public long ts;
        public String pkg;
        public String label;
        public String text;
        public String outcome;
    }

    public void log(String pkg, String label, String text, String outcome) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("ts", System.currentTimeMillis());
        v.put("pkg", pkg);
        v.put("label", label);
        v.put("text", text);
        v.put("outcome", outcome);
        db.insert("log", null, v);
        db.execSQL("DELETE FROM log WHERE id NOT IN (SELECT id FROM log ORDER BY id DESC LIMIT 80)");
    }

    public List<LogEntry> listLog() {
        List<LogEntry> out = new ArrayList<LogEntry>();
        Cursor c = getReadableDatabase().rawQuery("SELECT ts, pkg, label, text, outcome FROM log ORDER BY id DESC", null);
        try {
            while (c.moveToNext()) {
                LogEntry e = new LogEntry();
                e.ts = c.getLong(0);
                e.pkg = c.getString(1);
                e.label = c.getString(2);
                e.text = c.getString(3);
                e.outcome = c.getString(4);
                out.add(e);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public void clearLog() {
        getWritableDatabase().delete("log", null, null);
    }

    // ---------------------------------------------------------------- movimientos

    public static final class Tx {
        public long id;
        public double amount;
        public int type;
        public String description;
        public String source;
        public long ts;
        public String origin;
        public String raw;
        public long fixedId;
    }

    public long addTx(double amount, int type, String description, String source, long ts, String origin,
                      String raw, long fixedId) {
        ContentValues v = new ContentValues();
        v.put("amount", amount);
        v.put("type", type);
        v.put("description", description);
        v.put("source", source);
        v.put("ts", ts);
        v.put("origin", origin);
        v.put("raw", raw);
        v.put("fixed_id", fixedId);
        return getWritableDatabase().insert("tx", null, v);
    }

    public void updateTx(long id, double amount, int type, String description, long ts) {
        ContentValues v = new ContentValues();
        v.put("amount", amount);
        v.put("type", type);
        v.put("description", description);
        v.put("ts", ts);
        getWritableDatabase().update("tx", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteTx(long id) {
        getWritableDatabase().delete("tx", "id=?", new String[]{String.valueOf(id)});
    }

    public List<Tx> listTx(long from, long to) {
        List<Tx> out = new ArrayList<Tx>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, amount, type, description, source, ts, origin, raw, fixed_id FROM tx"
                        + " WHERE ts>=? AND ts<? ORDER BY ts DESC",
                new String[]{String.valueOf(from), String.valueOf(to)});
        try {
            while (c.moveToNext()) {
                Tx t = new Tx();
                t.id = c.getLong(0);
                t.amount = c.getDouble(1);
                t.type = c.getInt(2);
                t.description = c.getString(3);
                t.source = c.getString(4);
                t.ts = c.getLong(5);
                t.origin = c.getString(6);
                t.raw = c.getString(7);
                t.fixedId = c.isNull(8) ? 0 : c.getLong(8);
                out.add(t);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public double sum(int type, long from, long to) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount),0) FROM tx WHERE type=? AND ts>=? AND ts<?",
                new String[]{String.valueOf(type), String.valueOf(from), String.valueOf(to)});
        try {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        } finally {
            c.close();
        }
    }

    /**
     * Muchos bancos avisan el mismo movimiento por la app y por SMS: si ya se registró automáticamente
     * el mismo monto y tipo con menos de 3 minutos de diferencia, se trata como repetido.
     */
    public boolean isDuplicate(double amount, int type, long ts) {
        long w = 3 * 60 * 1000L;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM tx WHERE origin=? AND type=? AND ABS(amount-?)<0.01 AND ts>? AND ts<? LIMIT 1",
                new String[]{ORIGIN_AUTO, String.valueOf(type), String.valueOf(amount),
                        String.valueOf(ts - w), String.valueOf(ts + w)});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    // ---------------------------------------------------------------- gastos fijos

    public static final class Fixed {
        public long id;
        public String name;
        public double amount;
        public int day;
        public boolean active;
    }

    public long saveFixed(long id, String name, double amount, int day, boolean active) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("amount", amount);
        v.put("day", day);
        v.put("active", active ? 1 : 0);
        if (id > 0) {
            getWritableDatabase().update("fixed", v, "id=?", new String[]{String.valueOf(id)});
            return id;
        }
        return getWritableDatabase().insert("fixed", null, v);
    }

    public void deleteFixed(long id) {
        getWritableDatabase().delete("fixed", "id=?", new String[]{String.valueOf(id)});
    }

    public List<Fixed> listFixed() {
        List<Fixed> out = new ArrayList<Fixed>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id, name, amount, day, active FROM fixed ORDER BY day, name", null);
        try {
            while (c.moveToNext()) {
                Fixed f = new Fixed();
                f.id = c.getLong(0);
                f.name = c.getString(1);
                f.amount = c.getDouble(2);
                f.day = c.getInt(3);
                f.active = c.getInt(4) == 1;
                out.add(f);
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** Id del movimiento con el que se pagó el gasto fijo en el mes indicado, o 0 si está pendiente. */
    public long fixedPaymentTx(long fixedId, long monthFrom, long monthTo) {
        Cursor c = getReadableDatabase().rawQuery("SELECT id FROM tx WHERE fixed_id=? AND ts>=? AND ts<? LIMIT 1",
                new String[]{String.valueOf(fixedId), String.valueOf(monthFrom), String.valueOf(monthTo)});
        try {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } finally {
            c.close();
        }
    }

    public void payFixed(Fixed f, long ts) {
        addTx(f.amount, NotificationParser.EXPENSE, f.name, "Gasto fijo", ts, ORIGIN_FIXED, null, f.id);
    }

    /** Total de gastos fijos activos que aún no se han pagado este mes. */
    public double pendingFixedThisMonth() {
        long[] m = Periods.month(System.currentTimeMillis());
        double total = 0;
        for (Fixed f : listFixed()) {
            if (f.active && fixedPaymentTx(f.id, m[0], m[1]) == 0) total += f.amount;
        }
        return total;
    }

    /** Si el usuario lo activó, registra los gastos fijos cuyo día de cobro ya llegó este mes. */
    public boolean applyAutoFixed() {
        if (!prefs().getBoolean(PREF_AUTO_FIXED, false)) return false;
        Calendar now = Calendar.getInstance();
        int today = now.get(Calendar.DAY_OF_MONTH);
        int lastDay = now.getActualMaximum(Calendar.DAY_OF_MONTH);
        long[] m = Periods.month(now.getTimeInMillis());
        boolean changed = false;
        for (Fixed f : listFixed()) {
            int due = Math.min(f.day, lastDay);
            if (f.active && due <= today && fixedPaymentTx(f.id, m[0], m[1]) == 0) {
                Calendar when = Calendar.getInstance();
                when.set(Calendar.DAY_OF_MONTH, due);
                when.set(Calendar.HOUR_OF_DAY, 8);
                when.set(Calendar.MINUTE, 0);
                payFixed(f, Math.min(when.getTimeInMillis(), now.getTimeInMillis()));
                changed = true;
            }
        }
        return changed;
    }

    // ---------------------------------------------------------------- apps vigiladas

    public static final class App {
        public String pkg;
        public String label;
        public boolean enabled;
    }

    /** Registra una app que envió una notificación con dinero; devuelve si está habilitada. */
    public boolean touchApp(String pkg, String label) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT enabled FROM apps WHERE pkg=?", new String[]{pkg});
        boolean exists;
        boolean enabled = true;
        try {
            exists = c.moveToFirst();
            if (exists) enabled = c.getInt(0) == 1;
        } finally {
            c.close();
        }
        ContentValues v = new ContentValues();
        v.put("label", label);
        v.put("last_seen", System.currentTimeMillis());
        if (exists) {
            db.update("apps", v, "pkg=?", new String[]{pkg});
        } else {
            v.put("pkg", pkg);
            v.put("enabled", 1);
            db.insert("apps", null, v);
        }
        return enabled;
    }

    public void setAppEnabled(String pkg, boolean enabled) {
        ContentValues v = new ContentValues();
        v.put("enabled", enabled ? 1 : 0);
        getWritableDatabase().update("apps", v, "pkg=?", new String[]{pkg});
    }

    public List<App> listApps() {
        List<App> out = new ArrayList<App>();
        Cursor c = getReadableDatabase().rawQuery("SELECT pkg, label, enabled FROM apps ORDER BY label", null);
        try {
            while (c.moveToNext()) {
                App a = new App();
                a.pkg = c.getString(0);
                a.label = c.getString(1);
                a.enabled = c.getInt(2) == 1;
                out.add(a);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public void wipe() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("tx", null, null);
        db.delete("fixed", null, null);
        db.delete("apps", null, null);
        db.delete("seen", null, null);
        db.delete("log", null, null);
        prefs().edit().clear().apply();
    }

    // ---------------------------------------------------------------- presupuestos

    public SharedPreferences prefs() {
        return context.getSharedPreferences("finanzas", Context.MODE_PRIVATE);
    }

    public double budget(String key) {
        return Double.longBitsToDouble(prefs().getLong(key, Double.doubleToLongBits(0)));
    }

    public void setBudget(String key, double value) {
        prefs().edit().putLong(key, Double.doubleToLongBits(value)).apply();
    }

    // ---------------------------------------------------------------- formato

    public static String money(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "CO"));
        boolean cents = Math.abs(v - Math.rint(v)) > 0.004;
        nf.setMinimumFractionDigits(cents ? 2 : 0);
        nf.setMaximumFractionDigits(cents ? 2 : 0);
        return (v < 0 ? "-$ " : "$ ") + nf.format(Math.abs(v));
    }
}
