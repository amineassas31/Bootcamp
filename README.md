# Rondes NFC — POC supervision de rondes en musee

POC repondant a l'appel d'offres "supervision de rondes par patchs NFC" : un gardien
scanne un patch NFC avec son telephone, un PC de securite voit l'etat de toutes les
salles se mettre a jour en temps reel (WebSocket).

Deux clients de scan sont fournis, au choix pour la demo :

- **Application Android native** (`android/`) — recommandee pour la soutenance : pas de
  contrainte HTTPS, installation directe sur un telephone via USB/`adb`.
- **Page web mobile** (`scan.html`, Web NFC) — zero installation, mais exige HTTPS (voir
  plus bas), pratique pour le poste d'enrollment (`enroll.html`) et le dashboard PC.

## Stack

- **Backend** : Kotlin + [Ktor](https://ktor.io) (serveur Netty), persistance [Exposed](https://github.com/JetBrains/Exposed) + H2 (fichier local)
- **Scan NFC mobile** : app Android native (`NfcAdapter.enableReaderMode`) **ou** Web NFC (`NDEFReader`) — les deux envoient le meme `tagUid` (UID materiel du tag formate en hexa colonne), donc un patch enrole depuis l'un fonctionne avec l'autre
- **Supervision temps reel** : WebSocket, poussee a chaque scan + tick client cote navigateur pour l'ecoulement du temps
- **Frontend web** : HTML/CSS/JS vanilla servis directement par Ktor (`src/main/resources/static`)

Voir `docs/ARCHITECTURE.md` pour la justification detaillee des choix techniques.

## Lancer le serveur

Prerequis : JDK 21 (un JDK est deja detecte a `C:\Program Files\Java\jdk-21` sur ce poste).

```
mvn -DskipTests package
java -jar target/rondes-nfc.jar
```

Le serveur ecoute sur `http://localhost:8080` (port modifiable via la variable d'env `PORT`).
La base H2 est creee dans `./data/rondes-nfc.mv.db` au premier lancement, avec un jeu de
donnees de demo (voir plus bas). Supprimer le dossier `data/` pour repartir de zero.

## Comptes de demo (seed)

| Badge | PIN  | Role          |
|-------|------|---------------|
| DIR01 | 0000 | DIRECTION     |
| CP01  | 1111 | CHEF_DE_POSTE |
| G001  | 2222 | GARDIEN       |
| G002  | 3333 | GARDIEN       |

5 salles sont pre-creees (seuils d'alerte differents par salle) mais **aucun patch n'est
pre-associe** : l'association patch <-> salle se fait en direct via la page `enroll.html`,
ca fait partie de la demo (fonctionnalite "gestion des salles et des patchs").

## Pages

| URL              | Usage                                          | Roles                          |
|------------------|-------------------------------------------------|---------------------------------|
| `/index.html`    | Portail de navigation                          | tous                             |
| `/login.html`    | Connexion badge + PIN                          | tous                             |
| `/scan.html`     | Scan NFC d'une salle (poste gardien, mobile)   | GARDIEN, CHEF_DE_POSTE, DIRECTION|
| `/enroll.html`   | Associer un patch NFC a une salle              | CHEF_DE_POSTE, DIRECTION         |
| `/dashboard.html`| Ecran de supervision temps reel (PC securite)  | CHEF_DE_POSTE, DIRECTION         |
| `/history.html`  | Historique des passages                        | tous (gardien = ses scans uniquement) |

## Application Android (client de scan recommande pour la demo)

Code dans `android/` — projet Gradle independant du backend (module Maven), a ouvrir
separement dans Android Studio (ou a construire en ligne de commande, voir ci-dessous).

**Pourquoi une app native en plus du Web NFC** : `NfcAdapter.enableReaderMode` (API Android)
n'a pas la contrainte de contexte securise du Web NFC — l'app parle en `http://` direct a
l'IP du PC sur le reseau local du musee, sans tunnel HTTPS ni certificat a poser le jour de
la demo. C'est le chemin le plus fiable pour la soutenance.

### Construire et installer

```
cd android
./gradlew assembleDebug
# APK genere dans app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Ou, plus simple : ouvrir le dossier `android/` dans Android Studio, brancher le telephone en
USB (mode debogage USB active), et lancer via le bouton Run.

### Utilisation

1. Lancer le backend (`java -jar target/rondes-nfc.jar`) sur le PC, noter son IP locale
   (`ipconfig` -> IPv4, ex. `192.168.1.42`).
2. Ouvrir l'app sur le telephone, renseigner `http://<IP-du-PC>:8080` comme adresse serveur,
   se connecter avec un badge/PIN de la table de demo.
3. Approcher le telephone d'un patch NFC deja enrole (via `enroll.html` sur le PC, cf.
   ci-dessus) — le nom de la salle controlee s'affiche immediatement, et le dashboard
   (`dashboard.html`) se met a jour en temps reel sur l'ecran de supervision.

Le format d'identifiant de tag (UID materiel en hexa separe par `:`) est identique entre
l'app Android et Web NFC : un patch enrole depuis l'un des deux clients est reconnu par
l'autre sans reconfiguration.

**Limite assumee** : app de demo, sans build de release signe ni distribution (hors
perimetre d'un POC d'une semaine) ; `usesCleartextTraffic="true"` dans le manifest, a
restreindre en production a l'IP du serveur interne une fois celui-ci derriere TLS (cf.
`docs/SECURITY.md`).

## Demo live avec le Web NFC (`scan.html`, alternative sans installation)

Le Web NFC (`navigator.NDEFReader`) **exige un contexte securise** : `https://` ou
`http://localhost`. Un telephone qui ouvre l'IP locale du PC (`http://192.168.x.x:8080`)
n'est PAS considere comme securise -> le scan NFC ne s'activera pas dans le navigateur
(l'app Android ci-dessus n'a pas cette contrainte).

Deux options si vous demontrez quand meme via le navigateur :

1. **Le plus simple : ngrok / Cloudflare Tunnel**
   ```
   ngrok http 8080
   ```
   Ouvrir l'URL `https://xxxx.ngrok-free.app` depuis le telephone (Chrome Android, NFC active
   dans les parametres du telephone). Fonctionne immediatement, aucune configuration cote serveur.

