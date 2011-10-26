{
 ;; this will probably fail on windows
 :root "/tmp"
 :init "foo.bar/baz"
 :app-function "the-app-function"
 :daemons {"daemon-one" {"start" "foo.bar"
                         "stop" "bar.foo"}
           "daemon-two" {"start" "foo.bar.baz"}}
 }
