# Aturan Wajib Konversi UI Figma (React/Tailwind) ke JavaFX
- Sumber referensi UI mutlak ada di `design-ref/src/App.tsx` dan `design-ref/src/index.css`.
- JANGAN gunakan tampilan default JavaFX Modena. Semua komponen (`.button`, `.text-field`, `.combo-box`, `.table-view`, `.scroll-pane`, `.tab-pane`) wajib di-override di `src/main/resources/view/style.css`.
- Gunakan sintaks CSS JavaFX yang valid: `-fx-background-color`, `-fx-background-radius`, `-fx-border-color`, `-fx-border-radius`, `-fx-padding`, `-fx-font-size`, `-fx-font-weight`, `-fx-effect: dropshadow(...)`.
- JANGAN gunakan CSS web seperti `border-radius`, `box-shadow`, `background`, atau `gap` di dalam file CSS JavaFX.
- Semua ikon dari `lucide-react` di `App.tsx` wajib diterjemahkan menjadi node `<SVGPath content="..." />` di FXML.
- Tetap pertahankan koneksi `fx:id` dan `onAction` ke `ParkirController` serta database SQLite (`parkir.db`).