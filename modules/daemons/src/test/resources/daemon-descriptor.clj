{
 ;; this will probably fail on windows
 :root "/tmp"
 :app-function "the-app-function"
 :daemons {"daemon-one" {"start-function" "foo.bar"
                         "stop-function" "bar.foo"}
           "daemon-two" {"start-function" "foo.bar.baz"}}
 }
