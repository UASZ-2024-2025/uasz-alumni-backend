package com.uasz.alumni.joboffer.service;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implémentation provisoire : les propositions (ALUM-174) ne sont pas encore dans develop, donc aucune n'est comptée.
 * À SUPPRIMER quand ALUM-174 est fusionnée, au profit de JpaSentProposalsCounter (voir apres-ALUM-174/).
 */
@Component
class NoSentProposalsYet implements SentProposalsCounter {

    @Override
    public long countSentBy(UUID recruiterId) {
        return 0;
    }
}
