package secondlc;

import com.carrotsearch.junitbenchmarks.AbstractBenchmark;
import com.carrotsearch.junitbenchmarks.annotation.AxisRange;
import com.carrotsearch.junitbenchmarks.annotation.BenchmarkMethodChart;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cache.infinispan.InfinispanRegionFactory;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.stat.SecondLevelCacheStatistics;
import org.jboss.byteman.agent.submit.Submit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import secondlc.entities.Family;

import java.util.logging.LogManager;

import static org.jgroups.util.Util.assertTrue;
import static org.junit.Assert.assertEquals;
import static secondlc.Util.*;

/**
 * // TODO: Document this
 *
 * @author Galder Zamarreño
 */
@AxisRange(min = 0, max = 1)
@BenchmarkMethodChart(filePrefix = "benchmark-find")
public class FindWith2lcBenchmark extends AbstractBenchmark {

   static int REPS = 1000;
   static SessionFactory no2lcSessionFactory;
   static SessionFactory with2lcSessionFactory;

   static final String[] FAMILY_TITLES = {
         "Socrates",
         "Zico",
         "Falcao",
         "Eder",
   };

//   // Inject Byteman rules at runtime
//   static Submit submit;

   @Test
   public void timeFindNo2lc() throws Exception {
      benchmarkFindNo2lc();
   }

   @Test
   public void timeFind2lc() throws Exception {
      benchmarkFind2lc();
   }

   void benchmarkFindNo2lc() {
      for (int i = 0; i < REPS; i++) {
         Session s = no2lcSessionFactory.openSession();
         int id = (i % FAMILY_TITLES.length) + 1;
         // System.out.println("Load family with id=" + id);
         Family family = (Family) s.load(Family.class, id);

         // Assert expectations :)
         assertEquals(FAMILY_TITLES[id - 1], family.getName());
         SecondLevelCacheStatistics stats = stats2lc(no2lcSessionFactory);
         assertEquals(null, stats);

         s.close();
      }
   }

   void benchmarkFind2lc() {
      for (int i = 0; i < REPS; i++) {
         Session s = with2lcSessionFactory.openSession();
         int id = (i % FAMILY_TITLES.length) + 1;
         // System.out.println("Load family with id=" + id);
         Family family = (Family) s.load(Family.class, id);

         // Assert expectations :)
         assertEquals(FAMILY_TITLES[id - 1], family.getName());
         SecondLevelCacheStatistics stats = stats2lc(with2lcSessionFactory);
         assertTrue(stats.getSizeInMemory() > 0);

         s.close();
      }
   }

   @BeforeClass
   public static void beforeClass() throws Exception {
      // Avoid Hibernate log polluting output
      LogManager.getLogManager().reset();

      no2lcSessionFactory = createNo2lcSessionFactory();
      with2lcSessionFactory = create2lcSessionFactory();

      // Insert entities
      insertReadOnlyEntities(FAMILY_TITLES, no2lcSessionFactory);

//      submit = new Submit();
//      addBytemanRulesIfPresent(submit);
   }

   static SessionFactory create2lcSessionFactory() {
      Configuration cfg = create2lcConfiguration();
      return cfg.buildSessionFactory();
   }

   static Configuration create2lcConfiguration() {
      Configuration cfg = createMemoryDbConfiguration(); // With second level cache

      // 1. Set up overall cache configuration
      cfg.setProperty(Environment.USE_SECOND_LEVEL_CACHE, "true");
      cfg.setProperty(Environment.CACHE_REGION_FACTORY,
            "org.hibernate.cache.infinispan.InfinispanRegionFactory");
      cfg.setProperty(InfinispanRegionFactory.INFINISPAN_CONFIG_RESOURCE_PROP,
            "src/test/resources/infinispan-readonly-local.xml");
      cfg.setProperty(Environment.JTA_PLATFORM,
            "org.hibernate.service.jta.platform.internal.JBossStandAloneJtaPlatform");

      // 2. Configure cache settings per entity.
      configureCacheConcurrency(cfg, AccessType.READ_ONLY.getExternalName());
      // Alternatively, use:
      // @org.hibernate.annotations.Cache(
      //    usage=CacheConcurrencyStrategy.[NONE|READ_ONLY|NONSTRICT_READ_WRITE|READ_WRITE|TRANSACTIONAL]
      // )

      return cfg;
   }

   static SessionFactory createNo2lcSessionFactory() {
      Configuration cfg = createMemoryDbConfiguration(); // No second level cache
      return cfg.buildSessionFactory();
   }

//   @AfterClass
//   public static void afterClass() throws Exception {
//      deleteBytemanRulesIfPresent(submit);
//   }

}
