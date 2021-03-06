(ns reitit.perf-utils
  (:require [criterium.core :as cc]
            [clojure.string :as str]
            [reitit.core :as reitit]))

(defn raw-title [color s]
  (println (str color (apply str (repeat (count s) "#")) "\u001B[0m"))
  (println (str color s "\u001B[0m"))
  (println (str color (apply str (repeat (count s) "#")) "\u001B[0m")))

(def title (partial raw-title "\u001B[35m"))
(def suite (partial raw-title "\u001B[32m"))

(defmacro bench! [name & body]
  `(do
     (title ~name)
     (println ~@body)
     (cc/quick-bench ~@body)))

(defn valid-urls [router]
  (->>
    (for [name (reitit/route-names router)
          :let [match (reitit/match-by-name router name)
                params (if (reitit/partial-match? match)
                         (-> match :required (zipmap (range))))]]
      (:path (reitit/match-by-name router name params)))
    (into [])))

(defrecord Request [uri path-info request-method])

(defn bench-routes [routes req f]
  (let [router (reitit/router routes)
        urls (valid-urls router)]
    (mapv
      (fn [path]
        (let [request (map->Request (req path))
              time (int (* (first (:sample-mean (cc/quick-benchmark (dotimes [_ 1000] (f request)) {}))) 1e6))]
          (println path "=>" time "ns")
          [path time]))
      urls)))

(defn bench [routes req no-paths?]
  (let [routes (mapv (fn [[path name]]
                       (if no-paths?
                         [(str/replace path #"\:" "") name]
                         [path name])) routes)
        router (reitit/router routes)]
    (doseq [[path time] (bench-routes routes req #(reitit/match-by-path router %))]
      (println path "\t" time))))

;;
;; Perf tests
;;

(def handler (constantly {:status 200, :body "ok"}))

(defn bench!! [routes req verbose? name f]
  (println)
  (suite name)
  (println)
  (let [times (for [[path time] (bench-routes routes req f)]
                (do
                  (when verbose? (println (format "%7s" time) "\t" path))
                  time))]
    (title (str "average: " (int (/ (reduce + times) (count times)))))))
