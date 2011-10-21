(defn foo [msg]
  (println "foo" msg))

{
 ;; this will probably fail on windows
 :root "/tmp"
 :app-function "the-app-function"
 :queues {"/queue/one" {"durable" true}
          "/queue/two" {"durable" false}
          "/queue/three" {}}
 :processors [ [ "/queue/one"
                 (fn [msg] (println "received" msg))
                 {"concurrency" 2, "filter" "height >"} ]
               [ "/queue/two" foo {} ] ]
 
 }
