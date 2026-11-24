package model;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Interface untuk objek yang dapat mencetak struk transaksi parkir.
 */
public interface CetakStruk {

    /**
     * Menghasilkan teks struk pembayaran parkir yang terformat rapi.
     *
     * @return String representasi struk pembayaran
     */
    String cetakStruk();

    /**
     * Menampilkan struk ke standar output konsol.
     */
    default void tampilkanStrukKonsol() {
        System.out.println(cetakStruk());
    }

    /**
     * Helper default method untuk memformat angka nominal ke format Rupiah (RpX.XXX).
     *
     * @param nominal nilai rupiah
     * @return format string Rupiah
     */
    default String formatRupiah(double nominal) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"));
        return nf.format(nominal).replace(",00", "").replace("Rp", "Rp ");
    }
}
