package secondlc;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.stat.QueryStatistics;
import org.hibernate.stat.SecondLevelCacheStatistics;
import org.hibernate.stat.Statistics;
import org.jboss.byteman.agent.submit.Submit;
import secondlc.entities.Address;
import secondlc.entities.Family;
import secondlc.entities.Person;

import javax.transaction.Status;
import javax.transaction.TransactionManager;
import java.net.ConnectException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * // TODO: Document this
 *
 * @author Galder Zamarreño
 * @since // TODO
 */
public enum Util {

   ;

   static Configuration createMemoryDbConfiguration() {
      Configuration cfg = new Configuration();
      cfg.setProperty(Environment.DIALECT, "org.hibernate.dialect.H2Dialect");
      cfg.setProperty(Environment.DRIVER, "org.h2.Driver");
      cfg.setProperty(Environment.URL, "jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1;MVCC=TRUE");
      cfg.setProperty(Environment.USER, "sa");
      cfg.setProperty(Environment.POOL_SIZE, "5");
      cfg.setProperty(Environment.FORMAT_SQL, "true");
      cfg.setProperty(Environment.MAX_FETCH_DEPTH, "5");
      cfg.setProperty(Environment.HBM2DDL_AUTO, "create-drop");

      // Generate statistics to see effects of second level cache
      cfg.setProperty(Environment.GENERATE_STATISTICS, "true");

      // Set up entity mappings
      configureMappings(cfg);
      return cfg;
   }

   static Configuration createFileDbConfiguration() {
      Configuration cfg = new Configuration();
      cfg.setProperty(Environment.DIALECT, "org.hibernate.dialect.H2Dialect");
      cfg.setProperty(Environment.DRIVER, "org.h2.Driver");
      cfg.setProperty(Environment.URL, "jdbc:h2:file:target/database");
      cfg.setProperty(Environment.USER, "sa");
      cfg.setProperty(Environment.POOL_SIZE, "5");
      cfg.setProperty(Environment.FORMAT_SQL, "true");
      cfg.setProperty(Environment.MAX_FETCH_DEPTH, "5");
      cfg.setProperty(Environment.HBM2DDL_AUTO, "create-drop");

      // Generate statistics to see effects of second level cache
      cfg.setProperty(Environment.GENERATE_STATISTICS, "true");

      // Set up entity mappings
      configureMappings(cfg);
      return cfg;
   }

   static void configureMappings(Configuration cfg) {
      Class<?>[] annotatedClasses = new Class[]{Family.class, Person.class, Address.class};
      for (Class<?> annotatedClass : annotatedClasses) {
         cfg.addAnnotatedClass(annotatedClass);
      }

      cfg.buildMappings();
   }

   static void configureCacheConcurrency(Configuration cfg, String cacheStrategy) {
      Iterator it = cfg.getClassMappings();
      while (it.hasNext()) {
         PersistentClass clazz = (PersistentClass) it.next();
         if (!clazz.isInherited()) {
            cfg.setCacheConcurrencyStrategy(clazz.getEntityName(), cacheStrategy);
         }
      }
      it = cfg.getCollectionMappings();
      while (it.hasNext()) {
         Collection coll = (Collection) it.next();
         cfg.setCollectionCacheConcurrencyStrategy(coll.getRole(), cacheStrategy);
      }
   }

   static <T> T withTx(TransactionManager tm, Callable<T> c) {
      try {
         tm.begin();
         try {
            return c.call();
         } catch (Exception e) {
            tm.setRollbackOnly();
            throw e;
         } finally {
            if (tm.getStatus() == Status.STATUS_ACTIVE) tm.commit();
            else tm.rollback();
         }
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   static void insertReadOnlyEntities(String[] familyNames, final SessionFactory sessionFactory) {
      for (String familyName : familyNames) {
         Session s = sessionFactory.openSession();
         s.getTransaction().begin();
         s.persist(new Family(familyName));
         s.getTransaction().commit();
         s.close();
      }
   }

   static void insertTransactionalEntities(
         String[] familyNames, final SessionFactory sessionFactory,
         TransactionManager tm) {
      for (final String familyName : familyNames) {
         withTx(tm, new Callable<Void>() {
            public Void call() throws Exception {
               Session s = sessionFactory.openSession();
               s.getTransaction().begin();
               s.persist(new Family(familyName));
               s.getTransaction().commit();
               s.close();
               return null;
            }
         });
      }
   }

   static void assertContainsAllFamilies(String[] familyNames,
         java.util.Collection<Family> retrievedFamilies) {
      assertEquals(familyNames.length, retrievedFamilies.size());
      final Set<String> retrievedFamilyTitles = new HashSet<String>();
      for (Family family : retrievedFamilies) {
         System.out.println("* " + family);
         retrievedFamilyTitles.add(family.getName());
      }
      assertTrue(retrievedFamilyTitles.containsAll(Arrays.asList(familyNames)));
   }

   static SecondLevelCacheStatistics stats2lc(SessionFactory sf) {
      return sf.getStatistics()
            .getSecondLevelCacheStatistics(Family.class.getName());
   }

   static QueryStatistics statsQuery(Statistics stats, String query) {
      return stats.getQueryStatistics(query);
   }

   static void addBytemanRulesIfPresent(Submit submit) throws Exception {
      try {
         submit.addRulesFromFiles(
               Collections.singletonList("src/test/resources/slowdb.btm"));
      } catch (ConnectException e) {
         // Ignore if not there
      }
   }

   static void deleteBytemanRulesIfPresent(Submit submit) throws Exception {
      try {
         submit.deleteAllRules();
      } catch (ConnectException e) {
         // Ignore if not there
      }
   }

}
