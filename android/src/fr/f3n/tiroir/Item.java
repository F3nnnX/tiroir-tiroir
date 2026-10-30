package fr.f3n.tiroir;

import org.json.JSONObject;

import java.time.LocalDate;

/** Un élément : type "E" (échéance à date) ou "K" (kilométrage). */
class Item {
    static final int RIEN = Integer.MAX_VALUE;
    static final String[] MOIS = {"janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août",
            "septembre", "octobre", "novembre", "décembre"};

    int id, dossier;
    String type = "E", nom = "", designation = "", lien = "", niveaux = "30,15,7,2,0";
    // échéance
    int periode = 3, jour = 1;
    boolean mois = true;
    long ref, prec, montant;
    // kilométrage
    long intervalle = 10000, kmDernier;
    /** Données km propres (utilisées seulement si l'élément n'est dans aucun véhicule). */
    final Compteur c = new Compteur();
    /** Véhicule (sous-tiroir) auquel appartient l'élément, null = aucun. Posé par Donnees.lier(). */
    Dossier veh;
    int s1 = 1000, s2 = 300;
    boolean question = true;
    /** Niveau le plus urgent déjà notifié pour l'échéance en cours (RIEN = aucun). */
    int envoye = RIEN;

    boolean km() { return type.equals("K"); }

    long jours(LocalDate a) { return ref - a.toEpochDay(); }

    /** Données km utilisées : celles du véhicule, sinon celles de l'élément. */
    Compteur cpt() { return veh != null ? veh.c : c; }

    boolean estime() { return km() && cpt().kmSem > 0; }

    long actuel(LocalDate a) { return cpt().actuel(a); }

    long actuel() { return actuel(LocalDate.now()); }

    long reste(LocalDate a) { return kmDernier + intervalle - actuel(a); }

    long reste() { return reste(LocalDate.now()); }

    /** Date approximative où il restera 0 km (null si pas d'estimation ou déjà atteint). */
    LocalDate vers(LocalDate a) {
        long r = reste(a);
        if (!estime() || r <= 0) return null;
        long ks = cpt().kmSem;
        return a.plusDays((r * 7 + ks - 1) / ks);
    }

    /** « 28 octobre » ; l'année (« 28 octobre 2027 ») seulement si ce n'est pas l'année de a. Stockage inchangé. */
    static String jm(LocalDate d, LocalDate a) {
        return d.getDayOfMonth() + " " + MOIS[d.getMonthValue() - 1] + (d.getYear() == a.getYear() ? "" : " " + d.getYear());
    }

    static String jm(long epochDay, LocalDate a) { return jm(LocalDate.ofEpochDay(epochDay), a); }

    /** Nom du véhicule de l'élément, null si hors véhicule. */
    String vehNom() { return veh == null ? null : veh.nom; }

    /** Date affichée dans « À venir » : échéance, ou date estimée pour un km ; null = pas de date (km sans estimation). */
    LocalDate quand(LocalDate a) {
        if (!km()) return LocalDate.ofEpochDay(ref);
        if (reste(a) <= 0) return a;
        return vers(a);
    }

    /** 2 = en retard (rouge), 1 = proche (orange), 0 = pas pressé. */
    int urgence(LocalDate a) {
        if (retard(a)) return 2;
        if (!km()) return jours(a) <= 14 ? 1 : 0;
        LocalDate v = vers(a);
        return reste(a) <= s1 || (v != null && v.toEpochDay() - a.toEpochDay() <= 14) ? 1 : 0;
    }

    /** Ligne de la liste « À venir » : « date : action » (km : « date estimée : action – Véhicule »). */
    String ligne(LocalDate a) {
        String suffixe = veh != null && km() ? " – " + veh.nom : "";
        if (km() && retard(a)) return "maintenant : " + nom + suffixe;
        LocalDate q = quand(a);
        if (q == null) return "à " + nb(kmDernier + intervalle) + " km : " + nom + suffixe;
        return jm(q, a) + " : " + nom + suffixe;
    }

    /** Pastille v1.5 : 2 = rouge (en retard, J-7 ou moins, km restants < 1 000), 1 = orange (8 à 30 jours, km < 3 000), 0 = vert. */
    int alerte(LocalDate a) {
        if (retard(a)) return 2;
        if (km()) { long r = reste(a); return r < 1000 ? 2 : r < 3000 ? 1 : 0; }
        long j = jours(a);
        return j <= 7 ? 2 : j <= 30 ? 1 : 0;
    }

