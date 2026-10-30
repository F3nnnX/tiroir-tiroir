# Tiroir Tiroir — cahier des charges v1 (22/10/2026)

Demande d'Amestan Fellag (mail du 22/10 16:00), app pour SON téléphone Android, 0 IA, 0 serveur, hors ligne.
Lancement demandé par Félix le 22/10. Rien n'est envoyé à Amestan sans accord de Félix.

## Concept (mots d'Amestan)
Confier sa charge mentale au téléphone. « C'est pas à nous d'aller vers elle, c'est elle qui vient à nous. »
Tiroirs : Maison, Travail, Voiture, Autre ; sous-tiroirs ajoutables ; chaque élément paramétré individuellement.
Exemples : Maison (factures électricité/gaz trimestriel), Voiture (assurance semestrielle, vidange tous les 10 000 km,
contrôle technique annuel, distribution 70 000 km, huiles annexes 40 000 km, plaquettes), Travail (agrément sanitaire
semestriel, agrément camion annuel, salaires mensuel, crédit banque semestriel, stocks emballages/MP/nettoyage hebdo,
désinfection mensuelle).

## Écrans prévus par Amestan
1. Menu : 4 grands tiroirs verticaux (Maison, Travail, Voiture, Autre).
2. Maison : Factures, Entretien, Ajouter (sous-tiroir).
3. Factures : Sonelgaz, Eau, Ajouter une facture.
4. Paramétrage Sonelgaz : date de la précédente facture, date de référence du prochain paiement (choisie par
   l'utilisateur), montant estimé (ex. 20 000 DA). Rappels : J-30 « Ce mois-ci, prévoir le paiement de la facture
   Sonelgaz. Montant estimé : 20 000 DA. » ; J-15 « La facture Sonelgaz approche. » ; J-7 « Pensez à payer votre
   facture Sonelgaz. » (+ lien de paiement en ligne si renseigné) ; J-2 « Attention, délai de paiement imminent. » ;
   J0 et après « Échéance atteinte ou dépassée : pensez à régler votre facture Sonelgaz. » — niveaux paramétrables.
   Bouton « Payé » (même avant la date) → les rappels de cette échéance cessent, l'échéance suivante se calcule.
   Date dépassée sans « Payé » → alerte rouge permanente. Ignorer une notification ≠ payé.
Kilométrage : vidange tous les 10 000 km ; l'app demande d'elle-même une fois par semaine « À combien de km es-tu ? »,
alerte sérieuse « Vidange imminente !! » à l'approche.
Consultation : ouvrir un tiroir montre tout ce qui vient, par priorité (« Dans 3 500 km, vidange classique. Dans trois
mois, vidange annuelle »). Urgent = ça sonne et ça s'affiche seul, quel que soit l'usage du téléphone.

## Périmètre v1
- Parcours complets : Maison › Factures › Sonelgaz (+ Eau), Voiture › Vidange au km. Les 4 tiroirs et « Ajouter »
  fonctionnent partout (sous-tiroirs et éléments libres).
- Types d'élément : « échéance » (période en jours/mois, date de référence, montant DA, lien, niveaux J-30/15/7/2/0)
  et « kilométrage » (intervalle km, km au dernier entretien, km actuel, question hebdo, seuils 1000 / 300 km).
- Écran « À venir » trié par urgence ; rouge = dépassé.
- Notifications : canal normal ; canal urgent (son + plein écran via full-screen intent) pour J-2, J0, dépassé, km < 300.
  Dépassé = notification permanente (ongoing) jusqu'à « Payé/Fait ». Réponse km directement depuis la notification.
- Vérification quotidienne (AlarmManager, ~10:00) + reprogrammation au démarrage du téléphone.
- Français, DA, données locales seulement, aucune permission Internet nécessaire (le lien s'ouvre dans le navigateur).
