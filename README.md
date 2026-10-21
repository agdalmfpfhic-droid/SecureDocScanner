# SecureDoc Scanner

Application Android native de numérisation de documents : scans illimités, sans publicité, sans compte, hors ligne, avec chiffrement local de tous les documents.

Kotlin · Jetpack Compose · Material 3 · MVVM + Repository · Room + SQLCipher · Android Keystore · EncryptedSharedPreferences · EncryptedFile · ML Kit Document Scanner · Android 8.0 minimum (API 26).

---

## 1. Structure du projet

```
SecureDocScanner/
├── settings.gradle.kts
├── build.gradle.kts                 # versions des plugins
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts             # dépendances
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/                     # thème, icône adaptative, règles de sauvegarde, FileProvider
        └── java/com/securedoc/scanner/
            ├── SecureDocApp.kt      # Application + AppContainer (injection manuelle)
            ├── MainActivity.kt      # FLAG_SECURE, cycle de verrouillage
            ├── security/
            │   ├── SecurePrefs.kt          # MasterKey Keystore + EncryptedSharedPreferences + clé SQLCipher
            │   ├── EncryptedFileStore.kt   # pages chiffrées (EncryptedFile) + suppression sécurisée
            │   ├── PinManager.kt           # PIN haché PBKDF2, anti-force brute
            │   ├── BiometricHelper.kt      # BiometricPrompt (empreinte, visage, code/schéma appareil)
            │   └── LockController.kt       # verrouillage auto après mise en arrière-plan
            ├── data/
            │   ├── model/Entities.kt       # Document, Page, Category, SecureItem
            │   ├── model/Enums.kt          # profils de scan, filtres, formats
            │   ├── db/AppDatabase.kt       # Room + SupportOpenHelperFactory (SQLCipher)
            │   ├── db/Daos.kt
            │   ├── DocumentRepository.kt   # création, édition, fusion, export, partage
            │   └── SettingsRepository.kt
            ├── scan/DocumentScanner.kt     # lanceur ML Kit Document Scanner
            ├── imaging/                    # filtres (couleur, gris, N&B adaptatif, texte net, reçu)
            ├── pdf/PdfBuilder.kt           # PDF multipage avec PdfDocument
            └── ui/
                ├── AppRoot.kt              # navigation + barre d'onglets
                ├── ViewModels.kt           # un ViewModel par écran
                ├── components/             # vignettes, liste, pavé PIN / AuthGate
                └── screens/                # Accueil, Scan, Édition, Bibliothèque, Coffre-fort, Paramètres
```

## 2. Fonctionnalités

| Domaine | Ce qui est fait |
|---|---|
| Scan | ML Kit en mode complet : détection des bords, recadrage, perspective, rotation auto, filtres, retrait des ombres et taches. Aucune limite de pages ni de documents. |
| Profils | A4, A5, reçu/facture, passeport, CIN, permis, carte bancaire, contrat, note manuscrite, livre. Chaque profil fixe le format de page PDF (ex. ID-1 85,6 × 54 mm pour les cartes), le filtre par défaut, le dossier, et le recto/verso pour les cartes. |
| Filtres | Original, Couleur, Niveaux de gris, Noir et blanc (seuil adaptatif), Texte net (fond normalisé + netteté), Mode reçu. Non destructifs : l'original chiffré est conservé. |
| Édition | Rotation manuelle, réordonner, supprimer, ajouter (scan ou galerie), renommer, fusionner deux documents, changer de dossier, ranger au coffre-fort. |
| Aperçu | Après le scan, aperçu des pages avant enregistrement ; « Annuler » efface les images temporaires. |
| Bibliothèque | Recherche par nom, type, dossier ou date ; filtres par dossier, type et période. |
| Dossiers | Administratif, Banque, Identité, Travail, Santé, Autres. |
| Coffre-fort | CIN, passeport, permis, carte bancaire rangés automatiquement ; authentification à chaque ouverture ; masqués de la bibliothèque. |
| Export | PDF, JPEG ou PNG vers un emplacement choisi par l'utilisateur (Storage Access Framework). |
| Partage | Uniquement à la demande, après une confirmation qui explique que le PDF sort de l'app ; PDF temporaire effacé après le partage et à chaque démarrage. |
| Sécurité | Verrouillage PIN + biométrie / schéma de l'appareil, re-verrouillage après 15 s, captures d'écran bloquées, contenu masqué dans les applis récentes, suppression sécurisée, effacement total. |
| Paramètres | Sécurité, format d'export par défaut, qualité d'image, thème clair/sombre/système, langue, politique de confidentialité. |

## 3. Installation

**Prérequis** : Android Studio Ladybug (2024.2) ou plus récent, JDK 17 (fourni par Android Studio), un téléphone Android 8.0+ avec les services Google Play (requis par ML Kit Document Scanner) et au moins 1,7 Go de RAM.

1. Décompressez l'archive et ouvrez le dossier `SecureDocScanner` dans Android Studio (**File > Open**).
2. Si Android Studio signale que le wrapper Gradle est absent, acceptez de le générer (ou lancez `gradle wrapper --gradle-version 8.9` dans le dossier).
3. Laissez la synchronisation Gradle télécharger les dépendances.
4. Sur le téléphone : **Paramètres > À propos > Numéro de build** (7 appuis) pour activer les options développeur, puis activez **Débogage USB**.
5. Branchez le téléphone, choisissez-le dans la barre d'outils et cliquez sur **Run ▶**.

