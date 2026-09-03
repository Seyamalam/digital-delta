package com.example.digitaldelta.domain.identity

enum class Role {
    REQUESTER,
    RELAY,
    OPERATOR,
    COORDINATOR,
    RECIPIENT,
    AUDITOR,
}

enum class Permission {
    CREATE_REQUEST,
    RELAY_ENVELOPE,
    VIEW_PROTECTED_CARGO,
    CONFIRM_PREEMPTION,
    OFFER_CUSTODY,
    ACCEPT_CUSTODY,
    RESOLVE_CONFLICT,
    INSPECT_AUDIT,
}

data class OfflineCredential(
    val subjectId: String,
    val role: Role,
    val expiresAtMillis: Long,
    val revoked: Boolean = false,
)

enum class DenialReason {
    EXPIRED,
    REVOKED,
    ROLE_FORBIDDEN,
}

data class AuthorizationDecision(
    val allowed: Boolean,
    val denialReason: DenialReason? = null,
)

class AuthorizationPolicy {
    private val permissionsByRole = mapOf(
        Role.REQUESTER to setOf(Permission.CREATE_REQUEST, Permission.INSPECT_AUDIT),
        Role.RELAY to setOf(Permission.RELAY_ENVELOPE, Permission.INSPECT_AUDIT),
        Role.OPERATOR to setOf(
            Permission.RELAY_ENVELOPE,
            Permission.VIEW_PROTECTED_CARGO,
            Permission.OFFER_CUSTODY,
            Permission.ACCEPT_CUSTODY,
            Permission.INSPECT_AUDIT,
        ),
        Role.COORDINATOR to Permission.entries.toSet(),
        Role.RECIPIENT to setOf(
            Permission.CREATE_REQUEST,
            Permission.VIEW_PROTECTED_CARGO,
            Permission.ACCEPT_CUSTODY,
            Permission.INSPECT_AUDIT,
        ),
        Role.AUDITOR to setOf(Permission.INSPECT_AUDIT),
    )

    fun isAllowed(role: Role, permission: Permission): Boolean =
        permission in permissionsByRole.getValue(role)

    fun authorize(
        credential: OfflineCredential,
        permission: Permission,
        nowMillis: Long,
    ): AuthorizationDecision = when {
        credential.revoked -> AuthorizationDecision(false, DenialReason.REVOKED)
        nowMillis >= credential.expiresAtMillis -> AuthorizationDecision(false, DenialReason.EXPIRED)
        !isAllowed(credential.role, permission) ->
            AuthorizationDecision(false, DenialReason.ROLE_FORBIDDEN)
        else -> AuthorizationDecision(true)
    }
}
