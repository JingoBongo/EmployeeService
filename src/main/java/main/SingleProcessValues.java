package main;

public class SingleProcessValues {
    private static int idCounter = 0;
    public String id;
    public String name;
    private String role;
    private String location;
    public String status;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public SingleProcessValues(String name){
        this.name = name;
        this.id = MainRunner.servicePort+String.valueOf(idCounter++);
    }
    public static void setIdCounter(int idCounter) {
        SingleProcessValues.idCounter = idCounter;
    }

    public static int getIdCounter() {
        return idCounter;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
