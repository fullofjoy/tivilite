use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Manager, Window};

#[derive(Serialize, Deserialize)]
pub struct DesktopSystemInfo {
    pub os: String,
    pub arch: String,
    pub app_version: String,
}

#[tauri::command]
fn get_desktop_system_info() -> DesktopSystemInfo {
    DesktopSystemInfo {
        os: std::env::consts::OS.to_string(),
        arch: std::env::consts::ARCH.to_string(),
        app_version: env!("CARGO_PKG_VERSION").to_string(),
    }
}

#[tauri::command]
fn toggle_always_on_top(window: Window, enabled: bool) -> Result<bool, String> {
    window
        .set_always_on_top(enabled)
        .map_err(|e| e.to_string())?;
    Ok(enabled)
}

#[tauri::command]
fn toggle_boss_key(window: Window) -> Result<bool, String> {
    let is_visible = window.is_visible().unwrap_or(true);
    if is_visible {
        window.minimize().map_err(|e| e.to_string())?;
        window.hide().map_err(|e| e.to_string())?;
        Ok(false) // hidden
    } else {
        window.show().map_err(|e| e.to_string())?;
        window.unminimize().map_err(|e| e.to_string())?;
        window.set_focus().map_err(|e| e.to_string())?;
        Ok(true) // visible
    }
}

#[tauri::command]
fn set_mini_pip_mode(window: Window, enabled: bool) -> Result<bool, String> {
    if enabled {
        let size = tauri::LogicalSize::new(420.0, 236.0);
        window.set_size(size).map_err(|e| e.to_string())?;
        window.set_always_on_top(true).map_err(|e| e.to_string())?;
        window.set_resizable(true).map_err(|e| e.to_string())?;
    } else {
        let size = tauri::LogicalSize::new(1280.0, 720.0);
        window.set_size(size).map_err(|e| e.to_string())?;
        window.set_always_on_top(false).map_err(|e| e.to_string())?;
        window.center().map_err(|e| e.to_string())?;
    }
    Ok(enabled)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_global_shortcut::Builder::new().build())
        .plugin(tauri_plugin_window_state::Builder::default().build())
        .invoke_handler(tauri::generate_handler![
            get_desktop_system_info,
            toggle_always_on_top,
            toggle_boss_key,
            set_mini_pip_mode
        ])
        .run(tauri::generate_context!())
        .expect("error while running TiviLite desktop application");
}
