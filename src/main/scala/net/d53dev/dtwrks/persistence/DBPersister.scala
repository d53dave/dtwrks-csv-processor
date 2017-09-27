package persistence

import akka.actor.ActorLogging
import akka.actor.Actor

import scalikejdbc._, SQLInterpolation._
import models.CSVUpload
import models.Event
import scala.util.Try
import processing.CSVFileProcessor.WorkerAck
import processing.CSVFileProcessor.WorkerFailure

object DBPersister {
  case object Persist
}

/**
 * This actor will take a sequence of Events and batch-persist them into the database.
 */
class DBPersister(events: Seq[Option[Event]])(implicit val session: DBSession = AutoSession) extends Actor with ActorLogging {
  import persistence.DBPersister._

  def receive: Actor.Receive = {
    case Persist => {
      val batchParams: Seq[Seq[Any]] = events.flatten.map(e => Seq[Any](e.id, e.name, e.timeOfStart))
      try {
        sql"insert into events (id, name, time_of_start) values (?, ?, ?)".batch(batchParams: _*).apply()
        sender ! WorkerAck
      } catch {
        case t: Throwable => sender() ! WorkerFailure(t)
      }

      log.info(s"DBPersister $self finished, stopping.")
      context.stop(self)
    }
  }
}