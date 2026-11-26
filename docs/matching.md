# Score de matching étudiant / offre

> Ticket : ALUM-179. Code : `analytics-matching-service`, classes `MatchScoreCalculator` et `SkillNormalizer`
> (package `service`). Tests : `MatchScoreCalculatorTest`, `SkillNormalizerTest`.

Le score mesure, de 0 à 100, à quel point une offre convient à un étudiant. Le même calcul sert dans les deux sens :

- offres recommandées à un étudiant : `GET /analytics/matchings/students/{studentId}` (ALUM-180) ;
- candidats recommandés pour une offre : `GET /analytics/matchings/offers/{offerId}` (ALUM-181).

Le calcul est pur : il ne fait aucun appel réseau et ne lit pas l'heure. Les services qui récupèrent les profils et
les offres lui passent seulement les champs utiles.

## Formule

```
score = arrondi(70 × compétences + 20 × intérêts + 10 × disponibilité)
```

Chaque critère vaut entre 0 et 1. L'arrondi est l'arrondi au plus proche (47,5 → 48).

| Critère        | Poids | Données utilisées                                                                     |
| -------------- | ----- | ------------------------------------------------------------------------------------- |
| Compétences    | 70    | `StudentProfile.skills` et `JobOffer.requiredSkills`                                   |
| Intérêts       | 20    | `StudentProfile.interests` et le texte de l'offre (`title`, `description`, `requiredSkills`) |
| Disponibilité  | 10    | `StudentProfile.availabilityStatus` et `JobOffer.type`                                 |

Les compétences pèsent le plus : c'est le seul critère que l'offre exige explicitement. Les intérêts départagent des
profils proches. La disponibilité écarte surtout les étudiants qui ne cherchent pas.

### Compétences

```
compétences = nombre de compétences exigées que l'étudiant possède / nombre de compétences exigées
```

- Une compétence exigée deux fois (« Java », « java ») ne compte qu'une fois.
- `matchingSkills` et `missingSkills` reprennent les libellés de l'offre, dans son ordre.
- Une offre sans compétence exigée donne 1 : rien ne manque à l'étudiant.

### Intérêts

Un intérêt est retrouvé s'il correspond à une compétence exigée, ou s'il apparaît dans le titre ou la description de
l'offre comme mot ou expression entière (« Go » n'est pas retrouvé dans « Google »).

```
intérêts = min(1, intérêts retrouvés / min(nombre d'intérêts, 2))
```

Deux intérêts retrouvés suffisent pour le maximum : un étudiant qui déclare beaucoup d'intérêts n'est pas pénalisé.

### Disponibilité

| `availabilityStatus`         | Offre `INTERNSHIP` | Offre `JOB` |
| ---------------------------- | ------------------ | ----------- |
| `AVAILABLE_IMMEDIATELY`      | 1                  | 1           |
| `AVAILABLE_FOR_INTERNSHIP`   | 1                  | 0,25        |
| `AVAILABLE_FOR_JOB`          | 0,25               | 1           |
| `NOT_AVAILABLE`              | 0                  | 0           |

`availabilityDate` n'est pas utilisée : l'offre n'a pas de date de début, seulement une date limite de candidature.

### Information absente

Quand l'étudiant n'a déclaré aucun intérêt, que sa disponibilité n'est pas renseignée, ou que le type de l'offre est
inconnu, le critère vaut **0,5** : il ne favorise ni ne pénalise. Une nouvelle valeur ajoutée plus tard au contrat est
traitée de la même façon.

## Normalisation

Avant comparaison, chaque compétence ou intérêt est ramené à une clé :

1. accents retirés et minuscules : « Génie Logiciel » → « genie logiciel » ;
2. tirets, soulignés, barres obliques et espaces multiples remplacés par un espace ;
3. espaces et points supprimés : « Spring Boot », « spring-boot », « SpringBoot » → `springboot` ;
   « Node.js » → `nodejs`. Les caractères `+` et `#` sont gardés : C++, C# et C restent distincts ;
4. alias ramenés à un nom unique : `js` → `javascript`, `ts` → `typescript`, `k8s` → `kubernetes`,
   `postgres` → `postgresql`, `node` → `nodejs`, `reactjs` → `react`, `golang` → `go`, `csharp` → `c#`,
   `cpp` → `c++`, `ia` / `ai` → `intelligenceartificielle`, `ml` → `machinelearning`.

La liste d'alias est dans `SkillNormalizer` ; l'enrichir ne demande pas de changer la formule.

## Exemples

Offre `JOB` « Développeur Backend Java » (« Microservices déployés dans le Cloud »), compétences exigées
Java, Spring Boot, Docker, Kubernetes.

| Étudiant                                                                                   | Compétences | Intérêts | Dispo. | Score |
| ------------------------------------------------------------------------------------------ | ----------- | -------- | ------ | ----- |
| java, spring-boot, DOCKER, k8s ; intérêts Cloud, Microservices ; `AVAILABLE_IMMEDIATELY`    | 1           | 1        | 1      | 100   |
| Java, Docker, React ; intérêts Cloud, Design ; `AVAILABLE_FOR_INTERNSHIP`                   | 0,5         | 0,5      | 0,25   | 48    |
| Photoshop, Illustrator ; intérêt Design graphique ; `NOT_AVAILABLE`                         | 0           | 0        | 0      | 0     |
| Java ; aucun intérêt ; disponibilité non renseignée                                         | 0,25        | 0,5      | 0,5    | 33    |

## Limites connues

- Pas de niveau de compétence (débutant, confirmé) : le contrat n'en a pas.
- Les intérêts sont cherchés par mots, sans synonymes au-delà des alias.
- Les poids (70 / 20 / 10) sont un choix de départ, à ajuster après les premiers retours.
