package fr.f3n.tiroir;

import java.time.LocalDate;

/**
 * Données kilométriques d'une voiture : dernier relevé RÉEL (kmActuel, relevé le jour kmDate), moyenne hebdomadaire
 * approximative (kmSem, 0 = pas d'estimation) et date de la dernière question (qDate).
 * Portée par un véhicule (Dossier) et partagée par ses éléments km ; un élément km hors véhicule a la sienne.
 */
class Compteur {
    long kmActuel, kmSem, kmDate = LocalDate.now().toEpochDay(), qDate = LocalDate.now().toEpochDay();

    boolean estime() { return kmSem > 0; }

    /** Kilométrage courant : dernier relevé + estimation (km/semaine * jours écoulés / 7) si kmSem > 0. */
    long actuel(LocalDate a) {
        if (kmSem <= 0) return kmActuel;
        return kmActuel + kmSem * Math.max(0, a.toEpochDay() - kmDate) / 7;
    }

    void copie(Compteur o) {
        kmActuel = o.kmActuel; kmSem = o.kmSem; kmDate = o.kmDate; qDate = o.qDate;
    }

    /**
     * Relevé réel : remplace l'estimation et devient la nouvelle référence. Recalcule la moyenne hebdomadaire :
     * appliquée si >= 2 semaines écoulées, simplement signalée entre 1 et 2 semaines. Renvoie le message d'info (ou null).
     */
    String releve(long km, LocalDate a) {
        long j = a.toEpochDay() - kmDate;
        String info = null;
        if (kmSem > 0 && j >= 7 && km > kmActuel) {
            long nv = Math.round((km - kmActuel) * 7.0 / j);
            if (nv > 0 && j >= 14) {
                info = "Moyenne mise à jour : ≈ " + Item.nb(nv) + " km par semaine (avant : " + Item.nb(kmSem) + ").";
                kmSem = nv;
            } else if (nv > 0) {
                info = "Depuis le dernier relevé, tu roules ≈ " + Item.nb(nv) + " km par semaine (moyenne actuelle : "
                        + Item.nb(kmSem) + "). Modifie-la si tu veux.";
            }
        }
        kmActuel = km;
        kmDate = a.toEpochDay();
        qDate = kmDate;
        return info;
    }
}
