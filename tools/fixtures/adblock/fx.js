// Shared helpers for the fixture pages. Results go to #result and to the document title so an
// instrumented test or a person can read the verdict without knowing each page.
function fxResult(ok, message) {
  var el = document.getElementById('result');
  el.textContent = (ok ? 'PASS: ' : 'FAIL: ') + message;
  el.className = ok ? 'pass' : 'fail';
  document.title = (ok ? 'PASS ' : 'FAIL ') + document.title.replace(/^(PASS|FAIL) /, '');
}

// Paths the fixture server has seen requests for (requests a blocker stopped never show up).
function fxHits() {
  return fetch('/__hits', { cache: 'no-store' }).then(function (r) { return r.json(); });
}

function fxHidden(selector) {
  var el = document.querySelector(selector);
  return el === null || getComputedStyle(el).display === 'none' || el.offsetHeight === 0;
}
