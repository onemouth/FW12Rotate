#!/usr/bin/env bb

(require '[babashka.process :as p]
         '[babashka.fs :as fs]
         '[clojure.string :as str])

;; Configuration specific to the Framework 12
(def monitor-name "eDP-1")
(def resolution "1920x1200@60")
(def scale "1.2") ; particular scaling preference
(def state-file (str (System/getProperty "user.home") "/.config/hypr/rotation-state"))

;; Orientation mappings
(def orientations
  {:normal 0
   :bottom-up 2})

(def orientation-names
  {0 :normal
   2 :bottom-up})

;; Function to save current orientation to state file
(defn save-orientation [transform]
  (try
    (fs/create-dirs (fs/parent state-file))
    (spit state-file (str transform))
    (catch Exception e
      (println "Failed to save orientation state:" (.getMessage e)))))

;; Function to read current orientation from state file
(defn get-current-orientation []
  (try
    (if (fs/exists? state-file)
      (let [content (str/trim (slurp state-file))]
        (if (str/blank? content)
          0
          (Integer/parseInt content)))
      0)
    (catch Exception e
      (println "Failed to read orientation state:" (.getMessage e))
      0)))

;; Function to execute hyprctl transform commands
(defn set-orientation [transform]
  (let [monitor-cmd (str "hyprctl keyword monitor \"" monitor-name "," 
                        resolution ",0x0," scale ",transform," transform "\"")
        touch-cmd (str "hyprctl keyword input:touchdevice:transform " transform)
        tablet-cmd (str "hyprctl keyword input:tablet:transform " transform)]
    (println "Applying orientation: transform=" transform "(" (orientation-names transform) ")")
    (p/shell monitor-cmd)
    (p/shell touch-cmd)
    (p/shell tablet-cmd)
    (save-orientation transform)))


;; Function to cycle to next orientation
(defn cycle-orientation []
  (let [current-transform (get-current-orientation)
        transforms [0 2] ; normal, bottom-up
        current-index (.indexOf transforms current-transform)
        next-index (mod (inc current-index) (count transforms))
        next-transform (nth transforms next-index)]
    (set-orientation next-transform)))

;; Function to set specific orientation
(defn set-specific-orientation [orientation-name]
  (when-let [transform (orientations (keyword orientation-name))]
    (set-orientation transform)))

;; Function to reset to normal
(defn reset-to-normal []
  (set-orientation 0))

;; Function to show current orientation
(defn show-current-orientation []
  (let [current-transform (get-current-orientation)
        current-name (orientation-names current-transform)]
    (println "Current display orientation:" current-name "(transform:" current-transform ")")))

;; Main function
(defn -main [& args]
  (let [command (first args)]
    (case command
      "cycle" (cycle-orientation)
      "normal" (set-specific-orientation "normal")
      "bottom" (set-specific-orientation "bottom-up")
      "reset" (reset-to-normal)
      "status" (show-current-orientation)
      ;; Default: cycle orientation
      (do
        (if (empty? args)
          (cycle-orientation)
          (do
            (println "Usage: fw12rotate.bb [command]")
            (println "Commands:")
            (println "  cycle    - Cycle to next orientation (default)")
            (println "  normal   - Set to normal orientation")
            (println "  bottom   - Set to bottom-up orientation")
            (println "  reset    - Reset to normal orientation")
            (println "  status   - Show current orientation")
))))))

;; Run the main function
(apply -main *command-line-args*)