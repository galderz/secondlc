package secondlc;

import org.hibernate.annotations.QueryHints;
import org.hibernate.ejb.HibernateEntityManagerFactory;
import org.hibernate.stat.QueryStatistics;
import org.hibernate.stat.SecondLevelCacheStatistics;
import org.hibernate.stat.Statistics;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import secondlc.entities.Family;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.transaction.UserTransaction;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static secondlc.Util.assertContainsAllFamilies;
import static secondlc.Util.statsQuery;

/**
 * // TODO: Document this
 *
 * @author Galder Zamarreño
 * @since // TODO
 */
@RunWith(Arquillian.class)
public class JpaManagedWith2lcTest {

   static final String[] FAMILY_TITLES = {
         "Socrates",
         "Zico",
         "Falcao",
         "Eder",
   };

   static String DEPLOYMENT_NAME = "test.war";
   static String PERSISTENCE_UNIT_NAME = "jpa2lc-unit";
   static String FAMILY_REGION_NAME = String.format(
         "%s#%s.%s", DEPLOYMENT_NAME, PERSISTENCE_UNIT_NAME, Family.class.getName()
   );

   @Deployment
   public static Archive<?> createDeployment() {
      return ShrinkWrap.create(WebArchive.class, "test.war")
            .addPackage(Family.class.getPackage())
            .addClass(Util.class)
            .addAsResource("persistence-managed.xml", "META-INF/persistence.xml")
            .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
   }

   @PersistenceContext
   EntityManager em;

   @Inject
   UserTransaction utx;

   @Test
   public void shouldFindAllFamiliesUsingJpqlQuery() throws Exception {
      // given
      String fetchingAllFamilies = "select f from Family f order by f.id";
      for (int i = 0; i < 3; i++) {
         // when
         System.out.println("Selecting (using JPQL)...");
         TypedQuery<Family> query = em.createQuery(fetchingAllFamilies, Family.class);
         query.setHint(QueryHints.CACHEABLE, true); // <-- IMPORTANT, mark query cacheable!

         // then
         List<Family> families = query.getResultList();
         System.out.println("Found " + families.size() + " families (using JPQL):");
         assertContainsAllFamilies(FAMILY_TITLES, families);

         // Delay queries to get precise counts
         Thread.sleep(1000);
      }

      // finally, check stats
      Statistics stats = getStats();
      SecondLevelCacheStatistics secondlcStats = get2lcStats(stats);
      QueryStatistics queryStats = statsQuery(stats, fetchingAllFamilies);
      assertEquals(4, secondlcStats.getPutCount()); // insert families
      assertEquals(1, queryStats.getCachePutCount()); // insert first time query is executed
      assertEquals(2, queryStats.getCacheHitCount()); // hit query cache twice
   }

   private SecondLevelCacheStatistics get2lcStats(Statistics stats) {
      return stats.getSecondLevelCacheStatistics(FAMILY_REGION_NAME);
   }

   private Statistics getStats() {
      return ((HibernateEntityManagerFactory) em.getEntityManagerFactory())
            .getSessionFactory().getStatistics();
   }

   // Arquillian executes the @Before and @After methods inside the container,
   // before and after each test method, respectively. The @Before method gets
   // invoked after the injections have occurred.

   @Before
   public void preparePersistenceTest() throws Exception {
      clearData();
      insertData();
      startTransaction();
   }

   @After
   public void commitTransaction() throws Exception {
      utx.commit();
   }

   private void clearData() throws Exception {
      utx.begin();
      em.joinTransaction();
      System.out.println("Dumping old families...");
      em.createQuery("delete from Family").executeUpdate();
      utx.commit();
   }

   private void insertData() throws Exception {
      utx.begin();
      em.joinTransaction();
      System.out.println("Inserting records...");
      for (String familyName : FAMILY_TITLES) {
         Family family = new Family(familyName);
         em.persist(family);
      }
      utx.commit();
      // clear the persistence context (first-level cache)
      em.clear();
   }

   private void startTransaction() throws Exception {
      utx.begin();
      em.joinTransaction();
   }

}
