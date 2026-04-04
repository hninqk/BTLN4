public abstract class Entity {
    protected String id;
    protected LocalDateTime createdAt;

    public Entity() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
    }

    public String getId() { 
        return id; 
    }
}
