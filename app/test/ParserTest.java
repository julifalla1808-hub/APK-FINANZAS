import com.julifalla.finanzas.NotificationParser;
import com.julifalla.finanzas.NotificationParser.Result;

public class ParserTest {
    static int fails = 0;

    static void check(String title, String text, Integer type, Double amount) {
        Result r = NotificationParser.parse(title, text);
        boolean ok;
        if (type == null) ok = r == null;
        else ok = r != null && r.type == type && Math.abs(r.amount - amount) < 0.001;
        if (!ok) fails++;
        System.out.println((ok ? "OK   " : "FAIL ") + text + "  ->  " + r);
    }

    public static void main(String[] a) {
        int I = NotificationParser.INCOME, E = NotificationParser.EXPENSE;
        check("Bancolombia", "Bancolombia: Compraste $85.900,00 en EXITO CALLE 80 con tu T.Deb *1234, el 22/10/2026 a las 14:05.", E, 85900.0);
        check("Nequi", "¡Te enviaron plata! Juan Perez te envió $50.000 a tu Nequi.", I, 50000.0);
        check("Nequi", "Recibiste $1.250.000 de EMPRESA SAS", I, 1250000.0);
        check("Davivienda", "Davivienda le informa: Retiro por $200,000.00 en cajero. Saldo disponible $1,500,000.00", E, 200000.0);
        check("BBVA", "Pago de nómina recibido por $3.500.000", I, 3500000.0);
        check("Banco", "Transferiste $120.000 a la cuenta *4455", E, 120000.0);
        check("Banco", "Tu saldo disponible es $2.000.000. Compra por $45.000 en RAPPI", E, 45000.0);
        check("Nu", "Pagaste 32.500 pesos en Uber", E, 32500.0);
        check("Banco", "Compra rechazada por $50.000 en AMAZON", null, null);
        check("Banco", "Tu código de verificación es 123456", null, null);
        check("Banco", "Bienvenido a tu app", null, null);
        check("PayPal", "You received USD 25.50", null, null);
        check("Daviplata", "Te llegó un abono de $15.000", I, 15000.0);
        check("Bancolombia", "Bancolombia le informa Pago de Servicio de ENEL por $180.450,00 desde cta *1111", E, 180450.0);
        check("Banco", "Consignación recibida por $ 300.000 en su cuenta de ahorros", I, 300000.0);
        check("Nequi", "Juan Perez te envió $50.000", I, 50000.0);
        check("", "Recibiste 50.000 de JUAN PEREZ en tu cuenta *1234", I, 50000.0);
        check("Bancolombia", "Bancolombia: Recibiste una transferencia por $1,200,000 de PEDRO en tu cuenta *9876 el 23/10/2026 10:15", I, 1200000.0);
        check("Daviplata", "¡Te llegó plata! Tienes 80.000 pesos más en tu DaviPlata", I, 80000.0);
        check("Banco", "Enviaste $30.000 a MARIA", E, 30000.0);
        check("Banco", "Recordatorio: tu cita es el 23/10/2026 a las 10:15", null, null);
        System.out.println("parseNumber 1,5=" + NotificationParser.parseNumber("1,5") + " 1500=" + NotificationParser.parseNumber("1500") + " 1.234,56=" + NotificationParser.parseNumber("1.234,56"));
        System.out.println(fails == 0 ? "TODO OK" : fails + " FALLOS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
