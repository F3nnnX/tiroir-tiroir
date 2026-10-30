package fr.f3n.tiroir;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Toute l'interface, construite en code. Une pile de Runnable sert de navigation. */
public class Principal extends Activity {
    // Palette v1.4 (refonte Qdjar Qdjar)
    static final int FOND = 0xFFF4F6F9, TEXTE = 0xFF1A1C3B, SOUS = 0xFF5B5E7A, TEAL = 0xFF00A896, VERT = 0xFF00A86B,
            ROUGE = 0xFFE53935, ORANGE = 0xFFFB8C00, VERTOK = 0xFF4CAF50, GRIS = 0xFF9E9E9E, BLANC = Color.WHITE,
            PALE = 0xFFE3E8FB, DORE = 0xFFC9A84C, GRISBLEU = 0xFFD6DBEA, POIGNEE = 0xFFD8DCE8, INDIGO = 0xFF3A3F9E;
    static final int LARGE = LinearLayout.LayoutParams.MATCH_PARENT, HAUT = LinearLayout.LayoutParams.WRAP_CONTENT;

    private Donnees d;
    private final ArrayList<Runnable> pile = new ArrayList<>();
    private boolean surAccueil;
    private LinearLayout col;
    private Dialog overlayOuvert;
    private int forceOverlay; // id d'un élément à montrer en overlay (tap sur le widget)

    /** Une urgence à montrer en overlay : un élément rouge, ou un relevé km dû (véhicule v, ou élément km isolé it). */
    private static class Alerte {
        Item it;
        Dossier v;
        boolean releve;
        String cle() { return releve ? "r" + (v != null ? v.id : it.id) : "i" + it.id; }
    }

    // ---------- cycle de vie ----------
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = Donnees.get(this);
        Rappels.canaux(this);
        Rappels.programmer(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        aller(this::accueil);
        depuis(getIntent());
    }

    @Override protected void onNewIntent(Intent in) {
        super.onNewIntent(in);
        setIntent(in);
        depuis(in);
        if (forceOverlay != 0 && overlayOuvert == null) ouvrirOverlay();
    }

    @Override protected void onStart() {
        super.onStart();
        Postit.maj(this);
        if (overlayOuvert == null && !pile.isEmpty()) ouvrirOverlay();
    }

    private void depuis(Intent in) {
        int ov = in.getIntExtra("overlay", 0);
        in.removeExtra("overlay");
        if (ov != 0) forceOverlay = ov;
        int id = in.getIntExtra("item", 0);
        in.removeExtra("item");
        if (id != 0 && d.item(id) != null) aller(() -> detail(id));
        int idv = in.getIntExtra("veh", 0);
        in.removeExtra("veh");
        if (idv != 0 && d.dossier(idv) != null) aller(() -> dossier(idv));
    }

    @Override protected void onResume() {
        super.onResume();
        Rappels.verifier(this, false);
        if (surAccueil) accueil(); // l'utilisateur revient des réglages Android
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        Rappels.verifier(this, false);
        if (surAccueil) accueil();
    }

    @Override public void onBackPressed() { retour(1); }

    // ---------- navigation ----------
    private void aller(Runnable r) { pile.add(r); r.run(); }

    private void retour(int n) {
        if (pile.size() <= n) { finish(); return; }
        for (int i = 0; i < n; i++) pile.remove(pile.size() - 1);
        pile.get(pile.size() - 1).run();
    }

    private void rafraichir() { pile.get(pile.size() - 1).run(); }

