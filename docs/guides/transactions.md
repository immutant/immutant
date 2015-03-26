---
{:title "Transactions"
 :sequence 4.5
 :base-ns 'immutant.transactions
 :description "Providing support for distributed (XA) transactions"}
---

Immutant encapsulates the distributed transaction support provided by
[Narayana] within the [[immutant.transactions]] namespace. This allows
you to define XA (2PC) transactions involving Immutant components
either inside the WildFly application server or embedded within your
standalone apps.

A *distributed* transaction is one in which multiple types of
resources may participate. The most common example of a transactional
resource is a relational database, but in Immutant both caching
(backed by [Infinispan]) and messaging (backed by [HornetQ]) resources
are transactional. Technically speaking, they provide implementations
of the [XA protocol], and any resource that does may participate in an
Immutant transaction.

This allows your application to say, tie the success of a SQL
database update or the storage of an entry on a replicated cache
to the delivery of a message to a remote queue, i.e. the message is
only sent if the database and cache update successfully. If any
single component of an XA transaction fails, all of them rollback.

## Defining a transaction

If you're familiar with [JTA], we make a `TransactionManager`
available via the [[manager]] var, and everything else provided by the
library is mostly a syntactic sugary glaze around that instance. For
example, the [[transaction]] macro will query the manager to see if a
transaction is active. If so, it'll simply invoke its body, relying on
the transactional components within to automatically enlist themselves
as XA resources. Otherwise, it'll start a new transaction, execute its
body, and commit the transaction unless an exception is caught, in
which case the transaction is rolled back. For example,

```clojure
(def queue (msg/queue "/queue/test"))
(def cache (csh/cache "test" :transactional true))

(transaction
  (msg/publish queue "message")
  (csh/swap-in! cache :count (fnil inc 0)))
```

Here, we've combined a messaging call and a caching call into a
single, atomic operation. Either both succeed or neither succeeds.
Note that the cache had to be created with its `:transactional` flag
set. The publish function will detect an active transaction and create
an XA-capable context with which to send the message. If you pass a
context for publish to use, be sure its `:xa` flag is set if you
intend to publish within a transaction.

## Transaction Scope

When transactional components interact, the state of a transaction
when a particular function is invoked isn't always predictable. For
example, can a function that requires a transaction assume one has
been started prior to its invocation? In JEE container-managed
persistence, a developer answers these questions using the
`@TransactionAttribute` annotation.

But annotations are gross, my friend!

So in Immutant, [JEE transaction attributes] are represented as
Clojure macros. In fact, the [[transaction]]
macro is merely an alias for [[immutant.transactions.scope/required]],
which is the implicit attribute used in JEE. There are a total of 6
macros in the [[immutant.transactions.scope]] namespace:

* `required`- Execute within current transaction, if any, otherwise
  start a new one, execute, commit or rollback
* `requires-new`- Suspend current transaction, if any, start a new
  one, execute, commit or rollback, and resume the suspended one
* `not-supported` - Suspend current transaction, if any, and execute
  without a transaction
* `supports`- Execute the body whether there's a transaction or not;
  may lead to unpredictable results
* `mandatory` - Toss an exception if there's no active transaction
* `never` - Toss an exception if there is an active transaction

These macros give the developer complete declarative control over
the transactional semantics of their application as its functional
chunks are combined.

## Pseudo-Transactions

While messaging and caching resources in Immutant are XA capable, not
all data sources are, and even a JDBC library providing an XA driver
is insufficient outside of an application server.

But non-XA resources like Datomic or JDBC connections running outside
of a container can still participate in a distributed transaction as
long as their local transaction is the **last** operation in the body
defining the XA transaction. This technique, known as a
[pseudo transaction], relies on the non-XA resource throwing an
exception in the event of failure. This exception will cause the
operations on the XA resources to rollback. For example,

```clojure
(immutant.transactions/transaction
  (immutant.messaging/publish queue message)
  (immutant.caching/swap-in! cache :count (fnil inc 0))
  (clojure.java.jdbc/with-db-transaction [t spec]
    (write-sql-things t data)))
```

Note that we define a local transaction using `with-db-transaction`
assuming `write-sql-things` invokes multiple SQL statements, so that
they all rollback if any throw an exception. If you're only calling a
single SQL statement, e.g. one INSERT, the local transaction isn't
necessary.

## JDBC DataSources in WildFly

Within WildFly, it's possible to use real, honest-to-goodness JDBC XA
datasources, thereby obviating the need for pseudo transactions.

Configuring datasources in WildFly is
[beyond the scope of this guide](https://docs.jboss.org/author/display/WFLY8/DataSource+configuration),
but once you have the JNDI name for the XA datasource, you can pass
that as the `:name` option in the "database spec" required by
[java.jdbc] or any of its many DSL wrappers.

Unfortunately, `java.jdbc` attempts to invoke operations on the
connection returned from the datasource bound to that name that are
illegal within an XA transaction, specifically: `commit`, `rollback`,
and `setAutoCommit`. It's up to the `TransactionManager` to
commit/rollback a distributed transaction, not its constituents. So
Immutant provides a factory function,
[[immutant.transactions.jdbc/factory]], that wraps the connection to
turn those calls into no-ops. This means the database spec you pass to
[java.jdbc] should look something like this:

```clojure
(def db-spec {:factory immutant.transactions.jdbc/factory
              :name "java:jboss/datasources/ExampleDS"})
```

[immutant.transactions]: immutant.transactions.html
[Narayana]: http://www.jboss.org/narayana
[Infinispan]: http://infinispan.org
[HornetQ]: http://www.jboss.org/hornetq
[XA protocol]: http://en.wikipedia.org/wiki/X/Open_XA
[JEE transaction attributes]: https://docs.oracle.com/javaee/7/tutorial/transactions003.htm
[JTA]: http://www.oracle.com/technetwork/java/javaee/jta/index.html
[pseudo transaction]: http://www.theserverside.com/news/1363664/Java-Pseudo-Transactions-With-Non-Transactional-Resources
[java.jdbc]: https://github.com/clojure/java.jdbc
