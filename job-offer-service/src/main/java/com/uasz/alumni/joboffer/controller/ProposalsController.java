package com.uasz.alumni.joboffer.controller;

import com.uasz.alumni.joboffer.exceptions.NotImplementedYetException;
import com.uasz.alumni.joboffer.spi.api.ProposalsApi;
import com.uasz.alumni.joboffer.spi.model.Proposal;
import com.uasz.alumni.joboffer.spi.model.ProposalCreateRequest;
import com.uasz.alumni.joboffer.spi.model.ProposalStatus;
import com.uasz.alumni.joboffer.spi.model.ProposalStatusUpdateRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProposalsController implements ProposalsApi {

    @Override
    public ResponseEntity<List<Proposal>> listProposals(UUID studentId, UUID recruiterId, ProposalStatus status) {
        // TODO : propositions reçues ou envoyées
        throw new NotImplementedYetException("listProposals");
    }

    @Override
    public ResponseEntity<Proposal> createProposal(ProposalCreateRequest proposalCreateRequest) {
        // TODO : proposer une offre à un étudiant (recruiterId = utilisateur courant) -> 201 / 400
        throw new NotImplementedYetException("createProposal");
    }

    @Override
    public ResponseEntity<Proposal> updateProposalStatus(UUID id, ProposalStatusUpdateRequest proposalStatusUpdateRequest) {
        // TODO : accepter/refuser (étudiant) -> 200 / 404
        throw new NotImplementedYetException("updateProposalStatus");
    }
}
