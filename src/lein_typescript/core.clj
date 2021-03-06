(ns ^{:author "Vladislav Bauer"}
  lein-typescript.core
  (:import [java.io File])
  (:require [leiningen.npm :as npm]
            [leiningen.npm.process :as process]
            [leiningen.core.main :as main]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as string]))


; Internal API: Common

(defn- error [ex]
  (println
   (string/join
    "\r\n"
    [(str "An error has occurred: " (.getMessage ex))
     "Something is wrong:"
     " - installation: npm install typescript -g"
     " - configuration: https://github.com/vbauer/lein-typescript"])))

(defn- clean-path [p]
  (if (not (nil? p))
    (if (.startsWith (System/getProperty "os.name") "Windows")
      (string/replace p "/" "\\")
      (string/replace p "\\" "/"))))

(defn- to-coll [e] (if (nil? e) [] (if (sequential? e) e [e])))
(defn- file-path [& parts] (string/join File/separator parts))
(defn- abs-path [f] (.getAbsolutePath f))
(defn- scan-files [patterns] (set (mapcat fs/glob (map clean-path patterns))))


; Internal API: Configuration

(def ^:private DEF_TYPESCRIPT_CMD "tsc")
(def ^:private DEF_TYPESCRIPT_DIR
  (file-path "node_modules" "typescript" "bin"))


(defn- configs [project] (to-coll (get project :typescript)))
(defn- config-files [conf k]
  (scan-files (to-coll (get conf k))))

(defn- conf-sources [conf] (config-files conf :sources))
(defn- conf-excludes [conf] (config-files conf :excludes))
(defn- conf-out [conf] (get conf :out))
(defn- conf-out-dir [conf] (get conf :out-dir))
(defn- conf-debug [conf] (get conf :debug false))
(defn- conf-remove-comments [conf] (get conf :remove-comments false))
(defn- conf-preserve-const-enums [conf] (get conf :preserve-const-enums false))
(defn- conf-declaration [conf] (get conf :declaration false))
(defn- conf-module [conf] (get conf :module))
(defn- conf-source-map [conf] (get conf :source-map false))
(defn- conf-target [conf] (get conf :target))
(defn- conf-watch [conf] (get conf :watch false))
(defn- conf-suppress-implicit-any-index-errors [conf]
  (get conf :suppress-implicit-any-index-errors))

(defn- debug-log [conf & args]
  (if (conf-debug conf)
    (println (apply string/join " " args))))


; Internal API: Runner configuration

(defn- source-list [conf]
  (let [src (conf-sources conf)
        ex (conf-excludes conf)
        sources (remove (fn [s] (some #(.compareTo % s) ex)) src)]
    (if (empty? sources)
      (throw (RuntimeException.
              "Source list is empty. Check parameters :sources & :excludes"))
      (map abs-path sources))))

(defn- param-out [conf] (if-let [out (conf-out conf)] ["--out" out]))
(defn- param-out-dir [conf] (if-let [out-dir (conf-out-dir conf)] ["--outDir" out-dir]))
(defn- param-remove-comments [conf] (if (conf-remove-comments conf) ["--removeComments"]))
(defn- param-preserve-const-enums [conf] (if (conf-preserve-const-enums conf) ["--preserveConstEnums"]))
(defn- param-declaration [conf] (if (conf-declaration conf) ["--declaration"]))
(defn- param-module [conf] (if-let [module (conf-module conf)] ["--module" (name module)]))
(defn- param-source-map [conf] (if (conf-source-map conf) ["--sourceMap"]))
(defn- param-target [conf] (if-let [t (conf-target conf)] ["--target" (string/upper-case (name t))]))
(defn- param-watch [conf] (if (conf-watch conf) ["--watch"]))
(defn- param-suppress-implicit-any-index-errors [conf]
  (if (conf-suppress-implicit-any-index-errors conf) ["--suppressImplicitAnyIndexErrors"]))


; Internal API: Runner

(defn- typescript-cmd []
  (let [local (file-path DEF_TYPESCRIPT_DIR DEF_TYPESCRIPT_CMD)]
    (if (.exists (fs/file local)) local DEF_TYPESCRIPT_CMD)))

(defn- typescript-params [conf]
  (concat
   (param-watch conf)
   (param-out conf)
   (param-out-dir conf)
   (param-module conf)
   (param-target conf)
   (param-source-map conf)
   (param-declaration conf)
   (param-remove-comments conf)
   (param-preserve-const-enums conf)
   (param-suppress-implicit-any-index-errors conf)
   (source-list conf)))

(defn- compile-typescript [project conf]
  (let [root (:root project)
        cmd (typescript-cmd)
        params (typescript-params conf)
        args (concat [cmd] params)]
    (debug-log conf args)
    (process/exec root args)))

(defn- process-config [project conf]
  (try
    (do
      (npm/environmental-consistency project)
      (compile-typescript project conf))
    (catch Throwable t
      (if (conf-debug conf)
        (.printStackTrace t))
      (error t)
      (main/abort))))


; External API: Runner

(defn typescript [project & args]
  (let [confs (configs project)]
    (doseq [conf confs]
      (process-config project conf))))
