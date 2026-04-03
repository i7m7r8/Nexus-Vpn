// DualLogger — Android logcat + UI buffer

use android_log_sys::LogId;
use std::sync::Mutex;

static LOG_BUFFER: Mutex<Vec<String>> = Mutex::new(Vec::new());
const MAX_LOG_LINES: usize = 500;

pub struct DualLogger;

impl DualLogger {
    pub fn get_log_buffer() -> Vec<String> {
        LOG_BUFFER.lock().unwrap().clone()
    }
}

impl log::Log for DualLogger {
    fn enabled(&self, metadata: &log::Metadata) -> bool {
        metadata.level() <= log::Level::Debug
    }

    fn log(&self, record: &log::Record) {
        let msg = format!("[{}] {}", record.level(), record.args());

        // Android logcat
        let priority = match record.level() {
            log::Level::Error => android_log_sys::Priority::ERROR,
            log::Level::Warn => android_log_sys::Priority::WARN,
            log::Level::Info => android_log_sys::Priority::INFO,
            log::Level::Debug => android_log_sys::Priority::DEBUG,
            log::Level::Trace => android_log_sys::Priority::VERBOSE,
        };
        unsafe {
            android_log_sys::log(
                LogId::Main,
                priority,
                "NexusVPN",
                &msg,
            );
        }

        // UI buffer (ring buffer)
        let mut buffer = LOG_BUFFER.lock().unwrap();
        buffer.push(msg);
        if buffer.len() > MAX_LOG_LINES {
            buffer.remove(0);
        }
    }

    fn flush(&self) {}
}
