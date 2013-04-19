Pre-requisites
==============

* Increase font size of text and console!
* Set up ad-hoc network!
* Delete root benchmark-* files

Demo 1
======

1. Compare performance of loading a single entity, representing a family.
Show Family entity has @javax.persistence.Cacheable annotation.

2. FindWith2lcBenchmark runs two benchmarks, one w/ caching and the other wo/

3. Do not pay attention to caching settings, we'll go over them in the presentation

4. To compare performance, use JUnit Benchmarks library (extension of JUnit)

5. Hibernate configured to persist H2 in-memory database

6 Before benchmarking, 4 instances of Family entity stored in database

7. Go over the test explainig the second level cache statistic expectations

8. Run FindWith2lcBenchmark with system properties:
`-Djub.consumers=CONSOLE,H2 -Djub.db.file=.benchmarks`

9. Verify that numbers in console make sense (2lc should take less time than no2lc).

10. Open `benchmark-find.html` with browser and compare performance.

11. Explain in test how enable second level cache is set to `true`

12. Standalone Hibernate does not mandate a particular cache provider, so,
select a provider of second level cache defining the cache region factory

13. Provide a configuration for the second level cache provider

14. Today’s presentation focused on Infinispan, which requires a
JTA environment.


Demo 2
======

1. Family programmatically defined to be READ_ONLY. READ_ONLY means that
inserts, find and delete operations are allowed, no updates!

2. Using prog method here for flexibility: later demo will use a different
strategy Prog configuration allows definition based on application (vs
changing entity source code)

3. The first demo focused on entity caching, this second demo shows query
cache in action

4. Enabling query caching is a two-step process:

5. Query cache is enabled at the Hibernate SessionFactory level (or
Persistence Unit for JPA).

6. Mark individual queries as cacheable!! <-- crucial!

7. Go over the test explainig the second level cache statistic expectations

8. Run QueryWith2lcBenchmark with system properties:
`-Djub.consumers=CONSOLE,H2 -Djub.db.file=.benchmarks`

9. Verify that numbers in console make sense (2lc should take less time than no2lc).

10. Open `benchmark-query.html` with browser and compare performance.

Demo 3
======

1. Demostrate the clustering capabilities just explained

2. It uses a file based H2 database, where all nodes in the cluster are
pointing to same database.

3. Configured with TRANSACTIONAL cache concurrency strategy, because we're
going to update entities

4. Inspect 'infinispan-cluster.xml' which is where the differences are with other

5. Definition of a JGroups transport, which uses TCP to communicate between nodes

6. Entity cache has invalidation set up

7. Query cache is local, and timestamps replicated

8. If you want to experiment, configurations are there.

9. Instances stored in first node

10. Verify that when entries loaded from second node, first a 2lc put happens,
and second time loaded, it's a cache hit

11. Go over to the first node and update the entity, that should cause an
invalidation message to be sent to the other node.

12. If invalidation worked correctly, when loading the entity again in the
second session factory, a cache miss should happen, followed by a put, with
the correct updated data.

13. Run **Cluster2lcTest.testClusteredEntityCache** with system properties:
`-Djava.net.preferIPv4Stack=true -Djgroups.bind_addr=127.0.0.1`

14. Clustered query test verifies that queries, via the update timestamps cache,
are invalidated when entities are updated.

15. When the query is first executed in second node, a cache miss + put happens.

16. Second time the query is executed in second node, a cache hit happens.

17. Go to first node and update the instance, which should trigger an update
timestamps cache update.

18. When queried again in 2nd node, the updates are present, and the query
result in a cache miss + put (not hit!).

19. Run **Cluster2lcTest** with system properties:
`-Djava.net.preferIPv4Stack=true -Djgroups.bind_addr=127.0.0.1`

Demo 4
======

1. Use arquillian to demonstrate JPA second level caching in JBoss AS 7.1.1

2. Creates a war with an entity and a corresponding persistence.xml

3. persistence.xml has `ENABLE_SELECTIVE` as cache mode to only cache entities with @Cacheable

4. persistence.xml has second level cache and query cache enabled

5. The test starts inserting 4 entity instances

6. A query for all the entities inserted is executed and checks the second level cache statistics

7. Notice a slight difference in how the query is marked cacheable, this is done via a hint

8. Notice how to retrieve Hibernate cache statistics in a JPA environment

9. Run JpaManagedWith2lcTest
