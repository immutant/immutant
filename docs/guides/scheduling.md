---
{:title "Scheduling"
 :sequence 3
 :base-ns 'immutant.scheduling
 :description "Schedule asynchronous jobs"}
---

If you're coming from Immutant 1.x, you'll notice that the scheduling
namespace and artifact have been renamed (what used to be
`immutant.jobs` and `org.immutant/immutant-jobs` is now
`immutant.scheduling` and `org.immutant/scheduling`), and the API has
changed a bit.  It's still based on Quartz 2.2, though.

## The API

At first glance, the API for [[immutant.scheduling]] appears bigger than
it really is, but there are only two essential functions:

* [[schedule]] - for scheduling your jobs
* [[stop]] - for canceling them

The remainder of the namespace is syntactic sugar: functions that can
be composed to create the specification for when your job should run.

Your "job" will take the form of a plain ol' Clojure function taking
no arguments. The `schedule` function takes your job and a
*specification map* as arguments. The map determines when your
function gets called. It may contain any of the following keys:

* `:in` - a period after which your function will be called
* `:at` - an instant in time after which your function will be called
* `:every` - the period between calls
* `:until` - stops the calls at a specific time
* `:limit` - limits the calls to a specific count
* `:cron` - calls your function according to a [Quartz-style] cron spec

For each key there is a corresponding "sugar function". We'll see
those in the examples below.

Units for periods (`:in` and `:every`) are milliseconds, but can also
be represented as a keyword or a vector of multiplier/keyword pairs,
e.g. `[1 :week, 4 :days, 2 :hours, 30 :minutes, 59 :seconds]`. Both
singular and plural keywords are valid.

Time values (`:at` and `:until`) can be a `java.util.Date`, a long
representing milliseconds-since-epoch, or a String in `HH:mm` format.
The latter will be interpreted as the next occurence of `HH:mm:00` in
the currently active timezone.

Two additional options may be passed in the spec map:

* `:id` - a unique identifier for the scheduled job
* `:singleton` - a boolean denoting the job's behavior in a cluster

In Immutant 1.x, a name for the job was required. In Immutant 2, the
`:id` is optional, and if not provided, a UUID will be generated. If
`schedule` is called with an `:id` for a job that has already been
scheduled, the prior job will be replaced.

The return value from `schedule` is a map of the options with any
missing defaults filled in, including a generated id if necessary.
This result can be passed to `stop` to cancel the job.

### Some Examples

If you haven't already, you should read through the [installation]
guide and require the `immutant.scheduling` namespace at a REPL to
follow along:

```clojure
(require '[immutant.scheduling :refer :all])
```

We'll need a job to schedule. Here's one!

```clojure
(defn job []
  (prn 'fire!))
```

Let's schedule it:

```clojure
(schedule job)
```

That was pretty useless. Without a spec, the job will immediately be
called once asynchronously on one of the Quartz scheduler's threads.
Instead, let's have it run in 5 minutes:

```clojure
(schedule job (in 5 :minutes))
```

And maybe run again every second after that:

```clojure
(schedule job
  (-> (in 5 :minutes)
    (every :second)))
```

But no more than 60 times:

```clojure
(schedule job
  (-> (in 5 :minutes)
    (every :second)
    (limit 60)))
```

We could also anticipate getting stupid bored about halfway through,
and schedule another job to cancel the first one:

```clojure
(let [it (schedule job
           (-> (in 5 :minutes)
             (every :second)
             (limit 60)))]
  (schedule #(stop it) (in 5 :minutes, 30 :seconds)))
```

Of course, you can bring your own job id's if you like:

```clojure
(schedule job (-> (id :purge) (every 30 :minutes)))
(schedule job (-> (id :purge) (every :hour)))  ; reschedule
(stop (id :purge))
```

If a job is successfully canceled, `stop` returns true.

### It's Just Maps

Ultimately, the spec passed to `schedule` is just a map, and the sugar
functions are just assoc'ing keys corresponding to their names. The
map can be passed either explicitly or via keyword arguments, so all
of the following are equivalent:

```clojure
(schedule job (-> (in 5 :minutes) (every :day)))
(schedule job {:in [5 :minutes], :every :day})
(schedule job :in [5 :minutes], :every :day)
```

### Supports Joda clj-time

If you're using the [clj-time] library in your project, you can load
the [[immutant.scheduling.joda]] namespace. This will extend
`org.joda.time.DateTime` instances to the
[[immutant.coercions/AsTime]] protocol, enabling them to be used as
arguments to `at` and `until`, e.g.

```clojure
(require '[clj-time.core :refer [today-at plus hours]])

(let [t (today-at 9 00)]
  (schedule job
    (-> (at t)
      (every 2 :hours)
      (until (plus t (hours 8))))))
```

The `immutant.scheduling.joda` namespace also provides
[[immutant.scheduling.joda/schedule-seq]].  Inspired by [chime-at], it
takes not a specification map but a sequence of times, as might be
returned from `clj-time.periodic/periodic-seq`, subject to the
application of any of Clojure's core sequence-manipulating functions.

When defining complex recurring schedules, this presents an
interesting alternative to traditional cron specs. For example,
consider a job that must run at 10am every weekday. Here's how we'd
schedule that with a [Quartz-style] cron spec:

```clojure
(schedule job (cron "0 0 10 ? * MON-FRI"))
```

And here's the same schedule using a lazy sequence:

```clojure
(require '[immutant.scheduling.joda :refer [schedule-seq]]
         '[clj-time.core            :refer [today-at days]]
         '[clj-time.periodic        :refer [periodic-seq]]
         '[clj-time.predicates      :refer [weekday?]])

(schedule-seq job
  (->> (periodic-seq (today-at 10 0) (days 1))
    (filter weekday?)))
```

So each has trade-offs, of course. The cron spec is more concise, but
also arguably more error-prone, e.g. what is that `?` for!?

One very cool thing about the sequence is that I can test it without
actually scheduling it. On the other hand, my cron spec test is going
to take more than a week to run! ;)

## Accessing the internal Quartz scheduler

If you need access to the internal Quartz scheduler instance (maybe to
pass to another Quartz-based scheduling library like [Quartzite]), use
[[immutant.scheduling.quartz/quartz-scheduler]]. You just need to pass
it the same scheduler options you can pass to
[[immutant.scheduling/schedule]].

[Quartz-style]: http://quartz-scheduler.org/documentation/quartz-2.2.x/tutorials/tutorial-lesson-06
[installation]: guide-installation.html
[clj-time]: https://github.com/clj-time/clj-time
[chime-at]: https://github.com/james-henderson/chime#chime-at
[Quartzite]: http://clojurequartz.info/
