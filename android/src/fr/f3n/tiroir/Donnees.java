package fr.f3n.tiroir;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Stockage local (fichier JSON privé) et accès aux tiroirs / éléments. */
class Donnees {
    private static Donnees inst;
    private static File fichier;
    static Context ctx;

    static final String[] PRINCIPAUX = {"Maison", "Travail", "Voiture", "Santé", "Autres"};

    final ArrayList<Dossier> dossiers = new ArrayList<>();
    final ArrayList<Item> items = new ArrayList<>();
    private int suivant = 1;

    static synchronized Donnees get(Context c) {
        if (inst == null) {
            ctx = c.getApplicationContext();
            fichier = new File(ctx.getFilesDir(), "tiroirs.json");
            inst = new Donnees();
            inst.charger();
        }
        return inst;
    }

    private void charger() {
        try {
            if (fichier.exists()) {
                JSONObject o = new JSONObject(new String(Files.readAllBytes(fichier.toPath()), "UTF-8"));
                suivant = o.getInt("suivant");
                JSONArray d = o.getJSONArray("dossiers");
                for (int i = 0; i < d.length(); i++) {
                    JSONObject x = d.getJSONObject(i);
                    Dossier dd = new Dossier(x.getInt("id"), x.getInt("parent"), x.getString("nom"));
                    dd.vehicule = x.optBoolean("vehicule", false);
                    dd.c.kmActuel = x.optLong("kmActuel");
                    dd.c.kmSem = x.optLong("kmSem");
                    dd.c.kmDate = x.optLong("kmDate", LocalDate.now().toEpochDay());
                    dd.c.qDate = x.optLong("qDate", LocalDate.now().toEpochDay());
                    dossiers.add(dd);
                }
                JSONArray it = o.getJSONArray("items");
                for (int i = 0; i < it.length(); i++) items.add(Item.depuis(it.getJSONObject(i)));
                if (o.optInt("version", 1) < 2) { migrer(); lier(); sauver(); }
                lier();
                boolean modif = false;
                for (Dossier x : dossiers) if (x.parent == 0 && x.nom.equalsIgnoreCase("Autre")) { x.nom = "Autres"; modif = true; } // v1.5
                for (String n : PRINCIPAUX) { // les 5 tiroirs de l'accueil existent toujours (rien d'existant touché)
                    boolean la = false;
                    for (Dossier x : dossiers) if (x.parent == 0 && x.nom.equalsIgnoreCase(n)) la = true;
                    if (!la) { ajoutDossier(0, n); modif = true; }
                }
                if (modif) { lier(); sauver(); }
                return;
            }
        } catch (Exception e) { // fichier abîmé : on le garde de côté et on repart du modèle
            fichier.renameTo(new File(fichier.getParentFile(), "tiroirs.abime.json"));
            dossiers.clear();
            items.clear();
        }
        prerempli();
        sauver();
    }

