(defproject SuperSelect "0.1.0"
  :description "SuperSelect. Select the Web."
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/clojurescript "1.9.293"]
                 [org.clojure/core.async "0.2.395"]
                 [org.omcljs/om "1.0.0-alpha47" :exclusions [cljsjs/react]]
                 [cljsjs/react-with-addons "15.3.1-0"]
                 [cljsjs/jquery "2.2.4-0"]
                 [jayq "2.5.4"]
                 [binaryage/oops "0.5.2"]
                 [binaryage/chromex "0.5.1"]
                 [binaryage/devtools "0.8.2"]
                 [cljsjs/papaparse "4.1.1-1"]
                 [com.taoensso/tufte "1.1.0"]
                 [chronoid "0.1.1"]
                 [figwheel "0.5.8"]
                 [environ "1.1.0"]]

  :plugins [[lein-cljsbuild "1.1.4"]
            [lein-figwheel "0.5.8"]
            [lein-environ "1.1.0"]
            [lein-shell "0.5.0"]]

  :source-paths ["src/common"
                 "src/background"
                 "src/content_script"
                 "src/workspace"
                 "src/sandbox"]

  :clean-targets ^{:protect false} ["target"
                                    "releases"
                                    "resources/unpacked/compiled"
                                    "resources/release/compiled"]

  :cljsbuild {:builds {}}

  :profiles {:unpacked
             [{:cljsbuild
               {:builds
                [{:id "background"
                  :source-paths ["src/background" "src/common"]
                  :figwheel true
                  :compiler {:output-to "resources/unpacked/compiled/background/main.js"
                             :output-dir "resources/unpacked/compiled/background"
                             :asset-path "compiled/background"
                             :preloads [devtools.preload]
                             :main zelector.background
                             :optimizations :none
                             :source-map true
                             :externs ["foreign-lib/dexie/dexie-externs.js"
                                       "foreign-lib/handsontable/handsontable-externs.js"]
                             :foreign-libs [{:file "foreign-lib/dexie/dexie.js"
                                             :provides ["dexie"]}
                                            {:file "foreign-lib/handsontable/handsontable.full.js"
                                             :provides ["handsontable"]}]}}
                 {:id "workspace"
                  :source-paths ["src/workspace" "src/common"]
                  :figwheel {:on-jsload "zelector.workspace.core/fig-reload-hook"}
                  :compiler {:output-to "resources/unpacked/compiled/workspace/main.js"
                             :output-dir "resources/unpacked/compiled/workspace"
                             :asset-path "compiled/workspace"
                             :preloads [devtools.preload]
                             :main zelector.workspace
                             :optimizations :none
                             :source-map true
                             :externs ["foreign-lib/dexie/dexie-externs.js"
                                       "foreign-lib/handsontable/handsontable-externs.js"]
                             :foreign-libs [{:file "foreign-lib/dexie/dexie.js"
                                             :provides ["dexie"]}
                                            {:file "foreign-lib/handsontable/handsontable.full.js"
                                             :provides ["handsontable"]}]}}]}}]
             :sandbox
             {:cljsbuild
              {:builds
               [{:id "sandbox"
                 :source-paths ["src/sandbox" "src/content_script" "src/common"]
                 :figwheel {:on-jsload "zelector.sandbox/fig-reload-hook"}
                 :compiler {:output-to "resources/unpacked/compiled/sandbox/main.js"
                            :output-dir "resources/unpacked/compiled/sandbox"
                            :asset-path "compiled/sandbox"
                            :preloads [devtools.preload]
                            :main zelector.sandbox
                            :optimizations :none
                            :source-map true
                            :externs ["foreign-lib/dexie/dexie-externs.js"
                                      "foreign-lib/handsontable/handsontable-externs.js"]
                            :foreign-libs [{:file "foreign-lib/dexie/dexie.js"
                                            :provides ["dexie"]}
                                           {:file "foreign-lib/handsontable/handsontable.full.js"
                                            :provides ["handsontable"]}]}}]}}
             :unpacked-content-script
             {:cljsbuild
              {:builds
               [{:id "content-script"
                 :source-paths ["src/content_script" "src/common"]
                 :compiler {:output-to "resources/unpacked/compiled/content-script/main.js"
                            :output-dir "resources/unpacked/compiled/content-script"
                            :asset-path "compiled/content-script"
                            :main zelector.content-script
                            :optimizations :advanced
                            :pseudo-names true
                            :pretty-print false
                            :externs ["foreign-lib/dexie/dexie-externs.js"
                                      "foreign-lib/handsontable/handsontable-externs.js"]
                            :foreign-libs [{:file "foreign-lib/dexie/dexie.min.js"
                                            :provides ["dexie"]}
                                           {:file "foreign-lib/handsontable/handsontable.full.min.js"
                                            :provides ["handsontable"]}]}}]}}
             :figwheel
             {:figwheel {:server-port 6888
                         :server-logfile ".figwheel.log"
                         :repl true
                         :css-dirs ["resources/unpacked/css"]}}
             :release
             [{:env {:chromex-elide-verbose-logging "true"}
               :cljsbuild
               {:builds
                [{:id "background"
                  :source-paths ["src/background" "src/common"]
                  :compiler {:output-to "resources/release/compiled/background.js"
                             :output-dir "resources/release/compiled/background"
                             :asset-path "compiled/background"
                             :main zelector.background
                             :optimizations :advanced
                             :elide-asserts true
                             :externs ["foreign-lib/dexie/dexie-externs.js"
                                       "foreign-lib/handsontable/handsontable-externs.js"]
                             :foreign-libs [{:file "foreign-lib/dexie/dexie.min.js"
                                             :provides ["dexie"]}
                                            {:file "foreign-lib/handsontable/handsontable.full.min.js"
                                             :provides ["handsontable"]}]
                             :language-in :ecmascript5 :language-out :ecmascript5}}
                 {:id "workspace"
                  :source-paths ["src/workspace" "src/common"]
                  :compiler {:output-to "resources/release/compiled/workspace.js"
                             :output-dir "resources/release/compiled/workspace"
                             :asset-path "compiled/workspace"
                             :main zelector.workspace
                             :optimizations :advanced
                             :elide-asserts true
                             :externs ["foreign-lib/dexie/dexie-externs.js"
                                       "foreign-lib/handsontable/handsontable-externs.js"]
                             :foreign-libs [{:file "foreign-lib/dexie/dexie.min.js"
                                             :provides ["dexie"]}
                                            {:file "foreign-lib/handsontable/handsontable.full.min.js"
                                             :provides ["handsontable"]}]
                             :language-in :ecmascript5 :language-out :ecmascript5}}
                 {:id "content-script"
                  :source-paths ["src/content_script" "src/common"]
                  :compiler {:output-to "resources/release/compiled/content-script.js"
                             :output-dir "resources/release/compiled/content-script"
                             :asset-path "compiled/content-script"
                             :main zelector.content-script
                             :optimizations :advanced
                             :elide-asserts true
                             :externs ["foreign-lib/dexie/dexie-externs.js"
                                       "foreign-lib/handsontable/handsontable-externs.js"]
                             :foreign-libs [{:file "foreign-lib/dexie/dexie.min.js"
                                             :provides ["dexie"]}
                                            {:file "foreign-lib/handsontable/handsontable.full.min.js"
                                             :provides ["handsontable"]}]
                             :closure-defines {"goog.DEBUG" false}
                             :language-in :ecmascript5 :language-out :ecmascript5}}]}}]}

  :aliases {"dev-build" ["with-profile" "+unpacked,+unpacked-content-script" "cljsbuild" "once"]
            "content-dev" ["with-profile" "+unpacked-content-script" "cljsbuild" "auto" "content-script"]
            "fig-dev" ["with-profile" "+unpacked,+figwheel" "figwheel" "background" "workspace"]
            "sandbox" ["with-profile" "+sandbox,+figwheel" "figwheel" "sandbox"]
            "release" ["with-profile" "+release" "do"
                       ["clean"]
                       ["cljsbuild" "once" "background" "workspace" "content-script"]]
            "package" ["shell" "scripts/package.sh"]})
