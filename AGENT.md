Generate the Organisation Setup module for AuditInsight.

## Context
AuditInsight is a reactive audit-readiness platform built with:
- Java 21, Spring Boot 3.x WebFlux (fully reactive, NO blocking)
- R2DBC + PostgreSQL, Flyway migrations
- Spring Security with JWT filter
- Lombok (@Getter @Setter @RequiredArgsConstructor)
- Reactor (Mono/Flux — never use .block())

## Package
com.diana.auditinsightbackendspringboot

## Project structure
src/main/java/com/diana/auditinsightbackendspringboot/
├── Controllers/
├── Services/
├── Repositories/
├── Models/
├── DTOs/
├── Enum/
└── Exceptions/Custom/   ← InvalidRecord lives here

src/main/resources/db/migration/
└── V2__create_organisation_tables.sql   ← this is the next version

## Existing tables (do NOT recreate)
- users         (id BIGINT PK, username, password, role, auth_provider,
  full_name, is_verified)
- client_profile (id UUID PK, user_id, first_name, last_name,
  email_address, phone, address, company_name)
- otp_verification (id, email, otp_code, expiry, is_verified)

## AuditInsight — Organisation Module Specification

---

## Overview

The Organisation module is the foundational context for all activity in
AuditInsight. Every transaction, evidence document, review item, and report
belongs to an organisation. A client (business owner) can own multiple
organisations. Each organisation has its own member team, supported
currencies, and fiscal year settings.

---

## Table of Contents

