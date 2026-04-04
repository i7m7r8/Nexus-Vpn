// DualLogger — Android logcat + UI buffer

use std::ffi::CString;
use std::sync::Mutex;

// Raw FFI bindings for __android_log_write
extern "C" {
    fn __android_log_write(prio: i32, tag: *const i8, text: *const i8) -> i32;
}

const ANDROID_LOG_DEBUG: i32 = 3;
const ANDROID_LOG_INFO: i32 = 4;
const ANDROID_LOG_WARN: i32 = 5;
const ANDROID_LOG_ERROR: i32 = 6;
const ANDROID_LOG_VERBOSE: i32 = 2;

static LOG_BUFFER: Mutex<Vec<String>> = Mutex::new(Vec::new());
const MAX_LOG_LINES: usize = 500;

pub struct DualLogger;

pub fn get_log_buffer() -> Vec<String> {
    LOG_BUFFER.lock().unwrap().clone()
}

fn android_log(level: i32, tag: &str, msg: &str) {
    let tag_c = CString::new(tag).unwrap_or_default();
    let msg_c = CString::new(msg).unwrap_or_default();
    unsafe {
        __android_log_write(level, tag_c.as_ptr(), msg_c.as_ptr());
    }
}

impl log::Log for DualLogger {
    fn enabled(&self, metadata: &log::Metadata) -> bool {
        metadata.level() <= log::Level::Debug
    }

    fn log(&self, record: &log::Record) {
        let msg = format!("[{}] {}", record.level(), record.args());

        let priority = match record.level() {
            log::Level::Error => ANDROID_LOG_ERROR,
            log::Level::Warn => ANDROID_LOG_WARN,
            log::Level::Info => ANDROID_LOG_INFO,
            log::Level::Debug => ANDROID_LOG_DEBUG,
            log::Level::Trace => ANDROID_LOG_VERBOSE,
        };
        android_log(priority, "NexusVPN", &msg);

        // UI buffer (ring buffer)
        let mut buffer = LOG_BUFFER.lock().unwrap();
        buffer.push(msg);
        if buffer.len() > MAX_LOG_LINES {
            buffer.remove(0);
        }
    }

    fn flush(&self) {}
}
