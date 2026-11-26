package com.uasz.alumni.joboffer.service;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implémentation provisoire : les propositions (ALUM-174) ne sont pas encore dans develop, donc aucune n'est comptée.
 * À remplacer par une implémentation lisant la table des propositions une fois ALUM-174 fusionnée :
 * il suffira alors de supprimer ce composant, le service dépend uniquement de {@link SentProposalsCounter}.
 */
@Component
class NoSentProposalsYet implements SentProposalsCounter {

    @Override
    public long countSentBy(UUID recruiterId) {
        return 0;
    }
}
