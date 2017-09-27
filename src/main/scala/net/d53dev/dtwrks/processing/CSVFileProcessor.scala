package processing

import akka.actor.Actor
import akka.actor.ActorLogging
import models.CSVUpload
import java.nio.file.Paths
import scala.concurrent.Future
import akka.stream.IOResult
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Sink
import akka.stream.Materializer
import akka.stream.scaladsl.Framing
import akka.util.ByteString
import akka.stream.scaladsl.Flow
import com.typesafe.config.ConfigFactory
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.DateTimeFormat
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import models.Event
import akka.stream.scaladsl.Keep
import akka.kafka.scaladsl.Consumer.Control

import akka.actor.ActorRef
import akka.actor.Props
import persistence.DBPersister
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.DurationInt

import persistence.DBPersister.Persist
import scala.concurrent.Await
import scala.util.Try
import scala.concurrent.duration.FiniteDuration
import java.time.temporal.TemporalUnit

object CSVFileProcessor {
  case object StartProcessing
  case object WorkerAck
  case class WorkerFailure(t: Throwable)

  val batchSize = Try {
    ConfigFactory.load().getInt("processor.db.batchsize")
  }.getOrElse(100)

  val batchTime = Try {
    val d = ConfigFactory.load().getDuration("processor.db.batchtime")
    FiniteDuration(d.toNanos, TimeUnit.NANOSECONDS)
  }.getOrElse(500.millis)
  
  val delimiter = Try {
    ConfigFactory.load().getString("processor.csv.delimiter")
  }.getOrElse(",")
  
  val truncateLinesAfter = Try {
    ConfigFactory.load().getInt("processor.csv.truncateLineAfter")
  }.getOrElse(10000)
}

/**
 * The CSVFileProcessor is the Actor that will load a file and process it line by line
 * using akka streams and the assoctiated FileIO.
 * 
 * The pipeline roughly looks like this
 * 
 * Source the file
 *   --> Split it to lines
 *   --> Split it it by CSV delimiter
 *   --> Map the resulting values to a Event object
 *   --> Filter Nones and duplicates
 *   --> Group into batch
 *   --> Create actor to persist batch
 *   --> Shutdown when all child actors are done
 */
class CSVFileProcessor(val csv: CSVUpload, implicit val mat: Materializer) extends Actor with ActorLogging {
  import processing.CSVFileProcessor._
  import scala.concurrent.ExecutionContext.Implicits.global

  var workers = scala.collection.mutable.Set[ActorRef]()
  val duplicateChecker = DuplicateChecker[Long]

  def receive: Actor.Receive = {
    case StartProcessing => {

      val file = Paths.get(csv.path)
      log.info(s"CSVFileProcessor $self starts processing file ${file.toString()}")
      val result = FileIO.fromPath(file)
        .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = truncateLinesAfter, allowTruncation = true))
        .map(_.utf8String.trim.split(delimiter).toSeq)
        .map(Event(_))
        .filter(event => {
          event.isDefined match {
            case true => {
              val id = event.get.id
              if (!duplicateChecker.has(id)) {
                log.debug(s"Preparing ${event.get} for Persistence")
                duplicateChecker.add(event.get.id)
                true
              } else {
                log.debug(s"${id} is duplicate");
                false
              }
            }
            case _ => false
          }
        })
        .groupedWithin(batchSize, batchTime)
        .runWith(Sink.foreach(group => {
          if (group.flatten.length > 0) {
            val worker = createWorker(group)
            workers.add(worker)
            worker ! Persist
          }
        }))
        .onComplete(_ => {
          // we try to stop here. 
          // If there still are children writing to DB, this will not exit
          // and the exit will be performed when the last Ack or Failure message
          // arrives
          stopIfDone()
        })

      context.become(running)

      ()
    }
  }

  def createWorker(group: Seq[Option[Event]]): ActorRef = {
     context.actorOf(Props(new DBPersister(group)))
  }

  def running: Receive = {
    case WorkerFailure(t) => {
      workers.remove(sender)
      log.error(s"Worker $sender indicated that a failure happened while writing to the database: ${t.getMessage}")
      stopIfDone()
    }
    case WorkerAck => {
      workers.remove(sender)
      stopIfDone()
    }
  }

  private def stopIfDone(): Unit = {
    if (workers.isEmpty) {
      log.info(s"CSVFileProcessor $self finished, stopping.")
      duplicateChecker.dispose()
      context.stop(self)
    }
  }
}