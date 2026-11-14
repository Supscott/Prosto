// External on purpose: the page's CSP forbids inline script.
window.fxAdblockDetected = true;
setTimeout(function () {
  var hidden = fxHidden('.fx-site-ad');
  var scriptlet = window.fxAdblockDetected === false;
  fxResult(hidden && scriptlet, 'cosmetic ' + (hidden ? 'applied' : 'missing') + ', scriptlet ' + (scriptlet ? 'applied' : 'missing'));
}, 800);
