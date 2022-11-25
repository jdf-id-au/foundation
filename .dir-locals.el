((clojure-mode . ((cider-clojure-cli-aliases . "-A:dev")))
 (nil . ((cider-default-cljs-repl . shadow)
	 (cider-shadow-default-options . "app")
	 ; .nrepl.edn problem https://github.com/clojure-emacs/cider/issues/2927
	 (cider-jack-in-nrepl-middlewares . ("cider.nrepl/cider-middleware"
					     "shadow.cljs.devtools.server.nrepl/middleware")))))
