package messagebroker

import akka.stream.Materializer
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.kafka.ConsumerMessage.CommittableMessage
import scala.concurrent.Future
import akka.kafka.scaladsl.Consumer.Control
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Keep
import akka.kafka.ConsumerMessage.CommittableOffsetBatch
import akka.actor.Props
import play.api.libs.json.Json
import models.CSVUpload
import processing.CSVFileProcessor


object CSVUploadConsumer {
  type Message = CommittableMessage[Array[Byte], String]
  case object Start
  case object Stop
}

/**
 * This Actor listens for events on Kafka and spawns a CSVFileProcessor Actor
 * for each completed upload
 */
class CSVUploadConsumer(implicit mat: Materializer) extends Actor with ActorLogging {
  import CSVUploadConsumer._

  override def preStart(): Unit = {
    super.preStart()
    self ! Start
  }
  
  override def receive: Receive = {
    case Start =>
      log.info("Initializing CSV upload event consumer")
      val (control, future) = CSVUploadSource.create("CSVUploadConsumer")(context.system)
        .mapAsync(1)(processMessage)
        .map(_.committableOffset)
        .mapAsync(1)(_.commitScaladsl())
        .toMat(Sink.ignore)(Keep.both)
        .run()

      context.become(running(control))

      future.onFailure {
        case ex =>
          log.error("Stream failed due to error, restarting", ex)
          throw ex
      }

      log.info("Logging consumer started")
  }
  
  def running(control: Control): Receive = {
    case Stop =>
      log.info("Shutting down logging consumer stream and actor")
      control.shutdown().andThen {
        case _ =>
          context.stop(self)
      }
      ()
  }

  /**
   * Pull out the CSV Json, try to parse it and spawn the child Actor that
   * will handle the file processing
   */
  private def processMessage(msg: Message): Future[Message] = {
    log.info(s"Consumed CSV File: ${msg.record.value()}")
    val csv = Json.parse(msg.record.value()).validate[CSVUpload]
    if(csv.isSuccess) {
      val fileProcessor = context.actorOf(Props(new CSVFileProcessor(csv.get, mat)))
    
      fileProcessor ! CSVFileProcessor.StartProcessing
    } else {
      log.error(s"Could not parse JSON from received message")
    }
    
    Future.successful(msg)
  }

}