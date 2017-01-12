;; Copied and modified from potemkin, v0.4.3 (https://github.com/ztellman/potemkin), MIT licnensed, Copyright Zachary Tellman
;; Changes:
;; - fast-memoize removed

(ns from.potemkin
  (:require
    [from.potemkin namespaces types collections macros utils]))

(from.potemkin.namespaces/import-vars from.potemkin.namespaces/import-vars) ;; totally meta

(import-vars
  [from.potemkin.namespaces

   import-fn
   import-macro
   import-def]

  [from.potemkin.macros

   unify-gensyms
   normalize-gensyms
   equivalent?]

  [from.potemkin.utils

   condp-case
   try*
   fast-bound-fn
   fast-bound-fn*
   doit
   doary]

  [from.potemkin.types

   def-abstract-type
   reify+
   defprotocol+
   deftype+
   defrecord+
   definterface+
   extend-protocol+]

  [from.potemkin.collections

   reify-map-type
   def-derived-map
   def-map-type])