    /** Texte court pour le widget : « J-3 », « en retard », « 450 km »... */
    String court(LocalDate a) {
        if (km()) { long r = reste(a); return r <= 0 ? "km atteints" : nb(r) + " km"; }
        long j = jours(a);
        return j < 0 ? "retard " + (-j) + " j" : j == 0 ? "aujourd'hui" : "J-" + j;
    }

    /** Valeur de la prochaine échéance après « fait » : date ou kilométrage. */
    String prochaine(LocalDate a) {
        return km() ? nb(actuel(a) + intervalle) + " km" : jm(suivant(), a);
    }

    /** Petit emoji selon le nom. */
    String emoji() {
        String n = nom.toLowerCase();
        String[][] t = {{"sonelgaz", "⚡"}, {"électr", "⚡"}, {"elec", "⚡"}, {"gaz", "🔥"}, {"eau", "💧"}, {"seaal", "💧"},
                {"internet", "🌐"}, {"wifi", "🌐"}, {"téléphone", "📱"}, {"telephone", "📱"}, {"mobile", "📱"}, {"loyer", "🏠"},
                {"assurance", "🛡️"}, {"vidange", "🛢️"}, {"pneu", "🛞"}, {"contrôle", "🔧"}, {"controle", "🔧"}, {"distribution", "⚙️"},
                {"impôt", "🧾"}, {"impot", "🧾"}, {"taxe", "🧾"}, {"médecin", "🩺"}, {"dent", "🦷"}, {"vaccin", "💉"}};
        for (String[] x : t) if (n.contains(x[0])) return x[1];
        return km() ? "🚗" : "📌";
    }

    boolean retard(LocalDate a) { return km() ? reste(a) <= 0 : jours(a) < 0; }

    int[] lesNiveaux() {
        String[] t = niveaux.split(",");
        int[] r = new int[t.length];
        int n = 0;
        for (String s : t) if (!s.trim().isEmpty()) r[n++] = Integer.parseInt(s.trim());
        return java.util.Arrays.copyOf(r, n);
    }

    boolean aNiveau(int l) {
        for (int x : lesNiveaux()) if (x == l) return true;
        return false;
    }

    /** Niveau atteint : E = jours restants du palier (-1 = dépassé), K = palier km (0 = atteint). RIEN = rien à signaler. */
    int niveau(LocalDate a) {
        if (km()) {
            long r = reste(a);
            return r <= 0 ? 0 : r <= s2 ? s2 : r <= s1 ? s1 : RIEN;
        }
        long d = jours(a);
        if (d < 0) return -1;
        int best = RIEN;
        for (int l : lesNiveaux()) if (l >= d && l < best) best = l;
        return best;
    }

    boolean urgent(int niv) { return km() ? niv <= s2 : niv <= 2; }

    long suivant() {
        LocalDate d = LocalDate.ofEpochDay(ref);
        if (!mois) return d.plusDays(periode).toEpochDay();
        d = d.plusMonths(periode);
        return d.withDayOfMonth(Math.min(jour, d.lengthOfMonth())).toEpochDay();
    }

    /** Payé / Fait : arrête les rappels et passe à l'échéance suivante. */
    void paye() {
        if (km()) kmDernier = actuel();
        else { prec = ref; ref = suivant(); }
        envoye = RIEN;
    }

    double score(LocalDate a) {
        if (!km()) return jours(a);
        return estime() ? reste(a) * 7.0 / cpt().kmSem : reste(a) / 50.0; // estimé : en jours réels ; sinon ~50 km/jour
    }

