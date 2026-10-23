package com.julifalla.finanzas;

import java.util.Calendar;

/** Rangos [inicio, fin) en milisegundos para día, semana (lunes a domingo) y mes. */
public final class Periods {

    private Periods() {
    }

    private static Calendar startOfDay(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    public static long[] day(long ts) {
        Calendar c = startOfDay(ts);
        long from = c.getTimeInMillis();
        c.add(Calendar.DAY_OF_MONTH, 1);
        return new long[]{from, c.getTimeInMillis()};
    }

    public static long[] week(long ts) {
        Calendar c = startOfDay(ts);
        int dow = c.get(Calendar.DAY_OF_WEEK); // domingo = 1
        int back = (dow + 5) % 7; // días desde el lunes
        c.add(Calendar.DAY_OF_MONTH, -back);
        long from = c.getTimeInMillis();
        c.add(Calendar.DAY_OF_MONTH, 7);
        return new long[]{from, c.getTimeInMillis()};
    }

    public static long[] month(long ts) {
        Calendar c = startOfDay(ts);
        c.set(Calendar.DAY_OF_MONTH, 1);
        long from = c.getTimeInMillis();
        c.add(Calendar.MONTH, 1);
        return new long[]{from, c.getTimeInMillis()};
    }

    /** Desplaza el mes de ts en delta meses (útil para navegar el historial). */
    public static long shiftMonth(long ts, int delta) {
        Calendar c = startOfDay(ts);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.add(Calendar.MONTH, delta);
        return c.getTimeInMillis();
    }
}
