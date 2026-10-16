package com.diana.auditinsightbackendspringboot.DTOs;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class EvidenceSignaturesResponse {
    private List<SignatureResponse> signatures;
    private List<SignatureRequestResponse> pendingRequests;
    // Whether the logged-in user has already signed this document
    private boolean signedByMe;
    // The still-pending request addressed to the logged-in user, if someone asked them to sign
    private SignatureRequestResponse myPendingRequest;
}
