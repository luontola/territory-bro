(ns territory-bro.ui.assignment
  (:require [clojure.string :as str]
            [territory-bro.infra.util :as util]
            [territory-bro.ui.hiccup :as h]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.i18n :as i18n]))

(defn format-publisher-name [publisher]
  (or (:publisher/name publisher)
      (str "[" (i18n/t "Assignment.deletedPublisher") "]")))

(defn format-months-ago-with-date [date today]
  (h/html (-> (i18n/t "Assignment.durationMonthsAgo")
              (str/replace "{{months}}" (str (util/months-difference date today))))
          " ("
          (html/nowrap date)
          ")"))

(defn format-months-since-date [date today]
  (h/html "("
          (-> (i18n/t "Assignment.durationMonthsSinceDate")
              (str/replace "{{months}}" (str (util/months-difference date today)))
              (str/replace "{{date}}" (str (html/nowrap date)))
              (h/raw))
          ")"))

(defn format-status-vacant []
  (-> (i18n/t "Assignment.statusVacant")
      (str/replace "<0>" "<span style='color:blue'>")
      (str/replace "</0>" "</span>")
      (h/raw)))

(defn format-status-assigned [assignment]
  (-> (i18n/t "Assignment.statusAssignedToPublisher")
      (str/replace "<0>" "<span style='color:red'>")
      (str/replace "</0>" "</span>")
      (str/replace "{{name}}" (str (h/html (format-publisher-name assignment))))
      (h/raw)))

(defn format-status-assigned-duration [assignment today]
  (-> (i18n/t "Assignment.statusAssignedToPublisherForMonths")
      (str/replace "<0>" "<span style='color:red'>")
      (str/replace "</0>" "</span>")
      (str/replace "{{name}}" (str (h/html (format-publisher-name assignment))))
      (str/replace "{{months}}" (str (util/months-difference (:assignment/start-date assignment) today)))
      (h/raw)))
