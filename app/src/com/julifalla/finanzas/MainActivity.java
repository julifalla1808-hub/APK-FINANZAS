package com.julifalla.finanzas;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    public static final String ACTION_ADD_INCOME = "com.julifalla.finanzas.ADD_INCOME";
    public static final String ACTION_ADD_EXPENSE = "com.julifalla.finanzas.ADD_EXPENSE";

    private static final int C_PRIMARY = 0xFF00695C;
    private static final int C_INCOME = 0xFF2E7D32;
    private static final int C_EXPENSE = 0xFFC62828;
    private static final int C_WARN = 0xFFEF6C00;
    private static final int C_BG = 0xFFF2F4F5;
    private static final int C_TEXT = 0xFF212121;
    private static final int C_MUTED = 0xFF757575;

    private static final String[] TABS = {"Resumen", "Movimientos", "Presupuesto", "Fijos", "Ajustes"};
    private static final String[] PERIODS = {"Hoy", "Semana", "Mes"};

    private Db db;
    private LinearLayout content;
    private LinearLayout nav;
    private TextView headerSub;
    private int tab = 0;
    private int period = 2;
    private int monthOffset = 0;

    private final SimpleDateFormat dateFmt = new SimpleDateFormat("d MMM yyyy · HH:mm", new Locale("es", "CO"));
    private final SimpleDateFormat monthFmt = new SimpleDateFormat("MMMM yyyy", new Locale("es", "CO"));

    // ================================================================= ciclo de vida

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        db = Db.get(this);
        if (state != null) {
            tab = state.getInt("tab", 0);
            period = state.getInt("period", 2);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(C_PRIMARY);
        header.setPadding(dp(20), dp(18), dp(20), dp(14));
        TextView title = text("Mis Finanzas", 22, Color.WHITE, true);
        header.addView(title);
        headerSub = text("", 13, 0xCCFFFFFF, false);
        header.addView(headerSub);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(dp(8));
        root.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        setContentView(root);
        handleIntent(getIntent());
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
        out.putInt("period", period);
    }

    @Override
    protected void onResume() {
        super.onResume();
        db.applyAutoFixed();
        render();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (ACTION_ADD_INCOME.equals(intent.getAction())) {
            editTx(null, NotificationParser.INCOME);
        } else if (ACTION_ADD_EXPENSE.equals(intent.getAction())) {
            editTx(null, NotificationParser.EXPENSE);
        }
        intent.setAction(null);
    }

    private void changed() {
        FinanceWidget.refreshAll(this);
        render();
    }

    private void render() {
        renderNav();
        content.removeAllViews();
        headerSub.setText(hasNotificationAccess()
                ? "Leyendo notificaciones bancarias ✓"
                : "Sin acceso a notificaciones · ve a Ajustes");
        switch (tab) {
            case 0: renderSummary(); break;
            case 1: renderMovements(); break;
            case 2: renderBudget(); break;
            case 3: renderFixed(); break;
            default: renderSettings(); break;
        }
    }

    private void renderNav() {
        nav.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            TextView t = text(TABS[i], 12, i == tab ? C_PRIMARY : C_MUTED, i == tab);
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tab = idx;
                    render();
                }
            });
            nav.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
    }

    // ================================================================= Resumen

    private void renderSummary() {
        long now = System.currentTimeMillis();

        if (!hasNotificationAccess()) {
            LinearLayout warn = card();
            warn.addView(text("Activa la lectura de notificaciones", 16, C_WARN, true));
            warn.addView(text("La app registra ingresos y gastos leyendo las notificaciones de tus bancos. "
                    + "No se conecta a ningún banco.", 13, C_TEXT, false));
            warn.addView(button("Dar acceso", C_WARN, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openNotificationAccess();
                }
            }));
            content.addView(warn);
        }

        content.addView(segmented(PERIODS, period, new Pick() {
            @Override
            public void pick(int i) {
                period = i;
                render();
            }
        }));

        long[] r = period == 0 ? Periods.day(now) : period == 1 ? Periods.week(now) : Periods.month(now);
        double income = db.sum(NotificationParser.INCOME, r[0], r[1]);
        double spent = db.sum(NotificationParser.EXPENSE, r[0], r[1]);

        LinearLayout totals = card();
        LinearLayout row = hrow();
        row.addView(stat("Total ingresos", Db.money(income), C_INCOME), weight());
        row.addView(stat("Total gastado", Db.money(spent), C_EXPENSE), weight());
        totals.addView(row);
        double bal = income - spent;
        TextView balance = text("Balance: " + Db.money(bal), 15, bal >= 0 ? C_INCOME : C_EXPENSE, true);
        balance.setPadding(0, dp(10), 0, 0);
        totals.addView(balance);
        LinearLayout actions = hrow();
        actions.setPadding(0, dp(10), 0, 0);
        actions.addView(button("+ Ingreso", C_INCOME, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editTx(null, NotificationParser.INCOME);
            }
        }), weightGap());
        actions.addView(button("+ Gasto", C_EXPENSE, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editTx(null, NotificationParser.EXPENSE);
            }
        }), weightGap());
        totals.addView(actions);
        content.addView(totals);

        LinearLayout budgets = card();
        budgets.addView(text("Presupuesto", 16, C_TEXT, true));
        addBudgetRow(budgets, "Diario", Periods.day(now), db.budget(Db.PREF_BUDGET_DAY));
        addBudgetRow(budgets, "Semanal", Periods.week(now), db.budget(Db.PREF_BUDGET_WEEK));
        addBudgetRow(budgets, "Mensual", Periods.month(now), db.budget(Db.PREF_BUDGET_MONTH));
        budgets.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tab = 2;
                render();
            }
        });
        content.addView(budgets);

        List<Db.Fixed> fixed = db.listFixed();
        if (!fixed.isEmpty()) {
            long[] m = Periods.month(now);
            double total = 0, paid = 0;
            for (Db.Fixed f : fixed) {
                if (!f.active) continue;
                total += f.amount;
                if (db.fixedPaymentTx(f.id, m[0], m[1]) != 0) paid += f.amount;
            }
            LinearLayout fc = card();
            fc.addView(text("Gastos fijos del mes", 16, C_TEXT, true));
            fc.addView(text("Total " + Db.money(total) + " · pagado " + Db.money(paid), 13, C_MUTED, false));
            fc.addView(text("Pendiente: " + Db.money(total - paid), 15, total - paid > 0 ? C_WARN : C_INCOME, true));
            fc.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tab = 3;
                    render();
                }
            });
            content.addView(fc);
        }

        List<Db.Tx> recent = db.listTx(r[0], r[1]);
        LinearLayout last = card();
        last.addView(text("Últimos movimientos", 16, C_TEXT, true));
        if (recent.isEmpty()) {
            last.addView(text("Aún no hay movimientos en este periodo.", 13, C_MUTED, false));
        }
        for (int i = 0; i < Math.min(6, recent.size()); i++) last.addView(txRow(recent.get(i)));
        content.addView(last);
    }

    private void addBudgetRow(LinearLayout parent, String label, long[] range, double budget) {
        double spent = db.sum(NotificationParser.EXPENSE, range[0], range[1]);
        LinearLayout row = hrow();
        row.setPadding(0, dp(10), 0, dp(2));
        row.addView(text(label, 14, C_TEXT, true), weight());
        String right;
        int color;
        if (budget <= 0) {
            right = "gastado " + Db.money(spent) + " · sin presupuesto";
            color = C_MUTED;
        } else if (spent <= budget) {
            right = "quedan " + Db.money(budget - spent) + " de " + Db.money(budget);
            color = spent > budget * 0.8 ? C_WARN : C_INCOME;
        } else {
            right = "excedido " + Db.money(spent - budget);
            color = C_EXPENSE;
        }
        row.addView(text(right, 13, color, false));
        parent.addView(row);
        if (budget > 0) parent.addView(bar(FinanceWidget.percent(spent, budget), color));
    }

    // ================================================================= Movimientos

    private void renderMovements() {
        long ref = Periods.shiftMonth(System.currentTimeMillis(), monthOffset);
        long[] m = Periods.month(ref);

        LinearLayout navRow = hrow();
        navRow.setGravity(Gravity.CENTER_VERTICAL);
        navRow.addView(smallButton("‹", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                monthOffset--;
                render();
            }
        }));
        TextView month = text(capitalize(monthFmt.format(m[0])), 16, C_TEXT, true);
        month.setGravity(Gravity.CENTER);
        navRow.addView(month, weight());
        navRow.addView(smallButton("›", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (monthOffset < 0) monthOffset++;
                render();
            }
        }));
        content.addView(navRow);

        double income = db.sum(NotificationParser.INCOME, m[0], m[1]);
        double spent = db.sum(NotificationParser.EXPENSE, m[0], m[1]);
        LinearLayout totals = card();
        LinearLayout row = hrow();
        row.addView(stat("Ingresos", Db.money(income), C_INCOME), weight());
        row.addView(stat("Gastos", Db.money(spent), C_EXPENSE), weight());
        totals.addView(row);
        content.addView(totals);

        LinearLayout actions = hrow();
        actions.addView(button("+ Ingreso", C_INCOME, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editTx(null, NotificationParser.INCOME);
            }
        }), weightGap());
        actions.addView(button("+ Gasto", C_EXPENSE, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editTx(null, NotificationParser.EXPENSE);
            }
        }), weightGap());
        content.addView(actions);

        List<Db.Tx> list = db.listTx(m[0], m[1]);
        LinearLayout c = card();
        if (list.isEmpty()) c.addView(text("No hay movimientos este mes.", 13, C_MUTED, false));
        for (Db.Tx t : list) c.addView(txRow(t));
        c.addView(text("Toca un movimiento para editarlo o eliminarlo.", 11, C_MUTED, false));
        content.addView(c);
    }

    private View txRow(final Db.Tx t) {
        LinearLayout row = hrow();
        row.setPadding(0, dp(10), 0, dp(10));
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        String d = t.description == null || t.description.isEmpty() ? "(sin descripción)" : t.description;
        left.addView(text(d, 14, C_TEXT, false));
        String origin = Db.ORIGIN_AUTO.equals(t.origin) ? "notificación"
                : Db.ORIGIN_FIXED.equals(t.origin) ? "gasto fijo" : "manual";
        left.addView(text(dateFmt.format(t.ts) + " · " + origin, 11, C_MUTED, false));
        row.addView(left, weight());
        boolean in = t.type == NotificationParser.INCOME;
        row.addView(text((in ? "+" : "−") + Db.money(t.amount), 15, in ? C_INCOME : C_EXPENSE, true));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editTx(t, t.type);
            }
        });
        return row;
    }

    private void editTx(final Db.Tx existing, int defaultType) {
        LinearLayout form = form();
        final RadioGroup type = new RadioGroup(this);
        type.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton rIn = new RadioButton(this);
        rIn.setText("Ingreso");
        rIn.setId(View.generateViewId());
        final RadioButton rOut = new RadioButton(this);
        rOut.setText("Gasto");
        rOut.setId(View.generateViewId());
        type.addView(rIn);
        type.addView(rOut);
        int ty = existing != null ? existing.type : defaultType;
        type.check(ty == NotificationParser.INCOME ? rIn.getId() : rOut.getId());
        form.addView(type);

        final EditText amount = input("Monto", true);
        final EditText desc = input("Descripción", false);
        if (existing != null) {
            amount.setText(plain(existing.amount));
            desc.setText(existing.description);
        }
        form.addView(amount);
        form.addView(desc);

        final Calendar when = Calendar.getInstance();
        if (existing != null) when.setTimeInMillis(existing.ts);
        final Button date = new Button(this);
        date.setText("Fecha: " + dateFmt.format(when.getTimeInMillis()));
        date.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new DatePickerDialog(MainActivity.this, new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker p, int y, int mo, int d) {
                        when.set(y, mo, d);
                        date.setText("Fecha: " + dateFmt.format(when.getTimeInMillis()));
                    }
                }, when.get(Calendar.YEAR), when.get(Calendar.MONTH), when.get(Calendar.DAY_OF_MONTH)).show();
            }
        });
        form.addView(date);

        if (existing != null && existing.raw != null) {
            TextView raw = text("Notificación original:\n" + existing.raw, 11, C_MUTED, false);
            raw.setPadding(0, dp(8), 0, 0);
            form.addView(raw);
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Nuevo movimiento" : "Editar movimiento")
                .setView(form)
                .setPositiveButton("Guardar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        double a = NotificationParser.parseNumber(amount.getText().toString());
                        if (a <= 0) {
                            toast("Escribe un monto válido");
                            return;
                        }
                        int t = type.getCheckedRadioButtonId() == rIn.getId()
                                ? NotificationParser.INCOME : NotificationParser.EXPENSE;
                        String ds = desc.getText().toString().trim();
                        if (existing == null) {
                            db.addTx(a, t, ds, "Manual", when.getTimeInMillis(), Db.ORIGIN_MANUAL, null, 0);
                        } else {
                            db.updateTx(existing.id, a, t, ds, when.getTimeInMillis());
                        }
                        changed();
                    }
                })
                .setNegativeButton("Cancelar", null);
        if (existing != null) {
            b.setNeutralButton("Eliminar", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    db.deleteTx(existing.id);
                    changed();
                }
            });
        }
        b.show();
    }

    // ================================================================= Presupuesto

    private void renderBudget() {
        long now = System.currentTimeMillis();
        LinearLayout c = card();
        c.addView(text("Edita tu presupuesto", 16, C_TEXT, true));
        c.addView(text("Deja en 0 el que no quieras usar.", 12, C_MUTED, false));

        final EditText month = labeledInput(c, "Mensual", db.budget(Db.PREF_BUDGET_MONTH));
        final EditText week = labeledInput(c, "Semanal", db.budget(Db.PREF_BUDGET_WEEK));
        final EditText day = labeledInput(c, "Diario", db.budget(Db.PREF_BUDGET_DAY));

        LinearLayout actions = hrow();
        actions.setPadding(0, dp(12), 0, 0);
        actions.addView(button("Guardar", C_PRIMARY, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                db.setBudget(Db.PREF_BUDGET_MONTH, NotificationParser.parseNumber(month.getText().toString()));
                db.setBudget(Db.PREF_BUDGET_WEEK, NotificationParser.parseNumber(week.getText().toString()));
                db.setBudget(Db.PREF_BUDGET_DAY, NotificationParser.parseNumber(day.getText().toString()));
                toast("Presupuesto guardado");
                changed();
            }
        }), weightGap());
        actions.addView(button("Calcular desde mensual", 0xFF546E7A, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                double m = NotificationParser.parseNumber(month.getText().toString());
                if (m <= 0) {
                    toast("Primero escribe el presupuesto mensual");
                    return;
                }
                int days = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH);
                week.setText(plain(Math.round(m * 7 / days)));
                day.setText(plain(Math.round(m / days)));
                toast("Revisa los valores y toca Guardar");
            }
        }), weightGap());
        c.addView(actions);
        content.addView(c);

        LinearLayout status = card();
        status.addView(text("Cómo vas", 16, C_TEXT, true));
        addBudgetRow(status, "Hoy", Periods.day(now), db.budget(Db.PREF_BUDGET_DAY));
        addBudgetRow(status, "Esta semana", Periods.week(now), db.budget(Db.PREF_BUDGET_WEEK));
        addBudgetRow(status, "Este mes", Periods.month(now), db.budget(Db.PREF_BUDGET_MONTH));
        double pending = db.pendingFixedThisMonth();
        double bm = db.budget(Db.PREF_BUDGET_MONTH);
        if (pending > 0 && bm > 0) {
            double free = bm - db.sum(NotificationParser.EXPENSE, Periods.month(now)[0], Periods.month(now)[1]) - pending;
            TextView t = text("Libre del mes después de gastos fijos pendientes: " + Db.money(free),
                    13, free >= 0 ? C_INCOME : C_EXPENSE, true);
            t.setPadding(0, dp(10), 0, 0);
            status.addView(t);
        }
        content.addView(status);
    }

    private EditText labeledInput(LinearLayout parent, String label, double value) {
        TextView l = text(label, 13, C_MUTED, false);
        l.setPadding(0, dp(10), 0, 0);
        parent.addView(l);
        EditText e = input("$ 0", true);
        if (value > 0) e.setText(plain(value));
        parent.addView(e);
        return e;
    }

    // ================================================================= Gastos fijos

    private void renderFixed() {
        long[] m = Periods.month(System.currentTimeMillis());
        List<Db.Fixed> list = db.listFixed();

        LinearLayout head = card();
        double total = 0, paid = 0;
        for (Db.Fixed f : list) {
            if (!f.active) continue;
            total += f.amount;
            if (db.fixedPaymentTx(f.id, m[0], m[1]) != 0) paid += f.amount;
        }
        head.addView(text("Gastos fijos", 16, C_TEXT, true));
        head.addView(text("Arriendo, servicios, suscripciones, créditos… Al marcarlos como pagados se suman a tus gastos.",
                12, C_MUTED, false));
        LinearLayout row = hrow();
        row.setPadding(0, dp(8), 0, 0);
        row.addView(stat("Total mensual", Db.money(total), C_TEXT), weight());
        row.addView(stat("Pendiente", Db.money(total - paid), total - paid > 0 ? C_WARN : C_INCOME), weight());
        head.addView(row);
        head.addView(button("+ Agregar gasto fijo", C_PRIMARY, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editFixed(null);
            }
        }));
        content.addView(head);

        for (final Db.Fixed f : list) {
            final long payTx = db.fixedPaymentTx(f.id, m[0], m[1]);
            LinearLayout c = card();
            LinearLayout top = hrow();
            top.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.addView(text(f.name, 15, f.active ? C_TEXT : C_MUTED, true));
            String status = !f.active ? "Pausado" : payTx != 0 ? "Pagado este mes ✓" : "Pendiente · se cobra el día " + f.day;
            left.addView(text(status, 12, !f.active ? C_MUTED : payTx != 0 ? C_INCOME : C_WARN, false));
            top.addView(left, weight());
            top.addView(text(Db.money(f.amount), 15, C_EXPENSE, true));
            c.addView(top);

            LinearLayout actions = hrow();
            actions.setPadding(0, dp(8), 0, 0);
            if (f.active) {
                actions.addView(button(payTx != 0 ? "Desmarcar pago" : "Marcar pagado",
                        payTx != 0 ? 0xFF90A4AE : C_INCOME, new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (payTx != 0) db.deleteTx(payTx);
                                else db.payFixed(f, System.currentTimeMillis());
                                changed();
                            }
                        }), weightGap());
            }
            actions.addView(button("Editar", 0xFF546E7A, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editFixed(f);
                }
            }), weightGap());
            c.addView(actions);
            content.addView(c);
        }
    }

    private void editFixed(final Db.Fixed existing) {
        LinearLayout form = form();
        final EditText name = input("Nombre (ej. Arriendo)", false);
        final EditText amount = input("Monto mensual", true);
        final EditText day = input("Día de cobro (1-31)", true);
        day.setInputType(InputType.TYPE_CLASS_NUMBER);
        final Switch active = new Switch(this);
        active.setText("Activo");
        active.setChecked(existing == null || existing.active);
        if (existing != null) {
            name.setText(existing.name);
            amount.setText(plain(existing.amount));
            day.setText(String.valueOf(existing.day));
        }
        form.addView(name);
        form.addView(amount);
        form.addView(day);
        form.addView(active);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Nuevo gasto fijo" : "Editar gasto fijo")
                .setView(form)
                .setPositiveButton("Guardar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String n = name.getText().toString().trim();
                        double a = NotificationParser.parseNumber(amount.getText().toString());
                        int dd;
                        try {
                            dd = Integer.parseInt(day.getText().toString().trim());
                        } catch (NumberFormatException e) {
                            dd = 1;
                        }
                        if (n.isEmpty() || a <= 0) {
                            toast("Escribe nombre y monto");
                            return;
                        }
                        dd = Math.max(1, Math.min(31, dd));
                        db.saveFixed(existing == null ? 0 : existing.id, n, a, dd, active.isChecked());
                        changed();
                    }
                })
                .setNegativeButton("Cancelar", null);
        if (existing != null) {
            b.setNeutralButton("Eliminar", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    db.deleteFixed(existing.id);
                    changed();
                }
            });
        }
        b.show();
    }

    // ================================================================= Ajustes

    private void renderSettings() {
        LinearLayout access = card();
        boolean ok = hasNotificationAccess();
        access.addView(text("Lectura de notificaciones", 16, C_TEXT, true));
        access.addView(text(ok ? "Activa ✓ — los movimientos de tus bancos se registran solos."
                : "Inactiva — la app no puede ver las notificaciones de tus bancos.", 13, ok ? C_INCOME : C_EXPENSE, false));
        access.addView(text("Si Android dice \"Configuración restringida\": abre Información de la app → menú ⋮ → "
                + "\"Permitir configuración restringida\" y vuelve a intentarlo.", 12, C_MUTED, false));
        access.addView(button(ok ? "Abrir ajustes de acceso" : "Dar acceso", C_PRIMARY, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNotificationAccess();
            }
        }));
        access.addView(button("Información de la app", 0xFF546E7A, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        }));
        content.addView(access);

        LinearLayout apps = card();
        apps.addView(text("Apps detectadas", 16, C_TEXT, true));
        apps.addView(text("Apps que han enviado notificaciones con dinero. Desactiva las que no sean de tus bancos "
                + "(por ejemplo, chats).", 12, C_MUTED, false));
        List<Db.App> list = db.listApps();
        if (list.isEmpty()) apps.addView(text("Todavía ninguna. Aparecerán aquí al llegar la primera notificación.",
                13, C_MUTED, false));
        for (final Db.App a : list) {
            Switch s = new Switch(this);
            s.setText(a.label + "\n" + a.pkg);
            s.setTextSize(13);
            s.setChecked(a.enabled);
            s.setPadding(0, dp(6), 0, dp(6));
            s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton b, boolean checked) {
                    db.setAppEnabled(a.pkg, checked);
                }
            });
            apps.addView(s);
        }
        content.addView(apps);

        LinearLayout fixed = card();
        fixed.addView(text("Gastos fijos automáticos", 16, C_TEXT, true));
        Switch auto = new Switch(this);
        auto.setText("Registrar cada gasto fijo automáticamente el día de cobro. Desactívalo si tu banco ya te "
                + "notifica esos pagos, para no contarlos dos veces.");
        auto.setTextSize(13);
        auto.setChecked(db.prefs().getBoolean(Db.PREF_AUTO_FIXED, false));
        auto.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                db.prefs().edit().putBoolean(Db.PREF_AUTO_FIXED, checked).apply();
                if (db.applyAutoFixed()) changed();
            }
        });
        fixed.addView(auto);
        content.addView(fixed);

        LinearLayout tester = card();
        tester.addView(text("Probar una notificación", 16, C_TEXT, true));
        tester.addView(text("Pega el texto de una notificación de tu banco para ver cómo la interpreta la app.",
                12, C_MUTED, false));
        final EditText sample = input("Ej: Compraste $45.000 en RAPPI", false);
        sample.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        tester.addView(sample);
        final TextView result = text("", 13, C_TEXT, true);
        tester.addView(button("Probar", C_PRIMARY, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NotificationParser.Result r = NotificationParser.parse("", sample.getText().toString());
                if (r == null) {
                    result.setText("No se reconoce como ingreso ni gasto.");
                    result.setTextColor(C_MUTED);
                } else {
                    boolean in = r.type == NotificationParser.INCOME;
                    result.setText((in ? "Ingreso " : "Gasto ") + Db.money(r.amount) + " · " + r.description);
                    result.setTextColor(in ? C_INCOME : C_EXPENSE);
                }
            }
        }));
        tester.addView(result);
        content.addView(tester);

        LinearLayout danger = card();
        danger.addView(text("Privacidad", 16, C_TEXT, true));
        danger.addView(text("Todos los datos se guardan solo en este teléfono. La app no tiene permiso de internet.",
                12, C_MUTED, false));
        danger.addView(button("Borrar todos los datos", C_EXPENSE, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("¿Borrar todo?")
                        .setMessage("Se eliminarán movimientos, gastos fijos y presupuestos. No se puede deshacer.")
                        .setPositiveButton("Borrar", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                db.wipe();
                                changed();
                            }
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            }
        }));
        content.addView(danger);
    }

    private boolean hasNotificationAccess() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (flat == null) return false;
        ComponentName me = new ComponentName(this, BankListenerService.class);
        for (String s : flat.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(s);
            if (me.equals(cn)) return true;
        }
        return false;
    }

    private void openNotificationAccess() {
        try {
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    // ================================================================= componentes de UI

    private interface Pick {
        void pick(int i);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(rounded(Color.WHITE, 16));
        c.setElevation(dp(1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(6));
        c.setLayoutParams(lp);
        return c;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private LinearLayout hrow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private LinearLayout.LayoutParams weightGap() {
        LinearLayout.LayoutParams lp = weight();
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
    }

    private View stat(String label, String value, int color) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.addView(text(label, 12, C_MUTED, false));
        l.addView(text(value, 20, color, true));
        return l;
    }

    private Button button(String label, int color, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setBackground(rounded(color, 12));
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        lp.setMargins(0, dp(8), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView smallButton(String label, View.OnClickListener click) {
        TextView t = text(label, 26, C_PRIMARY, true);
        t.setPadding(dp(18), dp(4), dp(18), dp(4));
        t.setOnClickListener(click);
        return t;
    }

    private ProgressBar bar(int percent, int color) {
        ProgressBar p = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        p.setMax(100);
        p.setProgress(percent);
        p.setProgressTintList(ColorStateList.valueOf(color));
        p.setProgressBackgroundTintList(ColorStateList.valueOf(0xFFE0E0E0));
        return p;
    }

    private View segmented(String[] labels, int selected, final Pick pick) {
        LinearLayout row = hrow();
        row.setBackground(rounded(0xFFE0E7E6, 20));
        row.setPadding(dp(3), dp(3), dp(3), dp(3));
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = text(labels[i], 14, i == selected ? Color.WHITE : C_PRIMARY, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(8), 0, dp(8));
            if (i == selected) t.setBackground(rounded(C_PRIMARY, 18));
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pick.pick(idx);
                }
            });
            row.addView(t, weight());
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(4));
        row.setLayoutParams(lp);
        return row;
    }

    private LinearLayout form() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(dp(20), dp(8), dp(20), 0);
        return f;
    }

    private EditText input(String hint, boolean number) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(!(!number && hint.startsWith("Ej")));
        e.setInputType(number ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return e;
    }

    private static String plain(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.004) return String.valueOf((long) Math.rint(v));
        return String.format(Locale.US, "%.2f", v);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
