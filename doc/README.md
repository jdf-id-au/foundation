# jdf/foundation

Ffff-framework for client-server web applications.

## Structure

### Client
- [datascript](https://github.com/tonsky/datascript) store
- pure event functions
- coeffects and effects like [re-frame](https://github.com/day8/re-frame)
- React hooks via [helix](https://github.com/Lokeh/helix)
- Html5History URL fragment management
- config (baked in at build time, plus via html file)
- simple logging
- TODO ajax communication option
- TODO websocket communication option

### Common
- cross-platform time handling via [tick](https://github.com/juxt/tick) and some massaging/extra functions
- TODO communication over [transit](https://github.com/cognitect/transit-format)
- TODO validated ws messages via [spec](https://clojure.org/about/spec)

### Server
- config system integrated with cli
- [recaptcha](https://www.google.com/recaptcha/) support
- [nonce](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Security-Policy/script-src ) and [CSP](https://developer.mozilla.org/en-US/docs/Web/HTTP/CSP) support
- TODO websocket communication option

## Usage

1. pull in via deps `jdf/foundation {:local/root "../foundation"}` for the time being
1. `npm init`
1. copy dependencies from `package.json`
1. `npm i`
1. copy `shadow-cljs.edn` and adjust
1. copy basic structure from `dev/user.cljs` into appropriate client namespace