package com.uasz.alumni.joboffer.controller;

import com.uasz.alumni.joboffer.model.ProposalEntity;
import com.uasz.alumni.joboffer.security.AuthenticatedUser;
import com.uasz.alumni.joboffer.service.ProposalService;
import com.uasz.alumni.joboffer.spi.api.ProposalsApi;
import com.uasz.alumni.joboffer.spi.model.Proposal;
import com.uasz.alumni.joboffer.spi.model.ProposalCreateRequest;
import com.uasz.alumni.joboffer.spi.model.ProposalStatus;
import com.uasz.alumni.joboffer.spi.model.ProposalStatusUpdateRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProposalsController implements ProposalsApi {

    private final ProposalService proposals;

    public ProposalsController(ProposalService proposals) {
        this.proposals = proposals;
    }

    @Override
    public ResponseEntity<List<Proposal>> listProposals(UUID studentId, UUID recruiterId, ProposalStatus status) {
        return ResponseEntity.ok(proposals.list(studentId, recruiterId, status, currentUser()).stream()
                .map(ProposalsController::toDto)
                .toList());
    }

    @Override
    public ResponseEntity<Proposal> createProposal(ProposalCreateRequest proposalCreateRequest) {
        ProposalEntity created = proposals.create(proposalCreateRequest, currentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    }

    @Override
    public ResponseEntity<Proposal> updateProposalStatus(UUID id, ProposalStatusUpdateRequest proposalStatusUpdateRequest) {
        ProposalEntity updated = proposals.updateStatus(id, proposalStatusUpdateRequest.getStatus(), currentUser());
        return ResponseEntity.ok(toDto(updated));
    }

    private static AuthenticatedUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (AuthenticatedUser) auth.getPrincipal();
    }

    private static Proposal toDto(ProposalEntity p) {
        return new Proposal()
                .id(p.getId())
                .offerId(p.getOffer().getId())
                .offerTitle(p.getOffer().getTitle())
                .studentId(p.getStudentId())
                .studentName(p.getStudentName())
                .recruiterId(p.getRecruiterId())
                .recruiterName(p.getRecruiterName())
                .message(p.getMessage())
                .status(p.getStatus())
                .createdAt(p.getCreatedAt());
    }
}
