package fr.f3n.tiroir;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;

/** Notifications, vérification quotidienne et réception des réponses. */
class Rappels {
    static final String NORMAL = "normal", SILENCE = "silence";
    private static final int IMM = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;

    /** Alarme quotidienne (et relance au démarrage du téléphone / après mise à jour). */
    public static class Alarme extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent in) {
            verifier(c, true);
            programmer(c);
        }
    }

    /** v1.6 : chaque jour à 11 h ; le dimanche tous les véhicules, les autres jours seulement ceux « rappelés demain ». */
    public static class Hebdo extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent in) {
            try {
                programmerHebdo(c);
                StringBuilder ids = new StringBuilder();
                boolean dim = LocalDate.now().getDayOfWeek() == java.time.DayOfWeek.SUNDAY;
                long j = LocalDate.now().toEpochDay();
                for (Dossier v : Donnees.get(c).dossiers) {
                    if (!v.vehicule) continue;
                    long rapp = c.getSharedPreferences("tt", 0).getLong("rapp_" + v.id, -1);
                    if (dim || (rapp >= 0 && rapp <= j)) ids.append(ids.length() > 0 ? "," : "").append(v.id);
                }
                if (ids.length() == 0) return;
                c.getSharedPreferences("tt", 0).edit().putString("hebdo_ids", ids.toString()).apply();
                NotificationManager nm = c.getSystemService(NotificationManager.class);
                canaux(c);
                if (!nm.areNotificationsEnabled()) return;
                Intent i = new Intent(c, Principal.class).putExtra("hebdo", true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                nm.notify(990001, new Notification.Builder(c, canal(c)).setSmallIcon(android.R.drawable.ic_menu_edit)
                        .setContentTitle("Quels kilométrages ont atteint vos véhicules ?")
                        .setContentText("Touchez pour saisir les compteurs.")
                        .setContentIntent(PendingIntent.getActivity(c, 990001, i, IMM)).setAutoCancel(true).build());
            } catch (Exception e) { /* ne jamais planter dans un récepteur */ }
        }
    }

    /** Prochain passage à 11 h (inexact, tolère le mode Doze). */
    static void programmerHebdo(Context c) {
        LocalDateTime t = LocalDate.now().atTime(11, 0);
        if (!t.isAfter(LocalDateTime.now())) t = t.plusDays(1);
        long ms = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        PendingIntent pi = PendingIntent.getBroadcast(c, 8, new Intent(c, Hebdo.class), IMM);
        c.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi);
    }

    /** Réponse tapée directement dans la notification « À combien de km es-tu ? ». */
    public static class Reponse extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent in) {
            Bundle r = RemoteInput.getResultsFromIntent(in);
            Donnees d = Donnees.get(c);
            int idV = in.getIntExtra("veh", 0), idI = in.getIntExtra("item", 0);
            Dossier v = idV != 0 ? d.dossier(idV) : null;
            Item it = idI != 0 ? d.item(idI) : null;
            if (r == null || (v == null && it == null)) return;
            Compteur cp = v != null ? v.c : it.cpt();
            int id = v != null ? v.id : it.id;
            String nom = v != null ? v.nom : it.nom;
            CharSequence cs = r.getCharSequence("km");
            String t = cs == null ? "" : cs.toString().replaceAll("[^0-9]", "");
            if (t.isEmpty() || t.length() > 7) { question(c, id, nom, cp, v != null, true); return; }
            String info = cp.releve(Long.parseLong(t), LocalDate.now());
            d.sauver();
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            nm.cancel(id * 10 + 1);
            if (info != null) nm.notify(id * 10 + 5, new Notification.Builder(c, canal(c))
                    .setSmallIcon(android.R.drawable.ic_menu_edit).setContentTitle(nom)
                    .setContentText(info).setStyle(new Notification.BigTextStyle().bigText(info))
                    .setContentIntent(ouvrir(c, v != null ? "veh" : "item", id)).setAutoCancel(true).build());
            verifier(c, false);
        }
    }

    /** v1.5 : plus aucun canal « réveil ». Rappels = son de notification par défaut ; Silence = sans son ni vibration (ne pas déranger). */
    static void canaux(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.deleteNotificationChannel("urgent"); // ancien canal d'alarme (v1.0 à v1.4)
        nm.createNotificationChannel(new NotificationChannel(NORMAL, "Rappels", NotificationManager.IMPORTANCE_DEFAULT));
        NotificationChannel s = new NotificationChannel(SILENCE, "Rappels (plage ne pas déranger)", NotificationManager.IMPORTANCE_LOW);
        s.setSound(null, null);
        s.enableVibration(false);
        nm.createNotificationChannel(s);
    }

    // ---------- plage « Ne pas déranger » (minutes depuis minuit ; défaut 21:00 - 07:00) ----------
    static int dndDebut(Context c) { return c.getSharedPreferences("tt", 0).getInt("dnd_debut", 21 * 60); }

    static int dndFin(Context c) { return c.getSharedPreferences("tt", 0).getInt("dnd_fin", 7 * 60); }

    static void dnd(Context c, int debut, int fin) {
        c.getSharedPreferences("tt", 0).edit().putInt("dnd_debut", debut).putInt("dnd_fin", fin).apply();
    }

    static String hhmm(int m) { return String.format(java.util.Locale.FRANCE, "%02d:%02d", m / 60, m % 60); }

    static boolean silence(Context c) {
        LocalDateTime n = LocalDateTime.now();
        int m = n.getHour() * 60 + n.getMinute(), d = dndDebut(c), f = dndFin(c);
        if (d == f) return false;
        return d < f ? (m >= d && m < f) : (m >= d || m < f);
    }

    /** Canal à utiliser maintenant : silencieux dans la plage, sinon son par défaut. */
    static String canal(Context c) { return silence(c) ? SILENCE : NORMAL; }

    /** Programme la prochaine vérification vers 10 h (inexacte, tolère le mode Doze). */
    static void programmer(Context c) {
        LocalDateTime t = LocalDate.now().atTime(10, 0);
        if (!t.isAfter(LocalDateTime.now())) t = t.plusDays(1);
        long ms = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        PendingIntent pi = PendingIntent.getBroadcast(c, 7, new Intent(c, Alarme.class), IMM);
        c.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi);
        programmerHebdo(c);
    }

    /**
     * Parcourt tous les éléments. quotidien = appel de 10 h / démarrage : on reposte aussi les retards
     * et on pose la question hebdomadaire. Sinon, seulement les nouveaux niveaux atteints.
     */
    static void verifier(Context c, boolean quotidien) {
        try {
            Donnees d = Donnees.get(c);
            canaux(c);
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            boolean ok = nm.areNotificationsEnabled();
            LocalDate auj = LocalDate.now();
            boolean change = false;
            for (Item it : new ArrayList<>(d.items)) {
                int niv = it.niveau(auj);
                if (niv > it.envoye) { it.envoye = niv; change = true; } // situation améliorée (km corrigé...)
                if (niv == Item.RIEN) { nm.cancel(it.id * 10); continue; }
                boolean nouveau = niv < it.envoye;
                boolean retard = it.retard(auj);
                if (!ok || !(nouveau || (quotidien && retard))) continue;
                poster(c, nm, it, niv, nouveau, retard);
                if (nouveau) { it.envoye = niv; change = true; }
            }
            if (quotidien && ok) {
                long j = auj.toEpochDay();
                // v1.6 : la question hebdomadaire des véhicules est remplacée par la saisie du dimanche 11 h (Hebdo)
                for (Item it : d.items) { // élément km hors véhicule : sa propre question
                    if (!it.km() || it.veh != null || !it.question) continue;
                    if (j - it.c.qDate < (it.c.estime() ? 28 : 7)) continue;
                    question(c, it.id, it.nom, it.c, false, false);
                    it.c.qDate = j;
                    change = true;
                }
            }
            if (change) d.sauver();
        } catch (Exception e) { /* ne jamais planter dans un récepteur */ }
    }

    private static boolean aQuestion(java.util.List<Item> l) {
        for (Item x : l) if (x.question) return true;
        return false;
    }

    static void annuler(Context c, Item it) {
        c.getSystemService(NotificationManager.class).cancel(it.id * 10);
    }

    /** cle = "item" (fiche d'un élément) ou "veh" (page d'un véhicule). Les id d'éléments et de tiroirs sont tous distincts. */
    private static PendingIntent ouvrir(Context c, String cle, int id) {
        Intent i = new Intent(c, Principal.class).putExtra(cle, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, id * 10 + 2, i, IMM);
    }

    private static void poster(Context c, NotificationManager nm, Item it, int niv, boolean nouveau, boolean retard) {
        String texte = it.message(niv);
        // v1.5 : notification standard (ping discret), jamais d'alarme, de plein écran ni de notification permanente
        Notification.Builder b = new Notification.Builder(c, canal(c))
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(retard ? it.nom + " : en retard" : it.nom)
                .setContentText(texte)
                .setStyle(new Notification.BigTextStyle().bigText(texte))
                .setContentIntent(ouvrir(c, "item", it.id))
                .setAutoCancel(true)
                .setOnlyAlertOnce(!nouveau)
                .setCategory(Notification.CATEGORY_REMINDER);
        if (!it.km() && !it.lien.isEmpty()) {
            String u = it.lien.contains("://") ? it.lien : "https://" + it.lien;
            PendingIntent pi = PendingIntent.getActivity(c, it.id * 10 + 6, new Intent(Intent.ACTION_VIEW, Uri.parse(u)), IMM);
            b.addAction(new Notification.Action.Builder(Icon.createWithResource(c, android.R.drawable.ic_menu_send), "Payer en ligne", pi).build());
        }
        nm.notify(it.id * 10, b.build()); // une notification n'est JAMAIS un paiement : aucune action « Payé » ici
    }

    /** Question « À combien de km es-tu ? » pour un véhicule (veh = true, id = tiroir) ou un élément km isolé (id = élément). */
    static void question(Context c, int id, String nom, Compteur cp, boolean veh, boolean erreur) {
        RemoteInput ri = new RemoteInput.Builder("km").setLabel("Kilométrage actuel").build();
        // explicite, requis pour un PendingIntent mutable ; code de requête et extra propres à chaque véhicule
        Intent i = new Intent(c, Reponse.class).putExtra(veh ? "veh" : "item", id);
        PendingIntent pi = PendingIntent.getBroadcast(c, id * 10 + 4, i,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Action a = new Notification.Action.Builder(
                Icon.createWithResource(c, android.R.drawable.ic_menu_edit), "Répondre", pi).addRemoteInput(ri).build();
        String q = cp.estime() ? nom + " : on estime que tu es à ≈ " + Item.nb(cp.actuel(LocalDate.now())) + " km. Quel est le vrai kilométrage ?"
                : nom + " : à combien de km es-tu ?";
        String t = (erreur ? "Je n'ai pas compris : tape un nombre. " : "") + q;
        c.getSystemService(NotificationManager.class).notify(id * 10 + 1, new Notification.Builder(c, canal(c))
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentTitle("Question de la semaine")
                .setContentText(t)
                .setStyle(new Notification.BigTextStyle().bigText(t))
                .setContentIntent(ouvrir(c, veh ? "veh" : "item", id))
                .addAction(a)
                .setAutoCancel(true)
                .build());
    }
}