**APK installable sans Android Studio** : `Build > Build App Bundle(s) / APK(s) > Build APK(s)`, puis copiez `app/build/outputs/apk/debug/app-debug.apk` sur le téléphone et ouvrez-le (autorisez l'installation depuis cette source). Pour une version signée et optimisée : `Build > Generate Signed App Bundle / APK`, variante `release` (R8 + réduction des ressources).

Au premier scan, les services Google Play peuvent télécharger le module ML Kit (une seule fois). Ce téléchargement est fait par Play services, pas par SecureDoc, qui n'a aucune permission réseau.

## 4. Plan de test

| # | Test | Résultat attendu |
|---|---|---|
| 1 | Accueil > Scanner > Document A4, scanner 3 pages | Aperçu de 3 pages, puis document enregistré et ouvert en édition |
| 2 | Scanner une CIN | Limité à 2 pages, rangé dans le Coffre-fort, absent de la Bibliothèque |
| 3 | Importer depuis la galerie (Reçu) | Sélecteur de photos sans demande de permission, filtre « Mode reçu » |
| 4 | Édition : pivoter, changer de filtre, déplacer, supprimer une page | Aperçu et vignettes mis à jour |
| 5 | Fusionner deux documents | Pages de la source ajoutées à la fin, source supprimée |
| 6 | Exporter en PDF puis en JPEG | Sélecteur système ; fichiers lisibles à l'emplacement choisi |
| 7 | Partager | Dialogue de confirmation, puis sélecteur Android |
| 8 | Paramètres : définir un PIN, quitter l'app 20 s, revenir | Écran de verrouillage |
| 9 | 5 codes faux | Attente de 30 s imposée |
| 10 | Faire une capture d'écran | Bloquée ; vignette noire dans les applis récentes |
| 11 | Onglet Coffre-fort, puis un autre onglet, puis retour | Authentification redemandée |
| 12 | Mode avion, refaire les tests 1 à 7 | Tout fonctionne |
| 13 | `adb shell run-as com.securedoc.scanner ls files/pages` (build debug) | Fichiers `.enc` illisibles ; `securedoc.db` illisible sans clé |
| 14 | Effacer tous les documents | Bibliothèque et coffre vides, dossier `pages` vide |

## 5. Permissions

| Permission | Pourquoi |
|---|---|
| `USE_BIOMETRIC` | Déverrouiller l'app et le coffre-fort par empreinte ou visage. Aucune donnée biométrique n'est lue par l'app. |
| `INTERNET` | **Retirée** explicitement (`tools:node="remove"`) au cas où une bibliothèque l'ajouterait. |
| `CAMERA` | **Non demandée** : ML Kit Document Scanner gère son propre flux caméra. |
| Stockage / photos | **Non demandées** : galerie via le Photo Picker, export via le Storage Access Framework. |

Sauvegarde Android désactivée (`allowBackup=false`) et règles `data_extraction_rules.xml` qui excluent tout de la sauvegarde cloud et du transfert entre appareils.

## 6. Détails de sécurité et limites connues

- **Base de données** : SQLCipher (AES-256). La phrase de passe (256 bits aléatoires) est stockée dans EncryptedSharedPreferences, chiffrée par une clé AES-256-GCM d'Android Keystore. `PRAGMA secure_delete` activé.
- **Pages** : EncryptedFile (AES-256-GCM-HKDF, segments 4 Ko). Les images sont ré-encodées à l'import, ce qui supprime les métadonnées EXIF.
- **PIN** : seul un hachage PBKDF2-HMAC-SHA256 salé (120 000 itérations) est conservé. Pas de récupération possible : un PIN oublié sans biométrie oblige à effacer les données de l'app.
- **Suppression sécurisée** : écrasement aléatoire puis suppression. Sur mémoire flash, l'écrasement physique n'est pas garanti (répartition de l'usure), mais les données résiduelles sont chiffrées.
- **Captures d'écran** : `FLAG_SECURE` sur toute l'activité, plus simple et plus sûr qu'un blocage écran par écran.
- **Scanner** : ML Kit Document Scanner fonctionne dans les services Google Play. Les images restent sur l'appareil ; les copies temporaires en clair créées par ML Kit dans le cache sont effacées dès l'enregistrement ou l'annulation.
- **Partage** : le PDF partagé n'est plus sous le contrôle de l'app une fois transmis à une autre application.
- **Recadrage** : il se fait dans l'éditeur de ML Kit au moment du scan (poignées d'angle). L'écran d'édition propose rotation et filtres, pas de nouveau recadrage manuel.
- **Langue** : l'interface est en français uniquement dans cette version.
- **Taille de l'APK** : release avec R8 et `shrinkResources`, une seule langue de ressources, pas de framework d'injection. Le modèle ML Kit n'est pas embarqué (fourni par Play services).

## 7. Politique de confidentialité

**SecureDoc Scanner : vos documents restent sur votre téléphone.**

- L'application n'a pas accès à internet. Aucune image, aucun PDF, aucun texte et aucune métadonnée n'est envoyé à un serveur.
- Le scan est réalisé sur l'appareil par ML Kit Document Scanner (services Google Play).
- Les documents sont chiffrés sur l'appareil ; les clés sont protégées par Android Keystore.
- Pas de compte, pas de publicité, pas de statistiques d'usage, pas de télémétrie.
- Aucune sauvegarde cloud automatique.
- Un document ne quitte l'appareil que si vous l'exportez ou le partagez vous-même, après confirmation.
- La position GPS et les autres métadonnées des photos sont supprimées.
- Vous pouvez tout effacer depuis les Paramètres ; désinstaller l'application supprime aussi toutes les données.

Cette politique est aussi consultable dans l'application : **Paramètres > Confidentialité**.
