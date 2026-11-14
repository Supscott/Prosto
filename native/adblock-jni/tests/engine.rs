use adblock::Engine;
use adblock_jni::core::*;

const DOC: i32 = 1;
const IMAGE: i32 = 3;
const SCRIPT: i32 = 5;
const XHR: i32 = 8;
const STUBS: &str = include_str!("../../../tools/filterlists/scriptlet_stubs.json");
const GTM: &str = r#"[{"name":"googletagmanager_gtm.js","aliases":["google-analytics.com/gtm.js"],
 "kind":{"mime":"application/javascript"},"content":"Ly8gc3R1Yg=="}]"#;

fn engine(lists: &[&str]) -> Engine {
    from_lists(lists.iter().map(|s| s.to_string()).collect()).expect("engine")
}

fn scriptlet_engine(rules: &str) -> Engine {
    let mut e = engine(&[rules]);
    assert!(load_resources(&mut e, STUBS));
    e
}

#[test]
fn block_allow_and_exception() {
    let e = engine(&["||ads.example.com^", "||cdn.example.com/x.js", "@@||cdn.example.com/x.js"]);
    assert_eq!(check(&e, "https://ads.example.com/a.png", "https://site.org/", IMAGE) & BLOCK, BLOCK);
    assert_eq!(check(&e, "https://good.example.com/a.png", "https://site.org/", IMAGE), 0);
    let f = check(&e, "https://cdn.example.com/x.js", "https://site.org/", SCRIPT);
    assert_eq!(f & BLOCK, 0);
    assert_eq!(f & (EXCEPTION | MATCHED), EXCEPTION | MATCHED);
}

#[test]
fn important_beats_exception() {
    let e = engine(&["||t.example.com^$important", "@@||t.example.com^"]);
    let f = check(&e, "https://t.example.com/p", "https://site.org/", XHR);
    assert_eq!(f & (BLOCK | IMPORTANT), BLOCK | IMPORTANT);
}

#[test]
fn third_party_and_domain_options() {
    let e = engine(&["||tp.example.com^$third-party", "/banner.gif$domain=news.org"]);
    let blocked = |u, s| check(&e, u, s, IMAGE) & BLOCK != 0;
    assert!(blocked("https://tp.example.com/a.png", "https://site.org/"));
    assert!(!blocked("https://tp.example.com/a.png", "https://www.example.com/"));
    assert!(blocked("https://x.org/banner.gif", "https://news.org/a"));
    assert!(!blocked("https://x.org/banner.gif", "https://other.org/a"));
}

#[test]
fn redirect_returns_data_url_once_resources_loaded() {
    let mut e = engine(&["||google-analytics.com/gtm.js$script,redirect=googletagmanager_gtm.js"]);
    assert!(load_resources(&mut e, GTM));
    let f = check(&e, "https://google-analytics.com/gtm.js", "https://site.org/", SCRIPT);
    assert_eq!(f & (BLOCK | REDIRECT), BLOCK | REDIRECT);
    let r = redirect(&e, "https://google-analytics.com/gtm.js", "https://site.org/", SCRIPT);
    assert!(r.unwrap().starts_with("data:application/javascript;base64,"));
    assert!(redirect(&e, "https://other.com/a.js", "https://site.org/", SCRIPT).is_none());
}

