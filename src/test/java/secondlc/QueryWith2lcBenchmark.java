package secondlc;

import com.carrotsearch.junitbenchmarks.AbstractBenchmark;
import com.carrotsearch.junitbenchmarks.annotation.AxisRange;
import com.carrotsearch.junitbenchmarks.annotation.BenchmarkMethodChart;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.stat.QueryStatistics;
import org.jboss.byteman.agent.submit.Submit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import secondlc.entities.Family;

import java.util.List;
import java.util.logging.LogManager;

import static org.jgroups.util.Util.assertTrue;
import static org.junit.Assert.assertEquals;
import static secondlc.Util.*;

/**
 * // TODO: Document this
 *
 * @author Galder Zamarreño
 * @since // TODO
 */
@AxisRange(min = 0, max = 1)
@BenchmarkMethodChart(filePrefix = "benchmark-query")
public class QueryWith2lcBenchmark extends AbstractBenchmark {

   static final String[] FAMILY_TITLES = {
         "Socrates",
         "Zico",
         "Falcao",
         "Eder",
   };

   static int REPS = 1000;
   static SessionFactory no2lcSessionFactory;
   static SessionFactory with2lcSessionFactory;

   // Inject Byteman rules at runtime
   static Submit submit;

   @Test
   public void timeQueryNo2lc() throws Exception {
      benchmarkQueryNo2lc();
   }

   @Test
   public void timeQuery2lc() throws Exception {
      benchmarkQuery2lc();
   }

   @SuppressWarnings("unchecked")
   public void benchmarkQueryNo2lc() {
      for (int i = 0; i < REPS; i++) {
         Session s = no2lcSessionFactory.openSession();
         Query query = s.createQuery("from Family");
         List<Family> result = (List<Family>) query.list();

         // Assert expectations :)
         assertEquals(FAMILY_TITLES.length, result.size());
         QueryStatistics stats = statsQuery(no2lcSessionFactory);
         assertEquals(0, stats.getCacheHitCount());
         assertEquals(0, stats.getCachePutCount());

         s.close();
      }
   }

   @SuppressWarnings("unchecked")
   public void benchmarkQuery2lc() {
      for (int i = 0; i < REPS; i++) {
         Session s = with2lcSessionFactory.openSession();
         Query query = s.createQuery("from Family");
         query.setCacheable(true); // <-- IMPORTANT, mark query cacheable!
         List<Family> result = (List<Family>) query.list();

         // Assert expectations :)
         assertEquals(FAMILY_TITLES.length, result.size());
         QueryStatistics stats = statsQuery(no2lcSessionFactory);
         assertTrue(stats.getCacheHitCount() > 0 || stats.getCachePutCount() > 0);

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

      submit = new Submit();
      addBytemanRulesIfPresent(submit);
   }

   private static SessionFactory create2lcSessionFactory() {
      Configuration cfg = FindWith2lcBenchmark.create2lcConfiguration(); // With second level cache
      // Important! Enable query cache!
      cfg.setProperty(Environment.USE_QUERY_CACHE, "true");

      return cfg.buildSessionFactory();
   }

   private static SessionFactory createNo2lcSessionFactory() {
      Configuration cfg = createMemoryDbConfiguration(); // No second level cache
      return cfg.buildSessionFactory();
   }

   private QueryStatistics statsQuery(SessionFactory sf) {
      return sf.getStatistics().getQueryStatistics("from Family");
   }

   @AfterClass
   public static void afterClass() throws Exception {
      deleteBytemanRulesIfPresent(submit);
   }

}
