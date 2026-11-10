# Diagrammes de cas d'utilisation (par rôle)

Rôles définis dans `RoleName` (`user-service`) : **ETUDIANT**, **ALUMNI**, **ENSEIGNANT**, **ADMINISTRATEUR**.
L'acteur **Entreprise partenaire** (cahier des besoins, AL-62) n'a pas encore de rôle propre dans le contrat
(voir [architecture.md, §8](../architecture.md#8-points-ouverts)).

## Cas communs à tous les utilisateurs authentifiés

```mermaid
flowchart LR
    U(["👤 Utilisateur"])
    V(["👤 Visiteur"])
    subgraph sys["Plateforme Alumni UASZ"]
        uc1(["S'inscrire"])
        uc2(["Confirmer son email"])
        uc3(["Se connecter / se déconnecter"])
        uc4(["Réinitialiser son mot de passe"])
        uc5(["Consulter / rechercher les offres"])
        uc6(["Modifier son compte"])
        uc7(["Échanger en messagerie privée"])
        uc8(["Participer à des groupes de discussion"])
        uc9(["Consulter ses notifications"])
        uc10(["Consulter l'annuaire entreprises / alumni"])
    end
    V --> uc1 & uc2 & uc3 & uc4 & uc5
    U --> uc3 & uc6 & uc7 & uc8 & uc9 & uc10 & uc5
    uc1 -. "«include»" .-> uc2
```

## Étudiant

```mermaid
flowchart LR
    E(["👤 Étudiant"])
    subgraph sys["Plateforme Alumni UASZ"]
        e1(["Gérer son profil<br/>compétences, disponibilité"])
        e2(["Créer / mettre à jour son CV"])
        e3(["Postuler à une offre"])
        e4(["Suivre ses candidatures"])
        e5(["Accepter / refuser une proposition"])
        e6(["Voir les offres recommandées<br/>(matching)"])
    end
    E --> e1 & e2 & e3 & e4 & e5 & e6
    e3 -. "«include» choisir un CV" .-> e2
```

## Alumni et Entreprise partenaire

```mermaid
flowchart LR
    A(["👤 Alumni"])
    R(["👤 Entreprise partenaire"])
    subgraph sys["Plateforme Alumni UASZ"]
        a1(["Gérer son profil alumni"])
        a2(["Ajouter une expérience professionnelle"])
        a3(["Recommander un étudiant"])
        a4(["Publier / modifier / clôturer une offre"])
        a5(["Consulter les candidatures reçues"])
        a6(["Changer l'état d'une candidature"])
        a7(["Proposer une offre à un étudiant"])
        a8(["Voir les candidats recommandés<br/>(matching)"])
    end
    A --> a1 & a2 & a3 & a4 & a5 & a6 & a7 & a8
    R --> a4 & a5 & a6 & a7 & a8
    a6 -. "«include» notifier l'étudiant" .-> n(["Envoyer une notification"])
    a7 -. "«include»" .-> n
```

## Enseignant

```mermaid
flowchart LR
    T(["👤 Enseignant"])
    subgraph sys["Plateforme Alumni UASZ"]
        t1(["Consulter les profils étudiants / alumni"])
        t2(["Recommander un étudiant"])
        t3(["Consulter les promotions et diplômés"])
        t4(["Animer un groupe de discussion"])
    end
    T --> t1 & t2 & t3 & t4
```

## Administrateur

```mermaid
flowchart LR
    AD(["👤 Administrateur"])
    subgraph sys["Plateforme Alumni UASZ"]
        d1(["Gérer les utilisateurs<br/>activer, suspendre, supprimer"])
        d2(["Consulter les rôles"])
        d3(["Gérer les entreprises partenaires"])
        d4(["Créer les promotions"])
        d5(["Consulter le tableau de bord"])
        d6(["Générer le rapport d'insertion"])
        d7(["Analyser l'écart de compétences"])
        d8(["Envoyer une notification système"])
        d9(["Modérer les offres et les groupes"])
    end
    AD --> d1 & d2 & d3 & d4 & d5 & d6 & d7 & d8 & d9
```
