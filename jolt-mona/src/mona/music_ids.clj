(ns mona.music-ids
  "Super Mona's sound ids and their lookups, split from mona.music so the Wii
   build takes the ids without the note data, parser and emitter (the boot
   heap has no room for them).")

(def effect-ids
  "The rule events that have a sound effect, in the order the C tables use.
   :die has an effect entry that is silent, because the die jingle is music."
  [:jump :stomp :kick :bump :break :die :defeat :flag :pickup :item :grow
   :shrink :1up :sparkle :stored :switch])

(def song-ids
  "Every song id, in the order the C tables use."
  [:title :day :dusk :cloud :cave :castle :clear :die :over :win])

(def ^:private effect-index
  (zipmap effect-ids (range)))

(def ^:private song-index
  (zipmap song-ids (range)))

(defn effect-id
  "The C index of effect keyword k, or nil when k is not an effect id."
  [k]
  (get effect-index k))

(defn song-id
  "The C index of song keyword k, or nil when k is not a song id."
  [k]
  (get song-index k))
