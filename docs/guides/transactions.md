---
{:title "Transactions"
 :sequence 4.5
 :description "Providing support for distributed (XA) transactions"}
---

Immutant encapsulates the distributed transaction support provided
by [Narayana] within the [immutant.transactions] namespace.

A *distributed* transaction is one in which multiple types of
resources may participate. The most common example of a transactional
resource is a relational database, but in Immutant both caching
(backed by [Infinispan]) and messaging (backed by [HornetQ]) resources
are transactional. Technically speaking, they provide implementations
of the [XA protocol], and any resource that does may participate in an
Immutant transaction.

This allows your application to say, tie the success of a SQL
database update or the storage of an entry on a replicated data grid
to the delivery of a message to a remote queue, i.e. the message is
only sent if the database and data grid update successfully. If any
single component of an XA transaction fails, all of them rollback.

## Transaction Scope

When transactional components interact, the state of a transaction
when a particular function is invoked isn't always predictable. For
example, can a function that requires a transaction assume one has
been started prior to its invocation? In JEE container-managed
persistence, a developer answers these questions using the
`@TransactionAttribute` annotation.

But annotations are gross, man!

So in Immutant, [JEE transaction attributes] are represented as
Clojure macros. In fact, the `immutant.transactions/transaction` macro
is merely an alias for `immutant.transactions.scope/required`, which
is the implicit attribute used in JEE. There are a total of 6 macros:

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

[immutant.transactions]: immutant.transactions.html
[Narayana]: http://www.jboss.org/narayana
[Infinispan]: http://infinispan.org
[HornetQ]: http://www.jboss.org/hornetq
[XA protocol]: http://en.wikipedia.org/wiki/X/Open_XA
[JEE transaction attributes]: http://docs.oracle.com/javaee/7/tutorial/doc/transactions003.htm
