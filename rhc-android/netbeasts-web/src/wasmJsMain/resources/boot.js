// Loads the game only where it can run. Kotlin/Wasm needs wasm garbage collection
// (Chrome/Edge 119+, Firefox 120+, Safari 18.2+); anywhere else, say so instead of
// going blank.
(function () {
    // a module whose only type is a struct: it validates only where wasm GC is supported
    var gcModule = new Uint8Array([0, 97, 115, 109, 1, 0, 0, 0, 1, 5, 1, 95, 1, 127, 0]);
    var ok = false;
    try {
        ok = typeof WebAssembly === "object" && WebAssembly.validate(gcModule);
    } catch (e) {
        ok = false;
    }
    function tooOld() {
        document.getElementById("old").hidden = false;
    }
    if (!ok) {
        tooOld();
        return;
    }
    var s = document.createElement("script");
    s.src = "netbeasts.js";
    s.onerror = tooOld;
    document.body.appendChild(s);
})();
