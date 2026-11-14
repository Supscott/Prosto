//! Runs the expectations of the pages in tools/fixtures/adblock/ against the real engine: the
//! bundled Standard ads lists plus tools/fixtures/adblock/fixture-filters.txt, served from a LAN
//! address the way a phone sees it. A fixture page whose rule the engine cannot honour fails here,
//! before anyone opens it on a device.
use adblock::Engine;
use adblock_jni::core::*;

const DOC: i32 = 1;
const IMAGE: i32 = 3;
const SCRIPT: i32 = 5;
const FONT: i32 = 6;
const XHR: i32 = 8;

const HOST: &str = "192.168.1.28";
const FIXTURE_FILTERS: &str = include_str!("../../../tools/fixtures/adblock/fixture-filters.txt");
const RESOURCES: &str = include_str!("../../../app/src/main/assets/adblock/resources.json");

fn page(name: &str) -> String {
    format!("http://{HOST}:8765/{name}")
}

fn fixture_engine() -> Engine {
    let dir = concat!(env!("CARGO_MANIFEST_DIR"), "/../../app/src/main/assets/filters/standard/ads");
    let mut names: Vec<_> = std::fs::read_dir(dir)
        .expect("bundled ads lists")
        .map(|e| e.unwrap().path())
        .filter(|p| p.extension().is_some_and(|x| x == "txt"))
        .collect();
    names.sort();
    let mut lists: Vec<String> = names.iter().map(|p| std::fs::read_to_string(p).unwrap()).collect();
    lists.push(FIXTURE_FILTERS.replace("{{HOST}}", HOST));
    let mut engine = from_lists(lists).expect("engine");
    assert!(load_resources(&mut engine, RESOURCES));
    engine
}

fn cosmetic(engine: &Engine, url: &str) -> serde_json::Value {
    serde_json::from_str(&cosmetic_json(engine, url)).unwrap()
}

fn hides(resources: &serde_json::Value, selector: &str) -> bool {
    resources["hide"].as_array().unwrap().iter().any(|s| s == selector)
}

#[test]
fn fixture_pages_match_the_engine() {
    let e = fixture_engine();
    let src = page("network.html");

    // network.html
    for (path, ty) in [("fx-block/a.png", IMAGE), ("fx-block/a.js", SCRIPT), ("fx-block/a.woff2", FONT), ("fx-block/data.json", XHR)] {
        assert_eq!(check(&e, &page(path), &src, ty) & BLOCK, BLOCK, "{path} must be blocked");
    }
    let allowed = check(&e, &page("fx-block/allowed.js"), &src, SCRIPT);
    assert_eq!(allowed & BLOCK, 0, "the @@ exception must let allowed.js through");
    let important = check(&e, &page("fx-important/blocked.js"), &src, SCRIPT);
    assert_eq!(important & (BLOCK | IMPORTANT), BLOCK | IMPORTANT, "$important must beat the exception");

    // redirect.html
    let gtm = redirect(&e, "http://fixture-gtm.test/gtm.js", &page("redirect.html"), SCRIPT);
    assert!(gtm.is_some_and(|u| u.starts_with("data:application/javascript;base64,")));

    // document-blocked.html
    assert_eq!(check(&e, "http://fixture-docblock.test/", "http://fixture-docblock.test/", DOC) & BLOCK, BLOCK);

    // cosmetic-specific.html, overlay.html
    let specific = cosmetic(&e, &page("cosmetic-specific.html"));
    assert!(hides(&specific, ".fx-site-ad"));
    assert!(hides(&specific, "a.fx-overlay"));

    // cosmetic-generic.html: EasyList's local-host $generichide exceptions are badfiltered.
    let generic = cosmetic(&e, &page("cosmetic-generic.html"));
    assert_eq!(generic["generichide"], false, "generic hiding must stay on for the fixture host");
    let selectors = hidden_selectors(&e, vec!["fx-generic-ad".into(), "fx-keep".into()], vec![], vec![]);
    assert_eq!(selectors, vec![".fx-generic-ad".to_string()]);

    // generichide.html
    let opted_out = cosmetic(&e, &page("generichide.html"));
    assert_eq!(opted_out["generichide"], true);
    assert!(hides(&opted_out, ".fx-site-ad"));

    // scriptlet.html, csp-strict.html
    for name in ["scriptlet.html", "csp-strict.html"] {
        let scriptlets = cosmetic(&e, &page(name))["scriptlets"].clone();
        let calls: Vec<String> = scriptlets.as_array().unwrap().iter().map(|c| c.to_string()).collect();
        assert!(calls.contains(&r#"["set-constant","fxAdblockDetected","false"]"#.to_string()), "{name}: {calls:?}");
        assert!(calls.contains(&r#"["set-constant","fxOpenAd","noopFunc"]"#.to_string()), "{name}: {calls:?}");
    }
}

#[test]
fn easylist_turns_generic_hiding_off_on_local_hosts_without_the_fixture_badfilter() {
    let dir = concat!(env!("CARGO_MANIFEST_DIR"), "/../../app/src/main/assets/filters/standard/ads");
    let easylist = std::fs::read_to_string(format!("{dir}/easylist.txt")).unwrap();
    let e = from_lists(vec![easylist]).expect("engine");
    assert_eq!(cosmetic(&e, &page("cosmetic-generic.html"))["generichide"], true);
}
