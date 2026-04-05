# BTLN4
## CLASS DIAGRAM: AUCTION SYSTEM
```mermaid
classDiagram
direction TB

%% ===== CORE =====
class Entity {
  -String id
  -LocalDateTime createdAt
  +getId()
  +getCreatedAt()
}

%% ===== USER SIDE =====
class User {
  -String username
  -String email
  -String password
}

class Admin {
  -int accessLevel
}

class Bidder {
  -double accountBalance
}

class Seller {
  -String shopName
  -double rating
}

Entity <|-- User
User <|-- Admin
User <|-- Bidder
User <|-- Seller

%% ===== ITEM SIDE =====
class Item {
  -String name
  -String description
  -double startingPrice
  +getCategoryInfo()
}

class Electronics {
  -int warrantyMonths
}

class Art {
  -String artistName
  -int yearCreated
}

class Vehicle {
  -double mileage
  -int year
}

Entity <|-- Item
Item <|-- Electronics
Item <|-- Art
Item <|-- Vehicle

%% ===== AUCTION CORE =====
class Auction {
  -String auctionId
  -Seller seller
  -Item item
  -LocalDateTime startTime
  -LocalDateTime endTime
  -AuctionStatus status
  -BidTransaction highestBid
}

class BidTransaction {
  -String transactionId
  -Bidder bidder
  -double bidAmount
  -LocalDateTime timestamp
}

class AuctionStatus {
  <<enumeration>>
  OPEN
  RUNNING
  FINISHED
  PAID
  CANCELED
}

%% ===== RELATIONSHIPS =====
Auction --> Seller
Auction --> Item
Auction --> BidTransaction
BidTransaction --> Bidder
Auction --> AuctionStatus
