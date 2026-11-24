package model;

/**
 * Kelas abstrak dasar yang merepresentasikan sebuah Kendaraan dalam sistem parkir.
 * Menerapkan prinsip Object-Oriented Programming (Polimorfisme dan Enkapsulasi).
 */
public abstract class Kendaraan {

    private String platNomor;
    private String merk;
    private final String jenis;

    public Kendaraan(String platNomor, String merk, String jenis) {
        this.platNomor = normalisasiPlat(platNomor);
        this.merk = validasiMerk(merk);
        if (jenis == null || !java.util.Set.of("Roda2", "Roda4", "Truk").contains(jenis))
            throw new IllegalArgumentException("Jenis kendaraan tidak valid.");
        this.jenis = jenis;
    }

    /**
     * Mengembalikan tarif dasar per jam untuk jenis kendaraan bersangkutan.
     *
     * @return tarif dalam Rupiah per jam
     */
    public abstract double getTarifPerJam();

    /**
     * Menghitung total biaya parkir berdasarkan durasi (jam) dan status keanggotaan.
     * Durasi minimal dihitung 1 jam. Pembulatan jam ke atas dilakukan oleh sistem.
     *
     * @param durasiJam durasi parkir dalam satuan jam
     * @param isMember apakah pelanggan memiliki status member (diskon)
     * @return total biaya parkir
     */
    public abstract double hitungBiaya(long durasiJam, boolean isMember);

    // Getters and Setters

    public String getPlatNomor() {
        return platNomor;
    }

    public void setPlatNomor(String platNomor) {
        this.platNomor = normalisasiPlat(platNomor);
    }

    public String getMerk() {
        return merk;
    }

    public void setMerk(String merk) {
        this.merk = validasiMerk(merk);
    }

    public String getJenis() {
        return jenis;
    }

    private static String normalisasiPlat(String platNomor) {
        String value = platNomor == null ? "" : platNomor.trim().replaceAll("\\s+", " ").toUpperCase(java.util.Locale.ROOT);
        if (value.isEmpty()) throw new IllegalArgumentException("Plat nomor wajib diisi.");
        return value;
    }

    private static String validasiMerk(String merk) {
        String value = merk == null ? "" : merk.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Merk kendaraan wajib diisi.");
        return value;
    }

    @Override
    public String toString() {
        return platNomor + " - " + merk + " (" + jenis + ")";
    }

    /**
     * Factory method untuk membuat instance turunan Kendaraan yang sesuai.
     */
    public static Kendaraan createKendaraan(String platNomor, String merk, String jenis, boolean titipHelm) {
        if (jenis == null) throw new IllegalArgumentException("Jenis kendaraan wajib dipilih.");
        return switch (jenis.toLowerCase(java.util.Locale.ROOT)) {
            case "roda2", "motor" -> new Roda2(platNomor, merk, titipHelm);
            case "roda4", "mobil" -> new Roda4(platNomor, merk);
            case "truk", "bus", "besar" -> new Truk(platNomor, merk);
            default -> throw new IllegalArgumentException("Jenis kendaraan tidak dikenal: " + jenis);
        };
    }
}
