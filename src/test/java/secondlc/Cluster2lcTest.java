package secondlc;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cache.infinispan.InfinispanRegionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.stat.QueryStatistics;
import org.junit.BeforeClass;
import org.junit.Test;
import secondlc.entities.Family;

import javax.transaction.TransactionManager;

import java.util.List;
import java.util.concurrent.Callable;

import static org.jgroups.util.Util.assertTrue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static secondlc.Util.*;

/**
 * // TODO: Document this
 *
 * @author Galder Zamarreño
 * @since // TODO
 */
public class Cluster2lcTest {

   static TransactionManager tm = com.arjuna.ats.jta.TransactionManager.transactionManager();
   static SessionFactory sf1;
   static SessionFactory sf2;

   @Test
   public void testClusteredEntityCache() {
      // 1. Go to sf2, load and entity and verify 2lc put count
      Session s = sf2.openSession();
      Family family = (Family) s.load(Family.class, 1);
      assertEquals("Socrates", family.getName());
      assertEquals(1, stats2lc(sf2).getMissCount());
      assertEquals(1, stats2lc(sf2).getPutCount());
      assertEquals(0, stats2lc(sf2).getHitCount());
      s.close();

      // 2. Go to sf2, and load entity again, should be a hit
      s = sf2.openSession();
      family = (Family) s.load(Family.class, 1);
      assertEquals("Socrates", family.getName());
      assertEquals(1, stats2lc(sf2).getHitCount());
      s.close();

      // 3. Go to sf1, update the entity -> should invalidate
      Family updatedFamily = withTx(tm, new Callable<Family>() {
         @Override
         public Family call() throws Exception {
            Session s = sf1.openSession();
            s.getTransaction().begin();
            Family family = (Family) s.load(Family.class, 1);
            assertEquals("Socrates", family.getName());
            family.setName("Garrincha");
            s.update(family);
            s.getTransaction().commit();
            s.close();
            return family;
         }
      });
      assertEquals("Garrincha", updatedFamily.getName());

      // 4. Go to sf2, miss, put in 2lc, and verify new family name
      s = sf2.openSession();
      family = (Family) s.load(Family.class, 1);
      assertEquals("Garrincha", family.getName());
      assertEquals(2, stats2lc(sf2).getMissCount());
      assertEquals(2, stats2lc(sf2).getPutCount());
      s.close();
   }

   @Test
   public void testClusteredQuery() {
      final String fetchingAllFamilies = "from Family";

      // 1. Get all families from sf2
      String[] updatedFamilyNames = {
         "Garrincha", "Zico", "Falcao", "Eder",
      };
      assertContainsAllFamilies(updatedFamilyNames, queryAllFamilies());
      QueryStatistics queryStats = statsQuery(sf2.getStatistics(), fetchingAllFamilies);
      assertEquals(1, queryStats.getCacheMissCount()); // it's a miss
      assertEquals(1, queryStats.getCachePutCount()); // insert first time query is executed
      assertEquals(0, queryStats.getCacheHitCount()); // no hit yet

      // 2. Get all families again and verify hit
      assertContainsAllFamilies(updatedFamilyNames, queryAllFamilies());
      queryStats = statsQuery(sf2.getStatistics(), fetchingAllFamilies);
      assertEquals(1, queryStats.getCacheHitCount()); // now it's a hit

      // Go to sf1 and update a family
      Family updatedFamily = withTx(tm, new Callable<Family>() {
         @Override
         public Family call() throws Exception {
            Session s = sf1.openSession();
            s.getTransaction().begin();
            Family family = (Family) s.load(Family.class, 2);
            assertEquals("Zico", family.getName());
            family.setName("Rivaldo");
            s.update(family);
            s.getTransaction().commit();
            s.close();
            return family;
         }
      });
      assertEquals("Rivaldo", updatedFamily.getName());

      // Go go sf2, miss, requery and get the info
      updatedFamilyNames = new String[]{
         "Garrincha", "Rivaldo", "Falcao", "Eder",
      };
      assertContainsAllFamilies(updatedFamilyNames, queryAllFamilies());
      queryStats = statsQuery(sf2.getStatistics(), fetchingAllFamilies);
      assertEquals(2, queryStats.getCacheMissCount()); // it's a miss (query was invalidated)
      assertEquals(2, queryStats.getCachePutCount()); // insert query again
   }

   @SuppressWarnings("unchecked")
   private List<Family> queryAllFamilies() {
      return withTx(tm, new Callable<List<Family>>() {
         @Override
         public List<Family> call() throws Exception {
            Session s = sf2.openSession();
            Query query = s.createQuery("from Family");
            query.setCacheable(true); // <-- IMPORTANT, mark query cacheable!
            return (List<Family>) query.list();
         }
      });
   }

   @BeforeClass
   public static void beforeClass() throws Exception {
//      // Avoid Hibernate log polluting output
//      LogManager.getLogManager().reset();

      sf1 = create2lcSessionFactory();
      sf2 = create2lcSessionFactory();
      insertTransactionalEntities(JpaManagedWith2lcTest.FAMILY_TITLES, sf1, tm);
   }

   static SessionFactory create2lcSessionFactory() {
      Configuration cfg = create2lcConfiguration();
      return cfg.buildSessionFactory();
   }

   static Configuration create2lcConfiguration() {
      // 1. Create database pointing to file that can be shared by cluster nodes
      Configuration cfg = createFileDbConfiguration();

      // 2. Set up overall cache configuration
      cfg.setProperty(Environment.USE_SECOND_LEVEL_CACHE, "true");
      cfg.setProperty(Environment.USE_QUERY_CACHE, "true");
      cfg.setProperty(Environment.CACHE_REGION_FACTORY,
            "org.hibernate.cache.infinispan.InfinispanRegionFactory");
      cfg.setProperty(Environment.JTA_PLATFORM,
            "org.hibernate.service.jta.platform.internal.JBossStandAloneJtaPlatform");

      // 3. Use a clustered configuration for Infinispan so that caches can communicate
      cfg.setProperty(InfinispanRegionFactory.INFINISPAN_CONFIG_RESOURCE_PROP,
            "src/test/resources/infinispan-cluster.xml");

      // 4. Configure cache to be transactional since updates will happen!
      configureCacheConcurrency(cfg, "transactional"); // <-- Transactional!

      return cfg;
   }

}
