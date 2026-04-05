# BTLN4
## CLASS DIAGRAM: AUCTION SYSTEM
```mermaid 
classDiagram

class Entity {
  -String id
  -LocalDateTime createdAt
  +getId()
  +getCreatedAt()
}

class User {
  -String username
  -String email
  -String password
}

class Bidder {
  -double accountBalance
}

class Seller {
  -String shopName
  -double rating
}

class Admin {
  -int accessLevel
}

Entity <|-- User
User <|-- Bidder
User <|-- Seller
User <|-- Admin

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

Item <|-- Electronics
Item <|-- Art
Item <|-- Vehicle

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

Auction --> Seller
Auction --> Item
Auction --> BidTransaction
BidTransaction --> Bidder

class AuctionStatus {
  <<enumeration>>
  OPEN
  RUNNING
  FINISHED
  PAID
  CANCELED
}