1. [Database Schema](#database-schema)
2. [Enums](#enums)
3. [Business Rules](#business-rules)
4. [Organisation CRUD](#organisation-crud)
5. [Organisation Currencies](#organisation-currencies)
6. [Organisation Members](#organisation-members)
7. [Organisation Invitations](#organisation-invitations)
8. [Invite Flow Logic](#invite-flow-logic)
9. [Registration Hook](#registration-hook)
10. [API Endpoints](#api-endpoints)
11. [Error Messages](#error-messages)

---

## Database Schema

### `organisation`
```sql
CREATE TABLE organisation (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id          BIGINT NOT NULL REFERENCES ClientProfile(id),
    name              VARCHAR(255) NOT NULL,
    industry          VARCHAR(100),
    fiscal_year_start VARCHAR(5) NOT NULL DEFAULT '01-01',
    fiscal_year_end   VARCHAR(5) NOT NULL DEFAULT '12-31',
    default_currency  VARCHAR(10) NOT NULL DEFAULT 'USD',
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### `organisation_member`
```sql
CREATE TABLE organisation_member (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    role            VARCHAR(20) NOT NULL CHECK (role IN ('CLIENT','MEMBER','AUDITOR')),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('ACTIVE','PENDING','REVOKED')),
    joined_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

);
```

### `organisation_currency`
```sql
CREATE TABLE organisation_currency (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    currency_code   VARCHAR(10) NOT NULL,
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE(organisation_id, currency_code)
);
```

### `organisation_invitation`
```sql
CREATE TABLE organisation_invitation (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    invited_email   VARCHAR(255) NOT NULL,
    role            VARCHAR(20) NOT NULL CHECK (role IN ('MEMBER','AUDITOR')),
    token           VARCHAR(225) NOT NULL UNIQUE,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','ACCEPTED','EXPIRED','REVOKED')),
    invited_by      BIGINT NOT NULL REFERENCES ClientProfile(id),
    expires_at      TIMESTAMP NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## Enums

| Enum | Values |
|---|---|
| `MemberStatus` | `ACTIVE`, `PENDING`, `REVOKED` |
| `InvitationStatus` | `PENDING`, `ACCEPTED`, `EXPIRED`, `REVOKED` |

---

## Business Rules

### Organisation
- One client can own multiple organisations
- The client is automatically saved as `CLIENT` in `organisation_member` on creation of organisation
- Only the `CLIENT` can update the organisation settings
- The first currency in the currencies list becomes the default currency
- Fiscal year dates must follow `MM-dd` format (e.g. `01-01`, `12-31`)

### Members
- `CLIENT ` role cannot be assigned via invitation — only through `transferOwnership`
- Only `CLIENT` can invite, remove members, and transfer ownership
- Any member (`CLIENT`, `MEMBER`, `AUDITOR`) can view the organisation and member list
- The `CLIENT` cannot be removed via `removeMember`
- A user cannot be added to the same organisation twice
- When the members are sent the invitation it must include their username(email) and default password to be used so that they can be able to login but then the password must be forcibly change on the first login (means that they must be saved with their correspoding role)

### Ownership Transfer
- The new client must already be a member of the organisation
- On transfer: new client's role becomes `CLIENT`, old client's role becomes `MEMBER`
- `organisation.client_id` is updated to the new organisationMember's ID

### Currencies
- At least one currency is required when creating an organisation
- The first currency in the list is set as `is_default = true`
- On update, all existing currencies are replaced with the new list
- Currency codes are stored in uppercase (e.g. `USD`, `RWF`, `EUR`)
- there might be no duplications in the currencies 

### Invitations
- If the invitee **already has an account**: added directly to `organisation_member`
  with status `ACTIVE` — no invitation record created
- If the invitee **does not have an account**: an `organisation_invitation` record
  is created with status `PENDING` and a secure token is emailed to them
- Invitation tokens expire after **72 hours**
- A pending invitation can be revoked by the `OWNER` before it is accepted
- On login, if the new user's email matches a `PENDING` invitation,
  they are automatically added to the organisation with the assigned role
  and the invitation is marked `ACCEPTED`


---

## Organisation CRUD

### Create Organisation

**Request DTO: `CreateOrganisationRequest`**

| Field | Type | Validation |
|---|---|---|
| `name` | `String` | `@NotBlank` |
| `industry` | `String` | optional |
| `fiscalYearStart` | `String` | `@NotBlank`, pattern `MM-dd` |
| `fiscalYearEnd` | `String` | `@NotBlank`, pattern `MM-dd` |
| `currencies` | `List<String>` | `@NotEmpty`, first = default |

**Flow:**
```
1. Build Organisation entity from request
2. Save organisation
3. Save CLIENT  in organisation_member (status: ACTIVE)
4. Save each currency in organisation_currency
   → first in list: is_default = true
   → rest: is_default = false
5. Return OrganisationResponse
```

---

### Update Organisation

**Request DTO: `UpdateOrganisationRequest`**

| Field | Type | Validation |
|---|---|---|
| `name` | `String` | optional |
| `industry` | `String` | optional |
| `fiscalYearStart` | `String` | optional, pattern `MM-dd` |
| `fiscalYearEnd` | `String` | optional, pattern `MM-dd` |
| `currencies` | `List<String>` | optional |

**Flow:**
```
1. Assert requester is CLIENT
2. Apply non-null fields to existing organisation
3. If currencies provided → delete existing → save new list
4. Return updated OrganisationResponse
```

---

### Response DTO: `OrganisationResponse`

| Field             | Type            |
|-------------------|-----------------|
| `Message`         | `String`        |
| `name`            | `String`        |
| `industry`        | `String`        |
| `fiscalYearStart` | `String`        |
| `fiscalYearEnd`   | `String`        |
| `defaultCurrency` | `String`        |
| `currencies`      | `List<String>`  |
| `createdAt`       | `LocalDateTime` |

---

## Organisation Currencies

Currencies are stored separately in `organisation_currency` and always
returned as part of `OrganisationResponse`.

**Rules:**
- Minimum 1 currency required at creation
- First in list = default (`is_default = true`)
- Codes stored uppercase
- On currency update: full replace (delete all → insert new list)
- Default currency on `organisation` table is updated to match new first entry

---

## Organisation Members

### Invite Member

**Request DTO: `InviteMemberRequest`**

| Field | Type | Validation |
|---|---|---|
| `email` | `String` | `@NotBlank`, `@Email` |
| `role` | `OrgRole` | `@NotNull`, cannot be `OWNER` |

**Flow (dual-path):**
```
inviteMember(orgId, requesterId, email, role)
    │
    ├── Assert requester is CLIENT
    ├── Assert role != CLIENT
    │
    ├── userRepo.findByUsername(email)
    │       │
    │       ├── FOUND
    │       │     ├── Assert not already a member
    │       │     ├── Save to organisation_member (status: ACTIVE)
    │       │     └── Send "You have Invited to work" notification email
    │       │
    │       └── NOT FOUND
    │             ├── Generate secure UUID token
    │             ├── Set expires_at = now + 72 hours
    │             ├── Save organisation_invitation (status: PENDING)
    │             └── Send registration invite email with link:
    │                 /register?inviteToken={token}
    │
    └── Return ResponseMessage
```

---

### Remove Member

**Flow:**
```
1. Assert requester is CLIENT
2. Find member by organisationId + userId
3. Assert member role != CLIENT
4. Delete from organisation_member
5. Return ResponseMessage
```

---

### Transfer Ownership

**Flow:**
```
1. Assert current requester is CLIENT
2. Find new client's member record (must already be a member)
3. Set new client's role → CLIENT
4. Set old  client's role → MEMBER
5. Update organisation.client_id → new client's Id
6. Return ResponseMessage
```

---

### Response DTO: `OrganisationMemberResponse`

| Field      | Type           |
|------------|----------------|
| `Message`  | `String`       |
| `userId`   | `Long`         |
| `email`    | `String`       |
| `role`     | `OrgRole`      |
| `status`   | `MemberStatus` |
| `joinedAt` | `LocalDateTime` |

---

## Organisation Invitations

### Revoke Invitation

Only the `CLIENT` can revoke a `PENDING` invitation before it is accepted.

**Flow:**
```
1. Assert requester is CLIENT of the organisation
2. Find invitation by id and organisationId
3. Assert status is PENDING
4. Set status → REVOKED
5. Return ResponseMessage
```

---

### List Pending Invitations

Returns all `PENDING` invitations for an organisation.
Only accessible by the `CLIENT`.

**Response DTO: `OrganisationInvitationResponse`**

| Field          | Type               |
|----------------|--------------------|
| `Message`      | `string`           |
| `invitedEmail` | `String`           |
| `role`         | `OrgRole`          |
| `status`       | `InvitationStatus` |
| `expiresAt`    | `LocalDateTime`    |
| `createdAt`    | `LocalDateTime`    |

---

## Invite Flow Logic

```
client enters email
        │
        ▼
Does user exist in the system?
        │
   YES  │  NO
        │   └──► Create invitation record (PENDING, 72hr expiry)
        │        Send invite email → /register?inviteToken={token}
        │
        ▼
Is user already a member?
        │
   YES  │  NO
   Error│   └──► Add to organisation_member (ACTIVE)
        │        Send "You've been added" notification email
```

---

## Registration Hook

When a new user completes registration, the `AuthService.registerUser()`
method must include this additional step **after** saving the user:

```
registerUser(request)
    │
    ├── ... existing registration logic ...
    ├── Save user
    ├── Verify OTP (existing flow)
    │
    └── invitationRepo.findByInvitedEmailAndStatus(email, PENDING)
            │
            ├── FOUND (not expired)
            │     ├── Save to organisation_member with invitation's role
            │     └── Mark invitation status → ACCEPTED
            │
            └── NOT FOUND or EXPIRED → continue normally
```

**Note:** Check `expires_at > now()` before accepting. If expired, mark
the invitation `EXPIRED` and do not add the user to the organisation. this means the invitation token must be valid on the specified time 

---

## API Endpoints

| Method | Endpoint | Role Required         | Description |
|---|---|-----------------------|---|
| `POST` | `/api/organisations` | Authenticated CLIENT  | Create organisation |
| `GET` | `/api/organisations` | Authenticated CLIENT  | List my organisations |
| `GET` | `/api/organisations/{orgId}` | Any member            | Get organisation details |
| `PUT` | `/api/organisations/{orgId}` | CLIENT                | Update organisation |
| `POST` | `/api/organisations/{orgId}/members/invite` | CLIENT                | Invite member |
| `GET` | `/api/organisations/{orgId}/members` | Any Aunticated member | List all members |
| `DELETE` | `/api/organisations/{orgId}/members/{userId}` | CLIENT                | Remove member |
| `PATCH` | `/api/organisations/{orgId}/transfer-ownership/{newOwnerId}` | CLIENT                | Transfer ownership |
| `GET` | `/api/organisations/{orgId}/invitations` | CLIENT                | List pending invitations |
| `DELETE` | `/api/organisations/{orgId}/invitations/{invitationId}` | CLIENT                | Revoke invitation |

---

## Error Messages

| Scenario                    | Message                                                     |
|-----------------------------|-------------------------------------------------------------|
| Organisation not found      | `"Organisation not found"`                                  |
| Requester is not a member   | `"You are not a member of this organisation"`               |
| Requester is not the CLIENT | `"Only the owner can perform this action"`                  |
| Inviting with CLIENT role   | `"Cannot assign client role via invitation"`                |
| User already a member       | `"User is already a member of this organisation"`           |
| Removing the CLIENT         | `"Cannot remove the organisation owner"`                    |
| New client not a member     | `"New owner must already be a member of this organisation"` |
| Invite email not registered | Invitation created silently — no error returned             |
| Invitation not found        | `"Invitation not found"`                                    |
| Invitation already accepted | `"This invitation has already been accepted"`               |
| Invitation expired          | `"This invitation has expired. Please request a new one."`  |
| Invitation revoked          | `"This invitation has been revoked"`                        |


### Notice

- everything is going to be operated with the aunthenticated users , for creating the organisation and other operations will be done only with the aunthenticated CLIENT , to view the organisation details for any member then you need the organisation name and you must be auntheticated . refer your self to the slack workflow ,by having the different channels with theircoresponding member who are only allowed to view everything in the channel they are only invited in.
- the member can be invitied in different  organisations(when they already exist then we use their existing profile yet they have to receive the invitation to any new organisation they were added on). 
- it must be atomatically trigger the organisatio invitation at the time they are being added on the organisation
- The invitation token can never be used again because the status check blocks it.