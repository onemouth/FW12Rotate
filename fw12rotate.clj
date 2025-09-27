#!/usr/bin/env bb

(require '[babashka.process :as p]
         '[babashka.fs :as fs]
         '[clojure.string :as str])

;; Configuration specific to the Framework 12
(def monitor-name "eDP-1")
(def resolution "1920x1200@60")
(def scale "1.2") ; particular scaling preference

;; Orientation mappings
(def orientations
  {:normal 0
   :right-up 3
   :left-up 1
   :bottom-up 2})

(def orientation-names
  {0 :normal
   3 :right-up
   1 :left-up
   2 :bottom-up})

;; Function to execute hyprctl transform commands
(defn set-orientation [transform]
  (let [monitor-cmd (str "hyprctl keyword monitor \"" monitor-name "," 
                        resolution ",0x0," scale ",transform," transform "\"")
        touch-cmd (str "hyprctl keyword input:touchdevice:transform " transform)
        tablet-cmd (str "hyprctl keyword input:tablet:transform " transform)]
    (println "Applying orientation: transform=" transform "(" (orientation-names transform) ")")
    (p/shell monitor-cmd)
    (p/shell touch-cmd)
    (p/shell tablet-cmd)))

;; Function to get current orientation from hyprctl
(defn get-current-hypr-orientation []
  (try
    (let [result (p/shell {:out :string} "hyprctl monitors")
          output (:out result)]
      (when output
        (let [lines (str/split-lines output)
              monitor-line (first (filter #(str/includes? % monitor-name) lines))]
          (when monitor-line
            (cond
              (str/includes? monitor-line "transform,0") 0
              (str/includes? monitor-line "transform,1") 1
              (str/includes? monitor-line "transform,2") 2
              (str/includes? monitor-line "transform,3") 3
              :else 0)))))
    (catch Exception e
      (println "Failed to query current hyprctl orientation:" (.getMessage e))
      0)))

;; Function to get current orientation from iio-sensor-proxy via D-Bus
(defn get-current-orientation []
  (try
    (let [result (p/shell {:out :string} 
                         "dbus-send --system --print-reply --dest=net.hadess.SensorProxy /net/hadess/SensorProxy org.freedesktop.DBus.Properties.Get string:\"net.hadess.SensorProxy\" string:\"AccelerometerOrientation\"")
          output (:out result)]
      (when output
        (let [variant-pos (str/index-of output "variant")]
          (when variant-pos
            (let [quote-pos (str/index-of output "\"" variant-pos)]
              (when quote-pos
                (let [end-quote-pos (str/index-of output "\"" (inc quote-pos))]
                  (when end-quote-pos
                    (subs output (inc quote-pos) end-quote-pos)))))))))
    (catch Exception e
      (println "Failed to query current orientation:" (.getMessage e))
      "")))

;; Function to cycle to next orientation
(defn cycle-orientation []
  (let [current-transform (get-current-hypr-orientation)
        transforms [0 1 2 3] ; normal, left-up, bottom-up, right-up
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
  (let [current-transform (get-current-hypr-orientation)
        current-name (orientation-names current-transform)
        sensor-orientation (get-current-orientation)]
    (println "Current display orientation:" current-name "(transform:" current-transform ")")
    (when (not (str/blank? sensor-orientation))
      (println "Current sensor orientation:" sensor-orientation))))

;; Main function
(defn -main [& args]
  (let [command (first args)]
    (case command
      "cycle" (cycle-orientation)
      "normal" (set-specific-orientation "normal")
      "left" (set-specific-orientation "left-up")
      "right" (set-specific-orientation "right-up")
      "bottom" (set-specific-orientation "bottom-up")
      "reset" (reset-to-normal)
      "status" (show-current-orientation)
      "sensor" (println "Current sensor orientation:" (get-current-orientation))
      ;; Default: cycle orientation
      (do
        (if (empty? args)
          (cycle-orientation)
          (do
            (println "Usage: fw12rotate.bb [command]")
            (println "Commands:")
            (println "  cycle    - Cycle to next orientation (default)")
            (println "  normal   - Set to normal orientation")
            (println "  left     - Set to left-up orientation")
            (println "  right    - Set to right-up orientation")
            (println "  bottom   - Set to bottom-up orientation")
            (println "  reset    - Reset to normal orientation")
            (println "  status   - Show current orientation")
            (println "  sensor   - Show current sensor orientation")))))))

;; Run the main function
(-main *command-line-args*)
