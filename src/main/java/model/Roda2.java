package model;

/**
 * Kelas model untuk kendaraan Roda 2 (Sepeda Motor).
 * Memiliki fitur tambahan titip helm dan tarif Rp 2.000 / jam.
 */
public class Roda2 extends Kendaraan {

    public static final double TARIF_PER_JAM = 2000.0;
    public static final double BIAYA_TITIP_HELM = 2000.0;
    public static final double DISKON_MEMBER_RATE = 0.10; // Diskon 10%

    private boolean titipHelm;

    public Roda2(String platNomor, String merk) {
        this(platNomor, merk, false);
    }

    public Roda2(String platNomor, String merk, boolean titipHelm) {
        super(platNomor, merk, "Roda2");
        this.titipHelm = titipHelm;
    }

    @Override
    public double getTarifPerJam() {
        return TARIF_PER_JAM;
    }

    @Override
    public double hitungBiaya(long durasiJam, boolean isMember) {
        if (durasiJam < 0) throw new IllegalArgumentException("Durasi parkir tidak boleh negatif.");
        long durasiValid = Math.max(1, durasiJam);
        double subtotal = (durasiValid * TARIF_PER_JAM);
        if (titipHelm) {
            subtotal += BIAYA_TITIP_HELM;
        }

        if (isMember) {
            subtotal = subtotal * (1.0 - DISKON_MEMBER_RATE);
        }

        return Math.round(subtotal);
    }

    public boolean isTitipHelm() {
        return titipHelm;
    }

    public void setTitipHelm(boolean titipHelm) {
        this.titipHelm = titipHelm;
    }

    @Override
    public String toString() {
        return super.toString() + (titipHelm ? " [Titip Helm]" : "");
    }
}
