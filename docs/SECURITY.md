# Analyse de risques — Rondes NFC

Un systeme de controle de ronde qui peut etre trompe n'a aucune valeur : il sert a prouver
qu'un humain est physiquement passe quelque part. Chaque menace ci-dessous est traitee comme
un point de conception, pas comme une case a cocher.

## 1. Fraude sur la preuve de passage

| Menace | Analyse | Reponse |
|---|---|---|
| Un gardien scanne le patch depuis chez lui / sans etre physiquement present | Impossible en pratique : le scan est un evenement materiel Web NFC (`NDEFReader`), il ne se declenche que quand une antenne NFC detecte un tag a quelques centimetres. On ne peut pas "simuler" une lecture NFC depuis un navigateur a distance. | Le vrai point faible n'est pas le telephone, c'est **l'API** : rien n'empeche techniquement un `curl -X POST /api/scan` avec un `tagUid` connu, en dehors de toute lecture NFC. C'est pour ca que l'authentification (badge+PIN, jeton court) + la journalisation (IP, user-agent, device) sont necessaires pour la tracabilite, et que la detection d'anomalies (cf. tableau 3) est le vrai filet de securite, pas la seule presence d'un jeton valide. |
| Clonage du patch (copier l'UID sur un tag vierge) | Les tags NTAG213/215/216 recommandes pour ce projet ont un UID **grave en usine, non reinscriptible** (contrairement aux tags "magic" utilises pour cloner du MIFARE Classic). Cloner un UID NTAG21x demande un materiel specialise, hors de portee d'un gardien malveillant occasionnel. | POC : UID materiel = identifiant suffisant. **Roadmap production** : passer sur NTAG424 DNA (SUN/SDM), qui genere un message signe (CMAC) different a chaque lecture -> meme en connaissant l'UID, on ne peut pas rejouer une lecture. C'est l'etape naturelle si le client veut un niveau de preuve juridique fort. |
| Rejeu d'une requete interceptee (memes tagUid+token rejoues en boucle) | Une meme paire (tagUid, token) rejouee plusieurs fois cree simplement plusieurs scans horodates rapprochés — visible et suspect dans l'historique. | Le dashboard et l'historique n'occultent jamais les doublons : un meme gardien scannant 10 fois la meme salle en 2 minutes doit alerter visuellement le chef de poste (piste d'amelioration : seuil de detection automatique, cf. tableau 3). |

## 2. Disponibilite terrain

| Menace | Analyse | Reponse |
|---|---|---|
| Pas de reseau en sous-sol au moment du scan | Cas explicitement identifie dans le besoin client. Le scan NFC ne depend pas du reseau (lecture locale), seul l'envoi au serveur en depend. | `scan.html` capture l'horodatage **au moment du tap** (`new Date().toISOString()`), tente l'envoi, et si echec reseau (`TypeError` sur le `fetch`), met le scan en file locale (`localStorage`) rejouee automatiquement au retour du reseau (`online` event + retry periodique). Le serveur accepte un horodatage passe dans une fenetre bornee (6h par defaut, `ScanService.MAX_OFFLINE_WINDOW`) pour eviter qu'un horodatage soit falsifie a volonte depuis le client ; au-dela, le scan est refuse et doit etre traite manuellement. Le dashboard/historique marque distinctement ces scans ("synchronise hors-ligne") — la transparence prime sur l'automatisme total. |
| Le telephone du gardien tombe en panne / batterie morte pendant la ronde | Hors perimetre logiciel, mais le dashboard le rend visible immediatement : pas de scan = alerte orange puis rouge, le chef de poste peut reagir en temps reel plutot que de le decouvrir le lendemain. | — |

## 3. Integrite du terrain physique

| Menace | Analyse | Reponse |
|---|---|---|
| Patch arrache, detruit ou vole | Deux cas a distinguer : (a) personne ne le signale -> l'absence de scan fait naturellement monter l'alerte (vert -> orange -> rouge), le systeme detecte le probleme sans action humaine ; (b) un gardien constate la degradation et veut le signaler tout de suite. | Endpoint `POST /api/patches/{id}/report-damaged` (tout utilisateur authentifie) : force l'etat ROUGE sur la salle **meme si un scan recent existe**, pour ne jamais masquer un probleme materiel derriere un controle passe. Seul CHEF_DE_POSTE/DIRECTION peut lever le signalement (`clear-damaged`), typiquement apres remplacement physique et ré-association (`enroll.html`). |
| Un patch retire de sa salle et recolle ailleurs pour "valider" une salle non controlee | Menace residuelle non couverte par le POC (necessite verification physique du support, hors budget logiciel). | Roadmap : tag inviolable (etiquette destructible au retrait) + audit terrain periodique par la direction ; le cout d'un tel dispositif est a arbitrer avec le client (cf. `OFFRE.md`). |

## 4. Comptes et acces (qui voit / fait quoi)

| Role | Peut | Ne peut pas |
|---|---|---|
| **GARDIEN** | Scanner un patch, signaler un patch endommage, consulter **son propre** historique | Voir le dashboard de supervision (etat des autres salles/gardiens), gerer salles/patchs/comptes |
| **CHEF_DE_POSTE** | Dashboard temps reel, gestion salles/patchs, historique complet, creer des comptes GARDIEN | Creer des comptes CHEF_DE_POSTE/DIRECTION |
| **DIRECTION** | Tout ce que peut CHEF_DE_POSTE + gestion des comptes (y compris creation d'autres comptes DIRECTION) | Scanner (pas son role terrain, mais techniquement non bloque pour ne pas gener un controle exceptionnel) |

Ce cloisonnement repond directement au point souleve par le client ("qui a le droit de voir
quoi") : un gardien ne doit pas pouvoir observer les habitudes de controle de ses collegues
ni la carte de vulnerabilite du musee (salles les moins souvent controlees).

## 5. Authentification et session

- PIN a 4 chiffres = espace de recherche faible (10 000 combinaisons) : **verrouillage de
  compte apres 5 echecs** pendant 15 minutes (`AuthService`), pour rendre le bruteforce
  impraticable sans bloquer definitivement un utilisateur legitime qui se trompe deux fois.
- PIN jamais stocke en clair : hash **bcrypt** (`jbcrypt`), sale automatiquement.
- Jetons de session opaques (256 bits, `SecureRandom`), duree de vie courte (12h, alignee sur
  une vacation), stockage cote serveur (revocable), jamais dans un cookie persistant.
- Tout le trafic doit transiter en HTTPS/WSS en production (voir `README.md` pour la
  contrainte HTTPS liee a Web NFC lui-meme, qui impose deja ce choix).

## 6. Ce qui NE doit PAS partir en production tel quel

Le POC inclut un **secours de saisie manuelle du tagUid** sur `scan.html`/`enroll.html`,
utile uniquement pour rejouer une demo si le materiel NFC fait defaut le jour de la
soutenance. Ce champ **annule la garantie physique** que le systeme est cense apporter (un
identifiant tape au clavier ne prouve aucune presence). Il doit etre supprime du build de
production (flag de compilation / variable d'environnement `DEMO_MODE`), et ne jamais etre
accessible depuis un reseau autre que celui du poste de demo.

## 7. Pistes non implementees, assumees comme roadmap

- **Detection d'anomalie temporelle** : deux scans du meme gardien dans des salles trop
  eloignees pour le temps ecoule entre les deux ("impossible travel").
- **Geolocalisation du scan** (GPS telephone compare aux coordonnees de la salle) pour
  detecter un scan physique du patch hors de sa salle d'origine (ex: patch decolle et
  amene ailleurs).
- **Chainage cryptographique de l'historique** (hash du scan precedent inclus dans le
  suivant) pour rendre toute alteration a posteriori de la base detectable — pertinent si
  l'historique doit avoir une valeur probatoire juridique.
- **Journal d'audit des actions admin** (qui a cree/modifie quelle salle, quel compte).
