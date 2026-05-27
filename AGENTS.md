Project: Equity Committee Voting Mobile App
Overview

This project is a mobile-first internal system for equity committee members to:

submit potential client cases
discuss cases in a chat-like interface
upload and review collateral images (max 40 per case)
vote on cases (approve/reject/defer)
receive real-time notifications
track final decisions with full audit history

This is NOT a simple chat app.
It is a case-driven workflow system with messaging and structured decision-making.

Tech Stack
Mobile App
Flutter
BLoC (state management)
Dart
REST API + WebSockets
Firebase Cloud Messaging (push notifications)
Backend
Spring Boot (Java 21+)
Spring Security (JWT authentication)
PostgreSQL
JPA / Hibernate
Flyway (DB migrations)
WebSocket (STOMP or similar)
Storage
Object storage (AWS S3 or S3-compatible)
Images only (Phase 1), max 40 per case
Core Architecture Principle

Case-first architecture

Everything revolves around a CaseEntry:

Messages belong to a case
Images belong to a case
Votes belong to a case
Notifications reference a case

Do NOT design this as a standalone chat system.

Core Modules
1. Authentication & Authorization
JWT-based authentication
Role-based access control (RBAC)

Roles:

ADMIN
COMMITTEE_MEMBER
CHAIRPERSON
SECRETARY
2. Case Management Module
CaseEntry fields
id
referenceNumber
clientName
requestedAmount
productType
tenure
summary
riskNotes
collateralSummary
status
votingDeadline
verdict
createdBy
createdAt
updatedAt
Status flow
DRAFT
SUBMITTED
UNDER_REVIEW
VOTING_OPEN
APPROVED
REJECTED
DEFERRED
CLOSED
3. Discussion (Chat per Case)

Each case has its own message thread.

Features:

text messages
replies (optional threading)
timestamps
user attribution
@mentions (optional)
CaseMessage fields
id
caseId
senderId
messageText
parentMessageId (optional)
createdAt
4. Collateral Image Management

Constraints:

max 40 images per case
image compression required
secure storage (private access)
CaseImage fields
id
caseId
uploadedBy
imageUrl
caption
sortOrder
createdAt
5. Voting Module

Vote types:

APPROVE
REJECT
DEFER
ABSTAIN (optional)
Vote fields
id
caseId
voterId
voteChoice
reason
votedAt
Rules
one vote per user per case
voting only allowed when status = VOTING_OPEN
voting deadline enforced
majority determines outcome
chairperson can act as tie-breaker (optional)
6. Notification System

Trigger events:

new case created
new comment
user mentioned
voting opened
vote reminder
voting deadline approaching
final verdict
Notification fields
id
userId
type
title
body
caseId
isRead
createdAt
7. Audit Logging

Track all critical actions:

case creation
edits
image uploads
comments
votes
verdict changes
AuditLog fields
id
entityType
entityId
action
actorId
metadata (JSON)
createdAt
Backend API Design
Authentication
POST /api/auth/login
POST /api/auth/refresh
Cases
POST /api/cases
GET /api/cases
GET /api/cases/{id}
PUT /api/cases/{id}
PATCH /api/cases/{id}/status
Messages
GET /api/cases/{id}/messages
POST /api/cases/{id}/messages
Images
POST /api/cases/{id}/images
GET /api/cases/{id}/images
DELETE /api/images/{id}
Votes
POST /api/cases/{id}/vote
GET /api/cases/{id}/votes
Notifications
GET /api/notifications
PATCH /api/notifications/{id}/read
Realtime Features

Use WebSockets for:

new messages
new case notifications
vote updates
verdict announcements
Flutter App Structure
lib/
 ├── core/
 │   ├── network/
 │   ├── utils/
 │   ├── constants/
 │
 ├── features/
 │   ├── auth/
 │   ├── cases/
 │   │   ├── bloc/
 │   │   ├── models/
 │   │   ├── repository/
 │   │   ├── screens/
 │   │
 │   ├── discussion/
 │   ├── voting/
 │   ├── notifications/
 │   ├── images/
 │
 ├── shared/
 │   ├── widgets/
 │   ├── themes/
BLoC Design

Each feature should have its own BLoC:

AuthBloc
CaseBloc
CaseDetailBloc
MessageBloc
VotingBloc
NotificationBloc
Important Constraints
max 40 images per case (enforced on frontend + backend)
voting cannot occur outside VOTING_OPEN
users can only vote once
secure all endpoints (no public access)
all actions must be auditable
Non-Functional Requirements
secure (JWT + RBAC)
scalable backend
reliable notifications
performant image uploads
support poor network conditions
database integrity (ACID)
Phase Plan
Phase 1 (MVP)
authentication
case creation
case listing
case detail view
messages
image upload (max 40)
voting
notifications (basic)
Phase 2
real-time updates (WebSockets)
vote reminders
read/unread tracking
better UI/UX
Phase 3
audit logs
analytics
reporting
export functionality
Development Guidelines
Follow clean architecture principles
Separate domain, data, and presentation layers
Validate all inputs server-side
Use DTOs for API responses
Use transactions for vote and verdict operations
Use pagination for lists
Optimize image uploads
Key Design Decisions
Case-driven system (not chat-driven)
Strong backend enforcement of business rules
Mobile app as a client, not decision engine
Notifications treated as first-class feature
Auditability is mandatory
Expected Outcome

A production-grade mobile app that:

enables structured committee decision-making
ensures transparency and accountability
provides real-time collaboration
maintains full audit history
supports efficient voting workflows