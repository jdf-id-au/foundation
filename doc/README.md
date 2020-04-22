# jdf/foundation

Ffff-framework for client-server web applications.

## Client
- [datascript](https://github.com/tonsky/datascript) store
- pure event functions
- coeffects and effects like [re-frame](https://github.com/day8/re-frame)
- React hooks via [helix](https://github.com/Lokeh/helix)
- Html5History URL fragment management
- config (baked in at build time, plus via html file)
- simple logging
- TODO ajax communication option
- TODO websocket communication option

## Common
- cross-platform time handling via [tick](https://github.com/juxt/tick) and some massaging/extra functions
- TODO data sharing over [transit](https://github.com/cognitect/transit-format)
- TODO validated ws messages via [spec](https://clojure.org/about/spec)

## Server
- config system integrated with cli
- [recaptcha](https://www.google.com/recaptcha/) support
- nonce and CSP support
- TODO websocket communication option