package model;

/**
 * Kelas model untuk kendaraan Roda 4 (Mobil).
 * Memiliki tarif Rp 5.000 / jam.
 */
public class Roda4 extends Kendaraan {

    public static final double TARIF_PER_JAM = 5000.0;
    public static final double DISKON_MEMBER_RATE = 0.10; // Diskon 10%

    public Roda4(String platNomor, String merk) {
        super(platNomor, merk, "Roda4");
    }

    @Override
    public double getTarifPerJam() {
        return TARIF_PER_JAM;
    }

    @Override
    public double hitungBiaya(long durasiJam, boolean isMember) {
        if (durasiJam < 0) throw new IllegalArgumentException("Durasi parkir tidak boleh negatif.");
        long durasiValid = Math.max(1, durasiJam);
        double subtotal = durasiValid * TARIF_PER_JAM;

        if (isMember) {
            subtotal = subtotal * (1.0 - DISKON_MEMBER_RATE);
        }

        return Math.round(subtotal);
    }
}
