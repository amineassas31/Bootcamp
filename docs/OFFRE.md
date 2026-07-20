# Offre commerciale, methode et planning — Rondes NFC

> Chiffres indicatifs (TJM, couts unitaires) a ajuster aux conditions reelles de
> l'entreprise qui porte l'offre ; la structure et les ordres de grandeur sont, eux,
> representatifs d'un projet de cette taille.

## 1. Methodologie retenue : Scrum

**Pourquoi Scrum et pas un cycle en V** : le client exprime un besoin encore flou sur
plusieurs points (seuils d'alerte, gestion du hors-ligne, granularite des roles — cf.
les angles morts identifies en cadrage). Un cycle en V exige un cahier des charges fige
des le depart, ce qui ferait porter au prestataire le risque de mal interpreter des
zones d'ombre que seul un aller-retour rapide avec le client permet de clarifier. Scrum
permet de livrer un increment demontrable des la fin du premier sprint (le risque
principal — le scan NFC reel — est valide en premier, conformement a la regle "on
n'attaque pas le NFC le vendredi matin") et d'ajuster le perimeteure should-have au fil
de l'eau selon les retours du chef de poste pilote.

### Organisation d'equipe

| Role | Responsabilite |
|---|---|
| Product Owner (client) | Priorise le backlog, valide chaque increment, tranche les zones d'ombre (seuils, roles) |
| Scrum Master | Anime les rituels, leve les blocages, protege l'equipe des changements de perimetre en cours de sprint |
| 2 developpeurs backend/full-stack | API Ktor, base de donnees, integration Web NFC, dashboard |
| 1 charge de tests / recette | Ecrit les scenarios de recette, teste chaque increment sur materiel reel (tags + telephones vises) |

Repartition volontaire du binome dev pour eviter le point de defaillance unique ("le
developpeur qui sait tout faire seul") : chaque fonctionnalite est relue en binome/PR
avant merge.

### Rituels et outils

- Sprint de 1 a 2 semaines, daily de 15 min, sprint planning + revue + retro en fin de sprint.
- Backlog et suivi : Jira ou GitHub Projects (kanban) selon l'outillage deja en place chez le client.
- Code : Git (GitHub/GitLab), revue de code obligatoire avant merge, CI (GitHub Actions)
  executant tests unitaires + build a chaque push.
- Communication : canal dedie (Slack/Teams) avec le PO cote client pour les questions de
  cadrage rapides, sans attendre le prochain rituel.

### Strategie de tests et de recette

| Niveau | Ce qui est teste | Outillage |
|---|---|---|
| Unitaire | Regles metier (calcul du niveau d'alerte, fenetre hors-ligne acceptee, verrouillage de compte) | JUnit5 / kotlin-test |
| Integration | Endpoints API (auth, scan, droits par role) contre une base de test | Ktor test engine + H2 en memoire |
| Materiel | Lecture reelle de plusieurs modeles de tags NFC (NTAG213/215/216) sur plusieurs telephones Android (fragmentation Chrome/Android non negligeable) | Sur site, checklist de compatibilite |
| Recette utilisateur | Un gardien reel effectue une ronde complete avec le systeme, sur le musee pilote, avant generalisation | Grille de recette MUST/SHOULD du cahier des charges, signee par le client |
| Charge (avant multi-site) | Simulation de plusieurs dizaines de scans/minute et de connexions WebSocket simultanees | k6 / Gatling |

### Gestion des risques projet (distincte de l'analyse de risques securite, voir `SECURITY.md`)

| Risque | Impact | Mitigation |
|---|---|---|
| Incompatibilite Web NFC sur certains telephones/Android anciens | Blocage du MUST HAVE principal | Valide des le sprint 1, avant tout autre developpement ; liste de telephones certifies fournie au client |
| Gardiens peu a l'aise avec un smartphone | Adoption faible, rondes mal faites | Interface volontairement minimaliste (un seul gros bouton), formation courte sur site, plan B papier temporaire pendant la bascule |
| Reseau instable dans certaines salles | Perte de scans | File d'attente hors-ligne deja implementee dans le POC (cf. `SECURITY.md` §2) |
| Perimetre qui glisse en cours de sprint | Retard | Le PO ne modifie le contenu d'un sprint qu'a son debut ; toute demande en cours de sprint va dans le backlog du suivant |

## 2. Chiffrage indicatif

### Couts materiels (par musee, hors PC de supervision deja existant)

| Poste | Cout unitaire | Quantite type (musee moyen, 20 salles) | Total |
|---|---|---|---|
| Patch NFC NTAG213 (etiquette adhesive) | 0,40 € | 20 (+ 20% de stock de remplacement) | ~10 € |
| Support/protection du patch (boitier anti-vandalisme) | 3 € | 24 | ~72 € |
| Telephone Android compatible NFC (si non fourni par le client) | 180 € | 1 par gardien en poste simultanement (ex: 3) | ~540 € |
| Certificat TLS (site interne) | 0 a 60 €/an | 1 | 0-60 € |

### Couts recurrents (mensuels, pour un site pilote)

| Poste | Cout indicatif |
|---|---|
| Hebergement (base de donnees managee + serveur applicatif) | 40 a 100 €/mois |
| Nom de domaine / certificat | ~2 €/mois |
| Maintenance corrective + evolutive (forfait) | a definir selon SLA souhaite (ex: 1 jour/mois) |
| Supervision/monitoring (logs, alerting infra) | 0 a 30 €/mois (outils gratuits suffisants a cette echelle) |

### Chiffrage developpement (projet reel, au-dela du POC d'une semaine)

| Lot | Contenu | Charge estimee |
|---|---|---|
| Sprint 0 — Cadrage | Ateliers avec le client, levee des zones d'ombre, specification des seuils/roles | 3 jours |
| Sprint 1 — MVP | Scan NFC, auth, dashboard temps reel, historique basique (les MUST HAVE) | 10 jours |
| Sprint 2 — Confort | CRUD salles/patchs, roles fins, seuils configurables, hors-ligne (les SHOULD HAVE) | 8 jours |
| Sprint 3 — Durcissement | Verrouillage de comptes, audit, tests de charge, TLS, deploiement pilote | 6 jours |
| Recette + formation site pilote | Tests reels avec les gardiens, ajustements | 4 jours |
| **Total** | | **~31 jours-hommes** |

A un TJM indicatif de 500 €/jour, cela represente un budget de developpement de l'ordre
de **15 500 €** pour le site pilote, hors materiel et hors recurrent — a affiner avec le
client selon le TJM reel de l'entreprise et le nombre de developpeurs mobilises en
parallele (le planning ci-dessous suppose 2 developpeurs a temps plein).

## 3. Planning de realisation (projet reel)

```
Semaine 1        : Sprint 0 — cadrage, specification des seuils/roles avec le client
Semaines 2-3      : Sprint 1 — MVP (scan NFC, auth, dashboard temps reel)
Semaines 4-5      : Sprint 2 — CRUD, roles, hors-ligne
Semaine 6         : Sprint 3 — durcissement securite, tests de charge
Semaine 7         : Recette utilisateur sur le musee pilote, formation des gardiens
Semaine 8         : Go-live pilote, periode d'observation renforcee
Semaines 9+       : Generalisation aux autres musees (rythme a definir selon le nombre de sites)
```

Pour rappel, le POC livre cette semaine couvre deja techniquement l'integralite du
Sprint 1 et une bonne partie du Sprint 2 — le planning ci-dessus part donc d'une base
deja fonctionnelle, pas de zero.
