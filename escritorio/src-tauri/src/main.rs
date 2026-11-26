// Sin consola de Windows en la versión instalada.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

// La app solo muestra el frontend de MANÁ en una ventana propia.
// Los datos viven en el servidor del local (backend + Oracle), al que cada caja se conecta por la red.
fn main() {
    tauri::Builder::default()
        .run(tauri::generate_context!())
        .expect("no se pudo iniciar MANÁ");
}
