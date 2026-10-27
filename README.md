# Qdjar Qdjar (ex-Tiroir Tiroir, v1.4)

v1.4 : refonte visuelle d après les maquettes d Amestan (palette #F4F6F9 / cartes blanches, onglets Tous-Pro-Perso, tiroir Santé, boutons rapides Payé/Fait, fil d Ariane). Logique et rappels inchangés ; le tiroir Santé est ajouté aux installations existantes sans toucher aux données.

Appli Android hors ligne : on range ses échéances et entretiens dans des tiroirs (Maison, Travail, Voiture, Autre),
et le téléphone rappelle tout seul. Aucun serveur, aucune IA, aucune permission Internet. Données dans un fichier
privé du téléphone (`tiroirs.json`).

## Ce qu'elle fait
- 4 tiroirs, sous-tiroirs et éléments ajoutables partout (appui long sur un sous-tiroir : renommer / supprimer).
- Exemples déjà remplis : Maison > Factures > Sonelgaz (20 000 DA, tous les 3 mois) et Eau ; Maison > Entretien ; Voiture > Vidange (10 000 km). Les dates d'exemple sont à régler.
- Élément « échéance » : période en jours ou mois, date de référence, montant estimé en DA, lien de paiement facultatif,
  rappels J-30 / J-15 / J-7 / J-2 / jour J au choix. Bouton « Payé / Fait » : arrête les rappels et passe à l'échéance suivante.
- Élément « kilométrage » : intervalle, km au dernier entretien, km actuel, alertes à 1 000 et 300 km (modifiables),
  question chaque semaine « À combien de km es-tu ? » avec réponse directement dans la notification, bouton « Fait ».
- Véhicules (v1.2) : dans « Voiture », chaque véhicule est un sous-tiroir (« + Ajouter un véhicule » ou « Options ») qui porte le kilométrage, la date du relevé et la moyenne km/semaine, partagés par ses éléments km. Nom, km et moyenne modifiables, suppression avec confirmation. Une question hebdomadaire par véhicule (le nom y figure). Un élément km hors véhicule garde ses propres données. Les données v1.1 sont migrées (l'ancienne « Vidange » passe dans « Ma voiture »).
- Écran « À venir » trié par urgence (rouge = dépassé). Dépassé : notification permanente jusqu'à « Payé / Fait ».
- Ignorer ou balayer une notification n'est jamais un paiement.

## Installer l'APK
1. Copier `build/tiroir-tiroir.apk` sur le téléphone.
2. L'ouvrir ; si Android le demande, autoriser « Installer des applications inconnues » pour l'appli utilisée (Fichiers, Chrome...).
3. À la première ouverture : accepter les **notifications**. Sur Android 14, si le bouton orange « Autoriser les alertes plein écran » apparaît, l'appuyer et activer l'option.
4. Conseil : désactiver l'optimisation de batterie pour Tiroir Tiroir (Réglages > Applis > Batterie > Sans restriction).

Permissions : notifications, alertes plein écran, démarrage du téléphone (pour reprogrammer la vérification quotidienne).

## Compiler
Prérequis : JDK 17, SDK Android dans `~/android-sdk` (android-34, build-tools 34.0.0).
- Clé : `keytool -genkeypair -keystore cle.jks -storetype PKCS12 -storepass:file cle.mdp -keypass:file cle.mdp -alias tiroir -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Tiroir Tiroir"` (le mot de passe est dans `cle.mdp`, à ne pas partager ; garder la même clé pour les mises à jour).
- `./build.sh` produit et vérifie `build/tiroir-tiroir.apk`.

## Limites connues de la v1
- Non testée sur un téléphone réel (compilée et signée seulement).
- La vérification quotidienne vers 10 h est inexacte (Android peut la décaler de quelques minutes à quelques heures) ; elle est aussi refaite à l'ouverture de l'appli.
- Sur Android 14, une notification « permanente » peut être balayée ; elle est reposée à la vérification suivante.
- Alertes plein écran : sur Android 14 l'autorisation doit être donnée à la main.
- Si on paie une échéance très en retard, la suivante est calculée depuis l'ancienne date (elle peut rester en retard : la corriger via « Modifier »).
- Pas de sauvegarde ni d'export, pas de widget, pas de langue autre que le français.
- Le tri de « À venir » compare les km aux dates en comptant environ 50 km par jour.
- Migration v1.1 -> v1.2 non testée sur un vrai téléphone (relue seulement).
