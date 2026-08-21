package com.equitycommittee.voting.security;

import com.equitycommittee.voting.domain.entity.CaseEntry;
import com.equitycommittee.voting.domain.entity.User;
import com.equitycommittee.voting.domain.enums.CaseStatus;
import com.equitycommittee.voting.domain.enums.Role;

public final class RolePermissions {

    private RolePermissions() {
    }

    public static boolean isAdmin(User user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    public static boolean isManager(User user) {
        return user != null && user.getRole() == Role.MANAGER;
    }

    public static boolean isManagerApprovalRole(User user) {
        return isAdmin(user) || isManager(user);
    }

    public static boolean isDecisionRole(User user) {
        return user != null
                && (user.getRole() == Role.ADMIN
                || user.getRole() == Role.MANAGER
                || user.getRole() == Role.CHAIRPERSON);
    }

    public static boolean isCommitteeViewerRole(User user) {
        return user != null
                && (user.getRole() == Role.COMMITTEE_MEMBER
                || user.getRole() == Role.SECRETARY
                || user.getRole() == Role.CHAIRPERSON);
    }

    public static boolean isCaseSubmitterRole(User user) {
        return user != null
                && (user.getRole() == Role.CREDIT_OFFICER
                || user.getRole() == Role.MANAGER
                || user.getRole() == Role.ADMIN);
    }

    public static boolean isCaseCreator(User user, CaseEntry caseEntry) {
        return user != null
                && user.getId() != null
                && caseEntry != null
                && caseEntry.getCreatedBy() != null
                && caseEntry.getCreatedBy().getId() != null
                && caseEntry.getCreatedBy().getId().equals(user.getId());
    }

    public static boolean isManagerApproved(CaseEntry caseEntry) {
        return caseEntry != null
                && caseEntry.getStatus() != CaseStatus.DRAFT
                && caseEntry.getStatus() != CaseStatus.SUBMITTED;
    }
}
