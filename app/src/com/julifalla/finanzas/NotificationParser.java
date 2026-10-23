package com.julifalla.finanzas;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrae movimientos (ingreso / gasto) del texto de una notificación bancaria.
 * No depende de Android para poder probarse en la JVM.
 */
public final class NotificationParser {

    public static final int INCOME = 1;
    public static final int EXPENSE = -1;

    public static final class Result {
        public final double amount;
        public final int type;
        public final String description;

        Result(double amount, int type, String description) {
            this.amount = amount;
            this.type = type;
            this.description = description;
        }

        @Override
        public String toString() {
            return (type == INCOME ? "INGRESO " : "GASTO ") + amount + " · " + description;
        }
    }

    // Palabras que indican dinero que entra a la cuenta.
    private static final String[] INCOME_WORDS = {
            "recibiste", "recibes", "te enviaron", "te envio", "te transfirio", "te consigno", "te deposito",
            "te abono", "te pago", "llego plata", "te llego plata", "llegaron", "entro", "entraron", "te consignaron", "te transfirieron", "te depositaron",
            "te abonaron", "te llego", "te llegaron", "has recibido", "recibio", "recibido", "recibida",
            "consignacion", "consignaron", "abono", "abonamos", "abonado", "abonaron", "deposito",
            "depositaron", "ingreso", "nomina", "reembolso", "devolucion", "acreditado", "acreditamos",
            "acredito", "transferencia recibida", "pago recibido", "giro recibido", "pago de nomina", "pago nomina", "te pagaron", "entrada de dinero"
    };

    // Palabras que indican dinero que sale de la cuenta.
    private static final String[] EXPENSE_WORDS = {
            "compraste", "compra", "pagaste", "pago", "pagos", "retiraste", "retiro", "transferiste",
            "enviaste", "envio", "debito", "debitamos", "debitado", "debitaron", "cargo", "cobro",
            "cobramos", "avance", "transferencia a", "transferencia exitosa", "giro", "recarga",
            "salida de dinero", "realizaste"
    };

    // Si aparecen, la operación no se realizó: no se registra nada.
    private static final String[] IGNORE_WORDS = {
            "rechazad", "declinad", "fallid", "no exitos", "no fue posible", "no se pudo", "cancelad",
            "codigo de verificacion", "clave dinamica", "codigo de seguridad"
    };

    // Montos precedidos por estas palabras no son el movimiento sino un saldo o cupo.
    private static final String[] BALANCE_WORDS = {"saldo", "disponible", "cupo"};

    private static final String NUM = "([0-9]{1,3}(?:[.,][0-9]{3})+(?:[.,][0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)";
    private static final Pattern CURRENCY_FIRST = Pattern.compile(
            "(?:\\$|cop|usd|us\\$|eur|€)\\s?" + NUM);
    private static final Pattern CURRENCY_AFTER = Pattern.compile(
            NUM + "\\s?(?:pesos|cop|usd|dolares|euros)\\b");
    // Último recurso: número con separador de miles sin símbolo de moneda ("Recibiste 50.000 de Juan").
    private static final Pattern BARE_THOUSANDS = Pattern.compile(
            "(?<![0-9*/:.,])([0-9]{1,3}(?:[.,][0-9]{3})+(?:[.,][0-9]{1,2})?)(?![0-9/:])");
    private static final Pattern MERCHANT = Pattern.compile(
            "(?i)\\b(?:en|a|de|desde)\\s+([\\p{L}0-9][\\p{L}0-9 &*'\\-]{1,38}[\\p{L}0-9])");

    private NotificationParser() {
    }

    /** Devuelve null cuando el texto no parece un movimiento de dinero. */
    public static Result parse(String title, String text) {
        String full = ((title == null ? "" : title) + ". " + (text == null ? "" : text)).trim();
        String norm = normalize(full);
        if (norm.isEmpty()) return null;
        for (String w : IGNORE_WORDS) {
            if (norm.contains(w)) return null;
        }

        int type = classify(norm);
        if (type == 0) return null;

        int[] span = findAmountSpan(norm);
        if (span == null) return null;
        double amount = parseNumber(norm.substring(span[0], span[1]));
        if (amount <= 0) return null;

        String desc = describe(full, span[1], title);
        return new Result(amount, type, desc);
    }

    static int classify(String norm) {
        int bestPos = Integer.MAX_VALUE;
        int bestLen = 0;
        int bestType = 0;
        for (String w : INCOME_WORDS) {
            int p = indexOfWord(norm, w);
            if (p >= 0 && (p < bestPos || (p == bestPos && w.length() > bestLen))) {
                bestPos = p;
                bestLen = w.length();
                bestType = INCOME;
            }
        }
        for (String w : EXPENSE_WORDS) {
            int p = indexOfWord(norm, w);
            if (p >= 0 && (p < bestPos || (p == bestPos && w.length() > bestLen))) {
                bestPos = p;
                bestLen = w.length();
                bestType = EXPENSE;
            }
        }
        return bestType;
    }

