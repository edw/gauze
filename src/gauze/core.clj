(ns gauze.core
  [:import
   com.mchange.v2.c3p0.DataSources
   org.postgresql.ds.PGSimpleDataSource])

;;;
;;; Datasource functions
;;;

(defn datasource-info
  "Returns a datasource info map for a URL string."
  [s]
  (let [uri (java.net.URI. s)
        [user password] (clojure.string/split (.getUserInfo uri) #":")
        server (.getHost uri)
        port (.getPort uri)
        database (-> (.getPath uri) (clojure.string/split #"/") (last))]
    {:user user :password password :server server :port port
     :database database :ssl true
     :sslfactory "org.postgresql.ssl.NonValidatingFactory"}))

(defn datasource
  "Returns a datasource given a datasource info map."
  [info]
  (DataSources/pooledDataSource
   (doto (PGSimpleDataSource.)
     (.setServerName (:server info))
     (.setPortNumber (:port info))
     (.setUser (:user info))
     (.setDatabaseName (:database info))
     (.setPassword (:password info))
     (.setSsl (:ssl info))
     (.setSslfactory (:sslfactory info)))))

(def ^:dynamic *current-datasource* nil)

(defmacro with-datasource
  "Performs BODY with current datasource set to DS."
  [DS & BODY]
  `(binding [*current-datasource* ~DS] ~@BODY))

;;;
;;; Functions that do useful things like querying and updating.
;;;

(defn- result-column-names [rs]
  (let [meta (.getMetaData rs)]
    (for [i (range 1 (inc (.getColumnCount meta)))]
      [i (.getColumnName meta i)])))

(defn- set-statement-arguments! [stmt args]
  (dorun (map (fn [i arg] (.setObject stmt i arg))
              (range 1 (inc (count args)))
              args))
  stmt)

(defn- parse-object
  "Logic for jdbc interop"
  [object]
  (cond 
   (isJDBC4Array? object) (JDBC4Array-to-vector object)
   :else object))

(defn query
  "Returns a sequence of rows for a query given zero or more positional
  arguments. Query can be either a query string or a vector pair
  containing a datasource and a query string"
  [q & args]
  (let [[ds query] (if (vector? q) q [*current-datasource* q])]
    (with-open [conn (.getConnection ds)]
      (let [stmt (.prepareStatement conn query)
            rs (.executeQuery (set-statement-arguments! stmt args))
            column-names (result-column-names rs)
            current-row
            (fn []
              (into {}
                    (map (fn [[i name]] [(keyword name) (.getObject rs i)])
                         column-names)))]
        (loop [row-available? (.next rs) rows []]
          (if row-available?
            (let [row (current-row)]
              (recur (.next rs) (conj rows row)))
            rows))))))

(defn- pick
  "Returns a sequence of values for a list of keys into a collection,
  which can either be atoms or vectors, vectors being used as
  composite keys passed to get-in."
  [coll ks]
  (for [key ks]
    (if (vector? key)
      (get-in coll key)
      (get coll key))))

(defn update
  "Returns the number of rows updated, inserted, or deleted given zero or more
   positional arguments. Statement can either be a string or a vector pair
   containing a datasource and a statement."
  [s & args]
  (let [[ds query] (if (vector? s) s [*current-datasource* s])]
    (with-open [conn (.getConnection ds)]
      (let [stmt (.prepareStatement conn query)]
        (.executeUpdate (set-statement-arguments! stmt args))))))

(defn keyed-query
  "Like query, but takes a map from which keys are picked."
  [q map & ks]
  (apply query q (pick map ks)))

(defn keyed-update
  "Like update, but takes a map from which keys are picked."
  [s map & ks]
  (apply update s (pick map ks)))
