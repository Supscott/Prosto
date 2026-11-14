//! Pure logic on top of adblock-rust. No JNI types here, so `cargo test` covers it on the host.

use adblock::lists::{FilterSet, ParseOptions};
use adblock::request::Request;
use adblock::resources::Resource;
use adblock::Engine;
use serde::Serialize;
use std::collections::HashSet;
use std::panic::{catch_unwind, AssertUnwindSafe};

pub const BLOCK: i32 = 1;
pub const IMPORTANT: i32 = 2;
pub const EXCEPTION: i32 = 4;
pub const REDIRECT: i32 = 8;
pub const MATCHED: i32 = 16;

pub const VERSION: &str = concat!("1-adblock-", "0.13.5");

const _: fn() = || {
    fn assert_send_sync<T: Send + Sync>() {}
    assert_send_sync::<Engine>();
};

/// An engine with a spin-then-yield lock in front of it. adblock-rust holds one `std::sync::Mutex`
/// (its regex manager) for a whole network check, so concurrent callers queue on it anyway; on
/// Android a contended futex parks and wakes threads so slowly that four WebView IO threads turned
/// a ~30 µs check into ~2 ms at the median. A short spin before yielding keeps the waiters on CPU
/// for the few microseconds a check takes, so the inner mutex is never contended.
pub struct Guarded {
    engine: Engine,
    busy: std::sync::atomic::AtomicBool,
}

const SPINS_BEFORE_YIELD: u32 = 200;

impl Guarded {
    pub fn new(engine: Engine) -> Self {
        Self { engine, busy: std::sync::atomic::AtomicBool::new(false) }
    }

    /// Exclusive access for setup (resources) before the handle is shared.
    pub fn engine_mut(&mut self) -> &mut Engine {
        &mut self.engine
    }

    pub fn with<T>(&self, f: impl FnOnce(&Engine) -> T) -> T {
        use std::sync::atomic::Ordering;
        let mut spins = 0u32;
        while self.busy.compare_exchange_weak(false, true, Ordering::Acquire, Ordering::Relaxed).is_err() {
            spins += 1;
            if spins < SPINS_BEFORE_YIELD {
                std::hint::spin_loop();
            } else {
                std::thread::yield_now();
            }
        }
        struct Release<'a>(&'a std::sync::atomic::AtomicBool);
        impl Drop for Release<'_> {
            fn drop(&mut self) {
                self.0.store(false, std::sync::atomic::Ordering::Release);
            }
        }
        let _release = Release(&self.busy);
        f(&self.engine)
    }
}

/// Runs `f`, turning a panic into `None`.
pub fn guard<T>(f: impl FnOnce() -> Option<T>) -> Option<T> {
    catch_unwind(AssertUnwindSafe(f)).ok().flatten()
}

pub fn request_type(code: i32) -> Option<&'static str> {
    Some(match code {
        0 => "other",
        1 => "document",
        2 => "subdocument",
        3 => "image",
        4 => "stylesheet",
        5 => "script",
        6 => "font",
        7 => "media",
        8 => "xmlhttprequest",
        9 => "websocket",
        10 => "ping",
        _ => return None,
    })
}

pub fn from_lists(lists: Vec<String>) -> Option<Engine> {
    guard(|| {
        let mut set = FilterSet::new(false);
        for list in lists {
            set.add_filter_list(list, ParseOptions::default());
        }
        Some(Engine::new_with_filter_set(set))
    })
}

pub fn from_serialized(bytes: &[u8]) -> Option<Engine> {
    guard(|| {
        let mut engine = Engine::default();
        engine.deserialize(bytes).ok()?;
        Some(engine)
    })
}

/// Accepts a JSON array of resources, or an object with a `resources` array (the stubs file).
pub fn load_resources(engine: &mut Engine, json: &str) -> bool {
    guard(|| {
        let value: serde_json::Value = serde_json::from_str(json).ok()?;
        let array = match value {
            serde_json::Value::Object(mut o) => o.remove("resources")?,
            other => other,
        };
        let resources: Vec<Resource> = serde_json::from_value(array).ok()?;
        engine.use_resources(resources);
        Some(true)
    })
    .unwrap_or(false)
}

pub fn check(engine: &Engine, url: &str, source: &str, type_code: i32) -> i32 {
    guard(|| {
        let req = Request::new(url, source, request_type(type_code)?, "GET").ok()?;
        let r = engine.check_network_request(&req);
        let mut flags = 0;
        if r.should_block() {
            flags |= BLOCK;
        }
        if r.important {
            flags |= IMPORTANT;
        }
        if r.exception.is_some() {
            flags |= EXCEPTION;
        }
        if r.redirect.is_some() {
            flags |= REDIRECT;
        }
        if r.filter.is_some() {
            flags |= MATCHED;
        }
        Some(flags)
    })
    .unwrap_or(0)
}

pub fn redirect(engine: &Engine, url: &str, source: &str, type_code: i32) -> Option<String> {
    guard(|| {
        let req = Request::new(url, source, request_type(type_code)?, "GET").ok()?;
        engine.check_network_request(&req).redirect
    })
}

#[derive(Serialize, Default)]
struct Cosmetic {
    hide: Vec<String>,
    exceptions: Vec<String>,
    procedural: Vec<String>,
    generichide: bool,
    scriptlets: Vec<Vec<String>>,
}

fn sorted(set: HashSet<String>) -> Vec<String> {
    let mut v: Vec<String> = set.into_iter().collect();
    v.sort();
    v
}

pub fn cosmetic_json(engine: &Engine, url: &str) -> String {
    guard(|| {
        let r = engine.url_cosmetic_resources(url);
        let c = Cosmetic {
            hide: sorted(r.hide_selectors),
            exceptions: sorted(r.exceptions),
            procedural: sorted(r.procedural_actions),
            generichide: r.generichide,
            scriptlets: parse_scriptlets(&r.injected_script),
        };
        serde_json::to_string(&c).ok()
    })
    .unwrap_or_else(|| "{}".to_string())
}

pub fn hidden_selectors(
    engine: &Engine,
    classes: Vec<String>,
    ids: Vec<String>,
    exceptions: Vec<String>,
) -> Vec<String> {
    let exceptions: HashSet<String> = exceptions.into_iter().collect();
    guard(|| Some(engine.hidden_class_id_selectors(classes, ids, &exceptions))).unwrap_or_default()
}

fn is_placeholder(s: &str) -> bool {
    s.strip_prefix("{{")
        .and_then(|r| r.strip_suffix("}}"))
        .is_some_and(|n| !n.is_empty() && n.bytes().all(|b| b.is_ascii_digit()))
}

/// The engine renders every template scriptlet as `try {\n<body>\n} catch ( e ) { }\n`, where the
/// body is the stub's JSON array with `{{n}}` filled by JSON-escaped arguments. Bodies are single
/// lines because the engine escapes newlines inside arguments. Unfilled trailing slots are dropped.
pub fn parse_scriptlets(injected: &str) -> Vec<Vec<String>> {
    injected
        .lines()
        .filter(|l| l.starts_with('['))
        .filter_map(|l| serde_json::from_str::<Vec<String>>(l).ok())
        .map(|mut v| {
            while v.len() > 1 && v.last().is_some_and(|s| is_placeholder(s)) {
                v.pop();
            }
            v
        })
        .collect()
}
