{
 ;; this will probably fail on windows
 :root "/tmp"
 :app-function "the-app-function"
 :queues {"/queue/one" {:durable true}
          "/queue/two" {:durable false}
          "/queue/three" {}}
 }
