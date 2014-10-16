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

[immutant.transactions]: immutant.transactions.html
[Narayana]: http://www.jboss.org/narayana
[Infinispan]: http://infinispan.org
[HornetQ]: http://www.jboss.org/hornetq
[XA protocol]: http://en.wikipedia.org/wiki/X/Open_XA
