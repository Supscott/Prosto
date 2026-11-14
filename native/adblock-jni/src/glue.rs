//! Thin JNI glue for `com.jestk.browser.engine.adblock.AdblockNative`. Every export returns its
//! safe value on a panic or JNI error and never throws.

use crate::core;
use adblock::Engine;
use jni::objects::{JByteArray, JClass, JObject, JObjectArray, JString};
use jni::sys::{jboolean, jbyteArray, jint, jlong, jobjectArray, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use std::ptr::null_mut;

fn string(env: &mut JNIEnv, s: &JString) -> Option<String> {
    env.get_string(s).ok().map(String::from)
}

fn strings(env: &mut JNIEnv, arr: &JObjectArray) -> Option<Vec<String>> {
    let n = env.get_array_length(arr).ok()?;
    (0..n)
        .map(|i| {
            let o = env.get_object_array_element(arr, i).ok()?;
            string(env, &JString::from(o))
        })
        .collect()
}

fn to_handle(engine: Option<Engine>) -> jlong {
    engine.map_or(0, |e| Box::into_raw(Box::new(e)) as jlong)
}

/// Safety: a non-zero handle comes from `to_handle` and stays alive until `destroy`; Kotlin holds
/// a read lock around every call and the write lock around `destroy`.
unsafe fn engine<'a>(handle: jlong) -> Option<&'a Engine> {
    (handle as *const Engine).as_ref()
}

fn jstr(env: &mut JNIEnv, s: &str) -> jstring {
    env.new_string(s).map_or(null_mut(), |s| s.into_raw())
}

#[no_mangle]
pub extern "system" fn Java_com_jestk_browser_engine_adblock_AdblockNative_nativeVersion(
    mut env: JNIEnv,
    _: JClass,
) -> jstring {
    jstr(&mut env, core::VERSION)
}

#[no_mangle]
pub extern "system" fn Java_com_jestk_browser_engine_adblock_AdblockNative_createFromLists(
    mut env: JNIEnv,
    _: JClass,
    lists: JObjectArray,
) -> jlong {
    strings(&mut env, &lists).map_or(0, |l| to_handle(core::from_lists(l)))
}

#[no_mangle]
pub extern "system" fn Java_com_jestk_browser_engine_adblock_AdblockNative_createFromSerialized(
    env: JNIEnv,
    _: JClass,
    bytes: JByteArray,
) -> jlong {
    env.convert_byte_array(&bytes)
        .map_or(0, |b| to_handle(core::from_serialized(&b)))
}

#[no_mangle]
pub extern "system" fn Java_com_jestk_browser_engine_adblock_AdblockNative_serialize(
    env: JNIEnv,
    _: JClass,
    handle: jlong,
) -> jbyteArray {
    let bytes = unsafe { engine(handle) }.and_then(|e| core::guard(|| Some(e.serialize())));
    bytes
        .and_then(|b| env.byte_array_from_slice(&b).ok())
        .map_or(null_mut(), |a| a.into_raw())
}

#[no_mangle]
pub extern "system" fn Java_com_jestk_browser_engine_adblock_AdblockNative_useResources(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
    json: JString,
) -> jboolean {
    // Exclusive access: Kotlin calls this before the handle is shared with other threads.
    let Some(e) = (unsafe { (handle as *mut Engine).as_mut() }) else {
        return JNI_FALSE;
    };
    match string(&mut env, &json) {
        Some(j) if core::load_resources(e, &j) => JNI_TRUE,
        _ => JNI_FALSE,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_jestk_browser_engine_adblock_AdblockNative_checkRequest(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
    url: JString,
    source: JString,
    type_code: jint,
) -> jint {
    let (Some(e), Some(u), Some(s)) = (
        unsafe { engine(handle) },
        string(&mut env, &url),
        string(&mut env, &source),
    ) else {
        return 0;
    };
    core::check(e, &u, &s, type_code)
}

#[no_mangle]
pub extern "system" fn Java_com_jestk_browser_engine_adblock_AdblockNative_checkRequestRedirect(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
    url: JString,
    source: JString,
    type_code: jint,
) -> jstring {
    let (Some(e), Some(u), Some(s)) = (
        unsafe { engine(handle) },
        string(&mut env, &url),
        string(&mut env, &source),
    ) else {
        return null_mut();
    };
    core::redirect(e, &u, &s, type_code).map_or(null_mut(), |r| jstr(&mut env, &r))
}

#[no_mangle]
pub extern "system" fn Java_com_jestk_browser_engine_adblock_AdblockNative_urlCosmeticResources(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
    url: JString,
) -> jstring {
    let json = match (unsafe { engine(handle) }, string(&mut env, &url)) {
        (Some(e), Some(u)) => core::cosmetic_json(e, &u),
        _ => "{}".to_string(),
    };
    jstr(&mut env, &json)
}

#[no_mangle]
pub extern "system" fn Java_com_jestk_browser_engine_adblock_AdblockNative_hiddenClassIdSelectors(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
    classes: JObjectArray,
    ids: JObjectArray,
    exceptions: JObjectArray,
) -> jobjectArray {
    let found = (|| {
        let e = unsafe { engine(handle) }?;
        let c = strings(&mut env, &classes)?;
        let i = strings(&mut env, &ids)?;
        let x = strings(&mut env, &exceptions)?;
        Some(core::hidden_selectors(e, c, i, x))
    })()
    .unwrap_or_default();
    let string_class = env.find_class("java/lang/String");
    let out = string_class
        .and_then(|c| env.new_object_array(found.len() as i32, c, JObject::null()));
    let Ok(out) = out else { return null_mut() };
    for (i, s) in found.iter().enumerate() {
        if let Ok(js) = env.new_string(s) {
            let _ = env.set_object_array_element(&out, i as i32, js);
        }
    }
    out.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_jestk_browser_engine_adblock_AdblockNative_destroy(
    _: JNIEnv,
    _: JClass,
    handle: jlong,
) {
    if handle != 0 {
        let _ = std::panic::catch_unwind(|| drop(unsafe { Box::from_raw(handle as *mut Engine) }));
    }
}
