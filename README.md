# Rondes NFC — POC supervision de rondes en musee

POC repondant a l'appel d'offres "supervision de rondes par patchs NFC" : un gardien
scanne un patch NFC avec son telephone (Chrome Android, Web NFC), un PC de securite
voit l'etat de toutes les salles se mettre a jour en temps reel (WebSocket).

## Stack

- **Backend** : Kotlin + [Ktor](https://ktor.io) (serveur Netty), persistance [Exposed](https://github.com/JetBrains/Exposed) + H2 (fichier local)
- **Scan NFC** : Web NFC (`NDEFReader`) dans une page web mobile — aucune app native a installer/mettre a jour sur les telephones des gardiens
- **Supervision temps reel** : WebSocket, poussee a chaque scan + tick client cote navigateur pour l'ecoulement du temps
- **Frontend** : HTML/CSS/JS vanilla servis directement par Ktor (`src/main/resources/static`)

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

## Demo live avec un vrai tag NFC (important)

Le Web NFC (`navigator.NDEFReader`) **exige un contexte securise** : `https://` ou
`http://localhost`. Un telephone qui ouvre l'IP locale du PC (`http://192.168.x.x:8080`)
n'est PAS considere comme securise -> le scan NFC ne s'activera pas.

Deux options pour la soutenance :

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

**Must have** : scan NFC reel + identification salle, identification du gardien (login
badge/PIN), horodatage + enregistrement persistant, dashboard (temps ecoule / dernier
controleur / statut vert-orange-rouge), mise a jour automatique (WebSocket).

**Should have** : CRUD salles + association patch<->salle (`enroll.html`, `/api/rooms`,
`/api/patches`), historique consultable filtrable par salle/gardien/periode
(`history.html`, `/api/history`), comptes/roles (GARDIEN / CHEF_DE_POSTE / DIRECTION),
seuils d'alerte configurables par salle.

**Extras** : file d'attente hors-ligne cote telephone (scan sans reseau, sync differee),
signalement "patch endommage" qui force l'alerte ROUGE, verrouillage de compte apres
5 echecs de PIN.

## Limites connues du POC (assumees, roadmap en `docs/OFFRE.md`)

- Web NFC = Chrome/Android uniquement (pas iOS) — voir alternatives en roadmap.
- Pas de geolocalisation du scan (un gardien scanne bien le patch physique, mais rien ne
  verifie qu'il n'a pas retire le patch de son support).
- TLS demo via tunnel, pas de certificat interne permanent.
