package model;

/**
 * Model data petugas atau staf operator sistem parkir.
 */
public class Petugas {

    private int id;
    private String username;
    private String nama;
    private String role;

    public Petugas() {
    }

    public Petugas(int id, String username, String nama, String role) {
        this.id = id;
        this.username = username;
        this.nama = nama;
        this.role = role;
    }

    public Petugas(String username, String nama, String role) {
        this(0, username, nama, role);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNama() {
        return nama;
    }

    public void setNama(String nama) {
        this.nama = nama;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    @Override
    public String toString() {
        return nama + " (" + username + " - " + role + ")";
    }
}
