package fr.f3n.tiroir;

/** Un tiroir ou sous-tiroir. parent = 0 pour les 4 tiroirs principaux. Un « véhicule » porte les données km de ses éléments. */
class Dossier {
    final int id, parent;
    String nom;
    boolean vehicule;
    final Compteur c = new Compteur();

    Dossier(int id, int parent, String nom) {
        this.id = id;
        this.parent = parent;
        this.nom = nom;
    }
}