    synchronized void sauver() {
        try {
            JSONArray d = new JSONArray(), it = new JSONArray();
            for (Dossier x : dossiers) d.put(new JSONObject().put("id", x.id).put("parent", x.parent).put("nom", x.nom)
                    .put("vehicule", x.vehicule).put("kmActuel", x.c.kmActuel).put("kmSem", x.c.kmSem)
                    .put("kmDate", x.c.kmDate).put("qDate", x.c.qDate));
            for (Item x : items) it.put(x.toJson());
            JSONObject o = new JSONObject().put("version", 2).put("suivant", suivant).put("dossiers", d).put("items", it);
            File t = new File(fichier.getPath() + ".tmp");
            Files.write(t.toPath(), o.toString().getBytes("UTF-8"));
            Files.move(t.toPath(), fichier.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (ctx != null) Postit.maj(ctx); // le widget suit chaque modification
        } catch (Exception e) { /* disque plein : on réessaiera à la prochaine modification */ }
    }

    private void prerempli() {
        int maison = ajoutDossier(0, "Maison").id;
        ajoutDossier(0, "Travail");
        int voiture = ajoutDossier(0, "Voiture").id;
        ajoutDossier(0, "Santé");
        ajoutDossier(0, "Autres");
        int fact = ajoutDossier(maison, "Factures").id;
        ajoutDossier(maison, "Entretien");
        long p = LocalDate.now().plusMonths(1).withDayOfMonth(1).toEpochDay(); // date d'exemple, à régler
        Item s = nouvelItem(fact, "E");
        s.nom = "Sonelgaz"; s.montant = 20000; s.periode = 3; s.ref = p; s.jour = 1;
        items.add(s);
        Item e = nouvelItem(fact, "E");
        e.nom = "Eau"; e.periode = 3; e.ref = p; e.jour = 1;
        items.add(e);
        Dossier ma = ajoutDossier(voiture, "Ma voiture");
        ma.vehicule = true;
        Item v = nouvelItem(ma.id, "K");
        v.nom = "Vidange"; v.intervalle = 10000;
        items.add(v);
    }

    /**
     * v1 -> v2 : le kilométrage était partagé par tiroir (le 1er élément km commandait). Dans « Voiture » et ses
     * sous-tiroirs, chaque tiroir qui contient directement des éléments km devient un véhicule portant ces données.
     * Le tiroir « Voiture » lui-même ne peut pas devenir un véhicule : ses éléments km vont dans « Ma voiture ».
     */
    private void migrer() {
        for (Dossier x : new ArrayList<>(dossiers)) {
            if (x.vehicule || !(x.parent == 0 ? x.nom.equalsIgnoreCase("Voiture") : sousVoiture(x.id))) continue;
            ArrayList<Item> ks = new ArrayList<>();
            for (Item i : itemsDe(x.id)) if (i.km()) ks.add(i);
            if (ks.isEmpty()) continue;
            Dossier cible = x;
            if (x.parent == 0) {
                cible = ajoutDossier(x.id, "Ma voiture");
                for (Item i : ks) i.dossier = cible.id;
            }
            cible.vehicule = true;
            cible.c.copie(ks.get(0).c);
        }
    }

    private boolean sousVoiture(int id) {
        for (Dossier x = dossier(id); x != null; x = dossier(x.parent))
            if (x.parent == 0) return x.nom.equalsIgnoreCase("Voiture");
        return false;
    }

    /** Véhicule le plus proche au-dessus (ou égal à) ce tiroir, null si aucun. */
    Dossier vehiculeDe(int dossier) {
        for (Dossier x = dossier(dossier); x != null; x = dossier(x.parent)) if (x.vehicule) return x;
        return null;
    }

    void lier() { for (Item i : items) i.veh = vehiculeDe(i.dossier); }

    Item nouvelItem(int dossier, String type) {
        Item i = new Item();
        i.id = suivant++;
        i.dossier = dossier;
        i.type = type;
        LocalDate auj = LocalDate.now();
        i.c.qDate = auj.toEpochDay();
        i.c.kmDate = auj.toEpochDay();
        i.veh = vehiculeDe(dossier);
        if (!i.km()) {
            LocalDate r = auj.plusDays(30);
            i.ref = r.toEpochDay();
            i.jour = r.getDayOfMonth();
        }
        return i;
    }

    Dossier ajoutDossier(int parent, String nom) {
        Dossier x = new Dossier(suivant++, parent, nom);
        dossiers.add(x);
        return x;
    }

    Dossier dossier(int id) {
        for (Dossier x : dossiers) if (x.id == id) return x;
        return null;
    }

    Item item(int id) {
        for (Item x : items) if (x.id == id) return x;
        return null;
    }

    List<Dossier> enfants(int parent) {
        ArrayList<Dossier> r = new ArrayList<>();
        for (Dossier x : dossiers) if (x.parent == parent) r.add(x);
        return r;
    }

    List<Item> itemsDe(int dossier) {
        ArrayList<Item> r = new ArrayList<>();
        for (Item x : items) if (x.dossier == dossier) r.add(x);
        return r;
    }

    /** Éléments du tiroir et de tous ses sous-tiroirs (rac = 0 : tout). */
    List<Item> sousArbre(int rac) {
        ArrayList<Item> r = new ArrayList<>();
        for (Item x : items) if (rac == 0 || dans(x.dossier, rac)) r.add(x);
        return r;
    }

    private boolean dans(int id, int rac) {
        for (Dossier x = dossier(id); x != null; x = dossier(x.parent)) if (x.id == rac) return true;
        return false;
    }

    String chemin(int id) {
        String r = "";
        for (Dossier x = dossier(id); x != null; x = dossier(x.parent)) r = x.nom + (r.isEmpty() ? "" : " › " + r);
        return r;
    }

    /** Supprime un tiroir, ses sous-tiroirs et leurs éléments ; renvoie les éléments supprimés. */
    List<Item> supprimerDossier(int id) {
        List<Item> morts = sousArbre(id);
        items.removeAll(morts);
        ArrayList<Dossier> a = new ArrayList<>();
        for (Dossier x : dossiers) if (dans(x.id, id)) a.add(x);
        dossiers.removeAll(a);
        sauver();
        return morts;
    }

    /** Nouvelle lecture du compteur : s'applique au véhicule de l'élément (donc à tous ses éléments km), sinon à l'élément seul. */
    String majKm(Item it, long km) {
        String info = it.cpt().releve(km, LocalDate.now());
        sauver();
        return info;
    }

    /** Éléments km (avec question) d'un véhicule, sous-tiroirs compris. */
    List<Item> kmDe(Dossier v) {
        ArrayList<Item> r = new ArrayList<>();
        for (Item x : sousArbre(v.id)) if (x.km() && x.veh == v) r.add(x);
        return r;
    }
}