    // ---------- éléments d'interface ----------
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private GradientDrawable fond(int c, int rayon) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(c);
        g.setCornerRadius(dp(rayon));
        return g;
    }

    private TextView txt(String s, int sp, int couleur, boolean gras) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(couleur);
        if (gras) t.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LARGE, HAUT);
        lp.topMargin = dp(6);
        t.setLayoutParams(lp);
        return t;
    }

    /** Bouton pilule. */
    private Button btn(String s, int couleur, int texte, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(19);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setTextColor(texte);
        b.setBackground(fond(couleur, 40));
        b.setStateListAnimator(null);
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LARGE, HAUT);
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);
        return b;
    }

    /** Bouton contour (fond transparent, bordure indigo). */
    private Button btnContour(String s, View.OnClickListener l) {
        Button b = btn(s, Color.TRANSPARENT, TEXTE, l);
        GradientDrawable g = fond(Color.TRANSPARENT, 40);
        g.setStroke(dp(2), 0xFF4B4F9A);
        b.setBackground(g);
        return b;
    }

    private View pastille(int couleur) {
        View v = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(couleur);
        v.setBackground(g);
        return v;
    }

    private View poignee() {
        View h = new View(this);
        h.setBackground(fond(POIGNEE, 6));
        return h;
    }

    /** Carte blanche : ombre douce, coins 16, pastille en haut à droite, bouton rapide facultatif, bordure facultative. */
    private View carte(CharSequence titre, String sous, int pastille, int bordure, String rapide, Runnable fait,
                       Runnable clic, Runnable longClic) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable g = fond(BLANC, 16);
        if (bordure != 0) g.setStroke(dp(2), bordure);
        f.setBackground(g);
        f.setElevation(dp(3));
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(18), dp(16), dp(44), dp(14));
        TextView t = txt("", 22, TEXTE, true);
        t.setText(titre);
        l.addView(t);
        if (sous != null && !sous.isEmpty()) l.addView(txt(sous, 15, SOUS, false));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(70), dp(5));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.topMargin = dp(rapide != null ? 22 : 12);
        l.addView(poignee(), hp);
        f.addView(l, new FrameLayout.LayoutParams(LARGE, HAUT));
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.TOP | Gravity.END);
        pp.topMargin = dp(14);
        pp.rightMargin = dp(14);
        f.addView(pastille(pastille), pp);
        if (rapide != null) {
            Button b = new Button(this);
            b.setText(rapide);
            b.setAllCaps(false);
            b.setTextSize(14);
            b.setTypeface(null, android.graphics.Typeface.BOLD);
            b.setTextColor(BLANC);
            b.setBackground(fond(ROUGE, 40));
            b.setStateListAnimator(null);
            b.setPadding(dp(14), 0, dp(14), 0);
            b.setMinHeight(0);
            b.setMinimumHeight(0);
            b.setOnClickListener(v -> fait.run());
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(HAUT, dp(34), Gravity.BOTTOM | Gravity.END);
            bp.rightMargin = dp(12);
            bp.bottomMargin = dp(10);
            f.addView(b, bp);
        }
        f.setOnClickListener(v -> clic.run());
        if (longClic != null) f.setOnLongClickListener(v -> { longClic.run(); return true; });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LARGE, HAUT);
        lp.topMargin = dp(14);
        f.setLayoutParams(lp);
        return f;
    }

    private View tiroir(String titre, String sous, int pastille, Runnable clic, Runnable longClic) {
        return carte(titre, sous, pastille, 0, null, null, clic, longClic);
    }

    private LinearLayout page(String titre, boolean avecRetour) {
        return page(titre, null, avecRetour);
    }

    /** Page : fond clair, bouton « ‹ Retour » (pilule pâle), fil d'Ariane facultatif, titre. */
    private LinearLayout page(String titre, String ariane, boolean avecRetour) {
        surAccueil = false;
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(FOND);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(dp(18), dp(24), dp(18), dp(40));
        sv.addView(col);
        setContentView(sv);
        if (avecRetour) {
            Button r = btn("‹  Retour", PALE, INDIGO, v -> retour(1));
            r.setTextSize(16);
            r.setPadding(dp(20), dp(6), dp(20), dp(6));
            r.setLayoutParams(new LinearLayout.LayoutParams(HAUT, HAUT));
            col.addView(r);
        }
        if (ariane != null && !ariane.isEmpty()) col.addView(txt(ariane, 14, SOUS, false));
        col.addView(txt(titre, 28, TEXTE, true));
        return col;
    }

    private EditText champ(LinearLayout p, String lab, String val, int type) {
        p.addView(txt(lab, 16, TEXTE, false));
        EditText e = new EditText(this);
        e.setText(val);
        e.setInputType(type);
        e.setTextSize(20);
        e.setTextColor(TEXTE);
        p.addView(e);
        return e;
    }

    private static long lng(EditText e) {
        String s = e.getText().toString().replaceAll("[^0-9]", "");
        if (s.length() > 12) s = s.substring(0, 12);
        return s.isEmpty() ? 0 : Long.parseLong(s);
    }

    private Button dateBtn(long[] v) {
        Button b = btn("", GRISBLEU, TEXTE, null);
        Runnable maj = () -> b.setText(v[0] == 0 ? "Choisir une date" : Item.jm(v[0], LocalDate.now()));
        maj.run();
        b.setOnClickListener(x -> {
            LocalDate dt = v[0] == 0 ? LocalDate.now() : LocalDate.ofEpochDay(v[0]);
            new DatePickerDialog(this, (dp, y, m, j) -> { v[0] = LocalDate.of(y, m + 1, j).toEpochDay(); maj.run(); },
                    dt.getYear(), dt.getMonthValue() - 1, dt.getDayOfMonth()).show();
        });
        return b;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private void confirmer(String msg, Runnable oui) {
        new AlertDialog.Builder(this).setMessage(msg).setPositiveButton("Oui", (a, w) -> oui.run())
                .setNegativeButton("Non", null).show();
    }

    private void saisie(String titre, String init, Consumer<String> ok) {
        EditText e = new EditText(this);
        e.setText(init);
        e.setTextSize(20);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        new AlertDialog.Builder(this).setTitle(titre).setView(e).setPositiveButton("OK", (a, w) -> {
            String s = e.getText().toString().trim();
            if (!s.isEmpty()) ok.accept(s);
        }).setNegativeButton("Annuler", null).show();
    }

    private void reglage(Intent i) {
        try { startActivity(i); } catch (Exception e) { toast("Ouvrez les réglages d'Android pour cette application."); }
    }

    private static int teinte(int alerte) { return alerte == 2 ? ROUGE : alerte == 1 ? ORANGE : VERTOK; }

    private int couleur(Item it, LocalDate auj) { return teinte(it.alerte(auj)); }

    private String resume(int id) {
        List<Item> l = d.sousArbre(id);
        if (l.isEmpty()) return "Vide";
        LocalDate auj = LocalDate.now();
        Item best = null;
        for (Item x : l) if (best == null || x.score(auj) < best.score(auj)) best = x;
        int r = 0;
        for (Item x : l) if (x.retard(auj)) r++;
        String n = Item.pl(l.size(), "élément") + (r > 0 ? " · " + r + " en retard" : "");
        return best.alerte(auj) > 0 || r > 0 ? best.ligne(auj).replace(" : ", " · ") + "\n" + n : n;
    }

    private int couleurDossier(int id) {
        LocalDate auj = LocalDate.now();
        int m = 0;
        for (Item x : d.sousArbre(id)) m = Math.max(m, x.alerte(auj));
        return d.sousArbre(id).isEmpty() ? GRIS : teinte(m);
    }

    /** Pastille d'un tiroir (accueil ou sous-tiroir) : couleur de son élément le plus urgent, grise si vide. */
    private int pastilleDossier(Dossier r) { return couleurDossier(r.id); }

    private String emojiDossier(Dossier r) {
        String n = r.nom.toLowerCase();
        if (r.vehicule || n.equals("voiture")) return "🚗";
        String[][] t = {{"maison", "🏠"}, {"travail", "💼"}, {"santé", "🩺"}, {"sante", "🩺"}, {"autre", "📦"}, {"facture", "🧾"},
                {"entretien", "🔧"}, {"assurance", "🛡️"}, {"impôt", "🧾"}, {"banque", "🏦"}};
        for (String[] x : t) if (n.contains(x[0])) return x[1];
        return "📁";
    }

    /** Marquer fait/payé : boîte de confirmation (date ou km de la prochaine échéance), puis badge « ✓ Fait ! ». */
    private void marquerFait(Item it) { confirmerFait(it, this::rafraichir, null); }

    private void confirmerFait(Item it, Runnable apres, Runnable annule) {
        String suite = it.prochaine(LocalDate.now());
        new AlertDialog.Builder(this).setTitle("Confirmer la réalisation de cette tâche ?")
                .setMessage(it.emoji() + " " + it.nom + "\n\nProchaine échéance recalculée le : " + suite + ".")
                .setPositiveButton("Confirmer", (a, w) -> {
                    it.paye();
                    d.sauver();
                    Rappels.annuler(this, it);
                    Rappels.verifier(this, false);
                    badgeFait();
                    apres.run();
                })
                .setNegativeButton("Annuler", (a, w) -> { if (annule != null) annule.run(); })
                .setOnCancelListener(x -> { if (annule != null) annule.run(); }).show();
    }

    /** Badge vert « ✓ Fait ! » affiché brièvement. */
    private void badgeFait() {
        Dialog g = new Dialog(this);
        g.requestWindowFeature(Window.FEATURE_NO_TITLE);
        TextView t = new TextView(this);
        t.setText("✓ Fait !");
        t.setTextSize(28);
        t.setTextColor(BLANC);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setBackground(fond(VERTOK, 40));
        t.setPadding(dp(32), dp(16), dp(32), dp(16));
        g.setContentView(t);
        Window w = g.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        g.show();
        new Handler(Looper.getMainLooper()).postDelayed(() -> { try { g.dismiss(); } catch (Exception e) { /* écran fermé */ } }, 1600);
    }

    // ---------- Overlay d'urgence ----------
    private boolean reportee(String cle) {
        return getSharedPreferences("tt", 0).getLong("rep_" + cle, -1) == LocalDate.now().toEpochDay();
    }

    private void reporter(String cle) {
        getSharedPreferences("tt", 0).edit().putLong("rep_" + cle, LocalDate.now().toEpochDay()).apply();
    }

    private boolean releveDu(Compteur c, boolean question) {
        return question && LocalDate.now().toEpochDay() - c.qDate >= (c.estime() ? 28 : 7);
    }

    /** Éléments rouges (les plus urgents d'abord), puis relevés km dus ; ceux reportés aujourd'hui sont masqués. */
    private List<Alerte> urgences() {
        LocalDate auj = LocalDate.now();
        ArrayList<Item> rouges = new ArrayList<>();
        for (Item x : d.items) if (x.alerte(auj) == 2) rouges.add(x);
        rouges.sort((a, b) -> Double.compare(a.score(auj), b.score(auj)));
        ArrayList<Alerte> r = new ArrayList<>();
        for (Item x : rouges) {
            Alerte a = new Alerte();
            a.it = x;
            if (!reportee(a.cle())) r.add(a);
        }
        for (Dossier v : d.dossiers) {
            if (!v.vehicule) continue;
            boolean q = false;
            for (Item x : d.kmDe(v)) q |= x.question;
            if (!releveDu(v.c, q)) continue;
            Alerte a = new Alerte();
            a.v = v;
            a.releve = true;
            if (!reportee(a.cle())) r.add(a);
        }
        for (Item x : d.items) {
            if (!x.km() || x.veh != null || !releveDu(x.c, x.question)) continue;
            Alerte a = new Alerte();
            a.it = x;
            a.releve = true;
            if (!reportee(a.cle())) r.add(a);
        }
        return r;
    }

    private void ouvrirOverlay() {
        if (forceOverlay != 0) {
            Item it = d.item(forceOverlay);
            forceOverlay = 0;
            if (it != null) {
                Alerte a = new Alerte();
                a.it = it;
                ArrayList<Alerte> l = new ArrayList<>();
                l.add(a);
                overlay(l, 0);
                return;
            }
        }
        List<Alerte> l = urgences();
        if (!l.isEmpty()) overlay(l, 0);
    }

    /** Carte modale sur fond assombri ; enchaîne les urgences l'une après l'autre. */
    private void overlay(List<Alerte> l, int i) {
        if (i >= l.size()) { if (!pile.isEmpty()) rafraichir(); return; }
        Alerte a = l.get(i);
        LocalDate auj = LocalDate.now();
        Dialog g = new Dialog(this);
        g.requestWindowFeature(Window.FEATURE_NO_TITLE);
        g.setCancelable(false);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(fond(BLANC, 24));
        c.setPadding(dp(22), dp(20), dp(22), dp(20));
        String entete = (a.releve ? "RELEVÉ DU COMPTEUR" : "URGENT") + (l.size() > 1 ? "  ·  " + (i + 1) + " / " + l.size() : "");
        c.addView(txt(entete, 13, a.releve ? ORANGE : ROUGE, true));
        EditText km = null;
        if (a.releve) {
            String nom = a.v != null ? a.v.nom : a.it.nom;
            c.addView(txt("🧭  " + nom, 26, TEXTE, true));
            Compteur cp = a.v != null ? a.v.c : a.it.cpt();
            c.addView(txt(cp.estime() ? "On estime ≈ " + Item.nb(cp.actuel(auj)) + " km. Quel est le vrai kilométrage du compteur ?"
                    : "Dernier relevé : " + Item.nb(cp.kmActuel) + " km. Quel est le kilométrage du compteur ?", 17, TEXTE, false));
            km = new EditText(this);
            km.setInputType(InputType.TYPE_CLASS_NUMBER);
            km.setTextSize(22);
            km.setTextColor(TEXTE);
            km.setHint("km");
            c.addView(km);
        } else {
            Item it = a.it;
            c.addView(txt(it.emoji() + "  " + it.nom, 26, TEXTE, true));
            c.addView(txt(it.statut(auj), 18, teinte(it.alerte(auj)), true));
            c.addView(txt(d.chemin(it.dossier), 14, SOUS, false));
        }
        final EditText fkm = km;
        if (a.releve) {
            c.addView(btn("Valider", VERT, BLANC, v -> {
                if (fkm.getText().toString().trim().isEmpty()) { toast("Tape le kilométrage du compteur."); return; }
                Compteur cp = a.v != null ? a.v.c : a.it.cpt();
                String info = cp.releve(lng(fkm), LocalDate.now());
                d.sauver();
                Rappels.verifier(this, false);
                if (info != null) toast(info);
                overlayOuvert = null;
                g.dismiss();
                overlay(l, i + 1);
            }));
        } else {
            c.addView(btn("Urgent / Valider", ROUGE, BLANC, v -> {
                overlayOuvert = null;
                g.dismiss();
                confirmerFait(a.it, () -> overlay(l, i + 1), () -> overlay(l, i));
            }));
        }
        c.addView(btnContour("Reporter à plus tard", v -> {
            reporter(a.cle());
            overlayOuvert = null;
            g.dismiss();
            overlay(l, i + 1);
        }));
        FrameLayout f = new FrameLayout(this);
        f.setPadding(dp(18), 0, dp(18), 0);
        f.addView(c);
        g.setContentView(f);
        Window w = g.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setDimAmount(0.65f);
        w.setLayout(LARGE, HAUT);
        overlayOuvert = g;
        g.show();
    }

    // ---------- écrans ----------
    /** Carte épurée de l'accueil : nom en gras à gauche, emoji à droite du nom, pastille à l'extrême droite. */
    private View carteAccueil(String nom, String emoji, int pastille, Runnable clic, Runnable longClic) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(fond(BLANC, 16));
        r.setElevation(dp(3));
        r.setPadding(dp(20), dp(22), dp(20), dp(22));
        TextView n = new TextView(this);
        n.setText(nom);
        n.setTextSize(22);
        n.setTextColor(TEXTE);
        n.setTypeface(null, android.graphics.Typeface.BOLD);
        r.addView(n, new LinearLayout.LayoutParams(HAUT, HAUT));
        TextView e = new TextView(this);
        e.setText(emoji);
        e.setTextSize(24);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(HAUT, HAUT);
        ep.leftMargin = dp(10);
        r.addView(e, ep);
        r.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        if (pastille != 0) r.addView(pastille(pastille), new LinearLayout.LayoutParams(dp(22), dp(22)));
        r.setOnClickListener(v -> clic.run());
        if (longClic != null) r.setOnLongClickListener(v -> { longClic.run(); return true; });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LARGE, HAUT);
        lp.topMargin = dp(14);
        r.setLayoutParams(lp);
        return r;
    }

    private void accueil() {
        LinearLayout p = page("Qdjar Qdjar", false);
        surAccueil = true;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (!nm.areNotificationsEnabled())
            p.addView(btn("Activer les notifications (sinon aucun rappel !)", ROUGE, BLANC, v -> reglage(
                    new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()))));
        // Maison, Travail, Voiture, Santé, Autres, puis les tiroirs sur mesure déjà créés, puis la carte d'ajout
        ArrayList<Dossier> ordre = new ArrayList<>();
        for (String n : Donnees.PRINCIPAUX)
            for (Dossier r : d.enfants(0)) if (r.nom.equalsIgnoreCase(n)) { ordre.add(r); break; }
        int nStd = ordre.size();
        for (Dossier r : d.enfants(0)) if (!ordre.contains(r)) ordre.add(r);
        for (int k = 0; k < ordre.size(); k++) {
            Dossier r = ordre.get(k);
            boolean perso = k >= nStd;
            p.addView(carteAccueil(r.nom, emojiDossier(r), pastilleDossier(r), () -> aller(() -> dossier(r.id)),
                    perso ? () -> actionsDossier(r.id) : null));
        }
        p.addView(carteAccueil("➕  Ajouter un tiroir", "", 0, () -> saisie("Nom du tiroir", "", n -> {
            d.ajoutDossier(0, n);
            d.sauver();
            accueil();
        }), null));
        p.addView(btn("À venir", VERT, BLANC, v -> aller(() -> avenir(0))));
        TextView z = txt("🔕  Ne pas déranger de " + Rappels.hhmm(Rappels.dndDebut(this)) + " à " + Rappels.hhmm(Rappels.dndFin(this)) + " (modifier)", 15, INDIGO, false);
        z.setPaintFlags(z.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        z.setGravity(Gravity.CENTER_HORIZONTAL);
        z.setPadding(dp(8), dp(24), dp(8), dp(16));
        z.setOnClickListener(v -> reglerDnd());
        p.addView(z);
    }

    /** Réglage « Ne pas déranger entre [début] et [fin] » : dans la plage, notifications sans son ni vibration. */
    private void reglerDnd() {
        int d0 = Rappels.dndDebut(this), f0 = Rappels.dndFin(this);
        TimePickerDialog t1 = new TimePickerDialog(this, (vw, h, m) -> {
            int deb = h * 60 + m;
            TimePickerDialog t2 = new TimePickerDialog(this, (vw2, h2, m2) -> {
                Rappels.dnd(this, deb, h2 * 60 + m2);
                accueil();
            }, f0 / 60, f0 % 60, true);
            t2.setTitle("Fin : le son revient à");
            t2.show();
        }, d0 / 60, d0 % 60, true);
        t1.setTitle("Début : plus de son à partir de");
        t1.show();
    }

    private void dossier(int id) {
        Dossier cur = d.dossier(id);
        if (cur == null) { retour(1); return; }
        String chemin = d.chemin(id);
        boolean racine = cur.parent == 0;
        LinearLayout p = page(chemin, racine ? chemin + " › Qdjar Qdjar" : null, true);
        LocalDate auj = LocalDate.now();
        boolean voitures = cur.parent == 0 && cur.nom.equalsIgnoreCase("Voiture");
        if (cur.vehicule) p.addView(txt(infoVehicule(cur, auj), 18, TEXTE, false));
        // Niveau « Voiture » : les véhicules seulement (les anciens éléments/sous-tiroirs éventuels restent visibles en dessous).
        for (Dossier s : d.enfants(id))
            if (!voitures || s.vehicule)
                p.addView(tiroir(emojiDossier(s) + "  " + s.nom, (s.vehicule ? infoVehicule(s, auj) + "\n" : "") + resume(s.id), pastilleDossier(s), () -> aller(() -> dossier(s.id)),
                        () -> actionsDossier(s.id)));
        for (Dossier s : d.enfants(id))
            if (voitures && !s.vehicule)
                p.addView(tiroir(emojiDossier(s) + "  " + s.nom, resume(s.id), pastilleDossier(s), () -> aller(() -> dossier(s.id)), () -> actionsDossier(s.id)));
        for (Item it : d.itemsDe(id)) {
            int al = it.alerte(auj);
            p.addView(carte(it.emoji() + "  " + it.nom, it.statut(auj), teinte(al), al > 0 ? teinte(al) : 0,
                    al == 2 ? "Urgent" : null, () -> marquerFait(it),
                    () -> aller(() -> detail(it.id)), null));
        }
        if (voitures) {
            if (d.enfants(id).isEmpty()) p.addView(txt("Aucun véhicule pour l'instant.", 18, TEXTE, false));
            if (!d.sousArbre(id).isEmpty()) p.addView(btn("À venir dans ce tiroir", VERT, BLANC, v -> aller(() -> avenir(id))));
            p.addView(btnContour("+ Ajouter un véhicule", v -> aller(() -> formulaireVehicule(0, id))));
            return;
        }
        if (d.enfants(id).isEmpty() && d.itemsDe(id).isEmpty()) p.addView(txt("Ce tiroir est vide.", 18, TEXTE, false));
        Button ajout = btn("+ Ajouter un élément", cur.parent == 0 ? 0xFF4B4F9A : DORE, BLANC, v -> {
            if (cur.vehicule || d.vehiculeDe(id) != null) {
                new AlertDialog.Builder(this).setTitle("Quel type d'élément ?")
                        .setItems(new String[]{"Échéance (facture, assurance, contrôle...)", "Kilométrage (vidange, distribution...)"},
                                (a, w) -> aller(() -> formulaire(0, id, w == 0 ? "E" : "K"))).show();
            } else {
                new AlertDialog.Builder(this).setTitle("Quel type d'élément ?")
                        .setItems(new String[]{"Échéance (facture, assurance, contrôle...)", "Kilométrage (vidange, distribution...)",
                                        "Un sous-tiroir (pour regrouper)"},
                                (a, w) -> {
                                    if (w == 2) saisie("Nom du sous-tiroir", "", n -> { d.ajoutDossier(id, n); d.sauver(); dossier(id); });
                                    else aller(() -> formulaire(0, id, w == 0 ? "E" : "K"));
                                }).show();
            }
        });
        if (cur.parent == 0) { // « + Ajouter une sous-catégorie » (contour indigo) ; l'élément reste possible via le même bouton
            p.addView(btnContour("+ Ajouter une sous-catégorie", v -> saisie("Nom du sous-tiroir", "", n -> { d.ajoutDossier(id, n); d.sauver(); dossier(id); })));
            p.addView(btnContour("+ Ajouter un élément", v -> ajout.performClick()));
        } else p.addView(ajout);
        if (!d.sousArbre(id).isEmpty()) p.addView(btn("À venir dans ce tiroir", VERT, BLANC, v -> aller(() -> avenir(id))));
        if (cur.vehicule) {
            TextView r = txt("Réglages du véhicule", 15, INDIGO, false);
            r.setPaintFlags(r.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            r.setGravity(Gravity.CENTER_HORIZONTAL);
            r.setPadding(dp(8), dp(24), dp(8), dp(16));
            r.setOnClickListener(v -> aller(() -> formulaireVehicule(id, cur.parent)));
            p.addView(r);
        } else if (cur.parent != 0)
            p.addView(txt("💡 Astuce : appui long sur un sous-tiroir pour le renommer ou le supprimer.", 14, SOUS, false));
    }

    private void actionsDossier(int id) {
        Dossier x = d.dossier(id);
        String[] ch = x.vehicule ? new String[]{"Renommer", "Supprimer", "Modifier le véhicule (km)"}
                : new String[]{"Renommer", "Supprimer", "En faire un véhicule (km partagés)"};
        new AlertDialog.Builder(this).setTitle(x.nom).setItems(ch, (a, w) -> {
            if (w == 2) {
                if (x.vehicule) aller(() -> formulaireVehicule(x.id, x.parent));
                else { x.vehicule = true; d.lier(); d.sauver(); aller(() -> formulaireVehicule(x.id, x.parent)); }
            } else if (w == 0) saisie("Nouveau nom", x.nom, n -> { x.nom = n; d.sauver(); rafraichir(); });
            else confirmer("Supprimer « " + x.nom + " » avec tout son contenu ?", () -> {
                for (Item i : d.supprimerDossier(id)) Rappels.annuler(this, i);
                rafraichir();
            });
        }).show();
    }

    private boolean sousVoiture(Dossier x) {
        for (Dossier y = x; y != null; y = d.dossier(y.parent)) if (y.parent == 0) return y.nom.equalsIgnoreCase("Voiture");
        return false;
    }

    private String infoVehicule(Dossier v, LocalDate auj) {
        Compteur k = v.c;
        return k.estime() ? "≈ " + Item.nb(k.actuel(auj)) + " km (estimé, relevé : " + Item.nb(k.kmActuel) + " km le "
                + Item.jm(k.kmDate, auj) + ") · ≈ " + Item.nb(k.kmSem) + " km/semaine"
                : Item.nb(k.kmActuel) + " km (relevé le " + Item.jm(k.kmDate, auj) + ")";
    }

    /** Ajout (id = 0, dans le tiroir parent) ou modification d'un véhicule. */
    private void formulaireVehicule(int id, int parent) {
        Dossier ex = id == 0 ? null : d.dossier(id);
        if (id != 0 && ex == null) { retour(1); return; }
        LinearLayout p = page(ex == null ? "Nouveau véhicule" : "Véhicule : " + ex.nom, true);
        int txtType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, num = InputType.TYPE_CLASS_NUMBER;
        EditText nom = champ(p, "Nom du véhicule (ex. Peugeot 308)", ex == null ? "" : ex.nom, txtType);
        Compteur k = ex == null ? new Compteur() : ex.c;
        EditText ka = champ(p, ex != null && k.estime() ? "Dernier kilométrage relevé (réel)" : "Kilométrage actuel du compteur",
                ex == null ? "" : String.valueOf(k.kmActuel), num);
        EditText ks = champ(p, "Km roulés par semaine (approximatif, facultatif)", k.kmSem > 0 ? String.valueOf(k.kmSem) : "", num);
        p.addView(txt("Si tu le renseignes, l'appli estime le kilométrage toute seule et ne te le demande que toutes les 4 semaines. "
                + "Vide ou 0 : pas d'estimation. Ces données servent à tous les éléments km de ce véhicule.", 14, TEXTE, false));
        p.addView(btn("Enregistrer", VERT, BLANC, v -> {
            String n = nom.getText().toString().trim();
            if (n.isEmpty()) { toast("Donne un nom au véhicule."); return; }
            long nka = lng(ka), nks = Math.min(lng(ks), 100000);
            Dossier x = ex != null ? ex : d.ajoutDossier(parent, n);
            x.nom = n;
            x.vehicule = true;
            Compteur c = x.c;
            if (ex == null || nka != c.kmActuel || (c.kmSem == 0 && nks > 0)) c.kmDate = LocalDate.now().toEpochDay(); // nouveau point de départ
            c.kmActuel = nka;
            c.kmSem = nks;
            if (ex == null) c.qDate = LocalDate.now().toEpochDay();
            d.lier();
            for (Item i : d.sousArbre(x.id)) i.envoye = Item.RIEN;
            d.sauver();
            Rappels.verifier(this, false);
            retour(1);
        }));
        if (ex != null)
            p.addView(btn("Supprimer ce véhicule", ROUGE, BLANC, v -> confirmer(
                    "Supprimer « " + ex.nom + " » avec tous ses éléments ?", () -> {
                        for (Item i : d.supprimerDossier(ex.id)) Rappels.annuler(this, i);
                        getSystemService(NotificationManager.class).cancel(ex.id * 10 + 1);
                        retour(1); // la page du véhicule supprimé se referme seule
                    })));
    }

    private void avenir(int rac) {
        Dossier r = d.dossier(rac);
        LinearLayout p = page(r == null ? "À venir" : "À venir : " + r.nom, true);
        LocalDate auj = LocalDate.now();
        List<Item> l = d.sousArbre(rac);
        l.sort((a, b) -> Double.compare(a.score(auj), b.score(auj))); // km : ~50 km/jour pour comparer aux dates
        if (l.isEmpty()) p.addView(txt("Rien à venir. Ajoutez des éléments dans les tiroirs.", 18, TEXTE, false));
        for (Item it : l) {
            int al = it.alerte(auj);
            int c = teinte(al);
            String ligne = it.ligne(auj);
            int k = ligne.indexOf(" : ");
            String date = k < 0 ? "" : ligne.substring(0, k), nom = k < 0 ? ligne : ligne.substring(k + 3);
            android.text.SpannableString sp = new android.text.SpannableString(date + " :\n" + nom + "  " + it.emoji());
            sp.setSpan(new android.text.style.ForegroundColorSpan(c == GRIS ? SOUS : c), 0, date.length() + 1, 0);
            sp.setSpan(new android.text.style.RelativeSizeSpan(.75f), 0, date.length() + 1, 0);
            sp.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), date.length() + 3, sp.length(), 0);
            TextView t = txt("", 21, TEXTE, false);
            t.setText(sp);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, HAUT, 1);
            t.setLayoutParams(tp);
            View barre = new View(this);
            barre.setBackground(fond(c, 4));
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(fond(BLANC, 16));
            row.setElevation(dp(3));
            row.setPadding(dp(10), dp(12), dp(12), dp(12));
            row.addView(barre, new LinearLayout.LayoutParams(dp(5), dp(46)));
            View pt = pastille(c);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(14), dp(14));
            pl.leftMargin = dp(12);
            row.addView(pt, pl);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, HAUT, 1);
            tl.leftMargin = dp(12);
            row.addView(t, tl);
            if (al == 2) {
                Button b = new Button(this);
                b.setText("Urgent");
                b.setAllCaps(false);
                b.setTextSize(14);
                b.setTypeface(null, android.graphics.Typeface.BOLD);
                b.setTextColor(BLANC);
                b.setBackground(fond(ROUGE, 40));
                b.setStateListAnimator(null);
                b.setMinHeight(0);
                b.setMinimumHeight(0);
                b.setPadding(dp(14), 0, dp(14), 0);
                b.setOnClickListener(v -> marquerFait(it));
                row.addView(b, new LinearLayout.LayoutParams(HAUT, dp(36)));
            }
            row.setOnClickListener(v -> aller(() -> detail(it.id)));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(LARGE, HAUT);
            rp.topMargin = dp(12);
            p.addView(row, rp);
        }
    }

    private void ligneDetail(LinearLayout carte, String icone, String lab, String val, boolean sep) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        TextView i = new TextView(this);
        i.setText(icone);
        i.setTextSize(22);
        row.addView(i, new LinearLayout.LayoutParams(dp(40), HAUT));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        TextView a = new TextView(this);
        a.setText(lab);
        a.setTextSize(13);
        a.setTextColor(SOUS);
        TextView b = new TextView(this);
        b.setText(val);
        b.setTextSize(18);
        b.setTextColor(TEXTE);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        c.addView(a);
        c.addView(b);
        row.addView(c, new LinearLayout.LayoutParams(0, HAUT, 1));
        carte.addView(row);
        if (sep) {
            View v = new View(this);
            v.setBackgroundColor(0xFFEEF0F5);
            carte.addView(v, new LinearLayout.LayoutParams(LARGE, dp(1)));
        }
    }

    private void detail(int id) {
        Item it = d.item(id);
        if (it == null) { retour(1); return; }
        LocalDate auj = LocalDate.now();
        LinearLayout p = page(it.emoji() + "  " + it.nom, d.chemin(it.dossier), true);
        // Carte statut
        int al = it.alerte(auj);
        TextView st = txt(it.statut(auj), 22, BLANC, true);
        st.setBackground(fond(al == 0 ? VERTOK : teinte(al), 16));
        st.setElevation(dp(3));
        st.setPadding(dp(18), dp(16), dp(18), dp(16));
        ((LinearLayout.LayoutParams) st.getLayoutParams()).topMargin = dp(14);
        p.addView(st);
        Compteur k = it.cpt();
        LinearLayout carte = new LinearLayout(this);
        carte.setOrientation(LinearLayout.VERTICAL);
        carte.setBackground(fond(BLANC, 16));
        carte.setElevation(dp(3));
        if (it.km()) {
            if (it.veh != null) ligneDetail(carte, "🚗", "Véhicule", it.veh.nom, true);
            ligneDetail(carte, "🔁", "Intervalle", Item.nb(it.intervalle) + " km", true);
            ligneDetail(carte, "🛠️", "Dernier entretien à", Item.nb(it.kmDernier) + " km", true);
            ligneDetail(carte, "🧭", it.estime() ? "Kilométrage (estimé, relevé " + Item.nb(k.kmActuel) + " km le " + Item.jm(k.kmDate, auj) + ")" : "Kilométrage actuel",
                    (it.estime() ? "≈ " : "") + Item.nb(it.actuel(auj)) + " km", true);
            ligneDetail(carte, "📅", "Prochain à", Item.nb(it.kmDernier + it.intervalle) + " km", true);
            ligneDetail(carte, "🔔", "Alertes à", Item.nb(it.s1) + " et " + Item.nb(it.s2) + " km"
                    + (it.question ? " · question chaque semaine" : ""), false);
        } else {
            ligneDetail(carte, "🔁", "Récurrence", "Tous les " + it.periode + (it.mois ? " mois" : " jours"), true);
            ligneDetail(carte, "📅", "Prochaine échéance", Item.jm(it.ref, auj), true);
            if (it.prec != 0) ligneDetail(carte, "↩️", "Précédente", Item.jm(it.prec, auj), true);
            if (it.montant > 0) ligneDetail(carte, "💰", "Montant estimé", Item.nb(it.montant) + " DA", true);
            ligneDetail(carte, "🔔", "Rappels", rappels(it), !it.lien.isEmpty());
            if (!it.lien.isEmpty()) ligneDetail(carte, "🔗", "Lien", it.lien, false);
        }
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LARGE, HAUT);
        cp.topMargin = dp(14);
        p.addView(carte, cp);
        p.addView(btn(it.km() ? "Fait — ✓ Marquer comme Fait" : "Payé / Fait — ✓ Marquer comme Payé", VERT, BLANC, v -> marquerFait(it)));
        if (!it.km() && !it.lien.isEmpty())
            p.addView(btn("Payer en ligne (navigateur)", TEAL, BLANC, v -> reglage(
                    new Intent(Intent.ACTION_VIEW, Uri.parse(it.lien.contains("://") ? it.lien : "https://" + it.lien)))));
        if (it.km()) {
            EditText km = champ(p, it.estime() ? "Vrai kilométrage du compteur (remplace l'estimation" + (it.veh != null ? ", met à jour tout le véhicule)" : ")")
                    : "Kilométrage actuel" + (it.veh != null ? " (met à jour tout le véhicule)" : ""), it.estime() ? "" : String.valueOf(k.kmActuel), InputType.TYPE_CLASS_NUMBER);
            p.addView(btn("Enregistrer le kilométrage", TEAL, BLANC, v -> {
                if (km.getText().toString().trim().isEmpty()) { toast("Tape le kilométrage du compteur."); return; }
                String info = d.majKm(it, lng(km));
                Rappels.verifier(this, false);
                if (info != null) toast(info);
                rafraichir();
            }));
        }
        p.addView(btn("✏️  Modifier", GRISBLEU, TEXTE, v -> aller(() -> formulaire(id, it.dossier, it.type))));
    }

    private static String rappels(Item it) {
        StringBuilder r = new StringBuilder();
        for (int l : it.lesNiveaux()) r.append(r.length() > 0 ? ", " : "").append(l == 0 ? "jour J" : "J-" + l);
        return r.length() == 0 ? "aucun (seulement le retard)" : r.toString();
    }

    private void formulaire(int itemId, int dossierId, String type) {
        Item ex = d.item(itemId);
        Item it = ex != null ? ex : d.nouvelItem(dossierId, type);
        boolean km = it.km();
        LinearLayout p = page(ex == null ? "Nouvel élément" : "Modifier", true);
        int txtType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, num = InputType.TYPE_CLASS_NUMBER;
        EditText nom = champ(p, "Nom (ex. " + (km ? "Vidange" : "Sonelgaz") + ")", it.nom, txtType);
        EditText mont = null, per = null, lien = null, interv = null, kd = null, ka = null, s1 = null, s2 = null, ks = null;
        boolean[] mois = {it.mois};
        long[] prec = {it.prec}, ref = {it.ref};
        CheckBox[] cb = new CheckBox[5];
        CheckBox q = null;
        int[] niv = {30, 15, 7, 2, 0};
        if (!km) {
            mont = champ(p, "Montant estimé en DA (facultatif)", it.montant > 0 ? String.valueOf(it.montant) : "", num);
            p.addView(txt("Revient tous les", 16, TEXTE, false));
            LinearLayout l = new LinearLayout(this);
            per = new EditText(this);
            per.setInputType(num);
            per.setTextSize(20);
            per.setTextColor(TEXTE);
            per.setText(String.valueOf(it.periode));
            l.addView(per, new LinearLayout.LayoutParams(dp(100), HAUT));
            Button u = btn(mois[0] ? "mois" : "jours", GRISBLEU, TEXTE, null);
            u.setOnClickListener(v -> { mois[0] = !mois[0]; u.setText(mois[0] ? "mois" : "jours"); });
            l.addView(u, new LinearLayout.LayoutParams(0, HAUT, 1));
            p.addView(l);
            p.addView(txt("Date de la précédente échéance (facultatif)", 16, TEXTE, false));
            p.addView(dateBtn(prec));
            p.addView(txt("Date de la prochaine échéance (date de référence)", 16, TEXTE, false));
            p.addView(dateBtn(ref));
            lien = champ(p, "Lien de paiement en ligne (facultatif)", it.lien, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            p.addView(txt("Me rappeler :", 18, TEXTE, true));
            String[] nl = {"30 jours avant", "15 jours avant", "7 jours avant", "2 jours avant", "Le jour même"};
            for (int i = 0; i < 5; i++) {
                cb[i] = new CheckBox(this);
                cb[i].setText(nl[i]);
                cb[i].setTextSize(19);
                cb[i].setTextColor(TEXTE);
                cb[i].setChecked(it.aNiveau(niv[i]));
                p.addView(cb[i]);
            }
        } else {
            interv = champ(p, "Tous les combien de km ? (ex. 10000)", String.valueOf(it.intervalle), num);
            if (ex == null && it.veh != null) { // v1.5 : modèles proposés pour un nouvel élément de véhicule
                p.addView(txt("Modèles proposés (touche pour remplir) :", 14, SOUS, false));
                LinearLayout lm = new LinearLayout(this);
                final EditText iv = interv;
                String[][] mod = {{"Vidange", "10000"}, {"Huile de boîte", "40000"}, {"Chaîne / courroie", "70000"}};
                for (String[] m : mod) {
                    Button bm = btn(m[0] + "\n" + Item.nb(Long.parseLong(m[1])) + " km", GRISBLEU, TEXTE, v -> { nom.setText(m[0]); iv.setText(m[1]); });
                    bm.setTextSize(13);
                    bm.setPadding(dp(4), dp(8), dp(4), dp(8));
                    LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, HAUT, 1);
                    mp.leftMargin = dp(3);
                    mp.rightMargin = dp(3);
                    lm.addView(bm, mp);
                }
                p.addView(lm);
            }
            kd = champ(p, "Kilométrage au dernier entretien", String.valueOf(it.kmDernier), num);
            if (it.veh == null) { // hors véhicule : données km propres à l'élément
                ka = champ(p, it.estime() ? "Dernier kilométrage relevé (réel)" : "Kilométrage actuel", String.valueOf(it.c.kmActuel), num);
                ks = champ(p, "Km roulés par semaine (approximatif, facultatif)", it.c.kmSem > 0 ? String.valueOf(it.c.kmSem) : "", num);
                p.addView(txt("Si tu le renseignes, l'appli estime ton kilométrage toute seule et ne te le demande que toutes les 4 semaines. Vide ou 0 : pas d'estimation.", 14, TEXTE, false));
            } else p.addView(txt("Le kilométrage actuel et la moyenne par semaine se règlent sur le véhicule « " + it.veh.nom + " ».", 14, TEXTE, false));
            s1 = champ(p, "Première alerte quand il reste (km)", String.valueOf(it.s1), num);
            s2 = champ(p, "Alerte urgente quand il reste (km)", String.valueOf(it.s2), num);
            q = new CheckBox(this);
            q.setText("Me demander le kilométrage chaque semaine");
            q.setTextSize(19);
            q.setTextColor(TEXTE);
            q.setChecked(it.question);
            p.addView(q);
        }
        final EditText fMont = mont, fPer = per, fLien = lien, fInt = interv, fKd = kd, fKa = ka, fS1 = s1, fS2 = s2, fKs = ks;
        final CheckBox fQ = q;
        p.addView(btn("Enregistrer", VERT, BLANC, v -> {
            String n = nom.getText().toString().trim();
            if (n.isEmpty()) { toast("Donnez un nom à cet élément."); return; }
            if (!km) {
                long pe = lng(fPer);
                if (pe < 1 || pe > 1200) { toast("La période doit être d'au moins 1."); return; }
                if (ref[0] == 0) { toast("Choisissez la date de la prochaine échéance."); return; }
                it.periode = (int) pe;
                it.mois = mois[0];
                if (ref[0] != it.ref) it.jour = LocalDate.ofEpochDay(ref[0]).getDayOfMonth();
                it.ref = ref[0];
                it.prec = prec[0];
                it.montant = lng(fMont);
                it.lien = fLien.getText().toString().trim();
                StringBuilder nv = new StringBuilder();
                for (int i = 0; i < 5; i++) if (cb[i].isChecked()) nv.append(nv.length() > 0 ? "," : "").append(niv[i]);
                it.niveaux = nv.toString();
            } else {
                if (lng(fInt) < 1) { toast("L'intervalle doit être d'au moins 1 km."); return; }
                it.intervalle = lng(fInt);
                it.kmDernier = lng(fKd);
                if (fKa != null) {
                    long nka = lng(fKa), nks = Math.min(lng(fKs), 100000);
                    if (nka != it.c.kmActuel || (it.c.kmSem == 0 && nks > 0)) it.c.kmDate = LocalDate.now().toEpochDay(); // nouveau point de départ
                    it.c.kmActuel = nka;
                    it.c.kmSem = nks;
                }
                it.s1 = (int) Math.min(lng(fS1), 1000000);
                it.s2 = (int) Math.min(lng(fS2), 1000000);
                it.question = fQ.isChecked();
            }
            it.nom = n;
            it.envoye = Item.RIEN;
            if (ex == null) d.items.add(it);
            d.sauver();
            Rappels.verifier(this, false);
            retour(1);
        }));
        if (ex != null)
            p.addView(btn("Supprimer cet élément", ROUGE, BLANC, v -> confirmer("Supprimer « " + ex.nom + " » ?", () -> {
                d.items.remove(ex);
                d.sauver();
                Rappels.annuler(this, ex);
                retour(2); // formulaire + fiche
            })));
    }
}
