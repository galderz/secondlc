Pre-requisites
==============

* Increase font size of text and console!
* Set up ad-hoc network!
* Delete root benchmark-* files

Demo 1
======

* Compare performance of loading a single entity, representing a family
* FindWith2lcBenchmark runs two benchmarks, one w/ caching and the other wo/
* Do not pay attention to caching settings, we'll go over them in the presentation
* To compare performance, use JUnit Benchmarks library (extension of JUnit)
* Hibernate configured to persist H2 in-memory database
* Before benchmarking, 4 instances of Family entity stored in database
* Go over the test explainig the second level cache statistic expectations

1. Run FindWith2lcBenchmark with system properties:
`-Djub.consumers=CONSOLE,H2 -Djub.db.file=.benchmarks`

2. Verify that numbers in console make sense (2lc should take less time than no2lc).

3. Open `benchmark-find.html` with browser and compare performance.

Demo 2
======

* The first demo focused on entity caching, this second demo shows query cache in action
* Enabling query caching is a two-step process:
* Query cache is enabled at the Hibernate SessionFactory level (or Persistence Unit for JPA)
* Mark individual queries as cacheable!! <-- crucial!
* Go over the test explainig the second level cache statistic expectations

1. Run QueryWith2lcBenchmark with system properties:
`-Djub.consumers=CONSOLE,H2 -Djub.db.file=.benchmarks`

2. Verify that numbers in console make sense (2lc should take less time than no2lc).

3. Open `benchmark-query.html` with browser and compare performance.

Demo 3
======

* Demostrate the clustering capabilities just explained
* It uses a file based H2 database, where all nodes in the cluster are
pointing to same database.
* Inspect 'infinispan-cluster.xml' which is where the differences are with other
* Definition of a JGroups transport, which uses TCP to communicate between nodes
* Entity cache has invalidation set up
* Query cache is local, and timestamps replicated
* Alternative cache configurations for entities and query present too, where
entities/collections use replication and queries are clustered.
* If you want to experiment, configurations are there.
* Instances stored in first node
* Verify that when entries loaded from second node, first a 2lc put happens,
and second time loaded, it's a cache hit
* Go over to the first node and update the entity, that should cause an
invalidation message to be sent to the other node.
* If invalidation worked correctly, when loading the entity again in the
second session factory, a cache miss should happen, followed by a put, with
the correct updated data.

1. Run Cluster2lcTest.testClusteredEntityCache with system properties:
`-Djava.net.preferIPv4Stack=true -Djgroups.bind_addr=127.0.0.1`

* Clustered query test verifies that queries, via the update timestamps cache,
are invalidated when entities are updated.
* When the query is first executed in second node, a cache miss + put happens.
* Second time the query is executed in second node, a cache hit happens.
* Go to first node and update the instance, which should trigger an update
timestamps cache update.
* When queried again in 2nd node, the updates are present, and the query
result in a cache miss + put (not hit!).

2. Run Cluster2lcTest.testClusteredQuery with system properties:
`-Djava.net.preferIPv4Stack=true -Djgroups.bind_addr=127.0.0.1`

Demo 4
======

* Use arquillian to demonstrate JPA second level caching in JBoss AS 7.1.1
* Creates a war with an entity and a corresponding persistence.xml
* persistence.xml has `ENABLE_SELECTIVE` as cache mode to only cache entities with @Cacheable
* persistence.xml has second level cache and query cache enabled
* The test starts inserting 4 entity instances
* A query for all the entities inserted is executed and checks the second level cache statistics
* Notice a slight difference in how the query is marked cacheable, this is done via a hint

1. Run JpaManagedWith2lcTest