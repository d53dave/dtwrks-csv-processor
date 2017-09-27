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
import common.DBUtil
import processing.CSVFileProcessor.WorkerFailure
import org.specs2.specification.BeforeAfterAll
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.Paths
import processing.CSVFileProcessor
import models.CSVUpload
import akka.stream.ActorMaterializer
import akka.testkit.TestProbe
import akka.testkit.TestActorRef
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.DurationInt

import java.io.PrintWriter
import akka.actor.ActorRef

class FileProcessorSpec extends TestKit(ActorSystem("FileProcessorSpec")) with ImplicitSender with SpecificationLike with BeforeAfter with BeforeAfterAll {

  def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  implicit val materializer = ActorMaterializer()

  "A DB Persister" should {

    "Properly handle an empty file" in {
      val f = Files.createTempFile("test", "file")
      val csv = CSVUpload("id", "name", f.toString())
      val fileProcessor = TestActorRef(new CSVFileProcessor(csv, materializer) {
        override def createWorker(group: Seq[Option[Event]]): ActorRef = {
          println(s"CREATEWORKER with $group")
          throw new IllegalStateException("File is empty, this should not be called")
        }
      })
      fileProcessor ! CSVFileProcessor.StartProcessing

      val probe = TestProbe()
      probe watch fileProcessor
      probe.expectTerminated(fileProcessor)
      success
    }

    "Properly handle a nonextistent file" in {
      val csv2 = CSVUpload("id", "name", "garbagepath")
      val fileProcessor = TestActorRef(new CSVFileProcessor(csv2, materializer) {
        override def createWorker(group: Seq[Option[Event]]): ActorRef = {
          println(s"CREATEWORKER with $group")
          throw new IllegalStateException("File is empty, this should not be called")
        }
      })

      fileProcessor ! CSVFileProcessor.StartProcessing

      awaitAssert(assert(fileProcessor.underlyingActor.workers.size == 0))

      val probe = TestProbe()
      probe watch fileProcessor
      probe.expectTerminated(fileProcessor)
      success
    }
    
    // Testing Actors is hard!  
    //
    // At this point, I would have asked for help if there is anybody with
    // experience doing such tests. The following tests are failing.

    "Properly generate events based on CSV file" in pending {
      val f2 = Files.createTempFile("test", "file2")

      new PrintWriter(f2.toFile()) {
        write("1, Dave, 2016-02-02 00:00:00.0\n");
        write("2, Test, 2016-02-02 00:00:00.0");
        close
      }

      val csv3 = CSVUpload("id", "name", f2.toString())
      val childProbe = TestProbe()
      val fileProcessor = TestActorRef(new CSVFileProcessor(csv3, materializer) {
        override def createWorker(group: Seq[Option[Event]]): ActorRef = {
          println(s"CREATEWORKER with $group")
          assert(group.size == 2, s"Groups size is ${group.size} but should be 2")
          childProbe.ref
        }
      })

      within(1.second) {
        fileProcessor ! CSVFileProcessor.StartProcessing

        childProbe.expectMsg(DBPersister.Persist)

        val probe = TestProbe()
        probe watch fileProcessor
        probe.expectTerminated(fileProcessor)
      }

      success
    }

    "Properly generate events based on CSV file, with duplicates" in pending {
      val f3 = Files.createTempFile("test", "file3")

      new PrintWriter(f3.toFile()) {
        write("42, Dave42, 2016-02-02 00:00:00.0\n");
        write("42, Test, 2016-02-02 00:00:00.0");
        close
      }

      val csv4 = CSVUpload("id", "name", f3.toString())
      val childProbe = TestProbe()
      val fileProcessor = TestActorRef(Props(new CSVFileProcessor(csv4, materializer) {
        override def createWorker(group: Seq[Option[Event]]): ActorRef = {
          println(s"CREATEWORKER with $group")
          assert(group.size == 1, s"Groups size is ${group.size} but should be 1")
          childProbe.ref
        }
      }))

      within(5.second) {
        fileProcessor ! CSVFileProcessor.StartProcessing

        childProbe.expectMsg(DBPersister.Persist)

        val probe = TestProbe()
        probe watch fileProcessor
        probe.expectTerminated(fileProcessor)
      }

      success
    }
  }

  def after: Any = {

  }

  def before: Any = {

  }

  def beforeAll(): Unit = {

  }
}