#[test]
fn invalid_input_returns_allow_without_panic() {
    let e = engine(&["||ads.example.com^"]);
    assert_eq!(check(&e, "", "https://site.org/", IMAGE), 0);
    assert_eq!(check(&e, "not a url", "", IMAGE), 0);
    assert_eq!(check(&e, "https://ads.example.com/a", "https://site.org/", 99), 0);
    assert_eq!(check(&e, "https://ads.example.com/a", "https://site.org/", -1), 0);
    assert!(redirect(&e, "", "", 0).is_none());
    assert_eq!(cosmetic_json(&e, ""), r#"{"hide":[],"exceptions":[],"procedural":[],"generichide":false,"scriptlets":[]}"#);
    let mut e2 = engine(&[]);
    assert!(!load_resources(&mut e2, "not json"));
    assert!(!load_resources(&mut e2, r#"{"resources":42}"#));
}

#[test]
fn serialization_round_trip() {
    let e = engine(&["||ads.example.com^", "example.org##.ad"]);
    let copy = from_serialized(&e.serialize()).expect("round trip");
    for engine in [&e, &copy] {
        assert_eq!(check(engine, "https://ads.example.com/a", "https://s.org/", IMAGE) & BLOCK, BLOCK);
        assert_eq!(check(engine, "https://ok.example.com/a", "https://s.org/", IMAGE), 0);
    }
    assert_eq!(cosmetic_json(&e, "https://example.org/"), cosmetic_json(&copy, "https://example.org/"));
    assert!(from_serialized(b"garbage bytes").is_none());
    assert!(from_serialized(&[]).is_none());
}

#[test]
fn cosmetic_hide_exception_generichide_and_generic_selectors() {
    let e = engine(&[
        "example.org##.site-ad\n##.generic-ad\n###banner\nexample.org#@#.generic-ad\n@@||nogeneric.org^$generichide\n",
    ]);
    let v: serde_json::Value = serde_json::from_str(&cosmetic_json(&e, "https://example.org/p")).unwrap();
    assert_eq!(v["hide"], serde_json::json!([".site-ad"]));
    assert_eq!(v["generichide"], false);
    assert!(v["exceptions"].as_array().unwrap().iter().any(|x| x == ".generic-ad"));
    let g: serde_json::Value = serde_json::from_str(&cosmetic_json(&e, "https://nogeneric.org/")).unwrap();
    assert_eq!(g["generichide"], true);
    let sel = hidden_selectors(&e, vec!["generic-ad".into(), "other".into()], vec!["banner".into()], vec![]);
    assert!(sel.contains(&".generic-ad".to_string()) && sel.contains(&"#banner".to_string()));
    let sel = hidden_selectors(&e, vec!["generic-ad".into()], vec![], vec![".generic-ad".into()]);
    assert!(sel.is_empty());
}

#[test]
fn scriptlets_parse_from_stub_resources() {
    let e = scriptlet_engine("example.org##+js(json-prune, a, b)\nexample.org##+js(set-constant, x.y, true)\n");
    let v: serde_json::Value = serde_json::from_str(&cosmetic_json(&e, "https://example.org/")).unwrap();
    let mut got: Vec<_> = v["scriptlets"].as_array().unwrap().clone();
    got.sort_by_key(|s| s[0].as_str().unwrap().to_string());
    assert_eq!(
        serde_json::Value::Array(got),
        serde_json::json!([["json-prune", "a", "b"], ["set-constant", "x.y", "true"]])
    );
    let none: serde_json::Value = serde_json::from_str(&cosmetic_json(&e, "https://other.org/")).unwrap();
    assert_eq!(none["scriptlets"], serde_json::json!([]));
}

#[test]
fn scriptlet_alias_and_special_characters_survive() {
    let e = scriptlet_engine(
        "example.org##+js(aopr, window.foo)\nexample.net##+js(json-prune, a\\,b, \"q\", back\\\\slash, 'x')\n",
    );
    let a: serde_json::Value = serde_json::from_str(&cosmetic_json(&e, "https://example.org/")).unwrap();
    assert_eq!(a["scriptlets"], serde_json::json!([["abort-on-property-read", "window.foo"]]));
    let b: serde_json::Value = serde_json::from_str(&cosmetic_json(&e, "https://example.net/")).unwrap();
    let s = &b["scriptlets"][0];
    assert_eq!(s[0], "json-prune");
    assert_eq!(s[1], "a,b");
    assert!(s[2].as_str().unwrap().contains('q'));
    assert!(s[3].as_str().unwrap().contains("slash"));
}

#[test]
fn parse_scriptlets_drops_unfilled_slots_and_ignores_noise() {
    let text = "var f = function(){};\ntry {\n[\"json-prune\",\"a\",\"{{2}}\",\"{{3}}\"]\n} catch ( e ) { }\n";
    assert_eq!(parse_scriptlets(text), vec![vec!["json-prune".to_string(), "a".to_string()]]);
    assert!(parse_scriptlets("").is_empty());
}

#[test]
fn popup_option_does_not_block_document_requests() {
    let e = engine(&["||popup.example^$popup"]);
    let f = check(&e, "https://popup.example/landing", "https://site.org/", DOC);
    println!("popup rule vs document request flags = {f}");
    assert_eq!(f & BLOCK, 0, "crate now honours $popup on document requests; revisit CB-11");
}

#[test]
fn exception_in_one_list_neutralises_block_in_another_list() {
    let e = engine(&["||tracker.example^", "@@||tracker.example/needed.js^$script"]);
    assert_eq!(check(&e, "https://tracker.example/needed.js", "https://s.org/", SCRIPT) & BLOCK, 0);
    assert_eq!(check(&e, "https://tracker.example/other.js", "https://s.org/", SCRIPT) & BLOCK, BLOCK);
}

#[test]
fn type_codes_map_to_crate_strings() {
    for (code, name) in [(0, "other"), (1, "document"), (8, "xmlhttprequest"), (10, "ping")] {
        assert_eq!(request_type(code), Some(name));
    }
    assert_eq!(request_type(11), None);
}

const MERGED: &str = include_str!("../../../app/src/main/assets/adblock/resources.json");

#[test]
fn shipped_resources_json_carries_redirects_and_scriptlets() {
    let mut e = engine(&[
        "fixture.test##+js(set-constant, fxAdblockDetected, false)\n\
         fixture.test##+js(set-constant, fxOpenAd, noopFunc)\n\
         ||fixture-gtm.test/gtm.js$script,redirect=googletagmanager_gtm.js\n",
    ]);
    assert!(load_resources(&mut e, MERGED));
    let v: serde_json::Value = serde_json::from_str(&cosmetic_json(&e, "https://fixture.test/scriptlet.html")).unwrap();
    let mut got = v["scriptlets"].as_array().unwrap().clone();
    got.sort_by_key(|s| s[1].as_str().unwrap().to_string());
    assert_eq!(
        serde_json::Value::Array(got),
        serde_json::json!([
            ["set-constant", "fxAdblockDetected", "false"],
            ["set-constant", "fxOpenAd", "noopFunc"]
        ])
    );
    assert!(redirect(&e, "https://fixture-gtm.test/gtm.js", "https://fixture.test/", SCRIPT).is_some());
}

#[test]
fn engine_is_shareable_across_threads() {
    let e = std::sync::Arc::new(engine(&["||ads.example.com^"]));
    let hs: Vec<_> = (0..4)
        .map(|_| {
            let e = e.clone();
            std::thread::spawn(move || check(&e, "https://ads.example.com/a", "https://s.org/", IMAGE))
        })
        .collect();
    for h in hs {
        assert_eq!(h.join().unwrap() & BLOCK, BLOCK);
    }
}

#[test]
fn guarded_engine_serves_concurrent_callers_correctly() {
    let g = std::sync::Arc::new(Guarded::new(engine(&["||ads.example.com^", "@@||ads.example.com/ok.js"])));
    let hs: Vec<_> = (0..4)
        .map(|t| {
            let g = g.clone();
            std::thread::spawn(move || {
                for i in 0..2_000 {
                    let blocked = g.with(|e| check(e, &format!("https://ads.example.com/{t}/{i}.png"), "https://s.org/", IMAGE));
                    let allowed = g.with(|e| check(e, "https://ads.example.com/ok.js", "https://s.org/", SCRIPT));
                    assert_eq!(blocked & BLOCK, BLOCK);
                    assert_eq!(allowed & BLOCK, 0);
                }
            })
        })
        .collect();
    for h in hs {
        h.join().unwrap();
    }
}

#[test]
fn guarded_lock_is_released_after_a_panic() {
    let g = Guarded::new(engine(&["||ads.example.com^"]));
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| g.with(|_| panic!("boom"))));
    assert_eq!(g.with(|e| check(e, "https://ads.example.com/a", "https://s.org/", IMAGE)) & BLOCK, BLOCK);
}

fn standard_ads_engine() -> Engine {
    let dir = concat!(env!("CARGO_MANIFEST_DIR"), "/../../app/src/main/assets/filters/standard/ads");
    let lists: Vec<String> = ["easylist", "ru-adlist", "ubo-filters"]
        .iter()
        .map(|id| std::fs::read_to_string(format!("{dir}/{id}.txt")).expect("bundled list"))
        .collect();
    engine(&lists.iter().map(String::as_str).collect::<Vec<_>>())
}

#[test]
fn standard_lists_answer_generic_class_and_id_selectors() {
    let e = standard_ads_engine();
    let sel = hidden_selectors(
        &e,
        vec!["advert--banner-wrap".into(), "no-such-class-xyz".into()],
        vec!["aboveplayerad".into()],
        vec![],
    );
    assert!(sel.contains(&".advert--banner-wrap".to_string()), "got {sel:?}");
    assert!(sel.contains(&"#aboveplayerad".to_string()), "got {sel:?}");
    assert!(sel.iter().all(|s| !s.contains("no-such-class-xyz")));
    let excepted = hidden_selectors(&e, vec!["advert--banner-wrap".into()], vec![], vec![".advert--banner-wrap".into()]);
    assert!(excepted.iter().all(|s| s != ".advert--banner-wrap"));
}
