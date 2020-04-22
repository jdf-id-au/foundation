(ns foundation.client.connection
  (:import (goog.net WebSocket)
           (goog.net.WebSocket EventType)) ; != (goog.net EventType)
  (:require [goog.events :refer [listen]]
            ))