    /** Busca la palabra al inicio de un término (evita que "pago" coincida dentro de "apagon"). */
    private static int indexOfWord(String s, String w) {
        int from = 0;
        while (true) {
            int p = s.indexOf(w, from);
            if (p < 0) return -1;
            if (p == 0 || !Character.isLetter(s.charAt(p - 1))) return p;
            from = p + 1;
        }
    }

    /** Posición [inicio, fin) del número del monto del movimiento dentro del texto normalizado. */
    static int[] findAmountSpan(String norm) {
        int[] best = null;
        for (Pattern p : new Pattern[]{CURRENCY_FIRST, CURRENCY_AFTER}) {
            Matcher m = p.matcher(norm);
            while (m.find()) {
                if (isBalance(norm, m.start())) continue;
                if (best == null || m.start(1) < best[0]) {
                    best = new int[]{m.start(1), m.end(1)};
                }
                break;
            }
        }
        if (best == null) {
            Matcher m = BARE_THOUSANDS.matcher(norm);
            while (m.find()) {
                if (isBalance(norm, m.start())) continue;
                return new int[]{m.start(1), m.end(1)};
            }
        }
        return best;
    }

    private static boolean isBalance(String norm, int pos) {
        String before = norm.substring(Math.max(0, pos - 22), pos);
        for (String w : BALANCE_WORDS) {
            if (before.contains(w)) return true;
        }
        return false;
    }

    /**
     * Convierte "1.234.567,89", "1,234,567.89", "50.000", "50,5" o "1500" a número,
     * adivinando cuál es el separador decimal.
     */
    public static double parseNumber(String raw) {
        if (raw == null) return 0;
        String s = raw.trim().replace(" ", "").replace("$", "");
        if (s.isEmpty()) return 0;
        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');
        if (lastDot >= 0 && lastComma >= 0) {
            char dec = lastDot > lastComma ? '.' : ',';
            char th = dec == '.' ? ',' : '.';
            s = s.replace(String.valueOf(th), "").replace(dec, '.');
        } else if (lastDot >= 0 || lastComma >= 0) {
            char sep = lastDot >= 0 ? '.' : ',';
            int count = 0;
            for (int i = 0; i < s.length(); i++) if (s.charAt(i) == sep) count++;
            int digitsAfter = s.length() - 1 - s.lastIndexOf(sep);
            if (count > 1 || digitsAfter == 3) {
                s = s.replace(String.valueOf(sep), "");
            } else {
                s = s.replace(sep, '.');
            }
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String describe(String full, int amountEndInNorm, String title) {
        // normalize() conserva la longitud, así que las posiciones coinciden con el texto original.
        String merchant = null;
        if (amountEndInNorm <= full.length()) merchant = findMerchant(full.substring(amountEndInNorm));
        if (merchant == null) merchant = findMerchant(full);
        if (merchant != null) return merchant;
        String t = title == null ? "" : title.trim();
        if (!t.isEmpty()) return t.length() > 40 ? t.substring(0, 40) : t;
        return full.length() > 40 ? full.substring(0, 40) : full;
    }

    private static final String[] MERCHANT_STOP = {" con ", " el ", " por ", " en tu ", " en su ", " en la ", " desde ", " a las ", " ref", " cta", " para "};
    private static final String[] MERCHANT_REJECT = {"tu ", "su ", "cuenta", "la cuenta", "tarjeta", "cta", "t.",
            "nequi", "daviplata", "ahorros", "corriente", "pesos", "dinero", "plata"};

    private static String findMerchant(String s) {
        Matcher m = MERCHANT.matcher(s);
        while (m.find()) {
            String merchant = m.group(1).trim();
            String low = normalize(merchant);
            for (String stop : MERCHANT_STOP) {
                int p = (" " + low + " ").indexOf(stop);
                if (p > 0 && p - 1 <= low.length()) {
                    merchant = merchant.substring(0, p - 1).trim();
                    low = low.substring(0, p - 1).trim();
                }
            }
            boolean reject = merchant.length() < 2;
            for (String r : MERCHANT_REJECT) {
                if (low.startsWith(r)) reject = true;
            }
            if (!reject) return merchant;
        }
        return null;
    }

    /** Minúsculas y sin tildes, conservando la longitud del texto. */
    static String normalize(String s) {
        StringBuilder out = new StringBuilder(s.length());
        String lower = s.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            String d = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
            char base = d.isEmpty() ? c : d.charAt(0);
            out.append(Character.isWhitespace(base) ? ' ' : base);
        }
        return out.toString();
    }
}
