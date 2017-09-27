package persistence

import akka.actor.ActorSystem
import akka.actor.Actor
import akka.actor.Props
import akka.testkit.{ TestKit, ImplicitSender }
import org.specs2.mutable.SpecificationLike
import scalikejdbc._, SQLInterpolation._
import models.Event

import java.util.Formatter.DateTime
import org.joda.time.DateTime
import processing.CSVFileProcessor.WorkerAck
import org.specs2.mutable.BeforeAfter
import org.specs2.specification.BeforeAfterAll
import common.DBUtil
import processing.CSVFileProcessor.WorkerFailure
import akka.testkit.TestActor
import akka.testkit.TestActorRef

class DBPersisterSpec extends TestKit(ActorSystem("persistertest")) with ImplicitSender with BeforeAfterAll with SpecificationLike {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  Class.forName("org.h2.Driver")
  ConnectionPool.singleton("jdbc:h2:mem:persistertest", "user", "pass")

  implicit val session: DBSession = DB.autoCommitSession()
  DBUtil.createDB
  DBUtil.truncateEvents // force commit here

  sequential

  "A DB Persister" should {

    "Persist a seq of Events" in {
      val now = DateTime.now()
      val group1 = Seq(Some(Event.apply(1, "test1", now)), None, Some(Event.apply(42, "test2", now.minusDays(1))))

      val persister = TestActorRef(new DBPersister(group1))

      persister ! DBPersister.Persist
      expectMsg(WorkerAck)

      val events = DBUtil.getEventsFromDb
      assert(events.size == 2, "The actor should have persisted 2 events into the db")
      assert(events == group1.flatten, "The retrieved Events should be equal to the persisted events")
      DBUtil.truncateEvents
      success
    }

    "Not persist an empty seq" in {
      val group2 = Seq.empty
      val persister2 = TestActorRef(new DBPersister(group2))

      persister2 ! DBPersister.Persist
      expectMsg(WorkerAck)
      val events2 = DBUtil.getEventsFromDb
      assert(events2.size == 0, "The actor should not have persisted events into the db")
      DBUtil.truncateEvents
      success
    }

    "Should report WorkerFailure if persisting fails" in {
      val group3 = Seq(Some(Event.apply(42, null, null)))
      val persister3 = TestActorRef(new DBPersister(group3))

      persister3 ! DBPersister.Persist
      expectMsgPF() {
        case WorkerFailure(t) => true
      }
      DBUtil.truncateEvents
      success
    }

  }

  def beforeAll(): Unit = {

  }
}