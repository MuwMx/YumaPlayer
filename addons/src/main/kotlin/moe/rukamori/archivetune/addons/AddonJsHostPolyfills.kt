package moe.rukamori.archivetune.addons

internal object AddonJsHostPolyfills {
    val HOST_INIT_SCRIPT = """
        globalThis.console = {
            log: function() { __yumaLog(Array.prototype.slice.call(arguments).join(' ')); },
            info: function() { __yumaInfo(Array.prototype.slice.call(arguments).join(' ')); },
            warn: function() { __yumaWarn(Array.prototype.slice.call(arguments).join(' ')); },
            error: function() { __yumaError(Array.prototype.slice.call(arguments).join(' ')); }
        };

        globalThis.Headers = class Headers {
            constructor(init) {
                this.map = {};
                if (init) {
                    if (init instanceof Headers) {
                        Object.assign(this.map, init.map);
                    } else if (Array.isArray(init)) {
                        for (var i = 0; i < init.length; i++) this.set(init[i][0], init[i][1]);
                    } else if (typeof init === 'object') {
                        for (var k in init) this.set(k, init[k]);
                    }
                }
            }
            get(k) { return this.map[String(k).toLowerCase()] || null; }
            set(k, v) { this.map[String(k).toLowerCase()] = String(v); }
            has(k) { return Object.prototype.hasOwnProperty.call(this.map, String(k).toLowerCase()); }
            delete(k) { delete this.map[String(k).toLowerCase()]; }
        };

        globalThis.URLSearchParams = class URLSearchParams {
            constructor(init) {
                this.pairs = [];
                if (typeof init === 'string') {
                    var s = init.replace(/^\?/, '');
                    var parts = s ? s.split('&') : [];
                    for (var i = 0; i < parts.length; i++) {
                        var kv = parts[i].split('=');
                        this.append(decodeURIComponent(kv[0]), kv.length > 1 ? decodeURIComponent(kv[1]) : '');
                    }
                } else if (Array.isArray(init)) {
                    for (var j = 0; j < init.length; j++) this.append(init[j][0], init[j][1]);
                }
            }
            append(k, v) { this.pairs.push([String(k), String(v)]); }
            get(k) {
                for (var i = 0; i < this.pairs.length; i++) {
                    if (this.pairs[i][0] === String(k)) return this.pairs[i][1];
                }
                return null;
            }
            toString() {
                var out = [];
                for (var i = 0; i < this.pairs.length; i++) {
                    out.push(encodeURIComponent(this.pairs[i][0]) + '=' + encodeURIComponent(this.pairs[i][1]));
                }
                return out.join('&');
            }
        };

        globalThis.URL = class URL {
            constructor(url, base) {
                this.href = url;
                var raw = url;
                if (base && !url.match(/^[a-z]+:\/\//i)) {
                    raw = base.replace(/\/+$/, '') + '/' + url.replace(/^\/+/, '');
                }
                this.href = raw;
                var m = raw.match(/^([a-z]+:)\/\/([^/?#]+)([^?#]*)(?:\?([^#]*))?(?:#(.*))?$/i);
                if (m) {
                    this.protocol = m[1];
                    this.host = m[2];
                    this.hostname = m[2].split(':')[0];
                    this.pathname = m[3] || '/';
                    this.search = m[4] ? '?' + m[4] : '';
                    this.hash = m[5] ? '#' + m[5] : '';
                } else {
                    this.protocol = ''; this.host = ''; this.hostname = ''; this.pathname = raw; this.search = ''; this.hash = '';
                }
                this.searchParams = new URLSearchParams(this.search);
            }
            toString() { return this.href; }
        };

        globalThis.AbortController = class AbortController {
            constructor() { this.signal = { aborted: false }; }
            abort() { this.signal.aborted = true; }
        };

        globalThis.fetch = async function(url, options) {
            options = options || {};
            var method = (options.method || 'GET').toUpperCase();
            var headers = options.headers || {};
            if (headers instanceof Headers) headers = headers.map;
            var body = options.body;
            if (typeof body === 'object' && body !== null) body = JSON.stringify(body);
            var reqJson = JSON.stringify({ url: String(url), method: method, headers: headers, body: body });
            var respRaw = await __yumaFetch(reqJson);
            var parsed = JSON.parse(respRaw);
            return {
                status: parsed.status,
                ok: parsed.ok,
                statusText: parsed.ok ? 'OK' : 'Error',
                text: async function() { return parsed.body; },
                json: async function() { return JSON.parse(parsed.body); },
                headers: new Headers(parsed.headers || {})
            };
        };

        globalThis.setTimeout = function(fn, ms) {
            (async function() {
                await __yumaDelay(String(ms || 0));
                if (typeof fn === 'function') fn();
            })();
            return 1;
        };
        globalThis.clearTimeout = function() {};
    """.trimIndent()
}
