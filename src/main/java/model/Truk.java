package model;

/**
 * Kelas model untuk kendaraan besar seperti Truk atau Bus.
 * Memiliki tarif Rp 10.000 / jam.
 */
public class Truk extends Kendaraan {

    public static final double TARIF_PER_JAM = 10000.0;
    public static final double DISKON_MEMBER_RATE = 0.10; // Diskon 10%

    public Truk(String platNomor, String merk) {
        super(platNomor, merk, "Truk");
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