    // ---------- textes ----------
    static String nb(long n) {
        String s = Long.toString(Math.abs(n)), r = "";
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) r += " ";
            r += s.charAt(i);
        }
        return (n < 0 ? "-" : "") + r;
    }

    static String pl(long n, String mot) { return nb(n) + " " + mot + (n > 1 ? "s" : ""); }

    static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    /** « la facture Sonelgaz » ; si l'élément n'a pas de désignation (créé en v1.3), juste son nom. */
    String quoi() { return designation.isEmpty() ? nom : designation + " " + nom; }

    String votre() {
        String l = designation.toLowerCase();
        for (String a : new String[]{"la ", "le ", "l'", "l’"})
            if (l.startsWith(a)) return "votre " + designation.substring(a.length());
        return designation;
    }

    String statut(LocalDate a) {
        if (km()) {
            long r = reste(a);
            if (r < 0) return "Dépassé de " + nb(-r) + (estime() ? " km (estimé)" : " km") + " : à faire !";
            if (r == 0) return "Kilométrage atteint : à faire !";
            LocalDate v = vers(a);
            return "Dans " + (estime() ? "≈ " : "") + nb(r) + " km (à " + nb(kmDernier + intervalle) + " km)"
                    + (v == null ? "" : " · vers le " + jm(v, a));
        }
        long d = jours(a);
        String dt = " (" + jm(ref, a) + ")";
        String m = montant > 0 ? " · " + nb(montant) + " DA" : "";
        if (d < 0) return "EN RETARD de " + pl(-d, "jour") + dt + m;
        if (d == 0) return "Échéance aujourd'hui" + m;
        if (d == 1) return "Demain" + dt + m;
        return "Dans " + nb(d) + " jours" + dt + m;
    }

    String message(int niv) {
        if (km()) {
            long r = reste();
            if (r <= 0) return nom + " : le kilométrage est atteint ou dépassé. C'est le moment de le faire !";
            if (niv <= s2) return "Attention, " + nom + " : plus que " + nb(r) + " km !!";
            return "Dans " + nb(r) + " km : " + nom + ".";
        }
        switch (niv) {
            case 30:
                return "Ce mois-ci, prévoir le paiement : " + quoi() + "."
                        + (montant > 0 ? " Montant estimé : " + nb(montant) + " DA." : "");
            case 15:
                return cap(quoi()) + " approche.";
            case 7:
                return (designation.isEmpty() ? "Pensez à régler : " + nom : "Pensez à payer " + votre() + " " + nom) + "."
                        + (lien.isEmpty() ? "" : " Paiement en ligne : " + lien);
            case 2:
                return "Attention, délai de paiement imminent.";
            default:
                return "Échéance atteinte ou dépassée : pensez à régler " + (designation.isEmpty() ? "« " + nom + " »" : quoi()) + ".";
        }
    }

    // ---------- JSON ----------
    JSONObject toJson() throws Exception {
        return new JSONObject().put("id", id).put("dossier", dossier).put("type", type).put("nom", nom)
                .put("designation", designation).put("lien", lien).put("niveaux", niveaux)
                .put("periode", periode).put("jour", jour).put("mois", mois).put("ref", ref).put("prec", prec)
                .put("montant", montant).put("intervalle", intervalle).put("kmDernier", kmDernier)
                .put("kmActuel", c.kmActuel).put("qDate", c.qDate).put("s1", s1).put("s2", s2)
                .put("kmSem", c.kmSem).put("kmDate", c.kmDate).put("question", question).put("envoye", envoye);
    }

    static Item depuis(JSONObject o) {
        Item i = new Item();
        i.id = o.optInt("id");
        i.dossier = o.optInt("dossier");
        i.type = o.optString("type", "E");
        i.nom = o.optString("nom");
        i.designation = o.optString("designation", "la facture");
        i.lien = o.optString("lien");
        i.niveaux = o.optString("niveaux", "30,15,7,2,0");
        i.periode = o.optInt("periode", 3);
        i.jour = o.optInt("jour", 1);
        i.mois = o.optBoolean("mois", true);
        i.ref = o.optLong("ref");
        i.prec = o.optLong("prec");
        i.montant = o.optLong("montant");
        i.intervalle = o.optLong("intervalle", 10000);
        i.kmDernier = o.optLong("kmDernier");
        i.c.kmActuel = o.optLong("kmActuel");
        i.c.qDate = o.optLong("qDate");
        i.c.kmSem = o.optLong("kmSem", 0);
        i.c.kmDate = o.optLong("kmDate", LocalDate.now().toEpochDay());
        i.s1 = o.optInt("s1", 1000);
        i.s2 = o.optInt("s2", 300);
        i.question = o.optBoolean("question", true);
        i.envoye = o.optInt("envoye", RIEN);
        return i;
    }
}
