package net.d53dev.dtwrks

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration.Duration
import scalikejdbc.ConnectionPool
import scalikejdbc.AutoSession
import scalikejdbc._
import scalikejdbc.SQLInterpolation._
import akka.actor.Props
import org.apache.log4j.Logger
import org.apache.log4j.Level
import akka.actor.actorRef2Scala
import messagebroker.CSVUploadConsumer

/**
 * Entry point for the File Transformer Service
 * 
 * This will setup an in-memory database and create the actor that will
 * listen incoming events, after which it will wait until shut-down.
 */
object Filetransformer extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  // initialize JDBC driver & connection pool
  Class.forName("org.h2.Driver")
  ConnectionPool.singleton("jdbc:h2:mem:filetransformer", "user", "pass")
  
  def loadSchemaString(): String = {
    io.Source.fromInputStream(this.getClass.getResourceAsStream("/schema.sql"), "UTF-8").getLines.mkString
  }

  implicit val session = AutoSession
  
  val ddl = loadSchemaString()

  SQL(ddl).execute.apply()
  
 
  Logger.getLogger("kafka").setLevel(Level.WARN);  
  system.actorOf(Props(new CSVUploadConsumer()), "CSVUploadConsumer") ! CSVUploadConsumer.Start

  Await.result(Promise[Unit].future, Duration.Inf)
}
