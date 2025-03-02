# jdf/foundation

Ffff-framework for client-server web applications. Very alpha.

Pursues [Jamstack](https://jamstack.org/) concept, with statically served client (configurable via html and edn), websocket-centric server, and common, `clojure.spec`-ed messages over [transit](https://github.com/cognitect/transit-format).

## Structure

### Client: `jdf/foundation-client`
- [datascript](https://github.com/tonsky/datascript) store
- pure event functions
- subscriptions, coeffects and effects somewhat like [re-frame](https://github.com/day8/re-frame)
- React hooks via [helix](https://github.com/Lokeh/helix)
- Html5History URL fragment management
- config (baked in at build time, plus via html file)
- simple logging to console via [devtools](https://github.com/binaryage/cljs-devtools)
- put together with [shadow-cljs](https://github.com/thheller/shadow-cljs)
- websocket communication

### Common: `jdf/foundation-common`
- cross-platform messaging/extra functions incl time conveniences from [jdf/temper](https://github.com/jdf-id-au/temper)
- communication over [transit](https://github.com/cognitect/transit-format)
- validated ws messages via [spec](https://clojure.org/about/spec)

### Server: `jdf/foundation-server`
- config system integrated with cli
- built on [jdf/talk](https://github.com/jdf-id-au/talk)
- typed http messages TODO json or transit depending on headers
- websocket messages 
- limited file serving for convenience (better to serve static client with dedicated webserver like [nginx](https://www.nginx.com/))
- relies on reverse proxy for TLS
- TODO feature switches

[comment]: <> (- [recaptcha]&#40;https://www.google.com/recaptcha/&#41; support)
[comment]: <> (- [nonce]&#40;https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Security-Policy/script-src &#41; and [CSP]&#40;https://developer.mozilla.org/en-US/docs/Web/HTTP/CSP&#41; support)

## Usage

Pull in each part via deps, e.g. `jdf/foundation-server` at
* `{:local/root "../foundation/parts/server"}` or
* `{:git/url "https://github.com/jdf-id-au/foundation.git" :sha ... :deps/root "parts/server"}`
    
This allows application's own parts (e.g. aliases) to pull only what they need.
   
### Server
1. TODO example boilerplate (could go in f.s.api ns doc)

### Client 
1. `npm init`
1. `clj -m cljs.main --install-deps` [installs upstream deps](https://clojurescript.org/reference/compiler-options#install-deps)
1. `npm i`
1. copy `shadow-cljs.edn` and `build-client.edn` and adjust
1. copy basic structure from `dev/user.cljs` into appropriate client namespace
