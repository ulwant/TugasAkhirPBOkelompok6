package model;

/**
 * Custom Exception yang dilemparkan ketika slot parkir untuk suatu kategori kendaraan
 * atau zona tertentu sudah penuh / tidak tersedia.
 */
public class SlotPenuhException extends Exception {

    private String jenisKendaraan;
    private String zona;

    public SlotPenuhException(String message) {
        super(message);
    }

    public SlotPenuhException(String jenisKendaraan, String zona) {
        super("Maaf, slot parkir untuk kendaraan '" + jenisKendaraan + 
              (zona != null && !zona.isEmpty() ? "' di zona '" + zona : "") + "' sudah penuh!");
        this.jenisKendaraan = jenisKendaraan;
        this.zona = zona;
    }

    public String getJenisKendaraan() {
        return jenisKendaraan;
    }

    public String getZona() {
        return zona;
    }
}
