
export async function instantiate(imports={}, runInitializer=true) {
    const externrefBoxes = new WeakMap();
    // ref must be non-null
    function tryGetOrSetExternrefBox(ref, ifNotCached) {
        if (typeof ref !== 'object') return ifNotCached;
        const cachedBox = externrefBoxes.get(ref);
        if (cachedBox !== void 0) return cachedBox;
        externrefBoxes.set(ref, ifNotCached);
        return ifNotCached;
    }


    
    const js_code = {
        'kotlin.captureStackTrace' : () => new Error().stack,
        'kotlin.wasm.internal.throwJsError' : (message, wasmTypeName, stack) => { 
            const error = new Error();
            error.message = message;
            error.name = wasmTypeName;
            error.stack = stack;
            throw error;
             },
        'kotlin.wasm.internal.stringLength' : (x) => x.length,
        'kotlin.wasm.internal.jsExportStringToWasm' : (src, srcOffset, srcLength, dstAddr) => { 
            const mem16 = new Uint16Array(wasmExports.memory.buffer, dstAddr, srcLength);
            let arrayIndex = 0;
            let srcIndex = srcOffset;
            while (arrayIndex < srcLength) {
                mem16.set([src.charCodeAt(srcIndex)], arrayIndex);
                srcIndex++;
                arrayIndex++;
            }     
             },
        'kotlin.wasm.internal.importStringFromWasm' : (address, length, prefix) => { 
            const mem16 = new Uint16Array(wasmExports.memory.buffer, address, length);
            const str = String.fromCharCode.apply(null, mem16);
            return (prefix == null) ? str : prefix + str;
             },
        'kotlin.wasm.internal.getJsEmptyString' : () => '',
        'kotlin.wasm.internal.externrefToString' : (ref) => String(ref),
        'kotlin.wasm.internal.externrefEquals' : (lhs, rhs) => lhs === rhs,
        'kotlin.wasm.internal.externrefHashCode' : 
        (() => {
        const dataView = new DataView(new ArrayBuffer(8));
        function numberHashCode(obj) {
            if ((obj | 0) === obj) {
                return obj | 0;
            } else {
                dataView.setFloat64(0, obj, true);
                return (dataView.getInt32(0, true) * 31 | 0) + dataView.getInt32(4, true) | 0;
            }
        }
        
        const hashCodes = new WeakMap();
        function getObjectHashCode(obj) {
            const res = hashCodes.get(obj);
            if (res === undefined) {
                const POW_2_32 = 4294967296;
                const hash = (Math.random() * POW_2_32) | 0;
                hashCodes.set(obj, hash);
                return hash;
            }
            return res;
        }
        
        function getStringHashCode(str) {
            var hash = 0;
            for (var i = 0; i < str.length; i++) {
                var code  = str.charCodeAt(i);
                hash  = (hash * 31 + code) | 0;
            }
            return hash;
        }
        
        return (obj) => {
            if (obj == null) {
                return 0;
            }
            switch (typeof obj) {
                case "object":
                case "function":
                    return getObjectHashCode(obj);
                case "number":
                    return numberHashCode(obj);
                case "boolean":
                    return obj ? 1231 : 1237;
                default:
                    return getStringHashCode(String(obj)); 
            }
        }
        })(),
        'kotlin.wasm.internal.isNullish' : (ref) => ref == null,
        'kotlin.wasm.internal.externrefToInt' : (ref) => Number(ref),
        'kotlin.wasm.internal.externrefToBoolean' : (ref) => Boolean(ref),
        'kotlin.wasm.internal.externrefToLong' : (ref) => Number(ref),
        'kotlin.wasm.internal.externrefToFloat' : (ref) => Number(ref),
        'kotlin.wasm.internal.externrefToDouble' : (ref) => Number(ref),
        'kotlin.wasm.internal.intToExternref' : (x) => x,
        'kotlin.wasm.internal.getJsTrue' : () => true,
        'kotlin.wasm.internal.getJsFalse' : () => false,
        'kotlin.wasm.internal.longToExternref' : (x) => x,
        'kotlin.wasm.internal.floatToExternref' : (x) => x,
        'kotlin.wasm.internal.doubleToExternref' : (x) => x,
        'kotlin.wasm.internal.newJsArray' : () => [],
        'kotlin.wasm.internal.jsArrayPush' : (array, element) => { array.push(element); },
        'kotlin.wasm.internal.tryGetOrSetExternrefBox_$external_fun' : (p0, p1) => tryGetOrSetExternrefBox(p0, p1),
        'kotlin.js.jsCatch' : (f) => { 
            let result = null;
            try { 
                f();
            } catch (e) {
               result = e;
            }
            return result;
             },
        'kotlin.js.__convertKotlinClosureToJsClosure_(()->Unit)' : (f) => () => wasmExports['__callFunction_(()->Unit)'](f, ),
        'kotlin.js.jsThrow' : (e) => { throw e; },
        'kotlin.io.printlnImpl' : (message) => console.log(message),
        'kotlin.js.JsArray_$external_fun' : () => new Array(),
        'kotlin.js.length_$external_prop_getter' : (_this) => _this.length,
        'kotlin.js.JsArray_$external_class_instanceof' : (x) => x instanceof Array,
        'kotlin.js.JsBoolean_$external_fun' : () => new JsBoolean(),
        'kotlin.js.JsBoolean_$external_class_instanceof' : (x) => typeof x === 'boolean',
        'kotlin.js.JsNumber_$external_fun' : () => new JsNumber(),
        'kotlin.js.JsNumber_$external_class_instanceof' : (x) => typeof x === 'number',
        'kotlin.js.JsString_$external_fun' : () => new JsString(),
        'kotlin.js.JsString_$external_class_instanceof' : (x) => typeof x === 'string',
        'kotlin.js.Promise_$external_fun' : (p0) => new Promise(p0),
        'kotlin.js.__callJsClosure_((Js?)->Unit)' : (f, p0) => f(p0),
        'kotlin.js.__callJsClosure_((Js)->Unit)' : (f, p0) => f(p0),
        'kotlin.js.__convertKotlinClosureToJsClosure_((((Js?)->Unit),((Js)->Unit))->Unit)' : (f) => (p0, p1) => wasmExports['__callFunction_((((Js?)->Unit),((Js)->Unit))->Unit)'](f, p0, p1),
        'kotlin.js.then_$external_fun' : (_this, p0) => _this.then(p0),
        'kotlin.js.__convertKotlinClosureToJsClosure_((Js?)->Js?)' : (f) => (p0) => wasmExports['__callFunction_((Js?)->Js?)'](f, p0),
        'kotlin.js.then_$external_fun_1' : (_this, p0, p1) => _this.then(p0, p1),
        'kotlin.js.__convertKotlinClosureToJsClosure_((Js)->Js?)' : (f) => (p0) => wasmExports['__callFunction_((Js)->Js?)'](f, p0),
        'kotlin.js.catch_$external_fun' : (_this, p0) => _this.catch(p0),
        'kotlin.js.finally_$external_fun' : (_this, p0) => _this.finally(p0),
        'kotlin.js.Companion_$external_fun' : () => new Promise(),
        'kotlin.js.all_$external_fun' : (_this, p0) => _this.all(p0),
        'kotlin.js.race_$external_fun' : (_this, p0) => _this.race(p0),
        'kotlin.js.reject_$external_fun' : (_this, p0) => _this.reject(p0),
        'kotlin.js.resolve_$external_fun' : (_this, p0) => _this.resolve(p0),
        'kotlin.js.resolve_$external_fun_1' : (_this, p0) => _this.resolve(p0),
        'kotlin.js.Companion_$external_object_getInstance' : () => Promise,
        'kotlin.js.Companion_$external_class_instanceof' : (x) => x instanceof Promise,
        'kotlin.js.Promise_$external_class_instanceof' : (x) => x instanceof Promise,
        'kotlin.random.initialSeed' : () => ((Math.random() * Math.pow(2, 32)) | 0),
        'kotlin.test.xdescribe' : (name, fn) => xdescribe(name, fn),
        'kotlin.test.describe' : (name, fn) => describe(name, fn),
        'kotlin.test.jsThrow' : (jsException) => { throw jsException; },
        'kotlin.test.xit' : (name, fn) => xit(name, fn),
        'kotlin.test.__convertKotlinClosureToJsClosure_(()->Js?)' : (f) => () => wasmExports['__callFunction_(()->Js?)'](f, ),
        'kotlin.test.it' : (name, fn) => it(name, fn),
        'kotlin.test.throwableToJsError' : (message, stack) => { const e = new Error(); e.message = message; e.stack = stack; return e; },
        'kotlin.test.nodeArguments' : () => (typeof process != 'undefined' && typeof process.argv != 'undefined') ? process.argv.slice(2).join(' ') : '',
        'kotlin.test.d8Arguments' : () => globalThis.arguments?.join?.(' ') ?? '',
        'kotlin.test.isJasmine' : () => typeof describe === 'function' && typeof it === 'function'
    }
    
    // Placed here to give access to it from externals (js_code)
    let wasmInstance;
    let require; 
    let wasmExports;

    const isNodeJs = (typeof process !== 'undefined') && (process.release.name === 'node');
    const isStandaloneJsVM =
        !isNodeJs && (
            typeof d8 !== 'undefined' // V8
            || typeof inIon !== 'undefined' // SpiderMonkey
            || typeof jscOptions !== 'undefined' // JavaScriptCore
        );
    const isBrowser = !isNodeJs && !isStandaloneJsVM && (typeof window !== 'undefined');
    
    if (!isNodeJs && !isStandaloneJsVM && !isBrowser) {
      throw "Supported JS engine not detected";
    }
    
    const wasmFilePath = './RockHardBlocker-world-core-wasm-js-test.wasm';
    const importObject = {
        js_code,

    };
    
    try {
      if (isNodeJs) {
        const module = await import(/* webpackIgnore: true */'node:module');
        require = module.default.createRequire(import.meta.url);
        const fs = require('fs');
        const path = require('path');
        const url = require('url');
        const filepath = url.fileURLToPath(import.meta.url);
        const dirpath = path.dirname(filepath);
        const wasmBuffer = fs.readFileSync(path.resolve(dirpath, wasmFilePath));
        const wasmModule = new WebAssembly.Module(wasmBuffer);
        wasmInstance = new WebAssembly.Instance(wasmModule, importObject);
      }
      
      if (isStandaloneJsVM) {
        const wasmBuffer = read(wasmFilePath, 'binary');
        const wasmModule = new WebAssembly.Module(wasmBuffer);
        wasmInstance = new WebAssembly.Instance(wasmModule, importObject);
      }
      
      if (isBrowser) {
        wasmInstance = (await WebAssembly.instantiateStreaming(fetch(wasmFilePath), importObject)).instance;
      }
    } catch (e) {
      if (e instanceof WebAssembly.CompileError) {
        let text = `Please make sure that your runtime environment supports the latest version of Wasm GC and Exception-Handling proposals.
For more information, see https://kotl.in/wasm-help
`;
        if (isBrowser) {
          console.error(text);
        } else {
          const t = "\n" + text;
          if (typeof console !== "undefined" && console.log !== void 0) 
            console.log(t);
          else 
            print(t);
        }
      }
      throw e;
    }
    
    wasmExports = wasmInstance.exports;
    if (runInitializer) {
        wasmExports._initialize();
    }

    return { instance: wasmInstance,  exports: wasmExports };
}