2. **Reseau local + certificat de confiance (mkcert)** si pas d'acces internet le jour J :
   generer un certificat local de confiance et le charger dans Ktor (`sslConnector`), puis
   installer le certificat racine sur le telephone de demo. Plus robuste (pas de dependance
   a un service tiers) mais demande une preparation en amont.

**Plan B (panne reseau / NFC recalcitrant le jour J)** : le fallback "saisie manuelle" visible
sur `scan.html`/`enroll.html` quand `NDEFReader` est absent permet de rejouer la demo sans tag
physique. A ne jamais livrer active en production (voir `docs/SECURITY.md`).

## Ce qui est implemente (mapping avec le cahier des charges)

**Must have** : scan NFC reel + identification salle (app Android **ou** Web NFC),
identification du gardien (login badge/PIN), horodatage + enregistrement persistant,
dashboard (temps ecoule / dernier controleur / statut vert-orange-rouge), mise a jour
automatique (WebSocket).

**Should have** : CRUD salles + association patch<->salle (`enroll.html`, `/api/rooms`,
`/api/patches`), historique consultable filtrable par salle/gardien/periode
(`history.html`, `/api/history`), comptes/roles (GARDIEN / CHEF_DE_POSTE / DIRECTION),
seuils d'alerte configurables par salle.

**Extras** : file d'attente hors-ligne cote telephone (scan sans reseau, sync differee),
signalement "patch endommage" qui force l'alerte ROUGE, verrouillage de compte apres
5 echecs de PIN.

## Limites connues du POC (assumees, roadmap en `docs/OFFRE.md`)

- Scan mobile = Android uniquement (app native et Web NFC), pas iOS — voir alternatives en roadmap.
- L'app Android ne couvre que le scan ; l'enrollment patch<->salle et le dashboard restent
  sur le poste web (usage normal : le PC de securite n'a pas besoin d'app mobile).
- Pas de geolocalisation du scan (un gardien scanne bien le patch physique, mais rien ne
  verifie qu'il n'a pas retire le patch de son support).
- TLS demo via tunnel (Web NFC) ou trafic clair sur LAN (app Android), pas de certificat
  interne permanent.
